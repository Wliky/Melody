package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
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
import kotlinx.coroutines.flow.combine
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
    settingsRepository: SettingsRepository,
    dispatchers: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    /** 演示模式视为已登录，不打扰用户。 */
    val loggedIn: StateFlow<Boolean> = combine(
        session.loggedIn,
        settingsRepository.settings,
    ) { hasSession, settings ->
        settings.apiMode == ApiMode.MOCK || hasSession
    }.stateIn(scope, SharingStarted.Eagerly, session.loggedIn.value)

    val requiresLogin: StateFlow<Boolean> = settingsRepository.settings
        .combine(session.loggedIn) { settings, _ -> settings.apiMode != ApiMode.MOCK }
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun userId(): String = session.userId()

    suspend fun requestQrCode(): AppResult<QrCodeInfo> = appRunCatching {
        providers.current().requestQrCode()
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

    suspend fun loadProfile(force: Boolean = false): AppResult<UserProfile?> {
        if (!force) {
            _profile.value?.let { return AppResult.Success(it) }
        }
        return appRunCatching {
            val profile = providers.current().fetchProfile()
            _profile.value = profile
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
