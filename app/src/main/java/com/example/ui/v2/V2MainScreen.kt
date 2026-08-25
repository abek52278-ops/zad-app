package com.example.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.ZadRoutes
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ZadIntelligenceScreen
import com.example.ui.theme.background
import com.example.ui.theme.primary
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

/**
 * V2 root: the new UI's own NavHost wrapped in the v2 chrome (glass header +
 * floating capsule bottom bar with the central mic FAB). Reuses the exact same
 * ViewModels as the legacy UI — one data layer, two skins. Screens not yet
 * rebuilt in v2 temporarily render their legacy composables inside the v2 shell,
 * so nothing is unreachable while migration proceeds screen by screen.
 */

private val v2FullScreenRoutes = setOf(ZadRoutes.PROFILE)

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

    // app_command whitelist — نفس ترجمة MainScreen القديمة عشان العقل يفتح الشاشات.
    val pendingAppCommand by viewModel.pendingAppCommand.collectAsState()
    LaunchedEffect(pendingAppCommand) {
        val cmd = pendingAppCommand ?: return@LaunchedEffect
        when (cmd.screen) {
            "budget" -> go(V2Routes.BUDGET)
            "assistant", "tasks", "insights" -> go(V2Routes.ASSISTANT)
            "profile" -> go(V2Routes.PROFILE)
            else -> { /* بقية الشاشات لسه بتتحول — تتجاهل مؤقتًا */ }
        }
        viewModel.consumePendingAppCommand()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ZadRoutes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ZadRoutes.HOME) {
                V2HomeScreen(
                    viewModel = viewModel,
                    onNavigateToBudget = { go(V2Routes.BUDGET) },
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
            composable(ZadRoutes.ASSISTANT) {
                V2AssistantScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
            composable(ZadRoutes.BUDGET) {
                V2BudgetScreen(
                    viewModel = viewModel,
                    onOpenVoice = { showVoiceSheet = true },
                )
            }
            composable(ZadRoutes.INVENTORY) {
                V2InventoryScreen(viewModel = viewModel)
            }
            composable(ZadRoutes.PHARMACY) {
                V2PharmacyScreen(viewModel = viewModel)
            }
            composable(ZadRoutes.SHOPPING) {
                V2ShoppingScreen(viewModel = viewModel)
            }
            composable(ZadRoutes.SUBS) {
                V2SubscriptionsScreen(viewModel = viewModel)
            }
            composable(ZadRoutes.MAINTENANCE) {
                V2MaintenanceScreen(viewModel = viewModel)
            }
            composable(ZadRoutes.PROFILE) {
                ProfileScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onLogout = onLogout,
                    navController = navController,
                    onSwitchToKidsMode = { },
                )
            }
        }

        if (chromeVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                V2Header(
                    title = v2ScreenTitle(currentRoute),
                    onOpenDrawer = { },
                )
                Spacer(Modifier.weight(1f))
            }
            // Bottom capsule overlays content (floating), like the prototype.
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
        com.example.ui.components.ZadVoiceBottomSheet(viewModel = viewModel, onDismiss = { showVoiceSheet = false })
    }
}

@Composable
private fun v2ScreenTitle(route: String?): String = androidx.compose.ui.res.stringResource(
    when (route) {
        ZadRoutes.ASSISTANT -> com.example.R.string.nav_assistant
        ZadRoutes.BUDGET -> com.example.R.string.screen_title_budget
        ZadRoutes.INVENTORY -> com.example.R.string.nav_inventory
        ZadRoutes.PROFILE -> com.example.R.string.screen_title_profile
        else -> com.example.R.string.screen_title_home
    }
)
