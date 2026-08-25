package com.example.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ProfileScreen
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

private val v2FullScreenRoutes = setOf(V2Routes.PROFILE)

@Composable
fun V2MainScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val chromeVisible = currentRoute !in v2FullScreenRoutes
    var showVoiceSheet by remember { mutableStateOf(false) }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val pendingAppCommand by viewModel.pendingAppCommand.collectAsState()
    LaunchedEffect(pendingAppCommand) {
        val cmd = pendingAppCommand ?: return@LaunchedEffect
        when (cmd.screen) {
            "budget" -> go(V2Routes.BUDGET)
            "assistant", "tasks", "insights" -> go(V2Routes.ASSISTANT)
            "profile" -> go(V2Routes.PROFILE)
            "family" -> go(V2Routes.FAMILY)
            "inventory" -> go(V2Routes.INVENTORY)
            "shopping" -> go(V2Routes.SHOPPING)
            "pharmacy" -> go(V2Routes.PHARMACY)
            "maintenance" -> go(V2Routes.MAINTENANCE)
            "deals" -> go(V2Routes.DEALS)
            "notifications" -> go(V2Routes.NOTIFICATIONS)
            else -> { }
        }
        viewModel.consumePendingAppCommand()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = V2Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(V2Routes.HOME) {
                V2HomeScreen(
                    viewModel = viewModel,
                    onNavigateToBudget = { go(V2Routes.BUDGET) },
                    onOpenVoice = { showVoiceSheet = true },
                    onNavigate = { go(it) }
                )
            }
            composable(V2Routes.ASSISTANT) {
                V2AssistantScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onOpenVoice = { showVoiceSheet = true },
                    onNavigateToKnowledge = { go(V2Routes.KNOWLEDGE) }
                )
            }
            composable(V2Routes.BUDGET) {
                V2BudgetScreen(
                    viewModel = viewModel,
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
            composable(V2Routes.INVENTORY) {
                V2InventoryScreen(viewModel = viewModel)
            }
            composable(V2Routes.PHARMACY) {
                V2PharmacyScreen(viewModel = viewModel)
            }
            composable(V2Routes.SHOPPING) {
                V2ShoppingScreen(viewModel = viewModel)
            }
            composable(V2Routes.SUBS) {
                V2SubscriptionsScreen(viewModel = viewModel)
            }
            composable(V2Routes.MAINTENANCE) {
                V2MaintenanceScreen(viewModel = viewModel)
            }
            composable(V2Routes.PROFILE) {
                ProfileScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onLogout = onLogout,
                    navController = navController,
                    onSwitchToKidsMode = { },
                )
            }
            composable(V2Routes.FAMILY) {
                V2FamilyScreen()
            }
            composable(V2Routes.DEALS) {
                V2DealsScreen()
            }
            composable(V2Routes.NOTIFICATIONS) {
                V2NotificationsScreen()
            }
            composable(V2Routes.TASBIHA) {
                V2TasbihaScreen()
            }
            composable(V2Routes.KNOWLEDGE) {
                V2KnowledgeMapScreen()
            }
        }

        if (chromeVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                V2Header(
                    title = v2ScreenTitle(currentRoute),
                    onOpenDrawer = { /* Will implement drawer state soon */ },
                )
                Spacer(Modifier.weight(1f))
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                V2BottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { go(it) },
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
        }
    }

    if (showVoiceSheet) {
        // Siri-style Voice Sheet overlay
        com.example.ui.components.ZadVoiceBottomSheet(viewModel = viewModel, onDismiss = { showVoiceSheet = false })
    }
}

@Composable
private fun v2ScreenTitle(route: String?): String = androidx.compose.ui.res.stringResource(
    when (route) {
        V2Routes.ASSISTANT -> com.example.R.string.nav_assistant
        V2Routes.BUDGET -> com.example.R.string.screen_title_budget
        V2Routes.INVENTORY -> com.example.R.string.nav_inventory
        V2Routes.PROFILE -> com.example.R.string.screen_title_profile
        V2Routes.SHOPPING -> com.example.R.string.nav_shopping
        V2Routes.PHARMACY -> com.example.R.string.nav_pharmacy
        V2Routes.SUBS -> com.example.R.string.subscriptions_title
        V2Routes.MAINTENANCE -> com.example.R.string.nav_maintenance
        V2Routes.FAMILY -> com.example.R.string.nav_family
        V2Routes.DEALS -> com.example.R.string.nav_deals
        V2Routes.NOTIFICATIONS -> com.example.R.string.notifications_title
        V2Routes.TASBIHA -> com.example.R.string.app_name
        V2Routes.KNOWLEDGE -> com.example.R.string.nav_assistant
        else -> com.example.R.string.screen_title_home
    }
)
