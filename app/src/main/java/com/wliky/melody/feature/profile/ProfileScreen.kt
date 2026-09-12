package com.wliky.melody.feature.profile

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SettingItem
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.feature.common.PlaylistCard

/**
 * 我的（文档 §4）：账号信息 → 我的歌单 → 收藏 → 播放历史 → 设置。
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isWide: Boolean,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val requiresLogin by viewModel.requiresLogin.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            if (loggedIn) {
                ProfileHeader(profile = state.profile, onLogout = viewModel::logout)
            } else {
                LoginPrompt(
                    description = if (requiresLogin) {
                        "扫码登录后可以查看你的歌单、收藏与播放历史"
                    } else {
                        "当前是演示模式，无需登录即可体验全部界面"
                    },
                    onOpenLogin = onOpenLogin,
                    showAction = requiresLogin,
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                QuickEntry(
                    title = "我喜欢的音乐",
                    value = if (state.likedSongCount > 0) "${state.likedSongCount} 首" else "—",
                    icon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                )
                QuickEntry(
                    title = "播放历史",
                    value = "本地记录",
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader(title = "我的歌单", subtitle = if (loggedIn) null else "登录后可见") }

        if (state.loading && state.playlists.isEmpty()) {
            item { LoadingBox() }
        } else if (state.playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有歌单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isWide) {
            item { PlaylistWideSection(playlists = state.playlists, onOpenPlaylist = onOpenPlaylist) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            width = 150.dp,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            SettingItem(
                title = "播放历史与同步",
                subtitle = "查看本地历史、同步队列状态",
                onClick = onOpenHistory,
                trailing = { Icon(Icons.Rounded.History, contentDescription = null) },
            )
            SettingItem(
                title = "设置",
                subtitle = "数据源、音质、主题与隐私合规说明",
                onClick = onOpenSettings,
                trailing = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile?, onLogout: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                url = profile?.avatarUrl,
                seed = profile?.userId.orEmpty(),
                modifier = Modifier.size(64.dp),
                corner = 32.dp,
                iconSize = 28.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.nickname ?: "已登录",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile?.signature?.takeIf { it.isNotBlank() } ?: "欢迎回来",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onLogout) {
                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("退出")
            }
        }
        if (profile != null && profile.level > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Lv.${profile.level} · 累计听歌 ${profile.listenSongs} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginPrompt(
    description: String,
    onOpenLogin: () -> Unit,
    showAction: Boolean,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "未登录", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showAction) {
                TextButton(
                    onClick = onOpenLogin,
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("扫码登录")
                }
            }
        }
    }
}

@Composable
private fun QuickEntry(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            icon()
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistWideSection(
    playlists: List<Playlist>,
    onOpenPlaylist: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        playlists.chunked(COLUMNS).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COLUMNS = 4
