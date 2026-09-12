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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 登录（文档 §8）。
 *
 * 两条通路，互为兜底：
 *  1. **扫码** —— 默认方式，不接触密码。请求二维码 → 轮询 → 建立会话。
 *  2. **Cookie** —— 粘贴浏览器里已有的登录凭据。网易对扫码链路有风控（403 / 8821），
 *     一旦命中，继续扫码没有意义，这里会直接把用户引导到 Cookie 方式。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 登录方式；扫码失败时用户可一键切换。 */
    enum class Method { QR, COOKIE }

    sealed interface LoginState {
        data object Loading : LoginState
        data class QrReady(val qr: QrCodeInfo, val status: String) : LoginState
        data object Expired : LoginState

        /** 命中风控：继续轮询没有意义，需要换一种方式。 */
        data class RiskControlled(val message: String) : LoginState

        data object Success : LoginState
        data class Failed(val message: String) : LoginState
    }

    data class CookieState(
        val input: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _method = MutableStateFlow(Method.QR)
    val method: StateFlow<Method> = _method.asStateFlow()

    private val _cookieState = MutableStateFlow(CookieState())
    val cookieState: StateFlow<CookieState> = _cookieState.asStateFlow()

    val apiMode: StateFlow<ApiMode> = settingsRepository.settings
        .map { it.apiMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApiMode.DIRECT)

    private var pollJob: Job? = null

    init {
        refresh()
    }

    /** 切换登录方式。切回扫码时，如果上次是失败/风控态就重新要一个二维码。 */
    fun switchMethod(next: Method) {
        if (_method.value == next) return
        _method.value = next
        if (next == Method.QR && _state.value !is LoginState.QrReady) refresh()
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
                    // 拿不到二维码时不要只报错就完事：直接把用户推向 Cookie 方式
                    _state.value = LoginState.Failed(error.message)
                },
            )
        }
    }

    fun onCookieInputChange(value: String) {
        _cookieState.update { it.copy(input = value, error = null) }
    }

    /** 提交 Cookie 登录。成功后由 [LoginState.Success] 驱动页面回调。 */
    fun submitCookie() {
        val raw = _cookieState.value.input
        if (raw.isBlank()) {
            _cookieState.update { it.copy(error = "请先粘贴 Cookie 内容") }
            return
        }
        pollJob?.cancel()
        viewModelScope.launch {
            _cookieState.update { it.copy(submitting = true, error = null) }
            authRepository.loginWithCookie(raw).fold(
                onSuccess = {
                    _cookieState.update { it.copy(submitting = false) }
                    _state.value = LoginState.Success
                },
                onFailure = { error ->
                    _cookieState.update { it.copy(submitting = false, error = error.message) }
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

                            poll.isRiskControlled -> {
                                // 风控：再扫多少次都会被拒，停下来让用户换方式
                                _state.value = LoginState.RiskControlled(
                                    poll.message.ifBlank { "登录环境异常，请改用 Cookie 登录" },
                                )
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
