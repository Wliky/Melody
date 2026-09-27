package com.wliky.melody.podcast

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Podcast
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.ErrorState
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing

/** 热门电台列表页：点电台进入节目列表。 */
@Composable
fun PodcastScreen(
    onBack: () -> Unit,
    onOpenRadio: (podcast: Podcast) -> Unit,
    viewModel: PodcastViewModel = hiltViewModel(),
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
                .padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "播客",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        when {
            state.loading -> PodcastLoading()

            state.error != null -> ErrorState(
                text = state.error,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize(),
            )

            state.podcasts.isEmpty() -> Text(
                text = "暂无电台",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(state.podcasts, key = { "podcast-${it.id}" }) { podcast ->
                    PodcastRow(podcast, onClick = { onOpenRadio(podcast) })
                }
                item { Spacer(modifier = Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun PodcastRow(podcast: Podcast, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = podcast.coverUrl,
            contentDescription = podcast.name,
            modifier = Modifier
                // 播客节目行语义同歌单行，用歌单行封面档（原 64dp 不在档位体系内）
                .size(MelodySize.coverM)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            podcast.rcmdtext
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?.let { rcmd ->
                Text(
                    text = rcmd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PodcastLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(10) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    // 与 PodcastRow 对齐：coverM 56 + 上下 padding，行内容高度
                    .height(MelodySize.coverM + Spacing.xs * 2)
                    .clip(MaterialTheme.shapes.small),
            )
        }
    }
}
