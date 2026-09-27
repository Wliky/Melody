package com.wliky.melody.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.wliky.melody.ui.theme.MelodyMotion

/**
 * 按压缩放反馈：按下时整体轻微缩小，抬起回弹。
 * 配合 clickable 使用（clickable 负责波纹与点击，本修饰符只做缩放）。
 *
 * 档位取 [MelodyMotion.PressedScaleSmall] / [MelodyMotion.PressedScaleLarge]。
 */
fun Modifier.pressableScale(
    pressedScale: Float = MelodyMotion.PressedScaleSmall,
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = MelodyMotion.DurationShort,
            easing = MelodyMotion.EasingEmphasized,
        ),
        label = "pressScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(pressedScale) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    isPressed = false
                }
            }
        }
}
