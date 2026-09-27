package com.wliky.melody.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wliky.melody.data.model.LyricLine
import com.wliky.melody.ui.theme.Spacing

/**
 * AMLL 风格歌词页（参考 applemusic-like-lyrics）：
 * 当前行大号粗体 + 辉光、未唱行缩小变暗，缩放/透明度用弹簧动画过渡；
 * 当前行始终停在视口上部，点击任意行可跳转播放进度。
 * 主行字号可调（设置 - 歌词管理，10-20sp），行高与翻译行等比缩放。
 */
@Composable
internal fun AmllLyricsPage(
    lines: List<LyricLine>,
    loading: Boolean,
    positionMs: Long,
    fontSizeSp: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timeMs <= positionMs }
    }

    // 当前行变化 → 滚到视口上 1/4 处（首尾留白保证首/末行也能到焦点位）
    LaunchedEffect(currentIndex, lines.size) {
        if (currentIndex >= 0 && lines.isNotEmpty()) {
            val offset = -(listState.layoutInfo.viewportSize.height / 4)
            listState.animateScrollToItem(currentIndex, scrollOffset = offset)
        }
    }

    // fillMaxSize：仅 fillMaxWidth 时高度不受 Pager 约束，
    // LazyColumn 的 fillMaxSize 会撑到进度条/控制区下方（歌词被遮挡）
    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading -> Text(
                text = "歌词加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            lines.isEmpty() -> Text(
                text = "纯音乐 · 请欣赏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 信息行在歌词页已从组合中移除（不再占位），上下留白相应收紧，
                // 歌词获得完整显示空间；底部仍略大于顶部（末行进焦点区不贴边裁半）
                contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lines, key = { i, line -> "$i-${line.timeMs}" }) { index, line ->
                    LyricRow(
                        line = line,
                        active = index == currentIndex,
                        passed = index < currentIndex,
                        fontSizeSp = fontSizeSp,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

/** 单行歌词：弹簧缩放 + 透明度分级（唱过更暗），激活行带辉光。
 *  字号随设置（10-20sp）等比缩放：行高 1.36 倍、翻译行 0.64 倍。 */
@Composable
private fun LyricRow(
    line: LyricLine,
    active: Boolean,
    passed: Boolean,
    fontSizeSp: Float,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lyric-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            active -> 1f
            passed -> 0.3f
            else -> 0.5f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyric-alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.36f).sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = if (active) {
                TextStyle(
                    shadow = Shadow(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        blurRadius = 26f,
                    ),
                )
            } else {
                TextStyle.Default
            },
            modifier = Modifier
                .scale(scale)
                .alpha(alpha),
        )
        line.translation?.let { translation ->
            Text(
                text = translation,
                fontSize = (fontSizeSp * 0.64f).sp,
                lineHeight = (fontSizeSp * 0.96f).sp,
                textAlign = TextAlign.Center,
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
                    .padding(top = 2.dp),
            )
        }
    }
}
