package com.wliky.melody.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /**
     * 默认走**自建 API 服务**。
     *
     * 直连模式要自己实现 weapi / eapi 签名，网易一改风控就整条链路失效（扫码登录就是这么坏的）；
     * 自建服务是纯 HTTP，兼容性最好，出问题也能自己改服务端。地址见 [DEFAULT_API_BASE_URL]，
     * 用户可以在「设置 → 数据源」里改成自己部署的实例。
     */
    val apiMode: ApiMode = ApiMode.API_SERVER,
    /** 仅 [ApiMode.API_SERVER] 使用；留空则回落到 [DEFAULT_API_BASE_URL]。 */
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val audioQuality: AudioQuality = AudioQuality.EXHIGH,
    /**
     * 听歌记录自动同步。
     *
     * 默认开启：播放行为无需任何手动操作即自动入队并提交（见 SyncManager）。
     * 关闭它只会停止「上报」，本地播放历史始终照常记录。
     */
    val reportPlayback: Boolean = true,
)

/**
 * 默认的公共 API 服务地址（本项目作者部署的 api-enhanced 实例，见
 * https://github.com/Wliky/api-enhanced ）。它只做接口转发，不保存你的登录凭据；
 * 想用自己的服务把它换掉即可，扫码与 Cookie 登录都会走这里。
 */
const val DEFAULT_API_BASE_URL = "https://music.api.005201.xyz"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "melody_settings")

/**
 * 轻量配置存储（DataStore）。主题、接口模式、音质等。
 * 登录凭据不放这里，走 [com.wliky.melody.core.security.SecureSessionStore]。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val apiMode = stringPreferencesKey("api_mode")
        val apiBaseUrl = stringPreferencesKey("api_base_url")
        val audioQuality = stringPreferencesKey("audio_quality")
        val reportPlayback = booleanPreferencesKey("report_playback")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.themeMode].toEnum(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.dynamicColor] ?: true,
            apiMode = prefs[Keys.apiMode].toEnum(ApiMode.API_SERVER),
            apiBaseUrl = prefs[Keys.apiBaseUrl].orEmpty().trim().ifBlank { DEFAULT_API_BASE_URL },
            audioQuality = prefs[Keys.audioQuality].toEnum(AudioQuality.EXHIGH),
            reportPlayback = prefs[Keys.reportPlayback] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

    suspend fun setApiMode(mode: ApiMode) = edit { it[Keys.apiMode] = mode.name }

    suspend fun setApiBaseUrl(url: String) = edit { it[Keys.apiBaseUrl] = url.trim() }

    suspend fun setAudioQuality(quality: AudioQuality) = edit { it[Keys.audioQuality] = quality.name }

    suspend fun setReportPlayback(enabled: Boolean) = edit { it[Keys.reportPlayback] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        if (this == null) fallback else runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)
}
