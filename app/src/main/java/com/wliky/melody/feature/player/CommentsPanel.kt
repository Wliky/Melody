package com.wliky.melody.feature.player

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.model.Comment
import com.wliky.melody.core.model.CommentSort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 评论区（v0.3.0-preview.3+，只读）。
 *
 * 顶部一行「热门 / 最新」分段控件，下方无限滚动的评论列表；
 * 滚动到底自动加载下一页；首屏失败时给出 ErrorState + 重试。
 */
@Composable
fun CommentsPanel(
    state: CommentsViewModel.UiState,
    onSwitchSort: (CommentSort) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 触底自动加载：最后一项可见时触发
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisible.index >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.songId, state.hasMore) {
        if (shouldLoadMore && state.hasMore && !state.loadingMore && !state.loading && state.songId != null) {
            onLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SortHeader(
            total = state.total,
            current = state.sort,
            onSwitch = onSwitchSort,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        )

        when {
            state.loading && state.items.isEmpty() -> LoadingBox(modifier = Modifier.fillMaxSize())

            state.error != null && state.items.isEmpty() -> ErrorState(
                error = com.wliky.melody.core.common.AppError.Server(state.error),
                onRetry = onRefresh,
                modifier = Modifier.fillMaxSize(),
            )

            state.items.isEmpty() -> EmptyState(
                title = "还没有评论",
                description = "评论区空空如也 —— 要不你来当第一位？",
                icon = Icons.Rounded.ChatBubbleOutline,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items = state.items, key = { _, c -> c.id }) { _, comment ->
                    CommentRow(comment)
                }
                if (state.loadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHeader(
    total: Int,
    current: CommentSort,
    onSwitch: (CommentSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (total > 0) "共 $total 条评论" else "评论",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SingleChoiceSegmentedButtonRow {
            CommentSort.entries.forEachIndexed { index, sort ->
                SegmentedButton(
                    selected = sort == current,
                    onClick = { onSwitch(sort) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CommentSort.entries.size),
                ) {
                    Text(
                        text = sort.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoverImage(
            url = comment.avatarUrl,
            seed = comment.userId.ifBlank { comment.nickname },
            modifier = Modifier.size(38.dp),
            corner = 19.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                comment.ipLabel?.takeIf { it.isNotBlank() }?.let { ip ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = ip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatRelativeTime(comment.publishTimeSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (comment.likedCount > 0 || comment.replyCount > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (comment.likedCount > 0) formatCount(comment.likedCount) else "赞",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (comment.replyCount > 0) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "回复 ${formatCount(comment.replyCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val FULL_DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

private fun formatRelativeTime(seconds: Long): String {
    if (seconds <= 0L) return ""
    val now = System.currentTimeMillis() / 1000L
    val delta = (now - seconds).coerceAtLeast(0)
    return when {
        delta < 60 -> "刚刚"
        delta < 3600 -> "${delta / 60} 分钟前"
        delta < 86_400 -> "${delta / 3600} 小时前"
        delta < 86_400 * 30 -> "${delta / 86_400} 天前"
        else -> FULL_DATE_FMT.format(Date(seconds * 1000L))
    }
}

private fun formatCount(count: Int): String = when {
    count <= 0 -> "0"
    count < 10_000 -> count.toString()
    count < 100_000_000 -> String.format(Locale.CHINA, "%.1f万", count / 10_000.0)
    else -> String.format(Locale.CHINA, "%.1f亿", count / 100_000_000.0)
}