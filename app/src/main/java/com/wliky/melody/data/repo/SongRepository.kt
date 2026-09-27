package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.remote.NeteaseClient
import com.wliky.melody.settings.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
     * 音质来自设置页「播放 - 音质选择」（默认高品 higher；无损/Hi-Res 需账号权限）。
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
     * 接口为 weapi/radio/like（与 NeteaseCloudMusicApi 的 like 一致）：
     * query 携带 trackId/like，body 携带 alg/trackId/like/time。
     *
     * @param like true 为加入红心，false 为取消
     */
    suspend fun likeSong(songId: Long, like: Boolean): AppResult<Unit> =
        when (
            val res = client.post(
                "/weapi/radio/like?alg=itembased&trackId=$songId&like=$like",
                """{"alg":"itembased","trackId":"$songId","like":"$like","time":"3"}""",
                extraHeaders = mapOf("X-Real-IP" to "116.25.146.100"),
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(Unit)
        }

    /**
     * 收藏当前歌曲到指定歌单（需要登录 Cookie）。
     *
     * @param playlistId 目标歌单 id
     */
    suspend fun addToPlaylist(playlistId: Long, songId: Long): AppResult<Unit> =
        when (
            val res = client.callWeapi(
                "/weapi/playlist/manipulate/tracks",
                """{"op":"add","pid":$playlistId,"trackIds":"[$songId]","imme":"true","csrf_token":""}""",
            )
        ) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.success(Unit)
        }
}
