package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppError
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.Banner
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发现页仓库：Banner + 推荐歌单 + 用户歌单（s2-4 接口验证）。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 首页 Banner（web 端 pc 版）。 */
    suspend fun getBanners(): AppResult<List<Banner>> =
        when (val res = client.callWeapi("/api/v2/banner/get", """{"client":"android"}""")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val banners = res.data["banners"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val image = o["imageUrl"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    Banner(
                        id = o["targetId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        imageUrl = image,
                        title = o["title"]?.jsonPrimitive?.content ?: "",
                        targetType = o["targetType"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        url = o["url"]?.jsonPrimitive?.content,
                    )
                }.orEmpty()
                AppResult.success(banners)
            }
        }

    /** 推荐歌单（个性化推荐，登录后更准）。 */
    suspend fun getPersonalizedPlaylists(limit: Int = 10): AppResult<List<Playlist>> =
        when (val res = client.callWeapi(
            "/weapi/personalized/playlist",
            """{"limit":$limit,"offset":0,"total":true,"n":1000}""",
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val list = res.data["result"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    Playlist(
                        id = id,
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        coverUrl = o["picUrl"]?.jsonPrimitive?.content,
                        playCount = o["playCount"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L,
                        trackCount = o["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                }.orEmpty()
                AppResult.success(list)
            }
        }

    /** 用户创建/收藏的歌单（携带 MUSIC_U，验证登录态换数据）。 */
    suspend fun getUserPlaylists(userId: Long, limit: Int = 5): AppResult<List<Playlist>> =
        when (val res = client.callWeapi(
            "/weapi/user/playlist",
            """{"uid":"$userId","limit":$limit,"offset":0,"total":true,"n":1000}""",
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val list = res.data["playlist"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    Playlist(
                        id = id,
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        coverUrl = o["coverImgUrl"]?.jsonPrimitive?.content,
                        playCount = o["playCount"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L,
                        trackCount = o["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        creatorName = o["creator"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content,
                    )
                }.orEmpty()
                AppResult.success(list)
            }
        }

    /**
     * 歌单曲目列表（`/weapi/v6/playlist/detail`，取 `playlist.tracks` 简写结构）。
     *
     * @param limit 最多取多少首
     */
    suspend fun getPlaylistTracks(playlistId: Long, limit: Int = 30): AppResult<List<Song>> =
        when (val res = client.callWeapi(
            "/weapi/v6/playlist/detail",
            """{"id":$playlistId,"n":$limit,"s":8}""",
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val songs = res.data["playlist"]?.jsonObject
                    ?.get("tracks")?.jsonArray
                    ?.mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    Song(
                        id = id,
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        artists = o["ar"]?.jsonArray?.mapNotNull { artistEl ->
                            val a = artistEl.jsonObject
                            Artist(
                                id = a["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                name = a["name"]?.jsonPrimitive?.content ?: "",
                            )
                        }.orEmpty(),
                        album = o["al"]?.jsonObject?.let { al ->
                            Album(
                                id = al["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                name = al["name"]?.jsonPrimitive?.content ?: "",
                                coverUrl = al["picUrl"]?.jsonPrimitive?.content,
                            )
                        },
                        durationMs = o["dt"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L,
                    )
                }.orEmpty()
                AppResult.success(songs)
            }
        }

    /**
     * 歌单完整详情（`/weapi/v6/playlist/detail`，取 `playlist` 元信息：标签/简介/创建者/统计）。
     */
    suspend fun getPlaylistDetail(playlistId: Long): AppResult<Playlist> =
        when (val res = client.callWeapi(
            "/weapi/v6/playlist/detail",
            """{"id":$playlistId,"n":1,"s":0}""",
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val pl = res.data["playlist"]?.jsonObject
                if (pl == null) {
                    AppResult.failure(AppError.Parse("歌单详情为空"))
                } else {
                    AppResult.success(pl.toPlaylist())
                }
            }
        }

    /**
     * 收藏 / 取消收藏歌单。
     *
     * 参照 NeteaseCloudMusicApi 的 playlist_subscribe：**t 拼在 URL 路径里**
     * （`/weapi/playlist/subscribe/1` 收藏、`/2` 取消），body 只带 `id`；
     * 之前把 t 放 body 会被服务端忽略导致收藏永远不生效。
     */
    suspend fun subscribePlaylist(playlistId: Long, subscribe: Boolean): AppResult<Unit> =
        when (val res = client.callWeapi(
            "/weapi/playlist/subscribe/${if (subscribe) 1 else 2}",
            """{"id":$playlistId,"csrf_token":"${client.csrfToken()}"}""",
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(Unit)
        }
}

/** playlist JSON → [Playlist]，容忍缺失字段。 */
internal fun JsonObject.toPlaylist(): Playlist {
    val creator = this["creator"]?.jsonObject
    return Playlist(
        id = this["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        name = this["name"]?.jsonPrimitive?.content ?: "",
        coverUrl = this["coverImgUrl"]?.jsonPrimitive?.content
            ?: this["picUrl"]?.jsonPrimitive?.content,
        playCount = this["playCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        trackCount = this["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        creatorName = creator?.get("nickname")?.jsonPrimitive?.content,
        creatorAvatarUrl = creator?.get("avatarUrl")?.jsonPrimitive?.content,
        tags = this["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        commentCount = this["commentCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        description = this["description"]?.jsonPrimitive?.contentOrNull,
        subscribed = this["subscribed"]?.jsonPrimitive?.booleanOrNull ?: false,
        // 5 =「我喜欢的音乐」红心歌单（用于按红心状态实时过滤曲目）
        specialType = this["specialType"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
    )
}
