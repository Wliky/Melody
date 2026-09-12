package com.wliky.melody.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 播放历史（文档 §6 / §9）。
 *
 * 听歌记录的同步全自动（见 SyncManager），这里**不再持有任何同步状态**：
 * 同步是后台行为，不该占用界面。想关掉就去设置里的开关。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    data class UiState(
        val remoteSongs: List<Song> = emptyList(),
        val remoteError: AppError? = null,
        val loadingRemote: Boolean = false,
    )

    val localHistory: StateFlow<List<Song>> = historyRepository.localHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadRemote() {
        viewModelScope.launch {
            _state.update { it.copy(loadingRemote = true, remoteError = null) }
            historyRepository.remoteRecords().fold(
                onSuccess = { songs ->
                    _state.update { it.copy(loadingRemote = false, remoteSongs = songs) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loadingRemote = false, remoteError = error) }
                },
            )
        }
    }

    fun clearLocal() {
        viewModelScope.launch { historyRepository.clearLocal() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
