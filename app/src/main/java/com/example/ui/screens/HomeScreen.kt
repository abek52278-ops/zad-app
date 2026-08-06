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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import kotlinx.coroutines.launch
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
    onNavigateToPharmacy: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    /** تفعيل يدوي من الأب/الأم (Switch to Kids Mode) — بيفرض واجهة الأطفال حتى لو role الحساب "admin" */
    kidsModeOverride: Boolean = false
) {
    val inventory by viewModel.inventory.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val mealSuggestions by viewModel.mealSuggestions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val zadInsights by viewModel.zadInsights.collectAsState()
    val zadFacts by viewModel.zadFacts.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val budgetConfirmed by viewModel.budgetConfirmed.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val availableFigure by viewModel.availableFigure.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val nextObligationDue by viewModel.nextObligationDue.collectAsState()
    val daysLeftInCycle by viewModel.daysLeftInCycle.collectAsState()
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
    val livePrices by viewModel.livePrices.collectAsState()
    val marketPricesFetchState by viewModel.marketPricesFetchState.collectAsState()
    val cashOnHand by viewModel.cashOnHand.collectAsState()
    val habitChips by viewModel.habitChips.collectAsState()

    // "مصروف" في كارت الميزانية لازم يكون مصروف نفس الدورة اللي "متاح" اتحسب عليها.
    // كان BudgetMath.totalExpense — إجمالي كل المعاملات من أول يوم في التطبيق — جنب
    // "متاح" المحسوب على الدورة، فالكارت كان بيعرض رقمين مالهمش علاقة ببعض وبيكبر
    // للأبد. spentThisCycle هو نفس الرقم اللي ZadViewModel بيطرحه من الميزانية.
    val totalSpent by viewModel.spentThisCycle.collectAsState()
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
    val scope = rememberCoroutineScope()
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
        viewModel.refreshLiveMarketPrices()
        viewModel.loadZadInsights()
        familyViewModel.loadUpcomingSeasonalEvents()
        Log.d(TAG_HOME, "HomeScreen loaded — userName=$userNameState, budget=$budget, transactions=${transactions.size}, isChild=$isChild")
    }

    LaunchedEffect(upcomingSeasonalEvents) {
        if (upcomingSeasonalEvents.isNotEmpty()) viewModel.loadSeasonalForecast(upcomingSeasonalEvents)
    }

    val userName = userNameState ?: "..."

    var showAllTransactionsDialog by remember { mutableStateOf(false) }
    var showWhySheet by remember { mutableStateOf(false) } // Task 27.2 — طول الضغط على "متاح"
    var showTelegramSheet by remember { mutableStateOf(false) } // بوت تليجرام — اتنقل من البروفايل للرئيسية
    var selectedRecipeTitle by remember { mutableStateOf<String?>(null) }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showTasbihaReminder by remember { mutableStateOf(false) }

    // Use FamilyViewModel's tasbiha data instead of direct SupabaseRepo call
    val myTasbiha = familyViewModel.myTasbiha
    LaunchedEffect(Unit) {
        familyViewModel.loadTasbiha()
    }

    // No local canvas here any more — MainScreen paints ZadCanvasBackground once behind
    // the whole Scaffold so every screen shares the mockup's one gradient.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
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
            com.example.ui.components.LiveMarketTicker(
                prices = livePrices,
                fetchState = marketPricesFetchState,
                onRetry = { viewModel.refreshLiveMarketPrices() }
            )
            if (livePrices.isNotEmpty() || marketPricesFetchState != com.example.ui.viewmodels.ZadViewModel.LiveFetchState.NotFetchedYet) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section order below follows the "ZAD App.dc.html" mockup's Home screen
            // top-to-bottom, verbatim: ticker → hero → days/safe-spend pair → 6-icon
            // shortcut grid → insights → 2×2 stat grid → dark AI summary → tasbiha
            // garden → Chef Zad → Amazon picks → recent transactions. Everything the
            // mockup doesn't have (cash card, shortages, mini inventory/shopping,
            // urgent recipes, events radar, forecast) now sits in one block *after*
            // that sequence instead of being interleaved through it.
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — اقتراح تحويل السوق لو
                // بلد الشبكة الحالي مختلف عن السوق المختار. اقتراح بس، مفيش تبديل صامت —
                // التجاهل بيتذكر لنفس البلد المكتشف بس (SharedPreferences)، فمابيرجعش
                // يزعج في كل فتح للتطبيق لحد ما البلد يتغير تاني فعلاً.
                var dismissedTravelCountry by remember {
                    mutableStateOf(context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
                        .getString("dismissed_travel_country", null))
                }
                val detectedCountry = remember { com.example.data.TravelDetector.detectCurrentCountryCode(context) }
                val suggestedMarket = remember(detectedCountry, dismissedTravelCountry) {
                    if (detectedCountry != null && detectedCountry == dismissedTravelCountry) null
                    else com.example.data.TravelDetector.suggestedMarketFor(context)
                }
                if (suggestedMarket != null) {
                    com.example.ui.components.TravelBanner(
                        suggestedMarket = suggestedMarket,
                        onSwitch = {
                            com.example.data.MarketPrefs.setMarket(context, suggestedMarket)
                            scope.launch { com.example.data.SupabaseRepo.syncMarketProfile(suggestedMarket) }
                        },
                        onDismiss = {
                            dismissedTravelCountry = detectedCountry
                            context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("dismissed_travel_country", detectedCountry).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!isNotificationAccessGranted) {
                    NotificationPermissionCard {
                        Log.d(TAG_HOME, "NotificationPermissionCard button clicked")
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── 1. Hero (mockup: the 28dp mesh-gradient "متاح" card) ──
                // Task 26 — daysLeft بقى بحدود دورة الراتب (ZadViewModel.daysLeftInCycle)
                // مش الشهر التقويمي كان مؤجل من Task 25.
                val daysLeft = daysLeftInCycle
                val nextObligationText = nextObligationDue?.let { (ob, due) ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due).toInt()
                    stringResource(R.string.obligation_due_in_days, ob.title, days)
                }

                // Task 0ب — remaining/availableFigure بقوا nullable (null = السقف لسه مش
                // معروف). budgetConfirmed لوحدها كانت كفاية زمان لما remaining كان بيرجع
                // 0.0 صامت؛ دلوقتي الفلاتين لازم يتفقوا سوا قبل ما نعرض رقم حقيقي —
                // budgetConfirmed=true لسه بيلحق فراغ لحظي (recalculate جوّه coroutine)
                // من غير الشرط الإضافي ده.
                val availableFigureValue = availableFigure
                com.example.ui.components.AppearOnEntry {
                    if (budgetConfirmed && availableFigureValue != null && currentBudget != null) {
                        com.example.ui.components.ZadCardHero(
                            spent = totalSpent,
                            remaining = currentBudget,
                            available = availableFigureValue,
                            committed = committed,
                            nextObligationText = nextObligationText,
                            onAvailableLongPress = { showWhySheet = true }
                        )
                    } else {
                        // Task 19.0 معيار قبول ٦ — سقف مش مؤكد، نسأل بدل ما نعرض رقم
                        com.example.ui.components.BudgetSetupPromptCard(
                            onSetBudget = { viewModel.showBudgetDialog() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── 2. Days left / daily safe spend pair (mockup: two 18dp white cards) ──
                if (budgetConfirmed && availableFigureValue != null) {
                    com.example.ui.components.AppearOnEntry(delayMs = 60) {
                        com.example.ui.components.ZadDaysAndSafeSpendRow(
                            daysLeft = daysLeft,
                            available = availableFigureValue.value
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 3. Shortcut grid — 6 fixed columns, exactly the mockup's six
                // destinations. Was a 9-item horizontally-scrolling LazyRow, which is why
                // the row read as arbitrary: half of it was off-screen.
                com.example.ui.components.AppearOnEntry(delayMs = 80) {
                    com.example.ui.components.ZadPageShortcutsGrid(
                        items = listOf(
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Inventory2, stringResource(R.string.nav_inventory), primary, onNavigateToInventory),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.ShoppingCart, stringResource(R.string.nav_shopping), catDailyIcon, onNavigateToShopping),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.FamilyRestroom, stringResource(R.string.nav_family), kidsPrimary, onNavigateToFamily),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Subscriptions, stringResource(R.string.quick_stat_subscriptions_title), tertiary, onNavigateToSubscriptions),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.LocalPharmacy, stringResource(R.string.nav_pharmacy), dangerColor, onNavigateToPharmacy),
                            com.example.ui.components.ZadShortcutItem(Icons.Default.Park, stringResource(R.string.tasbiha_short_label), secondaryDark, onNavigateToTasbiha)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 3b. Telegram bot — كان جوه البروفايل، والناس ما بتوصلش له. الرئيسية
                // هي المكان اللي بيتشاف. زرار بيفتح شيت عشان الربط نفسه بيعمل نداء شبكة.
                com.example.ui.components.AppearOnEntry(delayMs = 90) {
                    com.example.ui.components.TelegramBotCard(onClick = { showTelegramSheet = true })
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 4. Insights (mockup: translucent glass rows, dot + text + tag) ──
                // أهم تنبيهات عقل زاد — zad_insights كان مكتوب من زاد-برين وميتقراش
                // خالص، فالتحليل والتنبيهات ما كانتش توصل هنا. دي أول محطة ليها.
                val homeInsights = zadInsights
                    .filter { it.surface == "home_card" }
                    .sortedByDescending { it.priority == "critical" }
                    .take(3)
                if (homeInsights.isNotEmpty()) {
                    Text(
                        stringResource(R.string.zad_smart_insight_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        homeInsights.forEach { insight ->
                            if (insight.kind == "question") {
                                com.example.ui.widgets.ZadQuestionCard(
                                    insight = insight,
                                    onAnswer = { answer -> viewModel.answerBrainQuestion(insight, answer) },
                                    onDismiss = { viewModel.dismissInsight(insight.id) },
                                    onOpenCamera = onNavigateToCamera
                                )
                                return@forEach
                            }
                            // mockup: translucent white glass row, 16dp radius, a colored
                            // priority dot (not an icon), title + body, and a pill tag on
                            // the trailing edge.
                            val isCritical = insight.priority == "critical"
                            val accent = if (isCritical) dangerColor else primary
                            com.example.ui.components.GlassCard(
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color.White.copy(alpha = 0.85f),
                                contentPadding = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(insight.title, style = Typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                                        Text(insight.body, style = Typography.bodyMedium, color = onSurfaceVariant, maxLines = 2)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // mockup: a kind tag pill on the trailing edge, tinted to
                                    // match the dot. The mockup has three (معلومة/مطلوب/تحذير)
                                    // because its data is hardcoded; ZadInsight.priority only
                                    // has normal|critical, so inventing a third would mean
                                    // inventing a state the brain never emits.
                                    Text(
                                        stringResource(
                                            if (isCritical) R.string.insight_tag_alert else R.string.insight_tag_info
                                        ),
                                        style = Typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Task 28 — "رفض بمعنى": بدل رفض صامت، ٣ خيارات بسبب فعلي
                                    var showDismissMenu by remember(insight.id) { mutableStateOf(false) }
                                    IconButton(onClick = { showDismissMenu = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
                                    }
                                    com.example.ui.components.DismissReasonMenu(
                                        expanded = showDismissMenu,
                                        onDismissRequest = { showDismissMenu = false },
                                        onReasonSelected = { reason -> viewModel.dismissInsightWithReason(insight, reason) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 5. Stat grid — the mockup's 2×2 of plain white label/value tiles.
                // Task 9 wired these ZadFacts numbers onto Home (they used to exist only
                // inside ZadIntelligenceScreen's report). Trimmed from six colored
                // icon-chip cards to the mockup's four: spending power, health score,
                // monthly spend, 7-day trend. Stress-test days and top-category still
                // live on the Zad Intelligence screen, which is where the mockup puts them.
                zadFacts?.let { facts ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // المockup بيحط نسبة مئوية هنا ("82%")، والحالة النصية
                            // ("قوي 💪") بتفضل على شاشة عقل زاد جنب العداد نفسه.
                            // powerPct = null يعني مفيش سقف ميزانية، فمفيش نسبة تتعرض.
                            com.example.ui.components.ZadStatTile(
                                modifier = Modifier.weight(1f).clickable { onNavigateToAssistant() },
                                label = stringResource(R.string.spending_power),
                                value = facts.report.spendingPower.powerPct?.let { "$it%" } ?: "—"
                            )
                            com.example.ui.components.ZadStatTile(
                                modifier = Modifier.weight(1f).clickable { onNavigateToAssistant() },
                                label = "الصحة المالية",
                                value = if (facts.report.hasEnoughData) "${facts.report.healthScore}/100" else "—"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.example.ui.components.ZadStatTile(
                                modifier = Modifier.weight(1f).clickable { onNavigateToAssistant() },
                                label = "الإنفاق الشهري",
                                value = com.example.data.CurrencyFormatter.format(context, facts.report.totalSpent)
                            )
                            // "اتجاه ٧ أيام" في الmockup نسبة تغيّر (↓ 6%)، مش مبلغ.
                            // اللي كان هنا مجموع آخر ٧ أيام بالريال تحت عنوان "اتجاه" —
                            // رقم صح باسم غلط، وما بيقولش اتجاه إيه مقارنة بإيه.
                            // dailyTrend فيها ١٤ يوم، فالأسبوع اللي فات هو خط الأساس.
                            val trend = facts.report.dailyTrend
                            val last7 = trend.takeLast(7).sumOf { it.amount }
                            val prev7 = trend.dropLast(7).takeLast(7).sumOf { it.amount }
                            val deltaPct = if (prev7 > 0.0) ((last7 - prev7) / prev7 * 100).toInt() else null
                            com.example.ui.components.ZadStatTile(
                                modifier = Modifier.weight(1f).clickable { onNavigateToAssistant() },
                                label = stringResource(R.string.seven_day_trend_label),
                                value = when {
                                    deltaPct == null -> "—"
                                    deltaPct > 0 -> "↑ $deltaPct%"
                                    deltaPct < 0 -> "↓ ${-deltaPct}%"
                                    else -> "0%"
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 6. Dark AI summary card (mockup: #052E16, mint title, chips) ──
                agentSummary?.let { summary ->
                    AgentSummaryCard(
                        agentSummary = summary,
                        isLoading = isAgentLoading,
                        onRefresh = { viewModel.refreshAgentSummary() },
                        onNavigateToAssistant = onNavigateToAssistant,
                        onNavigateToShopping = onNavigateToShopping,
                        onNavigateToInventory = onNavigateToInventory
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 7. Tasbiha garden ──
                TasbihaHomeWidget(
                    tree = myTasbiha,
                    onTasbih = { familyViewModel.tasbihaClick() },
                    onNavigateToTasbiha = onNavigateToTasbiha
                )
                Spacer(modifier = Modifier.height(18.dp))

                // ── 8. Chef Zad (mockup: one 18dp white row card — 56dp amber tile,
                // title, one-line suggestion — not a carousel of stock food photos) ──
                SmartChefSection(
                    suggestions = mealSuggestions,
                    onViewAll = onNavigateToAssistant,
                    onOpenRecipe = { title ->
                        selectedRecipeTitle = title
                        showRecipeDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(18.dp))

                // ── 9. Amazon picks (mockup: 140dp fixed-width cards in a horizontal
                // rail). AffiliateProductCard is `fillMaxWidth()` + its own 16dp margins,
                // so putting it inside a LazyRow gave every card the full viewport width —
                // that's the "overlapping cards" in the design review. ZadAmazonDealCard
                // is the mockup's actual rail card and has a fixed width.
                val activeAffiliateProducts = affiliateProducts.filter { it.isActive }
                if (activeAffiliateProducts.isNotEmpty()) {
                    Text(stringResource(R.string.shop_from_amazon), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(activeAffiliateProducts.take(8)) { product ->
                            com.example.ui.widgets.ZadAmazonDealCard(
                                product = product,
                                onClick = {
                                    viewModel.recordAffiliateClick(product.id, "home")
                                    com.example.data.AffiliateHelper.openProduct(context, product)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 10. Recent transactions ──
                PremiumTransactionsRow(
                    transactions = transactions,
                    onSeeAllClick = { showAllTransactionsDialog = true }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // ── Beyond the mockup ──────────────────────────────────────────────
                // Cards Zad has and the mockup doesn't. They stay (each one is backed by
                // real data the app computes), but they now sit below the mockup sequence
                // as one block instead of being scattered between its sections.

                // Task 19.4 — كارت الكاش. بيظهر بس لو فيه كاش فعلاً (cashOnHand > 0،
                // مشتق من BudgetMath.cashOnHand)، ويختفي لوحده لما يوصل صفر — الكارت
                // نفسه هو التذكير، مفيش إشعار منفصل غرضه بس "افتكر تسجل الكاش".
                if (cashOnHand > 0.0) {
                    com.example.ui.components.CashCard(
                        cashOnHand = cashOnHand,
                        habitChips = habitChips,
                        onChipTap = { chip ->
                            viewModel.addTransaction(
                                ZadTransaction(
                                    amount = chip.amount,
                                    title = chip.label,
                                    category = chip.category,
                                    isExpense = true,
                                    wallet = "cash",
                                    createdAt = java.time.Instant.now().toString()
                                )
                            )
                        },
                        onSpentFromCash = { amount, title, category ->
                            viewModel.addTransaction(
                                ZadTransaction(
                                    amount = amount,
                                    title = title,
                                    category = category,
                                    isExpense = true,
                                    wallet = "cash",
                                    createdAt = java.time.Instant.now().toString()
                                )
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (shortageCount > 0) {
                    com.example.ui.components.ShortagesSummaryCard(
                        shortageCount = shortageCount,
                        onViewShortagesClick = {
                            InventoryNavState.openShortagesTab = true
                            onNavigateToInventory()
                        }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                val alerts = insights.filter { it.type == "Alert" }
                if (alerts.isNotEmpty()) {
                    AiAlertBanner(title = alerts.first().title, description = alerts.first().description)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (autoSuggestions.isNotEmpty()) {
                    AutoSuggestionsCard(suggestions = autoSuggestions)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // رادار المناسبات (family-only: needs cross-member transaction history)
                if (familyState is FamilyState.Active && seasonalForecasts.isNotEmpty()) {
                    EventsRadarCard(forecasts = seasonalForecasts)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // PredictionCard بيقارن توقّع الشهر الجاي بالسقف نفسه (budget)، مش بمتبقي
                // الدورة — سقف <= 0 يبقى "غير معروف" أصلاً فمفيش كارت يتعرض من غير معنى.
                if (expensePrediction != null && budget > 0) {
                    PredictionCard(expensePrediction!!, budget)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                MiniInventoryWidget(inventory = inventory, onNavigateToInventory = onNavigateToInventory)
                Spacer(modifier = Modifier.height(18.dp))

                MiniShoppingWidget(shoppingList = shoppingList, onNavigateToShopping = onNavigateToShopping)
                Spacer(modifier = Modifier.height(18.dp))

                // العقل → الوصفات: "عندك دجاج هينتهي بكرة → 3 وصفات بيه"
                urgentRecipes?.let { urgent ->
                    UrgentRecipeCard(
                        triggerItems = urgent.triggerItems,
                        text = urgent.text,
                        // Task 23 — لو كل الأصناف المحفّزة راكدة (مفيش صنف هيخلص/ينتهي فعلاً)،
                        // العنوان يتغيّر — "هيخلص قريب" غلط لصنف حد ناسيه من شهر مش هيتلف بكرة.
                        isStagnantOnly = urgent.stagnantItems.isNotEmpty() && urgent.stagnantItems.size == urgent.triggerItems.size,
                        onOpenChat = onNavigateToAssistant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
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
    if (showTelegramSheet) {
        com.example.ui.components.TelegramBotSheet(onDismiss = { showTelegramSheet = false })
    }

    if (showWhySheet) {
        com.example.ui.components.WhyChangedSheet(onDismiss = { showWhySheet = false })
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
fun BudgetEditDialog(currentBudget: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var budgetStr by remember { mutableStateOf(currentBudget.toInt().toString()) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_monthly_budget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.budget_save_hint), style = Typography.labelSmall, color = onSurfaceVariant)
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
    isStagnantOnly: Boolean = false,
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
                        stringResource(
                            if (isStagnantOnly) R.string.items_stagnant_hint else R.string.items_expiring_soon_hint,
                            triggerItems.take(2).joinToString("، ")
                        ),
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412)
                    )
                    Text(
                        stringResource(if (isStagnantOnly) R.string.suggested_recipes_use_it_up else R.string.suggested_recipes_before_expiry),
                        style = Typography.labelSmall,
                        color = Color(0xFFC2410C)
                    )
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

/**
 * Chef Zad — one card, per the mockup. The old version rendered a rail of three
 * meal cards; when the AI hadn't answered it fell back to three hardcoded meals with
 * hardcoded Unsplash photos, which is exactly the kind of fake content the design
 * review flagged. Now: real suggestion or an honest empty line, never a placeholder
 * meal presented as a recommendation.
 */
@Composable
fun SmartChefSection(
    suggestions: String,
    onViewAll: () -> Unit,
    onOpenRecipe: (String) -> Unit
) {
    // Was `isNotBlank() && startsWith("1.") || startsWith("-") || startsWith("•")` —
    // && binds tighter than ||, so isNotBlank() only guarded the "1." branch, and the
    // real model output is plain prose ("يمكنك تحضير وجبة دجاج..."), never numbered/
    // bulleted. Net effect: isRealAi was false for every real AI response.
    val isRealAi = suggestions.isNotBlank() &&
        suggestions != com.example.data.ZadAiRepository.MEAL_SUGGESTIONS_FALLBACK

    val dish = if (isRealAi) {
        suggestions.split("\n").firstOrNull { it.isNotBlank() }?.trim()
            ?.replace(Regex("^[\\d\\-•·.]+\\s*"), "")
    } else null

    com.example.ui.components.ZadChefCard(
        suggestion = dish,
        // الكارت بيوعد بوصفة، فيفتح الوصفة. كان بيروح لعقل زاد، و RecipeDetailDialog
        // (بكل الـ parsing وقائمة المقادير وخطوات التحضير) ما كانش ليه أي مدخل —
        // showRecipeDialog اتعرّف واتقرا وعمره ما اتعمل true.
        onClick = { if (dish != null) onOpenRecipe(dish) else onViewAll() }
    )
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
    // The mockup's `aiSummaryTitle` card: #052E16 at 20dp radius, a mint title in
    // 12.5sp, the summary at 14sp/1.55, and the actions as translucent chips. The
    // 36dp robot-avatar circle and the 16sp white heading that used to sit on top
    // are gone — the mockup gives this card one small label, then the sentence.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(primaryContainer)
            .padding(18.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.zad_agent),
                    color = primaryFixed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_cd), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isLoading) {
                com.example.ui.components.ZadLoadingState(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    size = 24.dp,
                    strokeWidth = 2.dp
                )
            } else if (agentSummary != null) {
                Text(agentSummary.summary, color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)

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
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(suggestionColor.copy(alpha = 0.18f)).clickable {
                                    when (suggestion.action) {
                                        "add_to_shopping" -> onNavigateToShopping()
                                        "cook_meal" -> onNavigateToAssistant()
                                        else -> onNavigateToInventory()
                                    }
                                }.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text(suggestion.reason, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
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
    com.example.ui.components.ZadListCard(containerColor = catFoodBg, contentPadding = 0.dp) {
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
    com.example.ui.components.ZadListCard(containerColor = errorContainer, contentPadding = 0.dp) {
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

data class MiniTableRow(val name: String, val detail: String, val color: Color)


@Composable
fun MiniTableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    itemsCount: Int,
    items: List<MiniTableRow>,
    onSeeAll: () -> Unit
) {
    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
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

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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
    // هدف الادخار اتحقق (الرصيد وصل أو عدى الهدف) — نشغّل confetti مرة واحدة لحظة الوصول
    val savingsGoalReached = mySavingsGoal > 0 && myAllowance >= mySavingsGoal

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
            // هدف الادخار اتحقق — نفجّر confetti فوق الكارت مرة واحدة (iterations = 1)
            androidx.compose.animation.AnimatedVisibility(
                visible = savingsGoalReached,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZadLottieAsset(
                    resId = R.raw.lottie_confetti_burst,
                    iterations = 1,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = stringResource(R.string.savings_goal_colon, com.example.data.CurrencyFormatter.format(context, mySavingsGoal))
                )
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

