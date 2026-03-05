package com.lhzkml.jasmine.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class JasmineColors(
    val accent: Color = Accent,
    val accentLight: Color = AccentLight,
    val bgPrimary: Color = BgPrimary,
    val bgInput: Color = BgInput,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val divider: Color = Divider,
    val userBubble: Color = UserBubble,
    val userBubbleText: Color = UserBubbleText,
    val aiBubble: Color = AiBubble,
    val aiBubbleText: Color = AiBubbleText,
    val error: Color = ErrorColor,
    val generatingGreen: Color = GeneratingGreen
)

val LocalJasmineColors = staticCompositionLocalOf { JasmineColors() }

object JasmineTheme {
    val colors: JasmineColors
        @Composable get() = LocalJasmineColors.current
}

@Composable
fun JasmineTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
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
    CompositionLocalProvider(LocalJasmineColors provides JasmineColors()) {
        content()
    }
}
