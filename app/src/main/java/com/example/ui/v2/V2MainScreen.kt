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

private val v3FullScreenRoutes = setOf(V3Routes.PROFILE)

@Composable
fun V3MainScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val chromeVisible = currentRoute !in v3FullScreenRoutes
    var showVoiceSheet by remember { mutableStateOf(false) }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Listen for AI-driven navigation commands
    val pendingAppCommand by viewModel.pendingAppCommand.collectAsState()
    LaunchedEffect(pendingAppCommand) {
        val cmd = pendingAppCommand ?: return@LaunchedEffect
        val mapped = V3_TO_V2_ROUTE.entries.firstOrNull { it.value == cmd.screen }?.key
        when (mapped) {
            V3Routes.BUDGET -> go(V3Routes.BUDGET)
            V3Routes.ASSISTANT -> go(V3Routes.ASSISTANT)
            V3Routes.PROFILE -> go(V3Routes.PROFILE)
            V3Routes.FAMILY -> go(V3Routes.FAMILY)
            V3Routes.INVENTORY -> go(V3Routes.INVENTORY)
            V3Routes.SHOPPING -> go(V3Routes.SHOPPING)
            V3Routes.PHARMACY -> go(V3Routes.PHARMACY)
            V3Routes.MAINTENANCE -> go(V3Routes.MAINTENANCE)
            V3Routes.DEALS -> go(V3Routes.DEALS)
            V3Routes.NOTIFICATIONS -> go(V3Routes.NOTIFICATIONS)
            else -> {}
        }
        viewModel.consumePendingAppCommand()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = V3Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(V3Routes.HOME) {
                V3HomeScreen(
                    viewModel = viewModel,
                    onNavigateToBudget = { go(V3Routes.BUDGET) },
                    onOpenVoice = { showVoiceSheet = true },
                    onNavigate = { go(it) },
                    onOpenNotifications = { go(V3Routes.NOTIFICATIONS) },
                    onOpenProfile = { go(V3Routes.PROFILE) },
                )
            }
            composable(V3Routes.ASSISTANT) {
                V3AssistantScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onOpenVoice = { showVoiceSheet = true },
                    onNavigateToKnowledge = { go(V3Routes.KNOWLEDGE) },
                )
            }
            composable(V3Routes.BUDGET) {
                V3BudgetScreen(
                    viewModel = viewModel,
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
            composable(V3Routes.INVENTORY) {
                V3InventoryScreen(viewModel = viewModel)
            }
            composable(V3Routes.PHARMACY) {
                V3PharmacyScreen(viewModel = viewModel)
            }
            composable(V3Routes.SHOPPING) {
                V3ShoppingScreen(viewModel = viewModel)
            }
            composable(V3Routes.SUBS) {
                V3SubscriptionsScreen(viewModel = viewModel)
            }
            composable(V3Routes.MAINTENANCE) {
                V3MaintenanceScreen(viewModel = viewModel)
            }
            composable(V3Routes.PROFILE) {
                ProfileScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onLogout = onLogout,
                    navController = navController,
                    onSwitchToKidsMode = { },
                )
            }
            composable(V3Routes.FAMILY) {
                V3FamilyScreen()
            }
            composable(V3Routes.DEALS) {
                V3DealsScreen()
            }
            composable(V3Routes.NOTIFICATIONS) {
                V3NotificationsScreen()
            }
            composable(V3Routes.TASBIHA) {
                V3TasbihaScreen()
            }
            composable(V3Routes.KNOWLEDGE) {
                V3KnowledgeMapScreen()
            }
        }

        // Chrome overlay (header + bottom bar)
        if (chromeVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                V3Header(
                    title = v3ScreenTitle(currentRoute),
                    onOpenProfile = { go(V3Routes.PROFILE) },
                    onOpenNotifications = { go(V3Routes.NOTIFICATIONS) },
                )
                Spacer(Modifier.weight(1f))
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                V3BottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { go(it) },
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
        }
    }

    // Voice sheet overlay
    if (showVoiceSheet) {
        com.example.ui.components.ZadVoiceBottomSheet(
            viewModel = viewModel,
            onDismiss = { showVoiceSheet = false },
        )
    }
}

@Composable
private fun v3ScreenTitle(route: String?): String = androidx.compose.ui.res.stringResource(
    when (route) {
        V3Routes.ASSISTANT -> com.example.R.string.nav_assistant
        V3Routes.BUDGET -> com.example.R.string.screen_title_budget
        V3Routes.INVENTORY -> com.example.R.string.nav_inventory
        V3Routes.PROFILE -> com.example.R.string.screen_title_profile
        V3Routes.SHOPPING -> com.example.R.string.nav_shopping
        V3Routes.PHARMACY -> com.example.R.string.nav_pharmacy
        V3Routes.SUBS -> com.example.R.string.subscriptions_title
        V3Routes.MAINTENANCE -> com.example.R.string.nav_maintenance
        V3Routes.FAMILY -> com.example.R.string.nav_family
        V3Routes.DEALS -> com.example.R.string.nav_deals
        V3Routes.NOTIFICATIONS -> com.example.R.string.notifications_title
        V3Routes.TASBIHA -> com.example.R.string.app_name
        V3Routes.KNOWLEDGE -> com.example.R.string.nav_assistant
        else -> com.example.R.string.screen_title_home
    }
)