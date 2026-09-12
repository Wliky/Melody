package com.wliky.melody.data.netease.dto

import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.core.network.FlexibleIntSerializer
import com.wliky.melody.core.network.FlexibleLongSerializer
import com.wliky.melody.core.network.FlexibleStringSerializer
import kotlinx.serialization.Serializable

/**
 * 网易云接口 DTO。
 *
 * 设计要点（文档 §7 / §16）：
 *  - 所有 ID 一律 String（数字或字符串都能吃下）；
 *  - 数字字段用宽松序列化器，接口一会返回 number 一会返回 string 也不会崩；
 *  - 字段全部可空 + 默认值，接口加字段 / 少字段都不影响解析；
 *  - DTO 只出现在 data 层，通过 toDomain() 转成领域模型。
 */

@Serializable
data class ArtistDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val name: String? = null,
    val picUrl: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val albumSize: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val musicSize: Int? = null,
)

@Serializable
data class AlbumDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val name: String? = null,
    val picUrl: String? = null,
    val artists: List<ArtistDto>? = null,
    /** 搜索专辑接口返回单个 artist */
    val artist: ArtistDto? = null,
    @Serializable(with = FlexibleIntSerializer::class) val size: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val publishTime: Long? = null,
)

@Serializable
data class SongDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val name: String? = null,
    val ar: List<ArtistDto>? = null,
    val artists: List<ArtistDto>? = null,
    val al: AlbumDto? = null,
    val album: AlbumDto? = null,
    @Serializable(with = FlexibleLongSerializer::class) val dt: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class) val duration: Long? = null,
    @Serializable(with = FlexibleIntSerializer::class) val fee: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val st: Int? = null,
    /** 每日推荐接口会用 song 包一层 */
    val song: SongDto? = null,
) {
    val flat: SongDto get() = song ?: this
}

@Serializable
data class CreatorDto(
    @Serializable(with = FlexibleStringSerializer::class) val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class PlaylistDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val name: String? = null,
    val coverImgUrl: String? = null,
    /** 「推荐歌单」接口用的是 picUrl */
    val picUrl: String? = null,
    val description: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val trackCount: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val playCount: Long? = null,
    val creator: CreatorDto? = null,
    val subscribed: Boolean? = null,
    @Serializable(with = FlexibleIntSerializer::class) val specialType: Int? = null,
    /** 「推荐歌单」接口里是 playCount，排行榜接口里是 updateFrequency */
    val updateFrequency: String? = null,
    val tracks: List<SongDto>? = null,
    @Serializable(with = FlexibleStringSerializer::class) val userId: String? = null,
)

@Serializable
data class ProfileDto(
    @Serializable(with = FlexibleStringSerializer::class) val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val backgroundUrl: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val level: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val listenSongs: Long? = null,
)

@Serializable
data class AccountDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
)

@Serializable
data class SongUrlDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val url: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val br: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val size: Long? = null,
    val level: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val fee: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val time: Long? = null,
)

@Serializable
data class LyricDto(
    val lyric: String? = null,
)

@Serializable
data class LyricPayloadDto(
    val lrc: LyricDto? = null,
    val tlyric: LyricDto? = null,
)

// ---------------------------------------------------------------------------
// DTO -> Domain
// ---------------------------------------------------------------------------

fun ArtistDto.toDomain(): Artist = Artist(
    id = id.orEmpty(),
    name = name.orEmpty().ifBlank { "未知歌手" },
    avatarUrl = picUrl,
)

fun AlbumDto.toDomain(): Album = Album(
    id = id.orEmpty(),
    name = name.orEmpty(),
    coverUrl = picUrl,
    artists = (artists ?: listOfNotNull(artist)).map { it.toDomain() },
    trackCount = size ?: 0,
    publishTime = publishTime ?: 0L,
)

fun SongDto.toDomain(): Song {
    val source = flat
    val album = (source.al ?: source.album)?.toDomain()
    return Song(
        id = source.id.orEmpty(),
        name = source.name.orEmpty().ifBlank { "未知歌曲" },
        artists = (source.ar ?: source.artists).orEmpty().map { it.toDomain() },
        album = album,
        durationMs = source.dt ?: source.duration ?: 0L,
        coverUrl = album?.coverUrl,
        fee = source.fee ?: 0,
        // st < 0 表示资源不可用（下架 / 无版权）
        available = source.st == null || source.st >= 0,
    )
}

fun PlaylistDto.toDomain(): Playlist = Playlist(
    id = id.orEmpty(),
    name = name.orEmpty().ifBlank { "歌单" },
    coverUrl = coverImgUrl ?: picUrl,
    description = description,
    trackCount = trackCount ?: 0,
    playCount = playCount ?: 0L,
    creator = creator?.nickname,
    subscribed = subscribed ?: false,
    specialType = specialType ?: 0,
)

fun ProfileDto.toDomain(): UserProfile = UserProfile(
    userId = userId.orEmpty(),
    nickname = nickname.orEmpty().ifBlank { "音乐用户" },
    avatarUrl = avatarUrl,
    signature = signature,
    backgroundUrl = backgroundUrl,
    level = level ?: 0,
    listenSongs = listenSongs ?: 0L,
)
