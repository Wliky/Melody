package com.wliky.melody.artist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Song
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: ArtistRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    data class ArtistUiState(
        val loading: Boolean = true,
        val songs: List<Song> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(ArtistUiState())
        private set

    fun load(artistId: Long) {
        viewModelScope.launch {
            uiState = ArtistUiState(loading = true)
            when (val res = repository.topSongs(artistId)) {
                is AppResult.Failure -> uiState = ArtistUiState(
                    loading = false,
                    error = res.error.message,
                )

                is AppResult.Success -> uiState = ArtistUiState(
                    loading = false,
                    songs = res.data,
                )
            }
        }
    }

    /** 点击某首歌：从该首开始播放整个热门列表。 */
    fun playSongAt(index: Int) {
        playerQueue.setQueue(songs = uiState.songs, startIndex = index)
    }
}
