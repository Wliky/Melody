package com.wliky.melody.comment

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Comment
import com.wliky.melody.data.repo.CommentSort
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.theme.Spacing

/**
 * 评论底栏（当前播放歌曲 / 歌单），只读浏览。
 *
 * 高 80% 屏幕：排序页签（最热/最新）+ 评论列表 + 加载更多。
 * 点赞/回复/发表等写操作因服务端设备风控暂缓，见 [com.wliky.melody.data.repo.CommentRepository]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    songId: Long = 0L,
    songName: String? = null,
    playlistId: Long = 0L,
    playlistName: String? = null,
    onDismiss: () -> Unit,
    viewModel: CommentViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    LaunchedEffect(songId, playlistId) {
        if (playlistId > 0L) {
            viewModel.loadPlaylistComments(playlistId, playlistName)
        } else {
            viewModel.loadSongComments(songId, songName)
        }
    }

    // 面板高度按屏幕比例固定，否则 ModalBottomSheet 会按内容撑开，
    // weight(1f) 分不到剩余空间导致列表无法滚动
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val panelHeight = screenHeight * 0.9f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(panelHeight)) {
            // 标题行 + 排序页签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "评论 (${state.total})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = playlistName ?: songName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CommentSortChips(
                    selected = state.sort,
                    onSelect = viewModel::switchSort,
                )
            }

            // 列表
            CommentListBody(
                state = state,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** 排序页签（最热/最新）：评论面板与播放页评论页共用。 */
@Composable
fun CommentSortChips(
    selected: CommentSort,
    onSelect: (CommentSort) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = selected == CommentSort.HOT,
            onClick = { onSelect(CommentSort.HOT) },
            label = { Text("最热") },
        )
        FilterChip(
            selected = selected == CommentSort.LATEST,
            onClick = { onSelect(CommentSort.LATEST) },
            label = { Text("最新") },
        )
    }
}

/**
 * 评论列表主体（加载中/失败/空/列表 + 加载更多）。
 * 头部（标题、排序页签）由调用方排布；评论面板与播放页评论页共用。
 */
@Composable
fun CommentListBody(
    state: CommentViewModel.CommentUiState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "评论加载失败：${state.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.comments.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有评论，来抢沙发吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    // 底部留足余量：末条评论 / 加载更多行不贴边裁半
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.comments, key = { "c-${it.id}" }) { item ->
                    CommentItem(comment = item)
                }
                if (state.hasMore) {
                    item(key = "load-more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                TextButton(onClick = onLoadMore) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
                item(key = "bottom-space") { Spacer(modifier = Modifier.height(Spacing.xl)) }
            }
        }
    }
}

/** 一条评论（头像 / 昵称 / 内容 / 时间 / 点赞数，纯展示）。 */
@Composable
private fun CommentItem(comment: Comment) {
    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = comment.userAvatarUrl,
            contentDescription = comment.userNickname,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userNickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = if (comment.likedCount > 0) comment.likedCount.toString() else "赞",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = formatCommentTime(comment.timeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCommentTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timeMs))
}
