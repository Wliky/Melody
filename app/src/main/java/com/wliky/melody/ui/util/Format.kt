package com.wliky.melody.ui.util

/**
 * 全局展示格式化：时长 / 播放量等的唯一实现。
 *
 * 此前 formatDuration 有 3 份、formatCount 有 4 份私有拷贝，
 * 且万位格式存在「截断」与「四舍五入」两种行为，统一为多数派实现。
 */

/** 毫秒 → m:ss；无时长（≤0）显示 --:--。 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** 播放 / 评论 / 关注数 → 1.2万 / 3亿 风格（四舍五入保留一位小数）。 */
fun formatCount(count: Long): String {
    val wan = count / 10_000.0
    val yi = count / 100_000_000.0
    return when {
        count >= 100_000_000 ->
            if (yi % 1.0 == 0.0) "${yi.toInt()}亿" else "%.1f亿".format(yi)
        count >= 10_000 ->
            if (wan % 1.0 == 0.0) "${wan.toInt()}万" else "%.1f万".format(wan)
        else -> count.toString()
    }
}
