package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.model.Toplist
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
 * 发现页仓库：每日推荐 + 排行榜 + 新歌首发。
 *
 * - 每日推荐：`/weapi/v3/discovery/recommend/songs`（body 空），返回 `data.dailySongs[]`（约 30 首，
 *   `ar/al/dt` 简写结构）；需登录，未登录时接口报错。
 * - 排行榜：`/weapi/toplist`（body 空），返回 `list[]`；榜单 id 可直接当歌单 id 走现有歌单详情。
 * - 新歌首发：`/api/v1/discovery/new/songs`（body `{areaId, total, limit, offset}`），
 *   返回顶层 `data[]`；areaId 0=全部 / 7=华语 / 96=欧美 / 8=日语 / 16=韩语。
 */
@Singleton
class DiscoverRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /** 每日推荐歌曲（登录后每日 30 首）。 */
    suspend fun dailySongs(): AppResult<List<Song>> =
        when (val res = client.callWeapi("/weapi/v3/discovery/recommend/songs", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["data"]?.jsonObject
                    ?.get("dailySongs")?.jsonArray
                    ?.mapNotNull { parseSongJson(it.jsonObject) }
                    .orEmpty(),
            )
        }

    /** 新歌首发（新歌速递，默认全部区域 30 首）。 */
    suspend fun newSongs(areaId: Int = 0, limit: Int = 30): AppResult<List<Song>> =
        when (
            val res = client.callWeapi(
                "/api/v1/discovery/new/songs",
                buildJsonObject {
                    put("areaId", areaId)
                    put("total", true)
                    put("limit", limit)
                    put("offset", 0)
                }.toString(),
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["data"]?.jsonArray
                    ?.mapNotNull { parseSongJson(it.jsonObject) }
                    .orEmpty(),
            )
        }

    /** 排行榜列表（飙升/新歌/原创/热歌 + 各分类榜）。 */
    suspend fun toplists(): AppResult<List<Toplist>> =
        when (val res = client.callWeapi("/weapi/toplist", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(
                res.data["list"]?.jsonArray
                    ?.mapNotNull { el ->
                        val obj = el.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        Toplist(
                            id = id,
                            name = name,
                            coverUrl = obj["coverImgUrl"]?.jsonPrimitive?.content,
                            updateFrequency = obj["updateFrequency"]?.jsonPrimitive?.content,
                        )
                    }
                    .orEmpty(),
            )
        }
}
