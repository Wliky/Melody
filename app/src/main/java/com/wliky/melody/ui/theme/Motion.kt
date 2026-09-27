package com.wliky.melody.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 动效常量唯一来源。任何按压 / 过渡数值都从这里取，不散落硬编码。
 */
object MelodyMotion {

    // ── 时长（毫秒） ──────────────────────────────────────────
    const val DurationShort = 150
    const val DurationNormal = 300
    const val DurationLong = 450

    // ── 缓动曲线 ──────────────────────────────────────────────
    /** 强调过渡（进出场） */
    val EasingEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 退场 */
    val EasingExit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ── 按压反馈档位 ──────────────────────────────────────────
    /** 列表项 / 小按钮按压缩放 */
    const val PressedScaleSmall = 0.97f

    /** 卡片 / 大按钮按压缩放 */
    const val PressedScaleLarge = 0.96f
}
