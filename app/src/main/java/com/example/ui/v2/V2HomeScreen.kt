package com.example.ui.v2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.LocaleHelper
import com.example.ui.theme.ZadV3
import com.example.ui.viewmodels.ZadViewModel

// ── Tasbiha Garden (localStorage-equivalent persistence, daily reset) ────────

private const val TASBIHA_PREFS = "zad-tasbiha-garden"
private const val TASBIHA_GOAL = 100

private fun tasbihaPrefs(context: Context) =
    context.getSharedPreferences(TASBIHA_PREFS, Context.MODE_PRIVATE)

private fun loadTasbihaCount(context: Context): Int {
    val p = tasbihaPrefs(context)
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    return if (p.getString("date", null) == today) p.getInt("count", 0) else 0
}

private fun saveTasbihaCount(context: Context, count: Int) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    tasbihaPrefs(context).edit().putString("date", today).putInt("count", count).apply()
}

private data class ShortcutEntry(val route: String, val labelRes: Int, val bg: Color)

private val homeShortcuts = listOf(
    ShortcutEntry(V3Routes.INVENTORY, R.string.nav_inventory, ZadV3.tileInvBg),
    ShortcutEntry(V3Routes.SHOPPING, R.string.nav_shopping, ZadV3.tileShopBg),
    ShortcutEntry(V3Routes.FAMILY, R.string.nav_family, ZadV3.tileFamilyBg),
    ShortcutEntry(V3Routes.SUBS, R.string.subscriptions_title, ZadV3.tileSubsBg),
    ShortcutEntry(V3Routes.PHARMACY, R.string.nav_pharmacy, ZadV3.tilePharmBg),
    ShortcutEntry(V3Routes.MAINTENANCE, R.string.nav_maintenance, ZadV3.tileMaintBg),
)

