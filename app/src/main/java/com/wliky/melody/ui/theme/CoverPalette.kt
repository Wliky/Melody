package com.wliky.melody.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import android.util.LruCache
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wliky.melody.ui.components.coverUrlWithSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 封面取色结果（播放器局部莫奈配色）。
 *
 * @param accent 强调色：进度条 / 播放键渐变 / 激活态图标
 * @param scrimTop 播放页背景渐变遮罩·顶部（较透，露出模糊封面）
 * @param scrimBottom 播放页背景渐变遮罩·底部（较深，保证白字对比度）
 */
data class CoverColors(
    val accent: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
)

/** 颜色工具：按比例压暗 / 提亮。 */
fun Color.darken(f: Float): Color =
    Color(red * f, green * f, blue * f, alpha = alpha)

fun Color.lighten(f: Float): Color = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha,
)

/** 封面取色缓存：URL → 取色结果，LRU 有界（48 张），避免同封面重复解码、也防内存无限增长。 */
object CoverPaletteCache {
    private const val SOURCE_SIZE = 128
    private val cache = LruCache<String, CoverColors>(48)

    suspend fun getOrExtract(context: Context, url: String): CoverColors? {
        cache.get(url)?.let { return it }
        val colors = extract(context, url) ?: return null
        cache.put(url, colors)
        return colors
    }

    private suspend fun extract(context: Context, url: String): CoverColors? = runCatching {
        // 复用 Coil 的磁盘缓存解码小图（Palette 只需要低分辨率像素）；
        // 直接向 CDN 请求 128px 小图，取色不再下载原图
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(coverUrlWithSize(url, SOURCE_SIZE))
                .allowHardware(false) // Palette 需要软件位图
                .size(SOURCE_SIZE, SOURCE_SIZE)
                .build(),
        )
        val bitmap: Bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        val palette = withContext(Dispatchers.Default) {
            Palette.from(bitmap).maximumColorCount(32).generate()
        }
        buildColors(palette)
    }.getOrNull()

    /**
     * 强调色取鲜亮的候选并做激进增强（HSV 拉饱和），遮罩取主色压暗。
     * 取色偏网易橙/莫奈鲜艳风：饱和度不足时至少拉到 0.5，保证播放页强调色鲜明。
     */
    private fun buildColors(palette: Palette): CoverColors? {
        val accentSwatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.lightMutedSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
            ?: return null
        val dominant = Color(palette.dominantSwatch?.rgb ?: accentSwatch.rgb)
        return CoverColors(
            accent = saturate(accentSwatch.rgb),
            scrimTop = dominant.darken(0.30f).copy(alpha = 0.42f),
            scrimBottom = dominant.darken(0.12f).copy(alpha = 0.85f),
        )
    }

    /** 激进提饱和 + 提亮：HSV 空间拉高饱和度（低饱和封面也出鲜明强调色），
     *  过暗的封面拉到最小明度，避免黑封面取出一团脏色（莫奈鲜艳风）。 */
    private fun saturate(
        rgb: Int,
        boost: Float = 1.5f,
        minSaturation: Float = 0.55f,
        minValue: Float = 0.5f,
    ): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = (hsv[1] * boost).coerceIn(minSaturation, 1f)
        if (hsv[2] < minValue) hsv[2] = minValue
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/** 按封面 URL 异步取色；URL 变化重新提取，失败返回 null（调用方回退主题色）。 */
@Composable
fun rememberCoverColors(url: String?): State<CoverColors?> {
    val context = LocalContext.current
    val state = remember(url) { mutableStateOf<CoverColors?>(null) }
    LaunchedEffect(url) {
        state.value = if (url.isNullOrBlank()) {
            null
        } else {
            CoverPaletteCache.getOrExtract(context, url)
        }
    }
    return state
}
