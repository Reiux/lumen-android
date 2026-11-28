package com.reiux.lumen.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.reiux.lumen.util.Season
import com.reiux.lumen.util.SystemFontProvider

@Composable
fun LumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPreset: String = "Dynamic",
    activeSeason: Season = Season.NONE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val allPresets = BasePresets + PrismPreset + MatrixPreset + SpookyPreset + XmasPreset + NewYrPreset
    val selectedPreset = allPresets.find { it.name.equals(colorPreset, ignoreCase = true) } ?: BasePresets[0]

    val colorScheme = when {
        colorPreset.equals("Dynamic", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> selectedPreset.darkScheme
        else -> selectedPreset.lightScheme
    }

    val dynamicFont = remember {
        SystemFontProvider.getGoogleSansFamily() ?: FontFamily.Default
    }

    val titleFont = remember(dynamicFont) {
        SystemFontProvider.getGoogleSansRoundedFamily() ?: dynamicFont
    }

    val typography = remember(dynamicFont, titleFont) {
        createLumenTypography(bodyFontFamily = dynamicFont, titleFontFamily = titleFont)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = LumenShapes,
        typography = typography,
        content = content
    )
}
