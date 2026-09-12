package com.wliky.melody.data.netease

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.core.model.CommentPage
import com.wliky.melody.core.model.CommentSort
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.SongUrl
import com.wliky.melody.core.network.ApiClient
import com.wliky.melody.core.network.CookieParser
import com.wliky.melody.core.network.MelodyJson
import com.wliky.melody.core.network.arr
import com.wliky.melody.core.network.arrayOrNull
import com.wliky.melody.core.network.boolean
import com.wliky.melody.core.network.int
import com.wliky.melody.core.network.jsonObjectOf
import com.wliky.melody.core.network.obj
import com.wliky.melody.core.network.objOrNull
import com.wliky.melody.core.network.str
import com.wliky.melody.core.network.toJsonPrimitive
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.dto.toDomain
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
 *
 * ### 关于请求头（登录失败的根因）
 *
 * 网易近年收紧了风控：**登录链路（二维码 key / 轮询）只认移动端 UA，且要求
 * `Referer` 指向 `/login`**，否则服务端返回 403，body 里没有 unikey ——
 * 客户端侧看到的就是「二维码返回异常，接口可能变更」。
 * 因此 [requestViaWeapi] 对登录端点单独换了一套请求头，普通业务接口仍用桌面 Web 头。
 *
 * ### 双链路
 *
 * 登录端点与歌曲地址都做 weapi → eapi 的兜底重试。eapi 面向移动客户端，
 * 会额外携带设备信息（deviceId / appver / os），在 weapi 被拦时往往仍然可用。
 */
