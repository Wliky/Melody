package com.wliky.melody.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wliky.melody.core.datastore.ThemeMode

/**
 * Melody Design System。
 *
 * 视觉方向参考成熟的开源音乐客户端（Now in Android / SimpMusic / InnerTune）：
 * 大面积留白、较大的圆角、克制的色彩层级，以及**由专辑封面驱动的强调色**
 * —— 播放页的主色跟着封面走，这是这类应用最有辨识度的一处细节。
 *
 * 组件层（Components.kt）与各屏幕只消费语义色（MaterialTheme.colorScheme）
 * 和这里的令牌，不硬编码颜色。
 */

/** 品牌主色。 */
val MelodyBrand = Color(0xFFB4232A)

/** 精选/榜单用的金色强调。 */
val MelodyGold = Color(0xFF8A6A1F)

private val MelodyLightScheme = lightColorScheme(
    primary = MelodyBrand,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410006),
    inversePrimary = Color(0xFFFFB3B2),

    secondary = Color(0xFF775656),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD8),
    onSecondaryContainer = Color(0xFF2C1516),

    tertiary = Color(0xFF725C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9B),
    onTertiaryContainer = Color(0xFF261A00),

    background = Color(0xFFFDF8F8),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFDF8F8),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF4DDDC),
    onSurfaceVariant = Color(0xFF534342),
    surfaceTint = MelodyBrand,

    surfaceBright = Color(0xFFFDF8F8),
    surfaceDim = Color(0xFFE4D7D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1F0),
    surfaceContainer = Color(0xFFF7EBEA),
    surfaceContainerHigh = Color(0xFFF1E5E4),
    surfaceContainerHighest = Color(0xFFEBDFDF),

    outline = Color(0xFF857372),
    outlineVariant = Color(0xFFD8C2C0),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val MelodyDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB3B2),
    onPrimary = Color(0xFF68000A),
    primaryContainer = Color(0xFF930013),
    onPrimaryContainer = Color(0xFFFFDAD8),
    inversePrimary = MelodyBrand,

    secondary = Color(0xFFE7BDBD),
    onSecondary = Color(0xFF442929),
    secondaryContainer = Color(0xFF5D3F3F),
    onSecondaryContainer = Color(0xFFFFDAD8),

    tertiary = Color(0xFFE1C38C),
    onTertiary = Color(0xFF3F2E04),
    tertiaryContainer = Color(0xFF584418),
    onTertiaryContainer = Color(0xFFFFDF9B),

    background = Color(0xFF191112),
    onBackground = Color(0xFFF0DEDE),
    surface = Color(0xFF191112),
    onSurface = Color(0xFFF0DEDE),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2C0),
    surfaceTint = Color(0xFFFFB3B2),

    surfaceBright = Color(0xFF413738),
    surfaceDim = Color(0xFF191112),
    surfaceContainerLowest = Color(0xFF130C0D),
    surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF261D1E),
    surfaceContainerHigh = Color(0xFF312829),
    surfaceContainerHighest = Color(0xFF3C3233),

    outline = Color(0xFFA08C8B),
    outlineVariant = Color(0xFF534342),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * 排版：整体收紧字距、加大标题与正文的字号差，让层级一眼可辨。
 */
val MelodyTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 27.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)

/** 间距令牌（4dp 为基础单位）。 */
data class MelodySpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalMelodySpacing = staticCompositionLocalOf { MelodySpacing() }

/** 内容最大宽度：平板/折叠屏上避免一行拉到难以阅读的长度。 */
val ContentMaxWidth: Dp = 840.dp

val MelodyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 当前播放封面的强调色。
 *
 * 默认等于主题主色；进入播放页时由 [rememberArtworkAccent] 用封面图覆盖，
 * 让背景渐变、进度条、歌词高亮都跟着封面走。
 */
val LocalMelodyAccent = staticCompositionLocalOf { MelodyBrand }

@Composable
fun MelodyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && supportsDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> MelodyDarkScheme
        else -> MelodyLightScheme
    }

    CompositionLocalProvider(
        LocalMelodySpacing provides MelodySpacing(),
        LocalMelodyAccent provides colorScheme.primary,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MelodyTypography,
            shapes = MelodyShapes,
            content = content,
        )
    }
}

/** 便捷取用间距令牌。 */
object Spacing {
    val current: MelodySpacing
        @Composable get() = LocalMelodySpacing.current
}
