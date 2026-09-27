package com.wliky.melody.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 歌单详情页状态：曲目列表 + 点播。
 * 点击任意曲目 → 整个歌单进入 [PlayerQueue]，从该首开始播。
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    data class DetailUiState(
        val loading: Boolean = true,
        val playlist: Playlist? = null,
        val songs: List<Song> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(DetailUiState())
        private set

    /** 首次进入或重试时加载；同一歌单重复调用只生效一次。 */
    fun start(playlist: Playlist) {
        if (uiState.playlist?.id == playlist.id && uiState.songs.isNotEmpty()) return
        uiState = DetailUiState(loading = true, playlist = playlist)
        load(playlist)
    }

    private fun load(playlist: Playlist) {
        viewModelScope.launch {
            when (val r = homeRepository.getPlaylistTracks(playlist.id, limit = 1000)) {
                is AppResult.Failure ->
                    uiState = uiState.copy(loading = false, error = r.error.message)

                is AppResult.Success ->
                    uiState = uiState.copy(
                        loading = false,
                        songs = r.data,
                        error = if (r.data.isEmpty()) "歌单里没有曲目" else null,
                    )
            }
        }
    }

    /** 点歌：整单入队，从 [index] 播。 */
    fun playSong(index: Int) {
        if (index in uiState.songs.indices) {
            playerQueue.setQueue(uiState.songs, index)
        }
    }
}
