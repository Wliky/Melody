package com.wliky.melody.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.designsystem.format.formatDuration
import com.wliky.melody.core.model.Song
import kotlin.math.abs

/** 封面缺图时的占位渐变色板：沉稳的深色系，更像唱片封面而不是彩色色块。 */
private val CoverPalette = listOf(
    Color(0xFF4A4458),
    Color(0xFF7A4E58),
    Color(0xFF35595B),
    Color(0xFF6F5B3A),
    Color(0xFF3B506E),
    Color(0xFF5B4368),
    Color(0xFF2F4F4A),
    Color(0xFF6B4340),
)

fun coverBrush(seed: String): Brush {
    val hash = abs(seed.hashCode())
    val first = CoverPalette[hash % CoverPalette.size]
    val second = CoverPalette[(hash / 7 + 3) % CoverPalette.size]
    return Brush.linearGradient(listOf(first, second))
}

/**
 * 统一封面组件：有图走 Coil（自带内存/磁盘缓存 + 淡入），无图用确定性渐变兜底，
 * 演示模式下也能有不错的观感。
 */
@Composable
fun CoverImage(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    iconSize: Dp = 26.dp,
    elevation: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(coverBrush(seed)),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(220)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSize),
            )
        }
    }
}

/**
 * 「正在播放」的三条跳动指示条，替换掉列表里的序号。
 */
@Composable
fun PlayingIndicator(
    playing: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (!playing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(BAR_COUNT) {
                Box(
                    modifier = Modifier
                        .width(BAR_WIDTH)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.5f)),
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "playing-indicator")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(BAR_COUNT) { index ->
            val fraction by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420 + index * 110,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(BAR_WIDTH)
                    .height(INDICATOR_HEIGHT * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private const val BAR_COUNT = 3
private val BAR_WIDTH = 3.dp
private val INDICATOR_HEIGHT = 15.dp

/**
 * 歌曲列表项。
 *
 * @param playing 当前项是否正在播放（用跳动条替换序号）
 * @param index   传入时显示序号；正在播放则自动换成跳动条
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    index: Int? = null,
    playing: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center,
            ) { leading() }

            index != null -> Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (playing) {
                    PlayingIndicator()
                } else {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val hasLeading = leading != null || index != null
        if (hasLeading) Spacer(Modifier.width(8.dp))

        CoverImage(
            url = song.coverUrl,
            seed = song.id,
            modifier = Modifier.size(52.dp),
            corner = 14.dp,
        )
        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle ?: buildString {
                    append(song.artistText)
                    song.album?.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (song.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/**
 * 分区大标题。首页各区块、详情页分节都用它，保证一级标题风格统一。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 统一空态：图标套一个圆形色块，避免整页只有一行灰字。 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Rounded.MusicNote,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 统一错误态：文案来自 AppError 的语义分类，用户永远知道发生了什么、能不能重试。 */
@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null && error.recoverable) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
    }
}

/**
 * 分组卡片：把若干设置项/信息项收进一张圆角卡，替代过去"一行一条分割线"的平淡排版。
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = { content() },
        )
    }
}

/** 可点击的设置项。 */
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
