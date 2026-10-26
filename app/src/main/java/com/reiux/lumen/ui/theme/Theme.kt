package com.reiux.lumen.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.reiux.lumen.util.Season

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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = LumenShapes,
        typography = LumenTypography,
        content = content
    )
}
