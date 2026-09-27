package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Podcast
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
 * 播客（电台）仓库。
 *
 * - 精选电台：`/weapi/djradio/recommend/v1`（body 空），返回顶层 `djRadios[]`
 * - 电台节目：`/weapi/dj/program/byradio`（body `{radioId, limit, offset, asc}`），
 *   返回顶层 `programs[]`；节目自身 id 播不了，可播的是 `mainSong`
 *   （完整歌曲结构），复用 [parseSongJson]；缺失 mainSong 的节目直接跳过
 */
@Singleton
class PodcastRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 精选电台列表。 */
    suspend fun hotRadios(limit: Int = 30, offset: Int = 0): AppResult<List<Podcast>> =
        when (val res = client.callWeapi("/weapi/djradio/recommend/v1", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["djRadios"]?.jsonArray
                    ?.mapNotNull { el -> parseRadio(el.jsonObject) }
                    .orEmpty(),
            )
        }

    /** 电台节目列表（转为可播放 Song）。rid 走 body 而非路径，一次 30 条。 */
    suspend fun radioPrograms(radioId: Long, limit: Int = 30, offset: Int = 0): AppResult<List<Song>> =
        when (
            val res = client.callWeapi(
                "/weapi/dj/program/byradio",
                buildJsonObject {
                    put("radioId", radioId)
                    put("limit", limit)
                    put("offset", offset)
                    put("asc", false)
                }.toString(),
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["programs"]?.jsonArray
                    ?.mapNotNull { el ->
                        (el.jsonObject["mainSong"] as? JsonObject)?.let { parseSongJson(it) }
                    }
                    .orEmpty(),
            )
        }

    private fun parseRadio(obj: JsonObject): Podcast? {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        return Podcast(
            id = id,
            name = name,
            coverUrl = obj["picUrl"]?.jsonPrimitive?.content ?: obj["coverUrl"]?.jsonPrimitive?.content,
            rcmdtext = obj["rcmdtext"]?.jsonPrimitive?.content,
        )
    }
}
