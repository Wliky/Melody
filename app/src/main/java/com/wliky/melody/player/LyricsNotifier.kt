package com.wliky.melody.player

import androidx.compose.runtime.snapshotFlow
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.LyricLine
import com.wliky.melody.data.repo.LyricRepository
import com.wliky.melody.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知栏歌词：开关打开且播放中时，把系统媒体控件通知的标题行替换为当前歌词行
 * （经 [PlayerQueue.setNotificationLyric] 更新 MediaSession 元数据，通知自动刷新，
 * 不再额外发独立通知）；关闭开关时恢复标题为歌名。
 *
 * 歌词由本类自行经 [LyricRepository] 拉取（与播放页歌词加载互不影响）。
 */
@Singleton
class LyricsNotifier @Inject constructor(
    private val playerQueue: PlayerQueue,
    private val lyricRepository: LyricRepository,
    private val settingsPreferences: SettingsPreferences,
) {

    // MediaController 只能在主线程调用：本类会经 setNotificationLyric 改通知元数据，
    // 订阅与刷新统一跑主线程；歌词网络请求由 LyricRepository 内部自行切 IO。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 当前歌曲的歌词缓存（本类自行拉取）。 */
    private var cachedLines: List<LyricLine> = emptyList()
    private var lastLineIndex = -1

    init {
        // 开关 + 歌曲 → 拉歌词（重置行号）；关闭时恢复通知标题为歌名
        scope.launch {
            combine(
                settingsPreferences.notificationLyricsEnabled,
                snapshotFlow { playerQueue.state.current?.id ?: -1L },
            ) { enabled, songId -> enabled to songId }
                .collect { (enabled, songId) ->
                    lastLineIndex = -1
                    if (enabled && songId > 0) {
                        cachedLines = when (val res = lyricRepository.getLyric(songId)) {
                            is AppResult.Success -> res.data
                            is AppResult.Failure -> emptyList()
                        }
                    } else {
                        cachedLines = emptyList()
                        playerQueue.setNotificationLyric(null)
                    }
                }
        }

        // 播放位置 → 当前行变化才刷新通知标题（仅行变化时替换，不按帧刷）；
        // 无歌词的歌保持歌名（通知标题本就是歌名，无需兜底替换）
        scope.launch {
            combine(
                settingsPreferences.notificationLyricsEnabled,
                snapshotFlow { playerQueue.state.positionMs },
                snapshotFlow { playerQueue.state.isPlaying },
            ) { enabled, positionMs, isPlaying -> Triple(enabled, positionMs, isPlaying) }
                .collect { (enabled, positionMs, isPlaying) ->
                    if (!enabled || !isPlaying) return@collect
                    if (cachedLines.isEmpty()) return@collect
                    val index = cachedLines.indexOfLast { it.timeMs <= positionMs }
                    if (index == lastLineIndex) return@collect
                    lastLineIndex = index
                    playerQueue.setNotificationLyric(
                        when {
                            index >= 0 -> cachedLines[index].text
                                .ifBlank { cachedLines[index].translation.orEmpty() }

                            else -> playerQueue.state.current?.name
                        },
                    )
                }
        }
    }

    /** App 进程退出时停掉订阅（正常情况下进程销毁即终止，这里兜底恢复歌名）。 */
    fun shutdown() {
        playerQueue.setNotificationLyric(null)
        scope.cancel()
    }
}
