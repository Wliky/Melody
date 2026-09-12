package com.wliky.melody.core.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.PlayerState
import com.wliky.melody.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 播放器的 Android 实现（文档 §10）。
 *
 * 它是 [PlaybackService] 里 MediaSession 的客户端，本身不持有 ExoPlayer，
 * 所以界面销毁、进程切后台都不会影响播放。
 */
@Singleton
class Media3PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider,
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val connectMutex = Mutex()

    @Volatile
    private var controller: MediaController? = null

    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncSnapshot()
        }

        override fun onPlayerError(error: PlaybackException) {
            _snapshot.update {
                it.copy(state = PlayerState.ERROR, errorMessage = error.toUserMessage())
            }
        }
    }

    override suspend fun connect() {
        if (controller != null) return
        connectMutex.withLock {
            if (controller != null) return
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                val mediaController = suspendCancellableCoroutine { continuation ->
                    future.addListener(
                        {
                            runCatching { future.get() }
                                .onSuccess { continuation.resume(it) }
                                .onFailure { continuation.resumeWithException(it) }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                }
                mediaController.addListener(listener)
                controller = mediaController
                _isConnected.value = true
                syncSnapshot()
                startTicker()
            } catch (throwable: Throwable) {
                _snapshot.update {
                    it.copy(
                        state = PlayerState.ERROR,
                        errorMessage = "播放器初始化失败：${throwable.message ?: "未知原因"}",
                    )
                }
            }
        }
    }

    override fun release() {
        tickerJob?.cancel()
        tickerJob = null
        controller?.let {
            it.removeListener(listener)
            it.release()
        }
        controller = null
        _isConnected.value = false
    }

    override suspend fun setQueue(songs: List<Song>, startIndex: Int, playWhenReady: Boolean) {
        if (songs.isEmpty()) return
        val mediaController = controller ?: return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        _queue.value = songs
        mediaController.setMediaItems(
            songs.map { it.toMediaItem() },
            index,
            androidx.media3.common.C.TIME_UNSET,
        )
        mediaController.prepare()
        mediaController.playWhenReady = playWhenReady
        syncSnapshot()
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun toggle() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        syncSnapshot()
    }

    override fun seekToIndex(index: Int) {
        controller?.seekTo(index.coerceAtLeast(0), 0L)
        syncSnapshot()
    }

    override fun setRepeatMode(mode: AppRepeatMode) {
        controller?.repeatMode = mode.toMedia3()
        syncSnapshot()
    }

    override fun setShuffle(enabled: Boolean) {
        controller?.setShuffleModeEnabled(enabled)
        syncSnapshot()
    }

    override fun removeFromQueue(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        mediaController.removeMediaItem(index)
        _queue.update { songs -> songs.filterIndexed { i, _ -> i != index } }
        syncSnapshot()
    }

    override fun clearQueue() {
        controller?.clearMediaItems()
        _queue.value = emptyList()
        syncSnapshot()
    }

    // ------------------------------------------------------------------ 内部

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mediaController = controller
                if (mediaController != null && mediaController.isPlaying) {
                    _snapshot.update { it.copy(positionMs = mediaController.currentPosition.coerceAtLeast(0L)) }
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun syncSnapshot() {
        val mediaController = controller ?: return
        val item = mediaController.currentMediaItem
        val metadata = item?.mediaMetadata
        val nowPlaying = item?.let {
            com.wliky.melody.core.model.NowPlaying(
                songId = it.mediaId,
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                album = metadata?.albumTitle?.toString().orEmpty(),
                coverUrl = metadata?.artworkUri?.toString(),
                durationMs = metadata?.durationMs ?: 0L,
                isPlaying = mediaController.isPlaying,
                state = mediaController.playbackState.toPlayerState(),
            )
        }
        val duration = mediaController.duration.takeIf { it > 0 } ?: (metadata?.durationMs ?: 0L)
        _snapshot.update {
            it.copy(
                nowPlaying = nowPlaying,
                state = mediaController.playbackState.toPlayerState(),
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                repeatMode = mediaController.repeatMode.toAppRepeatMode(),
                shuffle = mediaController.shuffleModeEnabled,
                queueSize = mediaController.mediaItemCount,
                queueIndex = mediaController.currentMediaItemIndex,
            )
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(LazySongUrlDataSource.songUri(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(artistText)
                .setAlbumTitle(album?.name ?: "")
                .apply { coverUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun Int.toPlayerState(): PlayerState = when (this) {
        Player.STATE_IDLE -> PlayerState.IDLE
        Player.STATE_BUFFERING -> PlayerState.BUFFERING
        Player.STATE_READY -> PlayerState.READY
        Player.STATE_ENDED -> PlayerState.ENDED
        else -> PlayerState.IDLE
    }

    private fun AppRepeatMode.toMedia3(): Int = when (this) {
        AppRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        AppRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        AppRepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun Int.toAppRepeatMode(): AppRepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> AppRepeatMode.ALL
        Player.REPEAT_MODE_ONE -> AppRepeatMode.ONE
        else -> AppRepeatMode.OFF
    }

    private fun PlaybackException.toUserMessage(): String = when {
        message?.contains("不可播放") == true -> "该内容当前不可播放"
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败，请检查网络后重试"
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "音频加载失败，请稍后重试"
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "音频资源不存在"
        else -> message ?: "播放失败"
    }

    private companion object {
        const val TICK_INTERVAL_MS = 500L
    }
}
