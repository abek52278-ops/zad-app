package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.ui.theme.ZadV2
import com.example.ui.theme.zadV2Card
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate

@Composable
fun V2HomeScreen(
    viewModel: ZadViewModel,
    onNavigateToBudget: () -> Unit,
    onOpenVoice: () -> Unit,
    onNavigate: (String) -> Unit,
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
        item {
            V2HeroCard(
                availableText = CurrencyFormatter.format(context, remaining ?: 0.0),
                spentText = CurrencyFormatter.format(context, spent),
                committedText = CurrencyFormatter.format(context, committed),
                onOpen = onNavigateToBudget,
            )
        }
        item {
            val dailySafe = if (daysLeft > 0) (remaining ?: 0.0) / daysLeft else null
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                V2WidgetCell(
                    caption = stringResource(R.string.v2_daily_safe),
                    value = dailySafe?.let { CurrencyFormatter.format(context, it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                V2WidgetCell(
                    caption = stringResource(R.string.v2_days_left),
                    value = "$daysLeft",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        
        item {
            // Shortcuts Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                V2ShortcutTile("🧺", stringResource(R.string.nav_inventory), ZadV2.tileInvBg, { onNavigate(V2Routes.INVENTORY) })
                V2ShortcutTile("🛒", stringResource(R.string.nav_shopping), ZadV2.tileShopBg, { onNavigate(V2Routes.SHOPPING) })
                V2ShortcutTile("👨‍👩‍👧", stringResource(R.string.nav_family), ZadV2.tileFamilyBg, { onNavigate(V2Routes.FAMILY) })
                V2ShortcutTile("🔄", stringResource(R.string.subscriptions_title), ZadV2.tileSubsBg, { onNavigate(V2Routes.SUBS) })
                V2ShortcutTile("💊", stringResource(R.string.nav_pharmacy), ZadV2.tilePharmBg, { onNavigate(V2Routes.PHARMACY) })
                V2ShortcutTile("📿", stringResource(R.string.app_name), ZadV2.tileTasbihaBg, { onNavigate(V2Routes.TASBIHA) })
            }
        }
        
        item {
            V2AiSummaryCard(
                title = stringResource(R.string.v2_mind_title),
                body = "Your spending is on track! You have enough for the weekend grocery trip.",
                chips = listOf("Analyze", "Recipes")
            )
        }
        
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

@Composable
fun V2WidgetCell(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.zadV2Card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.gray500)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
    }
}
