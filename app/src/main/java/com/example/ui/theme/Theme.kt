package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * [darkTheme] follows the system setting by default. Both call sites in MainActivity
 * pass only a trailing lambda, so that default is what actually switches the app.
 *
 * Partial by design: this drives the ColorScheme, so everything reading a
 * MaterialTheme slot — directly or through Color.kt's aliases and ZadLuxe — follows it.
 * Tokens with no scheme slot (successColor, textTertiary, canvas*, surfaceContainer*)
 * are still static and stay light in dark mode, as do raw hex literals inside screens.
 * Closing that gap is the extended-colour work; until it lands, dark mode is incomplete.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ZadDarkColorScheme else ZadColorScheme,
        typography = Typography,
        shapes = ZadShapes,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalZadExtendedColors provides
                    if (darkTheme) ZadExtendedColorsDark else ZadExtendedColorsLight,
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                content()
            }
        }
    )
}
