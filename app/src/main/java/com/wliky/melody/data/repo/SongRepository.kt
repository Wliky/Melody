package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.remote.NeteaseClient
import com.wliky.melody.settings.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌曲仓库：播放地址获取。
 */
@Singleton
class SongRepository @Inject constructor(
    private val client: NeteaseClient,
    private val settingsPreferences: SettingsPreferences,
) {

    /**
     * 获取歌曲播放地址。
     *
     * 注意 weapi 的 `ids` 字段是「字符串化的 JSON 数组」，即 `"[186016]"` 而非 `[186016]`。
     *
     * 音质来自设置页「播放 - 音质选择」（默认极高 exhigh；无损/Hi-Res 需账号权限）。
     *
     * @return 播放 URL；null 表示 VIP 专享或无版权
     */
    suspend fun getSongUrl(songId: Long): AppResult<String?> {
        val level = settingsPreferences.audioQuality.first().apiLevel
        return when (
            val res = client.callWeapi(
                "/weapi/song/enhance/player/url/v1",
                """{"ids":"[$songId]","level":"$level","encodeType":"flac","csrf_token":""}""",
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val entry = res.data["data"]?.jsonArray?.firstOrNull()?.jsonObject
                val urlEl = entry?.get("url")
                val url = when {
                    entry == null || urlEl == null || urlEl is JsonNull -> null
                    else -> urlEl.jsonPrimitive.content.ifBlank { null }
                }
                AppResult.success(url)
            }
        }
    }

    /**
     * 红心 / 取消红心（需要登录 Cookie）。
     *
     * 与 NeteaseCloudMusicApi 的 like 一致：query 携带 alg/trackId/like，
     * body 为 `{"alg":"itembased","trackId":"<id>","like":<布尔>,"time":"3"}`。
     * 注意 like 必须是布尔值（不带引号），字符串会被服务端忽略导致不生效；
     * 走 callWeapi 校验业务码，未登录（301）才能正确回滚 UI。
     *
     * @param like true 为加入红心，false 为取消
     */
    suspend fun likeSong(songId: Long, like: Boolean): AppResult<Unit> =
        when (
            val res = client.callWeapi(
                "/weapi/radio/like?alg=itembased&trackId=$songId&like=$like",
                """{"alg":"itembased","trackId":"$songId","like":$like,"time":"3"}""",
                extraHeaders = mapOf("X-Real-IP" to "116.25.146.100"),
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(Unit)
        }

    /**
     * 红心歌曲 id 列表（NeteaseCloudMusicApi 的 likelist）：
     * `/weapi/song/like/get`，body `{uid}`，返回 `ids` 数组。
     * 启动 / 登录后拉一次填充播放页红心状态；未登录调用会失败，调用方自行忽略。
     */
    suspend fun getLikeList(userId: Long): AppResult<Set<Long>> =
        when (val res = client.callWeapi("/weapi/song/like/get", """{"uid":$userId}""")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val ids = res.data["ids"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.longOrNull }
                    ?.toSet()
                    .orEmpty()
                AppResult.success(ids)
            }
        }

    /**
     * 收藏歌曲到指定歌单（需要登录 Cookie，支持批量）。
     *
     * @param playlistId 目标歌单 id
     * @param songIds    要加入的歌曲 id 列表
     */
    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>): AppResult<Unit> =
        manipulateTracks(playlistId, songIds, op = "add")

    /**
     * 从歌单移除歌曲（需要登录 Cookie，支持批量；仅自己创建的歌单可删）。
     */
    suspend fun removeFromPlaylist(playlistId: Long, songIds: List<Long>): AppResult<Unit> =
        manipulateTracks(playlistId, songIds, op = "del")

    /**
     * 歌单曲目增删（NeteaseCloudMusicApi 的 playlist_tracks）：
     * `/weapi/playlist/manipulate/tracks`，body 携带 op / pid / trackIds。
     * 注意 trackIds 是「字符串形式的 JSON 数组」（如 `"[1,2]"`），不是原生数组。
     */
    private suspend fun manipulateTracks(
        playlistId: Long,
        songIds: List<Long>,
        op: String,
    ): AppResult<Unit> =
        when (
            val res = client.callWeapi(
                "/weapi/playlist/manipulate/tracks",
                """{"op":"$op","pid":$playlistId,"trackIds":"[${songIds.joinToString(",")}]","imme":"true","csrf_token":"${client.csrfToken()}"}""",
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(Unit)
        }
}
