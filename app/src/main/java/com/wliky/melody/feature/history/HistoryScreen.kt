package com.wliky.melody.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 播放历史。
 *
 * **同步是全自动的**：播放行为一产生就入队并在后台提交，这一页只是把状态如实摊开，
 * 没有任何「立即同步」按钮 —— 用户不需要为此做任何操作。
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
    val autoSyncActive by viewModel.autoSyncActive.collectAsStateWithLifecycle()
    val reportEnabled by viewModel.reportEnabled.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            AutoSyncCard(
                summary = summary,
                autoSyncActive = autoSyncActive,
                reportEnabled = reportEnabled,
                onOpenSettings = onOpenSettings,
            )
        }

        item {
            SectionHeader(
                title = "本地播放历史",
                subtitle = "共 ${local.size} 首 · 只存在本机",
                action = {
                    if (local.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearLocal) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
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
                SongRow(song = song, index = index, onClick = { onPlay(local, index) })
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader(title = "云端听歌记录", subtitle = "从你的账号读取，需要登录")
        }

        when {
            state.loadingRemote -> item { LoadingBox() }

            state.remoteError != null -> item {
                ErrorState(error = state.remoteError!!, onRetry = viewModel::loadRemote)
            }

            state.remoteSongs.isEmpty() -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    SettingItem(
                        title = "拉取云端记录",
                        subtitle = "点击从账号读取最近在听什么",
                        onClick = viewModel::loadRemote,
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }

            else -> itemsIndexed(
                state.remoteSongs,
                key = { index, song -> "remote-$index-${song.id}" },
            ) { index, song ->
                SongRow(song = song, index = index, onClick = { onPlay(state.remoteSongs, index) })
            }
        }
    }
}

/**
 * 自动同步状态卡。
 *
 * 刻意**不放任何操作按钮**：同步没有手动入口。这里只回答一个问题 ——
 * 「我的听歌记录现在是什么情况」。
 */
@Composable
private fun AutoSyncCard(
    summary: HistoryViewModel.SyncSummary,
    autoSyncActive: Boolean,
    reportEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val settled = summary.settled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpenSettings)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (settled) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "听歌记录自动同步",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            StatusDot(active = settled, accent = accent)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                !reportEnabled ->
                    "开关已关闭：播放记录照常保存在本机，只是不再上传。"

                !autoSyncActive ->
                    "已开启。当前数据源没有可用的上报通道，记录只保存在本机。"

                settled && summary.total == 0 -> "已开启，正在自动工作。播放任意歌曲后会自动记录并提交。"

                settled -> "已开启，队列已处理完毕（累计 ${summary.total} 条）。"

                else -> "已开启，正在后台处理：待同步 ${summary.pending} 条，失败待重试 ${summary.failed} 条。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (summary.total > 0) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SyncStat("待同步", summary.pending.toString())
                SyncStat("已失败", summary.failed.toString())
                SyncStat("已跳过", summary.skipped.toString())
                SyncStat("累计", summary.total.toString())
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "同步无需手动操作，网络恢复后会自动补交。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 小的状态圆点：一切处理完是绿色调，还在跑是主题色。 */
@Composable
private fun StatusDot(active: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) accent else MaterialTheme.colorScheme.outline),
    )
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
