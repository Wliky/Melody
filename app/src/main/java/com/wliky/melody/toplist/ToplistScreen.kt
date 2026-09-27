package com.wliky.melody.toplist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Toplist
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.ErrorState
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.theme.Spacing

/** 排行榜横向卡片宽度（大专辑封面）。 */
private val ToplistCardWidth = 150.dp

/** 官方榜数量：接口返回列表的前 4 张为官方榜，其余为特色榜。 */
private const val OFFICIAL_COUNT = 4

/**
 * 排行榜页：官方榜 / 特色榜两组横向大封面流。
 * 点卡片把榜单 id 当歌单 id 打开现有歌单详情（榜单本质是特殊歌单）。
 */
@Composable
fun ToplistScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    viewModel: ToplistViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "排行榜",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        when {
            state.loading -> ToplistLoading()

            state.error != null -> ErrorState(
                text = state.error,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize(),
            )

            state.toplists.isEmpty() -> Text(
                text = "暂无榜单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            )

            else -> {
                val official = state.toplists.take(OFFICIAL_COUNT)
                val featured = state.toplists.drop(OFFICIAL_COUNT)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (official.isNotEmpty()) {
                        item {
                            Text(
                                text = "官方榜",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                contentPadding = PaddingValues(end = Spacing.md),
                            ) {
                                items(official, key = { "toplist-${it.id}" }) { toplist ->
                                    ToplistCard(toplist) { toplist.toPlaylist().let(onOpenPlaylist) }
                                }
                            }
                        }
                    }
                    if (featured.isNotEmpty()) {
                        item {
                            Text(
                                text = "特色榜",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                contentPadding = PaddingValues(end = Spacing.md),
                            ) {
                                items(featured, key = { "toplist-${it.id}" }) { toplist ->
                                    ToplistCard(toplist) { toplist.toPlaylist().let(onOpenPlaylist) }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.xl)) }
                }
            }
        }
    }
}

/** 榜单 → 歌单（榜单 id 可直接当歌单 id 拉详情）。 */
private fun Toplist.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    coverUrl = coverUrl,
)

/** 榜单卡片：大专辑封面 + 榜名 + 更新频率。 */
@Composable
private fun ToplistCard(toplist: Toplist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(ToplistCardWidth)
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = toplist.coverUrl,
            contentDescription = toplist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(18.dp),
        )
        Text(
            text = toplist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        toplist.updateFrequency?.takeIf { it.isNotBlank() }?.let { freq ->
            Text(
                text = freq,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToplistLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier
                        .width(ToplistCardWidth)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
        }
        repeat(4) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
