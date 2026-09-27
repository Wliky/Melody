package com.wliky.melody.playlist

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.wliky.melody.comment.CommentSheet
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.player.AddToPlaylistSheet
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.ErrorState
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.util.formatCount
import com.wliky.melody.ui.util.formatDuration

/**
 * 歌单详情页：顶部信息区（大封面 + 标题/创建者/标签/统计/简介展开）
 * + 操作栏（播放全部/收藏/下载/评论/分享/多选）+ 完整曲目列表。
 * 多选模式下曲目行切换为选择框，顶栏变为「已选 n / 全选 / 播放所选 / 取消」。
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBack: () -> Unit,
    onCollectSongs: (List<Long>) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(key = "playlist-${playlist.id}"),
) {
    LaunchedEffect(playlist.id) { viewModel.start(playlist) }

    val state = viewModel.uiState
    val queueState = viewModel.playerQueue.state
    val detail = state.playlist ?: playlist
    var showComments by remember { mutableStateOf(false) }
    // 长按目标歌曲（收藏到歌单弹层）
    var songActionFor by remember { mutableStateOf<List<Long>?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // 二级页避让挖孔屏 / 状态栏
            .statusBarsPadding(),
    ) {
        // 顶栏：普通态（返回 + 歌单）/ 多选态（已选 n / 全选 / 播放所选 / 取消）
        if (viewModel.selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::exitSelectionMode) { Text("取消") }
                Text(
                    text = "已选 ${viewModel.selectedIndexes.size} 首",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = viewModel::selectAll) { Text("全选") }
                TextButton(onClick = viewModel::playSelected) { Text("播放所选") }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "歌单",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        when {
            state.loading -> DetailLoading()

            state.error != null && state.songs.isEmpty() -> ErrorState(
                text = state.error,
                onRetry = { viewModel.start(playlist) },
                modifier = Modifier.fillMaxSize(),
            )

            else -> Column(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        bottom = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    item { PlaylistHeaderInfo(playlist = detail) }
                    item {
                        PlaylistActionBar(
                            songCount = viewModel.displaySongs.size,
                            subscribed = detail.subscribed,
                            onPlayAll = { viewModel.playSong(0) },
                            onSubscribe = viewModel::toggleSubscribe,
                            onComment = { showComments = true },
                            onShare = {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "「${detail.name}」- 来自 Melody 的歌单分享\n" +
                                                        "https://music.163.com/playlist?id=${detail.id}",
                                                )
                                            },
                                            "分享歌单",
                                        ),
                                    )
                                }
                            },
                            onSelect = { viewModel.enterSelectionMode(0) },
                        )
                    }
                    itemsIndexed(viewModel.displaySongs, key = { _, s -> s.id }) { index, song ->
                        val playing = queueState.current?.id == song.id
                        val selected = index in viewModel.selectedIndexes
                        SongRow(
                            title = song.name,
                            subtitle = song.subtitle,
                            artworkUrl = null,
                            highlight = playing,
                            onClick = {
                                if (viewModel.selectionMode) {
                                    viewModel.toggleSelected(index)
                                } else {
                                    viewModel.playSong(index)
                                }
                            },
                            onLongClick = if (viewModel.selectionMode) {
                                null
                            } else {
                                { songActionFor = listOf(song.id) }
                            },
                            leading = {
                                if (viewModel.selectionMode) {
                                    Icon(
                                        imageVector = if (selected) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.RadioButtonUnchecked
                                        },
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(MelodySize.iconM),
                                    )
                                } else {
                                    Text(
                                        text = if (playing) "♪" else "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (playing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.width(32.dp),
                                        maxLines = 1,
                                    )
                                }
                            },
                            trailing = {
                                Text(
                                    text = formatDuration(song.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }

                // 多选模式底部操作栏：收藏到歌单 / 删除
                if (viewModel.selectionMode) {
                    SelectionBottomBar(
                        enabled = viewModel.selectedIndexes.isNotEmpty(),
                        onCollect = {
                            onCollectSongs(
                                viewModel.selectedSongs().map { it.id },
                            )
                        },
                        onDelete = viewModel::removeSelected,
                    )
                }
            }
        }
    }

    // 歌单评论底栏
    if (showComments) {
        CommentSheet(
            playlistId = playlist.id,
            playlistName = playlist.name,
            onDismiss = { showComments = false },
        )
    }

    // 长按歌曲 → 收藏到歌单
    songActionFor?.let { ids ->
        AddToPlaylistSheet(
            songIds = ids,
            onDismiss = { songActionFor = null },
        )
    }
}

@Composable
private fun DetailLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        repeat(8) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
    }
}

/**
 * 顶部信息区：左侧大封面（阴影/圆角）；
 * 右侧 歌单标题 / 创建者头像+昵称 / 标签 / 播放量·评论数 / 简介展开。
 */
@Composable
private fun PlaylistHeaderInfo(playlist: Playlist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.sm),
    ) {
        CoverImage(
            url = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(124.dp)
                .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium, clip = false),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            // 标题
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            // 创建者：头像 + 昵称
            Row(verticalAlignment = Alignment.CenterVertically) {
                playlist.creatorAvatarUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                Text(
                    text = playlist.creatorName ?: "未知创建者",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 标签
            if (playlist.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    playlist.tags.take(3).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            // 播放量 / 评论数
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = buildString {
                    append("播放 ${formatCount(playlist.playCount)}")
                    if (playlist.commentCount > 0) append(" · 评论 ${formatCount(playlist.commentCount)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 简介（可展开）
            if (!playlist.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                var expanded by remember(playlist.id) { mutableStateOf(false) }
                Column {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (expanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { expanded = !expanded },
                    )
                }
            }
        }
    }
}

/** 操作栏：播放全部（主按钮）+ 收藏 / 评论 / 分享 / 多选（下载功能未落地，暂不展示）。 */
@Composable
private fun PlaylistActionBar(
    songCount: Int,
    subscribed: Boolean,
    onPlayAll: () -> Unit,
    onSubscribe: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 播放全部（主按钮，胶囊造型；CircleShape 自适应任意高度始终全圆角）
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.clickable(onClick = onPlayAll),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MelodySize.iconS),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "播放全部($songCount)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        ActionBarIcon(
            icon = if (subscribed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = if (subscribed) "已收藏" else "收藏",
            tint = if (subscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onSubscribe,
        )
        ActionBarIcon(
            icon = Icons.Rounded.Comment,
            label = "评论",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onComment,
        )
        ActionBarIcon(
            icon = Icons.Rounded.Share,
            label = "分享",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onShare,
        )
        ActionBarIcon(
            icon = Icons.Rounded.Checklist,
            label = "多选",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onSelect,
        )
    }
}

/** 多选模式底部操作栏：收藏到歌单 / 删除。 */
@Composable
private fun SelectionBottomBar(
    enabled: Boolean,
    onCollect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 收藏到歌单
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = enabled, onClick = onCollect)
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MelodySize.iconS),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "收藏到歌单",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // 从歌单删除
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = enabled, onClick = onDelete)
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(MelodySize.iconS),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ActionBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(MelodySize.iconS),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}