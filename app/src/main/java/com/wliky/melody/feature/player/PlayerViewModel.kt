package com.wliky.melody.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.common.onFailure
import com.wliky.melody.core.common.onSuccess
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.PlayerState
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.player.NotificationLyricBridge
import com.wliky.melody.core.player.PlaybackEventRecorder
import com.wliky.melody.core.player.PlayerController
import com.wliky.melody.data.repository.HistoryRepository
import com.wliky.melody.data.repository.MusicRepository
import com.wliky.melody.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放器 ViewModel（文档 §10）。
 *
 * 只订阅 PlayerController 暴露的状态，并把「播放事件」写入本地历史与同步队列。
 * 界面上任何地方（首页 / 搜索 / 歌单 / 历史）点歌都调用同一个 [play]。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    private val syncRepository: SyncRepository,
    private val lyricBridge: NotificationLyricBridge,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    val snapshot: StateFlow<PlaybackSnapshot> = player.snapshot

    val queue: StateFlow<List<Song>> = player.queue

    private val _lyric = MutableStateFlow<Lyric?>(null)
    val lyric: StateFlow<Lyric?> = _lyric.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    private val _showQueue = MutableStateFlow(false)
    val showQueue: StateFlow<Boolean> = _showQueue.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val recorder = PlaybackEventRecorder(
        clock = clock,
        onEvent = { event ->
            viewModelScope.launch { syncRepository.enqueue(event) }
        },
    )

    private var lastSongId: String? = null

    init {
        viewModelScope.launch { player.connect() }
        // 订阅通知栏歌词开关
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                lyricBridge.setEnabled(settings.notificationLyric)
            }
        }
        viewModelScope.launch {
            snapshot.collect { snap ->
                val nowPlaying = snap.nowPlaying
                val songId = nowPlaying?.songId
                if (songId != lastSongId) {
                    lastSongId = songId
                    handleSongChanged(snap)
                }
                recorder.onPosition(snap.positionMs, nowPlaying?.isPlaying == true)
                if (snap.state == PlayerState.ENDED) recorder.markCompleted()
                snap.errorMessage?.let { _message.value = it }
                // 通知栏歌词：根据当前进度定位歌词行
                updateNotificationLyric(snap.positionMs)
            }
        }
    }

    private fun updateNotificationLyric(positionMs: Long) {
        val current = _lyric.value ?: run {
            lyricBridge.update(null)
            return
        }
        if (current.isEmpty) {
            lyricBridge.update(null)
            return
        }
        val index = current.indexAt(positionMs)
        val line = if (index >= 0) current.lines[index].text else null
        lyricBridge.update(line)
    }

    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            player.connect()
            player.setQueue(songs, startIndex, playWhenReady = true)
        }
    }

    fun playAt(index: Int) = player.seekToIndex(index)

    fun togglePlayPause() = player.toggle()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun cycleRepeat() {
        val next = when (snapshot.value.repeatMode) {
            AppRepeatMode.OFF -> AppRepeatMode.ALL
            AppRepeatMode.ALL -> AppRepeatMode.ONE
            AppRepeatMode.ONE -> AppRepeatMode.OFF
        }
        player.setRepeatMode(next)
    }

    fun toggleShuffle() = player.setShuffle(!snapshot.value.shuffle)

    fun removeFromQueue(index: Int) = player.removeFromQueue(index)

    fun clearQueue() {
        player.clearQueue()
        _lyric.value = null
    }

    fun openFullPlayer() {
        _showFullPlayer.value = true
    }

    fun closeFullPlayer() {
        _showFullPlayer.value = false
    }

    fun toggleQueue() {
        _showQueue.value = !_showQueue.value
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun handleSongChanged(snap: PlaybackSnapshot) {
        val song = resolveSong(snap)
        recorder.onSongChanged(song, snap.positionMs, completed = false)
        if (song == null) {
            _lyric.value = null
            return
        }
        viewModelScope.launch { historyRepository.recordPlay(song) }
        viewModelScope.launch {
            musicRepository.lyric(song.id)
                .onSuccess { _lyric.value = it }
                .onFailure { _lyric.value = null }
        }
    }

    /** 优先从队列里取完整歌曲信息，队列对不上时用 NowPlaying 兜底。 */
    private fun resolveSong(snap: PlaybackSnapshot): Song? {
        val nowPlaying = snap.nowPlaying ?: return null
        if (nowPlaying.songId.isBlank()) return null
        queue.value.getOrNull(snap.queueIndex)?.let { return it }
        return Song(
            id = nowPlaying.songId,
            name = nowPlaying.title,
            durationMs = nowPlaying.durationMs,
            coverUrl = nowPlaying.coverUrl,
        )
    }

    override fun onCleared() {
        // 结算最后一条播放事件，避免退出时丢掉
        recorder.flush()
        super.onCleared()
    }
}
