package com.wliky.melody.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.format.formatCount
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.RankingList

/** 歌单卡片。首页推荐、我的歌单、搜索结果共用。 */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    showPlayCount: Boolean = true,
) {
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = playlist.coverUrl,
            seed = playlist.id,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (width != null) width else 160.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showPlayCount && playlist.playCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatCount(playlist.playCount)} 次播放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 榜单卡片：横向滑动区域里使用。 */
@Composable
fun RankingCard(
    ranking: RankingList,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            CoverImage(
                url = ranking.coverUrl,
                seed = ranking.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                corner = 16.dp,
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = ranking.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ranking.updateFrequency?.takeIf { it.isNotBlank() }?.let { frequency ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = frequency,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 歌单 / 榜单详情页头部。 */
@Composable
fun PlaylistHeader(
    name: String,
    coverUrl: String?,
    seed: String,
    metaText: String,
    description: String?,
    onClickPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                CoverImage(
                    url = coverUrl,
                    seed = seed,
                    modifier = Modifier.size(160.dp),
                    corner = 20.dp,
                    iconSize = 44.dp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.clickable(onClick = onClickPlayAll),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = "播放全部",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}
