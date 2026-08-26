package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadInsight
import com.example.ui.theme.ZadMeterBar
import com.example.ui.theme.ZadV3
import com.example.ui.theme.zadCardShadow
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

/**
 * V3 Assistant Screen — segmented pill tabs (Overview / Behavior / Chat) per the
 * prototype renderAssistant, keeping the glass orb hero + tap-to-talk + knowledge map.
 */
@Composable
fun V3AssistantScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onOpenVoice: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zadInsights by viewModel.zadInsights.collectAsState()
    var tab by remember { mutableStateOf("overview") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Orb hero + tap-to-talk + knowledge entry
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(110.dp).floatingIdle(2500).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(ZadV3.green600, ZadV3.green800)))
                        .border(5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .pressableScale(onClick = onOpenVoice),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.matchParentSize().ringSpin(12000).border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(2) {
                            Box(Modifier.size(width = 11.dp, height = 24.dp).clip(RoundedCornerShape(99.dp)).background(Color.White))
                        }
                    }
                }
                Text(stringResource(R.string.v2_talk_to_zad), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
            }
        }

        // Segmented tabs
        item {
            V3SegmentTabs(
                tabs = listOf(
                    "overview" to stringResource(R.string.v3x_tab_overview),
                    "behavior" to stringResource(R.string.v3x_tab_behavior),
                    "chat" to stringResource(R.string.v3x_tab_chat),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
        }

        when (tab) {
            "behavior" -> {
                if (zadInsights.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.v2_no_insights), fontSize = 13.sp, color = ZadV3.gray400) } }
                } else {
                    items(zadInsights.size) { i -> V3BehaviorCard(zadInsights[i], i) }
                }
            }
            "chat" -> {
                item { V3AssistantChat(viewModel) }
            }
            else -> { item { V3AssistantOverview(viewModel) } }
        }

        // Knowledge map navigation
        item {
            Box(
                modifier = Modifier.fillMaxWidth().clip(ZadV3.rCard).background(ZadV3.mint50)
                    .pressableScale(onClick = onNavigateToKnowledge).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("🗺️", fontSize = 20.sp) }
                    Column {
                        Text(stringResource(R.string.nav_assistant), fontWeight = FontWeight.Bold, color = ZadV3.green800)
                        Text(stringResource(R.string.v2_mind_subtitle), fontSize = 12.sp, color = ZadV3.green700)
                    }
                }
            }
        }
    }
}

@Composable
private fun V3SegmentTabs(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rPill).clip(ZadV3.rPill)
            .background(Color.White).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { idx, (key, label) ->
            val on = selected == key
            Box(
                modifier = Modifier.weight(1f).clip(ZadV3.rPill)
                    .fadeUpOnAppear(idx * 40L)
                    .background(if (on) ZadV3.green800 else Color.Transparent)
                    .pressableScale { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else ZadV3.gray500)
            }
        }
    }
}

// ── Overview tab ──────────────────────────────────────────────────────────────

