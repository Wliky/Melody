package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.common.toAppError
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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

    /**
     * 用浏览器里已有的登录凭据（Cookie 中的 `MUSIC_U`）登录。
     *
     * 这是最可靠的通路：不触发登录验证，直接复用已有登录态。
     * 登录成功与否只看会话里有没有 MUSIC_U，用户信息由后续页面异步补拉。
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
     * 拉取用户信息。
     *
     * **登录态的唯一判据是会话里有没有 MUSIC_U**（见 [SecureSessionStore.loggedIn]），
     * 与这里能否拉到 profile 无关。profile 只是「昵称 / 头像 / uid」这些展示信息：
     * 拉不到（网络抖动 / 风控）绝不等同于「未登录」，**绝不能据此清会话**。
     *
     * 只有服务端明确返回「未登录」（fetchProfile 抛 [AppError.Unauthorized]）才清会话。
     * 否则一律保留会话、保留旧 profile（若有），让 UI 继续显示已登录。
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
            val profile = try {
                providers.current().fetchProfile()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                val error = t.toAppError()
                if (error is AppError.Unauthorized) {
                    // 服务端明确说「未登录」才清会话。
                    invalidateSession()
                    throw error
                }
                // 网络 / 风控 / 解析抖动：保留会话，沿用旧 profile（若有），返回 null 表示暂无信息。
                _profile.value
            }
            if (profile != null) {
                _profile.value = profile
                // 顺手把 userId 补进会话，后续「我的歌单 / 收藏 / 足迹」都靠它定位账号。
                if (profile.userId.isNotBlank()) session.updateUserId(profile.userId)
            }
            profile
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
