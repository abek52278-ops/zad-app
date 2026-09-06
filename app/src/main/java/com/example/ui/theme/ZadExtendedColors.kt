package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours the app needs that Material's ColorScheme has no slot for.
 *
 * `MaterialTheme.colorScheme` covers primary/surface/error and friends, but nothing in
 * it means "success", "tertiary text", or "the middle stop of the canvas gradient".
 * Those lived as top-level vals, which are fixed at class init and therefore invisible
 * to a theme switch — so after the dark scheme landed they stayed light while everything
 * around them went dark. This carries them through the composition instead, so a single
 * value swapped in AppTheme moves all of them at once.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value only changes
 * when the theme itself changes, and that should invalidate the whole subtree anyway.
 */
@Immutable
data class ZadExtendedColors(
    val success: Color,
    val info: Color,
    val textTertiary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondaryDark: Color,
    val secondaryLight: Color,
    val coral: Color,
    val coralLight: Color,
    val lilac: Color,
    val canvasTop: Color,
    val canvasMid: Color,
    val canvasBottom: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    /** Decorative radial washes behind the auth/onboarding canvas. */
    val authWashWarm: Color,
    val authWashCool: Color,
    /**
     * Categorical chart palette — colours whose only job is to tell series apart.
     *
     * Deliberately NOT the semantic tokens. The third slice of a spending breakdown is
     * not "a warning" and the fourth is not "an error"; reusing `warningColor` /
     * `dangerColor` there would attach a meaning the data does not have, and would also
     * make two series collide the moment a semantic token is retuned.
     *
     * Ordered by how distinguishable adjacent entries are, because the consumer takes
     * them in order (`palette[index % size]`) — so neighbouring categories must not look
     * alike.
     */
    val chartCategorical: List<Color>,
    /** Body text on a card that is not the primary label — dimmer than onSurface. */
    val textOnCardSecondary: Color,
)

/** Exactly the values these tokens held before the extended-colour mechanism existed. */
val ZadExtendedColorsLight = ZadExtendedColors(
    success = Color(0xFF238652),
    info = Color(0xFF2B6CB0),
    textTertiary = Color(0xFF9EA197),
    primaryDark = ZadForestEmeraldDark,
    primaryLight = ZadForestEmeraldLight,
    secondaryDark = ZadMustardDark,
    secondaryLight = ZadMustardLight,
    coral = ZadTerracottaRust,
    coralLight = ZadTerracottaContainer,
    lilac = Color(0xFF7C6F93),
    canvasTop = Color(0xFFF8F9FA),
    canvasMid = Color(0xFFF4F6F2),
    canvasBottom = Color(0xFFEDEFE9),
    surfaceContainerLow = Color(0xFFFBFBFA),
    surfaceContainer = ZadIosBackground,
    surfaceContainerHigh = ZadIosSurfaceVariant,
    authWashWarm = Color(0xFFFCD3C7),
    authWashCool = Color(0xFFBFE3D1),
    // Exactly the values CategoryBreakdownCard used as ZadV3.green600 / info / warn /
    // danger / violet, in the same order — so the light rendering is unchanged.
    chartCategorical = listOf(
        Color(0xFF0F9B76),
        Color(0xFF2563EB),
        Color(0xFFB45309),
        Color(0xFFDC5B4B),
        Color(0xFF7C3AED),
    ),
    textOnCardSecondary = Color(0xFF374151),
)

/**
 * Dark counterparts. Semantics are preserved rather than the hex being inverted:
 * `success` stays a green that reads as success, `coralLight` stays the *container*
 * behind coral (so it goes dark, not pale), and `textTertiary` stays dimmer than
 * onSurfaceVariant so the three text weights keep their order.
 *
 * `primaryDark`/`primaryLight` keep their relative direction — darker and lighter than
 * `primary` — because both are used as gradient stops against it.
 */
val ZadExtendedColorsDark = ZadExtendedColors(
    success = Color(0xFF4FBF87),
    info = Color(0xFF7FB3E8),
    textTertiary = Color(0xFF7F847A),
    primaryDark = Color(0xFF3E8F68),
    primaryLight = Color(0xFF95D9B5),
    secondaryDark = Color(0xFFC08A2C),
    secondaryLight = Color(0xFFF3C476),
    coral = ZadTerracottaLight,
    coralLight = Color(0xFF5A2A16),
    lilac = Color(0xFFB9A9D4),
    canvasTop = Color(0xFF10130F),
    canvasMid = Color(0xFF141712),
    canvasBottom = Color(0xFF191D17),
    surfaceContainerLow = Color(0xFF151813),
    surfaceContainer = ZadIosSurfaceDark,
    surfaceContainerHigh = Color(0xFF22261F),
    // Same role at dark luminance: a warm and a cool tint over the near-black canvas,
    // not the pastels — those wash out to a light screen, which is the bug being fixed.
    authWashWarm = Color(0xFF3A1E16),
    authWashCool = Color(0xFF14302A),
    // Same five hues, lifted to read on a dark ground. The light values are mid-tones
    // chosen against white; on near-black they lose separation and #B45309 in particular
    // goes muddy. Hue and order are preserved so a category keeps its colour identity
    // between themes — only luminance moves.
    chartCategorical = listOf(
        Color(0xFF34D399),
        Color(0xFF60A5FA),
        Color(0xFFE9A844),
        Color(0xFFF08A7A),
        Color(0xFFA78BFA),
    ),
    textOnCardSecondary = Color(0xFFB9BDB3),
)

val LocalZadExtendedColors = staticCompositionLocalOf { ZadExtendedColorsLight }
