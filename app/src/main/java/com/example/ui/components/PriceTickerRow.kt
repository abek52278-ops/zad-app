package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.surface

/**
 * Ticker أسعار متحرك — نسخة حرفية من البروتوتايب (zad_premium_v5 `.ticker`):
 * - المسار مكرر مرتين وبيتحرك بأنيميشن `tickerMove 22s linear infinite` (translateX 0→50%).
 * - قناع تلاشي جانبي `mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent)`
 *   منفّذ بـ drawWithContent + BlendMode.DstIn.
 * - الحبوب: padding 13×8، اسم Bold 12.5sp، نسبة w800 صغيرة.
 * بيتغذى من بيانات حقيقية لو موجودة، ولو مفيش بيتخفي (ممنوع أرقام مخترعة).
 */
data class PriceTick(val name: String, val deltaPercent: Double?)

@Composable
fun PriceTickerRow(ticks: List<PriceTick>, modifier: Modifier = Modifier) {
    if (ticks.isEmpty()) return
    val density = LocalDensity.current

    // مسار التكرار: نفس الصف مرتين جنب بعض، والحركة 0→50% بتخلق لوب سلس تماماً
    val marqueeProgress by rememberInfiniteTransition(label = "tickerMove").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing)
        ),
        label = "tickerTrackX"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9999.dp))
            .drawWithContent {
                // mask-image من البروتوتايب: تلاشي شفاف في أول/آخر 8% من العرض
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.08f to Color.Black,
                            0.92f to Color.Black,
                            1f to Color.Transparent
                        )
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = -marqueeProgress * (size.width / 2f)
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (ticks + ticks).forEach { tick ->
                TickerPill(tick)
            }
        }
    }
}

@Composable
private fun TickerPill(tick: PriceTick) {
    val color = when {
        tick.deltaPercent == null -> Color(0xFF9CA3AF)
        tick.deltaPercent > 0 -> Color(0xFF064E3B)
        tick.deltaPercent < 0 -> Color(0xFFDC5B4B)
        else -> Color(0xFF9CA3AF)
    }
    val deltaText = when {
        tick.deltaPercent == null -> "—"
        else -> String.format("%+.0f%%", tick.deltaPercent)
    }
    Row(
        modifier = Modifier
            .background(surface, RoundedCornerShape(9999.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(tick.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text(deltaText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
