package com.wliky.melody.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wliky.melody.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

/**
 * 上次播放会话持久化：重开 App 后迷你条仍显示上次歌曲与进度。
 *
 * 只存「状态快照」（队列 / 下标 / 进度 / 时长 / 播放模式），不存播放直链
 * （带 1200s 过期签名），恢复后不自动起播，用户点播放时才重新取链并从进度继续。
 */
@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 会话快照：足以还原迷你条与后续续播。 */
    data class Snapshot(
        val queue: List<Song>,
        val index: Int,
        val positionMs: Long,
        val durationMs: Long,
        val shuffle: Boolean,
        val repeatMode: RepeatMode,
    )

    val snapshot: Flow<Snapshot?> = context.playbackDataStore.data.map(::decode)

    suspend fun read(): Snapshot? = decode(context.playbackDataStore.data.first())

    suspend fun save(
        queue: List<Song>,
        index: Int,
        positionMs: Long,
        durationMs: Long,
        shuffle: Boolean,
        repeatMode: RepeatMode,
    ) {
        if (queue.isEmpty() || index !in queue.indices) return
        // 超大歌单截断，避免 DataStore 单值过大
        val trimmed = queue.take(MAX_QUEUE)
        if (index !in trimmed.indices) return
        val json = runCatching { Json.encodeToString(trimmed) }.getOrNull() ?: return
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_QUEUE] = json
            prefs[KEY_INDEX] = index
            prefs[KEY_POSITION] = positionMs.coerceAtLeast(0)
            prefs[KEY_DURATION] = durationMs.coerceAtLeast(0)
            prefs[KEY_SHUFFLE] = shuffle
            prefs[KEY_REPEAT] = repeatMode.name
        }
    }

    suspend fun clear() {
        context.playbackDataStore.edit { prefs -> prefs.clear() }
    }

    private fun decode(prefs: Preferences): Snapshot? {
        val json = prefs[KEY_QUEUE] ?: return null
        val queue = runCatching { Json.decodeFromString<List<Song>>(json) }.getOrNull() ?: return null
        val index = prefs[KEY_INDEX] ?: return null
        if (index !in queue.indices) return null
        val fallbackDuration = queue[index].durationMs
        return Snapshot(
            queue = queue,
            index = index,
            positionMs = (prefs[KEY_POSITION] ?: 0L).coerceAtLeast(0),
            durationMs = (prefs[KEY_DURATION] ?: 0L).takeIf { it > 0 } ?: fallbackDuration,
            shuffle = prefs[KEY_SHUFFLE] ?: false,
            repeatMode = runCatching {
                RepeatMode.valueOf(prefs[KEY_REPEAT] ?: RepeatMode.ALL.name)
            }.getOrDefault(RepeatMode.ALL),
        )
    }

    private companion object {
        /** 队列最多持久化的曲目数 */
        const val MAX_QUEUE = 300

        val KEY_QUEUE = stringPreferencesKey("queue_json")
        val KEY_INDEX = intPreferencesKey("index")
        val KEY_POSITION = longPreferencesKey("position_ms")
        val KEY_DURATION = longPreferencesKey("duration_ms")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle")
        val KEY_REPEAT = stringPreferencesKey("repeat_mode")
    }
}
