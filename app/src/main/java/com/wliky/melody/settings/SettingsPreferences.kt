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
    EXHIGH("极高 (HQ)", "exhigh"),
    LOSSLESS("无损 (SQ)", "lossless"),
    HIRES("Hi-Res", "hires"),
}

/**
 * 迷你条显示模式：固定显示 / 完全隐藏 / 下滑隐藏（换歌自动回来）。
 */
enum class MiniBarMode(val label: String) {
    FIXED("固定"),
    HIDDEN("隐藏"),
    SWIPE_HIDE("滑动隐藏"),
}

/**
 * 设置项持久化（播放音质、歌词字号、倍速、外部歌词等）。主题仍走 [com.wliky.melody.ui.theme.ThemePreferences]。
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 播放音质，默认极高 (HQ)。 */
    val audioQuality: Flow<AudioQuality> =
        context.settingsDataStore.data.map { prefs ->
            AudioQuality.entries.firstOrNull { it.name == prefs[KEY_AUDIO_QUALITY] } ?: AudioQuality.EXHIGH
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

    /** 播放倍速，范围 [SPEED_MIN, SPEED_MAX]，默认 1x。 */
    val playbackSpeed: Flow<Float> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[KEY_PLAYBACK_SPEED] ?: SPEED_DEFAULT).coerceIn(SPEED_MIN, SPEED_MAX)
        }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PLAYBACK_SPEED] = speed.coerceIn(SPEED_MIN, SPEED_MAX)
        }
    }

    /** 车载蓝牙歌词：把当前歌词行写入媒体元数据，经 AVRCP 发送到车机。 */
    val carBluetoothLyricsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_CAR_BT_LYRICS] ?: false }

    suspend fun setCarBluetoothLyricsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_CAR_BT_LYRICS] = enabled }
    }

    /** SuperLyric：向系统级歌词接收端（Xposed 模块等）广播实时歌词。 */
    val superLyricEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_SUPER_LYRIC] ?: false }

    suspend fun setSuperLyricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SUPER_LYRIC] = enabled }
    }

    /** 迷你条显示模式，默认固定显示。 */
    val miniBarMode: Flow<MiniBarMode> =
        context.settingsDataStore.data.map { prefs ->
            MiniBarMode.entries.firstOrNull { it.name == prefs[KEY_MINI_BAR_MODE] } ?: MiniBarMode.FIXED
        }

    suspend fun setMiniBarMode(mode: MiniBarMode) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_MINI_BAR_MODE] = mode.name }
    }

    private companion object {
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val KEY_LYRIC_FONT_SIZE = floatPreferencesKey("lyric_font_size")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_CAR_BT_LYRICS = booleanPreferencesKey("car_bluetooth_lyrics_enabled")
        val KEY_SUPER_LYRIC = booleanPreferencesKey("super_lyric_enabled")
        val KEY_MINI_BAR_MODE = stringPreferencesKey("mini_bar_mode")

        const val LYRIC_FONT_MIN = 12f
        const val LYRIC_FONT_MAX = 24f
        const val LYRIC_FONT_DEFAULT = 20f

        const val SPEED_MIN = 0.5f
        const val SPEED_MAX = 3f
        const val SPEED_DEFAULT = 1f
    }
}
