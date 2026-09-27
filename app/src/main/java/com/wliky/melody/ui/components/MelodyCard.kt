package com.wliky.melody.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wliky.melody.ui.theme.MelodyMotion
import com.wliky.melody.ui.theme.Spacing

/**
 * 基础容器卡片：大圆角 + 半透明 surface 色 + 主题描边。
 * 可点击时附带按压缩放反馈。
 */
@Composable
fun MelodyCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    )
    val pressModifier = if (onClick != null) {
        Modifier.pressableScale(MelodyMotion.PressedScaleLarge)
    } else {
        Modifier
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(pressModifier),
            shape = shape,
            color = container,
            border = border,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = container,
            border = border,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}
