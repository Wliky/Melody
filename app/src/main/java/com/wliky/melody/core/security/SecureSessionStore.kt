package com.wliky.melody.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private val _loggedIn = MutableStateFlow(readCookie().isNotBlank())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** 完整 Cookie 串，用于直连模式请求头。 */
    fun cookie(): String = readCookie()

    fun userId(): String = prefs.getString(KEY_USER_ID, "").orEmpty()

    fun saveSession(cookie: String, userId: String) {
        prefs.edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_USER_ID, userId)
            .apply()
        _loggedIn.value = cookie.isNotBlank()
    }

    /** 登录态变化（例如接口返回 301 需要重新登录）时局部更新 cookie。 */
    fun updateCookie(cookie: String) {
        if (cookie.isBlank()) return
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
        _loggedIn.value = true
    }

    fun clear() {
        prefs.edit().clear().apply()
        _loggedIn.value = false
    }

    private fun readCookie(): String = runCatching {
        prefs.getString(KEY_COOKIE, "").orEmpty()
    }.getOrDefault("")

    private companion object {
        const val FILE_NAME = "melody_secure_session"
        const val KEY_COOKIE = "cookie"
        const val KEY_USER_ID = "user_id"
    }
}
