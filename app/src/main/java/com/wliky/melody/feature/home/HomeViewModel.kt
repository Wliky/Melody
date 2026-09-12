package com.wliky.melody.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.data.repository.AuthRepository
import com.wliky.melody.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val feed: HomeFeed? = null,
        val error: AppError? = null,
    ) {
        val isEmpty: Boolean
            get() {
                val current = feed ?: return true
                return current.recommendedPlaylists.isEmpty() &&
                    current.newSongs.isEmpty() &&
                    current.rankings.isEmpty() &&
                    current.personalizedSongs.isEmpty()
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val loggedIn: StateFlow<Boolean> = authRepository.loggedIn

    init {
        load(forceRefresh = false)
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.feed == null && !forceRefresh,
                    refreshing = forceRefresh,
                    error = null,
                )
            }
            homeRepository.loadHome(forceRefresh)
                .onSuccess { feed ->
                    _state.update { it.copy(loading = false, refreshing = false, feed = feed, error = null) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, refreshing = false, error = error) }
                }
        }
    }

    fun refresh() = load(forceRefresh = true)
}
