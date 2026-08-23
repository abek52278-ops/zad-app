package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ticker أسعار متحرك — نسخة من البروتوتايب: حبوب بيضاء فيها اسم الصنف
 * ونسبة التغير (أخضر صعود / أحمر هبوط / رمادي ثابت). بيتغذى من بيانات
 * حقيقية لو موجودة، ولو مفيش بيتخفي (ممنوع أرقام مخترعة).
 */
data class PriceTick(val name: String, val deltaPercent: Double?)

@Composable
fun PriceTickerRow(ticks: List<PriceTick>, modifier: Modifier = Modifier) {
    if (ticks.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ticks.forEach { tick ->
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
                    .background(Color.White, RoundedCornerShape(9999.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(tick.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(deltaText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
