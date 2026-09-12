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

        return QrCodeInfo(key = unikey, content = officialUrl ?: qrContent(unikey, response))
    }

    /**
     * 二维码内容。
     *
     * 新版登录链路建议带 `chainId`：服务端返回了就用服务端的，否则用本机稳定的设备标识，
     * 保证同一台设备每次扫码的链路一致（随机值反而更容易被判为异常环境）。
     */
    private fun qrContent(unikey: String, response: JsonObject): String {
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
        val code = response.int("code") ?: -1
        val message = response.str("message") ?: response.str("msg") ?: ""
        // cookie 可能出现在 body，也可能在 Set-Cookie（由传输层写入会话存储）
        response.str("cookie")?.takeIf { it.isNotBlank() }?.let { session.updateCookie(it) }
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
        val profile = runCatching { fetchProfile() }.getOrNull()
        if (profile == null) {
            session.clear()
            throw AppError.Unauthorized("这份 Cookie 已失效，请重新从浏览器复制一份")
        }
        // fetchProfile 期间可能合并了新的 Set-Cookie，这里把它和 userId 一起落盘
        session.saveSession(session.cookie().ifBlank { normalized }, profile.userId)
        return profile
    }

    override suspend fun fetchProfile(): UserProfile? {
        val response = call(NeteaseEndpoint.ACCOUNT).objOrNull() ?: return null
        val profileJson = response.obj("profile") ?: response.obj("data").obj("profile") ?: return null
        return decode(ProfileDto.serializer(), profileJson)?.toDomain()
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

    /** 基类默认不支持上报，由 [ApiServerNeteaseDataSource] 覆盖。 */
    override suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean = false

    protected fun mergeCookies(cookies: List<Pair<String, String>>) {
        if (cookies.isEmpty()) return
        val current = parseCookie(session.cookie()).toMutableMap()
        var changed = false
        cookies.forEach { (name, value) ->
            if (name.isBlank()) return@forEach
            if (current[name] != value) {
                current[name] = value
                changed = true
            }
        }
        if (!changed) return
        val merged = current.entries.joinToString("; ") { "${it.key}=${it.value}" }
        session.updateCookie(merged)
    }

    protected fun parseCookie(raw: String): Map<String, String> =
        raw.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty() || !trimmed.contains('=')) null
                else trimmed.substringBefore('=').trim() to trimmed.substringAfter('=').trim()
            }
            .toMap()

    protected fun requirePath(path: String, endpointName: String): String {
        if (path.isBlank()) {
            throw AppError.Server("当前接口模式不支持「$endpointName」，请到设置里切换数据源或配置自建服务")
        }
        return path
    }
}

/** 扫码登录页地址；把 unikey 拼在 codekey 上即可被官方 App 识别。 */
private const val QR_LOGIN_BASE = "https://music.163.com/login"
