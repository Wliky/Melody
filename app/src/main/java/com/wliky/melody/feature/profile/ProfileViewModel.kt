package com.wliky.melody.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.data.repository.AuthRepository
import com.wliky.melody.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val profile: UserProfile? = null,
        val playlists: List<Playlist> = emptyList(),
        val likedSongCount: Int = 0,
        val error: AppError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val loggedIn: StateFlow<Boolean> = authRepository.loggedIn
    val requiresLogin: StateFlow<Boolean> = authRepository.requiresLogin

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.loadProfile(force = true).fold(
                onSuccess = { profile ->
                    _state.update { it.copy(profile = profile) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error) }
                    return@launch
                },
            )

            playlistRepository.userPlaylists().fold(
                onSuccess = { playlists ->
                    _state.update { it.copy(loading = false, error = null, playlists = playlists) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error) }
                },
            )

            playlistRepository.likedSongs().fold(
                onSuccess = { songs ->
                    _state.update { it.copy(likedSongCount = songs.size) }
                },
                onFailure = { /* 收藏不可用不影响其它区块 */ },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = UiState()
        }
    }
}
