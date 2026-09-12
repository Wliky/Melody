package com.wliky.melody.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.repository.HistoryRepository
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

/**
 * 播放历史（文档 §6 / §9）。
 *
 * 同步是全自动的（见 SyncManager），这里**不提供任何手动同步入口**，
 * 只如实展示后台队列的状态，让用户知道「已经自动处理到哪了」。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    syncRepository: SyncRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 自动同步队列的快照。 */
    data class SyncSummary(
        val pending: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val total: Int = 0,
    ) {
        /** 没有积压也没有失败，说明一切都已经自动处理完了。 */
        val settled: Boolean get() = pending == 0 && failed == 0
    }

    data class UiState(
        val remoteSongs: List<Song> = emptyList(),
        val remoteError: AppError? = null,
        val loadingRemote: Boolean = false,
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

    /** 当前模式是否真的存在自动上报通道（只有自建 API 服务才有）。 */
    val autoSyncActive: StateFlow<Boolean> = settingsRepository.settings
        .map { it.reportPlayback && it.apiMode == ApiMode.API_SERVER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** 上报总开关，默认开启。关掉只停止上传，本地记录不受影响。 */
    val reportEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.reportPlayback }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

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
