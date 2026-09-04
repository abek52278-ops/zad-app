package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// =========================================================================
// ZAD Apple iOS / Cupertino HIG Theme & Design System
// =========================================================================

val ZadShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),    // inner category chips, status pills
    medium = RoundedCornerShape(20.dp),   // signature squircle cards
    large = RoundedCornerShape(28.dp),    // interactive buttons, floating bottom bar
    extraLarge = RoundedCornerShape(32.dp)
)

val ZadColorScheme = lightColorScheme(
    primary = ZadForestEmerald,
    onPrimary = onPrimary,
    primaryContainer = ZadEmeraldContainer,
    onPrimaryContainer = ZadForestEmerald,
    secondary = ZadMustardOchre,
    onSecondary = onSecondary,
    secondaryContainer = ZadMustardContainer,
    onSecondaryContainer = ZadMustardDark,
    tertiary = ZadTerracottaRust,
    onTertiary = onPrimary,
    tertiaryContainer = ZadTerracottaContainer,
    onTertiaryContainer = ZadTerracottaDark,
    background = ZadIosBackground,
    onBackground = ZadNeutralDark,
    surface = ZadIosSurface,
    onSurface = ZadNeutralDark,
    surfaceVariant = ZadIosSurfaceVariant,
    onSurfaceVariant = ZadNeutralMuted,
    outline = ZadIosOutline,
    outlineVariant = outlineVariant,
    error = ZadTerracottaRust,
    onError = onError,
    errorContainer = ZadTerracottaContainer,
    onErrorContainer = ZadTerracottaDark
)

@Composable
fun ZadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZadColorScheme,
        typography = Typography,
        shapes = ZadShapes
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            content()
        }
    }
}
