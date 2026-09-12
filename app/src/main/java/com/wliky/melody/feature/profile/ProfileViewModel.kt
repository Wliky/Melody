package com.wliky.melody.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.data.repository.AuthRepository
import com.wliky.melody.data.repository.HistoryRepository
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
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val profile: UserProfile? = null,
        val playlists: List<Playlist> = emptyList(),
        val likedSongCount: Int = 0,
        val recentSongs: List<Song> = emptyList(),
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
            // 用户信息拉取失败（网络抖动/风控）不应中断整个页面：
            // 歌单、收藏、足迹仍要继续加载，profile 留待下次 refresh 再补。
            authRepository.loadProfile(force = true).fold(
                onSuccess = { profile ->
                    _state.update { it.copy(profile = profile) }
                },
                onFailure = { error ->
                    // 只标记错误，但不 return —— 下面三个区块照常加载。
                    _state.update { it.copy(error = error) }
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

            // 听歌足迹：官方账号最近播放（需要登录）
            historyRepository.recentSongs().fold(
                onSuccess = { songs ->
                    _state.update { it.copy(recentSongs = songs) }
                },
                onFailure = { /* 足迹不可用不影响其它区块 */ },
            )

            // profile 仍为空（首次拉取没成功）时，延迟再补拉一次，缓解登录后短暂的风控抖动。
            if (_state.value.profile == null && authRepository.loggedIn.value) {
                kotlinx.coroutines.delay(PROFILE_RETRY_DELAY_MS)
                authRepository.loadProfile(force = true).fold(
                    onSuccess = { profile ->
                        _state.update { it.copy(profile = profile) }
                    },
                    onFailure = { /* 仍失败就保持占位文案，等下次进页面再试 */ },
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = UiState()
        }
    }

    private companion object {
        const val PROFILE_RETRY_DELAY_MS = 2500L
    }
}
