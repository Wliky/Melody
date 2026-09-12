package com.wliky.melody.core.model

/**
 * 领域模型：与平台、与网易云接口完全解耦。
 * 所有远端 ID 统一使用 String，避免不同平台间的整数溢出 / 类型差异（见开发约定）。
 */

data class Artist(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class Album(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val artists: List<Artist> = emptyList(),
    val trackCount: Int = 0,
    val publishTime: Long = 0L,
) {
    val artistText: String get() = artists.joinToString(" / ") { it.name }
}

data class Song(
    val id: String,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    /** 网易云 fee 字段：0 免费 / 1 会员 / 4 付费专辑 / 8 低码率免费。仅用于展示状态，不用于绕过限制。 */
    val fee: Int = 0,
    val available: Boolean = true,
) {
    val artistText: String get() = artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }
    val isPaidOnly: Boolean get() = fee == 1 || fee == 4
}

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val creator: String? = null,
    val subscribed: Boolean = false,
    /** 「我喜欢的音乐」等系统歌单会带 specialType */
    val specialType: Int = 0,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val songs: List<Song> = emptyList(),
)

data class RankingList(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val updateFrequency: String? = null,
)

data class UserProfile(
    val userId: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val backgroundUrl: String? = null,
    val level: Int = 0,
    val listenSongs: Long = 0L,
)

data class HomeFeed(
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val personalizedSongs: List<Song> = emptyList(),
    val newSongs: List<Song> = emptyList(),
    val rankings: List<RankingList> = emptyList(),
)

data class SearchSuggestions(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

/** 统一分页模型（见开发约定：分页统一 Page<T>）。 */
data class Page<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val total: Int = -1,
) {
    fun append(next: Page<T>): Page<T> = Page(
        items = items + next.items,
        page = next.page,
        hasMore = next.hasMore,
        total = if (next.total >= 0) next.total else total,
    )

    companion object {
        fun <T> empty(): Page<T> = Page(emptyList(), 0, false, 0)
    }
}

data class QrCodeInfo(
    val key: String,
    val content: String,
)

/** 一行歌词。translation 为翻译（若接口提供）。 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/**
 * 一条歌曲评论（v0.3.0-preview.3+）。
 *
 * 只读模型 —— 当前不提供发送评论入口（官方风控严、第三方接口大概率失败）。
 * 时间戳为服务端返回的 `time`（秒），UI 层用 `Date` 解析时再做格式化。
 */
data class Comment(
    val id: String,
    val userId: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val content: String,
    val publishTimeSec: Long = 0L,
    val likedCount: Int = 0,
    val liked: Boolean = false,
    /** 楼层回复数（api-enhanced 在普通评论接口里也返回 `total`）。 */
    val replyCount: Int = 0,
    val ipLabel: String? = null,
)

/** 评论分页结果：一次性返回列表与游标。 */
data class CommentPage(
    val items: List<Comment> = emptyList(),
    val cursor: Long = 0L,
    val hasMore: Boolean = false,
    val total: Int = 0,
) {
    companion object {
        val EMPTY = CommentPage()
    }
}

/** 评论排序方式。 */
enum class CommentSort(val apiValue: String, val label: String) {
    HOT("hot", "热门"),
    TIME("time", "最新"),
}

/** 歌词。解析与 UI 完全解耦，见 [com.wliky.melody.core.lyric.LyricParser]。 */
data class Lyric(
    val lines: List<LyricLine> = emptyList(),
    val raw: String = "",
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** 二分查找当前时间对应的歌词行下标，找不到返回 -1。 */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyric()
    }
}

/** 二维码轮询结果。800 过期 / 801 等待扫码 / 802 待确认 / 803 成功。 */
data class LoginPollResult(
    val code: Int,
    val message: String,
) {
    val isExpired: Boolean get() = code == 800
    val isWaitingScan: Boolean get() = code == 801
    val isWaitingConfirm: Boolean get() = code == 802
    val isSuccess: Boolean get() = code == 803

    /**
     * 8821 = 触发登录风控。
     *
     * 这时继续轮询没有意义（扫多少次都会被拒），必须停下来引导用户换一条路
     * —— 通常是改用 Cookie 登录。
     */
    val isRiskControlled: Boolean get() = code == 8821
}

data class SongUrl(
    val songId: String,
    /** 为 null 表示当前账号/地区不可播放（版权或付费限制），UI 需要给出明确提示。 */
    val url: String? = null,
    val quality: AudioQuality = AudioQuality.STANDARD,
    val actualQuality: String? = null,
    val bitrate: Int = 0,
    val sizeBytes: Long = 0L,
    val expiresAt: Long = 0L,
) {
    val playable: Boolean get() = !url.isNullOrBlank()
}

enum class ApiMode(val label: String, val description: String) {
    MOCK(
        "演示模式（离线）",
        "内置示例数据，不访问网络，用于体验 UI 与播放器，不需要登录。",
    ),
    DIRECT(
        "直连模式",
        "客户端直接请求网易云公开接口（weapi/eapi 签名），无需自建服务；但登录链路受官方风控影响较大。",
    ),
    API_SERVER(
        "自建 API 服务（默认）",
        "走你自己部署的兼容服务（如 api-enhanced），纯 HTTP、兼容性最好，登录也最稳。地址可在下方修改。",
    ),
}

enum class AudioQuality(val apiValue: String, val label: String) {
    STANDARD("standard", "标准 128K"),
    HIGHER("higher", "较高 192K"),
    EXHIGH("exhigh", "极高 320K"),
    LOSSLESS("lossless", "无损 FLAC"),
    HIRES("hires", "Hi-Res"),
}

enum class SyncState { PENDING, SYNCED, FAILED, SKIPPED }

/**
 * 播放事件（文档 §9）。采用本地事件队列，而不是「播放一次立刻强推」。
 * eventId 为 UUID，保证幂等。
 */
data class PlaybackEvent(
    val eventId: String,
    val songId: String,
    val songName: String = "",
    val artistName: String = "",
    val startAt: Long = 0L,
    val durationSeconds: Long = 0L,
    val positionMs: Long = 0L,
    val completed: Boolean = false,
    val syncState: SyncState = SyncState.PENDING,
    val retryCount: Int = 0,
)
