package com.wliky.melody.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.QrLoginState
import com.wliky.melody.data.model.User
import com.wliky.melody.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 二维码登录状态机：生成 key → 每 2s 轮询 → 授权/过期。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class LoginUiState(
        val loading: Boolean = false,
        val qrContent: String? = null,
        val qrState: QrLoginState? = null,
        val user: User? = null,
        val error: String? = null,
    )

    var uiState by mutableStateOf(LoginUiState())
        private set

    private var pollJob: Job? = null

    /**
     * 进入登录页时刷新登录态（冷启动 / 退出登录后共用）：
     * 本地 cookie 有效直接展示成功态，否则走二维码流程。
     */
    fun refreshSession() {
        pollJob?.cancel()
        viewModelScope.launch {
            when (val me = authRepository.currentUser()) {
                is AppResult.Success -> {
                    val u = me.data
                    if (u == null) {
                        startLogin()
                    } else {
                        uiState = LoginUiState(
                            loading = false,
                            qrState = QrLoginState.Authorized(u),
                            user = u,
                        )
                    }
                }
                is AppResult.Failure -> startLogin()
            }
        }
    }

    /** 生成新的二维码（首次进入 / 过期 / 失败后手动重试共用）。 */
    fun startLogin() {
        pollJob?.cancel()
        uiState = LoginUiState(loading = true)
        pollJob = viewModelScope.launch {
            when (val key = authRepository.createQrKey()) {
                is AppResult.Failure ->
                    uiState = uiState.copy(loading = false, error = key.error.message)
                is AppResult.Success -> {
                    uiState = uiState.copy(
                        loading = false,
                        qrContent = authRepository.qrContent(key.data),
                        qrState = QrLoginState.WaitingScan,
                    )
                    pollQr(key.data)
                }
            }
        }
    }

    private suspend fun pollQr(unikey: String) {
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            when (val result = authRepository.checkQrLogin(unikey)) {
                is AppResult.Failure -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        uiState = uiState.copy(error = result.error.message)
                        return
                    }
                    // 瞬时失败：跳过本轮，继续轮询
                }
                is AppResult.Success -> {
                    consecutiveFailures = 0
                    uiState = uiState.copy(qrState = result.data)
                    when (val state = result.data) {
                        is QrLoginState.Authorized -> {
                            uiState = uiState.copy(user = state.user)
                            return
                        }
                        is QrLoginState.Expired -> return
                        else -> Unit // 继续轮询
                    }
                }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }
}
