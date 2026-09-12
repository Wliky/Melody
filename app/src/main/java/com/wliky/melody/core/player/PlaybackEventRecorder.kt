package com.wliky.melody.core.player

import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.Song
import java.util.UUID

/**
 * 播放事件记录器（文档 §9）。
 *
 * 只负责「累计有效播放时长」，不做任何网络动作：
 *  - 只在真正播放时累加，暂停不计；
 *  - 单次 tick 增量超过 [MAX_TICK_DELTA_MS] 视为用户拖动进度条，不计入（避免虚报）；
 *  - 进度回退视为重新开始，只重置基准点；
 *  - 切歌 / 退出时结算上一条事件，只有当次播放达到 [MIN_COUNTED_SECONDS] 才上报。
 *
 * 纯逻辑 + 注入时钟，因此可以直接单元测试。
 */
class PlaybackEventRecorder(
    private val clock: Clock,
    private val onEvent: (PlaybackEvent) -> Unit,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {

    private var currentSong: Song? = null
    private var startAt: Long = 0L
    private var accumulatedMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var completed: Boolean = false

    /** 当前这一条的累计有效播放秒数（用于 UI 展示）。 */
    val accumulatedSeconds: Long get() = accumulatedMs / 1000

    /** 播放器切到了另一首歌（或停止）。会先结算上一首。 */
    fun onSongChanged(song: Song?, positionMs: Long, completed: Boolean = false) {
        flush()
        val next = song ?: return
        currentSong = next
        startAt = clock.now()
        accumulatedMs = 0L
        lastPositionMs = positionMs.coerceAtLeast(0L)
        this.completed = completed
    }

    /** 播放进度变化 / 每次 tick 调用。 */
    fun onPosition(positionMs: Long, playing: Boolean) {
        if (currentSong == null) return
        val position = positionMs.coerceAtLeast(0L)
        if (playing) {
            val delta = position - lastPositionMs
            if (delta in 0..MAX_TICK_DELTA_MS) {
                accumulatedMs += delta
            }
            // delta < 0（拖动回退 / 重新开始）或 delta 过大（拖动前进）都不计入
        }
        lastPositionMs = position
    }

    fun markCompleted() {
        completed = true
    }

    /** 结算当前事件。歌曲、累计时长不达标时不上报。 */
    fun flush() {
        val song = currentSong ?: return
        currentSong = null
        val seconds = accumulatedMs / 1000
        val wasCompleted = completed
        accumulatedMs = 0L
        completed = false
        if (seconds < MIN_COUNTED_SECONDS) return
        onEvent(
            PlaybackEvent(
                eventId = idFactory(),
                songId = song.id,
                songName = song.name,
                artistName = song.artistText,
                startAt = startAt,
                durationSeconds = seconds,
                positionMs = lastPositionMs,
                completed = wasCompleted,
            ),
        )
    }

    companion object {
        /** 两条 tick 之间允许的最大增量，超过即认为是拖动进度条。 */
        const val MAX_TICK_DELTA_MS = 5_000L

        /** 少于这个秒数不值得记录（快速划过不计入历史）。 */
        const val MIN_COUNTED_SECONDS = 5L
    }
}
