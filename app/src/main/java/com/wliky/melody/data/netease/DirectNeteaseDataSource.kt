package com.wliky.melody.data.netease

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.core.model.SongUrl
import com.wliky.melody.core.network.ApiClient
import com.wliky.melody.core.network.HttpResponseData
import com.wliky.melody.core.network.arrayOrNull
import com.wliky.melody.core.network.int
import com.wliky.melody.core.network.jsonObjectOf
import com.wliky.melody.core.network.objOrNull
import com.wliky.melody.core.network.str
import com.wliky.melody.core.network.toJsonPrimitive
import com.wliky.melody.core.security.SecureSessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 直连模式（默认）：客户端直接请求官方接口，不需要自建服务。
 *
 * 请求方式与官方客户端一致：路径 `api` 段替换为 `weapi`，参数走 AES+RSA 加密后 POST。
 * 登录态通过 Cookie 维持，Cookie 存在 Android Keystore 保护的存储里，
 * 每次响应都会把新的 Set-Cookie 合并回会话（含 __csrf）。
 */
@Singleton
class DirectNeteaseDataSource @Inject constructor(
    apiClient: ApiClient,
    session: SecureSessionStore,
) : BaseNeteaseDataSource(apiClient, session) {

    override val mode: ApiMode = ApiMode.DIRECT

    override fun adaptPayload(endpoint: NeteaseEndpoint, payload: JsonObject): JsonObject = when (endpoint) {
        NeteaseEndpoint.CLOUD_SEARCH ->
            JsonObject(payload.filterKeys { it in setOf("s", "type", "limit", "offset", "total") })

        NeteaseEndpoint.SEARCH_SUGGEST -> JsonObject(payload.filterKeys { it == "s" })

        NeteaseEndpoint.SONG_URL -> JsonObject(payload.filterKeys { it in setOf("ids", "level", "encodeType") })

        NeteaseEndpoint.SONG_DETAIL -> {
            val ids = payload["ids"].arrayOrNull().orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val c = JsonArray(ids.map { id -> JsonObject(mapOf("id" to JsonPrimitive(id))) })
            jsonObjectOf("c" to JsonPrimitive(c.toString()))
        }

        else -> JsonObject(payload.filterKeys { it != "keywords" })
    }

    override suspend fun request(endpoint: NeteaseEndpoint, payload: JsonObject): JsonElement {
        val path = NeteaseCrypto.transformPath(
            requirePath(endpoint.directPath, endpoint.name),
            target = "weapi",
        )
        val cookie = session.cookie()
        val csrf = parseCookie(cookie)["__csrf"].orEmpty()
        val body = if (csrf.isBlank()) payload else JsonObject(payload + ("csrf_token" to JsonPrimitive(csrf)))
        val form = NeteaseCrypto.weapi(body.toString())
        val url = "$WEB_HOST$path?csrf_token=$csrf"

        val response = apiClient.postForm(url, form, webHeaders(cookie))
        mergeCookies(response.setCookies())
        response.requireSuccess()
        val element = response.parseBody(url)
        throwIfLoginRequired(element)
        return element
    }

    /**
     * 高音质 / 部分曲目在 weapi 下可能拿不到地址，此时用 eapi 再试一次。
     * 两条链路互相兜底，符合文档 §6「接口变更需可恢复」。
     */
    override suspend fun songUrl(songId: String, quality: AudioQuality): SongUrl {
        val primary = super.songUrl(songId, quality)
        if (primary.playable) return primary
        return runCatching { requestSongUrlViaEapi(songId, quality) }.getOrDefault(primary)
    }

    private suspend fun requestSongUrlViaEapi(songId: String, quality: AudioQuality): SongUrl {
        val path = NeteaseCrypto.transformPath(NeteaseEndpoint.SONG_URL.directPath, target = "eapi")
        val payload = JsonObject(
            mapOf(
                "ids" to JsonPrimitive(JsonArray(listOf(JsonPrimitive(songId))).toString()),
                "level" to JsonPrimitive(quality.apiValue),
                "encodeType" to JsonPrimitive("flac"),
            ),
        )
        val form = mapOf("params" to NeteaseCrypto.eapi(path, payload.toString()))
        val url = "$EAPI_HOST$path"
        val response = apiClient.postForm(url, form, webHeaders(session.cookie()))
        mergeCookies(response.setCookies())
        response.requireSuccess()
        val item = response.parseBody(url).objOrNull()?.get("data").arrayOrNull()?.firstOrNull()?.objOrNull()
            ?: return SongUrl(songId = songId, quality = quality)
        return SongUrl(
            songId = songId,
            url = item.str("url")?.takeIf { it.isNotBlank() },
            quality = quality,
            actualQuality = item.str("level"),
            bitrate = item.int("br") ?: 0,
            sizeBytes = item.str("size")?.toLongOrNull() ?: 0L,
        )
    }

    private fun throwIfLoginRequired(element: JsonElement) {
        val code = element.objOrNull()?.int("code") ?: return
        // 301 = 需要登录 / 登录态失效
        if (code == 301) throw AppError.Unauthorized()
    }

    private fun webHeaders(cookie: String): Map<String, String> = buildMap {
        put("Referer", "$WEB_HOST/")
        put("Origin", WEB_HOST)
        put("User-Agent", USER_AGENT)
        put("Content-Type", "application/x-www-form-urlencoded")
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    private companion object {
        const val WEB_HOST = "https://music.163.com"
        const val EAPI_HOST = "https://interface3.music.163.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
