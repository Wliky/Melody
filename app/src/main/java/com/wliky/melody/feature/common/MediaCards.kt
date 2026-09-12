package com.wliky.melody.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.format.formatCount
import com.wliky.melody.core.designsystem.theme.rememberArtworkAccent
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.RankingList

/**
 * 歌单卡片。首页推荐、我的歌单、搜索结果共用。
 * 播放量做成封面右上角的半透明徽标 —— 既有信息量，又不占用文字行。
 */
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
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Box {
            CoverImage(
                url = playlist.coverUrl,
                seed = playlist.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                corner = 16.dp,
            )
            if (showPlayCount && playlist.playCount > 0) {
                PlayCountBadge(
                    count = playlist.playCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 封面上的播放量徽标。 */
@Composable
private fun PlayCountBadge(count: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
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
            .width(180.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            CoverImage(
                url = ranking.coverUrl,
                seed = ranking.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                corner = 24.dp,
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 歌单 / 榜单详情页头部。
 *
 * 背景用封面提取出的强调色做一层向下淡出的渐变，和播放页保持同一套视觉语言。
 */
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
    val accent = rememberArtworkAccent(coverUrl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.32f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = coverUrl,
            seed = seed,
            modifier = Modifier
                .size(190.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = accent,
                    spotColor = accent,
                ),
            corner = 22.dp,
            iconSize = 54.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onClickPlayAll,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "播放全部",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
