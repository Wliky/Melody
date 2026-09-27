package com.wliky.melody.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import com.wliky.melody.ui.theme.Spacing

/**
 * 毛玻璃卡片（Haze 2.0）：背后内容经由 [hazeState] 捕获并高斯模糊。
 *
 * 用法：背景层标记 `Modifier.hazeSource(hazeState)`，本卡片叠加其上。
 * 材料默认 [HazeMaterials.thin]，透明度随深浅色主题自动适配。
 */
@Composable
fun GlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    style: HazeBlurStyle = HazeMaterials.thin(),
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = style,
            )
            .padding(Spacing.md),
        content = content,
    )
}
