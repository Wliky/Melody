package com.wliky.melody.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.LyricLine
import com.wliky.melody.data.repo.LyricRepository
import com.wliky.melody.settings.SettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放页状态：跟随 [PlayerQueue] 当前曲目加载歌词。
 * 歌词按曲目缓存一屏（切回上一首重新拉取，无本地持久化）。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerQueue: PlayerQueue,
    private val lyricRepository: LyricRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    /** 歌词字号（sp）：设置页「歌词管理」与播放页歌词面板均可实时调节（12–24）。 */
    val lyricFontSize: StateFlow<Float> = settingsPreferences.lyricFontSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, 20f)

    /** 播放倍速（0.5x–3.0x）：播放页倍速面板实时调节。 */
    val playbackSpeed: StateFlow<Float> = settingsPreferences.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    /** SuperLyric 系统级歌词广播开关。 */
    val superLyricEnabled: StateFlow<Boolean> = settingsPreferences.superLyricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 车载蓝牙歌词开关。 */
    val carBluetoothLyricsEnabled: StateFlow<Boolean> = settingsPreferences.carBluetoothLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var lyricLines by mutableStateOf<List<LyricLine>>(emptyList())
        private set

    /** 歌词加载中（仅当前曲目有效） */
    var lyricLoading by mutableStateOf(false)
        private set

    /** 已红心的歌曲 id：委托全局 [PlayerQueue]（likelist + 乐观更新，跨页面共享） */
    val likedSongIds: Set<Long> get() = playerQueue.likedSongIds

    /** 红心操作失败提示：委托 [PlayerQueue]。 */
    val likeError: String? get() = playerQueue.likeError

    private var lyricJob: Job? = null

    init {
        viewModelScope.launch {
            snapshotFlow { playerQueue.state.current?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId == null) {
                        lyricJob?.cancel()
                        lyricLines = emptyList()
                        lyricLoading = false
                    } else {
                        loadLyric(songId)
                    }
                }
        }
    }

    /** 红心 / 取消红心：委托 [PlayerQueue.toggleLike]（全局状态，歌单页实时联动）。 */
    fun toggleLike(songId: Long) = playerQueue.toggleLike(songId)

    /** UI 消费完红心失败提示后清除。 */
    fun clearLikeError() = playerQueue.clearLikeError()

    /** 调节倍速（0.5x–3.0x），立即生效并持久化。 */
    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsPreferences.setPlaybackSpeed(speed) }
    }

    private fun loadLyric(songId: Long) {
        lyricJob?.cancel()
        lyricJob = viewModelScope.launch {
            lyricLoading = true
            val lines = when (val r = lyricRepository.getLyric(songId)) {
                is AppResult.Failure -> emptyList()
                is AppResult.Success -> r.data
            }
            // 快速切歌时丢弃过期结果
            if (playerQueue.state.current?.id == songId) {
                lyricLines = lines
                lyricLoading = false
            }
        }
    }
}
