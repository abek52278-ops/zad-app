package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ZadSegmentedTabs
import com.example.ui.theme.Typography
import com.example.ui.theme.ZadHubListBottomPadding
import com.example.ui.theme.ZadHubListHorizontalPadding
import com.example.ui.theme.onSurface
import com.example.ui.viewmodels.ZadViewModel

/**
 * البوابة 2 — الميزانية والالتزامات (Finances), UI_ARCHITECTURE_SPEC.md §2.2/§4.2.
 * دمج حقيقي مش relabel: BudgetScreen وSubscriptionsScreen كانوا شاشتين منفصلتين
 * بموديلين مختلفين، بقوا تابات جوه شاشة واحدة. مخطط سداد الديون + العروض الحية +
 * تحديات العائلة + صناديق الادخار (كانوا كلهم مقحمين جوه تاب "الكل" في
 * SubscriptionsScreen) بقالهم تاب "الديون" مستقل.
 */
enum class FinancesTab { DAILY, SUBSCRIPTIONS, DEBTS }

/** نفس نمط [InventoryNavState]/[PantryShoppingNavState]. */
object FinancesNavState {
    var pendingTab by mutableStateOf<FinancesTab?>(null)
}

@Composable
fun FinancesScreen(
    viewModel: ZadViewModel,
    initialTab: FinancesTab = FinancesTab.DAILY,
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(FinancesNavState.pendingTab ?: initialTab) }
    LaunchedEffect(Unit) { FinancesNavState.pendingTab = null }

    Column(modifier = Modifier.fillMaxSize()) {
        ZadSegmentedTabs(
            tabs = listOf(
                stringResource(R.string.finances_tab_daily),
                stringResource(R.string.nav_subscriptions_installments),
                stringResource(R.string.debts_domain_label)
            ),
            selectedIndex = selectedTab.ordinal,
            onSelect = { selectedTab = FinancesTab.entries[it] },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                FinancesTab.DAILY -> BudgetScreen(
                    viewModel = viewModel,
                    onNavigateToAssistant = onNavigateToAssistant,
                    onNavigateToCamera = onNavigateToCamera
                )
                FinancesTab.SUBSCRIPTIONS -> SubscriptionsScreen(viewModel = viewModel)
                FinancesTab.DEBTS -> FinancesDebtsBody(viewModel = viewModel)
            }
        }
    }
}

/**
 * تاب "الديون" — مخطط سداد الديون + العروض الحية، منقولين هنا زي ما هم من
 * `SubscriptionsScreen`'s القديمة "الكل" tab (نفس الكروت، نفس الـ ViewModel calls).
 *
 * تحديات العائلة المالية وصناديق الادخار كانوا هنا مؤقتًا (قرار مؤجل وقت تنفيذ بوابة
 * Finances) — دلوقتي نقلوا لبوابة Brain & Family's تاب "العائلة" (`BrainFamilyScreen.kt`
 * → `FamilyHubBody`'s "صناديق الادخار" sub-view)، مكانهم الطبيعي (family_financial_challenges/
 * sinking_funds).
 */
@Composable
private fun FinancesDebtsBody(viewModel: ZadViewModel) {
    val debts by viewModel.debts.collectAsState()
    val inventory by viewModel.inventory.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZadHubListHorizontalPadding, 8.dp, ZadHubListHorizontalPadding, ZadHubListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DebtPayoffPlannerCard(debts, viewModel) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.subs_deals_section), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
        }
        item {
            LiveDealsCard(
                shortageItems = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }.map { it.itemName },
                viewModel = viewModel
            )
        }
    }
}
