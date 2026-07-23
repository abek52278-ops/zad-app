package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.viewmodels.ZadViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.data.TasbihaTree
import com.example.data.ZadInventory
import com.example.data.ZadTransaction
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.Image

import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.FamilyState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.widgets.ZadVoiceFab
import com.example.ui.widgets.AffiliateProductCard

import com.example.ui.components.*
import java.util.Calendar

private const val TAG_HOME = "HomeScreen"

@Composable
fun HomeScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel = viewModel(),
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToShopping: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToTasbiha: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    /** تفعيل يدوي من الأب/الأم (Switch to Kids Mode) — بيفرض واجهة الأطفال حتى لو role الحساب "admin" */
    kidsModeOverride: Boolean = false
) {
    val inventory by viewModel.inventory.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val mealSuggestions by viewModel.mealSuggestions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val showBudgetDialog by viewModel.showBudgetDialog.collectAsState()
    val shoppingList by viewModel.shoppingList.collectAsState()
    val globalAvatarUri by viewModel.avatarUri.collectAsState()
    val affiliateProducts by viewModel.affiliateProducts.collectAsState()
    val urgentRecipes by viewModel.urgentRecipes.collectAsState()
    val upcomingSeasonalEvents by familyViewModel.upcomingSeasonalEvents.collectAsState()
    val seasonalForecasts by viewModel.seasonalForecasts.collectAsState()
    val expensePrediction by viewModel.expensePrediction.collectAsState()
    val agentSummary by viewModel.agentSummary.collectAsState()
    val isAgentLoading by viewModel.isAgentLoading.collectAsState()
    val autoSuggestions by viewModel.autoSuggestions.collectAsState()

    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalSpent = transactions.filter { it.isExpense }.sumOf { it.amount }
    val currentBudget = remainingBalance

    val shortageCount = remember(inventory) {
        val lowStock = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }
        val expiring = inventory.filter { item ->
            val d = daysUntilExpiry(item.expiryDate)
            d != null && d <= 3
        }
        (lowStock + expiring).distinctBy { it.id }.size
    }

    val context = LocalContext.current
    var isNotificationAccessGranted by remember {
        mutableStateOf(
            androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context).contains(context.packageName)
        )
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = androidx.core.app.NotificationManagerCompat
                    .getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val familyState by familyViewModel.state.collectAsState()
    val userNameState by viewModel.userName.collectAsState()
    
    val isChild = remember(familyState, kidsModeOverride) {
        kidsModeOverride || if (familyState is FamilyState.Active) {
            val activeState = familyState as FamilyState.Active
            activeState.myMemberInfo.role == "child"
        } else false
    }

    LaunchedEffect(Unit) {
        if (userNameState.isNullOrBlank()) viewModel.loadUserProfile()
        viewModel.refreshAgentSummary()
        viewModel.refreshAutoSuggestions()
        viewModel.predictNextMonthExpenses()
        familyViewModel.loadUpcomingSeasonalEvents()
        Log.d(TAG_HOME, "HomeScreen loaded — userName=$userNameState, budget=$budget, transactions=${transactions.size}, isChild=$isChild")
    }

    LaunchedEffect(upcomingSeasonalEvents) {
        if (upcomingSeasonalEvents.isNotEmpty()) viewModel.loadSeasonalForecast(upcomingSeasonalEvents)
    }

    val userName = userNameState ?: "..."

    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showNotificationsPanel by remember { mutableStateOf(false) }
    var showAllTransactionsDialog by remember { mutableStateOf(false) }
    var selectedRecipeTitle by remember { mutableStateOf<String?>(null) }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showTasbihaReminder by remember { mutableStateOf(false) }

    // Use FamilyViewModel's tasbiha data instead of direct SupabaseRepo call
    val myTasbiha = familyViewModel.myTasbiha
    LaunchedEffect(Unit) {
        familyViewModel.loadTasbiha()
    }

    // SMS Permission Request
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG_HOME, "SMS Permission granted: $isGranted")
    }

    LaunchedEffect(Unit) {
        val permission = android.Manifest.permission.RECEIVE_SMS
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permission)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        com.example.ui.components.ZadCanvasBackground(modifier = Modifier.fillMaxSize())
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val appNotificationsState by viewModel.appNotifications.collectAsState()
        PremiumTopBar(
            userName = userName,
            avatarUrl = globalAvatarUri?.toString(),
            hasUnreadNotifications = appNotificationsState.any { !it.isRead },
            onNotificationsClick = {
                Log.d(TAG_HOME, "🔔 Notifications icon clicked — showing panel")
                showNotificationsPanel = true
            }
        )
        if (isChild) {
            // KIDS MODE UI
            val needAmountPattern = stringResource(R.string.need_amount_purchase)
            KidsModeContent(
                familyState = familyState as? FamilyState.Active,
                onAddRequest = { title, amount ->
                    val meta = """{"amount":$amount,"status":"PENDING"}"""
                    familyViewModel.sendMessage(String.format(needAmountPattern, amount, title), "PURCHASE_REQUEST", meta)
                },
                onNavigateToFamily = onNavigateToFamily,
                onNavigateToTasbiha = onNavigateToTasbiha,
                myTasbiha = myTasbiha,
                affiliateProducts = affiliateProducts,
                viewModel = viewModel,
                familyViewModel = familyViewModel
            )
        } else {
            // ADULT/ADMIN MODE UI
            Spacer(modifier = Modifier.height(16.dp))
            
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (!isNotificationAccessGranted) {
                    NotificationPermissionCard {
                        Log.d(TAG_HOME, "NotificationPermissionCard button clicked")
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 1. Signature Visa-card style budget hero (orchestrated entrance: card, then circles, then stats, then banner)
                val calendar = Calendar.getInstance()
                val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                val daysLeft = lastDay - currentDay

                com.example.ui.components.AppearOnEntry {
                    com.example.ui.components.ZadCardHero(
                        budget = budget,
                        spent = totalSpent,
                        remaining = currentBudget,
                        daysLeft = daysLeft,
                        onDepositClick = { showAddTransactionDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 1b. Circular one-tap shortcuts to every screen
                com.example.ui.components.AppearOnEntry(delayMs = 80) {
                    com.example.ui.components.ZadPageShortcutsRow(
                        items = listOf(
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Inventory2, stringResource(R.string.nav_inventory), primary, onNavigateToInventory),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.ShoppingCart, stringResource(R.string.nav_shopping), catDailyIcon, onNavigateToShopping),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.FamilyRestroom, stringResource(R.string.nav_family), coral, onNavigateToFamily),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Subscriptions, stringResource(R.string.quick_stat_subscriptions_title), secondaryDark, onNavigateToSubscriptions),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.AccountBalanceWallet, stringResource(R.string.nav_budget), primaryDark, onNavigateToBudget),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.AutoAwesome, stringResource(R.string.nav_assistant), lilac, onNavigateToAssistant),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Park, stringResource(R.string.tasbiha_short_label), catHealthIcon, onNavigateToTasbiha),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Person, stringResource(R.string.profile_title), tertiary, onNavigateToProfile)
                        )
                    )
                }

                if (shortageCount > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    com.example.ui.components.ShortagesSummaryCard(
                        shortageCount = shortageCount,
                        onViewShortagesClick = {
                            InventoryNavState.openShortagesTab = true
                            onNavigateToInventory()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // 2. Premium Quick Stats
                val activeSubsCount = subscriptions.count { it.isActive }
                val familyCount = if (familyState is FamilyState.Active) (familyState as FamilyState.Active).members.size else 1
                com.example.ui.components.AppearOnEntry(delayMs = 150) {
                    PremiumQuickStatsRow(
                        inventoryCount = inventory.size,
                        activeSubsCount = activeSubsCount,
                        familyCount = familyCount,
                        onInventoryClick = onNavigateToInventory,
                        onSubsClick = onNavigateToSubscriptions,
                        onFamilyClick = onNavigateToFamily
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // 3. AI Insight Banner
                val topInsight = insights.firstOrNull()?.description ?: stringResource(R.string.no_urgent_alerts_hint)
                com.example.ui.components.AppearOnEntry(delayMs = 300) {
                    PremiumInsightBanner(
                        title = stringResource(R.string.zad_smart_insight_title),
                        subtitle = topInsight,
                        onClick = onNavigateToAssistant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // 4. Premium Meals Row
                // فاضية عمدًا لحد ما يتوفر اقتراح وصفات مبني فعليًا على مخزون المستخدم —
                // متتحطش أسماء وصفات هنا من غير تحقق حقيقي من المكونات المتاحة.
                val recipeTitles = emptyList<String>()
                PremiumMealsRow(
                    meals = recipeTitles,
                    onMealClick = { recipeName ->
                        selectedRecipeTitle = recipeName
                        showRecipeDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 5. Premium Transactions Row
                PremiumTransactionsRow(
                    transactions = transactions,
                    onSeeAllClick = { showAllTransactionsDialog = true }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 6. Kids Snippet
                PremiumKidsSnippet(
                    onKidsClick = onNavigateToFamily
                )
                Spacer(modifier = Modifier.height(32.dp))
                Spacer(modifier = Modifier.height(24.dp))

                // 4. Zad Alerts
                val alerts = insights.filter { it.type == "Alert" }
                if (alerts.isNotEmpty()) {
                    AiAlertBanner(title = alerts.first().title, description = alerts.first().description)
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    ZadProactiveSummaryCard(insights = insights)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4a2. Agent Summary — ملخص الوكيل الذكي (كان محسوب ومهدور بدون عرض)
                agentSummary?.let { summary ->
                    AgentSummaryCard(
                        agentSummary = summary,
                        isLoading = isAgentLoading,
                        onRefresh = { viewModel.refreshAgentSummary() },
                        onNavigateToAssistant = onNavigateToAssistant,
                        onNavigateToShopping = onNavigateToShopping,
                        onNavigateToInventory = onNavigateToInventory
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4a3. Auto Suggestions — اقتراحات ذكية سريعة (nudges عامة، منفصلة عن ملخص الوكيل)
                if (autoSuggestions.isNotEmpty()) {
                    AutoSuggestionsCard(suggestions = autoSuggestions)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4b. Events Radar — رادار المناسبات (family-only: needs cross-member transaction history)
                if (familyState is FamilyState.Active && seasonalForecasts.isNotEmpty()) {
                    EventsRadarCard(forecasts = seasonalForecasts)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4c. Expense prediction — يقف جوار رادار المناسبات كـ"مستشار زاد" الثاني
                expensePrediction?.let { prediction ->
                    PredictionCard(prediction, currentBudget)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 5. Mini Inventory Edge
                MiniInventoryWidget(inventory = inventory, onNavigateToInventory = onNavigateToInventory)
                Spacer(modifier = Modifier.height(24.dp))

                // 6. Mini Shopping List Edge
                MiniShoppingWidget(shoppingList = shoppingList, onNavigateToShopping = onNavigateToShopping)
                Spacer(modifier = Modifier.height(24.dp))

                // 6b. Tasbiha Widget
                TasbihaHomeWidget(tree = myTasbiha, onNavigateToTasbiha = onNavigateToTasbiha)
                Spacer(modifier = Modifier.height(24.dp))

                // 6c. العقل → الوصفات: "عندك دجاج هينتهي بكرة → 3 وصفات بيه"
                urgentRecipes?.let { urgent ->
                    UrgentRecipeCard(
                        triggerItems = urgent.triggerItems,
                        text = urgent.text,
                        onOpenChat = onNavigateToAssistant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 7. AI Chef Widget
                SmartChefSection(
                    suggestions = mealSuggestions,
                    onViewAll = {
                        onNavigateToAssistant()
                    },
                    onRecipeClick = { title ->
                        selectedRecipeTitle = title
                        showRecipeDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 8. Amazon Affiliate Suggestions
                val activeAffiliateProducts = affiliateProducts.filter { it.isActive }
                if (activeAffiliateProducts.isNotEmpty()) {
                    Text(stringResource(R.string.shop_from_amazon), style = Typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(activeAffiliateProducts.take(5)) { product ->
                            AffiliateProductCard(
                                product = product,
                                onBuyClick = {
                                    viewModel.recordAffiliateClick(product.id, "home")
                                    com.example.data.AffiliateHelper.openProduct(context, product)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // clears both the voice FAB (bottom 24.dp + 70.dp tall) and the family chat
                // FAB stacked above it on this screen (bottom 106.dp + 56.dp tall), so the
                // last list item isn't left partially hidden behind either
                Spacer(modifier = Modifier.height(170.dp))
            } // closes inner Column
        } // closes else block (line 125)
    } // closes outer Column (line 103)
    
    // Floating Voice Agent Button
    ZadVoiceFab(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp),
        viewModel = viewModel
    )
} // closes Box
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            onDismiss = { showAddTransactionDialog = false },
            onSave = { amount, title, isExpense, category ->
                Log.d(TAG_HOME, "AddTransactionDialog SAVE → amount=$amount, title=$title, isExpense=$isExpense, category=$category")
                viewModel.addTransaction(amount, title, isExpense, category)
                showAddTransactionDialog = false
            }
        )
    }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentBudget = budget,
            onDismiss = { viewModel.hideBudgetDialog() },
            onSave = { newBudget ->
                Log.d(TAG_HOME, "BudgetEditDialog SAVE → newBudget=$newBudget → calling viewModel.updateBudget()")
                viewModel.updateBudget(newBudget)
                viewModel.hideBudgetDialog()
            }
        )
    }

    if (showNotificationsPanel) {
        NotificationsBottomSheet(
            viewModel = viewModel,
            onDismiss = { showNotificationsPanel = false }
        )
    }

    if (showAllTransactionsDialog) {
        AlertDialog(
            onDismissRequest = { showAllTransactionsDialog = false },
            title = { Text(stringResource(R.string.recent_transactions_full_log)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(transactions.reversed()) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(tx.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(tx.category ?: "Other", style = Typography.labelSmall, color = Color.Gray)
                            }
                            Text(
                                text = "${if (tx.isExpense) "-" else "+"}${tx.amount}",
                                style = Typography.bodyMedium,
                                color = if (tx.isExpense) dangerColor else successColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllTransactionsDialog = false }) { Text(stringResource(R.string.close_action)) }
            }
        )
    }
    if (showRecipeDialog && selectedRecipeTitle != null) {
        RecipeDetailDialog(
            recipeTitle = selectedRecipeTitle!!,
            inventory = inventory,
            onDismiss = { showRecipeDialog = false }
        )
    }
}

@Composable
fun TopAppBarSection(onOpenDrawer: () -> Unit, userName: String, globalAvatarUri: String?, unreadNotificationsCount: Int = 0, onNotificationsClick: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Right side (RTL Start) - Profile & Greeting
        Row(verticalAlignment = Alignment.CenterVertically) {
            var profileExpanded by remember { mutableStateOf(false) }
            Box {
                if (!globalAvatarUri.isNullOrBlank()) {
                    AsyncImage(
                        model = globalAvatarUri,
                        contentDescription = "User Profile",
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        error = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, primaryFixed, CircleShape)
                            .clickable { profileExpanded = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, primaryFixed, CircleShape)
                            .background(primary.copy(alpha = 0.12f))
                            .clickable { profileExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.take(1).uppercase().ifEmpty { "?" },
                            style = Typography.titleLarge,
                            color = primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                DropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_title)) },
                        onClick = { profileExpanded = false; onNavigateToProfile() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_menu)) },
                        onClick = { profileExpanded = false; onOpenDrawer() }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.welcome_back_comma),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                Text(
                    text = userName.ifEmpty { stringResource(R.string.guest_name_fallback) } + " \uD83D\uDC4B",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
        }

        // Left side (RTL End) - Notifications & Menu
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Notifications
            IconButton(onClick = onNotificationsClick, modifier = Modifier
                .size(44.dp)
                .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.05f))
                .clip(CircleShape)
                .background(surface)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = onSurfaceVariant, modifier = Modifier.size(24.dp))
                    if (unreadNotificationsCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dangerColor)
                                .border(1.5.dp, surface, CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }

            // Menu Drawer
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.05f))
                    .clip(CircleShape)
                    .background(surface)
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun BudgetCardSection(
    currentBudget: Double,
    totalSpent: Double,
    totalBudget: Double,
    onAddClick: () -> Unit = {},
    onBudgetLongClick: () -> Unit = {}
) {
    val percentage = if (totalBudget > 0) (totalSpent / totalBudget * 100).toInt().coerceIn(0, 100) else 0
    val isDanger = percentage >= 90
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = primary.copy(alpha = 0.4f)
            )
            .clickable { onBudgetLongClick() }
    ) {
        com.example.ui.components.HeroGradientCard(
            colors = if (isDanger) listOf(Color(0xFFE53935), Color(0xFFC62828)) else listOf(primary, secondary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Decorative circles
                Box(modifier = Modifier.offset(x = (-40).dp, y = (-40).dp).size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))
                Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 50.dp, y = 50.dp).size(200.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.allocated_salary),
                                style = Typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = totalBudget.toInt().toString(),
                                    style = Typography.displayLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.currency),
                                    style = Typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onAddClick,
                            modifier = Modifier.height(44.dp).pressableScale(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = primary
                            ),
                            shape = RoundedCornerShape(999.dp),
                            elevation = ButtonDefaults.buttonElevation(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.deposit), style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    com.example.ui.components.GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        containerColor = Color.White.copy(alpha = 0.15f),
                        borderColor = Color.White.copy(alpha = 0.2f),
                        contentPadding = 20.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.expense),
                                    style = Typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = String.format("%.2f", totalSpent) + " " + stringResource(R.string.currency),
                                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.remaining),
                                    style = Typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = String.format("%.2f", currentBudget) + " " + stringResource(R.string.currency),
                                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress bar — spring physics (ZadSprings.Screen) instead of a linear tween
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                        ) {
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = percentage / 100f,
                                animationSpec = com.example.ui.components.ZadSprings.Screen
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        com.example.ui.components.AppearOnEntry(delayMs = 300) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDanger) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.budget_percent, percentage),
                                    style = Typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetEditDialog(currentBudget: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var budgetStr by remember { mutableStateOf(currentBudget.toInt().toString()) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_monthly_budget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.budget_save_note), style = Typography.labelSmall, color = onSurfaceVariant)
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text(stringResource(R.string.budget_with_currency, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = budgetStr.toDoubleOrNull() ?: currentBudget
                Log.d(TAG_HOME, "BudgetEditDialog confirm → parsed=$parsed")
                onSave(parsed)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ExpenseAnalysisSection(transactions: List<ZadTransaction> = emptyList()) {
    // Groups spending by day of week from actual transactions
    val dayLabels = androidx.compose.ui.res.stringArrayResource(R.array.week_day_labels).toList()
    val dayTotals = FloatArray(7) { 0f }
    transactions.filter { it.isExpense }.forEach { tx ->
        val instant = tx.createdAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return@forEach
        // week_day_labels starts Sunday; DayOfWeek.value is MONDAY=1..SUNDAY=7, so %7 maps SUNDAY->0.
        val dayIndex = instant.atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value % 7
        dayTotals[dayIndex] += tx.amount.toFloat()
    }
    val maxVal = dayTotals.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val heightRatios = dayTotals.map { (it / maxVal).coerceIn(0.05f, 1.0f) }
    val maxDayIndex = dayTotals.indices.maxByOrNull { dayTotals[it] } ?: 3

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(28.dp))
            .background(surface)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.expense_analysis),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(catFoodBg)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = "Insights",
                    tint = catFoodIcon,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (transactions.isEmpty()) {
            com.example.ui.components.ZadEmptyState(
                title = stringResource(R.string.no_transactions_for_analysis),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(surfaceContainerLow, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dayLabels.forEachIndexed { i, label ->
                    ChartBar(label = label, heightRatio = heightRatios[i], isSelected = i == maxDayIndex)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(color = secondary, text = stringResource(R.string.grocery), bgColor = secondaryContainer, textColor = onSecondaryContainer)
            CategoryChip(color = primary, text = stringResource(R.string.subscriptions), bgColor = catBillsBg, textColor = catBillsIcon)
            CategoryChip(color = tertiary, text = stringResource(R.string.restaurants), bgColor = tertiaryContainer, textColor = onTertiaryContainer)
        }
    }
}

@Composable
fun ChartBar(label: String, heightRatio: Float, isSelected: Boolean) {
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        animationPlayed = true
    }

    val animatedHeightRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animationPlayed) heightRatio else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 1000,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedHeightRatio)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (isSelected) Brush.verticalGradient(listOf(primary.copy(alpha = 0.8f), primary)) 
                    else Brush.verticalGradient(listOf(primaryFixed.copy(alpha = 0.2f), primaryFixed.copy(alpha = 0.4f)))
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = Typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primary else onSurfaceVariant
        )
    }
}

@Composable
fun CategoryChip(color: Color, text: String, bgColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = text, style = Typography.labelSmall, color = textColor)
    }
}