@Composable
fun V3HomeScreen(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    onNavigateToBudget: () -> Unit,
    onOpenVoice: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remaining by viewModel.remainingBalance.collectAsState()
    val spent by viewModel.spentThisCycle.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val daysLeft by viewModel.daysLeftInCycle.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val insights by viewModel.zadInsights.collectAsState()
    val agentSummary by viewModel.agentSummary.collectAsState()
    val marketPrices by viewModel.livePrices.collectAsState()
    val marketFetchState by viewModel.marketPricesFetchState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadZadInsights() }
    LaunchedEffect(Unit) {
        if (marketFetchState == ZadViewModel.LiveFetchState.NotFetchedYet) {
            viewModel.refreshLiveMarketPrices()
        }
    }

    if (remaining == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ZadV3.green800)
        }
        return
    }

    var sectionIndex = 0
    fun nextDelay(): Long = (sectionIndex++ * 60L).coerceAtMost(600L)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── a. Live price ticker row ────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            com.example.ui.components.LiveMarketTicker(
                prices = marketPrices,
                fetchState = marketFetchState,
                onRetry = { viewModel.refreshLiveMarketPrices() },
            )
        }

        // ── b. Hero mesh-gradient card ─────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            V3HeroCard(
                availableText = CurrencyFormatter.format(context, remaining ?: 0.0),
                spentText = CurrencyFormatter.format(context, spent),
                committedText = CurrencyFormatter.format(context, committed),
                onOpen = onNavigateToBudget,
                modifier = Modifier.fadeUpOnAppear(nextDelay()),
            )
        }

        // ── c. Days-left + daily-safe widget cards ──────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            val dailySafe = if (daysLeft > 0) (remaining ?: 0.0) / daysLeft else null
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fadeUpOnAppear(nextDelay())) {
                V3WidgetCell(
                    caption = stringResource(R.string.v2_daily_safe),
                    value = dailySafe?.let { CurrencyFormatter.format(context, it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                V3WidgetCell(
                    caption = stringResource(R.string.v2_days_left),
                    value = stringResource(R.string.v2_days_value, daysLeft),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── d. Shortcuts grid (6 tiles) ────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                homeShortcuts.forEachIndexed { idx, sc ->
                    V3ShortcutTile(
                        emoji = "•",
                        label = stringResource(sc.labelRes),
                        bg = sc.bg,
                        onClick = { onNavigate(sc.route) },
                        modifier = Modifier.fadeUpOnAppear(idx * 60L),
                    )
                }
            }
        }

        // ── e. زاد الذكي insights feed (real data; hidden when empty) ──────────
        if (insights.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.v3_insights_title),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink,
                    )
                    insights.take(4).forEachIndexed { idx, insight ->
                        V3InsightCard(insight = insight, modifier = Modifier.fadeUpOnAppear(idx * 90L))
                    }
                }
            }
        }

        // ── g. Dark AI summary card — body from real agent summary only ────────
        agentSummary?.let { summary ->
            if (summary.summary.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    V3AiSummaryCard(
                        title = stringResource(R.string.v3_ai_summary_title),
                        body = summary.summary,
                        chips = listOf(
                            stringResource(R.string.v3_chip_cancel_sub),
                            stringResource(R.string.v3_chip_increase_budget),
                            stringResource(R.string.v2_see_all),
                        ),
                        modifier = Modifier.fadeUpOnAppear(nextDelay()),
                    )
                }
            }
        }

        // ── h. Tasbiha Garden glass card ───────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            V3TasbihaGardenCard(modifier = Modifier.fadeUpOnAppear(nextDelay()))
        }

        // ── i. Chef Zad card ───────────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fadeUpOnAppear(nextDelay())
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(ZadV3.tileMaintBg),
                    contentAlignment = Alignment.Center,
                ) { Text("👨‍🍳", fontSize = 26.sp) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.v3_chef_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                    Text(
                        stringResource(R.string.v3_chef_subtitle),
                        fontSize = 12.5.sp, color = ZadV3.gray500, lineHeight = 17.sp,
                    )
                }
            }
        }

        // ── k. Recent transactions ─────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fadeUpOnAppear(nextDelay())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.v2_recent_tx), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                    Text(
                        stringResource(R.string.v2_see_all),
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.green800,
                        modifier = Modifier.pressableScale(onClick = onNavigateToBudget),
                    )
                }
                transactions.take(3).forEachIndexed { idx, tx ->
                    Box(Modifier.fadeUpOnAppear(idx * 70L)) {
                        V3TxRow(
                            title = tx.title,
                            date = tx.createdAt?.take(10) ?: "",
                            amount = CurrencyFormatter.format(context, tx.amount),
                            negative = tx.isExpense,
                        )
                    }
                }
            }
        }

        // ── l. Talk-to-Zad voice pill ──────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fadeUpOnAppear(nextDelay())
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(ZadV3.rPill)
                    .background(ZadV3.green800)
                    .pressableScale(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.v2_talk_to_zad),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Insight row: kind dot (#064E3B/#B45309/#DC5B4B) + title/subtitle + tag pill */
@Composable
private fun V3InsightCard(
    insight: com.example.data.ZadInsight,
    modifier: Modifier = Modifier,
) {
    // kind: insight | question | alert → prototype's info | warn | danger
    val kind = when {
        insight.kind == "alert" || insight.priority == "critical" -> "danger"
        insight.kind == "question" -> "warn"
        else -> "info"
    }
    val dotColor = when (kind) {
        "danger" -> Color(0xFFDC5B4B)
        "warn" -> Color(0xFFB45309)
        else -> ZadV3.green800
    }
    val tagText = stringResource(
        when (kind) {
            "danger" -> R.string.v3_insight_tag_alert
            "warn" -> R.string.v3_insight_tag_action
            else -> R.string.v3_insight_tag_info
        }
    )

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(insight.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
            Text(insight.body, fontSize = 13.sp, color = ZadV3.gray500, lineHeight = 18.sp)
        }
        Text(
            tagText,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dotColor,
            modifier = Modifier
                .clip(ZadV3.rPill)
                .background(dotColor.copy(alpha = 0.1f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/**
 * Tasbiha Garden glass card — tap-to-grow counter persisted in SharedPreferences
 * with daily reset, growing garden emoji, violet→green gradient progress bar and
 * confetti on completion.
 */
@Composable
private fun V3TasbihaGardenCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(loadTasbihaCount(context)) }
    var showConfetti by remember { mutableStateOf(false) }
    // V4 — one-shot Lottie petal burst (zad_v4_garden_burst.json); increments on
    // every 25% milestone so each burst is a fresh restart of the animation.
    var burstKey by remember { mutableIntStateOf(0) }

    val pct = ((count.toFloat() / TASBIHA_GOAL) * 100).toInt()
    val gardenEmoji = when {
        count >= 100 -> "🌳"
        count >= 75 -> "🌿🌿"
        count >= 50 -> "🦋"
        count >= 25 -> "🌿"
        else -> "🌱"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.v3_tasbiha_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
            Text("$pct%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F9B76))
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(
                gardenEmoji,
                fontSize = 52.sp,
                lineHeight = 56.sp,
                modifier = Modifier
                    .pressableScale(onClick = {
                        val next = (count + 1).coerceAtMost(TASBIHA_GOAL)
                        saveTasbihaCount(context, next)
                        // V4 — burst at every 25% milestone (v5 prototype's petal rises),
                        // not just completion, so the garden celebrates progress too.
                        val crossedMilestone = (next % 25 == 0 && count < next)
                        val justCompleted = next == TASBIHA_GOAL && count < TASBIHA_GOAL
                        count = next
                        if (crossedMilestone || justCompleted) {
                            showConfetti = true
                            burstKey++
                        }
                    })
                    .floatingIdle(3000),
            )
            GardenBurstLottie(
                playKey = burstKey,
                modifier = Modifier.matchParentSize(),
            )
            ConfettiOverlay(
                isTriggered = showConfetti,
                modifier = Modifier.matchParentSize(),
            )
        }
        LaunchedEffect(showConfetti) {
            if (showConfetti) {
                kotlinx.coroutines.delay(2200)
                showConfetti = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ZadV3.rPill)
                .background(Color(0xFF0F172A).copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .height(8.dp)
                    .clip(ZadV3.rPill)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF0F9B76)))),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.v3_tasbiha_count_of_goal, count, TASBIHA_GOAL),
                fontSize = 12.sp, color = ZadV3.gray500,
            )
            Text(
                stringResource(if (LocaleHelper.isArabic()) R.string.v3_tasbiha_tap_ar else R.string.v3_tasbiha_tap_en),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(ZadV3.rPill)
                    .background(Color(0xFF7C3AED))
                    .pressableScale(onClick = {
                        val next = (count + 1).coerceAtMost(TASBIHA_GOAL)
                        saveTasbihaCount(context, next)
                        val justCompleted = next == TASBIHA_GOAL && count < TASBIHA_GOAL
                        count = next
                        if (justCompleted) showConfetti = true
                    })
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            )
        }
    }
}
