package com.wliky.melody.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.comment.CommentListBody
import com.wliky.melody.comment.CommentSortChips
import com.wliky.melody.comment.CommentViewModel
import com.wliky.melody.ui.theme.Spacing

/**
 * 播放页 · 评论页（横滑三页之一）：标题 + 排序页签 + 只读列表 + 加载更多。
 * 独立 CommentViewModel 实例，切歌自动重载。
 */
@Composable
internal fun PlayerCommentsPage(
    songId: Long,
    songName: String,
    viewModel: CommentViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) {
        viewModel.loadSongComments(songId, songName)
    }
    val state = viewModel.uiState

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题行 + 排序页签（不展示歌名歌手，播放页常驻区已有）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "评论 (${state.total})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
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
