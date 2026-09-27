@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.wliky.melody.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** 播放页封面共享元素 key：迷你条缩略图 ↔ 播放页大封面。 */
const val NOW_PLAYING_COVER_KEY = "now-playing-cover"

/** 跨页共享元素容器（MainScreen 最外层 SharedTransitionLayout）提供的作用域。 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 当前页面分支（AnimatedContent / AnimatedVisibility 内容）的动画作用域。 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 给元素挂共享元素转场：两处同名 key 的元素在页面切换期间互相飞越
 * （位置/尺寸连续插值，渲染在覆盖层之上）。
 * 未处于转场容器内（如预览/独立页面）时原样返回，无副作用。
 */
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedCover.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
        )
    }
}
