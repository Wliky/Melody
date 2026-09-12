package com.wliky.melody.data.netease

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

/**
 * 数据源抽象（文档 §7）。
 *
 * Compose / ViewModel / Repository 只依赖这个接口，网易云特有的字段、路径、加密
 * 全部关在实现类里。接口变更只会影响 data 层。
 *
 * 目前有三个实现，可在设置里随时切换：
 *  - [DirectNeteaseDataSource]     直连官方接口（默认）
 *  - [ApiServerNeteaseDataSource]  指向自建 API 服务
 *  - [MockNeteaseDataSource]       离线演示数据
 */
interface NeteaseDataSource {

    val mode: ApiMode

    /** 该数据源是否支持播放记录上报（文档 §9 的可插拔能力探测）。 */
    val supportsPlaybackReport: Boolean get() = false

    // ---- 登录 ----
    suspend fun requestQrCode(): QrCodeInfo
    suspend fun pollQrLogin(key: String): LoginPollResult
    suspend fun fetchProfile(): UserProfile?
    suspend fun logout()

    // ---- 首页 ----
    suspend fun homeFeed(): HomeFeed
    suspend fun recommendedPlaylists(limit: Int = 12): List<Playlist>
    suspend fun newSongs(limit: Int = 20): List<Song>
    suspend fun dailySongs(): List<Song>
    suspend fun rankings(): List<RankingList>

    // ---- 播放 / 详情 ----
    suspend fun songDetail(ids: List<String>): List<Song>
    suspend fun songUrl(songId: String, quality: AudioQuality): SongUrl
    suspend fun lyric(songId: String): Lyric
    suspend fun playlistDetail(playlistId: String): PlaylistDetail

    // ---- 搜索 ----
    suspend fun searchSongs(keyword: String, page: Int, pageSize: Int): Page<Song>
    suspend fun searchArtists(keyword: String, page: Int, pageSize: Int): Page<Artist>
    suspend fun searchAlbums(keyword: String, page: Int, pageSize: Int): Page<Album>
    suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): Page<Playlist>
    suspend fun suggestions(keyword: String): SearchSuggestions

    // ---- 我的 ----
    suspend fun userPlaylists(userId: String): List<Playlist>
    suspend fun likeSongs(userId: String): List<Song>
    suspend fun remotePlayRecords(userId: String): List<Song>

    /** 上报播放事件。返回 false 表示当前模式不支持（由 SyncRepository 标记为 SKIPPED）。 */
    suspend fun reportPlayback(events: List<PlaybackEvent>): Boolean
}
