package com.wliky.melody.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.repository.HistoryRepository
import com.wliky.melody.data.repository.SyncManager
import com.wliky.melody.data.repository.SyncReport
import com.wliky.melody.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 同步队列计数，用于把「待同步 / 已失败」说清楚。 */
    data class SyncSummary(
        val pending: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val total: Int = 0,
    )

    data class UiState(
        val syncing: Boolean = false,
        val remoteSongs: List<Song> = emptyList(),
        val remoteError: AppError? = null,
        val loadingRemote: Boolean = false,
        val lastReport: SyncReport? = null,
    )

    val localHistory: StateFlow<List<Song>> = historyRepository.localHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val syncSummary: StateFlow<SyncSummary> = combine(
        syncRepository.pendingCount,
        syncRepository.failedCount,
        syncRepository.skippedCount,
        syncRepository.totalCount,
    ) { pending, failed, skipped, total ->
        SyncSummary(pending = pending, failed = failed, skipped = skipped, total = total)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncSummary())

    /** 用户是否开启了播放记录上报（默认关闭）。 */
    val reportEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.reportPlayback }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

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

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val report = syncManager.syncNow()
            _state.update { it.copy(syncing = false, lastReport = report) }
        }
    }

    fun clearLocal() {
        viewModelScope.launch { historyRepository.clearLocal() }
    }

    fun consumeReport() {
        _state.update { it.copy(lastReport = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
