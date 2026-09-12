package com.wliky.melody.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.BuildConfig
import com.wliky.melody.core.datastore.AppSettings
import com.wliky.melody.core.datastore.ThemeMode
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SettingItem
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality

/**
 * 设置（文档 §5 / §6 / §16）。
 * 数据源可切换、音质可选、主题可调，隐私与合规说明直接摆在界面里。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val reportSupported by viewModel.reportSupported.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { SectionHeader(title = "数据源", subtitle = "接口变化时可随时切换，不必等 App 更新") }

        item {
            ApiModeSection(
                settings = settings,
                onSelect = viewModel::setApiMode,
                onBaseUrlChange = viewModel::setApiBaseUrl,
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(title = "播放") }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = "音质", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "实际可用音质取决于你的账号权限与歌曲本身",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = settings.audioQuality == quality,
                            onClick = { viewModel.setAudioQuality(quality) },
                            label = { Text(quality.label) },
                        )
                    }
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionHeader(title = "外观") }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = "主题", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item {
            SettingItem(
                title = "动态取色",
                subtitle = "Android 12+ 从壁纸提取主色，关闭则使用 Melody 品牌色",
                onClick = { viewModel.setDynamicColor(!settings.dynamicColor) },
                trailing = {
                    Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                },
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(title = "数据与隐私") }

        item {
            SettingItem(
                title = "上报播放记录（实验性）",
                subtitle = when {
                    !reportSupported -> "当前数据源没有提供明确可控的上报接口，因此该开关不可用"
                    settings.reportPlayback -> "已开启：网络恢复后会批量提交播放记录"
                    else -> "默认关闭。播放记录只保存在本机"
                },
                onClick = { if (reportSupported) viewModel.setReportPlayback(!settings.reportPlayback) },
                trailing = {
                    Switch(
                        checked = settings.reportPlayback && reportSupported,
                        enabled = reportSupported,
                        onCheckedChange = { viewModel.setReportPlayback(it) },
                    )
                },
            )
        }

        item {
            SettingItem(
                title = "清理缓存",
                subtitle = "清理歌词等内存缓存（音频文件不做缓存）",
                onClick = viewModel::clearCaches,
            )
        }

        item {
            SettingItem(
                title = "清空同步队列",
                subtitle = "删除本地待同步的播放事件",
                onClick = viewModel::clearSyncQueue,
            )
        }

        if (loggedIn) {
            item {
                SettingItem(
                    title = "退出登录",
                    subtitle = "清除本机保存的登录凭据",
                    onClick = viewModel::logout,
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionHeader(title = "关于") }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Melody v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = COMPLIANCE_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "开源协议：MIT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApiModeSection(
    settings: AppSettings,
    onSelect: (ApiMode) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    var baseUrlDraft by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApiMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.apiMode == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = settings.apiMode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (settings.apiMode == ApiMode.API_SERVER) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = { baseUrlDraft = it },
                singleLine = true,
                label = { Text("服务地址") },
                placeholder = { Text("http://192.168.1.10:3000") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onBaseUrlChange(baseUrlDraft) }) { Text("保存地址") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private const val COMPLIANCE_TEXT =
    "Melody 是非官方开源第三方客户端，与网易云音乐官方无任何关联。\n" +
        "本应用不破解会员、不绕过 DRM、不提供未授权下载，也不规避任何访问控制；" +
        "只调用你本人有权访问的数据与音源，音源地址仅用于在线播放，不做本地留存。\n" +
        "登录凭据保存在 Android Keystore 保护的存储中，不会写入日志，也不会离开你的设备。"
