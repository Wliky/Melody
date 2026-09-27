package com.wliky.melody.data.model

import kotlinx.serialization.Serializable

/**
 * 领域模型：与网易云接口字段解耦，UI 只认这些类型。
 * 阶段 2 只建登录与发现所需的最小集，后续阶段按需扩充。
 * @Serializable 供页面快照缓存（RepoCache）序列化用。
 */
@Serializable
data class User(
    val id: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Int = 0,
)

@Serializable
data class Artist(
    val id: Long,
    val name: String,
)

@Serializable
data class Album(
    val id: Long,
    val name: String,
    val coverUrl: String?,
)

@Serializable
data class Song(
    val id: Long,
    val name: String,
    val artists: List<Artist>,
    val album: Album?,
    val durationMs: Long,
) {
    /** 歌曲行的副标题：歌手 - 专辑 */
    val subtitle: String
        get() = buildString {
            append(artists.joinToString(" / ") { it.name })
            if (album != null && album.name.isNotBlank()) append(" - ${album.name}")
        }

    val coverUrl: String? get() = album?.coverUrl
}

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val creatorName: String? = null,
)

/** 排行榜条目（榜单 id 可直接当歌单 id 打开现有歌单详情） */
@Serializable
data class Toplist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val updateFrequency: String?,
)

/** 电台（播客） */
@Serializable
data class Podcast(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val rcmdtext: String?,
)/** 一条评论 */
data class Comment(
    val id: Long,
    val userNickname: String,
    val userAvatarUrl: String?,
    val content: String,
    val timeMs: Long,
    val likedCount: Int,
)

/** 听歌排行条目（歌曲 + 时间段内播放次数） */
data class PlayRecordEntry(
    val song: Song,
    val playCount: Int,
)

/** 用户详情（个人主页统计） */
data class UserDetail(
    val level: Int,
    val listenSongs: Int,
    val createDays: Int,
    val followCount: Int,
    val followerCount: Int,
    val playlistCount: Int,
    val subscribedPlaylistCount: Int,
)

/** 一行歌词（LRC 解析结果），[translation] 为同时间轴的翻译文本 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/** 首页 Banner（轮播图） */
@Serializable
data class Banner(
    val id: Long,
    val imageUrl: String,
    val title: String,
    /** 跳转目标类型：1 单曲 / 10 专辑 / 1000 歌单 / 3000 外链 … */
    val targetType: Int = 0,
    val url: String? = null,
)

/** 二维码登录状态机 */
sealed interface QrLoginState {
    /** 等待扫码 */
    data object WaitingScan : QrLoginState

    /** 已扫码，等待手机上确认 */
    data object WaitingConfirm : QrLoginState

    /** 已授权，登录完成 */
    data class Authorized(val user: User) : QrLoginState

    /** 二维码过期（code 800） */
    data object Expired : QrLoginState
}
