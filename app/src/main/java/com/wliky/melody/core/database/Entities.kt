package com.wliky.melody.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 本地播放历史（文档 §6「缓存：优先缓存元数据」，音乐文件本身不缓存）。 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val songId: String,
    val songName: String,
    val artistName: String,
    val albumName: String,
    val coverUrl: String?,
    val durationMs: Long,
    val lastPlayedAt: Long,
    val playCount: Int,
)

/**
 * 待同步的播放事件队列（文档 §9）。
 * eventId 为主键，插入用 IGNORE，天然幂等，重复事件不会重复提交。
 */
@Entity(tableName = "playback_events")
data class PlaybackEventEntity(
    @PrimaryKey val eventId: String,
    val songId: String,
    val songName: String,
    val artistName: String,
    val startAt: Long,
    val durationSeconds: Long,
    val positionMs: Long,
    val completed: Boolean,
    val syncState: String,
    val retryCount: Int,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val keyword: String,
    val searchedAt: Long,
)
