package com.wliky.melody.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角 5 档（唯一来源，页面不得散落 RoundedCornerShape 硬编码）：
 *
 * | 档位        | 值   | 用途                       |
 * |-------------|------|----------------------------|
 * | extraSmall  | 8dp  | Chip / 标签 / 进度条        |
 * | small       | 12dp | 所有封面缩略图（统一档）     |
 * | medium      | 16dp | 卡片容器 / Dialog / 设置分组 |
 * | large       | 24dp | 大卡片 / 内嵌面板            |
 * | extraLarge  | 28dp | BottomSheet 顶部            |
 */
val MelodyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
