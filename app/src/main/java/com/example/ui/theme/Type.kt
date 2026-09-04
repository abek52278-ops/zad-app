package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// ZAD Typography System — per "new ui ux" design contract:
//   Cairo (Arabic UI face) + Inter (SF Pro substitute for numbers/English).
// Item 36.2 — CairoFamily used to register only 600/700/800, but bodyLarge/bodyMedium/
// labelSmall below ask for Normal(400) and displayLarge/displayMedium ask for
// Black(900): neither weight had a matching instance, so Android substituted the
// nearest registered weight and Compose synthesized (faked) the rest — for body/label
// that meant most of the app's actual text (not just headlines) was silently rendering
// in the system font's weight approximation, not a real Cairo instance. Confirmed via
// the font file's own fvar table (fonttools: wght axis spans 200-1000) that both 400
// and 900 are real instances of this variable font, not synthesized guesses — matching
// res/font/cairo.xml, which now registers the same five weights.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
val CairoFamily = FontFamily(
    Font(R.font.cairo_variable, weight = FontWeight.Normal),
    Font(R.font.cairo_variable, weight = FontWeight.SemiBold),
    Font(R.font.cairo_variable, weight = FontWeight.Bold),
    Font(R.font.cairo_variable, weight = FontWeight.ExtraBold),
    Font(R.font.cairo_variable, weight = FontWeight.Black),
)
val InterFamily = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.SemiBold),
    Font(R.font.inter_variable, weight = FontWeight.Bold),
    Font(R.font.inter_variable, weight = FontWeight.ExtraBold),
)

private val displayFont = CairoFamily
private val headlineFont = CairoFamily

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = headlineFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = CairoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)
