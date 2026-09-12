package com.wliky.melody.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.BuildConfig
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SettingItem
import com.wliky.melody.core.designsystem.component.SettingsGroup
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.model.UserProfile
import com.wliky.melody.feature.common.PlaylistCard

/**
 * 我的：账号卡片 → 快捷统计 → 最近播放（听歌足迹）→ 我的歌单 → 设置。
 * 未登录时用一张友好的引导卡替代，而不是空白页。
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isWide: Boolean,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val requiresLogin by viewModel.requiresLogin.collectAsStateWithLifecycle()

    // 登录态从「未登录」切到「已登录」时（例如从登录页返回），重新拉取用户数据。
    // 否则 ProfileViewModel 在首次进入页面时已经 init 过（当时未登录），
    // 登录成功后 pop 回来不会重新 init，profile / 歌单 / 足迹都是空的。
    LaunchedEffect(loggedIn) {
        if (loggedIn) viewModel.refresh()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            if (loggedIn) {
                ProfileHero(profile = state.profile, onLogout = viewModel::logout)
            } else {
                GuestHero(
                    description = if (requiresLogin) {
                        "登录后可以查看你的歌单、收藏与听歌足迹"
                    } else {
                        "当前是演示模式，无需登录即可体验全部界面"
                    },
                    onOpenLogin = onOpenLogin,
                    showAction = requiresLogin,
                )
            }
        }

        item {
            StatsRow(
                likedCount = state.likedSongCount,
                playlistCount = state.playlists.size,
            )
        }

        // 最近播放（听歌足迹）：官方账号最近听的歌
        item { SectionHeader(title = "最近播放", subtitle = "同步自你的网易云账号") }
        if (state.recentSongs.isEmpty()) {
            item {
                Text(
                    text = if (loggedIn) "还没有播放记录" else "登录后同步听歌足迹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                )
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(state.recentSongs, key = { it.id }) { song ->
                        RecentSongCard(
                            song = song,
                            onClick = { onPlay(state.recentSongs, state.recentSongs.indexOf(song)) },
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "我的歌单", subtitle = if (loggedIn) null else "登录后可见") }

        when {
            state.loading && state.playlists.isEmpty() -> item { LoadingBox() }

            state.playlists.isEmpty() -> item {
                Text(
                    text = "还没有歌单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                )
            }

            isWide -> item {
                PlaylistGridWide(playlists = state.playlists, onOpenPlaylist = onOpenPlaylist)
            }

            else -> item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            width = 148.dp,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        item {
            SettingsGroup {
                SettingItem(
                    title = "设置",
                    subtitle = "音质、主题、通知栏歌词与隐私说明",
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Melody v${BuildConfig.VERSION_NAME} · 开源许可 MIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 最近播放卡片（听歌足迹）。 */
@Composable
private fun RecentSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(132.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        CoverImage(
            url = song.coverUrl,
            seed = song.id,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.artistText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 已登录：大头像 + 昵称 + 等级，背景用一层主题色渐变。 */
@Composable
private fun ProfileHero(profile: UserProfile?, onLogout: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.26f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = profile?.avatarUrl,
            seed = profile?.userId.orEmpty(),
            modifier = Modifier
                .size(92.dp)
                .shadow(14.dp, CircleShape),
            corner = 46.dp,
            iconSize = 40.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = profile?.nickname ?: "网易云用户",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = profile?.signature?.takeIf { it.isNotBlank() }
                ?: if (profile == null) "昵称与头像加载中，稍后自动更新" else "欢迎回来",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (profile != null && profile.level > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lv.${profile.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "累计听歌 ${profile.listenSongs} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onLogout)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "退出登录",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 未登录：引导卡。 */
@Composable
private fun GuestHero(
    description: String,
    onOpenLogin: () -> Unit,
    showAction: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "未登录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showAction) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenLogin,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

/** 两张快捷统计卡。 */
@Composable
private fun StatsRow(
    likedCount: Int,
    playlistCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatCard(
            icon = Icons.Rounded.Favorite,
            title = "我喜欢的音乐",
            value = if (likedCount > 0) "$likedCount 首" else "—",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Rounded.History,
            title = "我的歌单",
            value = if (playlistCount > 0) "$playlistCount 个" else "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistGridWide(
    playlists: List<Playlist>,
    onOpenPlaylist: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        playlists.chunked(COLUMNS_WIDE).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS_WIDE - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COLUMNS_WIDE = 4
