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
import com.example.ui.theme.surface
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
    // "الإنجازات والرتب" — إنجازات المساهمة بالأسعار (user_achievements)، متاحة من
    // إعدادات البروفايل. كانت الشاشة موجودة بالكامل بس من غير أي route ليها.
    const val ACHIEVEMENTS = "achievements"
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
    ZadNav.ACHIEVEMENTS,
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
    // المسكوت الأليف في HomeScreen بيفتح على طول في وضع المكالمة الحية (Gemini Live) —
    // كل مداخل الصوت التانية (المايك في الشريط السفلي، كارت "مساعدك الذكي جاهز") لسه
    // بتفتح في الوضع العادي دور-بدور زي ما كانت.
    var voiceSheetLiveMode by remember { mutableStateOf(false) }
    LaunchedEffect(wakeRequest) {
        if (wakeRequest) {
            showVoiceSheet = true
            MainActivity.openVoiceRequest.value = false
        }
    }

    // شيت شات سريع لزر "بوت زاد" العائم — مش تنقّل لشاشة "عقل زاد" (تحليلات 4200 سطر،
    // صندوق الشات فيها مدفون بعد 40 كارت). ZadAgentOverlay مكوّن جاهز بالفعل ومعمول
    // لبالظبط الغرض ده، من onQuickChat القديم بتاع المسكوت اللي اتشال.
    var showAgentOverlay by remember { mutableStateOf(false) }
    val companionMood by viewModel.companionMood.collectAsState()

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // zad://rewards من زر تليجرام لما الرصيد يخلص. الاستهلاك مرة واحدة عشان
    // العودة للتطبيق بعدين ما تفتحش الشاشة تاني.
    LaunchedEffect(MainActivity.openRewardsFromDeepLink.value) {
        if (MainActivity.openRewardsFromDeepLink.value) {
            MainActivity.openRewardsFromDeepLink.value = false
            go(ZadRoutes.PREMIUM_PLANS)
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
        // الكاميرا بتتفتح كـroute بمود صريح مش كشيت، عشان "صوّر الفاتورة" توديه على
        // طول لوضع الفاتورة بدل ما تسيبه يختار. ده أهم أمر في القايمة: مدخل المخزون
        // كله بيمرّ من هنا، وهو أقل مسار مستخدم في التطبيق.
        val cameraRoute = when (cmd.screen) {
            "camera_receipt" -> "${ZadRoutes.CAMERA}/RECEIPT"
            "camera" -> "${ZadRoutes.CAMERA}/INVENTORY"
            else -> null
        }
        val route = cameraRoute ?: when (cmd.screen) {
            "inventory" -> ZadRoutes.INVENTORY
            "shopping" -> ZadRoutes.SHOPPING
            "pharmacy" -> ZadRoutes.PHARMACY
            "budget" -> ZadRoutes.BUDGET
            "family" -> ZadRoutes.FAMILY
            "maintenance" -> ZadRoutes.MAINTENANCE
            "subscriptions", "obligations", "debts" -> ZadRoutes.SUBS
            "tasks", "insights" -> ZadRoutes.ASSISTANT
            "home" -> ZadRoutes.HOME
            "tasbiha" -> ZadRoutes.TASBIHA
            "notifications" -> ZadRoutes.NOTIFICATIONS
            "profile" -> ZadRoutes.PROFILE
            "statement" -> ZadRoutes.STATEMENT
            else -> null
        }
        // القايمة البيضا دي تالت حارس بعد validators.ts والـrepo، فأي شاشة السيرفر
        // يسمح بيها ومش متعرّفة هنا بتتجاهل بصمت — وده صح أمنياً وغلط تشخيصياً، لأن
        // الأمر بيضيع من غير أثر. اللوج ده بيخلّي أي فجوة بين القايمتين مرئية.
        if (route == null) {
            android.util.Log.w("ZadNav", "app_command: شاشة غير معروفة للعميل: ${cmd.screen}")
        }
        // وضع الأطفال بيلغي التنقّل بالكامل: الطفل مايوصلش لشاشة مالية بأمر من العقل
        // أكتر من ما بيوصلها بإيده.
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
                drawerContainerColor = surface,
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
                // `innerPadding` هو المصدر الوحيد لمساحة الشريط السفلي: Scaffold بيقيس
                // ارتفاع bottomBar الحقيقي وبيحجزه هنا مرة واحدة لكل الشاشات. كان فيه
                // كمان CompositionLocal بينشر نفس الرقم والشاشات بتضيفه تاني — شوف
                // التعليق في ZadShell.kt مكان تعريفه القديم.
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
                                onNavigateToCurrencySettings = { go(ZadNav.PAYMENT_BUDGET) },
                                onNavigateToMaintenance = { go(ZadRoutes.MAINTENANCE) },
                                onNavigateToPlans = { go(ZadRoutes.PREMIUM_PLANS) },
                                onOpenVoice = { showVoiceSheet = true },
                                onOpenVoiceLive = {
                                    voiceSheetLiveMode = true
                                    showVoiceSheet = true
                                },
                                onOpenBotChat = { showAgentOverlay = true }
                            )
                        }
                        composable(ZadRoutes.NOTIFICATIONS) {
                            NotificationCenterScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ZadRoutes.INVENTORY) {
                            com.example.ui.screens.PantryShoppingScreen(
                                viewModel = viewModel,
                                initialTab = com.example.ui.screens.PantryShoppingTab.INVENTORY,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.SUBS) {
                            com.example.ui.screens.FinancesScreen(
                                viewModel = viewModel,
                                initialTab = com.example.ui.screens.FinancesTab.SUBSCRIPTIONS,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.PHARMACY) {
                            PharmacyScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.MAINTENANCE) {
                            com.example.ui.screens.PantryShoppingScreen(
                                viewModel = viewModel,
                                initialTab = com.example.ui.screens.PantryShoppingTab.MAINTENANCE,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
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
                            // وضع الأطفال ممنوع يوصل للـ route ده أصلاً (goGuarded فوق) —
                            // البوابة دي أدوات بالغين بالكامل، مانع دايمًا هنا.
                            com.example.ui.screens.BrainFamilyScreen(
                                viewModel = viewModel,
                                familyViewModel = familyViewModel,
                                initialTab = com.example.ui.screens.BrainFamilyTab.INTELLIGENCE,
                                pendingInviteCode = pendingInviteCode,
                                unreadNotificationCount = appNotifications.count { !it.isRead },
                                onNotificationsClick = { goGuarded(ZadRoutes.NOTIFICATIONS) },
                                onNavigateToStatementImport = { go(ZadRoutes.STATEMENT) },
                                onNavigateToRoute = { route -> goGuarded(route) }
                            )
                        }
                        composable(ZadRoutes.TASBIHA) { TasbihaScreen(viewModel = familyViewModel) }
                        composable(ZadRoutes.FAMILY) {
                            // وضع الأطفال: FamilyScreen وحدها بلا أي تبويبات — نفس القيد
                            // القديم بالظبط (route != FAMILY ممنوع لغير home)، مفيش تسريب
                            // لتبويب "عقل زاد" أو أي sub-view تاني للطفل.
                            if (kidsModeEffective) {
                                FamilyScreen(
                                    pendingInviteCode = pendingInviteCode,
                                    viewModel = familyViewModel,
                                    unreadNotificationCount = appNotifications.count { !it.isRead },
                                    onNotificationsClick = { goGuarded(ZadRoutes.NOTIFICATIONS) },
                                    showFinancials = false
                                )
                            } else {
                                com.example.ui.screens.BrainFamilyScreen(
                                    viewModel = viewModel,
                                    familyViewModel = familyViewModel,
                                    initialTab = com.example.ui.screens.BrainFamilyTab.FAMILY,
                                    pendingInviteCode = pendingInviteCode,
                                    unreadNotificationCount = appNotifications.count { !it.isRead },
                                    onNotificationsClick = { goGuarded(ZadRoutes.NOTIFICATIONS) },
                                    onNavigateToStatementImport = { go(ZadRoutes.STATEMENT) },
                                    onNavigateToRoute = { route -> goGuarded(route) }
                                )
                            }
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
                            com.example.ui.screens.FinancesScreen(
                                viewModel = viewModel,
                                initialTab = com.example.ui.screens.FinancesTab.DAILY,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
                            )
                        }
                        composable(ZadRoutes.SHOPPING) {
                            com.example.ui.screens.PantryShoppingScreen(
                                viewModel = viewModel,
                                initialTab = com.example.ui.screens.PantryShoppingTab.SHOPPING,
                                onNavigateToAssistant = { go(ZadRoutes.ASSISTANT) },
                                onNavigateToCamera = { showCameraSheet = true }
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
                        composable(ZadNav.ACHIEVEMENTS) {
                            com.example.ui.screens.AchievementsRoute(onBack = { navController.popBackStack() })
                        }
                        composable(ZadNav.EDIT_PROFILE) { EditProfileScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.FAMILY_MANAGEMENT) { FamilyManagementScreen(familyViewModel) { navController.popBackStack() } }
                        composable(ZadNav.PAYMENT_BUDGET) { PaymentAndBudgetScreen(viewModel) { navController.popBackStack() } }
                        composable(ZadNav.ASSISTANT_ALERTS) { AssistantAlertsScreen { navController.popBackStack() } }
                        composable(ZadNav.TERMS) { TermsOfServiceScreen { navController.popBackStack() } }
                    }
                }
            }
            if (chromeVisible) {
                com.example.ui.components.DraggableFloatingCompanion(
                    companionMood = companionMood,
                    bottomNavHeight = 80.dp,
                    onOpenVoice = { showVoiceSheet = true },
                    onOpenChat = { showAgentOverlay = true }
                )
            }

            com.example.ui.components.ZadAgentOverlay(
                visible = showAgentOverlay,
                viewModel = viewModel,
                onDismiss = { showAgentOverlay = false },
                onOpenFullChat = {
                    showAgentOverlay = false
                    goGuarded(ZadRoutes.ASSISTANT)
                }
            )
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
            onDismiss = {
                showVoiceSheet = false
                voiceSheetLiveMode = false
            },
            initialLiveMode = voiceSheetLiveMode
        )
    }
}
