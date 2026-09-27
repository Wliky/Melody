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
import com.wliky.melody.data.repo.SongRepository
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
    private val songRepository: SongRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    /** 歌词字号（sp）：设置页「歌词管理」滑条实时写入，播放页歌词页实时生效。 */
    val lyricFontSize: StateFlow<Float> = settingsPreferences.lyricFontSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, 20f)

    var lyricLines by mutableStateOf<List<LyricLine>>(emptyList())
        private set

    /** 歌词加载中（仅当前曲目有效） */
    var lyricLoading by mutableStateOf(false)
        private set

    /** 已红心的歌曲 id（乐观更新，接口失败会回滚） */
    var likedSongIds by mutableStateOf<Set<Long>>(emptySet())
        private set

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

    /** 红心 / 取消红心：先乐观更新，接口失败（多为未登录）则回滚。 */
    fun toggleLike(songId: Long) {
        val liked = likedSongIds.contains(songId)
        val next = !liked
        android.util.Log.d("LIKE", "toggle id=$songId next=$next")
        likedSongIds = if (next) likedSongIds + songId else likedSongIds - songId
        viewModelScope.launch {
            val result = songRepository.likeSong(songId, next)
            if (result is AppResult.Failure) {
                val err = result.error
                android.util.Log.d("LIKE", "failed: code=${(err as? com.wliky.melody.data.error.AppError.Api)?.code} msg=${err.message}")
                likedSongIds = if (next) likedSongIds - songId else likedSongIds + songId
            }
        }
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
