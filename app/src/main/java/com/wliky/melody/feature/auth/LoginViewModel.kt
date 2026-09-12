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
 * 三条通路，互为兜底：
 *  1. **手机验证码** —— 默认方式。国内网络下最稳，不碰密码，也不受二维码风控影响。
 *  2. **扫码** —— 手机上没有账号或不想收短信时用。请求二维码 → 轮询 → 建立会话。
 *  3. **Cookie** —— 前面两条都被风控挡住时的终极兜底，直接复用浏览器里已有的登录态。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 登录方式；任意一条失败都能一键切到另一条。 */
    enum class Method(val label: String) {
        PHONE("验证码"),
        QR("扫码"),
        COOKIE("Cookie"),
    }

    sealed interface LoginState {
        data object Loading : LoginState
        data class QrReady(val qr: QrCodeInfo, val status: String) : LoginState
        data object Expired : LoginState

        /** 命中风控：继续轮询没有意义，需要换一种方式。 */
        data class RiskControlled(val message: String) : LoginState

        data object Success : LoginState
        data class Failed(val message: String) : LoginState
    }

    data class PhoneState(
        val phone: String = "",
        val captcha: String = "",
        val sending: Boolean = false,
        val submitting: Boolean = false,
        /** 重新获取验证码的倒计时（秒），0 表示可以点。 */
        val countdown: Int = 0,
        val error: String? = null,
        val notice: String? = null,
    ) {
        val canSend: Boolean get() = !sending && countdown == 0 && phone.filter { it.isDigit() }.length == 11
        val canSubmit: Boolean get() = !submitting && phone.filter { it.isDigit() }.length == 11 && captcha.length >= 4
    }

    data class CookieState(
        val input: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _method = MutableStateFlow(Method.PHONE)
    val method: StateFlow<Method> = _method.asStateFlow()

    private val _phoneState = MutableStateFlow(PhoneState())
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    private val _cookieState = MutableStateFlow(CookieState())
    val cookieState: StateFlow<CookieState> = _cookieState.asStateFlow()

    val apiMode: StateFlow<ApiMode> = settingsRepository.settings
        .map { it.apiMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApiMode.API_SERVER)

    private var pollJob: Job? = null
    private var countdownJob: Job? = null

    init {
        // 默认是验证码方式，不需要一进来就去要二维码 —— 二维码在切到「扫码」时才拉，
        // 少一次无谓的请求，也少一次可能被风控记录的机会。
        if (_method.value == Method.QR) refresh()
    }

    /** 切换登录方式。切到扫码时，如果还没拿到二维码就补一个。 */
    fun switchMethod(next: Method) {
        if (_method.value == next) return
        _method.value = next
        when (next) {
            Method.QR -> if (_state.value !is LoginState.QrReady) refresh()
            Method.PHONE, Method.COOKIE -> pollJob?.cancel()
        }
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
                    // 拿不到二维码时不要只报错就完事：直接把用户推向验证码 / Cookie 方式
                    _state.value = LoginState.Failed(error.message)
                },
            )
        }
    }

    // ------------------------------------------------------------ 手机验证码

    fun onPhoneChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(PHONE_LENGTH)
        _phoneState.update { it.copy(phone = digits, error = null) }
    }

    fun onCaptchaChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _phoneState.update { it.copy(captcha = digits, error = null) }
    }

    fun sendCaptcha() {
        val current = _phoneState.value
        if (current.phone.length != PHONE_LENGTH) {
            _phoneState.update { it.copy(error = "请输入 11 位手机号") }
            return
        }
        if (current.sending || current.countdown > 0) return

        viewModelScope.launch {
            _phoneState.update { it.copy(sending = true, error = null, notice = null) }
            authRepository.sendCaptcha(current.phone).fold(
                onSuccess = {
                    _phoneState.update { it.copy(sending = false, notice = "验证码已发送，请查看短信") }
                    startCountdown()
                },
                onFailure = { error ->
                    _phoneState.update { it.copy(sending = false, error = error.message) }
                },
            )
        }
    }

    fun submitPhone() {
        val current = _phoneState.value
        if (current.phone.length != PHONE_LENGTH) {
            _phoneState.update { it.copy(error = "请输入 11 位手机号") }
            return
        }
        if (current.captcha.length < 4) {
            _phoneState.update { it.copy(error = "请填写收到的短信验证码") }
            return
        }

        viewModelScope.launch {
            _phoneState.update { it.copy(submitting = true, error = null) }
            authRepository.loginWithPhone(current.phone, current.captcha).fold(
                onSuccess = {
                    _phoneState.update { it.copy(submitting = false) }
                    _state.value = LoginState.Success
                },
                onFailure = { error ->
                    _phoneState.update { it.copy(submitting = false, error = error.message) }
                },
            )
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = COUNTDOWN_SECONDS
            while (remaining > 0 && isActive) {
                _phoneState.update { it.copy(countdown = remaining) }
                delay(1_000)
                remaining--
            }
            _phoneState.update { it.copy(countdown = 0) }
        }
    }

    // ---------------------------------------------------------------- Cookie

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

    // ------------------------------------------------------------------ 扫码

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
                                    poll.message.ifBlank { "登录环境异常，请改用验证码或 Cookie 登录" },
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
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val INITIAL_INTERVAL_MS = 1_500L
        const val MAX_INTERVAL_MS = 5_000L
        const val PHONE_LENGTH = 11
        const val COUNTDOWN_SECONDS = 60
    }
}
