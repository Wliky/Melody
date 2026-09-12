package com.wliky.melody.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.datastore.AppSettings
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.datastore.ThemeMode
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.data.repository.AuthRepository
import com.wliky.melody.data.repository.MusicRepository
import com.wliky.melody.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val loggedIn: StateFlow<Boolean> = authRepository.loggedIn

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前数据源是否支持播放记录上报（用于把开关的可用性说清楚）。 */
    private val _reportSupported = MutableStateFlow(false)
    val reportSupported: StateFlow<Boolean> = _reportSupported.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = launchSetting { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launchSetting { settingsRepository.setDynamicColor(enabled) }

    fun setApiMode(mode: ApiMode) = launchSetting { settingsRepository.setApiMode(mode) }

    fun setApiBaseUrl(url: String) = launchSetting { settingsRepository.setApiBaseUrl(url) }

    fun setAudioQuality(quality: AudioQuality) = launchSetting { settingsRepository.setAudioQuality(quality) }

    fun setReportPlayback(enabled: Boolean) = launchSetting { settingsRepository.setReportPlayback(enabled) }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _message.value = "已退出登录"
        }
    }

    /** 清理内存缓存（歌词等）。音频文件本来就不缓存，遵守版权限制。 */
    fun clearCaches() {
        musicRepository.clearCache()
        _message.value = "已清理缓存"
    }

    fun clearSyncQueue() {
        viewModelScope.launch {
            syncRepository.clear()
            _message.value = "已清空同步队列"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            // 目前只有「自建 API 服务」模式提供了明确、可控的上报接口。
            // 直连模式下不硬编码不可控的私有接口（见文档 §9）。
            _reportSupported.value = settings.value.apiMode == ApiMode.API_SERVER
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refreshCapabilities()
        }
    }

    init {
        refreshCapabilities()
    }
}
