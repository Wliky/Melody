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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Speed
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.wliky.melody.ui.theme.Spacing
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 歌词管理：外部歌词（SuperLyric / 车载蓝牙）、歌词字号、播放倍速（持久化）。 */
@HiltViewModel
class LyricsSettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    /** 歌词字号（sp，12-24，默认 20）：滑条实时写入，播放页歌词实时生效。 */
    val lyricFontSize: StateFlow<Float> = settingsPreferences.lyricFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20f)

    fun setLyricFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsPreferences.setLyricFontSize(sizeSp) }
    }

    /** 播放倍速（0.5x-3.0x，步进 0.05）：与播放页倍速面板同一份数据。 */
    val playbackSpeed: StateFlow<Float> = settingsPreferences.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsPreferences.setPlaybackSpeed(speed) }
    }

    /** SuperLyric：向系统级歌词接收端（Xposed 模块）广播实时歌词。 */
    val superLyricEnabled: StateFlow<Boolean> = settingsPreferences.superLyricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSuperLyricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setSuperLyricEnabled(enabled) }
    }

    /** 车载蓝牙歌词：歌词行经 AVRCP 同步到车机屏幕。 */
    val carBluetoothLyricsEnabled: StateFlow<Boolean> = settingsPreferences.carBluetoothLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setCarBluetoothLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setCarBluetoothLyricsEnabled(enabled) }
    }
}

/** 歌词管理二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    viewModel: LyricsSettingsViewModel = hiltViewModel(),
) {
    val fontSize by viewModel.lyricFontSize.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val superLyric by viewModel.superLyricEnabled.collectAsState()
    val carBt by viewModel.carBluetoothLyricsEnabled.collectAsState()

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
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 歌词大小：12-24sp 实时调节，拖动即时生效于播放页歌词
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
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 比设置标题多缩进 12dp（层级缩进，非档位值）
                        .padding(horizontal = Spacing.md + 12.dp, vertical = Spacing.xs),
                ) {
                    Slider(
                        value = fontSize,
                        onValueChange = viewModel::setLyricFontSize,
                        valueRange = 12f..24f,
                        steps = 11,
                    )
                    Text(
                        text = "播放页歌词字号 · 范围 12 - 24 · 拖动实时生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            // 播放倍速：0.5x-3.0x 连续调节，播放页倍速面板同步
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null)
                Text(
                    text = "播放倍速",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                )
                Text(
                    text = String.format("%.2fx", speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 比设置标题多缩进 12dp（层级缩进，非档位值）
                        .padding(horizontal = Spacing.md + 12.dp, vertical = Spacing.xs),
                ) {
                    Slider(
                        value = speed,
                        onValueChange = viewModel::setPlaybackSpeed,
                        valueRange = 0.5f..3f,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { preset ->
                            Text(
                                text = if (preset == 1f) "1x" else
                                    if (preset % 1f == 0f) "${preset.toInt()}x" else "${preset}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (kotlin.math.abs(speed - preset) < 0.026f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // SuperLyric 系统级歌词
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null)
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text("SuperLyric 系统歌词", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "接入 SuperLyricApi 协议，向系统级歌词接收端（如 Xposed 歌词模块）广播实时歌词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = superLyric,
                    onCheckedChange = viewModel::setSuperLyricEnabled,
                )
            }

            // 车载蓝牙歌词
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text("车载蓝牙歌词", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "把当前歌词行写入蓝牙媒体信息（AVRCP），车机屏幕随播放滚动显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = carBt,
                    onCheckedChange = viewModel::setCarBluetoothLyricsEnabled,
                )
            }

            Text(
                text = "两个外部歌词通道独立开关：SuperLyric 面向已安装接收端（Xposed 模块等），" +
                    "车载蓝牙歌词面向所有支持 AVRCP 的车机/蓝牙设备；均不影响通知栏显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
