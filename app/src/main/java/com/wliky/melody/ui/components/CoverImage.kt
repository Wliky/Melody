package com.wliky.melody.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 网易云 CDN 封面 URL 附加缩略尺寸参数（?param=WxH）：
 * 由 CDN 直接输出小图，列表缩略图不再下载原图，流量与解码开销骤降。
 * 非网易图床的 URL 原样返回；已有参数会被替换。
 */
fun coverUrlWithSize(url: String, px: Int): String {
    if (!url.contains("music.126.net", ignoreCase = true)) return url
    val base = url.substringBefore('?')
    return "$base?param=${px}y$px"
}

/**
 * 封面图统一封装：Coil 3 加载、裁剪、淡入。
 * - URL 自动附加网易云缩略参数（requestSizePx），避免列表加载原图
 * - 占位/失败兜底色，加载期间不再是白块闪烁
 * - ImageRequest remember 化，快速滚动时减少临时对象分配
 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop,
    requestSizePx: Int = DEFAULT_REQUEST_SIZE_PX,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val model = remember(url, requestSizePx, context) {
        url?.let {
            ImageRequest.Builder(context)
                .data(coverUrlWithSize(it, requestSizePx))
                .crossfade(true)
                .build()
        }
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}

/** 默认请求 480px：覆盖列表卡片（约 160dp @3x）以内的全部展示尺寸。 */
private const val DEFAULT_REQUEST_SIZE_PX = 480
