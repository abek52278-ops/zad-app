package com.example.ui.theme

import android.os.Build
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ZadTheme v2 — the iOS-style design language rebuilt from "new ui ux/"
 */

object ZadV2 {
    // Brand greens
    val green900 = Color(0xFF052E16)
    val green800 = Color(0xFF064E3B)
    val green700 = Color(0xFF0B6B4E)
    val green600 = Color(0xFF0F9B76)
    val mint100 = Color(0xFFD9F2E6)
    val mint50 = Color(0xFFE6F4EC)
    val mintGlow = Color(0xFF6EE7B7)

    // Neutrals
    val ink = Color(0xFF0F172A)
    val slate = Color(0xFF374151)
    val gray500 = Color(0xFF6B7280)
    val gray400 = Color(0xFF9CA3AF)
    val hairline = Color(0x0D000000)

    // Canvases
    val canvasWarm = Color(0xFFFBFAF8)
    val canvasCoolTop = Color(0xFFF4F5F7)
    val canvasCoolBottom = Color(0xFFE9ECEF)

    // Semantic
    val warn = Color(0xFFB45309)
    val danger = Color(0xFFDC5B4B)
    val info = Color(0xFF2563EB)
    val violet = Color(0xFF7C3AED)
    val amberDot = Color(0xFFF4A93B)
    val coralDot = Color(0xFFFF8066)

    // Category tile fills
    val tileInvBg = Color(0xFFE3F5EC);  val tileInvFg = Color(0xFF0B6B4E)
    val tileShopBg = Color(0xFFFCEEE3); val tileShopFg = Color(0xFFC2703D)
    val tileFamilyBg = Color(0xFFF1EAFB); val tileFamilyFg = Color(0xFF7C3AED)
    val tileSubsBg = Color(0xFFE8F1FC); val tileSubsFg = Color(0xFF2563EB)
    val tilePharmBg = Color(0xFFFCE8ED); val tilePharmFg = Color(0xFFDC5B4E)
    val tileMaintBg = Color(0xFFFDF3E1); val tileMaintFg = Color(0xFFB45309)
    val tileTasbihaBg = Color(0xFFF3E8FF); val tileTasbihaFg = Color(0xFF9333EA)

    val aiPlate = Color(0xFF052E16)

    // Radii
    val rCard = RoundedCornerShape(16.dp)
    val rCardLg = RoundedCornerShape(18.dp)
    val rSheet = RoundedCornerShape(24.dp)
    val rHero = RoundedCornerShape(28.dp)
    val rPill = RoundedCornerShape(999.dp)

    val heroAmountBrush = Brush.verticalGradient(listOf(Color.White, mint100))
}

/** Glassmorphic blur support for Android 12+ */
fun Modifier.glassBlur(radius: Float = 16f): Modifier = this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = android.graphics.RenderEffect.createBlurEffect(
            radius, radius, android.graphics.Shader.TileMode.DECAL
        ).let { it.asComposeRenderEffect() }
    }
}

fun Modifier.zadV2Card(shape: Shape = ZadV2.rCard): Modifier = this
    .shadow(
        elevation = 12.dp,
        shape = shape,
        ambientColor = Color(0x0A0F172A),
        spotColor = Color(0x120F172A),
    )
    .clip(shape)
    .background(Color.White)

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

@Composable
fun ZadSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink, modifier = modifier)
}

@Composable
fun ZadStatCell(caption: String, value: String, modifier: Modifier = Modifier, valueSize: TextUnit = 24.sp) {
    Column(modifier.zadV2Card().padding(horizontal = 14.dp, vertical = 14.dp)) {
        Text(caption, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.gray500)
        Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold, color = ZadV2.ink)
    }
}
