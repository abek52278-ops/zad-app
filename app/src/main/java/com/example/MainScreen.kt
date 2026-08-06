package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.KidsModePin
import com.example.ui.components.PinPromptDialog
import com.example.ui.components.ZadBottomNavBar
import com.example.ui.components.ZadCameraSheet
import com.example.ui.components.ZadCanvasBackground
import com.example.ui.components.ZadDrawerContent
import com.example.ui.components.ZadDrawerEntry
import com.example.ui.components.ZadMoreSheet
import com.example.ui.components.ZadRoutes
import com.example.ui.components.ZadTopHeader
import com.example.ui.components.zadDrawerEntries
import com.example.ui.components.zadScreenTitle
import com.example.ui.screens.*
import com.example.ui.theme.background
import com.example.ui.theme.primary
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

/**
 * The app's single navigation graph.
 *
 * Chrome (header, bottom pill, drawer, "more" grid, camera sheet) is drawn once
 * here by `ZadShell` and never by a screen — that is the mockup's structure, and
 * it is what replaced the twelve per-screen `TopAppBar`s the app used to carry.
 */
object ZadNav {
    // Routes reachable only from inside another screen (no nav destination of
    // their own in the drawer or the bottom pill).
    const val EDIT_PROFILE = "edit_profile"
    const val FAMILY_MANAGEMENT = "family_management"
    const val PAYMENT_BUDGET = "payment_budget"
    const val ASSISTANT_ALERTS = "assistant_alerts"
    const val TERMS = "terms_of_service"
    const val HELP = "help_support"
}

/** Routes that own the whole viewport — no shell header, no bottom pill. */
private val fullScreenRoutes = setOf(
    ZadRoutes.CAMERA,
    ZadNav.EDIT_PROFILE,
    ZadNav.FAMILY_MANAGEMENT,
    ZadNav.PAYMENT_BUDGET,
    ZadNav.ASSISTANT_ALERTS,
    ZadNav.TERMS,
    ZadNav.HELP,
)

