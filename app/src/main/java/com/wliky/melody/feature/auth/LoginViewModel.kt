package com.wliky.melody.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.common.onFailure
import com.wliky.melody.core.common.onSuccess
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.QrCodeInfo
import com.wliky.melody.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 扫码登录（文档 §8）。
 *
 * 请求二维码 → 展示 → 按指数退避轮询 → 成功后建立 Session。
 * 二维码过期（800）会明确提示并提供刷新，而不是静默转圈。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    sealed interface LoginState {
        data object Loading : LoginState
        data class QrReady(val qr: QrCodeInfo, val status: String) : LoginState
        data object Expired : LoginState
        data object Success : LoginState
        data class Failed(val message: String) : LoginState
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    val apiMode: StateFlow<ApiMode> = settingsRepository.settings
        .map { it.apiMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApiMode.DIRECT)

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        _state.value = LoginState.Loading
        viewModelScope.launch {
            authRepository.requestQrCode().fold(
                onSuccess = { qr ->
                    _state.value = LoginState.QrReady(qr, "请使用网易云音乐 App 扫描二维码")
                    startPolling(qr.key)
                },
                onFailure = { error ->
                    _state.value = LoginState.Failed(error.message)
                },
            )
        }
    }

    private fun startPolling(key: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var interval = INITIAL_INTERVAL_MS
            while (isActive) {
                delay(interval)
                val result = authRepository.pollLogin(key)
                var stop = false
                result
                    .onSuccess { poll ->
                        when {
                            poll.isSuccess -> {
                                _state.value = LoginState.Success
                                stop = true
                            }

                            poll.isExpired -> {
                                _state.value = LoginState.Expired
                                stop = true
                            }

                            poll.isWaitingConfirm -> _state.value = LoginState.QrReady(
                                currentQr() ?: return@onSuccess,
                                "已扫码，请在手机上确认登录",
                            )

                            poll.isWaitingScan -> _state.value = LoginState.QrReady(
                                currentQr() ?: return@onSuccess,
                                "等待扫码…",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.value = LoginState.Failed(error.message)
                        stop = true
                    }
                if (stop) break
                // 指数退避：1.5s → 2.25s → … → 最多 5s，避免高频打接口
                interval = (interval * 3 / 2).coerceAtMost(MAX_INTERVAL_MS)
            }
        }
    }

    private fun currentQr(): QrCodeInfo? = (_state.value as? LoginState.QrReady)?.qr

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val INITIAL_INTERVAL_MS = 1_500L
        const val MAX_INTERVAL_MS = 5_000L
    }
}
