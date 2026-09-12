package com.wliky.melody.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * **同步是完全自动的后台行为，不在这里露面。** 页面只做两件事：
 * 列出本机历史、按需拉取云端记录。听歌记录的自动上报没有任何手动入口，
 * 想关掉就去「设置 → 播放」里的那个开关，所以这一页不摆同步状态卡。
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val local by viewModel.localHistory.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
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
