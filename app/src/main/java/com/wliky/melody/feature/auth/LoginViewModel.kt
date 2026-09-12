package com.wliky.melody.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.network.CookieParser
import com.wliky.melody.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录（v0.4.0-preview.5+）：两种原生登录方式的状态机。
 *
 *  1. **手机号登录**：发短信验证码 → 输入验证码 → 登录。
 *  2. **Cookie 登录**：粘贴浏览器里的 MUSIC_U。
 *
 * 登录成功与否**只看会话里有没有 MUSIC_U**（见 [CookieParser.hasMusicU]），
 * 与能否拉到用户信息无关；profile 由后续页面异步补拉。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    sealed interface LoginState {
        /** 登录成功，由 NavController 走 onLoggedIn 退出登录页。 */
        data object Success : LoginState

        /** 空闲 / 无操作。 */
        data object Idle : LoginState
    }

    // ------------------------------------------------------------------ 手机号

    sealed interface PhoneState {
        data object Idle : PhoneState

        /** 正在发送验证码。 */
        data object Sending : PhoneState

        /** 验证码已发送，等待输入。 */
        data class Sent(val countdown: Int = 60) : PhoneState

        /** 正在登录。 */
        data object LoggingIn : PhoneState

        data class Failed(val message: String, val recoverable: Boolean = false) : PhoneState
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _phoneState = MutableStateFlow<PhoneState>(PhoneState.Idle)
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    private val _cookieError = MutableStateFlow<String?>(null)
    val cookieError: StateFlow<String?> = _cookieError.asStateFlow()

    private val _cookieLoading = MutableStateFlow(false)
    val cookieLoading: StateFlow<Boolean> = _cookieLoading.asStateFlow()

    private var countdownJob: Job? = null

    // ------------------------------------------------------------------ 手机号

    /** 发送短信验证码。 */
    fun sendCaptcha(phone: String) {
        if (_phoneState.value is PhoneState.Sending) return
        _phoneState.value = PhoneState.Sending
        viewModelScope.launch {
            authRepository.sendCaptcha(phone).fold(
                onSuccess = {
                    _phoneState.value = PhoneState.Sent(60)
                    startCountdown()
                },
                onFailure = { error ->
                    _phoneState.value = PhoneState.Failed(
                        message = error.toUserMessage(),
                        recoverable = error.recoverable,
                    )
                },
            )
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                val current = _phoneState.value
                if (current is PhoneState.Sent) {
                    _phoneState.value = current.copy(countdown = remaining)
                }
            }
        }
    }

    /** 手机号 + 验证码登录。 */
    fun loginWithPhone(phone: String, captcha: String) {
        if (_phoneState.value is PhoneState.LoggingIn) return
        _phoneState.value = PhoneState.LoggingIn
        viewModelScope.launch {
            authRepository.loginWithPhone(phone, captcha).fold(
                onSuccess = { _state.value = LoginState.Success },
                onFailure = { error ->
                    _phoneState.value = PhoneState.Failed(
                        message = error.toUserMessage(),
                        recoverable = error.recoverable,
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------------ Cookie

    /** Cookie 登录。 */
    fun submitManualCookie(raw: String) {
        val normalized = CookieParser.normalize(raw)
            ?: run {
                _cookieError.value = "Cookie 格式不正确，请粘贴包含 MUSIC_U 的完整内容"
                return
            }
        if (!CookieParser.looksUsable(normalized)) {
            _cookieError.value = "没有识别到有效的 MUSIC_U，请确认复制的是登录后的 Cookie"
            return
        }
        _cookieLoading.value = true
        _cookieError.value = null
        viewModelScope.launch {
            authRepository.loginWithCookie(normalized).fold(
                onSuccess = { _state.value = LoginState.Success },
                onFailure = { error ->
                    _cookieLoading.value = false
                    _cookieError.value = error.toUserMessage()
                },
            )
        }
    }

    fun clearCookieError() {
        _cookieError.value = null
    }

    fun reset() {
        _state.value = LoginState.Idle
        _phoneState.value = PhoneState.Idle
        _cookieError.value = null
        _cookieLoading.value = false
    }

    private fun AppError.toUserMessage(): String = when (this) {
        is AppError.Unauthorized -> "登录态已失效，请重新登录"
        is AppError.Parse -> "登录参数有误，请检查输入"
        is AppError.Network -> "网络连接失败，请稍后重试"
        is AppError.Server -> message.ifBlank { "登录服务暂时不可用，请稍后重试" }
        else -> message
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
