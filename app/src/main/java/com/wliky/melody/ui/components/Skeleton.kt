package com.wliky.melody.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing

/**
 * 骨架屏基元：呼吸式透明度动画。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * 歌曲行骨架：与 [SongRow] 同构，加载列表时占位。
 */
@Composable
fun SongRowSkeleton(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(
            modifier = Modifier.size(MelodySize.coverS),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            SkeletonBox(
                modifier = Modifier
                    .width(140.dp)
                    .height(16.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            SkeletonBox(
                modifier = Modifier
                    .width(90.dp)
                    .height(12.dp),
            )
        }
    }
}