@Singleton
class DirectNeteaseDataSource @Inject constructor(
    apiClient: ApiClient,
    session: SecureSessionStore,
) : BaseNeteaseDataSource(apiClient, session) {

    override val mode: ApiMode = ApiMode.DIRECT

    override val supportsPlaybackReport: Boolean = true

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
        if (endpoint.isLoginEndpoint) {
            // 登录链路：weapi 优先，失败或拿不到关键字段时用 eapi 再试一次。
            val viaWeapi = runCatching { requestViaWeapi(endpoint, payload, mobileHeaders = true) }
            val weapiResult = viaWeapi.getOrNull()
            if (weapiResult != null && weapiResult.looksLikeQrPayload()) return weapiResult

            val viaEapi = runCatching { requestViaEapi(endpoint, payload) }
            return viaEapi.getOrElse {
                // 两条链路都失败：抛出信息量更大的那一个
                viaWeapi.exceptionOrNull()?.let { error -> throw error }
                weapiResult ?: throw AppError.Server("登录接口无响应，请稍后重试")
            }
        }

        // 账号信息接口（/api/w/nuser/account/get）在 weapi 下经常被风控拦掉（返回空 profile，
        // 表现就是「暂时无法获取用户信息」）。Ncrust 用 eapi 的 /eapi/w/nuser/account/get 稳定得多，
        // 这里对 ACCOUNT 端点做 weapi → eapi 兜底重试。
        if (endpoint == NeteaseEndpoint.ACCOUNT) {
            val viaWeapi = runCatching {
                requestViaWeapi(endpoint, payload, mobileHeaders = true)
            }.getOrNull()
            // weapi 拿到了有效 profile / account 节点就直接用；否则改走 eapi。
            if (viaWeapi != null && viaWeapi.hasAccountData()) return viaWeapi
            val viaEapi = runCatching { requestViaEapi(endpoint, payload) }
            return viaEapi.getOrElse {
                viaWeapi?.let { return it }
                throw AppError.Server("登录信息获取失败，请稍后重试")
            }
        }

        // 账号信息等移动端接口（/api/w/ 前缀）需要移动端 UA，否则容易被风控拦掉。
        return requestViaWeapi(endpoint, payload, mobileHeaders = endpoint.requiresMobileHeader)
    }

    /** 判断账号接口响应是否真的带回了 profile 或 account 节点（而非风控空响应）。 */
    private fun JsonElement.hasAccountData(): Boolean {
        val obj = objOrNull() ?: return false
        return obj.obj("profile") != null ||
            obj.obj("account") != null ||
            obj.obj("data")?.obj("profile") != null ||
            obj.obj("data")?.obj("account") != null
    }

    private suspend fun requestViaWeapi(
        endpoint: NeteaseEndpoint,
        payload: JsonObject,
        mobileHeaders: Boolean,
    ): JsonElement {
        val path = NeteaseCrypto.transformPath(requirePath(endpoint.directPath, endpoint.name), target = "weapi")
        val cookie = session.cookie()
        val csrf = CookieParser.parsePairs(cookie)["__csrf"].orEmpty()
        val body = if (csrf.isBlank()) payload else JsonObject(payload + ("csrf_token" to JsonPrimitive(csrf)))
        val form = NeteaseCrypto.weapi(body.toString())
        val url = "$WEB_HOST$path?csrf_token=$csrf"

        val response = apiClient.postForm(url, form, webHeaders(cookie, mobileHeaders))
        mergeCookies(response.setCookies())
        response.requireSuccess()
        val element = response.parseBody(url)
        throwIfLoginRequired(element)
        return element
    }

    /** eapi 链路：模拟移动客户端，参数仍然只走公开的签名算法。 */
    private suspend fun requestViaEapi(endpoint: NeteaseEndpoint, payload: JsonObject): JsonElement {
        val path = NeteaseCrypto.transformPath(requirePath(endpoint.directPath, endpoint.name), target = "eapi")
        val body = JsonObject(payload + ("header" to eapiHeader()))
        val form = mapOf("params" to NeteaseCrypto.eapi(path, body.toString()))
        val url = "$EAPI_HOST$path"
        val response = apiClient.postForm(url, form, eapiHeaders())
        mergeCookies(response.setCookies())
        response.requireSuccess()
        val element = response.parseBody(url)
        throwIfLoginRequired(element)
        return element
    }

    /**
     * eapi 的 header 字段。官方客户端会带上设备与版本信息，缺失时容易被判为异常环境。
     * deviceId 由会话存储生成并持久化，保证同一台设备始终一致。
     */
    private fun eapiHeader(): JsonObject {
        val now = System.currentTimeMillis()
        val cookie = CookieParser.parsePairs(session.cookie())
        return JsonObject(
            buildMap {
                put("osver", JsonPrimitive("13"))
                put("deviceId", JsonPrimitive(session.deviceId()))
                put("appver", JsonPrimitive(APP_VERSION))
                put("versioncode", JsonPrimitive(APP_VERSION_CODE))
                put("mobilename", JsonPrimitive(DEVICE_MODEL))
                put("buildver", JsonPrimitive((now / 1000).toString()))
                put("resolution", JsonPrimitive("1920x1080"))
                put("os", JsonPrimitive("android"))
                put("channel", JsonPrimitive(""))
                put("requestId", JsonPrimitive("${now}_0000"))
                cookie["MUSIC_U"]?.takeIf { it.isNotBlank() }?.let { put("MUSIC_U", JsonPrimitive(it)) }
                cookie["__csrf"]?.takeIf { it.isNotBlank() }?.let { put("__csrf", JsonPrimitive(it)) }
            },
        )
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
                "header" to eapiHeader(),
            ),
        )
        val form = mapOf("params" to NeteaseCrypto.eapi(path, payload.toString()))
        val url = "$EAPI_HOST$path"
        val response = apiClient.postForm(url, form, eapiHeaders())
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

    /**
     * 官方直连的播放记录上报（听歌足迹）。
     *
     * 走 weapi 的 `/api/feedback/weblog`，这是网易云官方客户端上报「听歌」行为的接口，
     * 上报成功后该歌曲会出现在账号的「最近播放」里 —— 也就是用户要的「听歌足迹」。
     * 参数：songId / sourceId=0（来自搜索/推荐）/ time（本次听的秒数）。
     */
    override suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean {
        if (events.isEmpty()) return true
        var allSucceeded = true
        events.forEach { event ->
            val ok = runCatching {
                val payload = JsonObject(
                    mapOf(
                        "logs" to JsonPrimitive(
                            JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "action" to JsonPrimitive("play"),
                                            "json" to JsonObject(
                                                mapOf(
                                                    "download" to JsonPrimitive(0),
                                                    "end" to JsonPrimitive("playend"),
                                                    "id" to JsonPrimitive(event.songId),
                                                    "sourceId" to JsonPrimitive("0"),
                                                    "time" to JsonPrimitive(event.durationSeconds.coerceAtLeast(1)),
                                                    "type" to JsonPrimitive("song"),
                                                    "wifi" to JsonPrimitive(1),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ).toString(),
                        ),
                    ),
                )
                requestViaWeapiForReport(payload)
                true
            }.getOrDefault(false)
            if (!ok) allSucceeded = false
        }
        return allSucceeded
    }

    /** scrobble 上报专用：走 weapi 的 /api/feedback/weblog 接口（与普通业务接口同 host）。 */
    private suspend fun requestViaWeapiForReport(payload: JsonObject) {
        val path = "/weapi/feedback/weblog"
        val cookie = session.cookie()
        val csrf = CookieParser.parsePairs(cookie)["__csrf"].orEmpty()
        val body = if (csrf.isBlank()) payload else JsonObject(payload + ("csrf_token" to JsonPrimitive(csrf)))
        val form = NeteaseCrypto.weapi(body.toString())
        val url = "$WEB_HOST$path?csrf_token=$csrf"
        val response = apiClient.postForm(url, form, webHeaders(cookie, mobile = false))
        mergeCookies(response.setCookies())
        response.requireSuccess()
    }

    /**
     * 官方直连的歌曲评论（只读）。
     *
     * 走 weapi 的 `/api/v1/resource/comments/R_SO_4_${id}`，这是官方客户端同款评论接口，
     * 支持 `limit / offset / beforeTime` 分页，按时间倒序返回。这里把「热门/最新」都映射到
     * 同一接口（官方该资源接口本身不分热门维度，热门评论另有接口，为简化只做时间序）。
     */
    override suspend fun songComments(
        songId: String,
        sort: CommentSort,
        cursor: Long,
        limit: Int,
    ): CommentPage {
        if (songId.isBlank()) return CommentPage.EMPTY
        val safeLimit = limit.coerceAtLeast(1)
        val offset = cursor.toInt().coerceAtLeast(0)
        val payload = JsonObject(
            mapOf(
                "rid" to JsonPrimitive(songId),
                "threadId" to JsonPrimitive("R_SO_4_$songId"),
                "pageNo" to JsonPrimitive((offset / safeLimit.coerceAtLeast(1)) + 1),
                "pageSize" to JsonPrimitive(safeLimit),
                "cursor" to JsonPrimitive(offset),
                "offset" to JsonPrimitive(0),
                "orderType" to JsonPrimitive(if (sort == CommentSort.HOT) 99 else 3),
                "csrf_token" to JsonPrimitive(CookieParser.parsePairs(session.cookie())["__csrf"].orEmpty()),
            ),
        )
        val element = requestCommentViaWeapi(payload)
        val obj = element.objOrNull() ?: return CommentPage.EMPTY
        val data = obj.obj("data") ?: return CommentPage.EMPTY
        val commentsArray = data.arr("comments") ?: return CommentPage.EMPTY

        val items = commentsArray.mapNotNull { el ->
            val commentObj = el.objOrNull() ?: return@mapNotNull null
            val dto = runCatching {
                MelodyJson.decodeFromJsonElement(
                    com.wliky.melody.data.netease.dto.CommentDto.serializer(),
                    commentObj,
                )
            }.getOrNull() ?: return@mapNotNull null
            dto.toDomain()
        }
        val total = data.int("totalCount") ?: data.int("total") ?: 0
        val hasMore = data.boolean("hasMore") ?: (items.size >= safeLimit)
        val nextCursor = (offset + items.size).toLong()
        return CommentPage(items = items, cursor = nextCursor, hasMore = hasMore, total = total)
    }

    /** 评论接口专用：直接走 weapi 的 /api/v1/resource/comments/R_SO_4_${id}，不经过 requirePath。 */
    private suspend fun requestCommentViaWeapi(payload: JsonObject): JsonElement {
        val cookie = session.cookie()
        val csrf = CookieParser.parsePairs(cookie)["__csrf"].orEmpty()
        val body = if (csrf.isBlank()) payload else JsonObject(payload + ("csrf_token" to JsonPrimitive(csrf)))
        val form = NeteaseCrypto.weapi(body.toString())
        val url = "$WEB_HOST/weapi/v1/resource/comments/${payload.str("threadId").orEmpty()}"
        val response = apiClient.postForm(url, form, webHeaders(cookie, mobile = false))
        mergeCookies(response.setCookies())
        response.requireSuccess()
        return response.parseBody(url)
    }

    /** 判断登录链路响应是否真的带回了可用数据（403 时 body 是一段 HTML 或空对象）。 */
    private fun JsonElement.looksLikeQrPayload(): Boolean {
        val obj = objOrNull() ?: return false
        if (obj.str("unikey") != null) return true
        if (obj.obj("data")?.str("unikey") != null) return true
        // 轮询接口成功时只返回 code/message，没有 unikey，此时也算有效响应
        val code = obj.int("code") ?: return false
        return code in QR_SUCCESS_CODES
    }

    private fun webHeaders(cookie: String, mobile: Boolean): Map<String, String> = buildMap {
        put("Referer", if (mobile) "$WEB_HOST/login" else "$WEB_HOST/")
        put("Origin", WEB_HOST)
        put("User-Agent", if (mobile) MOBILE_USER_AGENT else DESKTOP_USER_AGENT)
        put("Content-Type", "application/x-www-form-urlencoded")
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    private fun eapiHeaders(): Map<String, String> = buildMap {
        put("Referer", "$WEB_HOST/")
        put("Origin", WEB_HOST)
        put("User-Agent", EAPI_USER_AGENT)
        put("Content-Type", "application/x-www-form-urlencoded")
        val cookie = session.cookie()
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    private companion object {
        const val WEB_HOST = "https://music.163.com"
        const val EAPI_HOST = "https://interface3.music.163.com"

        /**
         * 登录链路必须是移动端标识 —— 用桌面 UA 请求二维码接口会直接 403。
         * 这是「二维码返回异常」问题的直接原因。
         */
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

        /** 普通 Web 接口沿用桌面 UA。 */
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 模拟官方 Android 客户端的 UA。 */
        const val EAPI_USER_AGENT =
            "NeteaseMusic/8.10.90.240103170852(8009090);Dalvik/2.1.0 (Linux; U; Android 13; Pixel 6 Build/TQ3A.230805.001)"

        const val APP_VERSION = "8.10.90"
        const val APP_VERSION_CODE = "8009090"
        const val DEVICE_MODEL = "Pixel 6"

        /** 登录状态码（801 待扫码 / 802 待确认 / 803 成功 / 800 过期 / 8821 风控）。 */
        val QR_SUCCESS_CODES = setOf(800, 801, 802, 803, 8821)
    }
}
