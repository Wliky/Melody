package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云盘仓库：`/weapi/v1/cloud/get`（body `{limit, offset}`），返回 `data[]` + `count`。
 *
 * 每个元素优先取 `simpleSong`（标准 `ar/al/dt` 歌曲结构，能拿到封面）；
 * 无匹配的条目只有 `songId/songName/artist/album` 平铺字段，降级构造。
 */
@Singleton
class CloudRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    suspend fun cloudSongs(limit: Int = 300, offset: Int = 0): AppResult<List<Song>> =
        when (
            val res = client.callWeapi(
                "/weapi/v1/cloud/get",
                buildJsonObject {
                    put("limit", limit)
                    put("offset", offset)
                }.toString(),
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["data"]?.jsonArray
                    ?.mapNotNull { el ->
                        val obj = el.jsonObject
                        (obj["simpleSong"] as? kotlinx.serialization.json.JsonObject)
                            ?.let { parseSongJson(it) }
                            ?: parseFlatCloudItem(obj)
                    }
                    .orEmpty(),
            )
        }

    /** 无 simpleSong 的云盘条目：songId/songName/artist/album 平铺字段。 */
    private fun parseFlatCloudItem(obj: kotlinx.serialization.json.JsonObject): Song? {
        val id = obj["songId"]?.jsonPrimitive?.longOrNull ?: return null
        val name = obj["songName"]?.jsonPrimitive?.content ?: return null
        return Song(
            id = id,
            name = name,
            artists = obj["artist"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(Artist(id = 0L, name = it)) }
                .orEmpty(),
            album = obj["album"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { Album(id = 0L, name = it, coverUrl = null) },
            durationMs = 0L,
        )
    }
}
