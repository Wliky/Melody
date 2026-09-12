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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wliky.melody.core.datastore.ThemeMode

/**
 * Melody Design System（文档 §5）。
 *
 * 圆角 12/16/24 三档、4dp 基础间距、跟随系统的深浅色，
 * 并在 Android 12+ 支持动态取色（从壁纸取色），播放器页面再按封面生成强调色。
 */

/** 品牌主色（也是未开启动态取色时的默认种子色）。 */
val MelodyBrand = Color(0xFFC20C0C)
private val MelodyBrandDark = Color(0xFFE85C5C)

private val MelodyLightScheme = lightColorScheme(
    primary = MelodyBrand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    background = Color(0xFFFFFBF8),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857371),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val MelodyDarkScheme = darkColorScheme(
    primary = MelodyBrandDark,
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val MelodyTypography = Typography()

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

    CompositionLocalProvider(LocalMelodySpacing provides MelodySpacing()) {
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
