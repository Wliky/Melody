package com.wliky.melody.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.NOW_PLAYING_COVER_KEY
import com.wliky.melody.ui.components.sharedCover
import com.wliky.melody.ui.theme.Spacing

/**
 * 底部常驻迷你播放条（方形圆角卡片样式）：
 * 顶部贴边进度条保持在方形上边框，颜色固定跟随「设置」莫奈取色（不跟专辑封面取色）；
 * 内部封面缩略图 + 曲名/歌手 + 右侧播放暂停 / 下一曲 / 播放列表，点卡片进播放页。
 * 图标统一线性圆角（Outlined），颜色跟随莫奈取色。
 */
@Composable
fun MiniPlayerBar(
    state: PlayerQueue.QueueState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 设置莫奈取色（Android 12+ 跟随系统壁纸；以下回退静态主色），不取封面色
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.clip(RoundedCornerShape(18.dp))) {
            // 顶部贴边进度条（方形上边框，莫奈取色）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(accent),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 挂共享元素：点开播放页时这张小封面飞越放大为播放页大封面
                CoverImage(
                    url = song.coverUrl,
                    contentDescription = song.name,
                    modifier = Modifier
                        .size(48.dp)
                        .sharedCover(NOW_PLAYING_COVER_KEY),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "下一曲",
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = "播放列表",
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
