package com.wliky.melody.artist

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌手仓库：热门歌曲（Top 50）。
 *
 * 接口说明：`/api/artist/top/song`（uid 不在路径里，body 传 `{id}`），
 * 返回 `songs[]`，元素结构与 cloudsearch 一致（`ar` 歌手数组 / `al` 专辑对象 / `dt` 时长）。
 *
 * 歌手详情（简介/粉丝数等）需要 `/api/v1/artist/{id}`，本页暂不需要：
 * 名称由调用方传入（歌曲行里就有），封面用热门歌曲的首张专辑图兜底。
 */
@Singleton
class ArtistRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 歌手热门歌曲（`/weapi/v1/artist/{id}` 返回 hotSongs 约 50 首）。 */
    suspend fun topSongs(artistId: Long): AppResult<List<Song>> =
        when (val res = client.callWeapi("/weapi/v1/artist/$artistId", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["hotSongs"]?.jsonArray?.mapNotNull { el ->
                    parseSong(el.jsonObject)
                }.orEmpty(),
            )
        }

    private fun parseSong(obj: JsonObject): Song? {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val artists = obj["ar"]?.jsonArray?.mapNotNull { a ->
            val ao = a.jsonObject
            val artistName = ao["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Artist(
                id = ao["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                name = artistName,
            )
        }
            // 老接口 `/weapi/v1/artist/{id}` 用 `artists`/`album`/`duration` 简写结构
            ?: obj["artists"]?.jsonArray?.mapNotNull { a ->
                val ao = a.jsonObject
                val artistName = ao["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                Artist(
                    id = ao["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    name = artistName,
                )
            }.orEmpty()
        val album = (obj["al"] as? JsonObject ?: obj["album"] as? JsonObject)?.let {
            Album(
                id = it["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                name = it["name"]?.jsonPrimitive?.content ?: "",
                coverUrl = it["picUrl"]?.jsonPrimitive?.content,
            )
        }
        return Song(
            id = id,
            name = name,
            artists = artists,
            album = album,
            durationMs = (obj["dt"]?.jsonPrimitive?.longOrNull ?: obj["duration"]?.jsonPrimitive?.longOrNull)
                ?: 0L,
        )
    }
}
