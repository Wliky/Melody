package com.wliky.melody.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing

/**
 * 通用歌单行（全 App 唯一样式）：封面(56dp/12dp) + 名称/曲目数 + [trailing 槽位]。
 *
 * 用于：我的页歌单列表、搜索结果歌单行、收藏到歌单弹层行等。
 *
 * @param trailing 行尾槽位：已收藏打勾 / 请求中转圈等
 */
@Composable
fun PlaylistRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = coverUrl,
            contentDescription = title,
            modifier = Modifier.size(MelodySize.coverM),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
        trailing?.let {
            Spacer(modifier = Modifier.width(Spacing.sm))
            it()
        }
    }
}
