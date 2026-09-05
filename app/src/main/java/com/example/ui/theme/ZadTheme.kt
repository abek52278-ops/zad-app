package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
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

// Built from the raw Zad* palette only. The lowercase aliases in Color.kt are
// `@Composable get()` accessors onto *this* scheme, so naming one here would be
// both circular and impossible — scheme construction is not a composable context.
val ZadColorScheme = lightColorScheme(
    primary = ZadForestEmerald,
    onPrimary = ZadOnAccent,
    primaryContainer = ZadEmeraldContainer,
    onPrimaryContainer = ZadForestEmerald,
    secondary = ZadMustardOchre,
    onSecondary = ZadOnAccent,
    secondaryContainer = ZadMustardContainer,
    onSecondaryContainer = ZadMustardDark,
    tertiary = ZadTerracottaRust,
    onTertiary = ZadOnAccent,
    tertiaryContainer = ZadTerracottaContainer,
    onTertiaryContainer = ZadTerracottaDark,
    background = ZadIosBackground,
    onBackground = ZadNeutralDark,
    surface = ZadIosSurface,
    onSurface = ZadNeutralDark,
    surfaceVariant = ZadIosSurfaceVariant,
    onSurfaceVariant = ZadNeutralMuted,
    outline = ZadIosOutline,
    outlineVariant = ZadOutlineVariant,
    error = ZadTerracottaRust,
    onError = ZadOnAccent,
    errorContainer = ZadTerracottaContainer,
    onErrorContainer = ZadTerracottaDark
)

// Same slots as ZadColorScheme, so every `@Composable get()` alias in Color.kt follows
// the theme automatically. Tokens with no slot here (successColor, textTertiary, the
// canvas/surfaceContainer family) are still static and stay light — that gap is the
// extended-colour work, not this scheme.
val ZadDarkColorScheme = darkColorScheme(
    primary = ZadEmeraldOnDark,
    onPrimary = ZadOnAccentDark,
    primaryContainer = ZadEmeraldContainerOnDark,
    onPrimaryContainer = ZadEmeraldOnDark,
    secondary = ZadMustardOnDark,
    onSecondary = ZadOnAccentDark,
    secondaryContainer = ZadMustardContainerOnDark,
    onSecondaryContainer = ZadMustardOnDark,
    tertiary = ZadTerracottaOnDark,
    onTertiary = ZadOnAccentDark,
    tertiaryContainer = ZadTerracottaContainerOnDark,
    onTertiaryContainer = ZadTerracottaOnDark,
    background = ZadIosBackgroundDark,
    onBackground = ZadNeutralOnDark,
    surface = ZadIosSurfaceDark,
    onSurface = ZadNeutralOnDark,
    surfaceVariant = ZadIosSurfaceVariantDark,
    onSurfaceVariant = ZadNeutralMutedOnDark,
    outline = ZadIosOutlineDark,
    outlineVariant = ZadIosOutlineDark,
    error = ZadTerracottaOnDark,
    onError = ZadOnAccentDark,
    errorContainer = ZadTerracottaContainerOnDark,
    onErrorContainer = ZadTerracottaOnDark
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
