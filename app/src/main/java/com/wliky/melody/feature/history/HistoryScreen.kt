package com.wliky.melody.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SettingItem
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.model.Song

/**
 * 播放历史 + 同步队列（文档 §6 / §9）。
 * 本地历史离线可看；同步队列把「待同步 / 失败 / 跳过」摊开给用户看，不藏着。
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val local by viewModel.localHistory.collectAsStateWithLifecycle()
    val summary by viewModel.syncSummary.collectAsStateWithLifecycle()
    val reportEnabled by viewModel.reportEnabled.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            SyncPanel(
                pending = summary.pending,
                failed = summary.failed,
                skipped = summary.skipped,
                total = summary.total,
                reportEnabled = reportEnabled,
                syncing = state.syncing,
                onSync = viewModel::syncNow,
                onOpenSettings = onOpenSettings,
            )
        }

        if (state.lastReport != null) {
            val report = state.lastReport!!
            item {
                Text(
                    text = when {
                        report.nothingToDo -> "队列里没有待同步的事件"
                        report.synced > 0 -> "已同步 ${report.synced} 条播放记录"
                        report.skipped > 0 -> "当前接口不支持上报，已跳过 ${report.skipped} 条"
                        else -> "同步失败 ${report.failed} 条，稍后自动重试"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        item { HorizontalDivider() }
        item {
            SectionHeader(
                title = "本地播放历史",
                subtitle = "共 ${local.size} 首 · 只存在本机",
                action = {
                    if (local.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearLocal) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                },
            )
        }

        if (local.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有播放记录",
                    description = "播放任意歌曲后会自动记录",
                    icon = Icons.Rounded.History,
                )
            }
        } else {
            itemsIndexed(local, key = { index, song -> "local-$index-${song.id}" }) { index, song ->
                SongRow(song = song, onClick = { onPlay(local, index) })
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            SectionHeader(title = "云端听歌记录", subtitle = "需要登录后拉取")
        }

        when {
            state.loadingRemote -> item { LoadingBox() }
            state.remoteError != null -> item {
                ErrorState(error = state.remoteError!!, onRetry = viewModel::loadRemote)
            }
            state.remoteSongs.isEmpty() -> item {
                SettingItem(
                    title = "拉取云端记录",
                    subtitle = "从你的账号读取最近的听歌记录",
                    onClick = viewModel::loadRemote,
                    trailing = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                )
            }
            else -> itemsIndexed(state.remoteSongs, key = { index, song -> "remote-$index-${song.id}" }) { index, song ->
                SongRow(song = song, onClick = { onPlay(state.remoteSongs, index) })
            }
        }
    }
}

@Composable
private fun SyncPanel(
    pending: Int,
    failed: Int,
    skipped: Int,
    total: Int,
    reportEnabled: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = "播放记录同步队列", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSync) { Text("立即同步") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SyncStat("待同步", pending.toString())
                SyncStat("已失败", failed.toString())
                SyncStat("已跳过", skipped.toString())
                SyncStat("累计", total.toString())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (reportEnabled) {
                    "上报开关已打开，网络恢复后会自动批量提交。"
                } else {
                    "上报开关默认关闭。当前仅保存在本机，不会上传任何数据。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!reportEnabled) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "去设置里了解并开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                TextButton(onClick = onOpenSettings) { Text("打开设置") }
            }
        }
    }
}

@Composable
private fun SyncStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
