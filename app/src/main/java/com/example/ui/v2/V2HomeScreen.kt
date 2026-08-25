package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadTransaction
import com.example.ui.theme.ZadV2
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate

/**
 * V2 home — the "Apple Wallet" dashboard from the Gemini render:
 * wallet-style balance card, daily-spend-safety + days-left widgets,
 * spend-trend mini chart, AI summary plate, recent transactions.
 * All numbers come from the real ZadViewModel — no mock data.
 */

@Composable
fun V2HomeScreen(
    viewModel: ZadViewModel,
    onNavigateToBudget: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remaining by viewModel.remainingBalance.collectAsState()
    val spent by viewModel.spentThisCycle.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val daysLeft by viewModel.daysLeftInCycle.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    if (remaining == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ZadV2.green800)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Wallet balance card ──────────────────────────────────────────────
        item {
            V2WalletCard(
                availableText = CurrencyFormatter.format(context, remaining ?: 0.0),
                spentText = CurrencyFormatter.format(context, spent),
                committedText = CurrencyFormatter.format(context, committed),
                onOpen = onNavigateToBudget,
            )
        }
        // ── Widgets: daily safety + days left ────────────────────────────────
        item {
            val dailySafe = if (daysLeft > 0) (remaining ?: 0.0) / daysLeft else null
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                V2WidgetCell(
                    caption = stringResource(R.string.v2_daily_safe),
                    value = dailySafe?.let { CurrencyFormatter.format(context, it) }
                        ?: "—",
                    modifier = Modifier.weight(1f),
                )
                V2WidgetCell(
                    caption = stringResource(R.string.v2_days_left),
                    value = "$daysLeft",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // ── Spend trend (last 7 days from real transactions) ────────────────
        item { V2SpendTrendCard(transactions = transactions) }
        // ── Recent transactions ─────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.v2_recent_tx),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZadV2.ink,
                )
                transactions.take(5).forEach { tx ->
                    V2TxRow(
                        title = tx.title,
                        date = tx.createdAt?.take(10) ?: "",
                        amount = CurrencyFormatter.format(context, tx.amount),
                        negative = tx.isExpense,
                    )
                }
            }
        }
    }
}

/** Wallet-style card: deep emerald #052E16→#064E3B gradient, dotted texture feel. */
@Composable
private fun V2WalletCard(
    availableText: String,
    spentText: String,
    committedText: String,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rHero)
            .background(Brush.linearGradient(listOf(Color(0xFF052E16), Color(0xFF064E3B))))
            .clickable { onOpen() }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.v2_available),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = Color.White.copy(alpha = 0.72f),
        )
        Text(
            availableText,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.2).sp,
            style = androidx.compose.ui.text.TextStyle(brush = ZadV2.heroAmountBrush),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.example.ui.theme.ZadGlassChip(
                label = stringResource(R.string.v2_spent),
                value = spentText,
                dotColor = ZadV2.amberDot,
            )
            com.example.ui.theme.ZadGlassChip(
                label = stringResource(R.string.v2_committed),
                value = committedText,
                dotColor = ZadV2.coralDot,
            )
        }
    }
}

/** Minimal widget cell: white card, gray caption, big ink number. */
@Composable
fun V2WidgetCell(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.zadV2CardLocal().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.gray500)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
    }
}

private fun Modifier.zadV2CardLocal(): Modifier =
    this.then(
        Modifier
            .clip(ZadV2.rCard)
            .background(Color.White)
    )

/** Last-7-days expense bars — real data only; hidden when no expenses exist. */
@Composable
fun V2SpendTrendCard(transactions: List<ZadTransaction>) {
    val perDay = HashMap<LocalDate, Double>()
    val today = LocalDate.now()
    transactions.filter { it.isExpense }.forEach { tx ->
        val date = tx.createdAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
        perDay.merge(date, tx.amount, Double::plus)
    }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val max = days.maxOfOrNull { perDay[it] ?: 0.0 } ?: 0.0
    if (max <= 0.0) return // لا رسم ببيانات مختلقة — لو مفيش مصروف الأسبوع، القسم يتخفي
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCardLg)
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.v2_spend_trend),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ZadV2.slate,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day ->
                val v = perDay[day] ?: 0.0
                val frac = if (max > 0) (v / max).toFloat() else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((8 + 64 * frac).dp)
                        .clip(CircleShape)
                        .background(if (frac > 0f) Brush.verticalGradient(listOf(ZadV2.green600, ZadV2.green800)) else Brush.verticalGradient(listOf(Color(0x110F172A), Color(0x110F172A)))),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach {
                Text(
                    it.dayOfWeek.name.take(1),
                    fontSize = 10.sp,
                    color = ZadV2.gray400,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
