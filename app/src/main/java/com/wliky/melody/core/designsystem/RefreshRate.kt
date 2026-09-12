package com.wliky.melody.core.designsystem

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * 高刷新率适配。
 *
 * Android 只有在应用主动声明时才会把窗口切到高刷模式；默认情况下普通应用
 * （尤其非游戏、非视频类）会被压在 60Hz。音乐 App 里滚动列表、封面交叉淡入、
 * 播放器展开/歌词滚动全是连续动画，锁 60Hz 会明显发涩。
 *
 * 做法：在设备支持的显示模式里挑一个**分辨率不变、刷新率最高**的模式，
 * 通过 `preferredDisplayModeId` 应用到窗口上。分辨率必须保持一致，
 * 否则会触发一次重新布局（俗称"切分辨率闪一下"）。
 *
 * 不需要任何权限，也不影响功耗策略——系统在静止画面时仍会自行降频。
 */
object RefreshRate {

    /** 低于这个刷新率就不值得切（60Hz 是默认值，切了也没意义）。 */
    private const val MIN_TARGET_HZ = 90f

    /**
     * 把当前窗口切到设备支持的最高刷新率。
     *
     * @return 实际生效的刷新率（Hz）；无法获取或无需切换时返回 null。
     */
    fun applyTo(activity: Activity): Float? {
        val display = resolveDisplay(activity) ?: return null
        val current = runCatching { display.mode }.getOrNull() ?: return null
        val modes = runCatching { display.supportedModes }.getOrNull() ?: return null

        val best = modes
            .asSequence()
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { it.refreshRate >= MIN_TARGET_HZ }
            .maxByOrNull { it.refreshRate }
            ?: return null

        if (best.modeId == current.modeId) return best.refreshRate

        runCatching {
            val attributes = activity.window.attributes
            attributes.preferredDisplayModeId = best.modeId
            activity.window.attributes = attributes
        }.onFailure { return null }

        return best.refreshRate
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplay(activity: Activity): Display? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            activity.windowManager.defaultDisplay
        }
    }.getOrNull()
}
