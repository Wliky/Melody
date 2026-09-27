package com.wliky.melody.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 播放音质（网易云 /song/url/v1 的 level 参数）。
 * [label] 设置页显示名；[apiLevel] 接口取值。
 */
enum class AudioQuality(val label: String, val apiLevel: String) {
    STANDARD("标准", "standard"),
    HIGHER("高品", "higher"),
    LOSSLESS("无损", "lossless"),
    HIRES("Hi-Res", "hires"),
}

/**
 * 设置项持久化（通知栏歌词、播放音质、歌词字号等）。主题仍走 [com.wliky.melody.ui.theme.ThemePreferences]。
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 通知栏歌词开关。 */
    val notificationLyricsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_NOTIFICATION_LYRICS] ?: false }

    suspend fun setNotificationLyricsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_NOTIFICATION_LYRICS] = enabled }
    }

    /** 播放音质，默认高品。 */
    val audioQuality: Flow<AudioQuality> =
        context.settingsDataStore.data.map { prefs ->
            AudioQuality.entries.firstOrNull { it.name == prefs[KEY_AUDIO_QUALITY] } ?: AudioQuality.HIGHER
        }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AUDIO_QUALITY] = quality.name }
    }

    /** 歌词字号（sp），范围 [LYRIC_FONT_MIN, LYRIC_FONT_MAX]，默认 20。 */
    val lyricFontSize: Flow<Float> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[KEY_LYRIC_FONT_SIZE] ?: LYRIC_FONT_DEFAULT)
                .coerceIn(LYRIC_FONT_MIN, LYRIC_FONT_MAX)
        }

    suspend fun setLyricFontSize(sizeSp: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LYRIC_FONT_SIZE] = sizeSp.coerceIn(LYRIC_FONT_MIN, LYRIC_FONT_MAX)
        }
    }

    private companion object {
        val KEY_NOTIFICATION_LYRICS = booleanPreferencesKey("notification_lyrics_enabled")
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val KEY_LYRIC_FONT_SIZE = floatPreferencesKey("lyric_font_size")

        const val LYRIC_FONT_MIN = 10f
        const val LYRIC_FONT_MAX = 20f
        const val LYRIC_FONT_DEFAULT = 20f
    }
}
