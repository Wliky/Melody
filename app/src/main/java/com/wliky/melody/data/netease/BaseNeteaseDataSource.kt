package com.wliky.melody.data.netease

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.toAppError
import com.wliky.melody.core.lyric.LyricParser
import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.LoginPollResult
import com.wliky.melody.core.model.Page
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.PlaylistDetail
import com.wliky.melody.core.model.QrCodeInfo
import com.wliky.melody.core.model.RankingList
import com.wliky.melody.core.model.SearchSuggestions
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.model.SongUrl
import com.wliky.melody.core.model.UserProfile
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
import com.wliky.melody.core.network.objects
import com.wliky.melody.core.network.str
import com.wliky.melody.core.network.toJsonPrimitive
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.dto.AlbumDto
import com.wliky.melody.data.netease.dto.ArtistDto
import com.wliky.melody.data.netease.dto.PlaylistDto
import com.wliky.melody.data.netease.dto.ProfileDto
import com.wliky.melody.data.netease.dto.SongDto
import com.wliky.melody.data.netease.dto.SongUrlDto
import com.wliky.melody.data.netease.dto.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 直连 / 自建服务两个真实数据源的公共实现：负责所有响应解析与领域模型映射。
 * 子类只需要实现「怎么把一次请求发出去」。
 *
 * 容错策略（文档 §6「错误恢复」）：
 *  - 单个区块失败不会拖垮整个首页，区块各自兜底；
 *  - 全部区块都失败时，抛出第一个真实错误，让 UI 明确展示原因；
 *  - 任何字段缺失都返回空值而不是崩溃。
 */
