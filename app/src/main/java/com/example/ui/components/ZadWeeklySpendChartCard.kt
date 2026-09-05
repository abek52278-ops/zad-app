package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ZadTransaction
import com.example.ui.theme.ZadLuxe
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.example.R

/** آخر ٧ أيام من مصروفات حقيقية (zad_transactions)، مجمّعة يوميًا — لا بيانات مختلقة. */
fun computeWeeklySpend(transactions: List<ZadTransaction>): List<Pair<String, Double>> {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val spentByDay = HashMap<java.time.LocalDate, Double>()
    transactions.forEach { tx ->
        if (!tx.isExpense) return@forEach
        val created = tx.createdAt ?: return@forEach
        val date = try {
            Instant.parse(created).atZone(zone).toLocalDate()
        } catch (e: Exception) {
            return@forEach
        }
        if (date in days) {
            spentByDay[date] = (spentByDay[date] ?: 0.0) + tx.amount
        }
    }
    return days.map { d ->
        val label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ar")).take(3)
        label to (spentByDay[d] ?: 0.0)
    }
}

/** كارت المصروف الأسبوعي — Bezier chart جاهز (ZadBezierSpendChart) داخل كارت زجاجي فاخر. */
@Composable
fun ZadWeeklySpendChartCard(
    transactions: List<ZadTransaction>,
    modifier: Modifier = Modifier
) {
    val weeklySpend = androidx.compose.runtime.remember(transactions) { computeWeeklySpend(transactions) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZadLuxe.squircle)
            .background(ZadLuxe.cardWhite)
            .border(0.5.dp, ZadLuxe.hairline, ZadLuxe.squircle)
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.weekly_spend_chart_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ZadLuxe.emerald
        )
        ZadBezierSpendChart(weeklySpend = weeklySpend)
    }
}
