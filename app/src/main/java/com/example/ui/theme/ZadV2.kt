package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ZadTheme v2 — the iOS-style design language rebuilt from "new ui ux/"
 * (the Gemini render, both reference screenshots, and the standalone HTML
 * prototype's exact CSS values).
 *
 * Every token below is copied from the prototype's inline styles, not guessed:
 * - hero: `linear-gradient(120deg,#0B6B4E,#0F9B76,#064E3B,#0B6B4E)`, 300% size
 *   with a 9s mesh-shift loop in the prototype; inner top hairline
 *   rgba(255,255,255,.2); drop shadow rgba(6,78,59,.35).
 * - glass chips: white @14% fill + blur(10px) + white @14% hairline border.
 * - cards: white, radius 16-28, shadow `0 1px 2px rgba(15,23,42,.04),
 *   0 8px 20px rgba(15,23,42,.07)` — approximated as Compose elevation+spot.
 *
 * This file is additive: it does NOT touch Color.kt so screens migrate one by
 * one without a flag-day rewrite.
 */

// ── v2 palette (prototype-exact) ────────────────────────────────────────────
object ZadV2 {
    // Brand greens (prototype ic()/buttons/hero)
    val green900 = Color(0xFF052E16)
    val green800 = Color(0xFF064E3B)   // primary action color everywhere
    val green700 = Color(0xFF0B6B4E)   // hero gradient start/end
    val green600 = Color(0xFF0F9B76)   // hero gradient mid, success accents
    val mint100 = Color(0xFFD9F2E6)    // hero amount text-gradient bottom
    val mint50 = Color(0xFFE6F4EC)     // logo tile bg
    val mintGlow = Color(0xFF6EE7B7)   // AI summary title on dark plate

    // Neutrals
    val ink = Color(0xFF0F172A)        // primary text
    val slate = Color(0xFF374151)      // secondary-strong text / inactive icons
    val gray500 = Color(0xFF6B7280)    // body-secondary
    val gray400 = Color(0xFF9CA3AF)    // captions, inactive nav
    val hairline = Color(0x0D000000)   // black @5% borders

    // Canvases: warm splash / cool app
    val canvasWarm = Color(0xFFFBFAF8)
    val canvasCoolTop = Color(0xFFF4F5F7)
    val canvasCoolBottom = Color(0xFFE9ECEF)

    // Semantic (prototype kindDot / statusColor maps)
    val warn = Color(0xFFB45309)
    val danger = Color(0xFFDC5B4B)
    val info = Color(0xFF2563EB)
    val violet = Color(0xFF7C3AED)
    val amberDot = Color(0xFFF4A93B)   // spent chip dot + glow
    val coralDot = Color(0xFFFF8066)   // committed chip dot + glow

    // Category tile fills (SHORTCUTS/MORE_ITEMS bg+fg pairs, verbatim)
    val tileInvBg = Color(0xFFE3F5EC);  val tileInvFg = Color(0xFF0B6B4E)
    val tileShopBg = Color(0xFFFCEEE3); val tileShopFg = Color(0xFFC2703D)
    val tileFamilyBg = Color(0xFFF1EAFB); val tileFamilyFg = Color(0xFF7C3AED)
    val tileSubsBg = Color(0xFFE8F1FC); val tileSubsFg = Color(0xFF2563EB)
    val tilePharmBg = Color(0xFFFCE8ED); val tilePharmFg = Color(0xFFDC5B4E)
    val tileMaintBg = Color(0xFFFDF3E1); val tileMaintFg = Color(0xFFB45309)

    // AI summary dark plate
    val aiPlate = Color(0xFF052E16)

    // Radii (prototype: cards 16, obligations 18, sheets 24, hero 28)
    val rCard = RoundedCornerShape(16.dp)
    val rCardLg = RoundedCornerShape(18.dp)
    val rSheet = RoundedCornerShape(24.dp)
    val rHero = RoundedCornerShape(28.dp)
    val rPill = RoundedCornerShape(999.dp)

    /** The hero mesh gradient (`zadMeshShift` loop, static frame here). */
    val heroBrush = Brush.linearGradient(
        colors = listOf(green700, green600, green800, green700),
    )

    /** Hero amount text gradient (`linear-gradient(180deg,#fff,#D9F2E6)`). */
    val heroAmountBrush = Brush.verticalGradient(listOf(Color.White, mint100))
}

/**
 * Prototype `card()` helper: white card, radius 16 default, double-shadow
 * `0 1px 2px rgba(15,23,42,.04), 0 8px 20px rgba(15,23,42,.07)` approximated
 * with a soft Compose elevation + tinted spot color.
 */
fun Modifier.zadV2Card(shape: Shape = ZadV2.rCard): Modifier = this
    .shadow(
        elevation = 12.dp,
        shape = shape,
        ambientColor = Color(0x0A0F172A),
        spotColor = Color(0x120F172A),
    )
    .clip(shape)
    .background(Color.White)

/** Glass chip from the hero (spent/committed pills), prototype-exact styling. */
@Composable
fun ZadGlassChip(
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(ZadV2.rPill)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), ZadV2.rPill)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            "$label: $value",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Section header like "زاد الذكي" — 15sp bold ink. */
@Composable
fun ZadSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink, modifier = modifier)
}

/** Small gray caption above a big number (HOME_STATS pattern). */
@Composable
fun ZadStatCell(caption: String, value: String, modifier: Modifier = Modifier, valueSize: TextUnit = 24.sp) {
    Column(modifier.zadV2Card().padding(horizontal = 14.dp, vertical = 14.dp)) {
        Text(caption, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.gray500)
        Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold, color = ZadV2.ink)
    }
}
