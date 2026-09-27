package com.wliky.melody.player

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.repo.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局播放队列：跨页面共享（首页 / 我的 / 歌单详情共用一个队列）。
 *
 * 职责：
 * - 持有当前歌单曲目列表与播放位置；
 * - 逐首解析播放直链（无版权 / VIP / 云盘曲自动跳过）；
 * - 一首播完自动续播下一首（STATE_ENDED → resolve next）。
 *
 * 播放 URL 带 1200s 过期签名，不做缓存，每次起播现取。
 *
 * 会话持久化：队列 / 下标 / 进度 / 时长 / 播放模式落盘（[PlaybackPreferences]），
 * 重开 App 后迷你条照旧显示上次歌曲与进度；此时播放器是空的，用户点播放才重新
 * 取直链并从上次进度继续（懒恢复，不自动发声）。
 */
/**
 * 循环模式：列表循环（回绕）/ 单曲循环 / 顺序播放（播完即停）。
 */
enum class RepeatMode { ALL, ONE, OFF }

@Singleton
class PlayerQueue @Inject constructor(
    private val songRepository: SongRepository,
    private val playerConnection: PlayerConnection,
    private val playbackPrefs: PlaybackPreferences,
) {

    data class QueueState(
        val queue: List<Song> = emptyList(),
        /** 当前正在解析/播放的曲目下标（跳过不可播曲目后指向实际播放的那首） */
        val index: Int = -1,
        val current: Song? = null,
        val isPlaying: Boolean = false,
        /** 正在解析播放地址 */
        val resolving: Boolean = false,
        /** 当前播放位置（500ms 轮询刷新） */
        val positionMs: Long = 0,
        /** 当前曲目总时长；未知为 0 */
        val durationMs: Long = 0,
        /** 随机播放（切歌时随机选一首，排除当前曲） */
        val shuffle: Boolean = false,
        /** 循环模式，默认列表循环 */
        val repeatMode: RepeatMode = RepeatMode.ALL,
        val error: String? = null,
    )

    var state by mutableStateOf(QueueState())
        private set

    /** 用户点播事件（[setQueue] 触发）：UI 订阅后可自动打开全屏播放页。 */
    val userPlayEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playJob: Job? = null
    /** 上次落盘时间，用于进度写入节流 */
    private var lastPersistAt = 0L

    init {
        // 恢复上次会话：只还原状态（迷你条可见、显示进度），不自动起播
        scope.launch {
            val snap = runCatching { playbackPrefs.read() }.getOrNull() ?: return@launch
            val current = snap.queue.getOrNull(snap.index) ?: return@launch
            state = state.copy(
                queue = snap.queue,
                index = snap.index,
                current = current,
                positionMs = snap.positionMs,
                durationMs = snap.durationMs,
                shuffle = snap.shuffle,
                repeatMode = snap.repeatMode,
                isPlaying = false,
                resolving = false,
                error = null,
            )
        }
        scope.launch {
            playerConnection.controller.collect { controller ->
                if (controller == null) return@collect
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        state = state.copy(isPlaying = isPlaying)
                        persistState()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) playNext(auto = true)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        // 切歌才归零；播放器被清空（null）时保留恢复出来的进度
                        if (mediaItem != null) {
                            state = state.copy(positionMs = 0, durationMs = 0)
                        }
                    }
                })
                state = state.copy(isPlaying = controller.isPlaying)
            }
        }
        // 进度轮询：ExoPlayer 位置不是事件流，500ms 采样一次（仅在有变化时写状态）
        scope.launch {
            while (isActive) {
                playerConnection.controller.value?.let { c ->
                    // 播放器为空时（恢复态 / 未起播）不覆盖已恢复的进度与时长
                    if (c.currentMediaItem == null) return@let
                    val pos = c.currentPosition.coerceAtLeast(0)
                    val dur = c.duration.takeIf { it > 0 } ?: 0L
                    if (state.positionMs != pos || state.durationMs != dur) {
                        state = state.copy(positionMs = pos, durationMs = dur)
                        persistState(throttle = true)
                    }
                }
                delay(500)
            }
        }
    }

    /** 设置新队列并从 [startIndex] 开始播放。 */
    fun setQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        state = state.copy(queue = songs, index = startIndex, error = null)
        userPlayEvents.tryEmit(Unit)
        resolveAndPlay(startIndex)
    }

    /** 队列内切歌（详情页点任意一首）。 */
    fun playAt(index: Int) {
        if (index !in state.queue.indices) return
        state = state.copy(error = null)
        resolveAndPlay(index)
    }

    /** 自动连播：当前曲播完 → 下一首。 */
    /**
     * 下一首。auto=true 为播完自动续播：单曲循环时重播本首（手动点下一首仍真切歌）。
     */
    fun playNext(auto: Boolean = false) {
        if (state.queue.isEmpty()) return
        if (auto && state.repeatMode == RepeatMode.ONE) {
            playerConnection.controller.value?.run {
                seekTo(0)
                if (!isPlaying) play()
            }
            return
        }
        val next = neighborIndex(forward = true) ?: return
        state = state.copy(error = null)
        resolveAndPlay(next)
    }

    /** 切换随机播放。 */
    fun toggleShuffle() {
        state = state.copy(shuffle = !state.shuffle)
        persistState()
    }

    /** 循环模式轮换：列表循环 → 单曲循环 → 顺序播放 → 列表循环。 */
    fun cycleRepeatMode() {
        state = state.copy(
            repeatMode = when (state.repeatMode) {
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
                RepeatMode.OFF -> RepeatMode.ALL
            },
        )
        persistState()
    }

    /**
     * 播放模式键（随机 + 循环合并为一个按钮，点击切换）：
     * 列表循环 → 单曲循环 → 顺序播放 → 随机播放 → 列表循环。
     * 随机为独立开关，其余状态复用 [RepeatMode]。
     */
    fun cycleMode() {
        state = when {
            state.shuffle -> state.copy(shuffle = false, repeatMode = RepeatMode.ALL)
            state.repeatMode == RepeatMode.ALL -> state.copy(repeatMode = RepeatMode.ONE)
            state.repeatMode == RepeatMode.ONE -> state.copy(repeatMode = RepeatMode.OFF)
            else -> state.copy(shuffle = true, repeatMode = RepeatMode.ALL)
        }
        persistState()
    }

    /** 顶栏/迷你条：播放 ⇄ 暂停。恢复态（播放器为空）下重新取直链并从上次进度续播。 */
    fun togglePlayPause() {
        val controller = playerConnection.controller.value ?: return
        when {
            controller.isPlaying -> controller.pause()
            controller.currentMediaItem != null -> controller.play()
            state.current != null && state.index in state.queue.indices -> {
                resolveAndPlay(state.index, startPositionMs = state.positionMs)
            }
        }
    }

    /** 上一首：已播超 3s 先回到本首开头，否则真切上一首（随机模式随机选，列表循环回绕）。 */
    fun playPrevious() {
        val controller = playerConnection.controller.value ?: return
        if (state.positionMs > 3_000) {
            controller.seekTo(0)
            if (!controller.isPlaying) controller.play()
            return
        }
        val prev = neighborIndex(forward = false) ?: return
        state = state.copy(error = null)
        resolveAndPlay(prev)
    }

    /**
     * 依当前随机/循环模式算相邻下标：随机模式任选一个非当前下标；
     * 顺序越界时列表循环回绕、顺序播放返回 null（停）。
     */
    private fun neighborIndex(forward: Boolean): Int? {
        val size = state.queue.size
        if (size == 0) return null
        if (state.shuffle && size > 1) {
            return (0 until size).filter { it != state.index }.random()
        }
        val target = if (forward) state.index + 1 else state.index - 1
        return when {
            target in state.queue.indices -> target
            state.repeatMode == RepeatMode.ALL -> ((target % size) + size) % size
            else -> null
        }
    }

    /**
     * 通知栏歌词：把系统媒体控件通知的标题替换为 [line]（传 null 恢复歌名）。
     * 通过 replaceMediaItem 更新当前曲目元数据，通知随 MediaSession 自动刷新，
     * 不额外发独立通知；恢复态（播放器为空）下无操作。
     */
    fun setNotificationLyric(line: String?) {
        val controller = playerConnection.controller.value ?: return
        val index = controller.currentMediaItemIndex
        if (index < 0 || index >= controller.mediaItemCount) return
        val song = state.current ?: return
        val old = runCatching { controller.getMediaItemAt(index) }.getOrNull() ?: return
        val title = line?.takeIf { it.isNotBlank() } ?: song.name
        val metadata = old.mediaMetadata.buildUpon().setTitle(title).build()
        controller.replaceMediaItem(index, old.buildUpon().setMediaMetadata(metadata).build())
    }

    /** 进度条拖动：跳到指定位置。 */
    fun seekTo(positionMs: Long) {
        val controller = playerConnection.controller.value ?: return
        val target = positionMs.coerceAtLeast(0)
        controller.seekTo(target)
        state = state.copy(positionMs = target)
        persistState()
    }

    /** 退出登录等场景：停播放并清空队列（连持久化一并清除，迷你条不再显示）。 */
    fun stop() {
        playJob?.cancel()
        playerConnection.controller.value?.run {
            pause()
            stop()
            clearMediaItems()
        }
        state = QueueState()
        scope.launch { runCatching { playbackPrefs.clear() } }
    }

    /** 落盘当前会话（节流可选）：供下次启动恢复迷你条与进度。 */
    private fun persistState(throttle: Boolean = false) {
        val s = state
        val current = s.current ?: return
        val now = System.currentTimeMillis()
        if (throttle && now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistAt = now
        val snapshotIndex = s.index.takeIf { it in s.queue.indices } ?: return
        scope.launch {
            runCatching {
                playbackPrefs.save(
                    queue = s.queue,
                    index = snapshotIndex,
                    positionMs = s.positionMs,
                    durationMs = s.durationMs.takeIf { it > 0 } ?: current.durationMs,
                    shuffle = s.shuffle,
                    repeatMode = s.repeatMode,
                )
            }
        }
    }

    /**
     * 从 [index] 起逐首解析直链，第一个可播曲目起播；
     * 连续不可播最多尝试 [MAX_RESOLVE] 首后放弃。
     */
    private fun resolveAndPlay(index: Int, startPositionMs: Long = 0) {
        playJob?.cancel()
        playJob = scope.launch {
            val songs = state.queue
            if (index !in songs.indices) return@launch
            state = state.copy(resolving = true)

            var tries = 0
            var i = index
            while (i in songs.indices && tries < MAX_RESOLVE) {
                tries++
                val song = songs[i]
                when (val url = songRepository.getSongUrl(song.id)) {
                    is AppResult.Failure -> {
                        state = state.copy(
                            resolving = false,
                            error = "取播放地址失败：${url.error.message}",
                        )
                        return@launch
                    }

                    is AppResult.Success -> {
                        val playUrl = url.data
                        if (playUrl == null) { // 无版权 / VIP / 云盘曲 → 跳过
                            i++
                            continue
                        }
                        val controller = playerConnection.controller.value
                        if (controller == null) {
                            state = state.copy(resolving = false, error = "播放服务未就绪，稍后再试")
                            return@launch
                        }
                        val mediaItem = MediaItem.Builder()
                            .setUri(playUrl)
                            .setMediaId(song.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.name)
                                    .setArtist(song.subtitle)
                                    .setArtworkUri(song.coverUrl?.let(Uri::parse))
                                    .build(),
                            )
                            .build()
                        // 恢复上次会话时按保存位置起播（交给播放器，避免 prepare 后 seek 被缓冲期进度覆盖）
                        if (startPositionMs > 0) {
                            controller.setMediaItem(mediaItem, startPositionMs)
                        } else {
                            controller.setMediaItem(mediaItem)
                        }
                        controller.prepare()
                        controller.play()
                        state = state.copy(
                            index = i,
                            current = song,
                            resolving = false,
                            error = null,
                            positionMs = startPositionMs,
                        )
                        persistState()
                        return@launch
                    }
                }
            }
            state = state.copy(
                resolving = false,
                error = "连续 $tries 首不可播（无版权 / VIP / 云盘曲）",
            )
        }
    }

    private companion object {
        /** 逐首解析直链时的最大尝试数（防止整页全不可播时空转） */
        private const val MAX_RESOLVE = 20

        /** 播放进度落盘最小间隔（500ms 轮询下节流，避免频繁写 DataStore） */
        private const val PERSIST_INTERVAL_MS = 5_000L
    }
}
