package com.wliky.melody.core.designsystem.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从专辑封面里提取一个可用作强调色的颜色。
 *
 * 这是 Apple Music / Spotify / SimpMusic 这类播放器最抓眼的细节：整个播放页的
 * 背景渐变、进度条、歌词高亮都跟着当前封面走。
 *
 * 实现要点：
 *  - 只解码一张 [SAMPLE_SIZE] 的小图，用于取色，开销可忽略；
 *  - 取色后把亮度**约束到当前主题合适的区间**，否则浅色封面在浅色主题下会糊成一片；
 *  - 提取失败（无封面 / 加载失败 / 纯色封面）时回落到主题主色，不会出现突变。
 *
 * @param url      封面地址；为空时直接返回 [fallback]
 * @param fallback 兜底色，默认取主题主色
 */
@Composable
fun rememberArtworkAccent(
    url: String?,
    fallback: Color = MaterialTheme.colorScheme.primary,
): Color {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var accent by remember(url, darkTheme) { mutableStateOf(fallback) }

    LaunchedEffect(url, darkTheme) {
        if (url.isNullOrBlank()) {
            accent = fallback
            return@LaunchedEffect
        }
        val extracted = runCatching { extractAccent(context, url, darkTheme) }.getOrNull()
        accent = extracted ?: fallback
    }

    return accent
}

/** 取色用的解码尺寸：够用且足够便宜。 */
private const val SAMPLE_SIZE = 96

private suspend fun extractAccent(
    context: android.content.Context,
    url: String,
    darkTheme: Boolean,
): Color? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(SAMPLE_SIZE)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    val bitmap = result.drawable.toBitmap() ?: return null

    val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null

    return normalizeForTheme(Color(swatch.rgb), darkTheme)
}

/**
 * 保持色相不变，只把亮度收进主题可用的区间：
 * 深色主题下需要够亮才能当强调色，浅色主题下需要够深才有对比度。
 */
private fun normalizeForTheme(color: Color, darkTheme: Boolean): Color {
    val hsl = FloatArray(3)
    AndroidColor.colorToHSL(color.toArgb(), hsl)
    // 低饱和的封面（黑白、灰调）取出来会像"没上色"，给它一点饱和度下限
    hsl[1] = hsl[1].coerceAtLeast(0.20f)
    hsl[2] = if (darkTheme) {
        hsl[2].coerceIn(0.50f, 0.74f)
    } else {
        hsl[2].coerceIn(0.24f, 0.46f)
    }
    return Color(AndroidColor.HSLToColor(hsl))
}
