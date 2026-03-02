package com.lhzkml.jasmine.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JasmineLightColorScheme = lightColorScheme(
    primary = BgPrimary,
    onPrimary = TextPrimary,
    primaryContainer = BgPrimary,
    onPrimaryContainer = TextPrimary,
    secondary = BgPrimary,
    onSecondary = TextPrimary,
    secondaryContainer = BgPrimary,
    onSecondaryContainer = TextPrimary,
    tertiary = BgPrimary,
    onTertiary = TextPrimary,
    tertiaryContainer = BgPrimary,
    onTertiaryContainer = TextPrimary,
    error = BgPrimary,
    onError = TextPrimary,
    errorContainer = BgPrimary,
    onErrorContainer = TextPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgPrimary,
    onSurfaceVariant = TextPrimary,
    surfaceTint = BgPrimary,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgPrimary,
    inversePrimary = BgPrimary,
    outline = Divider,
    outlineVariant = Divider,
    scrim = TextPrimary,
    surfaceBright = BgPrimary,
    surfaceDim = BgPrimary,
    surfaceContainer = BgPrimary,
    surfaceContainerHigh = BgPrimary,
    surfaceContainerHighest = BgPrimary,
    surfaceContainerLow = BgPrimary,
    surfaceContainerLowest = BgPrimary
)

@Composable
fun JasmineTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = BgPrimary.toArgb()
            window.navigationBarColor = BgPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = JasmineLightColorScheme,
        content = content
    )
}
