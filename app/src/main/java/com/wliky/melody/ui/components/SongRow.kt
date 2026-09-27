package com.wliky.melody.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing

/**
 * 通用歌曲行（全 App 唯一样式）：[leading 槽位] + [封面(48dp/12dp，artworkUrl 为空则不渲染)] + 标题/副标题 + [trailing 槽位]。
 *
 * 歌单详情等密排场景传 null 封面 + leading 序号；标准场景传封面 URL。
 *
 * @param leading 行首槽位：序号 / 播放标记 / 多选框等；宽度由调用方控制
 * @param highlight 播放中高亮：标题变主题色 + SemiBold
 * @param trailing 行尾槽位：时长 / 更多按钮等
 * @param onLongClick 长按反馈（自带 LongPress 触感），列表页用于弹出歌曲操作
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick?.let { callback ->
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                callback()
                            }
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        if (artworkUrl != null) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            CoverImage(
                url = artworkUrl,
                contentDescription = title,
                modifier = Modifier.size(MelodySize.coverS),
                shape = MaterialTheme.shapes.small,
            )
            Spacer(modifier = Modifier.width(Spacing.md))
        } else if (leading == null) {
            // 无行首槽位时文字保留起始缩进，避免顶到行首
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlight) FontWeight.SemiBold else null,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        trailing?.invoke()
    }
}
