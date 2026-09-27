package com.wliky.melody.player

import androidx.compose.runtime.snapshotFlow
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.LyricLine
import com.wliky.melody.data.repo.LyricRepository
import com.wliky.melody.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外部歌词发布器（在 [PlayerQueue] 的 Main 作用域内常驻，后台播放时同样工作）：
 *
 * 1. **SuperLyric**（[规范](https://github.com/HChenX/SuperLyricApi)）——向 SuperLyric
 *    系统服务（需 Xposed 模块端，如「墨·状态栏歌词」等）发布实时歌词；
 *    切歌发全量包，行推进发增量包，停播发停止包。
 * 2. **车载蓝牙歌词**（参考 Salt Player `car_bluetooth_lyrics`）——把当前歌词行写入
 *    媒体元数据歌手字段，经 AVRCP/蓝牙同步到车机屏幕（通知栏标题保持歌名不变）。
 */
@Singleton
class ExternalLyricsPublisher @Inject constructor(
    private val lyricRepository: LyricRepository,
    private val settingsPreferences: SettingsPreferences,
) {

    private var lines: List<LyricLine> = emptyList()
    private var superLyricEnabled = false
    private var carLyricEnabled = false

    /** 当前曲目 id（用于构建 lyricId） */
    private var songId: Long = -1L
    private var activeLineIndex = -1
    private var loadJob: Job? = null

    /** 由 [PlayerQueue] 启动（Main 作用域）。 */
    fun start(queue: PlayerQueue, scope: CoroutineScope) {
        // 注册为 SuperLyric 发布者（规范要求在应用启动时调用一次，
        // 未注册时 sendXxx 会抛 IllegalStateException 导致外部歌词静默失效）
        runCatching { SuperLyricHelper.registerPublisher() }

        // 设置开关：SuperLyric 关闭时补发停止包；车载歌词关闭时还原歌手字段
        scope.launch {
            combine(
                settingsPreferences.superLyricEnabled,
                settingsPreferences.carBluetoothLyricsEnabled,
            ) { superOn, carOn -> superOn to carOn }
                .collect { (superOn, carOn) ->
                    val wasSuperOn = superLyricEnabled
                    superLyricEnabled = superOn
                    carLyricEnabled = carOn
                    if (wasSuperOn && !superOn) runCatching {
                        SuperLyricHelper.sendStop(SuperLyricData())
                    }
                    if (!carOn) queue.setCarBluetoothLyric(null)
                }
        }

        // 切歌：重置行号、还原车载字段、加载歌词并发 SuperLyric 全量包
        scope.launch {
            snapshotFlow { queue.state.current?.id }
                .distinctUntilChanged()
                .collect { id ->
                    activeLineIndex = -1
                    queue.setCarBluetoothLyric(null)
                    loadJob?.cancel()
                    if (id == null) {
                        lines = emptyList()
                        songId = -1L
                        if (superLyricEnabled) runCatching { SuperLyricHelper.sendStop(SuperLyricData()) }
                    } else {
                        songId = id
                        loadJob = scope.launch {
                            val loaded = when (val r = lyricRepository.getLyric(id)) {
                                is AppResult.Failure -> emptyList()
                                is AppResult.Success -> r.data
                            }
                            lines = loaded
                            if (superLyricEnabled && loaded.isNotEmpty()) {
                                runCatching { SuperLyricHelper.sendFullLyric(buildFullData(queue, loaded)) }
                            }
                        }
                    }
                }
        }

        // 进度：活动行变化时发 SuperLyric 增量包 + 更新车载蓝牙歌词行
        scope.launch {
            snapshotFlow { queue.state.positionMs }
                .collect { positionMs ->
                    val idx = activeIndexAt(positionMs)
                    if (idx != activeLineIndex) {
                        activeLineIndex = idx
                        if (superLyricEnabled && idx >= 0 && lines.isNotEmpty()) {
                            val line = lines.getOrNull(idx) ?: return@collect
                            runCatching {
                                SuperLyricHelper.sendLyricProgress(
                                    SuperLyricData()
                                        .setLyricId(songId.toString())
                                        .setDuration(queue.state.durationMs)
                                        .setCurrentLyricIndex(idx)
                                        .setPosition(positionMs)
                                        .setLyric(SuperLyricLine(line.text)),
                                )
                            }
                        }
                        if (carLyricEnabled) {
                            queue.setCarBluetoothLyric(lines.getOrNull(idx)?.text)
                        }
                    }
                }
        }

        // 暂停：还原车载字段并通知 SuperLyric 停止；恢复播放时重发全量包
        scope.launch {
            snapshotFlow { queue.state.isPlaying }
                .distinctUntilChanged()
                .collect { playing ->
                    if (!playing) {
                        queue.setCarBluetoothLyric(null)
                        if (superLyricEnabled) runCatching { SuperLyricHelper.sendStop(SuperLyricData()) }
                    } else if (superLyricEnabled && lines.isNotEmpty()) {
                        runCatching { SuperLyricHelper.sendFullLyric(buildFullData(queue, lines)) }
                    }
                }
        }
    }

    /** 全量包：标题/歌手/时长 + 整份逐行歌词（带时间与翻译）。 */
    private fun buildFullData(queue: PlayerQueue, lyricLines: List<LyricLine>): SuperLyricData {
        val song = queue.state.current
        val duration = queue.state.durationMs
        val all = lyricLines.mapIndexed { i, line ->
            // LRC 只有起始时间：行结束取下一行起点，最后一行兜底歌曲时长 / +5s
            val start = line.timeMs
            val end = lyricLines.getOrNull(i + 1)?.timeMs
                ?: duration.takeIf { it > start }
                ?: (start + 5_000L)
            SuperLyricLine(
                line.text,
                null,
                line.translation,
                start,
                end,
            )
        }
        return SuperLyricData()
            .setLyricId(songId.toString())
            .setTitle(song?.name)
            .setArtist(song?.subtitle)
            .setDuration(queue.state.durationMs)
            .setCurrentLyricIndex(0)
            .setAllLyrics(all)
    }

    /** 当前 [positionMs] 落在哪一行；间隙期返回前一行（车载显示延续），开头前返回 -1。 */
    private fun activeIndexAt(positionMs: Long): Int {
        var result = -1
        for ((i, line) in lines.withIndex()) {
            if (line.timeMs <= positionMs) result = i else break
        }
        return result
    }
}
