package com.wliky.melody.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.PlayRecordEntry
import com.wliky.melody.data.model.User
import com.wliky.melody.data.model.UserDetail
import com.wliky.melody.data.repo.AuthRepository
import com.wliky.melody.data.repo.UserRepository
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人主页状态：用户信息 + 账号统计 + 听歌排行（本周/全部切换）。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    data class ProfileUiState(
        val loading: Boolean = true,
        val user: User? = null,
        val detail: UserDetail? = null,
        val weekly: Boolean = true,
        val record: List<PlayRecordEntry> = emptyList(),
        val recordLoading: Boolean = false,
        val error: String? = null,
    )

    var uiState by mutableStateOf(ProfileUiState())
        private set

    private var recordJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val me = authRepository.currentUser()) {
                is AppResult.Failure ->
                    uiState = uiState.copy(loading = false, error = me.error.message)
                is AppResult.Success -> {
                    val user = me.data
                    if (user == null) {
                        uiState = uiState.copy(loading = false, error = "未登录")
                        return@launch
                    }
                    uiState = uiState.copy(user = user)
                    when (val detail = userRepository.getUserDetail(user.id)) {
                        is AppResult.Failure ->
                            uiState = uiState.copy(loading = false, error = detail.error.message)
                        is AppResult.Success ->
                            uiState = uiState.copy(loading = false, detail = detail.data)
                    }
                    loadRecord(user.id, weekly = uiState.weekly)
                }
            }
        }
    }

    /** 切换 本周 / 全部 听歌排行。 */
    fun switchPeriod(weekly: Boolean) {
        val uid = uiState.user?.id ?: return
        if (weekly == uiState.weekly) return
        uiState = uiState.copy(weekly = weekly)
        loadRecord(uid, weekly)
    }

    private fun loadRecord(userId: Long, weekly: Boolean) {
        recordJob?.cancel()
        uiState = uiState.copy(recordLoading = true)
        recordJob = viewModelScope.launch {
            when (val r = userRepository.getPlayRecord(userId, weekly)) {
                is AppResult.Failure ->
                    uiState = uiState.copy(recordLoading = false, record = emptyList())
                is AppResult.Success ->
                    uiState = uiState.copy(recordLoading = false, record = r.data)
            }
        }
    }

    /** 从排行 [index] 首开始播整页。 */
    fun playAt(index: Int) {
        val songs = uiState.record.map { it.song }
        if (index in songs.indices) playerQueue.setQueue(songs, index)
    }
}
