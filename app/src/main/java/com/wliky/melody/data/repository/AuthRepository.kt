package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.LoginPollResult
import com.wliky.melody.core.model.QrCodeInfo
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 登录 / 会话（文档 §8）。
 *
 * 流程：请求二维码 key → 展示二维码 → 轮询状态 → 成功后凭据由数据源写入
 * Keystore 保护的安全存储 → 拉取用户信息建立 Session → 失效时清理并引导重新登录。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
    private val session: SecureSessionStore,
    dispatchers: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    /**
     * 是否已登录。唯一判据是会话里有没有真正的 `MUSIC_U`（见 [SecureSessionStore.loggedIn]）。
     *
     * 数据源已固定为自建 API 服务（api-enhanced），不再有「演示模式视为已登录」的特例：
     * 未登录就无法使用网易云播放功能，登录页是唯一入口。
     */
    val loggedIn: StateFlow<Boolean> = session.loggedIn

    val requiresLogin: StateFlow<Boolean> = session.loggedIn
        .map { !it }
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun userId(): String = session.userId()

    suspend fun requestQrCode(): AppResult<QrCodeInfo> = appRunCatching {
        providers.current().requestQrCode()
    }

    /**
     * 用浏览器里已有的登录凭据（Cookie 中的 `MUSIC_U`）登录。
     *
     * 扫码链路被风控拦截时这是最可靠的通路：不触发登录验证，直接复用已有登录态。
     */
    suspend fun loginWithCookie(rawCookie: String): AppResult<UserProfile?> = appRunCatching {
        val profile = providers.current().loginWithCookie(rawCookie)
        _profile.value = profile
        profile
    }

    /** 发送登录短信验证码。 */
    suspend fun sendCaptcha(phone: String): AppResult<Boolean> = appRunCatching {
        providers.current().sendCaptcha(phone)
    }

    /** 手机号 + 验证码登录。成功后用户信息立即就位，不需要再拉一次。 */
    suspend fun loginWithPhone(phone: String, captcha: String): AppResult<UserProfile?> = appRunCatching {
        val profile = providers.current().loginWithPhone(phone, captcha)
        _profile.value = profile
        profile
    }

    /**
     * 轮询登录状态。成功（803）后立即拉取用户信息，建立 Session。
     */
    suspend fun pollLogin(key: String): AppResult<LoginPollResult> = appRunCatching {
        val result = providers.current().pollQrLogin(key)
        if (result.isSuccess) {
            loadProfile(force = true)
        }
        result
    }

    /**
     * 拉取用户信息。
     *
     * 这里同时承担「会话体检」的职责：拿着凭据却拉不到 profile，说明这份登录态已经失效，
     * 直接清掉并让 UI 回到未登录状态，而不是留着一个"看起来已登录、实际什么都拉不到"的假象。
     */
    suspend fun loadProfile(force: Boolean = false): AppResult<UserProfile?> {
        if (!force) {
            _profile.value?.let { return AppResult.Success(it) }
        }
        if (!session.hasAuthToken()) {
            _profile.value = null
            return AppResult.Success(null)
        }
        return appRunCatching {
            val profile = providers.current().fetchProfile()
            if (profile == null) {
                invalidateSession()
                null
            } else {
                _profile.value = profile
                profile
            }
        }
    }

    suspend fun logout() {
        appRunCatching { providers.current().logout() }
        session.clear()
        _profile.value = null
    }

    /** 接口返回 301 / 拉取用户信息失败时调用。 */
    fun invalidateSession() {
        session.clear()
        _profile.value = null
    }

    fun describeLoginRequirement(): AppError =
        AppError.Unauthorized("请先扫码登录后再使用该功能")
}