@Composable
fun QuickStatsSection(inventoryCount: Int, subsCount: Int) {
    // ✅ Counts from Room DB (real data): inventory.size from zad_inventory, subscriptions.size from zad_subscriptions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Inventory2,
            iconColor = catBankingIcon,
            iconBg = catBankingBg,
            label = stringResource(R.string.inventory_items),
            value = inventoryCount.toString() + " " + stringResource(R.string.inventory_items)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Subscriptions,
            iconColor = catBillsIcon,
            iconBg = catBillsBg,
            label = stringResource(R.string.active_subscriptions),
            value = subsCount.toString() + " " + stringResource(R.string.subscriptions)
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = Typography.labelMedium,
            color = onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = Typography.titleMedium,
            color = onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

/** كرت "العقل → الوصفات": يظهر لما فيه أصناف هتخلص/تنتهي مع اقتراحات وصفات فعلية بيها */
@Composable
fun UrgentRecipeCard(
    triggerItems: List<String>,
    text: String,
    onOpenChat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF7ED),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenChat
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFED7AA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.items_expiring_soon_hint, triggerItems.take(2).joinToString("، ")),
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412)
                    )
                    Text(stringResource(R.string.suggested_recipes_before_expiry), style = Typography.labelSmall, color = Color(0xFFC2410C))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text.take(220),
                style = Typography.bodySmall,
                color = Color(0xFF7C2D12),
                lineHeight = 18.sp,
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.tap_for_more_in_chat), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
        }
    }
}

