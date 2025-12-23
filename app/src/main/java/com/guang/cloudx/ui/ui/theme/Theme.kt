package com.guang.cloudx.ui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

@Composable
fun CloudXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: String = "跟随系统",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val colorScheme = when {
        themeColor == "跟随系统" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeColor != "跟随系统" && SeedColors.containsKey(themeColor) -> {
            generateColorSchemeFromSeed(SeedColors[themeColor]!!, darkTheme)
        }
        darkTheme -> darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
        else -> lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 最终版：基于 LAB 色彩空间模拟 Material 3 官方 Tone 分布
 * 严格遵循 M3 规范，确保背景、容器和文字对比度完美
 */
private fun generateColorSchemeFromSeed(seed: Color, isDark: Boolean): ColorScheme {
    val seedArgb = seed.toArgb()
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(seedArgb, lab)
    val L = lab[0]
    val a = lab[1]
    val b = lab[2]

    // 核心函数：根据指定的 Tone (0-100) 生成颜色
    fun tone(t: Double, chromaFactor: Double = 1.0): Color {
        return Color(ColorUtils.LABToColor(t, a * chromaFactor, b * chromaFactor))
    }

    return if (isDark) {
        darkColorScheme(
            primary = tone(80.0),
            onPrimary = tone(20.0),
            primaryContainer = tone(30.0),
            onPrimaryContainer = tone(90.0),
            inversePrimary = tone(40.0),
            secondary = tone(80.0, 0.3),
            onSecondary = tone(20.0, 0.3),
            secondaryContainer = tone(30.0, 0.3),
            onSecondaryContainer = tone(90.0, 0.3),
            tertiary = tone(80.0, 0.5),
            onTertiary = tone(20.0, 0.5),
            tertiaryContainer = tone(30.0, 0.5),
            onTertiaryContainer = tone(90.0, 0.5),
            background = tone(6.0, 0.02),
            onBackground = tone(90.0, 0.02),
            surface = tone(6.0, 0.02),
            onSurface = tone(90.0, 0.02),
            surfaceVariant = tone(30.0, 0.1),
            onSurfaceVariant = tone(80.0, 0.1),
            surfaceTint = tone(80.0),
            inverseSurface = tone(90.0, 0.02),
            inverseOnSurface = tone(20.0, 0.02),
            outline = tone(60.0, 0.1),
            outlineVariant = tone(30.0, 0.1),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            scrim = Color.Black,
            surfaceBright = tone(24.0, 0.02),
            surfaceDim = tone(6.0, 0.02),
            surfaceContainer = tone(12.0, 0.02),
            surfaceContainerHigh = tone(17.0, 0.02),
            surfaceContainerHighest = tone(22.0, 0.02),
            surfaceContainerLow = tone(10.0, 0.02),
            surfaceContainerLowest = tone(4.0, 0.02),
            primaryFixed = tone(90.0),
            primaryFixedDim = tone(80.0),
            onPrimaryFixed = tone(10.0),
            onPrimaryFixedVariant = tone(30.0),
            secondaryFixed = tone(90.0, 0.3),
            secondaryFixedDim = tone(80.0, 0.3),
            onSecondaryFixed = tone(10.0, 0.3),
            onSecondaryFixedVariant = tone(30.0, 0.3),
            tertiaryFixed = tone(90.0, 0.5),
            tertiaryFixedDim = tone(80.0, 0.5),
            onTertiaryFixed = tone(10.0, 0.5),
            onTertiaryFixedVariant = tone(30.0, 0.5)
        )
    } else {
        lightColorScheme(
            primary = tone(40.0),
            onPrimary = Color.White,
            primaryContainer = tone(90.0),
            onPrimaryContainer = tone(10.0),
            inversePrimary = tone(80.0),
            secondary = tone(40.0, 0.3),
            onSecondary = Color.White,
            secondaryContainer = tone(95.0, 0.3),
            onSecondaryContainer = tone(10.0, 0.3),
            tertiary = tone(40.0, 0.5),
            onTertiary = Color.White,
            tertiaryContainer = tone(95.0, 0.5),
            onTertiaryContainer = tone(10.0, 0.5),
            background = tone(99.0, 0.01),
            onBackground = tone(10.0, 0.01),
            surface = tone(98.0, 0.01),
            onSurface = tone(10.0, 0.01),
            surfaceVariant = tone(90.0, 0.05),
            onSurfaceVariant = tone(30.0, 0.05),
            surfaceTint = tone(40.0),
            inverseSurface = tone(20.0, 0.02),
            inverseOnSurface = tone(95.0, 0.02),
            outline = tone(50.0, 0.05),
            outlineVariant = tone(80.0, 0.05),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            scrim = Color.Black,
            surfaceBright = tone(98.0, 0.01),
            surfaceDim = tone(87.0, 0.01),
            surfaceContainer = tone(94.0, 0.01),
            surfaceContainerHigh = tone(92.0, 0.01),
            surfaceContainerHighest = tone(90.0, 0.01),
            surfaceContainerLow = tone(96.0, 0.01),
            surfaceContainerLowest = Color.White,
            primaryFixed = tone(90.0),
            primaryFixedDim = tone(80.0),
            onPrimaryFixed = tone(10.0),
            onPrimaryFixedVariant = tone(30.0),
            secondaryFixed = tone(90.0, 0.3),
            secondaryFixedDim = tone(80.0, 0.3),
            onSecondaryFixed = tone(10.0, 0.3),
            onSecondaryFixedVariant = tone(30.0, 0.3),
            tertiaryFixed = tone(90.0, 0.5),
            tertiaryFixedDim = tone(80.0, 0.5),
            onTertiaryFixed = tone(10.0, 0.5),
            onTertiaryFixedVariant = tone(30.0, 0.5)
        )
    }
}
