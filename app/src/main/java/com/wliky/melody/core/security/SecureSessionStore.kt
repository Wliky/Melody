package com.wliky.melody.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wliky.melody.core.network.CookieParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登录凭据存储（文档 §16）。
 *
 * Key 由 Android Keystore 生成并保管（[MasterKey]），文件内容再用 AES256-SIV/GCM 加密，
 * 明文 Cookie 不落盘、不进日志。此文件已排除在云备份与设备迁移之外，见 res/xml/backup_rules.xml。
 *
 * **登录态的唯一判据是 Cookie 里有没有 `MUSIC_U`**（见 [CookieParser.hasMusicU]）。
 * 不能看「Cookie 是否非空」：服务端对匿名请求也会下发 `NMTID` / `_ntes_nuid` 等访客 Cookie，
 * 一旦据此判定登录，就会出现「一进登录页就显示已登录」。因此这里**只持久化带登录态的
 * Cookie**，匿名 Cookie 一律丢弃。
 */
@Singleton
class SecureSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _loggedIn = MutableStateFlow(CookieParser.hasMusicU(readCookie()))
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** 完整 Cookie 串，用于请求头 / 自建服务的 `cookie` 参数。 */
    fun cookie(): String = readCookie()

    /** 当前是否持有可用的登录凭据。 */
    fun hasAuthToken(): Boolean = CookieParser.hasMusicU(readCookie())

    fun userId(): String = prefs.getString(KEY_USER_ID, "").orEmpty()

    fun saveSession(cookie: String, userId: String) {
        prefs.edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_USER_ID, userId)
            .apply()
        _loggedIn.value = CookieParser.hasMusicU(cookie)
    }

    /**
     * 用服务端回传的 Cookie 刷新会话。
     *
     * 只有**确实带着登录态**的 Cookie 才会被采纳；匿名访客 Cookie（`NMTID` 等）直接忽略，
     * 这样「请求过一次接口」永远不会被误判成「已登录」。
     */
    fun updateCookie(cookie: String) {
        if (!CookieParser.hasMusicU(cookie)) return
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
        _loggedIn.value = true
    }

    /** 只更新 userId，不动 Cookie（例如拉取用户信息后补上 uid）。 */
    fun updateUserId(userId: String) {
        if (userId.isBlank()) return
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun clear() {
        // 只清登录凭据，保留 deviceId：退出登录后设备标识应当保持稳定，
        // 否则每次重新登录都像一台新设备，反而更容易被风控。
        prefs.edit()
            .remove(KEY_COOKIE)
            .remove(KEY_USER_ID)
            .apply()
        _loggedIn.value = false
    }

    /**
     * 稳定的匿名设备标识。
     *
     * 网易的 eapi 链路会校验请求里的 deviceId，同一台设备频繁更换会被判定为异常环境；
     * 因此这里生成一次后持久化，卸载重装才会变化。它不是硬件标识，也不含任何用户信息。
     */
    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = buildString(DEVICE_ID_LENGTH) {
            val alphabet = "0123456789ABCDEF"
            repeat(DEVICE_ID_LENGTH) { append(alphabet.random()) }
        }
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun readCookie(): String = runCatching {
        prefs.getString(KEY_COOKIE, "").orEmpty()
    }.getOrDefault("")

    private companion object {
        const val FILE_NAME = "melody_secure_session"
        const val KEY_COOKIE = "cookie"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val DEVICE_ID_LENGTH = 16
    }
}
