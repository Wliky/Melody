package com.wliky.melody.data.netease

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.core.model.Comment
import com.wliky.melody.core.model.CommentPage
import com.wliky.melody.core.model.CommentSort
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.lyric.LyricParser
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
import com.wliky.melody.core.player.DemoAudioFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * 演示模式（离线）。内置示例曲库，不访问网络、不需要登录。
 *
 * 用途：
 *  - 新用户/评审可以零配置体验完整 UI 与播放器链路；
 *  - 单元测试与 CI 里的稳定替身；
 *  - 接口全部失效时，App 依然是「可用的」。
 *
 * 注意：曲库全部为自造示例内容，音频由 [DemoAudioFactory] 实时合成，不包含任何第三方版权素材。
 */
@Singleton
class MockNeteaseDataSource @Inject constructor(
    private val demoAudioFactory: DemoAudioFactory,
) : NeteaseDataSource {

    override val mode: ApiMode = ApiMode.MOCK

    override val supportsPlaybackReport: Boolean = false

    // ------------------------------------------------------------------ 登录

    override suspend fun requestQrCode(): QrCodeInfo = throw AppError.Server("演示模式不需要登录，直接使用即可")

    override suspend fun pollQrLogin(key: String): LoginPollResult = LoginPollResult(803, "演示模式已就绪")

    override suspend fun loginWithCookie(rawCookie: String): UserProfile? =
        throw AppError.Server("演示模式不需要登录，切到「直连模式」或自建服务后再登录")

    override suspend fun sendCaptcha(phone: String): Boolean =
        throw AppError.Server("演示模式不需要登录，切到「直连模式」或自建服务后再登录")

    override suspend fun loginWithPhone(phone: String, captcha: String): UserProfile? =
        throw AppError.Server("演示模式不需要登录，切到「直连模式」或自建服务后再登录")

    override suspend fun fetchProfile(): UserProfile = UserProfile(
        userId = DEMO_USER_ID,
        nickname = "Melody 演示用户",
        signature = "当前处于演示模式，数据全部为内置示例",
        level = 8,
        listenSongs = 1240,
    )

    override suspend fun logout() = Unit

    // ------------------------------------------------------------------ 首页

    override suspend fun homeFeed(): HomeFeed = HomeFeed(
        recommendedPlaylists = playlists,
        personalizedSongs = songs.take(6),
        newSongs = songs.shuffled(kotlin.random.Random(7)),
        rankings = rankings,
    )

    override suspend fun recommendedPlaylists(limit: Int): List<Playlist> = playlists.take(limit)

    override suspend fun newSongs(limit: Int): List<Song> = songs.take(limit)

    override suspend fun dailySongs(): List<Song> = songs.take(6)

    override suspend fun rankings(): List<RankingList> = rankings

    // ------------------------------------------------------------ 播放 / 详情

    override suspend fun songDetail(ids: List<String>): List<Song> = songs.filter { it.id in ids }

    override suspend fun songUrl(songId: String, quality: AudioQuality): SongUrl {
        val uri = demoAudioFactory.toneUri()
        return SongUrl(
            songId = songId,
            url = uri,
            quality = quality,
            actualQuality = "demo",
            bitrate = 256_000,
            sizeBytes = 768_000L,
        )
    }

    override suspend fun lyric(songId: String): Lyric = LyricParser.parse(DEMO_LYRICS)

    override suspend fun playlistDetail(playlistId: String): PlaylistDetail {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: throw AppError.NotFound("示例歌单不存在")
        val parity = if (playlistId.hashCode() % 2 == 0) 0 else 1
        val picked = songs.filterIndexed { index, _ -> index % 2 == parity }
        return PlaylistDetail(playlist = playlist, songs = picked.ifEmpty { songs })
    }

    // ------------------------------------------------------------------ 搜索

    override suspend fun searchSongs(keyword: String, page: Int, pageSize: Int): Page<Song> =
        pageOf(songs.filter { it.matches(keyword) }, page, pageSize)

    override suspend fun searchArtists(keyword: String, page: Int, pageSize: Int): Page<Artist> =
        pageOf(artists.filter { it.name.contains(keyword, ignoreCase = true) }, page, pageSize)

    override suspend fun searchAlbums(keyword: String, page: Int, pageSize: Int): Page<Album> =
        pageOf(albums.filter { it.name.contains(keyword, ignoreCase = true) }, page, pageSize)

    override suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): Page<Playlist> =
        pageOf(playlists.filter { it.name.contains(keyword, ignoreCase = true) }, page, pageSize)

    override suspend fun suggestions(keyword: String): SearchSuggestions {
        if (keyword.isBlank()) return SearchSuggestions()
        delay(120) // 模拟网络往返，方便观察加载态
        return SearchSuggestions(
            songs = songs.filter { it.matches(keyword) }.take(3),
            artists = artists.filter { it.name.contains(keyword, ignoreCase = true) }.take(2),
            albums = albums.filter { it.name.contains(keyword, ignoreCase = true) }.take(2),
            playlists = playlists.filter { it.name.contains(keyword, ignoreCase = true) }.take(2),
        )
    }

    // ------------------------------------------------------------------ 我的

    override suspend fun userPlaylists(userId: String): List<Playlist> = playlists

    override suspend fun likeSongs(userId: String): List<Song> = songs.filterIndexed { index, _ -> index % 3 == 0 }

    override suspend fun remotePlayRecords(userId: String): List<Song> = songs.take(8)

    override suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean = false

    // ---- 评论 ----

    override suspend fun songComments(
        songId: String,
        sort: CommentSort,
        cursor: Long,
        limit: Int,
    ): CommentPage {
        if (songId.isBlank()) return CommentPage.EMPTY
        val startIndex = cursor.toInt().coerceAtLeast(0)
        val pool = mockComments(songId)
        val items = pool.drop(startIndex).take(limit.coerceAtLeast(1))
        val hasMore = startIndex + items.size < pool.size
        val nextCursor = (startIndex + items.size).toLong()
        return CommentPage(items = items, cursor = nextCursor, hasMore = hasMore, total = pool.size)
    }

    private fun mockComments(songId: String): List<Comment> = listOf(
        Comment(
            id = "$songId-c1",
            userId = "m-artist-1",
            nickname = "晨雾里的远行",
            avatarUrl = null,
            content = "这首歌的氛围感太好了，深夜一个人戴耳机听，窗外下着小雨的时候特别合适。",
            publishTimeSec = (System.currentTimeMillis() / 1000L) - 3_600,
            likedCount = 248,
            liked = false,
            replyCount = 12,
            ipLabel = "上海",
        ),
        Comment(
            id = "$songId-c2",
            userId = "m-artist-2",
            nickname = "复古调音台",
            avatarUrl = null,
            content = "混音上的吉他音色和合成器 pad 比例把握得很好，不糊也不空。",
            publishTimeSec = (System.currentTimeMillis() / 1000L) - 86_400,
            likedCount = 167,
            liked = false,
            replyCount = 8,
            ipLabel = "北京",
        ),
        Comment(
            id = "$songId-c3",
            userId = "m-artist-3",
            nickname = "失眠急救站",
            avatarUrl = null,
            content = "有没有人和我一样，每次听到副歌就开始想起以前的一些人？",
            publishTimeSec = (System.currentTimeMillis() / 1000L) - 172_800,
            likedCount = 73,
            liked = false,
            replyCount = 4,
            ipLabel = "广州",
        ),
        Comment(
            id = "$songId-c4",
            userId = "m-artist-4",
            nickname = "耳机党",
            avatarUrl = null,
            content = "用 Wh-1000XM4 听效果拔群，背景的雨声采样细节非常清楚。",
            publishTimeSec = (System.currentTimeMillis() / 1000L) - 345_600,
            likedCount = 35,
            liked = false,
            replyCount = 1,
            ipLabel = null,
        ),
    )

    // -------------------------------------------------------------- 内置数据

    private fun Song.matches(keyword: String): Boolean =
        name.contains(keyword, ignoreCase = true) ||
            artistText.contains(keyword, ignoreCase = true) ||
            album?.name?.contains(keyword, ignoreCase = true) == true

    private fun <T> pageOf(all: List<T>, page: Int, pageSize: Int): Page<T> {
        val from = (page * pageSize).coerceAtLeast(0)
        if (from >= all.size) return Page(items = emptyList(), page = page, hasMore = false, total = all.size)
        val items = all.subList(from, minOf(from + pageSize, all.size))
        return Page(items = items, page = page, hasMore = from + pageSize < all.size, total = all.size)
    }

    private val artists = listOf(
        Artist("m-artist-1", "林澈"),
        Artist("m-artist-2", "Nova Sound"),
        Artist("m-artist-3", "苏晚"),
        Artist("m-artist-4", "Kite & Co."),
    )

    private val albums = listOf(
        Album("m-album-1", "晨雾", artists = listOf(artists[0]), trackCount = 3),
        Album("m-album-2", "Neon Harbor", artists = listOf(artists[1]), trackCount = 3),
        Album("m-album-3", "晚风信箱", artists = listOf(artists[2]), trackCount = 3),
        Album("m-album-4", "Paper Planes", artists = listOf(artists[3]), trackCount = 3),
    )

    private val songs: List<Song> = listOf(
        Triple("晨雾里", artists[0], albums[0]) to 3 * 60_000L + 42_000L,
        Triple("缓慢的河", artists[0], albums[0]) to 4 * 60_000L + 8_000L,
        Triple("灯塔之外", artists[0], albums[0]) to 3 * 60_000L + 21_000L,
        Triple("Neon Harbor", artists[1], albums[1]) to 3 * 60_000L + 55_000L,
        Triple("夜班列车", artists[1], albums[1]) to 4 * 60_000L + 30_000L,
        Triple("Static Bloom", artists[1], albums[1]) to 2 * 60_000L + 58_000L,
        Triple("晚风信箱", artists[2], albums[2]) to 3 * 60_000L + 12_000L,
        Triple("写给八月", artists[2], albums[2]) to 4 * 60_000L + 2_000L,
        Triple("旧唱片", artists[2], albums[2]) to 3 * 60_000L + 33_000L,
        Triple("Paper Planes", artists[3], albums[3]) to 3 * 60_000L + 6_000L,
        Triple("Slow Sunday", artists[3], albums[3]) to 4 * 60_000L + 18_000L,
        Triple("Roof Light", artists[3], albums[3]) to 3 * 60_000L + 45_000L,
    ).mapIndexed { index, (meta, duration) ->
        val (title, artist, album) = meta
        Song(
            id = "m-song-${index + 1}",
            name = title,
            artists = listOf(artist),
            album = album,
            durationMs = duration,
            coverUrl = null,
            fee = 0,
            available = true,
        )
    }

    private val playlists: List<Playlist> = listOf(
        Triple("深夜电台", "适合一个人戴耳机的时候", 0) to listOf(0, 4, 8, 2, 7),
        Triple("通勤路上", "地铁与耳机是绝配", 1) to listOf(3, 5, 9, 1, 11),
        Triple("专注此刻", "无人声器乐优先", 2) to listOf(1, 6, 10, 3),
        Triple("城市漫游", "适合傍晚散步", 3) to listOf(4, 9, 0, 11, 5, 8),
        Triple("柔和开场", "让一天温柔地开始", 4) to listOf(0, 6, 10, 2),
        Triple("周末慢摇", "不着急，慢慢来", 5) to listOf(5, 8, 11, 1, 3, 7, 9),
    ).mapIndexed { index, (meta, songIndexes) ->
        val (name, description, _) = meta
        Playlist(
            id = "m-playlist-${index + 1}",
            name = name,
            coverUrl = null,
            description = description,
            trackCount = songIndexes.size,
            playCount = (index + 1) * 12_345L,
            creator = "Melody 编辑部",
            subscribed = index % 2 == 0,
        )
    }

    private val rankings: List<RankingList> = listOf(
        RankingList("m-playlist-1", "Melody 飙升榜", updateFrequency = "每天更新"),
        RankingList("m-playlist-2", "Melody 新歌榜", updateFrequency = "每天更新"),
        RankingList("m-playlist-3", "Melody 治愈榜", updateFrequency = "每周更新"),
    )

    private companion object {
        const val DEMO_USER_ID = "demo-user"

        val DEMO_LYRICS = """
            [ti:演示歌词]
            [ar:Melody]
            [al:演示专辑]
            [offset:0]
            [00:00.00]Melody 演示歌词
            [00:03.00]这一段文字来自内置示例
            [00:06.50]用来验证歌词滚动与高亮
            [00:10.00]切歌、拖动进度条都会同步
            [00:13.50]接入真实接口后即显示正式歌词
            [00:17.00]歌词解析器支持同一行多时间标签
            [00:20.50]也支持翻译行的自动合并
            [00:24.00]感谢使用 Melody
        """.trimIndent()
    }
}
