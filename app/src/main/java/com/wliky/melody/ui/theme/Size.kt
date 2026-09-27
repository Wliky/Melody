package com.wliky.melody.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 尺寸 token：封面 / 行高 / 图标 / 触控目标的唯一档位来源。
 *
 * 此前封面 40/48/52 三档混用、图标 14~36 八档、行高 56/64/72 混用；
 * 页面迁移（P3 起）一律引用此处，不再散落硬编码。
 */
object MelodySize {

    // ── 封面 ──────────────────────────────────────────────
    /** 队列弹层行缩略图 */
    val coverXs: Dp = 40.dp

    /** 歌曲行标准封面（统一档，替代 40/48/52 混用） */
    val coverS: Dp = 48.dp

    /** 歌单行 / 收藏弹层行封面 */
    val coverM: Dp = 56.dp

    /** 歌手圆形头像 / 首页大卡 */
    val coverL: Dp = 72.dp

    /** 个人页圆形头像 */
    val coverXl: Dp = 96.dp

    // ── 行高 ──────────────────────────────────────────────
    /** 歌曲行高（coverS 48 + 上下间距） */
    val rowSong: Dp = 64.dp

    /** 歌单行高 */
    val rowPlaylist: Dp = 72.dp

    // ── 触控 ──────────────────────────────────────────────
    /** 最小触控目标（Material 无障碍标准） */
    val touchMin: Dp = 48.dp

    // ── 图标 ──────────────────────────────────────────────
    /** 行内小图标（勾 / 箭头） */
    val iconS: Dp = 20.dp

    /** 标准图标 / IconButton */
    val iconM: Dp = 24.dp

    /** 迷你条播放键 */
    val iconL: Dp = 28.dp

    /** 播放页播放键 */
    val iconPlay: Dp = 36.dp

    // ── 按钮 ──────────────────────────────────────────────
    /** 按钮高度 */
    val buttonHeight: Dp = 48.dp
}
