package com.wliky.melody.core.player

import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEventRecorderTest {

    private class FakeClock(var current: Long = 1_000_000L) : Clock {
        override fun now(): Long = current
    }

    private val clock = FakeClock()
    private val events = mutableListOf<PlaybackEvent>()
    private val recorder = PlaybackEventRecorder(
        clock = clock,
        onEvent = { events += it },
        idFactory = { "event-${events.size + 1}" },
    )

    private fun song(id: String) = Song(id = id, name = "歌曲 $id")

    /**
     * 模拟播放器逐秒上报进度，累计 [seconds] 秒有效播放。
     *
     * 注意：不能一次性跳到目标时间——[PlaybackEventRecorder.MAX_TICK_DELTA_MS] 会把
     * 单次过大的增量判定为「用户拖动进度条」而不计入，真实播放器是每 500ms 上报一次。
     */
    private fun playFor(seconds: Int, stepMs: Long = 1_000L) {
        val steps = (seconds * 1_000L / stepMs).toInt()
        repeat(steps + 1) { index -> recorder.onPosition(positionMs = index * stepMs, playing = true) }
    }

    @Test
    fun `累计播放时长达到阈值才上报`() {
        recorder.onSongChanged(song("1"), positionMs = 0)
        playFor(seconds = 6)
        recorder.onSongChanged(null, positionMs = 0)

        assertEquals(1, events.size)
        assertEquals("1", events[0].songId)
        assertEquals(6L, events[0].durationSeconds)
    }

    @Test
    fun `播放不足阈值不上报`() {
        recorder.onSongChanged(song("1"), positionMs = 0)
        recorder.onPosition(positionMs = 1_000L, playing = true)
        recorder.flush()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `暂停期间不计入时长`() {
        recorder.onSongChanged(song("1"), positionMs = 0)
        recorder.onPosition(positionMs = 3_000L, playing = true)  // +3s
        recorder.onPosition(positionMs = 3_000L, playing = false) // 暂停，位置没动
        recorder.onPosition(positionMs = 3_000L, playing = false)
        recorder.onPosition(positionMs = 6_000L, playing = true)  // +3s
        recorder.flush()

        assertEquals(1, events.size)
        assertEquals(6L, events[0].durationSeconds)
    }

    @Test
    fun `拖动进度条造成的大跨度前进不计入时长`() {
        recorder.onSongChanged(song("1"), positionMs = 0)
        playFor(seconds = 6, stepMs = 2_000L)                      // 正常播放 6s
        recorder.onPosition(positionMs = 90_000L, playing = true)  // 大跨度：视为拖动，不计入
        recorder.onPosition(positionMs = 91_000L, playing = true)  // +1s
        recorder.flush()

        assertEquals(1, events.size)
        assertEquals(7L, events[0].durationSeconds)
    }

    @Test
    fun `进度回退视为重新开始且不加时长`() {
        recorder.onSongChanged(song("1"), positionMs = 0)
        recorder.onPosition(positionMs = 2_000L, playing = true) // +2s
        recorder.onPosition(positionMs = 0L, playing = true)     // 回退
        recorder.onPosition(positionMs = 4_000L, playing = true) // +4s
        recorder.flush()

        assertEquals(1, events.size)
        assertEquals(6L, events[0].durationSeconds)
    }

    @Test
    fun `切歌会结算上一首并开启新事件`() {
        recorder.onSongChanged(song("A"), positionMs = 0)
        playFor(seconds = 6)
        recorder.onSongChanged(song("B"), positionMs = 0)
        playFor(seconds = 6)
        recorder.flush()

        assertEquals(2, events.size)
        assertEquals("A", events[0].songId)
        assertEquals(6L, events[0].durationSeconds)
        assertEquals("B", events[1].songId)
        assertEquals(6L, events[1].durationSeconds)
        // 每次结算都会重新生成 eventId，保证服务端幂等去重
        assertTrue(events[0].eventId != events[1].eventId)
    }

    @Test
    fun `完成标记会带到事件上`() {
        recorder.onSongChanged(song("A"), positionMs = 0)
        playFor(seconds = 6)
        recorder.markCompleted()
        recorder.flush()

        assertEquals(1, events.size)
        assertTrue(events[0].completed)
    }

    @Test
    fun `没有播放内容时 flush 不产生事件`() {
        recorder.flush()
        assertTrue(events.isEmpty())
    }
}
