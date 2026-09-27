package com.wliky.melody.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 歌词管理：通知栏歌词开关、歌词字号（持久化）。 */
@HiltViewModel
class LyricsSettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    val notificationLyricsEnabled: StateFlow<Boolean> =
        settingsPreferences.notificationLyricsEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setNotificationLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setNotificationLyricsEnabled(enabled)
        }
    }

    /** 歌词字号（sp，10-20）：滑条实时写入，播放页歌词实时生效。 */
    val lyricFontSize: StateFlow<Float> = settingsPreferences.lyricFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20f)

    fun setLyricFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsPreferences.setLyricFontSize(sizeSp) }
    }
}

/** 歌词管理二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    viewModel: LyricsSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.notificationLyricsEnabled.collectAsState()
    val fontSize by viewModel.lyricFontSize.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌词管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 通知栏歌词开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null)
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text("通知栏歌词", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "播放时把通知媒体控件上的歌名替换为当前歌词行（网易云式）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = viewModel::setNotificationLyricsEnabled,
                )
            }

            // 使用说明
            Text(
                text = "开启后：播放歌曲时，系统媒体控件通知的标题会跟随播放进度" +
                    "滚动显示当前歌词行（歌手信息不变）；暂停时停留在最后一行，" +
                    "切歌或关闭开关自动恢复为歌名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 歌词大小：10-20sp 实时调节，拖动即时生效于播放页歌词
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FormatSize, contentDescription = null)
                Text(
                    text = "歌词大小",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                )
                Text(
                    text = "${fontSize.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp + 12.dp, vertical = 4.dp),
                ) {
                    Slider(
                        value = fontSize,
                        onValueChange = viewModel::setLyricFontSize,
                        valueRange = 10f..20f,
                        steps = 9,
                    )
                    Text(
                        text = "播放页歌词字号 · 范围 10 - 20 · 拖动实时生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
