package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.PlayRecordEntry
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.model.UserDetail
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户体系仓库：
 * - 听歌记录（最近播放/听歌排行）：`/weapi/v1/play/record`，type 0=全部 / 1=本周，
 *   返回 `allData` / `weekData`，元素为歌曲对象（`ar`/`al`/`dt` 完整结构）平铺 `playCount`
 * - 用户详情：`/api/v1/user/detail/{uid}`（uid 在路径里，weapi 加密，body 为空）
 */
@Singleton
class UserRepository @Inject constructor(
    private val client: NeteaseClient,
) {

    /**
     * 听歌记录（按播放次数降序）。
     *
     * @param weekly true=最近一周（weekData），false=全部（allData）
     */
    suspend fun getPlayRecord(userId: Long, weekly: Boolean): AppResult<List<PlayRecordEntry>> =
        when (
            val res = client.callWeapi(
                "/weapi/v1/play/record",
                """{"uid":$userId,"type":${if (weekly) 1 else 0},"limit":100,"offset":0}""",
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val key = if (weekly) "weekData" else "allData"
                AppResult.success(
                    res.data[key]?.jsonArray
                        ?.mapNotNull { el ->
                            val obj = el.jsonObject
                            // 元素是 {playCount, score, song:{...}}：歌曲字段嵌在 song 里
                            val songObj = obj["song"] as? JsonObject ?: return@mapNotNull null
                            val song = parseRecordSong(songObj) ?: return@mapNotNull null
                            PlayRecordEntry(
                                song = song,
                                playCount = obj["playCount"]?.jsonPrimitive?.intOrNull ?: 0,
                            )
                        }
                        .orEmpty(),
                )
            }
        }

    /** 用户详情：等级/累计听歌/关注/粉丝（统计口径来自服务端 profile）。 */
    suspend fun getUserDetail(userId: Long): AppResult<UserDetail> =
        when (val res = client.callWeapi("/api/v1/user/detail/$userId", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val profile = res.data["profile"] as? JsonObject
                AppResult.success(
                    UserDetail(
                        level = res.data["level"]?.jsonPrimitive?.intOrNull ?: 0,
                        listenSongs = res.data["listenSongs"]?.jsonPrimitive?.intOrNull ?: 0,
                        createDays = res.data["createDays"]?.jsonPrimitive?.intOrNull ?: 0,
                        followCount = profile?.get("follows")?.jsonPrimitive?.intOrNull ?: 0,
                        followerCount = profile?.get("followeds")?.jsonPrimitive?.intOrNull ?: 0,
                        playlistCount = profile?.get("playlistCount")?.jsonPrimitive?.intOrNull ?: 0,
                        subscribedPlaylistCount = profile?.get("playlistBeSubscribedCount")
                            ?.jsonPrimitive?.intOrNull ?: 0,
                    ),
                )
            }
        }

    /** 听歌记录里的歌曲解析：与 cloudsearch 同为 `ar` 数组 / `al` 对象 / `dt` 时长结构。 */
    private fun parseRecordSong(obj: JsonObject): Song? {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val artists = obj["ar"]?.jsonArray?.mapNotNull { a ->
            a.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
                Artist(
                    id = a.jsonObject["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    name = name,
                )
            }
        }.orEmpty()
        val album = (obj["al"] as? JsonObject)?.let {
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
            durationMs = obj["dt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }
}
