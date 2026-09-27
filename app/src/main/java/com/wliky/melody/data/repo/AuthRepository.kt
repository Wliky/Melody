package com.wliky.melody.data.repo

import com.wliky.melody.data.error.AppError
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.QrLoginState
import com.wliky.melody.data.model.User
import com.wliky.melody.data.remote.CookieStore
import com.wliky.melody.data.remote.NeteaseClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** 登录态变化事件：授权成功 / 退出登录，驱动首页、我的等依赖登录态的页面刷新。 */
sealed interface SessionEvent {
    data object LoggedIn : SessionEvent
    data object LoggedOut : SessionEvent
}

/**
 * 账号仓库：二维码登录流程。
 *
 * 流程：createQrKey() → 展示二维码 → 轮询 checkQrLogin() 直到 Authorized / Expired。
 * 授权瞬间服务端通过 Set-Cookie 下发 MUSIC_U，由 CookieJar 自动捕获并持久化。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val client: NeteaseClient,
    private val cookieStore: CookieStore,
) {

    private val _sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)

    /** 登录态变化事件流：HomeViewModel / MineViewModel 订阅后强制刷新。 */
    val sessionEvents: SharedFlow<SessionEvent> = _sessionEvents.asSharedFlow()

    /** 生成二维码 key（unikey，有效期约 2 分钟）。 */
    suspend fun createQrKey(): AppResult<String> =
        when (val res = client.callWeapi("/weapi/login/qrcode/unikey", """{"type":1}""")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val unikey = res.data["unikey"]?.jsonPrimitive?.content
                if (unikey.isNullOrBlank()) {
                    AppResult.failure(AppError.Parse("unikey 缺失"))
                } else {
                    AppResult.success(unikey)
                }
            }
        }

    /** 二维码内容（用任意扫码器编码为图片即可，网易云 App 扫码）。 */
    fun qrContent(unikey: String): String =
        "https://music.163.com/login?codekey=$unikey"

    /**
     * 轮询二维码状态。业务码即状态：
     * 800 过期 / 801 待扫码 / 802 已扫码待确认 / 803 已授权。
     */
    suspend fun checkQrLogin(unikey: String): AppResult<QrLoginState> =
        when (val res = client.post("/weapi/login/qrcode/client/login", """{"key":"$unikey","type":1}""")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                val code = res.data["code"]?.toString()?.toIntOrNull() ?: -1
                when (code) {
                    800 -> AppResult.success(QrLoginState.Expired)
                    801 -> AppResult.success(QrLoginState.WaitingScan)
                    802 -> AppResult.success(QrLoginState.WaitingConfirm)
                    803 -> {
                        // 授权成功：Set-Cookie 已入 jar，再拉一次账号信息拿 User
                        _sessionEvents.tryEmit(SessionEvent.LoggedIn)
                        when (val account = currentUser()) {
                            is AppResult.Failure -> account
                            is AppResult.Success ->
                                AppResult.success(QrLoginState.Authorized(account.data ?: return AppResult.failure(AppError.Parse("用户信息缺失"))))
                        }
                    }
                    else -> AppResult.failure(AppError.Api(code, "二维码登录异常"))
                }
            }
        }

    /** 当前登录用户；未登录返回 Success(null)。 */
    suspend fun currentUser(): AppResult<User?> =
        when (val res = client.callWeapi("/weapi/nuser/account/get", "{}")) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                // 未登录时服务端返回 "profile": null（JsonNull），as? 安全转换
                val profile = res.data["profile"] as? JsonObject ?: return AppResult.success(null)
                AppResult.success(
                    User(
                        id = profile["userId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        nickname = profile["nickname"]?.jsonPrimitive?.content ?: "未知用户",
                        avatarUrl = profile["avatarUrl"]?.jsonPrimitive?.content,
                        vipType = profile["vipType"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    ),
                )
            }
        }

    /** 退出登录：清空本地 cookie（含 MUSIC_U），播放停止由调用方处理。 */
    fun logout() {
        cookieStore.clear()
        _sessionEvents.tryEmit(SessionEvent.LoggedOut)
    }
}
