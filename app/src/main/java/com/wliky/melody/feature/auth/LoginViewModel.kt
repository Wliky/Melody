package com.wliky.melody.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.network.CookieParser
import com.wliky.melody.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录（v0.3.0-preview.3+）。
 *
 * 只剩一条通路：**WebView 直接加载网易云官方登录页**。
 *
 * 选这条路的原因：api-enhanced 暴露的 `/login/qr/*` / `/login/cellphone`
 * 走的是网易加密接口，国内网络经常被风控挡住（403 / 8821），表现就是
 * 「扫码无响应 / 验证码登录报 400」。换成官方登录页之后，扫码 / 验证码 / 邮箱
 * 都由网易自己处理风控，我们只需要在登录成功后从 `WebView` 的 `CookieManager`
 * 里把整套 Cookie 拿出来即可。
 *
 * Cookie 兜底仍然有用（浏览器已经登录好了的场景），但属于次要入口，
 * 放在「高级选项」折叠面板里。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    sealed interface LoginState {
        /** 初始状态：WebView 正在加载网易云官方登录页。 */
        data object Loading : LoginState

        /** 页面加载完成，等待用户登录。 */
        data object Ready : LoginState

        /** 登录失败：Cookie 没有 MUSIC_U 或解析失败。 */
        data class Failed(val message: String) : LoginState

        /** 登录成功，由 NavController 走 onLoggedIn 退出登录页。 */
        data object Success : LoginState
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /**
     * WebView 页面加载状态（驱动 UI 层的"页面已就绪/等待登录"提示）。
     */
    private val _pageLoaded = MutableStateFlow(false)
    val pageLoaded: StateFlow<Boolean> = _pageLoaded.asStateFlow()

    fun onPageLoaded() {
        _pageLoaded.value = true
        if (_state.value is LoginState.Loading) {
            _state.value = LoginState.Ready
        }
    }

    fun onPageStarted() {
        _pageLoaded.value = false
    }

    /**
     * 把 WebView 的全部 Cookie 拿出来过一遍。
     *
     * 调用时机：WebView 跳转到一个能识别 `MUSIC_U` 的路径之后。
     *
     * 返回值：
     *  - true  —— 这份 Cookie 真的带着 MUSIC_U，登录成功并退出登录页；
     *  - false —— 还没有登录成功，调用方继续等下次跳转。
     */
    fun onWebViewCookies(rawCookie: String?): Boolean {
        if (rawCookie.isNullOrBlank()) return false
        val sanitized = CookieParser.sanitize(rawCookie) ?: return false
        if (!CookieParser.hasMusicU(sanitized)) return false
        if (_state.value is LoginState.Success) return true
        submitCookie(sanitized)
        return true
    }

    /** 用户从「高级选项 → Cookie 兜底」走手动粘贴。 */
    fun submitManualCookie(raw: String) {
        val normalized = CookieParser.normalize(raw)
            ?: run {
                _state.value = LoginState.Failed("Cookie 格式不正确，请粘贴包含 MUSIC_U 的完整内容")
                return
            }
        if (!CookieParser.looksUsable(normalized)) {
            _state.value = LoginState.Failed("没有识别到有效的 MUSIC_U，请确认复制的是登录后的 Cookie")
            return
        }
        submitCookie(normalized)
    }

    fun consumeError() {
        if (_state.value is LoginState.Failed) _state.value = LoginState.Ready
    }

    private fun submitCookie(cookie: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            authRepository.completeWebLogin(cookie).fold(
                onSuccess = { _state.value = LoginState.Success },
                onFailure = { error -> _state.value = LoginState.Failed(error.toUserMessage()) },
            )
        }
    }

    private fun AppError.toUserMessage(): String = when (this) {
        is AppError.Unauthorized -> "这份 Cookie 已失效，请重新登录或复制一份新的"
        is AppError.Parse -> "Cookie 解析失败，请检查粘贴内容是否完整"
        is AppError.Network -> "网络连接失败，请稍后重试"
        else -> message
    }

    override fun onCleared() {
        super.onCleared()
    }
}