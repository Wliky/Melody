package com.wliky.melody.data.repo

import com.wliky.melody.data.model.Album
import com.wliky.melody.data.model.Artist
import com.wliky.melody.data.model.Song
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 网易歌曲 JSON 的通用解析（两个仓库复用）：
 * - cloudsearch / 每日推荐 / 云盘 simpleSong：`ar` / `al` / `dt` 简写
 * - `/weapi/v1/artist/{id}` hotSongs：`artists` / `album` / `duration` 全名
 * 两种形状都兼容，字段缺失返回 null（调用方 mapNotNull 静默丢弃）。
 */
internal fun parseSongJson(obj: JsonObject): Song? {
    val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
    val name = obj["name"]?.jsonPrimitive?.content ?: return null

    fun artistArray(key: String) = obj[key]?.jsonArray?.mapNotNull { a ->
        val ao = a.jsonObject
        val artistName = ao["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        Artist(
            id = ao["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = artistName,
        )
    }.orEmpty()

    val artists = artistArray("ar").ifEmpty { artistArray("artists") }
    val album = (obj["al"] as? JsonObject ?: obj["album"] as? JsonObject)?.let {
        Album(
            id = it["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            // 后端可能把空名下发为字面 "null"，过滤之
            name = it["name"]?.jsonPrimitive?.content
                ?.takeIf { n -> n.isNotBlank() && n != "null" }
                ?: "",
            coverUrl = it["picUrl"]?.jsonPrimitive?.content,
        )
    }
    val duration = obj["dt"]?.jsonPrimitive?.longOrNull
        ?: obj["duration"]?.jsonPrimitive?.longOrNull
        ?: 0L
    return Song(
        id = id,
        name = name,
        artists = artists,
        album = album,
        durationMs = duration,
    )
}
