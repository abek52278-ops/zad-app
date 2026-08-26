package com.example.ui.v2

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.ui.theme.ZadV3
import com.example.ui.viewmodels.ZadViewModel

data class V3Category(
    val emoji: String,
    val nameRes: Int,
    val bg: Color,
    val route: String,
)

private val homeCategories = listOf(
    V3Category("🧺", R.string.nav_inventory, ZadV3.tileInvBg, V3Routes.INVENTORY),
    V3Category("🛒", R.string.nav_shopping, ZadV3.tileShopBg, V3Routes.SHOPPING),
    V3Category("👨‍👩‍👧", R.string.nav_family, ZadV3.tileFamilyBg, V3Routes.FAMILY),
    V3Category("🔄", R.string.subscriptions_title, ZadV3.tileSubsBg, V3Routes.SUBS),
    V3Category("💊", R.string.nav_pharmacy, ZadV3.tilePharmBg, V3Routes.PHARMACY),
    V3Category("🛠️", R.string.nav_maintenance, ZadV3.tileMaintBg, V3Routes.MAINTENANCE),
    V3Category("📿", R.string.app_name, ZadV3.tileTasbihaBg, V3Routes.TASBIHA),
    V3Category("🎯", R.string.nav_deals, Color(0xFFF0E6FF), V3Routes.DEALS),
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

    if (remaining == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ZadV3.green800)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(top = 80.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Apple Wallet Hero Card ──────────────────────────────────────────────
        item {
            V3HeroCard(
                availableText = CurrencyFormatter.format(context, remaining ?: 0.0),
                spentText = CurrencyFormatter.format(context, spent),
                committedText = CurrencyFormatter.format(context, committed),
                onOpen = onNavigateToBudget,
            )
        }

        // ── Widget Row ──────────────────────────────────────────────────────────
        item {
            val dailySafe = if (daysLeft > 0) (remaining ?: 0.0) / daysLeft else null
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                V3WidgetCell(
                    caption = stringResource(R.string.v2_daily_safe),
                    value = dailySafe?.let { CurrencyFormatter.format(context, it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                V3WidgetCell(
                    caption = stringResource(R.string.v2_days_left),
                    value = "$daysLeft",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Bezier Spend Chart ──────────────────────────────────────────────────
        item {
            V2BezierChart(transactions = transactions)
        }

        // ── AI Summary (Brain insight) ──────────────────────────────────────────
        item {
            V3AiSummaryCard(
                title = stringResource(R.string.v2_mind_title),
                body = "Your spending is on track! You have enough for the weekend grocery trip.",
                chips = listOf("Analyze", "Recipes"),
            )
        }

        // ── Category Grid (pastel Apple-style) ──────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.v2_categories),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink,
                    )
                    Text(
                        stringResource(R.string.v2_see_all),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.green800,
                        modifier = Modifier.pressableScale { onOpenProfile() },
                    )
                }
                // Grid: 4 items per row
                val chunks = homeCategories.chunked(4)
                chunks.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { cat ->
                            V3CategoryCard(
                                emoji = cat.emoji,
                                name = stringResource(cat.nameRes),
                                itemCount = "",
                                bgColor = cat.bg,
                                onClick = { onNavigate(cat.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Fill remaining slots if row is incomplete
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        // ── Exclusive Offers (from the vision) ─────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.v2_exclusive_offers),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        V3OfferCard(
                            image = "🫒",
                            title = "Zaitoun Olive Oil",
                            subtitle = "Extra virgin, 1L · 27% OFF",
                            price = "SAR 39.99",
                            onClick = { },
                        )
                    }
                    item {
                        V3OfferCard(
                            image = "🥩",
                            title = "Fresh Lamb Cuts",
                            subtitle = "Premium Australian lamb bundle",
                            price = "SAR 89.00",
                            onClick = { },
                        )
                    }
                    item {
                        V3OfferCard(
                            image = "🧀",
                            title = "Dairy Bundle",
                            subtitle = "Milk, yogurt, cheese mix",
                            price = "SAR 24.99",
                            onClick = { },
                        )
                    }
                }
            }
        }

        // ── Recent Transactions ─────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.v2_recent_tx),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink,
                )
                transactions.take(5).forEach { tx ->
                    V3TxRow(
                        title = tx.title,
                        date = tx.createdAt?.take(10) ?: "",
                        amount = CurrencyFormatter.format(context, tx.amount),
                        negative = tx.isExpense,
                    )
                }
            }
        }

        // Voice trigger button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
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
