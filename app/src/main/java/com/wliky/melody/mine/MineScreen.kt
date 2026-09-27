package com.wliky.melody.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.EmptyState
import com.wliky.melody.ui.components.MelodyButton
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.theme.Spacing

/**
 * 「我的」页：账号信息 + 我的歌单（网易云官方「我的」Tab 的最小形态）。
 */
@Composable
fun MineScreen(
    onLogout: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MineViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置")
            }
        }

        when {
            state.loading -> MineLoading()
            state.user == null -> NotLoggedInSection(onLogout)
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.load(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                MineContent(
                    state = state,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenProfile = onOpenProfile,
                    onOpenCloud = onOpenCloud,
                )
            }
        }
    }
}

@Composable
private fun MineLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        )
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        repeat(5) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun NotLoggedInSection(onBackToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        EmptyState(text = "未登录", hint = "登录后查看你的歌单")
        MelodyButton(text = "去登录", onClick = onBackToLogin)
    }
}

@Composable
private fun MineContent(
    state: MineViewModel.MineUiState,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            UserProfile(
                state = state,
                onOpenProfile = onOpenProfile,
            )
        }
        item {
            MusicLibraryEntries(
                onOpenCloud = onOpenCloud,
                onOpenProfile = onOpenProfile,
            )
        }
        item {
            Text(
                text = "我的歌单（${state.playlists.size}）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        items(state.playlists, key = { "mine-${it.id}" }) { playlist ->
            PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun UserProfile(
    state: MineViewModel.MineUiState,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = state.user?.avatarUrl,
            contentDescription = state.user?.nickname,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenProfile),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.clickable(onClick = onOpenProfile)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.user?.nickname ?: "未知用户",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "ID ${state.user?.id ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 音乐库入口：云盘音乐 + 听歌排行（个人主页）。 */
@Composable
private fun MusicLibraryEntries(
    onOpenCloud: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MusicEntryCard(
            icon = Icons.Rounded.CloudQueue,
            title = "云盘音乐",
            hint = "上传的音乐",
            modifier = Modifier.weight(1f),
            onClick = onOpenCloud,
        )
        MusicEntryCard(
            icon = Icons.Rounded.BarChart,
            title = "听歌排行",
            hint = "等级 · 听歌数据",
            modifier = Modifier.weight(1f),
            onClick = onOpenProfile,
        )
    }
}

@Composable
private fun MusicEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} 首 · 播放 ${formatCount(playlist.playCount)} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatCount(count: Long): String {
    val wan = count / 10_000.0
    val yi = count / 100_000_000.0
    return when {
        count >= 100_000_000 ->
            if (yi % 1.0 == 0.0) "${yi.toInt()}亿" else "%.1f亿".format(yi)
        count >= 10_000 ->
            if (wan % 1.0 == 0.0) "${wan.toInt()}万" else "%.1f万".format(wan)
        else -> count.toString()
    }
}
