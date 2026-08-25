package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun V2BudgetScreen(
    viewModel: ZadViewModel,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remaining by viewModel.remainingBalance.collectAsState()
    val spent by viewModel.spentThisCycle.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val daysLeft by viewModel.daysLeftInCycle.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var showQuickExpense by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Apple-Wallet card with Animated Mesh
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZadV2.rHero)
            ) {
                AnimatedMeshGradient(
                    modifier = Modifier.matchParentSize(),
                    colors = listOf(Color(0xFF0A382C), Color(0xFF064E3B), ZadV2.green700)
                )
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.v2_available),
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Text(
                        CurrencyFormatter.format(context, remaining ?: 0.0),
                        fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                        style = androidx.compose.ui.text.TextStyle(brush = ZadV2.heroAmountBrush),
                    )
                    Text(
                        stringResource(R.string.v2_spent_colon, CurrencyFormatter.format(context, spent))
                            + " · " + stringResource(R.string.v2_committed_colon, CurrencyFormatter.format(context, committed)),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        
        // Minimal widgets row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val dailySafe = if (daysLeft > 0) (remaining ?: 0.0) / daysLeft else null
                V2WidgetCell(
                    caption = stringResource(R.string.v2_daily_safe),
                    value = dailySafe?.let { CurrencyFormatter.format(context, it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                V2WidgetCell(
                    caption = stringResource(R.string.v2_days_left),
                    value = stringResource(R.string.v2_days_value, daysLeft),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        
        // Bezier smooth curve
        item {
            V2BezierChart(transactions = transactions)
        }
        
        // Quick expense button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ZadV2.green800)
                    .pressableScale { showQuickExpense = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.v2_quick_expense),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        
        // Recent transactions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.v2_recent_tx), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
                transactions.take(6).forEach { tx ->
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

    if (showQuickExpense) {
        V2QuickExpenseSheet(
            onDismiss = { showQuickExpense = false },
            onSend = { name, total ->
                viewModel.addTransaction(
                    amount = total,
                    title = name,
                    isExpense = true,
                    category = "Other",
                )
                showQuickExpense = false
            },
        )
    }
}

@Composable
private fun V2QuickExpenseSheet(
    onDismiss: () -> Unit,
    onSend: (String, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    val totalValue = total.replace(",", ".").toDoubleOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZadV2.rSheet)
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.v2_quick_expense), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.v2_expense_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZadV2.green800),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = total,
                onValueChange = { total = it },
                label = { Text(stringResource(R.string.v2_expense_total)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZadV2.green800),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty() && totalValue != null && totalValue > 0) onSend(n, totalValue)
                },
                enabled = name.trim().isNotEmpty() && totalValue != null && totalValue > 0,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZadV2.green800),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.v2_send), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun V2BezierChart(transactions: List<ZadTransaction>) {
    val perDay = HashMap<LocalDate, Double>()
    val today = LocalDate.now()
    transactions.filter { it.isExpense }.forEach { tx ->
        val d = tx.createdAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
        perDay.merge(d, tx.amount, Double::plus)
    }
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { perDay[it] ?: 0.0 }
    val max = values.maxOrNull() ?: 0.0
    if (max <= 0.0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCardLg)
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.v2_spend_trend), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV2.slate)
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(140.dp),
        ) {
            val w = size.width
            val h = size.height
            val step = w / (values.size - 1).coerceAtLeast(1)
            val points = values.mapIndexed { i, v ->
                androidx.compose.ui.geometry.Offset(i * step, h - 12f - ((v / max).toFloat() * (h - 24f)))
            }
            fun bez(path: Path, pts: List<androidx.compose.ui.geometry.Offset>) {
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]; val p1 = pts[i]
                    val cx = (p0.x + p1.x) / 2f
                    path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            val line = Path().also { bez(it, points) }
            val fill = Path().also {
                bez(it, points)
                it.lineTo(points.last().x, h); it.lineTo(points.first().x, h); it.close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(listOf(Color(0x330F9B76), Color(0x000F9B76))),
            )
            drawPath(line, color = ZadV2.green800, style = Stroke(width = 6f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(days.first().let { "${it.dayOfMonth}/${it.monthValue}" }, fontSize = 10.sp, color = ZadV2.gray400)
            Text(days.last().let { "${it.dayOfMonth}/${it.monthValue}" }, fontSize = 10.sp, color = ZadV2.gray400)
        }
    }
}
