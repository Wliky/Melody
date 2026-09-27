package com.wliky.melody.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing

/**
 * 模块分区标题（全 App 唯一样式）。
 *
 * @param onMore 提供时整行可点，右侧显示「更多 >」；否则为纯静态标题。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier)
            // ripple 边界（与行卡片同圆角档）
            .clip(MaterialTheme.shapes.extraSmall)
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onMore != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "更多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看全部$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MelodySize.iconS),
            )
        }
    }
}
