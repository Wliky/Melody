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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.BuildConfig
import com.wliky.melody.core.datastore.ThemeMode
import com.wliky.melody.core.designsystem.component.SettingItem
import com.wliky.melody.core.designsystem.component.SettingsGroup
import com.wliky.melody.core.model.AudioQuality

/**
 * 设置。所有选项都收在分组卡片里，不再是一长条分割线列表。
 *
 * 关于同步：听歌记录的同步是**后台自动行为**，界面上只留一个「同步听歌记录」开关，
 * 不摆状态、不摆统计、更没有任何手动触发按钮 —— 播放行为一产生就会自动入队并提交
 * （见 SyncManager）。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            SettingsGroup(title = "账号") {
                if (loggedIn) {
                    SettingItem(
                        title = "已登录网易云音乐",
                        subtitle = "登录态由 MUSIC_U 维护，凭据加密保存在本机",
                        icon = Icons.Rounded.Person,
                    )
                    SettingItem(
                        title = "退出登录",
                        subtitle = "清除本机保存的登录凭据",
                        icon = Icons.Rounded.Logout,
                        onClick = viewModel::logout,
                    )
                } else {
                    SettingItem(
                        title = "登录网易云音乐",
                        subtitle = "登录后才能使用播放、歌单、收藏与听歌记录同步",
                        icon = Icons.Rounded.Login,
                        onClick = onOpenLogin,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "播放音质") {
                Column(modifier = Modifier.padding(16.dp)) {
                    ChoiceChips(
                        options = AudioQuality.entries.map { it to it.label },
                        selectedIndex = AudioQuality.entries.indexOf(settings.audioQuality),
                        onSelect = { viewModel.setAudioQuality(AudioQuality.entries[it]) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "实际可用音质取决于你的账号权限与歌曲本身",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "外观") {
                Column(modifier = Modifier.padding(16.dp)) {
                    ChoiceChips(
                        options = ThemeMode.entries.map { it to it.label },
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                    )
                }
                SettingItem(
                    title = "动态取色",
                    subtitle = "Android 12+ 从壁纸提取主色；播放页始终跟随专辑封面",
                    icon = Icons.Rounded.Palette,
                    onClick = { viewModel.setDynamicColor(!settings.dynamicColor) },
                    trailing = {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }
        }

        item {
            SettingsGroup(title = "播放与记录") {
                SettingItem(
                    title = "同步听歌记录",
                    subtitle = autoSyncDescription(settings.reportPlayback),
                    icon = Icons.Rounded.CloudSync,
                    onClick = { viewModel.setReportPlayback(!settings.reportPlayback) },
                    trailing = {
                        Switch(
                            checked = settings.reportPlayback,
                            onCheckedChange = viewModel::setReportPlayback,
                        )
                    },
                )
                SettingItem(
                    title = "清理缓存",
                    subtitle = "清理歌词等内存缓存（音频文件不做缓存）",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = viewModel::clearCaches,
                )
            }
        }

        item {
            SettingsGroup(title = "关于") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Melody v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "开源协议：MIT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = COMPLIANCE_TEXT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}

/**
 * 「自动同步」的真实状态说明。
 *
 * 同步是听歌后自动记录并提交的（无手动入口），这里只如实告知开关的含义：
 * 数据源固定为自建 API 服务，只有它提供上报通道。
 */
private fun autoSyncDescription(reportEnabled: Boolean): String =
    if (reportEnabled) {
        "已开启：播放后自动提交，无需任何手动操作；关掉只是停止上传，本地记录不受影响"
    } else {
        "已关闭：播放记录仍然照常保存在本机，只是不再上传"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<Pair<Any, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (_, label) ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

/** 隐私与合规说明：直接摆在界面里，而不是藏在某个网页后面。 */
private const val COMPLIANCE_TEXT =
    "Melody 是非官方开源第三方客户端，与网易云音乐官方无任何关联。\n" +
        "本应用不破解会员、不绕过 DRM、不提供未授权下载，也不规避任何访问控制；" +
        "只调用你本人有权访问的数据与音源，音源地址仅用于在线播放，不做本地留存。\n" +
        "登录凭据保存在 Android Keystore 保护的存储中，不会写入日志，也不会离开你的设备。"
