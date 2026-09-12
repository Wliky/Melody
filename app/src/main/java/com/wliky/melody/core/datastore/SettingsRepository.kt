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
    val apiMode: ApiMode = ApiMode.DIRECT,
    /** 仅 [ApiMode.API_SERVER] 使用，例如 http://192.168.1.10:3000 */
    val apiBaseUrl: String = "",
    val audioQuality: AudioQuality = AudioQuality.EXHIGH,
    /** 播放记录上报（实验性，默认关闭）。见文档 §9 / §16。 */
    val reportPlayback: Boolean = false,
)

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
            apiMode = prefs[Keys.apiMode].toEnum(ApiMode.DIRECT),
            apiBaseUrl = prefs[Keys.apiBaseUrl].orEmpty(),
            audioQuality = prefs[Keys.audioQuality].toEnum(AudioQuality.EXHIGH),
            reportPlayback = prefs[Keys.reportPlayback] ?: false,
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
