package com.wliky.melody.core.player

import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * 平台无关的播放器接口（文档 §10）。
 *
 * UI / ViewModel 只依赖它，未来做桌面端或换成其它播放引擎时，
 * 上层代码一行都不用改。
 */
interface PlayerController {

    /** 当前播放快照：曲目、状态、进度、循环/随机。 */
    val snapshot: StateFlow<PlaybackSnapshot>

    /** 当前队列（逻辑队列，不是 MediaItem 队列）。 */
    val queue: StateFlow<List<Song>>

    val isConnected: StateFlow<Boolean>

    suspend fun connect()

    fun release()

    /**
     * 设置队列并开始播放。
     * 注意：这里只登记歌曲元数据，音频地址在真正播放到该曲目时才解析（懒解析），
     * 避免一次请求几百个歌曲地址。
     */
    suspend fun setQueue(songs: List<Song>, startIndex: Int, playWhenReady: Boolean = true)

    fun play()

    fun pause()

    fun toggle()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun seekToIndex(index: Int)

    fun setRepeatMode(mode: AppRepeatMode)

    fun setShuffle(enabled: Boolean)

    fun removeFromQueue(index: Int)

    fun clearQueue()
}

/**
 * 音频地址解析端口。
 *
 * core 层定义接口，data 层提供实现（真正的接口适配属于 data 层职责）。
 * ExoPlayer 在加载线程上调用它，因此这里是阻塞式签名。
 */
fun interface SongUrlProvider {
    /** 返回可播放的 URL；返回 null 表示当前不可播放（无版权 / 需要会员 / 已下架）。 */
    fun resolveBlocking(songId: String): String?
}