@Composable
fun SmartChefSection(
    suggestions: String,
    onViewAll: () -> Unit,
    onRecipeClick: (String) -> Unit
) {
    val isRealAi = suggestions.isNotBlank() && suggestions.startsWith("1.") || suggestions.startsWith("-") || suggestions.startsWith("•")

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(catFoodBg),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.SmartToy, contentDescription = null, tint = catFoodIcon, modifier = Modifier.size(18.dp)) }
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.zad_chef_suggestions), style = Typography.titleMedium, color = onSurface)
            }
            TextButton(onClick = onViewAll) { Text(stringResource(R.string.view_all), style = Typography.labelMedium, color = primary) }
        }
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (isRealAi) {
                val lines = suggestions.split("\n").filter { it.trim().isNotEmpty() }.take(3)
                items(lines) { line ->
                    val cleanLine = line.replace(Regex("^[\\d\\-•·.]+\\s*"), "").trim()
                    val title = cleanLine.substringBefore(":").substringBefore("(").take(30)
                    val desc = cleanLine.take(80)
                    MealCard(
                        title = title, desc = desc,
                        status = stringResource(R.string.zad_chef_suggestion_status),
                        isAvailable = true,
                        imgUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400&q=80",
                        onClick = { onRecipeClick(title) }
                    )
                }
            } else {
                item {
                    val name = stringResource(R.string.meal_chicken_pasta)
                    MealCard(
                        title = name,
                        desc = stringResource(R.string.meal_chicken_pasta_desc),
                        status = stringResource(R.string.meal_status_available),
                        isAvailable = true,
                        imgUrl = "https://images.unsplash.com/photo-1621996346565-e3dbc646d9a9?w=400&q=80",
                        onClick = { onRecipeClick(name) }
                    )
                }
                item {
                    val name = stringResource(R.string.meal_quinoa_salad)
                    MealCard(
                        title = name,
                        desc = stringResource(R.string.meal_quinoa_salad_desc),
                        status = stringResource(R.string.meal_status_missing_lemon),
                        isAvailable = false,
                        imgUrl = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=400&q=80",
                        onClick = { onRecipeClick(name) }
                    )
                }
                item {
                    val name = stringResource(R.string.meal_breakfast_shakshuka)
                    MealCard(
                        title = name,
                        desc = stringResource(R.string.meal_breakfast_shakshuka_desc),
                        status = stringResource(R.string.meal_status_available),
                        isAvailable = true,
                        imgUrl = "https://images.unsplash.com/photo-1590412200988-a436970781fa?w=400&q=80",
                        onClick = { onRecipeClick(name) }
                    )
                }
            }
        }
    }
}


