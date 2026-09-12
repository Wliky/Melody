package com.wliky.melody.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知栏歌词桥（v0.4.0）。
 *
 * UI 层的 [PlayerViewModel] 知道当前播放的歌词行，但它和 [PlaybackService]
 * （持有 MediaSession、负责通知栏展示）在不同的进程/生命周期里，没有直接引用。
 * 这个单例是一根「数据管道」：
 *  - PlayerViewModel 拿到歌词后，把「当前歌词行」推进来；
 *  - PlaybackService 订阅它，把歌词行写入 MediaSession 的 metadata，
 *    系统媒体通知就会显示歌词（无需蓝牙、无需悬浮窗权限）。
 *
 * 开关关闭时（notificationLyric = false），PlayerViewModel 不再写入，桥保持 null，
 * 通知栏自然回到只显示歌名。
 */
@Singleton
class NotificationLyricBridge @Inject constructor() {

    private val _currentLine = MutableStateFlow<String?>(null)
    val currentLine: StateFlow<String?> = _currentLine.asStateFlow()

    /** 是否启用通知栏歌词。由 SettingsRepository 驱动。 */
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) _currentLine.value = null
    }

    /** 更新当前歌词行（空字符串会被当作「无歌词」处理为 null）。 */
    fun update(line: String?) {
        if (!_enabled.value) {
            if (_currentLine.value != null) _currentLine.value = null
            return
        }
        val normalized = line?.trim()?.takeIf { it.isNotEmpty() }
        if (_currentLine.value != normalized) {
            _currentLine.value = normalized
        }
    }
}
