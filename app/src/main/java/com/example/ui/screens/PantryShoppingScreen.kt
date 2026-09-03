package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ZadSegmentedTabs
import com.example.ui.viewmodels.ZadViewModel

/**
 * البوابة 3 — المخزون والتسوق (Pantry & Shopping), UI_ARCHITECTURE_SPEC.md §2.3/§4.2.
 * دمج حقيقي مش relabel: أربع شاشات كانت مستقلة (InventoryScreen، ShoppingListScreen،
 * RecommendationsScreen — كانت orphaned، وMaintenanceScreen) بقوا تابات جوه شاشة واحدة.
 * كل شاشة فرعية باقية بمنطقها وViewModel-calls الأصلية زي ما هي — الدمج على مستوى
 * التنقل/العرض بس، مش إعادة كتابة المنطق الداخلي.
 */
enum class PantryShoppingTab { INVENTORY, SHOPPING, RECOMMENDATIONS, MAINTENANCE }

/** نفس نمط [InventoryNavState]: caller خارجي (زي ProfileScreen) بيحدد التاب المطلوب
 *  قبل الملاحة، والشاشة بتقراه مرة واحدة وترجعه null. */
object PantryShoppingNavState {
    var pendingTab by mutableStateOf<PantryShoppingTab?>(null)
}

@Composable
fun PantryShoppingScreen(
    viewModel: ZadViewModel,
    initialTab: PantryShoppingTab = PantryShoppingTab.INVENTORY,
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(PantryShoppingNavState.pendingTab ?: initialTab) }
    LaunchedEffect(Unit) { PantryShoppingNavState.pendingTab = null }

    Column(modifier = Modifier.fillMaxSize()) {
        ZadSegmentedTabs(
            tabs = listOf(
                stringResource(R.string.nav_inventory),
                stringResource(R.string.nav_shopping),
                stringResource(R.string.pantry_tab_recommendations),
                stringResource(R.string.nav_maintenance)
            ),
            selectedIndex = selectedTab.ordinal,
            onSelect = { selectedTab = PantryShoppingTab.entries[it] },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                PantryShoppingTab.INVENTORY -> InventoryScreen(
                    viewModel = viewModel,
                    onNavigateToAssistant = onNavigateToAssistant,
                    onNavigateToCamera = onNavigateToCamera
                )
                PantryShoppingTab.SHOPPING -> ShoppingListScreen(
                    viewModel = viewModel,
                    onNavigateToAssistant = onNavigateToAssistant
                )
                PantryShoppingTab.RECOMMENDATIONS -> RecommendationsRoute()
                PantryShoppingTab.MAINTENANCE -> MaintenanceScreen(viewModel = viewModel)
            }
        }
    }
}