@Composable
fun MealCard(title: String, desc: String, status: String, isAvailable: Boolean, imgUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
            AsyncImage(
                model = imgUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isAvailable) catFoodBg else Color(0xFFFFF3E0))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAvailable) Icons.Default.Eco else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAvailable) catFoodIcon else warningColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = status,
                    style = Typography.labelSmall,
                    color = if (isAvailable) catFoodIcon else warningColor
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontSize = 14.sp),
                color = onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = Typography.labelSmall,
                color = onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun RecentTransactionsSection(transactions: List<ZadTransaction>, onViewAll: () -> Unit = {}) {
    // ✅ transactions from Room DB zad_transactions (real data synced from Supabase)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(RoundedCornerShape(24.dp))
            .background(surface)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_transactions),
                style = Typography.titleMedium,
                color = onSurface
            )
            // ✅ FIXED: Now a clickable IconButton
            IconButton(onClick = {
                Log.d(TAG_HOME, "MoreHoriz (transactions) clicked → onViewAll()")
                onViewAll()
            }) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More",
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (transactions.isEmpty()) {
            com.example.ui.components.ZadEmptyState(
                title = stringResource(R.string.no_transactions),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        } else {
            transactions.take(5).forEach { tx ->
                TransactionItem(
                    title = tx.title,
                    subtitle = tx.category ?: "عام",
                    amount = String.format("%.2f", tx.amount) + " " + stringResource(R.string.currency),
                    isExpense = tx.isExpense
                )
                HorizontalDivider(color = outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        // ✅ FIXED: stringResource(R.string.view_full_history) button now has real onClick with Log
        Button(
            onClick = {
                Log.d(TAG_HOME, "عرض السجل الكامل clicked — total=${transactions.size} transactions in Room DB")
                onViewAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = primary
            ),
            shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, primary)
        ) {
            Text(
                text = stringResource(R.string.view_full_history),
                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun TransactionItem(
    icon: ImageVector = Icons.Default.ShoppingCart,
    iconBg: Color = primaryContainer,
    iconColor: Color = primary,
    title: String,
    subtitle: String,
    amount: String,
    isExpense: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontSize = 14.sp),
                color = onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = Typography.labelSmall,
                color = onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = Typography.titleMedium.copy(fontSize = 14.sp),
            color = if (isExpense) dangerColor else successColor
        )
    }
}

@Composable
fun AgentSummaryCard(
    agentSummary: com.example.data.AiAgentSummary?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToInventory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = primary.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(20.dp))
            .background(primary),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.zad_agent), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_cd), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                com.example.ui.components.ZadLoadingState(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    size = 24.dp,
                    strokeWidth = 2.dp
                )
            } else if (agentSummary != null) {
                Text(agentSummary.summary, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)

                if (agentSummary.alerts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    agentSummary.alerts.take(2).forEach { alert ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                when (alert.type) { "warning" -> Icons.Default.Warning; "success" -> Icons.Default.CheckCircle; else -> Icons.Default.Info },
                                contentDescription = null, tint = when (alert.type) {
                                    "warning" -> Color(0xFFFF9800); "success" -> Color(0xFF4CAF50); else -> Color.White.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(alert.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (agentSummary.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        agentSummary.suggestions.take(3).forEach { suggestion ->
                            val suggestionColor = when (suggestion.action) {
                                "add_to_shopping" -> Color(0xFF4CAF50)
                                "check_budget" -> Color(0xFFFF9800)
                                "cook_meal" -> Color(0xFF2196F3)
                                else -> Color.White.copy(alpha = 0.3f)
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(suggestionColor.copy(alpha = 0.2f)).clickable {
                                    when (suggestion.action) {
                                        "add_to_shopping" -> onNavigateToShopping()
                                        "cook_meal" -> onNavigateToAssistant()
                                        else -> onNavigateToInventory()
                                    }
                                }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(suggestion.reason, color = Color.White, fontSize = 11.sp, maxLines = 1) }
                        }
                    }
                }

                if (agentSummary.stats.inventoryCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(stringResource(R.string.nav_inventory), "${agentSummary.stats.inventoryCount}", Color.White)
                        StatItem(stringResource(R.string.expiring_soon_stat_label), "${agentSummary.stats.expiringSoon}",
                            if (agentSummary.stats.expiringSoon > 0) Color(0xFFFF9800) else Color.White.copy(alpha = 0.6f))
                        StatItem(stringResource(R.string.subscriptions), "${agentSummary.stats.subscriptionsActive}", Color.White)
                    }
                }
            } else {
                Text(stringResource(R.string.zad_analyzing_now), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AutoSuggestionsCard(suggestions: List<com.example.data.ZadAiRepository.AutoSuggestion>) {
    val priorityOrder = mapOf("high" to 0, "medium" to 1, "low" to 2)
    val sorted = suggestions.sortedBy { priorityOrder[it.priority] ?: 1 }.take(4)
    GlassCard(
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("اقتراحات سريعة", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sorted.forEach { suggestion ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(suggestion.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(suggestion.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        if (suggestion.description.isNotBlank()) {
                            Text(suggestion.description, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
fun PredictionCard(prediction: com.example.data.AiExpensePrediction, budget: Double) {
    val context = LocalContext.current
    val color = when {
        prediction.predictedTotal > budget -> dangerColor
        prediction.predictedTotal > budget * 0.8 -> Color(0xFFF9A825)
        else -> successColor
    }
    GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.zad_prediction_next_month), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.example.data.CurrencyFormatter.format(context, prediction.predictedTotal), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.prediction_confidence, (prediction.confidence * 100).toInt()), color = onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (prediction.warnings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            prediction.warnings.take(2).forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(w, color = onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        if (prediction.tips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            prediction.tips.take(2).forEach { tip ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tip, color = onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun seasonalEventDisplayName(slug: String?): Int? = when (slug) {
    "ramadan" -> R.string.event_ramadan
    "eid_al_fitr" -> R.string.event_eid_al_fitr
    "eid_al_adha" -> R.string.event_eid_al_adha
    "back_to_school" -> R.string.event_back_to_school
    else -> null
}

@Composable
fun EventsRadarCard(forecasts: List<com.example.data.AiSeasonalForecast>) {
    val next = forecasts.minByOrNull { it.daysUntil } ?: return
    val context = LocalContext.current
    val nameResId = seasonalEventDisplayName(next.slug)
    GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.events_radar_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (nameResId != null) stringResource(nameResId) else next.slug ?: "",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(stringResource(R.string.events_radar_days_until, next.daysUntil), color = onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.example.data.CurrencyFormatter.format(context, next.predictedTotal), color = dangerColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.prediction_confidence, (next.confidence * 100).toInt()), color = onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (next.tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(next.tip, color = onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
fun HomeScreenPreview() {}

@Composable
fun NotificationPermissionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = catFoodBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = catFoodIcon,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.enable_bank_notifications), fontWeight = FontWeight.Bold, color = onSurface)
                Text(
                    stringResource(R.string.enable_bank_notifications_desc),
                    fontSize = 12.sp,
                    color = onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    Log.d(TAG_HOME, "تفعيل إشعارات البنك clicked → startActivity(NOTIFICATION_LISTENER_SETTINGS)")
                    onClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.enable), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AiAlertBanner(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = dangerColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = dangerColor)
                Text(
                    description,
                    fontSize = 12.sp,
                    color = dangerColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String, Boolean, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("عام") }
    val noDescriptionFallback = stringResource(R.string.no_description_fallback)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExpense) stringResource(R.string.add_expense_title) else stringResource(R.string.add_income_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Type Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text(stringResource(R.string.income_deposit)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text(stringResource(R.string.expense_deduction)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = dangerColor,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.description_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isExpense) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(stringResource(R.string.category_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    val finalCategory = if (!isExpense) "دخل" else category
                    val finalTitle = title.ifEmpty { noDescriptionFallback }
                    Log.d(TAG_HOME, "AddTransactionDialog → confirm: amount=$parsedAmount, title=$finalTitle, isExpense=$isExpense, category=$finalCategory")
                    onSave(parsedAmount, finalTitle, isExpense, finalCategory)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExpense) dangerColor else primary
                )
            ) {
                Text(if (isExpense) stringResource(R.string.deduct_amount) else stringResource(R.string.add_amount))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DashboardSummariesSection(
    inventory: List<com.example.data.ZadInventory>,
    subscriptions: List<com.example.data.ZadSubscription>,
    shoppingList: List<com.example.data.ZadShoppingItem>,
    onNavigateToInventory: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToShopping: () -> Unit
) {
    val context = LocalContext.current
    val quantityTemplate = stringResource(R.string.quantity_colon_count)
    val countTemplate = stringResource(R.string.count_colon)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Inventory Summary
        MiniTableCard(
            title = stringResource(R.string.top_low_stock_title),
            icon = Icons.Default.Inventory2,
            itemsCount = inventory.size,
            items = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }.take(3).map {
                MiniTableRow(it.itemName, String.format(quantityTemplate, it.quantity), if (it.quantity == 0) dangerColor else primary)
            },
            onSeeAll = onNavigateToInventory
        )

        // 2. Subscriptions Summary
        MiniTableCard(
            title = stringResource(R.string.active_subscriptions_title),
            icon = Icons.Default.CalendarToday,
            itemsCount = subscriptions.filter { it.isActive }.size,
            items = subscriptions.filter { it.isActive }.take(3).map {
                MiniTableRow(it.title, com.example.data.CurrencyFormatter.format(context, it.amount), primary)
            },
            onSeeAll = onNavigateToSubscriptions
        )

        // 3. Shopping List Summary
        MiniTableCard(
            title = stringResource(R.string.household_shortages_title),
            icon = Icons.Default.ShoppingCart,
            itemsCount = shoppingList.filter { !it.isPurchased }.size,
            items = shoppingList.filter { !it.isPurchased }.take(3).map {
                MiniTableRow(it.itemName, String.format(countTemplate, it.quantity), dangerColor)
            },
            onSeeAll = onNavigateToShopping
        )
    }
}

data class MiniTableRow(val name: String, val detail: String, val color: Color)

@Composable
fun MiniTransactionsWidget(
    transactions: List<ZadTransaction>,
    onNavigateToTransactions: () -> Unit
) {
    val recentTransactions = transactions.sortedByDescending { it.id }.take(3)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.recent_transactions), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                }
                TextButton(onClick = onNavigateToTransactions, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.view_all), style = Typography.labelMedium, color = primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (recentTransactions.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.no_transactions_recorded),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                recentTransactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (tx.isExpense) dangerColor else successColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(tx.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
                                Text(tx.category ?: "عام", style = Typography.labelSmall, color = onSurfaceVariant)
                            }
                        }
                        Text(
                            text = "${if (tx.isExpense) "-" else "+"}${com.example.data.CurrencyFormatter.format(context, tx.amount)}",
                            style = Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (tx.isExpense) dangerColor else successColor
                        )
                    }
                    if (tx != recentTransactions.last()) {
                        HorizontalDivider(color = outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}


@Composable
fun MiniTableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    itemsCount: Int,
    items: List<MiniTableRow>,
    onSeeAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSeeAll, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.view_all_count, itemsCount), style = Typography.labelSmall, color = primary)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (items.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.no_items_to_show),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.name, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = item.detail, style = Typography.labelMedium, color = item.color, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun ZadProactiveSummaryCard(insights: List<com.example.data.AiInsight>) {
    LaunchedEffect(insights) {
        Log.d("HomeScreen", "🤖 الذكاء الاستباقي (ميزة 2): تم عرض ${insights.size} نصيحة استباقية")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.proactive_summary_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (insights.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.proactive_summary_loading)
                )
            } else {
                insights.forEachIndexed { index, insight ->
                    val (icon, color) = when (insight.type.uppercase()) {
                        "WARNING" -> Icons.Default.Warning to dangerColor
                        "REWARD" -> Icons.Default.Star to Color(0xFFFFC107) // Amber
                        else -> Icons.Default.Lightbulb to primary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(insight.title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
                            Text(insight.description, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                    if (index < insights.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickGlanceWidgets(
    inventoryCount: Int,
    criticalInventoryCount: Int,
    shoppingListCount: Int,
    shoppingTotal: Double,
    insightsCount: Int
) {
    val context = LocalContext.current
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlanceWidgetCard(
                title = stringResource(R.string.nav_inventory),
                icon = Icons.Default.Inventory2,
                color = primary,
                mainValue = stringResource(R.string.product_count, inventoryCount),
                subValue = if (criticalInventoryCount > 0) stringResource(R.string.critical_shortage_count, criticalInventoryCount) else stringResource(R.string.all_good),
                subColor = if (criticalInventoryCount > 0) dangerColor else onSurfaceVariant
            )
        }
        item {
            GlanceWidgetCard(
                title = stringResource(R.string.purchases_title),
                icon = Icons.Default.ShoppingCart,
                color = Color(0xFFE65100), // Orange
                mainValue = stringResource(R.string.items_count, shoppingListCount),
                subValue = stringResource(R.string.total_colon, com.example.data.CurrencyFormatter.format(context, shoppingTotal)),
                subColor = onSurfaceVariant
            )
        }
        item {
            GlanceWidgetCard(
                title = stringResource(R.string.todays_recommendations_title),
                icon = Icons.Default.Lightbulb,
                color = Color(0xFFFBC02D), // Yellow
                mainValue = stringResource(R.string.recommendations_count, insightsCount),
                subValue = stringResource(R.string.from_ai),
                subColor = onSurfaceVariant
            )
        }
    }
}

@Composable
fun GlanceWidgetCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    mainValue: String,
    subValue: String,
    subColor: Color
) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = Typography.labelMedium, color = onSurfaceVariant)
            Text(mainValue, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Text(subValue, style = Typography.labelSmall, color = subColor)
        }
    }
}

@Composable
fun KidsModeContent(
    familyState: com.example.ui.viewmodels.FamilyState.Active?,
    onAddRequest: (title: String, amount: Double) -> Unit,
    onNavigateToFamily: () -> Unit = {},
    onNavigateToTasbiha: () -> Unit = {},
    myTasbiha: TasbihaTree? = null,
    affiliateProducts: List<com.example.data.AffiliateProduct> = emptyList(),
    viewModel: ZadViewModel? = null,
    familyViewModel: FamilyViewModel? = null,
    showBalanceNumber: Boolean = true
) {
    if (familyState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading_family_data), color = onSurfaceVariant)
        }
        return
    }

    val myChores = familyState.chores.filter { it.assignedTo == familyState.myMemberInfo.id }
    val myAllowance = familyState.myMemberInfo.balance
    val mySavingsGoal = familyState.myMemberInfo.savingsGoal
    val myAlias = familyState.myMemberInfo.alias.ifBlank { stringResource(R.string.hero_name_fallback) }
    val recentMessages = familyState.messages.takeLast(3)
    val context = LocalContext.current
    val unknownAliasFallback = stringResource(R.string.unknown_alias_fallback)
    var showWishDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── Cute Greeting Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            KidAvatar(seed = familyState.myMemberInfo.id.ifBlank { myAlias }, size = 52.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.greeting_hi_name, myAlias), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Text(stringResource(R.string.greeting_subtitle), style = Typography.labelMedium, color = onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))

        // ── Balance Card (Candy Gradient) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(28.dp), spotColor = kidsPrimary.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(kidsPrimary, Color(0xFFEC4899), Color(0xFFF59E0B))))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.available_spending), color = Color.White.copy(alpha = 0.9f), style = Typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (showBalanceNumber) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = myAllowance,
                            label = "BalanceAnimation"
                        ) { targetAllowance ->
                            Text(com.example.data.CurrencyFormatter.formatNumber(context, targetAllowance), color = Color.White, style = Typography.displayLarge, fontWeight = FontWeight.Black)
                        }
                        Text(" " + com.example.data.CurrencyFormatter.symbol(context), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    }
                }
                if (mySavingsGoal > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🎯 " + stringResource(R.string.savings_goal_colon, com.example.data.CurrencyFormatter.format(context, mySavingsGoal)), color = Color.White.copy(alpha = 0.95f), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("🚀", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (myAllowance / mySavingsGoal).toFloat().coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(1500, easing = FastOutSlowInEasing),
                        label = "ProgressAnimation"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                val myDailyLimit = familyState.myMemberInfo.dailyLimit
                if (myDailyLimit != null && myDailyLimit > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val spentToday = com.example.data.approvedSpendSince(familyState.messages, familyState.myMemberInfo.id, java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    val limitRatio = (spentToday / myDailyLimit).toFloat().coerceIn(0f, 1f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("💸 " + stringResource(R.string.spent_today_colon, com.example.data.CurrencyFormatter.format(context, spentToday), com.example.data.CurrencyFormatter.format(context, myDailyLimit)), color = Color.White.copy(alpha = 0.95f), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { limitRatio },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (limitRatio >= 1f) Color(0xFFFECACA) else Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { showWishDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = kidsPrimaryDark),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.pressableScale()
                ) {
                    Text("✋", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.need_extra_expense), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── My Chores Section (Fun cards) ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.my_tasks), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (myChores.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF0FDF4), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.no_tasks_now), color = Color(0xFF15803D), style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            myChores.forEachIndexed { index, chore ->
                AppearOnEntry(delayMs = (index * 60).coerceAtMost(400)) {
                val choreRowShape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .shadow(elevation = 4.dp, shape = choreRowShape, spotColor = kidsPrimary.copy(alpha = 0.12f))
                        .clip(choreRowShape)
                        .background(if (chore.isCompleted) Color(0xFFECFDF5) else surface)
                        .pressableScale()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                            .background(if (chore.isCompleted) Color(0xFF22C55E) else kidsPrimaryLight.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (chore.isCompleted) "✅" else "📋", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chore.title, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 14.sp)
                        if (chore.rewardAmount > 0) {
                            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFEF3C7), modifier = Modifier.padding(top = 2.dp)) {
                                Text("🪙 +" + com.example.data.CurrencyFormatter.format(context, chore.rewardAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Gamification Badges ──
        val completedChores = myChores.count { it.isCompleted }
        val savingsProgress = if (mySavingsGoal > 0) (myAllowance / mySavingsGoal).toFloat().coerceIn(0f, 1f) else 0f
        KidBadgeRow(
            badges = buildKidBadges(
                completedChores = completedChores,
                tasbihaStreakDays = myTasbiha?.streakDays ?: 0,
                savingsProgress = savingsProgress
            )
        )
        Spacer(modifier = Modifier.height(22.dp))

        // ── Mini Family Chat ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💬", fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.last_family_messages), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNavigateToFamily) {
                Text(stringResource(R.string.open_chat), fontSize = 12.sp)
                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (recentMessages.isEmpty()) {
            com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_messages_yet))
        } else {
            recentMessages.forEach { msg ->
                val senderAlias = familyState.members.find { it.userId == msg.senderId }?.alias?.ifBlank { unknownAliasFallback } ?: unknownAliasFallback
                Surface(shape = RoundedCornerShape(14.dp), color = surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        KidAvatar(seed = msg.senderId.ifBlank { senderAlias }, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(senderAlias, fontSize = 10.sp, color = kidsPrimary, fontWeight = FontWeight.Bold)
                            Text(msg.message, fontSize = 13.sp, color = onSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Tasbiha Widget ──
        Surface(
            onClick = onNavigateToTasbiha,
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF1F8E9),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0xFF2E7D32).copy(alpha = 0.15f))
                .pressableScale()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌳", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasbiha_garden), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text(if (myTasbiha != null) stringResource(R.string.your_tree_score, myTasbiha.score) else stringResource(R.string.plant_your_tree), fontSize = 12.sp, color = Color(0xFF558B2F))
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF2E7D32))
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Amazon Suggestions ──
        val activeAffiliate = affiliateProducts.filter { it.isActive }
        if (activeAffiliate.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛍️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.shopping_suggestions), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(activeAffiliate.take(5)) { product ->
                    com.example.ui.widgets.AffiliateProductCard(
                        product = product,
                        onBuyClick = {
                            viewModel?.recordAffiliateClick(product.id, "kids_home")
                            com.example.data.AffiliateHelper.openProduct(context, product)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── قائمة أمنيات: طلب صنف/مبلغ يروح لموافقة الأب/الأم (نفس آلية PURCHASE_REQUEST
    // الموجودة أصلاً) — قبل كده الزرار ده كان بيبعت amount:0 دايماً بدل مبلغ حقيقي ──
    if (showWishDialog) {
        var wishTitle by remember { mutableStateOf("") }
        var wishAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWishDialog = false },
            title = { Text(stringResource(R.string.expense_purchase_request)) },
            text = {
                Column {
                    OutlinedTextField(value = wishTitle, onValueChange = { wishTitle = it }, label = { Text(stringResource(R.string.what_to_buy)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = wishAmount, onValueChange = { wishAmount = it }, label = { Text(stringResource(R.string.requested_amount_with_currency, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = wishAmount.toDoubleOrNull() ?: 0.0
                        if (wishTitle.isNotBlank() && amount > 0) {
                            onAddRequest(wishTitle, amount)
                            showWishDialog = false
                        }
                    },
                    modifier = Modifier.pressableScale(),
                    shape = RoundedCornerShape(50)
                ) { Text(stringResource(R.string.send_request)) }
            },
            dismissButton = { TextButton(onClick = { showWishDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private val kidAvatarEmojis = listOf("🦁", "🐼", "🦊", "🐨", "🐯", "🐰", "🐸", "🦉", "🐵", "🐻", "🦄", "🐳")
private val kidAvatarColors = listOf(
    Color(0xFFFDE68A), Color(0xFFBFDBFE), Color(0xFFFBCFE8), Color(0xFFBBF7D0),
    Color(0xFFDDD6FE), Color(0xFFFED7AA), Color(0xFFA7F3D0), Color(0xFFC7D2FE)
)

/** أفاتار كيوت ثابت لكل طفل (إيموجي + لون) مبني من هاش الاسم/الـID — بدون الحاجة لصورة */
@Composable
fun KidAvatar(seed: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val idx = kotlin.math.abs(seed.hashCode())
    val emoji = kidAvatarEmojis[idx % kidAvatarEmojis.size]
    val bg = kidAvatarColors[idx % kidAvatarColors.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsBottomSheet(
    viewModel: ZadViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val notifications by viewModel.appNotifications.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val budget by viewModel.budget.collectAsState()

    val totalSpent = transactions.filter { it.isExpense }.sumOf { it.amount }
    val percentage = if (budget > 0) (totalSpent / budget * 100).toInt().coerceIn(0, 100) else 0

    val salaryDetectedTitle = stringResource(R.string.salary_detected_title)
    val salaryDetectedBody = stringResource(R.string.salary_detected_body)
    val budgetExceededTitle = stringResource(R.string.budget_exceeded_title)
    val budgetExceededBodyTemplate = stringResource(R.string.budget_exceeded_body)
    val budgetNearLimitTitleTemplate = stringResource(R.string.budget_near_limit_title)
    val budgetNearLimitBodyTemplate = stringResource(R.string.budget_near_limit_body)
    val subscriptionDetectedTitleTemplate = stringResource(R.string.subscription_detected_title)
    val subscriptionDetectedBodyTemplate = stringResource(R.string.subscription_detected_body)

    val smartNotifications = remember(transactions, budget, totalSpent) {
        val smartList = mutableListOf<SmartNotification>()

        // Check for salary today
        val today = java.time.LocalDate.now().toString()
        val todaySalary = transactions.any { tx ->
            !tx.isExpense && tx.category == "الراتب" && tx.createdAt?.startsWith(today) == true
        }
        if (todaySalary) {
            smartList.add(SmartNotification(salaryDetectedTitle, salaryDetectedBody, Icons.Default.AttachMoney, Color(0xFF4CAF50)))
        }

        // Budget warnings
        if (percentage >= 100) {
            smartList.add(SmartNotification(budgetExceededTitle, String.format(budgetExceededBodyTemplate, percentage), Icons.Default.Warning, Color(0xFFE53935)))
        } else if (percentage >= 85) {
            smartList.add(SmartNotification(String.format(budgetNearLimitTitleTemplate, percentage), String.format(budgetNearLimitBodyTemplate, percentage), Icons.Default.TrendingUp, Color(0xFFFF9800)))
        }

        // Check for subscriptions detected recently
        val recentSubscriptions = transactions.filter { tx ->
            tx.createdAt?.startsWith(today) == true && tx.category == "الاشتراكات"
        }
        recentSubscriptions.forEach { tx ->
            smartList.add(SmartNotification(String.format(subscriptionDetectedTitleTemplate, tx.title), String.format(subscriptionDetectedBodyTemplate, com.example.data.CurrencyFormatter.format(context, tx.amount)), Icons.Default.Subscriptions, Color(0xFF2196F3)))
        }

        smartList
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.notifications_title),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Smart Notifications
            if (smartNotifications.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.smart_notifications_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                smartNotifications.forEach { smartNotif ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = smartNotif.color.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(smartNotif.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(smartNotif.icon, contentDescription = null, tint = smartNotif.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(smartNotif.title, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                                Text(smartNotif.message, style = Typography.bodySmall, color = onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (notifications.isEmpty() && smartNotifications.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = stringResource(R.string.no_notifications_yet),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            } else if (notifications.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.app_notifications_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn {
                    items(notifications.sortedByDescending { it.createdAt }) { notif ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.markNotificationRead(notif.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant else primaryLight.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = notif.title,
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!notif.isRead) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(CircleShape).background(primaryLight)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notif.message,
                                    style = Typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private data class SmartNotification(
    val title: String,
    val message: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@Composable
fun FamilyMiniHub(familyState: FamilyState, onNavigateToFamily: () -> Unit) {
    if (familyState is FamilyState.Active) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.family_hub), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Text(stringResource(R.string.manage_family), style = Typography.labelMedium, color = primary, modifier = Modifier.clickable { onNavigateToFamily() })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.05f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .clickable { onNavigateToFamily() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(catBillsBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FamilyRestroom, contentDescription = "Family", tint = catBillsIcon)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.family_hub), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(stringResource(R.string.family_desc), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = "Enter", tint = primary)
            }
        }
    }
}

@Composable
fun FeaturesRowSection(
    onNavigateToAssistant: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToFamily: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.quick_shortcuts),
            style = Typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            item { FeatureItem(stringResource(R.string.zad_ai), Icons.Default.AutoAwesome, catTransportBg, catTransportIcon, onNavigateToAssistant) }
            item { FeatureItem(stringResource(R.string.family_hub), Icons.Default.FamilyRestroom, catBillsBg, catBillsIcon, onNavigateToFamily) }
            item { FeatureItem(stringResource(R.string.inventory_items), Icons.Default.Inventory, catFoodBg, catFoodIcon, onNavigateToInventory) }
            item { FeatureItem(stringResource(R.string.active_subscriptions), Icons.Default.CalendarToday, catBankingBg, catBankingIcon, onNavigateToSubscriptions) }
            item { FeatureItem(stringResource(R.string.grocery), Icons.Default.ShoppingCart, catDailyBg, catDailyIcon, onNavigateToShopping) }
        }
    }
}

@Composable
fun FeatureItem(title: String, icon: ImageVector, bgColor: Color, iconColor: Color, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(100), label = "feature_scale"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "feature_glow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Card(
            modifier = Modifier
                .size(76.dp)
                .scale(scale)
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }.also { src ->
                        LaunchedEffect(src) {
                            src.interactions.collect { interaction ->
                                when (interaction) {
                                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed = true
                                    is androidx.compose.foundation.interaction.PressInteraction.Release -> pressed = false
                                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed = false
                                    else -> {}
                                }
                            }
                        }
                    }
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(iconColor.copy(alpha = glow * 0.3f), surface.copy(alpha = 0.0f)),
                        radius = 200f
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = Typography.labelMedium,
            color = onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            minLines = 2
        )
    }
}
@Composable
fun GreetingBanner(userName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "greeting")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "wave"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.welcome_back_name_prefix), style = Typography.headlineMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Text(
                text = userName.ifEmpty { stringResource(R.string.guest_name_fallback) },
                style = Typography.headlineMedium.copy(
                    brush = Brush.linearGradient(colors = listOf(primary, primaryFixed))
                ),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.home_summary_subtitle), style = Typography.bodyMedium, color = onSurfaceVariant)
    }
}

@Composable
fun DashboardGrid(
    inventoryCount: Int,
    budgetLeft: Double,
    unreadFamilyMessages: Int,
    onNavigateToInventory: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToFamily: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Inventory Card
        Card(
            modifier = Modifier.weight(1f).aspectRatio(1f).clickable { onNavigateToInventory() }.shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = primary)
                }
                Column {
                    Text(text = "$inventoryCount", style = Typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = primary)
                    Text(text = stringResource(R.string.inventory_items_label), style = Typography.labelMedium, color = outline)
                }
            }
        }

        // Budget Card
        Card(
            modifier = Modifier.weight(1f).aspectRatio(1f).clickable { onNavigateToBudget() }.shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = primary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text(text = com.example.data.CurrencyFormatter.format(context, budgetLeft), style = Typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(text = stringResource(R.string.remaining), style = Typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    // Family Card
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigateToFamily() }.shadow(8.dp, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = secondary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(R.string.nav_family), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    if (unreadFamilyMessages > 0) {
                        Text(text = stringResource(R.string.unread_family_messages_count, unreadFamilyMessages), style = Typography.bodySmall, color = secondary)
                    } else {
                        Text(text = stringResource(R.string.no_new_messages), style = Typography.bodySmall, color = outline)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = outline)
        }
    }
}