abstract class BaseNeteaseDataSource(
    protected val apiClient: ApiClient,
    protected val session: SecureSessionStore,
) : NeteaseDataSource {

    /** 子类实现具体传输方式。 */
    protected abstract suspend fun request(endpoint: NeteaseEndpoint, payload: JsonObject): JsonElement

    /** 不同模式下同一个接口的参数名不一样，在这里做规整。 */
    protected open fun adaptPayload(endpoint: NeteaseEndpoint, payload: JsonObject): JsonObject = payload

    protected suspend fun call(
        endpoint: NeteaseEndpoint,
        payload: JsonObject = JsonObject(emptyMap()),
    ): JsonElement = request(endpoint, adaptPayload(endpoint, payload))

    // ---------------------------------------------------------------- 解析工具

    protected fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonElement?): T? {
        if (element == null) return null
        return try {
            MelodyJson.decodeFromJsonElement(deserializer, element)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            null
        }
    }

    protected fun songs(element: JsonElement?): List<Song> {
        val array = element.arrayOrNull() ?: return emptyList()
        return decode(ListSerializer(SongDto.serializer()), array)
            .orEmpty()
            .map { it.toDomain() }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    protected fun playlists(element: JsonElement?): List<Playlist> {
        val array = element.arrayOrNull() ?: return emptyList()
        return decode(ListSerializer(PlaylistDto.serializer()), array)
            .orEmpty()
            .map { it.toDomain() }
            .filter { it.id.isNotBlank() }
    }

    protected fun artists(element: JsonElement?): List<Artist> {
        val array = element.arrayOrNull() ?: return emptyList()
        return decode(ListSerializer(ArtistDto.serializer()), array)
            .orEmpty()
            .map { it.toDomain() }
            .filter { it.id.isNotBlank() }
    }

    protected fun albums(element: JsonElement?): List<Album> {
        val array = element.arrayOrNull() ?: return emptyList()
        return decode(ListSerializer(AlbumDto.serializer()), array)
            .orEmpty()
            .map { it.toDomain() }
            .filter { it.id.isNotBlank() }
    }

    private suspend fun <T> section(block: suspend () -> List<T>): Pair<List<T>, AppError?> = try {
        block() to null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        emptyList<T>() to t.toAppError()
    }

    private fun emptyObject(): JsonObject = JsonObject(emptyMap())

    // ------------------------------------------------------------------ 登录

    override suspend fun requestQrCode(): QrCodeInfo {
        val response = call(NeteaseEndpoint.QR_KEY, jsonObjectOf("type" to 1.toJsonPrimitive()))
            .objOrNull()
            ?: throw AppError.Server(
                "登录二维码返回异常：服务端没有返回 JSON，请求可能被拦截。" +
                    "可稍后重试，或改用「Cookie 登录」",
            )

        // 不同线路的返回结构不一致：有的直接给 unikey，有的包一层 data。
        val unikey = response.str("unikey")?.takeIf { it.isNotBlank() }
            ?: response.obj("data")?.str("unikey")?.takeIf { it.isNotBlank() }
            ?: throw AppError.Server(describeQrFailure(response))

        val officialUrl = if (mode != ApiMode.DIRECT) {
            runCatching {
                call(
                    NeteaseEndpoint.QR_CREATE,
                    jsonObjectOf(
                        "key" to JsonPrimitive(unikey),
                        "qrimg" to JsonPrimitive(false),
                    ),
                ).objOrNull()?.obj("data")?.str("qrurl")
            }.getOrNull()
        } else {
            null
        }

        return QrCodeInfo(
            key = unikey,
            content = officialUrl ?: qrContent(unikey, response),
        )
    }

    /**
     * 二维码内容。
     *
     * - 直连模式自行拼接：新版登录链路建议带 `chainId`，服务端返回了就用服务端的，
     *   否则用本机稳定的设备标识，保证同一台设备每次扫码的链路一致。
     * - 自建服务模式（`/login/qr/create` 拿不到 qrurl 时的兜底）用最朴素的形式：
     *   api-enhanced 只在 `platform=web` 时才拼 chainId，多加反而可能不被识别。
     */
    private fun qrContent(unikey: String, response: JsonObject): String {
        if (mode != ApiMode.DIRECT) return "$QR_LOGIN_BASE?codekey=$unikey"
        val chainId = response.str("chainId")?.takeIf { it.isNotBlank() }
            ?: session.deviceId()
        return "$QR_LOGIN_BASE?codekey=$unikey&chainId=$chainId"
    }

    /** 把服务端返回的错误码翻译成用户能看懂、并且知道下一步该做什么的话。 */
    private fun describeQrFailure(response: JsonObject): String {
        val code = response.int("code")
        val message = response.str("message") ?: response.str("msg")
        return when {
            code == 403 ->
                "登录接口拒绝了本次请求（403），通常是账号被临时风控。" +
                    "请稍等几分钟再试，或直接改用「Cookie 登录」"

            code == 8821 ->
                "触发了登录风控（8821），请稍后再试，或改用「Cookie 登录」"

            !message.isNullOrBlank() -> "登录二维码返回异常：$message（可改用「Cookie 登录」）"

            else -> "登录二维码返回异常，接口可能已变更（可改用「Cookie 登录」）"
        }
    }

    override suspend fun pollQrLogin(key: String): LoginPollResult {
        val response = call(
            NeteaseEndpoint.QR_CHECK,
            jsonObjectOf("key" to JsonPrimitive(key), "type" to 1.toJsonPrimitive()),
        ).objOrNull() ?: return LoginPollResult(-1, "无法解析登录状态")
        val code = response.int("code") ?: response.obj("data").int("code") ?: -1
        val message = response.str("message") ?: response.str("msg") ?: ""

        // 服务端把**原始 Set-Cookie 数组** join(';') 回传（含 Path / Expires / HttpOnly 等
        // 响应头属性），先清洗成真正的 Cookie 再落盘。匿名 Cookie 会被 SecureSessionStore 忽略，
        // 所以「轮询了几下」绝不会把 App 变成"已登录"。
        val rawCookie = response.str("cookie") ?: response.obj("data").str("cookie")
        if (!rawCookie.isNullOrBlank()) {
            CookieParser.sanitize(rawCookie)?.let { session.updateCookie(it) }
        }

        // 服务端说登录成功、我们却没拿到 MUSIC_U —— 这在自建服务回传结构变化时会出现。
        // 不能就这么返回 803 让上层"假装登录成功"，必须明确报错并指向 Cookie 登录。
        if (code == QR_SUCCESS_CODE && !session.hasAuthToken()) {
            return LoginPollResult(-1, "登录成功但未取得登录凭据，请改用「Cookie 登录」")
        }

        return LoginPollResult(
            code = code,
            message = if (code == 8821) "登录环境异常，请稍后重试或改用 Cookie 登录" else message,
        )
    }

    override suspend fun loginWithCookie(rawCookie: String): UserProfile? {
        val normalized = CookieParser.normalize(rawCookie)
            ?: throw AppError.Parse("Cookie 格式不正确，请粘贴包含 MUSIC_U 的完整内容")
        if (!CookieParser.looksUsable(normalized)) {
            throw AppError.Parse("没有识别到有效的 MUSIC_U，请确认复制的是登录后的 Cookie")
        }
        session.saveSession(normalized, "")

        // 登录成功与否，**只看 Cookie 里有没有有效的 MUSIC_U**。
        // MUSIC_U 落盘的那一刻就已完成登录 —— 用户信息（昵称 / 头像 / uid）是展示信息，
        // 由上层（ProfileViewModel 进页面时）异步补拉，**绝不在登录这一步同步等待**。
        //
        // 之前的实现把「同步 fetchProfile」塞在登录链路里，导致登录要等一次最长 20s 的
        // 账号接口请求（被风控时必超时），表现就是「Cookie 登录一直转圈」。
        // 现在登录瞬间完成；profile 拉不到也不清会话、不影响已登录状态。
        return null
    }

    override suspend fun sendCaptcha(phone: String): Boolean {
        val number = normalizePhone(phone)
        val response = call(
            NeteaseEndpoint.CAPTCHA_SENT,
            jsonObjectOf(
                "phone" to JsonPrimitive(number),
                "ctcode" to JsonPrimitive(COUNTRY_CODE),
            ),
        ).objOrNull() ?: throw AppError.Server("验证码发送失败，服务端没有返回内容")

        val code = response.int("code") ?: -1
        if (code != SUCCESS_CODE) {
            throw AppError.Server(describeLoginFailure(code, messageOf(response), "验证码发送"))
        }
        return true
    }

    override suspend fun loginWithPhone(phone: String, captcha: String): UserProfile? {
        val number = normalizePhone(phone)
        val code = captcha.filter { it.isDigit() }
        if (code.length < 4) throw AppError.Parse("请填写收到的短信验证码")

        val response = call(
            NeteaseEndpoint.LOGIN_CELLPHONE,
            jsonObjectOf(
                "phone" to JsonPrimitive(number),
                "countrycode" to JsonPrimitive(COUNTRY_CODE),
                "captcha" to JsonPrimitive(code),
            ),
        ).objOrNull() ?: throw AppError.Server("登录失败，服务端没有返回内容")

        val resultCode = response.int("code") ?: -1
        if (resultCode != SUCCESS_CODE) {
            throw AppError.Unauthorized(describeLoginFailure(resultCode, messageOf(response), "登录"))
        }

        // 自建服务把登录 Cookie 放在 body 里回传（同样是 Set-Cookie 原始串，需要清洗）；
        // 直连模式则由 Set-Cookie 头经 mergeCookies 写入。
        response.str("cookie")?.takeIf { it.isNotBlank() }
            ?.let { CookieParser.sanitize(it) }
            ?.let { session.updateCookie(it) }

        // 登录成功的判据是「会话里有没有 MUSIC_U」，而不是「能否拉到 profile」。
        // 手机号登录接口成功（200）后，直连模式 MUSIC_U 已经经 mergeCookies 落盘；
        // 若此时还没拿到 MUSIC_U，说明登录凭据确实没拿到，这才算失败。
        if (!session.hasAuthToken()) {
            session.clear()
            throw AppError.Unauthorized("登录成功但没有取得有效凭据，请重试或改用 Cookie 登录")
        }

        // profile（昵称 / 头像 / uid）是展示信息：拉不到（风控 / 网络抖动）不清会话、
        // 不判登录失败，静默返回 null，由上层页面异步补拉。
        val profile = try {
            fetchProfile()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            null
        }
        if (profile != null) session.updateUserId(profile.userId)
        return profile
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.length != 11 || !digits.startsWith("1")) {
            throw AppError.Parse("请输入 11 位中国大陆手机号")
        }
        return digits
    }

    private fun messageOf(response: JsonObject): String? =
        response.str("message") ?: response.str("msg") ?: response.str("data").orEmpty().takeIf { it.isNotBlank() }

    /** 把登录相关的错误码翻译成「用户知道下一步做什么」的话。 */
    private fun describeLoginFailure(code: Int, message: String?, action: String): String = when (code) {
        501 -> "手机号格式不正确"
        502 -> "该账号密码错误（本客户端只支持验证码登录）"
        503 -> "验证码错误或已过期，请重新获取"
        504 -> "该手机号还没有注册网易云音乐"
        8821 -> "触发了登录风控，请稍后再试，或改用 Cookie 登录"
        -460 -> "当前网络环境被判定为异常（-460），请关闭代理 / VPN 后重试"
        else -> if (!message.isNullOrBlank()) "$action 失败：$message" else "$action 失败（错误码 $code）"
    }

    override suspend fun fetchProfile(): UserProfile? {
        val response = call(NeteaseEndpoint.ACCOUNT).objOrNull() ?: return null
        // 优先取 profile 节点；拿不到时兜底 account 节点（eapi / weapi 两种链路返回结构不同，
        // account 里同样带 id / nickname / avatarUrl，Ncrust 即按 account 兜底解析）。
        val profileJson = response.obj("profile")
            ?: response.obj("data").obj("profile")
            ?: response.obj("account")
            ?: response.obj("data").obj("account")
            ?: return null
        val dto = decode(ProfileDto.serializer(), profileJson) ?: return null
        // account 节点的 id 字段名是 "id" 而非 "userId"，userId 缺省时用 id 兜底。
        val resolvedUserId = dto.userId.orEmpty()
            .ifBlank { profileJson.str("id").orEmpty() }
        val domain = dto.toDomain()
        return if (domain.userId.isBlank() && resolvedUserId.isNotBlank()) {
            domain.copy(userId = resolvedUserId)
        } else {
            domain
        }
    }

    override suspend fun logout() {
        runCatching { call(NeteaseEndpoint.LOGOUT, jsonObjectOf()) }
    }

    // ------------------------------------------------------------------ 首页

    override suspend fun homeFeed(): HomeFeed {
        val (recommended, recommendedError) = section { recommendedPlaylists() }
        val (fresh, newSongsError) = section { newSongs() }
        val (charts, rankingsError) = section { rankings() }
        val (daily, _) = section { if (session.loggedIn.value) dailySongs() else emptyList() }

        val allEmpty = recommended.isEmpty() && fresh.isEmpty() && charts.isEmpty()
        val firstError = listOfNotNull(recommendedError, newSongsError, rankingsError).firstOrNull()
        if (allEmpty && firstError != null) throw firstError

        return HomeFeed(
            recommendedPlaylists = recommended,
            personalizedSongs = daily,
            newSongs = fresh,
            rankings = charts,
        )
    }

    override suspend fun recommendedPlaylists(limit: Int): List<Playlist> {
        val element = call(
            NeteaseEndpoint.RECOMMEND_PLAYLIST,
            jsonObjectOf("limit" to limit.toJsonPrimitive()),
        )
        val array = when (element) {
            is JsonArray -> element
            else -> element.objOrNull()?.arr("result") ?: element.objOrNull()?.arr("data") ?: JsonArray(emptyList())
        }
        return playlists(array)
    }

    override suspend fun newSongs(limit: Int): List<Song> {
        val element = call(
            NeteaseEndpoint.RECOMMEND_NEW_SONG,
            jsonObjectOf("limit" to limit.toJsonPrimitive()),
        )
        val array = when (element) {
            is JsonArray -> element
            else -> element.objOrNull()?.arr("result") ?: element.objOrNull()?.arr("data") ?: JsonArray(emptyList())
        }
        return songs(array)
    }

    override suspend fun dailySongs(): List<Song> {
        val response = call(NeteaseEndpoint.DAILY_SONGS, jsonObjectOf()).objOrNull() ?: return emptyList()
        val data = response.obj("data") ?: response
        return songs(data.arr("dailySongs"))
    }

    override suspend fun rankings(): List<RankingList> {
        val element = call(NeteaseEndpoint.TOPLIST, jsonObjectOf())
        val array = when (element) {
            is JsonArray -> element
            else -> element.objOrNull()?.arr("list") ?: element.objOrNull()?.arr("data") ?: JsonArray(emptyList())
        }
        return array.objects().mapNotNull { item ->
            val dto = decode(PlaylistDto.serializer(), item) ?: return@mapNotNull null
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RankingList(
                id = id,
                name = dto.name.orEmpty().ifBlank { "榜单" },
                coverUrl = dto.coverImgUrl ?: dto.picUrl,
                updateFrequency = dto.updateFrequency,
            )
        }
    }

    // ------------------------------------------------------------ 播放 / 详情

    override suspend fun songDetail(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val response = call(
            NeteaseEndpoint.SONG_DETAIL,
            jsonObjectOf("ids" to JsonArray(ids.map { JsonPrimitive(it) })),
        )
        val array = when (response) {
            is JsonArray -> response
            else -> response.objOrNull()?.arr("songs") ?: response.objOrNull()?.arr("data") ?: JsonArray(emptyList())
        }
        return songs(array)
    }

    override suspend fun songUrl(songId: String, quality: AudioQuality): SongUrl {
        val response = call(
            NeteaseEndpoint.SONG_URL,
            jsonObjectOf(
                "ids" to JsonArray(listOf(JsonPrimitive(songId))),
                "level" to JsonPrimitive(quality.apiValue),
                "encodeType" to JsonPrimitive("flac"),
            ),
        ).objOrNull() ?: throw AppError.Parse("歌曲地址返回异常")

        val array = response.arr("data") ?: JsonArray(emptyList())
        val item = array.firstOrNull()?.objOrNull() ?: throw AppError.Unavailable()
        val dto = decode(SongUrlDto.serializer(), item) ?: throw AppError.Parse("歌曲地址字段解析失败")
        return SongUrl(
            songId = songId,
            url = dto.url?.takeIf { it.isNotBlank() },
            quality = quality,
            actualQuality = dto.level,
            bitrate = dto.br ?: 0,
            sizeBytes = dto.size ?: 0L,
            expiresAt = dto.time ?: 0L,
        )
    }

    override suspend fun lyric(songId: String): Lyric {
        val response = call(
            NeteaseEndpoint.LYRIC,
            jsonObjectOf(
                "id" to JsonPrimitive(songId),
                "lv" to (-1).toJsonPrimitive(),
                "kv" to (-1).toJsonPrimitive(),
                "tv" to (-1).toJsonPrimitive(),
            ),
        ).objOrNull() ?: return Lyric.EMPTY
        val raw = response.obj("lrc").str("lyric").orEmpty()
        val translated = response.obj("tlyric").str("lyric").orEmpty()
        return LyricParser.parse(raw, translated)
    }

    override suspend fun playlistDetail(playlistId: String): PlaylistDetail {
        val response = call(
            NeteaseEndpoint.PLAYLIST_DETAIL,
            jsonObjectOf(
                "id" to JsonPrimitive(playlistId),
                "n" to 1000.toJsonPrimitive(),
                "s" to 8.toJsonPrimitive(),
            ),
        ).objOrNull() ?: throw AppError.Parse("歌单详情返回异常")
        val playlistJson = response.obj("playlist")
            ?: response.obj("data").obj("playlist")
            ?: throw AppError.NotFound("没有找到该歌单")
        val dto = decode(PlaylistDto.serializer(), playlistJson)
            ?: throw AppError.Parse("歌单字段解析失败")
        return PlaylistDetail(
            playlist = dto.toDomain(),
            songs = songs(playlistJson.arr("tracks")),
        )
    }

    // ------------------------------------------------------------------ 搜索

    private val searchTypes = mapOf(
        SearchKind.SONG to 1,
        SearchKind.ARTIST to 100,
        SearchKind.ALBUM to 10,
        SearchKind.PLAYLIST to 1000,
    )

    private enum class SearchKind { SONG, ARTIST, ALBUM, PLAYLIST }

    private suspend fun cloudSearch(keyword: String, kind: SearchKind, page: Int, pageSize: Int): JsonObject {
        val type = searchTypes[kind] ?: 1
        val payload = jsonObjectOf(
            "s" to JsonPrimitive(keyword),
            "keywords" to JsonPrimitive(keyword),
            "type" to type.toJsonPrimitive(),
            "limit" to pageSize.toJsonPrimitive(),
            "offset" to (page * pageSize).toJsonPrimitive(),
            "total" to true.toJsonPrimitive(),
        )
        val element = call(NeteaseEndpoint.CLOUD_SEARCH, payload)
        return when (element) {
            is JsonObject -> element.obj("result") ?: element.obj("data") ?: emptyObject()
            else -> emptyObject()
        }
    }

    override suspend fun searchSongs(keyword: String, page: Int, pageSize: Int): Page<Song> {
        if (keyword.isBlank()) return Page.empty()
        val result = cloudSearch(keyword, SearchKind.SONG, page, pageSize)
        val items = songs(result.arr("songs"))
        return pageOf(items, result, page, pageSize)
    }

    override suspend fun searchArtists(keyword: String, page: Int, pageSize: Int): Page<Artist> {
        if (keyword.isBlank()) return Page.empty()
        val result = cloudSearch(keyword, SearchKind.ARTIST, page, pageSize)
        val items = artists(result.arr("artists"))
        return pageOf(items, result, page, pageSize)
    }

    override suspend fun searchAlbums(keyword: String, page: Int, pageSize: Int): Page<Album> {
        if (keyword.isBlank()) return Page.empty()
        val result = cloudSearch(keyword, SearchKind.ALBUM, page, pageSize)
        val items = albums(result.arr("albums"))
        return pageOf(items, result, page, pageSize)
    }

    override suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): Page<Playlist> {
        if (keyword.isBlank()) return Page.empty()
        val result = cloudSearch(keyword, SearchKind.PLAYLIST, page, pageSize)
        val items = playlists(result.arr("playlists"))
        return pageOf(items, result, page, pageSize)
    }

    private fun <T> pageOf(items: List<T>, result: JsonObject, page: Int, pageSize: Int): Page<T> {
        val total = result.int("songCount")
            ?: result.int("artistCount")
            ?: result.int("albumCount")
            ?: result.int("playlistCount")
            ?: -1
        val hasMore = result.boolean("hasMore") ?: (total > (page + 1) * pageSize)
        return Page(items = items, page = page, hasMore = hasMore, total = total)
    }

    override suspend fun suggestions(keyword: String): SearchSuggestions {
        if (keyword.isBlank()) return SearchSuggestions()
        val response = call(
            NeteaseEndpoint.SEARCH_SUGGEST,
            jsonObjectOf(
                "s" to JsonPrimitive(keyword),
                "keywords" to JsonPrimitive(keyword),
            ),
        ).objOrNull() ?: return SearchSuggestions()
        val result = response.obj("result") ?: response.obj("data") ?: return SearchSuggestions()
        return SearchSuggestions(
            songs = songs(result.arr("songs")),
            artists = artists(result.arr("artists")),
            albums = albums(result.arr("albums")),
            playlists = playlists(result.arr("playlists")),
        )
    }

    // ------------------------------------------------------------------ 我的

    override suspend fun userPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()
        val response = call(
            NeteaseEndpoint.USER_PLAYLIST,
            jsonObjectOf(
                "uid" to JsonPrimitive(userId),
                "limit" to 1000.toJsonPrimitive(),
                "offset" to 0.toJsonPrimitive(),
            ),
        )
        val array = when (response) {
            is JsonArray -> response
            else -> response.objOrNull()?.arr("playlist")
                ?: response.objOrNull()?.arr("data")
                ?: JsonArray(emptyList())
        }
        return playlists(array)
    }

    override suspend fun likeSongs(userId: String): List<Song> {
        val all = userPlaylists(userId)
        val liked = all.firstOrNull { it.specialType == 5 } ?: return emptyList()
        return playlistDetail(liked.id).songs
    }

    override suspend fun remotePlayRecords(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()
        val response = call(
            NeteaseEndpoint.PLAY_RECORD,
            jsonObjectOf("uid" to JsonPrimitive(userId), "type" to 0.toJsonPrimitive()),
        ).objOrNull() ?: return emptyList()
        val array = response.arr("allData") ?: response.arr("data") ?: JsonArray(emptyList())
        return songs(array)
    }

    /**
     * 最近播放（听歌足迹）：走官方 /api/record/recent/song，需要登录态。
     * 返回结构为 `data.list` 数组（每项是 song 对象），也可能是顶层数组。
     */
    override suspend fun recentSongs(): List<Song> {
        if (!session.hasAuthToken()) return emptyList()
        val response = call(NeteaseEndpoint.RECENT_SONG, jsonObjectOf()).objOrNull() ?: return emptyList()
        val array = response.arr("list")
            ?: response.obj("data").arr("list")
            ?: response.obj("data").arr("songs")
            ?: JsonArray(emptyList())
        return songs(array)
    }

    /** 基类默认不支持上报，由 [ApiServerNeteaseDataSource] 覆盖。 */
    override suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean = false

    /**
     * 基类默认走空实现：直连模式与未来新增的数据源只要不打算支持评论，继承这个就行。
     * [ApiServerNeteaseDataSource] 与 [MockNeteaseDataSource] 各自提供实现。
     */
    override suspend fun songComments(
        songId: String,
        sort: com.wliky.melody.core.model.CommentSort,
        cursor: Long,
        limit: Int,
    ): com.wliky.melody.core.model.CommentPage =
        throw AppError.Server("当前数据源不支持查看评论")

    /**
     * 把响应的 Set-Cookie 合并进会话。
     *
     * **只有合并结果里带 `MUSIC_U` 才会落盘**。服务端对匿名请求也会下发
     * `NMTID` / `_ntes_nuid` / `WNMCID` 之类的访客 Cookie，早先无条件写入会话，
     * 结果「请求过一次接口」就等于「已登录」——这正是"点登录就显示已登录"的根因。
     * 已经在登录态下时，合并结果天然带着原本的 MUSIC_U，所以正常的会话刷新不受影响。
     */
    protected fun mergeCookies(cookies: List<Pair<String, String>>) {
        if (cookies.isEmpty()) return
        val current = CookieParser.parsePairs(session.cookie())
        var changed = false
        cookies.forEach { (name, value) ->
            if (name.isBlank() || value.isBlank()) return@forEach
            if (current[name] != value) {
                current[name] = value
                changed = true
            }
        }
        if (!changed) return
        session.updateCookie(CookieParser.join(current))
    }

    protected fun requirePath(path: String, endpointName: String): String {
        if (path.isBlank()) {
            throw AppError.Server("当前接口模式不支持「$endpointName」，请到设置里切换数据源或配置自建服务")
        }
        return path
    }
}

/** 扫码登录页地址；把 unikey 拼在 codekey 上即可被官方 App 识别。 */
private const val QR_LOGIN_BASE = "https://music.163.com/login"

/** 轮询二维码状态时表示「已确认登录」的返回码。 */
private const val QR_SUCCESS_CODE = 803

/** 接口通用的成功码。 */
private const val SUCCESS_CODE = 200

/** 手机号登录默认区号：只支持中国大陆号码。 */
private const val COUNTRY_CODE = "86"
