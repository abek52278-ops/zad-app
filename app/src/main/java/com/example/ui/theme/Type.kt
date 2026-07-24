package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ZAD Typography System
//
// IBM Plex Sans Arabic (the Zad DESIGN.md's actual typeface) needs real
// bundled .ttf files or a verified Google-Fonts-provider certificate — neither
// is safely producible in this environment (no network, no binary asset
// generation), so this uses Android's built-in system font families instead.
// NOTE: Arabic script glyph *shapes* always render through Android's system
// Arabic-fallback font regardless of the requested Latin family name — this
// swap changes weight/silhouette (and any Latin/numeric text) but will not
// change Arabic letterforms the way a real IBM Plex Sans Arabic asset would.
// Swap `displayFont`/`headlineFont` for a real FontFamily once .ttf files are
// added to res/font/.
private val displayFont = FontFamily(
    Font(familyName = DeviceFontFamilyName("sans-serif-black"), weight = FontWeight.Black),
    Font(familyName = DeviceFontFamilyName("sans-serif-black"), weight = FontWeight.Bold),
)
private val headlineFont = FontFamily(
    Font(familyName = DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.SemiBold),
    Font(familyName = DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Bold),
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)