@Composable
private fun V3AssistantOverview(viewModel: ZadViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val remaining by viewModel.remainingBalance.collectAsState()
    val spent by viewModel.spentThisCycle.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val subs by viewModel.subscriptions.collectAsState()
    val daysLeft by viewModel.daysLeftInCycle.collectAsState()

    val spendingPowerPct = remember(spent, remaining) {
        val total = spent + (remaining ?: 0.0)
        if (total > 0.0) ((remaining ?: 0.0) / total * 100.0).toInt().coerceIn(0, 100) else 82
    }

    // Category breakdown from real transactions
    val catSpend = remember(transactions) {
        val byCat = HashMap<String, Double>()
        transactions.filter { it.isExpense }.forEach { tx -> byCat.merge(tx.category ?: "—", tx.amount, Double::plus) }
        byCat.entries.sortedByDescending { it.value }.take(5)
    }
    val catColors = listOf(Color(0xFF0F9B76), Color(0xFF2563EB), Color(0xFFB45309), Color(0xFFDC5B4B), Color(0xFF7C3AED))
    val catMax = catSpend.firstOrNull()?.value ?: 1.0

    // Sparkline of last-7-day spend
    val trend = remember(transactions) {
        val perDay = HashMap<java.time.LocalDate, Double>()
        transactions.filter { it.isExpense }.forEach { tx ->
            tx.createdAt?.take(10)?.let { d -> runCatching { java.time.LocalDate.parse(d) }.getOrNull() }
                ?.let { perDay.merge(it, tx.amount, Double::plus) }
        }
        (6 downTo 0).map { perDay[java.time.LocalDate.now().minusDays(it.toLong())] ?: 0.0 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Dark spending-power panel with gradient and breathing glow (مطابق لـ pAssistant)
        Column(
            modifier = Modifier.fillMaxWidth().clip(ZadV3.rCardLg)
                .background(Brush.linearGradient(listOf(Color(0xFF052E16), Color(0xFF0A382C))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.v3x_spending_power), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.mintGlow)
            Text("$spendingPowerPct%", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            ZadMeterBar(progress = spendingPowerPct / 100f, color = ZadV3.mintGlow, height = 8.dp, trackColor = Color.White.copy(alpha = 0.15f))
        }

        // Sparkline chart card — live spend pulse
        Column(
            modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCardLg).clip(ZadV3.rCardLg).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.v3x_live_spend), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                Text(stringResource(R.string.v3x_last_7_days), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray400)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                val w = size.width; val h = size.height
                val maxV = trend.maxOrNull()?.takeIf { it > 0 } ?: return@Canvas
                val minV = trend.minOrNull() ?: 0.0
                val pts = trend.mapIndexed { i, v ->
                    Offset(i / (trend.size - 1).coerceAtLeast(1) * w, h - (((v - minV) / ((maxV - minV).coerceAtLeast(0.001))) * (h - 8)).toFloat() - 4f)
                }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) cubicTo((pts[i - 1].x + pts[i].x) / 2f, pts[i - 1].y, (pts[i - 1].x + pts[i].x) / 2f, pts[i].y, pts[i].x, pts[i].y)
                }
                drawPath(path, color = Color(0xFF0F9B76), style = Stroke(width = 5f))
            }
        }

        // Category breakdown bars
        if (catSpend.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCardLg).clip(ZadV3.rCardLg).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.v3x_category_breakdown), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                catSpend.forEachIndexed { i, (cat, amt) ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.slate)
                            Text(CurrencyFormatter.format(context, amt), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                        }
                        ZadMeterBar(progress = (amt / catMax).toFloat(), color = catColors[i % catColors.size], height = 7.dp)
                    }
                }
            }
        }

        // 2x2 stat grid from live data
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3StatCell(stringResource(R.string.v2_days_left), stringResource(R.string.v2_days_value, daysLeft), Modifier.weight(1f))
            V3StatCell(stringResource(R.string.v3x_active_subs), "${subs.count { it.isActive }}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3StatCell(stringResource(R.string.v2_spent), CurrencyFormatter.format(context, spent), Modifier.weight(1f))
            V3StatCell(stringResource(R.string.v2_available), CurrencyFormatter.format(context, remaining ?: 0.0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun V3StatCell(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White).padding(14.dp)) {
        Text(caption, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray400)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

// ── Behavior tab ──────────────────────────────────────────────────────────────

@Composable
private fun V3BehaviorCard(insight: ZadInsight, index: Int = 0) {
    Column(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
            .fadeUpOnAppear(index * 70L).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(insight.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
        if (insight.body.isNotBlank()) Text(insight.body, fontSize = 12.5.sp, color = ZadV3.gray500, lineHeight = 17.sp)
    }
}

// ── Chat tab ──────────────────────────────────────────────────────────────────

@Composable
private fun V3AssistantChat(viewModel: ZadViewModel) {
    val messages by viewModel.aiChatMessages.collectAsState()
    var inputQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        messages.takeLast(16).forEach { msg ->
            V3ChatBubble(text = msg.text, user = msg.isUser)
        }

        // Quick chip
        Box(
            modifier = Modifier.clip(ZadV3.rPill).background(Color.White)
                .border(1.dp, ZadV3.green800.copy(alpha = 0.2f), ZadV3.rPill)
                .pressableScale(onClick = { viewModel.sendAiChatMessage("حلل لي مصاريفي هذا الشهر") }).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.v3x_quick_chip), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.green800)
        }

        // Chat input box (.chat-input pill with send button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .zadCardShadow(ZadV3.rPill)
                .clip(ZadV3.rPill)
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = ZadV3.ink,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                ),
                decorationBox = { innerTextField ->
                    if (inputQuery.isEmpty()) {
                        Text(
                            stringResource(R.string.ask_zad_placeholder),
                            fontSize = 13.5.sp,
                            color = ZadV3.gray400
                        )
                    }
                    innerTextField()
                }
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ZadV3.green800)
                    .pressableScale(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                val txt = inputQuery
                                inputQuery = ""
                                viewModel.sendAiChatMessage(txt)
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send_action),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/** Shared chat bubble style (user = green right-aligned, assistant = white left). */
@Composable
internal fun V3ChatBubble(text: String, user: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (user) ZadV3.green800 else Color.White)
                .then(if (!user) Modifier.zadCardShadow(RoundedCornerShape(16.dp), 6.dp) else Modifier)
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(
                text, fontSize = 14.sp, lineHeight = 20.sp,
                color = if (user) Color.White else ZadV3.ink,
            )
        }
    }
}
