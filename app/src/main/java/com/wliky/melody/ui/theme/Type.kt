package com.wliky.melody.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版阶梯（Design System 唯一来源）。
 *
 * | 用途             | 槽位            | 规格            |
 * |------------------|-----------------|-----------------|
 * | 登录页大标题      | headlineLarge   | 32/40 Bold      |
 * | 特殊页大标题      | headlineMedium  | 28/36 Bold      |
 * | 页面标题（唯一档） | titleLarge      | 22/28 Bold      |
 * | 模块标题          | titleMedium     | 17/24 SemiBold  |
 * | 歌曲名 / 主文本   | bodyLarge       | 16/22 Medium    |
 * | 次级正文          | bodyMedium      | 14/20 Normal    |
 * | 歌手名 / 副标题   | bodySmall       | 13/18 Normal    |
 * | 按钮文字          | labelLarge      | 14/20 SemiBold  |
 * | 辅助文字（时长等） | labelSmall      | 12/16 Medium    |
 *
 * 规则：
 * - 页面标题只用 titleLarge；headlineMedium 仅限登录等特殊大标题场景。
 * - 中文场景统一 0 字距，不沿用 M3 面向拉丁文的默认字距。
 */
val MelodyTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
)
