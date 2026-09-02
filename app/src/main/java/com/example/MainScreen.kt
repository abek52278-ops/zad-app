package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainActivity
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
import com.example.ui.components.LocalBottomBarInset
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
import kotlinx.coroutines.delay
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
    // W5 — "سجل تعديلات زاد" (agent_actions log + undo), متاحة من إعدادات البروفايل.
    const val AGENT_ACTION_LOG = "agent_action_log"
    // "زاد عارف عني إيه" — شفافية zad_memory، متاحة من إعدادات البروفايل.
    const val ZAD_MEMORY = "zad_memory"
    // "أدوية العيلة" — رؤية للوالدين بس، متاحة من شاشة الصيدلية.
    const val FAMILY_PHARMACY = "family_pharmacy"
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
    ZadNav.AGENT_ACTION_LOG,
    ZadNav.ZAD_MEMORY,
    ZadNav.FAMILY_PHARMACY,
)

@Composable
fun MainScreen(onLogout: () -> Unit = {}, pendingInviteCode: String? = null, openVoiceOnStart: Boolean = false) {
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
    // كان recordNavigation() بيتنده بـisSubscribed الافتراضية (false) دايماً — يعني
    // المشتركين المدفوعين كانوا بيشوفوا إعلانات interstitial زي أي حد تاني رغم "زاد بلس
    // = من غير إعلانات". نفس singleton شاشة الدفع (ZadSubscriptionPaywallScreen) بتقرا
    // منه أصلاً، فمفيش نداء شبكة إضافي هنا.
    val billingManagerForAds = remember { com.example.billing.GooglePlayBillingManager.getInstance(context) }
    val activeSubscriptionPlan by billingManagerForAds.activePlan.collectAsState()
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
    // budgetLoaded == false يعني loadBudget() لسه في الطريق — عرض الرئيسية دلوقتي كان
    // بيفلّش صفر/رقم قديم لحد ما الرد يرجع (السبب اللي "البادجت بيبان فاضي/مش حي").
    if (!kidsModeEffective && !budgetLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = primary)
        }
        return
    }
    if (!kidsModeEffective && !budgetConfirmedForGate) {
        com.example.ui.screens.BudgetGateScreen(onComplete = { budget, market ->
            // البلد/العملة بتتكتبوا هنا كمان مش في شاشة اختيار السوق بس — دي أول نقطة
            // مضمون فيها إن في جلسة، فالكتابة بتوصل السيرفر فعلاً والبوت يبطّل يسأل.
            viewModel.completeInitialSetup(budget, market)
        })
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
    // "Hey Zad" — نراقب طلب الخدمة مباشرة، ونصفره بعد ما نفتح
    val wakeRequest = MainActivity.openVoiceRequest.value
    var showVoiceSheet by remember { mutableStateOf(wakeRequest) }
    LaunchedEffect(wakeRequest) {
        if (wakeRequest) {
            showVoiceSheet = true
            MainActivity.openVoiceRequest.value = false
        }
    }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // بند 32.2 — دوس على إشعار المعاملة البنكية (FCM fallback لما تيليجرام مش مربوط)
    // يرجّع العميل للرئيسية عشان يشوف الكارت، بدل ما يفضل واقف في أي شاشة تانية كان فيها.
    val transactionProposalNotifRequest = MainActivity.openTransactionProposalsRequest.value
    LaunchedEffect(transactionProposalNotifRequest) {
        if (transactionProposalNotifRequest) {
            go(ZadRoutes.HOME)
            MainActivity.openTransactionProposalsRequest.value = false
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

    // ── أوامر واجهة العقل (app_command) ─────────────────────────────────────────
    // العقل يقدر يطلب فتح شاشة ("وريني المخزون"). ViewModel بيحطّ الأمر في
    // pendingAppCommand، وهنا بنلاحظه وبنترجم screen → route محلياً.
    // القايمة البيضاء هنا تالت حارس (سيرفر validators.ts + repo + هنا).
    val pendingAppCommand by viewModel.pendingAppCommand.collectAsState()
    LaunchedEffect(pendingAppCommand) {
        val cmd = pendingAppCommand ?: return@LaunchedEffect
        val route = when (cmd.screen) {
            "inventory" -> ZadRoutes.INVENTORY
            "shopping" -> ZadRoutes.SHOPPING
            "pharmacy" -> ZadRoutes.PHARMACY
            "budget" -> ZadRoutes.BUDGET
            "family" -> ZadRoutes.FAMILY
            "maintenance" -> ZadRoutes.MAINTENANCE
            "subscriptions", "obligations", "debts" -> ZadRoutes.SUBS
            "tasks", "insights" -> ZadRoutes.ASSISTANT
            else -> null
        }
        if (route != null && !kidsModeEffective) go(route)
        viewModel.consumePendingAppCommand()
    }

    // A child-role account's PIN unlock is session-scoped, not permanent — re-lock once
    // they navigate back to the kids-safe zone, or after 3 idle minutes past a guarded
    // screen, so one correct PIN entry can't stay unlocked for the rest of the session.
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            com.example.ads.InterstitialAdManager.recordNavigation(context, isSubscribed = activeSubscriptionPlan != null)
        }
        if (isChildRole && pinUnlockedOverride && (currentRoute == ZadRoutes.HOME || currentRoute == ZadRoutes.FAMILY)) {
            pinUnlockedOverride = false
        }
    }
    LaunchedEffect(pinUnlockedOverride, currentRoute) {
        if (isChildRole && pinUnlockedOverride) {
            delay(3 * 60 * 1000L)
            pinUnlockedOverride = false
        }
    }

    LaunchedEffect(pendingInviteCode) {
        if (!pendingInviteCode.isNullOrBlank()) {
            goGuarded(ZadRoutes.FAMILY)
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
                    },
                    showRelockAction = isChildRole && pinUnlockedOverride,
                    onRelockKidsMode = {
                        scope.launch { drawerState.close() }
                        pinUnlockedOverride = false
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
                        val isInventory = currentRoute == ZadRoutes.INVENTORY
                        val inventorySearchQuery by viewModel.inventorySearchQuery.collectAsState()
                        var isInventorySearchOpen by remember { mutableStateOf(false) }

                        ZadTopHeader(
                            title = zadScreenTitle(if (kidsModeEffective && currentRoute != ZadRoutes.FAMILY) ZadRoutes.HOME else currentRoute),
                            kidsMode = kidsModeEffective,
                            hasUnreadNotifications = hasUnread,
                            avatarUri = avatarUri,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNotificationsClick = { go(ZadRoutes.NOTIFICATIONS) },
                            onExitKidsMode = { showPinPrompt = true },
                            showRelockAction = isChildRole && pinUnlockedOverride,
                            onRelockKidsMode = { pinUnlockedOverride = false },
                            onAvatarClick = { goGuarded(ZadRoutes.PROFILE) },
                            actions = {
                                if (isInventory) {
                                    AnimatedVisibility(
                                        visible = isInventorySearchOpen,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        OutlinedTextField(
                                            value = inventorySearchQuery,
                                            onValueChange = { viewModel.setSearchQuery(it) },
                                            placeholder = {
                                                Text(
                                                    stringResource(R.string.search_inventory),
                                                    fontSize = 12.sp
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(50),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = primary,
                                                unfocusedBorderColor = primary.copy(alpha = 0.3f),
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            trailingIcon = {
                                                if (inventorySearchQuery.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = { viewModel.setSearchQuery("") },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .width(170.dp)
                                                .height(38.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isInventorySearchOpen = !isInventorySearchOpen
                                            if (!isInventorySearchOpen) viewModel.setSearchQuery("")
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isInventorySearchOpen || inventorySearchQuery.isNotEmpty()) primary.copy(alpha = 0.12f) else primary.copy(alpha = 0.08f))
                                    ) {
                                        Icon(
                                            if (isInventorySearchOpen) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = stringResource(R.string.search_inventory),
                                            tint = primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
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
                            onOpenVoice = { showVoiceSheet = true },
                            onOpenMore = { showMoreSheet = true },
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                  // كل شاشة جوه NavHost بتقرأ LocalBottomBarInset.current بدل ما تخمّن
                  // ارتفاع الشريط بنفسها.
                  CompositionLocalProvider(LocalBottomBarInset provides innerPadding.calculateBottomPadding()) {
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
                                onNavigateToCurrencySettings = { go(ZadNav.PAYMENT_BUDGET) },
                                onNavigateToMaintenance = { go(ZadRoutes.MAINTENANCE) },
                                onNavigateToPlans = { go(ZadRoutes.PREMIUM_PLANS) },
                                onOpenVoice = { showVoiceSheet = true }
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
                        composable(ZadRoutes.SUBS) { SubscriptionsScreen(viewModel, familyViewModel) }
                        composable(ZadRoutes.PHARMACY) {
                            PharmacyScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                onNavigateToCamera = { showCameraSheet = true },
                                onNavigateToFamilyPharmacy = { go(ZadNav.FAMILY_PHARMACY) }
                            )
                        }
                        composable(ZadRoutes.MAINTENANCE) { MaintenanceScreen(viewModel = viewModel) }
                        composable(ZadRoutes.STATEMENT) { StatementImportScreen() }
                        composable(ZadRoutes.KNOWLEDGE_MAP) {
                            ZadKnowledgeMapScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToRoute = { route -> goGuarded(route) }
                            )
                        }
                        composable(ZadRoutes.DEALS) {
                            com.example.ui.screens.NearbyDealsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(ZadRoutes.ASSISTANT) {
                            ZadIntelligenceScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                onNavigateToFamily = { go(ZadRoutes.FAMILY) },
                                onNavigateToStatementImport = { go(ZadRoutes.STATEMENT) },
                                onNavigateToKnowledgeMap = { go(ZadRoutes.KNOWLEDGE_MAP) }
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
                        composable(ZadRoutes.PREMIUM_PLANS) {
                            com.example.ui.screens.ZadSubscriptionPaywallScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        // Sub-screens — full-viewport, reached from inside a screen
                        composable(ZadNav.HELP) { HelpSupportScreen(onBack = { navController.popBackStack() }) }
                        composable(ZadNav.AGENT_ACTION_LOG) {
                            com.example.ui.screens.AgentActionLogScreen(onBack = { navController.popBackStack() })
                        }
                        composable(ZadNav.ZAD_MEMORY) {
                            com.example.ui.screens.ZadMemoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(ZadNav.FAMILY_PHARMACY) {
                            val famState by familyViewModel.state.collectAsState()
                            val members = (famState as? com.example.ui.viewmodels.FamilyState.Active)?.members ?: emptyList()
                            com.example.ui.screens.FamilyPharmacyScreen(
                                members = members,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ZadNav.EDIT_PROFILE) { EditProfileScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.FAMILY_MANAGEMENT) { FamilyManagementScreen(familyViewModel) { navController.popBackStack() } }
                        composable(ZadNav.PAYMENT_BUDGET) { PaymentAndBudgetScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.ASSISTANT_ALERTS) { AssistantAlertsScreen { navController.popBackStack() } }
                        composable(ZadNav.TERMS) { TermsOfServiceScreen { navController.popBackStack() } }
                    }
                  }
                }
            }
            // المسكوت العائم (الكورة الخضرا) اتشال من فوق كل الشاشات: كان zIndex(100f)
            // فوق محتوى الـLazyColumn، فبيقطع أسماء الأدوية والمخزون في PharmacyScreen
            // وInventoryScreen — ومكانش فيه أي padding في القوايم دي يخلي المحتوى يعدّيه.
            // مدخل الشات بقى زر واحد صريح ("بوت زاد") في الرئيسية + تبويب المساعد في
            // شريط التنقل، فمفيش وظيفة اتفقدت — بس الشاشات بقت نضيفة من طبقة عائمة
            // كانت بتغطي المحتوى في كل مكان. ZadAgentOverlay (شيت الشات السريع) اتشال
            // معاه: المسكوت كان **المشغّل الوحيد** ليه (onQuickChat)، فمن غيره بقى كود
            // ميت مستحيل يظهر — وسيبه كان هيوهم إنه شغال.
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

    if (showVoiceSheet) {
        com.example.ui.components.ZadVoiceBottomSheet(
            viewModel = viewModel,
            onDismiss = { showVoiceSheet = false }
        )
    }
}
