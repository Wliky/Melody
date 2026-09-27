package com.wliky.melody.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.EmptyState
import com.wliky.melody.ui.components.ErrorState
import com.wliky.melody.ui.components.GlassCard
import com.wliky.melody.ui.components.MelodyButton
import com.wliky.melody.ui.components.MelodyCard
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.components.SongRowSkeleton
import com.wliky.melody.ui.preview.PreviewData
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.theme.ThemeMode
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * 阶段 1 组件画廊：毛玻璃背景 + 全套基础组件 + 深浅色切换。
 * 用于真机视觉验收；阶段 4 会被真正的导航骨架替换。
 */
@Composable
fun ComponentGalleryScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景大图：作为毛玻璃的取样源
        CoverImage(
            url = "https://picsum.photos/seed/melodybg/900/1600",
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            shape = RectangleShape,
        )
        // 底部渐变压暗，保证列表区可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Black.copy(alpha = 0.2f),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.screen,
                vertical = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // ── 毛玻璃卡片 + 主题切换 ──
            item {
                GlassCard(hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Melody",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "设计系统 · 阶段 1 组件画廊",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // ── 卡片与按钮 ──
            item {
                SectionLabel("卡片与按钮")
                Spacer(modifier = Modifier.height(Spacing.sm))
                MelodyCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { },
                ) {
                    Text(
                        text = "每日推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "根据你的口味生成 · 30 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MelodyButton(text = "立即聆听", onClick = { })
                }
            }

            // ── 歌曲行 ──
            item {
                SectionLabel("歌曲行")
                Spacer(modifier = Modifier.height(Spacing.xs))
                PreviewData.songs.forEach { song ->
                    SongRow(
                        title = song.title,
                        subtitle = song.subtitle,
                        artworkUrl = song.artworkUrl,
                        onClick = { },
                    )
                }
            }

            // ── 骨架屏 ──
            item {
                SectionLabel("骨架屏（加载占位）")
                Spacer(modifier = Modifier.height(Spacing.xs))
                repeat(2) {
                    SongRowSkeleton(modifier = Modifier.fillMaxWidth())
                }
            }

            // ── 状态组件 ──
            item {
                SectionLabel("空状态 / 错误状态")
                Spacer(modifier = Modifier.height(Spacing.sm))
                MelodyCard(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        text = "这里还没有歌曲",
                        hint = "去发现页找些喜欢的音乐吧",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                MelodyCard(modifier = Modifier.fillMaxWidth()) {
                    ErrorState(
                        text = "加载失败",
                        hint = "网络似乎不太顺畅",
                        onRetry = { },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── 毛玻璃材料对比 ──
            item {
                SectionLabel("毛玻璃材料（thin / regular / thick）")
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    GlassCard(
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        style = HazeMaterials.thin(),
                        cornerRadius = 16.dp,
                    ) {
                        Text(
                            text = "thin",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    GlassCard(
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        style = HazeMaterials.regular(),
                        cornerRadius = 16.dp,
                    ) {
                        Text(
                            text = "regular",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    GlassCard(
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        style = HazeMaterials.thick(),
                        cornerRadius = 16.dp,
                    ) {
                        Text(
                            text = "thick",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
