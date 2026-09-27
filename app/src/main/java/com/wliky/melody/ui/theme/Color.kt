package com.wliky.melody.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 静态回退色板（仅 Android 12 以下使用）。
 *
 * 全局颜色策略：Monet-only —— Android 12+ 完全跟随系统壁纸动态取色
 * （见 [Theme.kt] 的 dynamicLight/DarkColorScheme），本文件不参与；
 * Android 8~11 无 Monet 能力，回退到这套中性表面 + 紫蓝强调的静态色板。
 * 播放器内部（背景/进度条/播放键）另行使用封面取色，见 CoverPaletteCache。
 */

// ── 浅色 ──────────────────────────────────────────────────────
val LightPrimary = Color(0xFF5B5BD6)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE4E4FF)
val LightOnPrimaryContainer = Color(0xFF141462)
val LightSecondary = Color(0xFF5F5F74)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE5E4F2)
val LightOnSecondaryContainer = Color(0xFF1C1D2E)
val LightTertiary = Color(0xFF7A5480)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF5D9F8)
val LightOnTertiaryContainer = Color(0xFF2E1340)
val LightBackground = Color(0xFFF7F7FB)
val LightOnBackground = Color(0xFF17171E)
val LightSurface = Color(0xFFF7F7FB)
val LightOnSurface = Color(0xFF17171E)
val LightSurfaceVariant = Color(0xFFEDEDF4)
val LightOnSurfaceVariant = Color(0xFF6E6E7A)
val LightOutline = Color(0xFFC9C9D4)
val LightSurfaceContainer = Color(0xFFF0F0F6)
val LightSurfaceContainerHigh = Color(0xFFEAEAF2)
val LightSurfaceContainerHighest = Color(0xFFE4E4EC)

// ── 深色 ──────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFA8AAFF)
val DarkOnPrimary = Color(0xFF1B1D66)
val DarkPrimaryContainer = Color(0xFF3A3D9E)
val DarkOnPrimaryContainer = Color(0xFFE2E3FF)
val DarkSecondary = Color(0xFFC5C5D8)
val DarkOnSecondary = Color(0xFF2D2E42)
val DarkSecondaryContainer = Color(0xFF444559)
val DarkOnSecondaryContainer = Color(0xFFE2E1F6)
val DarkTertiary = Color(0xFFDFBBE4)
val DarkOnTertiary = Color(0xFF402648)
val DarkTertiaryContainer = Color(0xFF583C60)
val DarkOnTertiaryContainer = Color(0xFFF6DCF9)
val DarkBackground = Color(0xFF11141C)
val DarkOnBackground = Color(0xFFE8E8EF)
val DarkSurface = Color(0xFF11141C)
val DarkOnSurface = Color(0xFFE8E8EF)
val DarkSurfaceVariant = Color(0xFF2A2E3A)
val DarkOnSurfaceVariant = Color(0xFF9B9BAA)
val DarkOutline = Color(0xFF363B49)
val DarkSurfaceContainer = Color(0xFF1D212B)
val DarkSurfaceContainerHigh = Color(0xFF262A36)
val DarkSurfaceContainerHighest = Color(0xFF303542)
