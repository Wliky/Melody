package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** 一次搜索的结果（单曲 + 歌单） */
data class SearchResult(
    val songs: List<Song>,
    val playlists: List<Playlist>,
)

/**
 * 搜索仓库：
 * - 热搜：`/weapi/search/hot`（`result.hots[].first`）
 * - 联想词：`/weapi/search/suggest/keyword`（`result.allMatch[].keyword`）
 * - 搜索：`/weapi/cloudsearch/get/web`，type=1 单曲 / 1000 歌单
 *
 * cloudsearch 曲目为 `ar`（歌手数组）+ `al`（专辑对象）+ `dt`（时长 ms），
 * 与 `/weapi/v6/playlist/detail` 的简写结构不同，注意区分。
 */
@Singleton
class SearchRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 热搜榜关键词（按热度降序） */
    suspend fun hotSearch(): AppResult<List<String>> =
        when (val res = client.callWeapi("/weapi/search/hot", """{"type":1111}""")) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["result"]?.jsonObject?.get("hots")?.jsonArray
                    ?.mapNotNull { el -> el.jsonObject["first"]?.jsonPrimitive?.content }
                    .orEmpty(),
            )
        }

    /** 输入联想词（无匹配时为空列表） */
    suspend fun suggest(keyword: String): AppResult<List<String>> =
        when (val res = client.callWeapi(
            "/weapi/search/suggest/keyword",
            buildJsonObject { put("s", keyword) }.toString(),
        )) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["result"]?.jsonObject?.get("allMatch")?.jsonArray
                    ?.mapNotNull { el -> el.jsonObject["keyword"]?.jsonPrimitive?.content }
                    .orEmpty(),
            )
        }

    /**
     * 搜索单曲 + 歌单。单曲失败则整体失败；歌单失败降级为空列表。
     */
    suspend fun search(keyword: String, limit: Int = 30): AppResult<SearchResult> {
        val body = buildJsonObject {
            put("s", keyword)
            put("type", 1)
            put("offset", 0)
            put("limit", limit)
            put("total", true)
        }.toString()

        val songs: List<Song> = when (val res = client.callWeapi("/weapi/cloudsearch/get/web", body)) {
            is AppResult.Failure -> return res
            is AppResult.Success -> res.data["result"]?.jsonObject?.get("songs")?.jsonArray
                ?.mapNotNull(::parseCloudSong)
                .orEmpty()
        }

        val playlists: List<Playlist> = when (
            val res = client.callWeapi(
                "/weapi/cloudsearch/get/web",
                buildJsonObject {
                    put("s", keyword)
                    put("type", 1000)
                    put("offset", 0)
                    put("limit", limit)
                    put("total", true)
                }.toString(),
            )
        ) {
            is AppResult.Failure -> emptyList() // 歌单失败不阻塞单曲结果
            is AppResult.Success -> res.data["result"]?.jsonObject?.get("playlists")?.jsonArray
                ?.mapNotNull { el ->
                    val pl = el.jsonObject
                    val id = pl["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    Playlist(
                        id = id,
                        name = pl["name"]?.jsonPrimitive?.content ?: "未知歌单",
                        coverUrl = pl["coverImgUrl"]?.jsonPrimitive?.content,
                        playCount = pl["playCount"]?.jsonPrimitive?.longOrNull ?: 0L,
                        trackCount = pl["trackCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        creatorName = pl["creator"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content,
                    )
                }
                .orEmpty()
        }

        return AppResult.success(SearchResult(songs, playlists))
    }

    /** cloudsearch 曲目解析：`ar` 歌手数组 / `al` 专辑对象 / `dt` 时长 */
    private fun parseCloudSong(el: JsonElement): Song? {
        val obj = el.jsonObject
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val artists = obj["ar"]?.jsonArray?.mapNotNull { a ->
            val artist = a.jsonObject
            artist["name"]?.jsonPrimitive?.content?.let {
                Artist(artist["id"]?.jsonPrimitive?.longOrNull ?: 0L, it)
            }
        }.orEmpty()
        val album = obj["al"]?.jsonObject?.let { al ->
            Album(
                id = al["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                name = al["name"]?.jsonPrimitive?.content ?: "",
                coverUrl = al["picUrl"]?.jsonPrimitive?.content,
            )
        }
        return Song(
            id = id,
            name = name,
            artists = artists,
            album = album,
            durationMs = obj["dt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }
}