@Composable
fun MainScreen(onLogout: () -> Unit = {}, pendingInviteCode: String? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val viewModel: ZadViewModel = viewModel()
    val familyViewModel: FamilyViewModel = viewModel()

    // تغذية سياق العائلة لشات زاد — عشان يعرف كل حاجة عن العيلة
    val familyStateForChat by familyViewModel.state.collectAsState()
    LaunchedEffect(familyStateForChat) {
        viewModel.updateFamilyContext(familyStateForChat)
    }

    // نفس النمط لبستان التسبيح — البستان بيتخزن في FamilyViewModel، وعقل زاد كان
    // مش شايفه خالص قبل كده رغم إنه بيانات حقيقية في family_tasbiha.
    LaunchedEffect(familyViewModel.myTasbiha, familyViewModel.familyTasbiha) {
        viewModel.updateTasbihaContext(familyViewModel.myTasbiha, familyViewModel.familyTasbiha)
    }

    // كتالوچ ترشيحات أمازون — مرة واحدة، عشان زاد يقدر يرشح منتج للناقص في المخزون.
    LaunchedEffect(Unit) { viewModel.loadAffiliateProducts() }

    // ─── وضع الأطفال: قفل تنقّل على مستوى الشاشة كلها ───
    val context = LocalContext.current
    val isChildRole = (familyStateForChat as? FamilyState.Active)?.myMemberInfo?.role == "child"
    var manualKidsModeActive by remember { mutableStateOf(KidsModePin.isManualModeActive(context)) }
    LaunchedEffect(manualKidsModeActive) {
        familyViewModel.kidsModePreviewOverride = manualKidsModeActive
    }
    var pinUnlockedOverride by remember { mutableStateOf(false) }
    val kidsModeEffective = (isChildRole && !pinUnlockedOverride) || manualKidsModeActive
    var showPinPrompt by remember { mutableStateOf(false) }

    fun setManualKidsMode(active: Boolean) {
        manualKidsModeActive = active
        KidsModePin.setManualModeActive(context, active)
    }

    // مايعرضش أي واجهة لحد ما الـ role يتحمل فعلياً
    if (familyStateForChat is FamilyState.Loading) {
        Box(modifier = Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = primary)
        }
        return
    }

    // مرحلة ٠ج (docs/agent/PLAN_2026_08_06_rebuild.md) — بوابة إجبارية: مفيش شاشة مالية
    // (HomeScreen وغيرها) تتعرض قبل ما السقف يتأكد. budgetLoaded بيمنع ومضة الشاشة دي
    // للحظة قبل ما loadBudget() الأول يخلّص. مستبعدة من وضع الأطفال — الطفل أصلاً
    // مايوصلش لأي شاشة مالية (goGuarded) ومالوش سلطة يحدد سقف العيلة.
    val budgetLoaded by viewModel.budgetLoaded.collectAsState()
    val budgetConfirmedForGate by viewModel.budgetConfirmed.collectAsState()
    if (!kidsModeEffective && budgetLoaded && !budgetConfirmedForGate) {
        com.example.ui.screens.BudgetGateScreen(onSetBudget = { viewModel.updateBudget(it) })
        return
    }

    if (showPinPrompt) {
        PinPromptDialog(
            onDismiss = { showPinPrompt = false },
            onUnlocked = {
                showPinPrompt = false
                if (isChildRole) pinUnlockedOverride = true
                if (manualKidsModeActive) setManualKidsMode(false)
            }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val chromeVisible = currentRoute !in fullScreenRoutes

    var showMoreSheet by remember { mutableStateOf(false) }
    var showCameraSheet by remember { mutableStateOf(false) }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Kids mode never reaches a financial screen — the PIN prompt gates it instead. */
    fun goGuarded(route: String) {
        if (kidsModeEffective && route != ZadRoutes.HOME && route != ZadRoutes.FAMILY) {
            showPinPrompt = true
        } else {
            go(route)
        }
    }

    val drawerEntries: List<ZadDrawerEntry> = if (kidsModeEffective) {
        zadDrawerEntries.filter { it.route == ZadRoutes.HOME || it.route == ZadRoutes.FAMILY }
    } else {
        zadDrawerEntries
    }

    val userName by viewModel.userName.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()
    val appNotifications by viewModel.appNotifications.collectAsState()
    val zadInsights by viewModel.zadInsights.collectAsState()
    val hasUnread = appNotifications.any { !it.isRead } || zadInsights.any { it.surface == "bell" }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = chromeVisible,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                drawerShape = RectangleShape,
                modifier = Modifier.fillMaxWidth(0.78f)
            ) {
                ZadDrawerContent(
                    currentRoute = currentRoute,
                    entries = drawerEntries,
                    userName = userName,
                    avatarUri = avatarUri,
                    kidsMode = kidsModeEffective,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        goGuarded(route)
                    },
                    onProfileClick = {
                        scope.launch { drawerState.close() }
                        goGuarded(ZadRoutes.PROFILE)
                    },
                    onExitKidsMode = {
                        scope.launch { drawerState.close() }
                        showPinPrompt = true
                    }
                )
            }
        }
    ) {
        // The mockup's neutral canvas gradient is the app background for EVERY screen.
        Box(modifier = Modifier.fillMaxSize()) {
            ZadCanvasBackground(modifier = Modifier.fillMaxSize())
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (chromeVisible) {
                        ZadTopHeader(
                            title = zadScreenTitle(if (kidsModeEffective && currentRoute != ZadRoutes.FAMILY) ZadRoutes.HOME else currentRoute),
                            kidsMode = kidsModeEffective,
                            hasUnreadNotifications = hasUnread,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNotificationsClick = { go(ZadRoutes.NOTIFICATIONS) },
                            onExitKidsMode = { showPinPrompt = true }
                        )
                    }
                },
                bottomBar = {
                    if (chromeVisible) {
                        ZadBottomNavBar(
                            currentRoute = currentRoute,
                            kidsMode = kidsModeEffective,
                            onNavigate = { goGuarded(it) },
                            onOpenCamera = { showCameraSheet = true },
                            onOpenMore = { showMoreSheet = true },
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavHost(
                        navController = navController,
                        startDestination = ZadRoutes.HOME,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { com.example.ui.components.ZadTransitions.enter },
                        exitTransition = { com.example.ui.components.ZadTransitions.exit },
                        popEnterTransition = { com.example.ui.components.ZadTransitions.popEnter },
                        popExitTransition = { com.example.ui.components.ZadTransitions.popExit }
                    ) {
                        composable(ZadRoutes.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                kidsModeOverride = kidsModeEffective,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToInventory = { go(ZadRoutes.INVENTORY) },
                                onNavigateToCamera = { showCameraSheet = true },
                                onNavigateToSubscriptions = { go(ZadRoutes.SUBS) },
                                onNavigateToShopping = { go(ZadRoutes.SHOPPING) },
                                onNavigateToFamily = { go(ZadRoutes.FAMILY) },
                                onNavigateToBudget = { go(ZadRoutes.BUDGET) },
                                onNavigateToTasbiha = { go(ZadRoutes.TASBIHA) },
                                onNavigateToProfile = { go(ZadRoutes.PROFILE) },
                                onNavigateToPharmacy = { go(ZadRoutes.PHARMACY) },
                                onNavigateToNotifications = { go(ZadRoutes.NOTIFICATIONS) },
                                onNavigateToNearby = { go(ZadRoutes.DEALS) }
                            )
                        }
                        composable(ZadRoutes.NOTIFICATIONS) {
                            NotificationCenterScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ZadRoutes.INVENTORY) {
                            InventoryScreen(
                                viewModel = viewModel,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.SUBS) { SubscriptionsScreen(viewModel) }
                        composable(ZadRoutes.PHARMACY) {
                            PharmacyScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.MAINTENANCE) { MaintenanceScreen(viewModel = viewModel) }
                        composable(ZadRoutes.DEALS) { NearbyDealsScreen(viewModel = viewModel) }
                        composable(ZadRoutes.STATEMENT) { StatementImportScreen() }
                        composable(ZadRoutes.ASSISTANT) {
                            ZadIntelligenceScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel
                            )
                        }
                        composable(ZadRoutes.TASBIHA) { TasbihaScreen(viewModel = familyViewModel) }
                        composable(ZadRoutes.FAMILY) {
                            FamilyScreen(
                                pendingInviteCode = pendingInviteCode,
                                viewModel = familyViewModel,
                                unreadNotificationCount = appNotifications.count { !it.isRead },
                                onNotificationsClick = { goGuarded(ZadRoutes.NOTIFICATIONS) },
                                showFinancials = !kidsModeEffective
                            )
                        }
                        composable(ZadRoutes.CAMERA) {
                            CameraScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("${ZadRoutes.CAMERA}/{mode}") { entry ->
                            CameraScreen(
                                viewModel = viewModel,
                                initialMode = entry.arguments?.getString("mode") ?: "INVENTORY",
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ZadRoutes.PROFILE) {
                            ProfileScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                onLogout = onLogout,
                                navController = navController,
                                onSwitchToKidsMode = { setManualKidsMode(true) }
                            )
                        }
                        composable(ZadRoutes.BUDGET) {
                            BudgetScreen(
                                viewModel = viewModel,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.SHOPPING) {
                            ShoppingListScreen(
                                viewModel = viewModel,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) }
                            )
                        }
                        composable(ZadRoutes.REPORTS) {
                            WeeklyReportScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel
                            )
                        }

                        // Sub-screens — full-viewport, reached from inside a screen
                        composable(ZadNav.HELP) { HelpSupportScreen(onBack = { navController.popBackStack() }) }
                        composable(ZadNav.EDIT_PROFILE) { EditProfileScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.FAMILY_MANAGEMENT) { FamilyManagementScreen(familyViewModel) { navController.popBackStack() } }
                        composable(ZadNav.PAYMENT_BUDGET) { PaymentAndBudgetScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.ASSISTANT_ALERTS) { AssistantAlertsScreen { navController.popBackStack() } }
                        composable(ZadNav.TERMS) { TermsOfServiceScreen { navController.popBackStack() } }
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        ZadMoreSheet(
            onDismiss = { showMoreSheet = false },
            onNavigate = { route ->
                showMoreSheet = false
                goGuarded(route)
            }
        )
    }

    if (showCameraSheet) {
        ZadCameraSheet(
            onDismiss = { showCameraSheet = false },
            onScanInventory = {
                showCameraSheet = false
                navController.navigate("${ZadRoutes.CAMERA}/INVENTORY") { launchSingleTop = true }
            },
            onScanReceipt = {
                showCameraSheet = false
                navController.navigate("${ZadRoutes.CAMERA}/RECEIPT") { launchSingleTop = true }
            }
        )
    }
}
