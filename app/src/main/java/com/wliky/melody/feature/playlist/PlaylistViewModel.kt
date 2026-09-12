package com.wliky.melody.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>(ARG_PLAYLIST_ID).orEmpty()

    data class UiState(
        val loading: Boolean = true,
        val playlist: Playlist? = null,
        val songs: List<Song> = emptyList(),
        val error: AppError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (playlistId.isBlank()) {
                _state.update { it.copy(loading = false, error = AppError.NotFound("歌单不存在")) }
                return@launch
            }
            homeRepository.playlistDetail(playlistId).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            loading = false,
                            playlist = detail.playlist,
                            songs = detail.songs,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error) }
                },
            )
        }
    }

    companion object {
        const val ARG_PLAYLIST_ID = "playlistId"
        const val ROUTE = "playlist/{$ARG_PLAYLIST_ID}"
        fun route(playlistId: String): String = "playlist/$playlistId"
    }
}
