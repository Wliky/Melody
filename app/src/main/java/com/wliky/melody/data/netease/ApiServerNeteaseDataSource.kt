package com.wliky.melody.data.netease

import com.wliky.melody.BuildConfig
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.network.ApiClient
import com.wliky.melody.core.network.int
import com.wliky.melody.core.network.objOrNull
import com.wliky.melody.core.security.SecureSessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 自建 API 服务模式：把请求交给用户自己部署的兼容服务（例如 NeteaseCloudMusicApi）。
 *
 * 兼容性最好，也最容易在接口变化时自行修复——纯 HTTP，不涉及客户端加密。
 * 地址在「设置 → 数据源」里填写。
 */
@Singleton
class ApiServerNeteaseDataSource @Inject constructor(
    // 不加 private val：这两个由基类以 protected 形式持有，直接复用，避免遮蔽同名成员
    apiClient: ApiClient,
    session: SecureSessionStore,
    private val settingsRepository: SettingsRepository,
) : BaseNeteaseDataSource(apiClient, session) {

    override val mode: ApiMode = ApiMode.API_SERVER

    override val supportsPlaybackReport: Boolean = true

    override fun adaptPayload(endpoint: NeteaseEndpoint, payload: JsonObject): JsonObject = when (endpoint) {
        NeteaseEndpoint.CLOUD_SEARCH ->
            JsonObject(payload.filterKeys { it in setOf("keywords", "type", "limit", "offset") })

        NeteaseEndpoint.SEARCH_SUGGEST -> JsonObject(payload.filterKeys { it == "keywords" })

        NeteaseEndpoint.SONG_DETAIL -> {
            val ids = payload["ids"].flatStrings()
            JsonObject(mapOf("ids" to JsonPrimitive(ids.joinToString(","))))
        }

        NeteaseEndpoint.SONG_URL -> {
            val first = payload["ids"].flatStrings().firstOrNull().orEmpty()
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(first),
                    "level" to (payload["level"] ?: JsonPrimitive("exhigh")),
                ),
            )
        }

        else -> JsonObject(payload.filterKeys { it != "s" && it != "total" })
    }

    override suspend fun request(endpoint: NeteaseEndpoint, payload: JsonObject): JsonElement {
        val path = requirePath(endpoint.serverPath, endpoint.name)
        val url = baseUrl() + path
        val params = payload.toQueryMap().toMutableMap()
        session.cookie().takeIf { it.isNotBlank() }?.let { params["cookie"] = it }

        val response = if (endpoint == NeteaseEndpoint.SCROBBLE) {
            apiClient.postForm(url, params, defaultHeaders())
        } else {
            apiClient.get(url, params, defaultHeaders())
        }
        mergeCookies(response.setCookies())
        response.requireSuccess()
        val element = response.parseBody(url)
        if (element.objOrNull()?.int("code") == 301) throw AppError.Unauthorized()
        return element
    }

    /**
     * 播放记录上报。只在用户显式打开开关、且填了自建服务时才会走到这里，
     * 属于「可插拔 Provider」的一部分（文档 §9）。
     */
    override suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean {
        if (events.isEmpty()) return true
        val url = baseUrl() + requirePath(NeteaseEndpoint.SCROBBLE.serverPath, NeteaseEndpoint.SCROBBLE.name)
        var allSucceeded = true
        events.forEach { event ->
            val ok = runCatching {
                val form = mutableMapOf(
                    "id" to event.songId,
                    "sourceid" to "0",
                    "time" to (event.durationSeconds * 1000L).toString(),
                )
                session.cookie().takeIf { it.isNotBlank() }?.let { form["cookie"] = it }
                val response = apiClient.postForm(url, form, defaultHeaders())
                mergeCookies(response.setCookies())
                response.requireSuccess()
                true
            }.getOrDefault(false)
            if (!ok) allSucceeded = false
        }
        return allSucceeded
    }

    private suspend fun baseUrl(): String {
        val configured = settingsRepository.current().apiBaseUrl.trim().trimEnd('/')
        if (configured.isBlank()) {
            throw AppError.Server("还没有填写自建 API 服务地址，请到「设置 → 数据源」里配置")
        }
        return configured
    }

    private fun defaultHeaders(): Map<String, String> = mapOf("User-Agent" to USER_AGENT)

    private fun JsonObject.toQueryMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        forEach { (key, value) ->
            when (value) {
                is JsonPrimitive -> result[key] = value.contentOrNull ?: value.toString()
                is JsonArray -> result[key] =
                    value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(",")

                else -> Unit
            }
        }
        return result
    }

    private fun JsonElement?.flatStrings(): List<String> = when (this) {
        null -> emptyList()
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        else -> emptyList()
    }

    private companion object {
        val USER_AGENT = "Melody/${BuildConfig.VERSION_NAME}"
    }
}
