package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.viewmodels.AiChatMessage
import com.example.data.ZadTransaction
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.AiInsight
import com.example.data.ZadAiRepository
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ════════════════════════════════════════════════════════════════

@Composable
fun ZadIntelligenceScreen(
    viewModel: ZadViewModel,
    familyViewModel: com.example.ui.viewmodels.FamilyViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val messages by viewModel.aiChatMessages.collectAsState()
    val isTyping by viewModel.isAiTyping.collectAsState()
    val prediction by viewModel.expensePrediction.collectAsState()
    val patterns by viewModel.behaviorPatterns.collectAsState()
    val serverBehaviorProfile by viewModel.behaviorProfile.collectAsState()
    val isRefreshingBehaviorProfile by viewModel.isRefreshingBehaviorProfile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // تشغيل تحليل شامل عند فتح الشاشة
    LaunchedEffect(Unit) {
        viewModel.refreshAgentSummary()
        viewModel.predictNextMonthExpenses()
        viewModel.detectSubscriptions()
        viewModel.generateBrainReport()
        viewModel.loadBehaviorProfile()
        viewModel.refreshBehaviorProfile()
    }

    val brainReport by viewModel.brainReport.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        IntelligenceTopBar(onOpenDrawer)

        val tabs = listOf(
            Icons.Default.Psychology to stringResource(R.string.analytics_tab),
            Icons.Default.BarChart to stringResource(R.string.subscriptions_title),
            Icons.Default.Chat to stringResource(R.string.zad_chat_tab)
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = background,
            contentColor = primary,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                    height = 3.dp,
                    color = primary
                )
            }
        ) {
            tabs.forEachIndexed { i, (icon, title) ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == i) primary else onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                title,
                                style = Typography.labelLarge,
                                color = if (selectedTab == i) primary else onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "tabs"
        ) { tab ->
            when (tab) {
                0 -> AnalyticsTab(
                    transactions = transactions,
                    inventory = inventory,
                    subscriptions = subscriptions,
                    insights = insights,
                    prediction = prediction,
                    patterns = patterns,
                    report = brainReport,
                    serverBehaviorProfile = serverBehaviorProfile,
                    isRefreshingBehaviorProfile = isRefreshingBehaviorProfile,
                    onRefreshBehaviorProfile = { viewModel.refreshBehaviorProfile() },
                    viewModel = viewModel,
                    familyViewModel = familyViewModel
                )
                1 -> SubscriptionsTab(
                    subscriptions = subscriptions,
                    viewModel = viewModel
                )
                else -> ChatTab(
                    messages = messages,
                    isTyping = isTyping,
                    inputText = inputText,
                    listState = listState,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendAiChatMessage(inputText)
                            inputText = ""
                        }
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  TAB 1: ANALYTICS
// ════════════════════════════════════════════════════════════════

@Composable
fun AnalyticsTab(
    transactions: List<ZadTransaction>,
    inventory: List<ZadInventory>,
    subscriptions: List<ZadSubscription>,
    insights: List<AiInsight>,
    prediction: com.example.data.AiExpensePrediction?,
    patterns: List<com.example.data.ZadBehaviorPattern>,
    report: com.example.data.ZadCentralBrain.BrainReport? = null,
    serverBehaviorProfile: com.example.data.UserBehaviorProfile? = null,
    isRefreshingBehaviorProfile: Boolean = false,
    onRefreshBehaviorProfile: () -> Unit = {},
    viewModel: ZadViewModel,
    familyViewModel: com.example.ui.viewmodels.FamilyViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val otherCategoryLabel = stringResource(R.string.other_category)
    val emergencyFund by viewModel.emergencyFund.collectAsState()
    val expenses = transactions.filter { it.isExpense }
    val income = transactions.filter { !it.isExpense }
    val totalExpense = expenses.sumOf { it.amount }
    val totalIncome = income.sumOf { it.amount }

    val categoryMap = expenses
        .groupBy { it.category ?: otherCategoryLabel }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    val monthlyData = computeMonthlyData(transactions, context)
    val predictedNextMonth = predictNextMonth(monthlyData)
    val lowStockCount = inventory.count { it.quantity <= (it.lowStockThreshold ?: 2) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══ تقرير العقل: نقاط الصحة المالية ═══
        if (report != null) {
            item { SpendingPowerGaugeCard(report.spendingPower) }
            item { HealthScoreCard(report) }
            item { FinancialStressTestCard(transactions, emergencyFund, onUpdateEmergencyFund = { viewModel.updateEmergencyFund(it) }) }
            report.monthComparison?.let { mc ->
                item { MonthComparisonCard(mc) }
            }
            report.behaviorProfile?.let { bp ->
                item { BehaviorAnalysisCard(bp) }
            }
            if (report.insights.isNotEmpty()) {
                item { BrainInsightsCard(report.insights) }
            }
            if (report.depletionForecasts.isNotEmpty()) {
                item { DepletionForecastCard(report.depletionForecasts) }
            }
            item { ExportReportButton(report) }
        }

        // بروفايل السلوك المحسوب على الخادم (user_behavior_profile)
        item {
            ServerBehaviorProfileCard(
                profile = serverBehaviorProfile,
                isRefreshing = isRefreshingBehaviorProfile,
                onRefresh = onRefreshBehaviorProfile
            )
        }

        // ملاحظة سلوكية: يوم الإنفاق الأعلى مقارنة ببقية الأيام (Feature 4)
        if (detectWeekdaySpike(serverBehaviorProfile) != null) {
            item { BehavioralNudgeCard(serverBehaviorProfile) }
        }

        // إحصائيات سريعة
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.expense_label),
                    value = com.example.data.CurrencyFormatter.format(context, totalExpense),
                    icon = Icons.Default.TrendingDown,
                    iconColor = Color(0xFFEF4444),
                    bgColor = Color(0xFFFFF5F5)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.income_label),
                    value = com.example.data.CurrencyFormatter.format(context, totalIncome),
                    icon = Icons.Default.TrendingUp,
                    iconColor = Color(0xFF22C55E),
                    bgColor = Color(0xFFF0FDF4)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.nav_inventory),
                    value = stringResource(R.string.inventory_items_count_pill, inventory.size),
                    icon = Icons.Default.Inventory2,
                    iconColor = Color(0xFF8B5CF6),
                    bgColor = Color(0xFFF5F3FF)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.active_subscriptions_label),
                    value = "${subscriptions.count { it.isActive }}",
                    icon = Icons.Default.Subscriptions,
                    iconColor = Color(0xFF0891B2),
                    bgColor = Color(0xFFECFEFF)
                )
            }
        }

        // توزيع المصروفات
        item { ExpenseDonutCard(categoryMap = categoryMap, total = totalExpense) }

        // رادار التضخم الشخصي (Feature 3)
        item { InflationRadarCard(transactions) }

        // رادار تغيرات الأسعار في السوق — بحث حي حقيقي (Price Shock Predictor)
        item { PriceShockRadarCard(categoryMap.map { it.first }.take(5), viewModel) }

        // الرسم البياني الشهري
        item {
            MonthlyBarChartCard(
                monthlyData = monthlyData,
                predictedNextMonth = predictedNextMonth
            )
        }

        // بطاقة التنبؤ الذكي
        item {
            PredictionCard(
                predictedAmount = predictedNextMonth,
                currentMonthAmount = monthlyData.lastOrNull()?.second ?: 0.0,
                lowStockCount = lowStockCount,
                subscriptionsCount = subscriptions.count { it.isActive }
            )
        }

        // توقيت الشراء الذكي للمخزون (Feature 6)
        item { SmartBuyingTimingCard(inventory, serverBehaviorProfile) }

        // العروض المتاحة لنواقصك — بحث حي حقيقي (Deal Matcher)
        item {
            LiveDealsCard(
                shortageItems = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }.map { it.itemName },
                viewModel = viewModel
            )
        }

        // تحديات العائلة المالية (Feature 5)
        item { FinancialChallengesCard(familyViewModel) }

        // صناديق التجميع للمناسبات الموسمية (Seasonal & Event Budget Forecasting)
        item { SinkingFundsCard(familyViewModel) }

        // محاكي القرارات المالية (What-If)
        item {
            WhatIfSimulatorCard(viewModel = viewModel, predictedMonthlySpend = predictedNextMonth)
        }

        // الأنماط السلوكية
        if (patterns.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.behavior_patterns_detected_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                }
            }
            items(patterns.take(5)) { pattern ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(catBillsBg),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = catBillsIcon, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pattern.category, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(stringResource(R.string.avg_every_days, com.example.data.CurrencyFormatter.format(context, pattern.avgAmount), pattern.frequencyDays), style = Typography.bodySmall, color = onSurfaceVariant)
                    }
                }
            }
        }

        // رؤى زاد الذكية
        if (insights.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.zad_smart_insights_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                }
            }
            items(insights.take(5)) { insight -> IntelligenceInsightCard(insight, viewModel) }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun IntelligenceInsightCard(insight: AiInsight, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val (bgColor, iconColor, icon) = when (insight.type) {
        "Alert" -> Triple(Color(0xFFFFF5F5), Color(0xFFEF4444), Icons.Default.Warning)
        "Tip" -> Triple(Color(0xFFF0FDF4), Color(0xFF16A34A), Icons.Default.Lightbulb)
        "Warning" -> Triple(Color(0xFFFFFBEB), Color(0xFFF59E0B), Icons.Default.WarningAmber)
        else -> Triple(primaryContainer, primary, Icons.Default.Info)
    }
    var actionDone by remember(insight.actionRefId, insight.actionType) { mutableStateOf(false) }
    var showCancelConfirm by remember(insight.actionRefId, insight.actionType) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(insight.title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(insight.description, style = Typography.bodySmall, color = onSurfaceVariant)
            }
        }

        if (insight.actionType != null && insight.actionRefId != null) {
            Spacer(modifier = Modifier.height(10.dp))
            if (actionDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.insight_action_done), style = Typography.labelSmall, color = iconColor, fontWeight = FontWeight.Bold)
                }
            } else {
                when (insight.actionType) {
                    "cancel_subscription" -> {
                        Button(
                            onClick = { showCancelConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                stringResource(R.string.cancel_subscription_action, com.example.data.CurrencyFormatter.format(context, insight.actionAmount ?: 0.0)),
                                style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                        if (showCancelConfirm) {
                            AlertDialog(
                                onDismissRequest = { showCancelConfirm = false },
                                title = { Text(stringResource(R.string.confirm_cancel_subscription_title)) },
                                text = { Text(stringResource(R.string.confirm_cancel_subscription_body)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.updateSubscriptionActive(insight.actionRefId, false)
                                        actionDone = true
                                        showCancelConfirm = false
                                    }) { Text(stringResource(R.string.confirm_cancel_subscription_action), color = dangerColor, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCancelConfirm = false }) { Text(stringResource(R.string.keep_subscription_action)) }
                                }
                            )
                        }
                    }
                    "increase_budget" -> {
                        Button(
                            onClick = {
                                com.example.data.BudgetTracker.setCategoryBudget(context, insight.actionRefId, insight.actionAmount ?: 0.0)
                                viewModel.refreshBudgetInsights()
                                actionDone = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                stringResource(R.string.increase_budget_action, com.example.data.CurrencyFormatter.format(context, insight.actionAmount ?: 0.0)),
                                style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Server Behavior Profile Card (user_behavior_profile) ──────────────────────
@Composable
fun ServerBehaviorProfileCard(
    profile: com.example.data.UserBehaviorProfile?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = if (isRefreshing) infiniteRepeatable(tween(900, easing = LinearEasing)) else tween(0),
        label = "refreshSpin"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.behavior_insights_title), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_action),
                        tint = primary,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            if (profile == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.no_behavior_data_yet), style = Typography.bodySmall, color = onSurfaceVariant)
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.avg_weekly_spending_label), style = Typography.labelSmall, color = onSurfaceVariant)
                        Text(com.example.data.CurrencyFormatter.format(context, profile.avgWeeklySpending), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.subscription_load_label), style = Typography.labelSmall, color = onSurfaceVariant)
                        Text(com.example.data.CurrencyFormatter.format(context, profile.subscriptionLoadMonthly), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                }

                if (profile.topSpendingCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(stringResource(R.string.top_categories_label), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.topSpendingCategories.take(3).forEach { cat ->
                            Surface(shape = RoundedCornerShape(10.dp), color = primaryContainer) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(cat.category, style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                                    Text(com.example.data.CurrencyFormatter.format(context, cat.total), style = Typography.labelSmall, color = primary)
                                }
                            }
                        }
                    }
                }

                if (!profile.lastUpdatedAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.last_updated_label, profile.lastUpdatedAt.take(10)),
                        style = Typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  TAB 2: SUBSCRIPTIONS
// ════════════════════════════════════════════════════════════════

@Composable
fun SubscriptionsTab(
    subscriptions: List<ZadSubscription>,
    viewModel: ZadViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val debts by viewModel.debts.collectAsState()

    val filteredSubs = if (showInactive) subscriptions else subscriptions.filter { it.isActive }
    val totalMonthly = filteredSubs.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(primary).padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.total_monthly_subscriptions), style = Typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(com.example.data.CurrencyFormatter.format(context, totalMonthly), style = Typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // مخطط سداد الديون (Feature 2) — نفس تبويب الاشتراكات، مجموعتين "التزامات شهرية متكررة"
        item { DebtPayoffPlannerCard(debts, viewModel) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.subscriptions_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Row {
                    FilterChip(
                        selected = !showInactive,
                        onClick = { showInactive = false },
                        label = { Text(stringResource(R.string.active_filter)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = showInactive,
                        onClick = { showInactive = true },
                        label = { Text(stringResource(R.string.filter_all)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer)
                    )
                }
            }
        }

        if (filteredSubs.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Subscriptions, contentDescription = null, tint = onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(if (showInactive) R.string.no_subscriptions_any else R.string.no_subscriptions_active), style = Typography.titleMedium, color = onSurfaceVariant)
                }
            }
        } else {
            items(filteredSubs, key = { it.id }) { sub ->
                ZadIntSubscriptionCardFull(
                    sub = sub,
                    onToggleActive = { viewModel.updateSubscriptionActive(sub.id, !sub.isActive) },
                    onDelete = { viewModel.deleteSubscription(sub.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // زر الإضافة
    Box(modifier = Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 88.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_action))
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, amount, renewalDate, provider ->
                viewModel.addSubscription(
                    com.example.data.ZadSubscription(
                        title = title,
                        amount = amount,
                        renewalDate = renewalDate,
                        provider = provider
                    )
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ZadIntSubscriptionCardFull(
    sub: com.example.data.ZadSubscription,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = java.time.LocalDate.now()
    val renewal = try {
        if (!sub.renewalDate.isNullOrBlank()) java.time.LocalDate.parse(sub.renewalDate.take(10)) else null
    } catch (e: Exception) { null }
    val daysLeft = renewal?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() }
    val daysColor = when {
        daysLeft == null -> onSurfaceVariant
        daysLeft <= 3 -> dangerColor
        daysLeft <= 7 -> warningColor
        else -> successColor
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (sub.isActive) surface else surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(catBillsBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = catBillsIcon)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = if (sub.isActive) onSurface else onSurfaceVariant)
                    if (!sub.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(outlineVariant).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.cancelled_badge), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                if (daysLeft != null) {
                    Text(
                        when {
                            daysLeft == 0 -> stringResource(R.string.renews_today)
                            daysLeft < 0 -> stringResource(R.string.expired_since_days, -daysLeft)
                            else -> stringResource(R.string.renews_in_days, daysLeft)
                        },
                        style = Typography.labelSmall, color = daysColor, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(com.example.data.CurrencyFormatter.format(context, sub.amount), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = if (sub.isActive) onSurface else onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onToggleActive, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (sub.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (sub.isActive) stringResource(R.string.pause_action) else stringResource(R.string.activate_action),
                    tint = if (sub.isActive) warningColor else successColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = dangerColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Donut Chart Card ─────────────────────────────────────────────────────────
@Composable
fun ExpenseDonutCard(categoryMap: List<Pair<String, Double>>, total: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = listOf(
        Color(0xFF16A34A), Color(0xFFF59E0B), Color(0xFF3B82F6),
        Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF06B6D4)
    )
    // اختيار فئة (بالضغط على القوس نفسه أو على سطرها في القايمة) يبدّل مركز
    // الدونات من "الإجمالي" لتفاصيل الفئة دي — عرض أمرن بدل رقم إجمالي ثابت.
    var selectedIndex by remember(categoryMap) { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.expense_distribution), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (categoryMap.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_transactions), color = onSurfaceVariant)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                        ZadDonutChart(
                            segments = categoryMap.map { it.second.toFloat() },
                            colors = colors.take(categoryMap.size),
                            selectedIndex = selectedIndex,
                            onSegmentTap = { i -> selectedIndex = if (selectedIndex == i) null else i },
                            modifier = Modifier.size(160.dp)
                        )
                        val selected = selectedIndex?.let { categoryMap.getOrNull(it) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (selected != null) {
                                val (cat, amount) = selected
                                val pct = if (total > 0) (amount / total * 100).toInt() else 0
                                Text(cat, style = Typography.labelSmall, color = onSurfaceVariant, maxLines = 1)
                                Text(com.example.data.CurrencyFormatter.formatNumber(context, amount), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                                Text("$pct%", style = Typography.labelSmall, color = onSurfaceVariant)
                            } else {
                                Text(stringResource(R.string.total_label), style = Typography.labelSmall, color = onSurfaceVariant)
                                Text(com.example.data.CurrencyFormatter.formatNumber(context, total), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                                Text(com.example.data.CurrencyFormatter.symbol(context), style = Typography.labelSmall, color = onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryMap.take(5).forEachIndexed { i, (cat, amount) ->
                            val pct = if (total > 0) (amount / total * 100).toInt() else 0
                            val isSelected = selectedIndex == i
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedIndex = if (selectedIndex == i) null else i }
                                    .background(if (isSelected) (colors.getOrElse(i) { primary }).copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.getOrElse(i) { primary }))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(cat, style = Typography.labelSmall, color = onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text("$pct%", style = Typography.labelSmall, color = onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZadDonutChart(
    segments: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSegmentTap: (Int) -> Unit = {}
) {
    val total = segments.sum()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donut"
    )
    // حدود كل قطاع بالدرجات — محسوبة مرة كل ما تتغير المعطيات، ومستخدمة لكل من
    // الرسم واختبار موضع الضغطة (نفس المنطق، مرة واحدة).
    val boundaries = remember(segments) {
        var angle = -90f
        segments.map { value ->
            val sweep = if (total > 0) (value / total) * 360f else 0f
            val start = angle
            angle += sweep
            start to angle
        }
    }
    Canvas(
        modifier = modifier.pointerInput(segments) {
            detectTapGestures { offset ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = offset.x - center.x
                val dy = offset.y - center.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val outerRadius = kotlin.math.min(size.width, size.height) / 2f
                val innerRadius = outerRadius - 28.dp.toPx()
                if (distance < innerRadius || distance > outerRadius) return@detectTapGestures
                var deg = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                if (deg < -90f) deg += 360f
                val tappedIndex = boundaries.indexOfFirst { (start, end) -> deg >= start && deg < end }
                if (tappedIndex >= 0) onSegmentTap(tappedIndex)
            }
        }
    ) {
        val baseStrokeWidth = 28.dp.toPx()
        segments.forEachIndexed { i, value ->
            val isDimmed = selectedIndex != null && selectedIndex != i
            val isEmphasized = selectedIndex == i
            val strokeWidth = if (isEmphasized) baseStrokeWidth * 1.15f else baseStrokeWidth
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            val (start, end) = boundaries[i]
            val sweep = (end - start) * animatedProgress
            drawArc(
                color = colors.getOrElse(i) { Color.Gray }.copy(alpha = if (isDimmed) 0.3f else 1f),
                startAngle = start,
                sweepAngle = sweep - 2f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

// ── Monthly Bar Chart ─────────────────────────────────────────────────────────
@Composable
fun MonthlyBarChartCard(monthlyData: List<Pair<String, Double>>, predictedNextMonth: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val animatedProgress by animateFloatAsState(targetValue = 1f, animationSpec = tween(1000), label = "bars")

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.monthly_spending), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.predicted_label, com.example.data.CurrencyFormatter.format(context, predictedNextMonth)), style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = maxOf(monthlyData.maxOfOrNull { it.second } ?: 1.0, predictedNextMonth, 1.0)
            val predictionLabel = stringResource(R.string.prediction_bar_label)
            val allData = monthlyData + Pair(predictionLabel, predictedNextMonth)

            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                allData.forEachIndexed { i, (month, value) ->
                    val isPredict = i == allData.lastIndex
                    val heightFraction = ((value / maxVal) * animatedProgress).toFloat().coerceIn(0.05f, 1f)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${value.toInt()}", style = Typography.labelSmall.copy(fontSize = 8.sp), color = onSurfaceVariant, maxLines = 1)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isPredict) Brush.verticalGradient(listOf(Color(0xFFFACC15), Color(0xFFF59E0B)))
                                    else Brush.verticalGradient(listOf(primary, primaryContainer))
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(month.take(3), style = Typography.labelSmall.copy(fontSize = 9.sp), color = if (isPredict) Color(0xFFF59E0B) else onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(primary))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.actual_label), style = Typography.labelSmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFACC15)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.zad_forecast_label), style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
    }
}

// ── Prediction Card ──────────────────────────────────────────────────────────
@Composable
fun PredictionCard(predictedAmount: Double, currentMonthAmount: Double, lowStockCount: Int, subscriptionsCount: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val diff = predictedAmount - currentMonthAmount
    val isUp = diff > 0
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF166534), Color(0xFF15803D))))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.zad_ai_predictions), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.next_month_label), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text(com.example.data.CurrencyFormatter.format(context, predictedAmount), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (isUp) stringResource(R.string.increase_amount_label, com.example.data.CurrencyFormatter.format(context, diff))
                        else stringResource(R.string.saving_amount_label, com.example.data.CurrencyFormatter.format(context, -diff)),
                        style = Typography.labelSmall,
                        color = if (isUp) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.nav_inventory), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text(stringResource(R.string.low_stock_count_pill, lowStockCount), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (subscriptionsCount > 0) stringResource(R.string.active_subscriptions_count_pill, subscriptionsCount) else stringResource(R.string.no_subscriptions_label),
                        style = Typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ── What-If Simulator ────────────────────────────────────────────────────────
enum class WhatIfVerdict { SAFE, RISKY, EXCEEDS }

data class WhatIfProjection(val month: Int, val remaining: Double, val installmentActive: Boolean)

data class WhatIfResult(
    val verdict: WhatIfVerdict,
    val projection: List<WhatIfProjection>,
    val firstExceedMonth: Int?
)

/** يحاكي أثر التزام شهري جديد (قسط) على الميزانية المتوقعة، شهر بشهر، ولحد ما بعد انتهاء القسط بشهرين عشان يبان التعافي. */
fun simulateWhatIf(monthlyBudget: Double, predictedMonthlySpend: Double, monthlyInstallment: Double, installmentMonths: Int): WhatIfResult {
    val months = installmentMonths.coerceIn(1, 24)
    val totalMonths = (months + 2).coerceAtMost(26)
    var firstExceed: Int? = null
    val projection = (1..totalMonths).map { m ->
        val installmentActive = m <= months
        val remaining = monthlyBudget - predictedMonthlySpend - (if (installmentActive) monthlyInstallment else 0.0)
        if (installmentActive && remaining < 0 && firstExceed == null) firstExceed = m
        WhatIfProjection(m, remaining, installmentActive)
    }
    val duringInstallment = projection.filter { it.installmentActive }
    val minRemaining = duringInstallment.minOfOrNull { it.remaining } ?: 0.0
    val verdict = when {
        minRemaining < 0 -> WhatIfVerdict.EXCEEDS
        monthlyBudget > 0 && minRemaining < monthlyBudget * 0.15 -> WhatIfVerdict.RISKY
        else -> WhatIfVerdict.SAFE
    }
    return WhatIfResult(verdict, projection, firstExceed)
}

@Composable
fun WhatIfSimulatorCard(viewModel: ZadViewModel, predictedMonthlySpend: Double) {
    val budget by viewModel.budget.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var purchaseAmountStr by remember { mutableStateOf("") }
    var installmentStr by remember { mutableStateOf("") }
    var monthsStr by remember { mutableStateOf("12") }
    var result by remember { mutableStateOf<WhatIfResult?>(null) }
    var aiNarrative by remember { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.whatif_simulator_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.whatif_simulator_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = purchaseAmountStr, onValueChange = { purchaseAmountStr = it },
                label = { Text(stringResource(R.string.whatif_purchase_amount_label, com.example.data.CurrencyFormatter.symbol(context))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = installmentStr, onValueChange = { installmentStr = it },
                    label = { Text(stringResource(R.string.whatif_monthly_installment_label, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = monthsStr, onValueChange = { monthsStr = it },
                    label = { Text(stringResource(R.string.whatif_duration_months_label)) },
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            val installment = installmentStr.toDoubleOrNull() ?: 0.0
            val months = monthsStr.toIntOrNull() ?: 0
            Button(
                onClick = {
                    val r = simulateWhatIf(budget, predictedMonthlySpend, installment, months)
                    result = r
                    aiNarrative = null
                    isLoadingNarrative = true
                    scope.launch {
                        aiNarrative = try {
                            ZadAiRepository.evaluateWhatIf(
                                monthlyBudget = budget,
                                predictedMonthlySpend = predictedMonthlySpend,
                                purchaseAmount = purchaseAmountStr.toDoubleOrNull() ?: 0.0,
                                monthlyInstallment = installment,
                                installmentMonths = months,
                                verdict = r.verdict.name,
                                firstExceedMonth = r.firstExceedMonth
                            )
                        } catch (e: Exception) { null } finally {
                            isLoadingNarrative = false
                        }
                    }
                },
                enabled = installment > 0 && months > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.whatif_run_simulation_action))
            }

            result?.let { r ->
                Spacer(modifier = Modifier.height(16.dp))
                val (verdictColor, verdictLabel) = when (r.verdict) {
                    WhatIfVerdict.SAFE -> successColor to stringResource(R.string.whatif_verdict_safe)
                    WhatIfVerdict.RISKY -> warningColor to stringResource(R.string.whatif_verdict_risky)
                    WhatIfVerdict.EXCEEDS -> dangerColor to stringResource(R.string.whatif_verdict_exceeds)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = verdictColor.copy(alpha = 0.12f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(verdictColor))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(verdictLabel, style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = verdictColor)
                        r.firstExceedMonth?.let { fm ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.whatif_exceeds_at_month, fm), style = Typography.labelSmall, color = verdictColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                val maxAbs = r.projection.maxOf { kotlin.math.abs(it.remaining) }.coerceAtLeast(1.0)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(r.projection.take(12)) { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                            val barColor = if (p.remaining < 0) dangerColor else if (p.installmentActive) warningColor else successColor
                            val heightFraction = (kotlin.math.abs(p.remaining) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier.height(70.dp).fillMaxWidth().padding(horizontal = 2.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${p.month}", style = Typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (isLoadingNarrative) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.whatif_ai_loading), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                } else if (aiNarrative != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF5F3FF), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(aiNarrative ?: "", style = Typography.bodySmall, color = Color(0xFF4C1D95))
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  ZAD INTELLIGENCE: 6 NEW FEATURES — pure calc layer (co-located with
//  result types, same pattern as WhatIfVerdict/WhatIfResult/simulateWhatIf
//  above: deterministic Kotlin first, AI narrative is an optional add-on)
// ════════════════════════════════════════════════════════════════

// ── Feature 1: Financial Stress Test ──────────────────────────────────────────
enum class StressTestStatus { CRITICAL, LOW, HEALTHY }

data class StressTestResult(
    val coverageDays: Int,
    val avgDailySpend: Double,
    val liquidSavings: Double,
    val targetDays: Int,
    val suggestedMonthlySaving: Double,
    val status: StressTestStatus
)

/** أيام التغطية = رصيد الطوارئ ÷ متوسط الصرف اليومي (آخر 30 يوم). التوفير الشهري المقترح يقفل الفجوة حتى الهدف على مدار 6 أشهر. */
fun calculateStressTest(
    transactions: List<ZadTransaction>,
    liquidSavings: Double,
    targetDays: Int = 90,
    windowDays: Int = 30,
    savingHorizonMonths: Int = 6
): StressTestResult {
    val cutoff = java.time.Instant.now().minusSeconds(windowDays * 86400L)
    val recentExpenseTotal = transactions
        .filter { it.isExpense }
        .filter { tx -> try { java.time.Instant.parse(tx.createdAt ?: "") >= cutoff } catch (e: Exception) { false } }
        .sumOf { it.amount }
    val avgDailySpend = recentExpenseTotal / windowDays
    val coverageDays = if (avgDailySpend > 0) (liquidSavings / avgDailySpend).toInt().coerceAtLeast(0) else 0
    val gapDays = (targetDays - coverageDays).coerceAtLeast(0)
    val suggestedMonthlySaving = if (gapDays > 0 && avgDailySpend > 0) (gapDays * avgDailySpend) / savingHorizonMonths else 0.0
    val status = when {
        coverageDays < 30 -> StressTestStatus.CRITICAL
        coverageDays < targetDays -> StressTestStatus.LOW
        else -> StressTestStatus.HEALTHY
    }
    return StressTestResult(coverageDays, avgDailySpend, liquidSavings, targetDays, suggestedMonthlySaving, status)
}

// ── Feature 2: Debt Snowball / Avalanche ──────────────────────────────────────
enum class DebtStrategy { SNOWBALL, AVALANCHE }

data class DebtPayoffStep(val debtName: String, val order: Int, val monthsToPayoff: Int, val totalInterest: Double)

data class DebtPayoffPlan(val strategy: DebtStrategy, val steps: List<DebtPayoffStep>, val totalMonths: Int, val totalInterestPaid: Double)

/**
 * يحاكي السداد شهراً بشهر: كل دين ياخد الحد الأدنى، والدين المستهدف (الأصغر رصيد في Snowball،
 * أو الأعلى فائدة في Avalanche) ياخد أي مبلغ إضافي + الحد الأدنى المُحرَّر من ديون اتسددت بالكامل.
 */
fun calculateDebtPayoffPlan(debts: List<com.example.data.ZadDebt>, strategy: DebtStrategy, extraMonthlyPayment: Double = 0.0): DebtPayoffPlan {
    if (debts.isEmpty()) return DebtPayoffPlan(strategy, emptyList(), 0, 0.0)
    val ordered = when (strategy) {
        DebtStrategy.SNOWBALL -> debts.sortedBy { it.remainingBalance }
        DebtStrategy.AVALANCHE -> debts.sortedByDescending { it.interestRate }
    }
    class Working(val debt: com.example.data.ZadDebt) {
        var balance = debt.remainingBalance
        var totalInterest = 0.0
        var payoffMonth = 0
    }
    val working = ordered.map { Working(it) }
    var month = 0
    var freedMinimums = 0.0
    val maxMonths = 600 // سقف أمان 50 سنة يمنع حلقة لا نهائية لو الحد الأدنى صفر لكل الديون
    while (working.any { it.balance > 0.01 } && month < maxMonths) {
        month++
        var extraPool = extraMonthlyPayment + freedMinimums
        val firstActive = working.firstOrNull { it.balance > 0.0 }
        for (w in working) {
            if (w.balance <= 0.0) continue
            val monthlyRate = w.debt.interestRate / 100.0 / 12.0
            val interest = w.balance * monthlyRate
            w.totalInterest += interest
            w.balance += interest
            var payment = w.debt.minimumPayment.coerceAtLeast(0.0)
            if (w === firstActive) {
                payment += extraPool
                extraPool = 0.0
            }
            payment = payment.coerceAtMost(w.balance)
            w.balance -= payment
            if (w.balance <= 0.01 && w.payoffMonth == 0) {
                w.payoffMonth = month
                freedMinimums += w.debt.minimumPayment
            }
        }
    }
    val steps = working.mapIndexed { idx, w -> DebtPayoffStep(w.debt.name, idx + 1, w.payoffMonth, w.totalInterest) }
    return DebtPayoffPlan(strategy, steps, working.maxOf { it.payoffMonth }, working.sumOf { it.totalInterest })
}

// ── Feature 3: Personalized Inflation Radar ───────────────────────────────────
data class CategoryInflation(val category: String, val currentMonthAvg: Double, val trailingAvg: Double, val deltaPct: Double)

/** يقارن صرف كل فئة هذا الشهر بمتوسط آخر trailingMonths شهر. فئات بدون تاريخ كافٍ (trailing = 0) تتجاهل عشان مفيش خط أساس يتقاس عليه. */
fun calculateInflationRadar(transactions: List<ZadTransaction>, context: android.content.Context, trailingMonths: Int = 3): List<CategoryInflation> {
    val otherLabel = context.getString(R.string.other_category)
    val now = java.time.ZonedDateTime.now()
    fun monthKey(dt: java.time.ZonedDateTime) = "${dt.year}-${dt.monthValue.toString().padStart(2, '0')}"
    val currentKey = monthKey(now)
    val trailingKeys = (1..trailingMonths).map { monthKey(now.minusMonths(it.toLong())) }.toSet()

    data class Bucket(val category: String, val monthKey: String, val amount: Double)
    val buckets = transactions.filter { it.isExpense }.mapNotNull { tx ->
        try {
            val zdt = java.time.Instant.parse(tx.createdAt ?: "").atZone(java.time.ZoneId.systemDefault())
            Bucket(tx.category ?: otherLabel, monthKey(zdt), tx.amount)
        } catch (e: Exception) { null }
    }

    val currentByCategory = buckets.filter { it.monthKey == currentKey }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val trailingByCategory = buckets.filter { it.monthKey in trailingKeys }.groupBy { it.category }
        .mapValues { (_, l) -> l.sumOf { it.amount } / trailingMonths }

    return (currentByCategory.keys + trailingByCategory.keys).distinct().mapNotNull { cat ->
        val current = currentByCategory[cat] ?: 0.0
        val trailing = trailingByCategory[cat] ?: 0.0
        if (trailing <= 0.0) return@mapNotNull null
        CategoryInflation(cat, current, trailing, ((current - trailing) / trailing) * 100.0)
    }.sortedByDescending { it.deltaPct }
}

// ── Feature 4: Behavioral Nudge Engine ────────────────────────────────────────
data class WeekdaySpike(val weekday: String, val amount: Double, val avgOtherDays: Double, val spikeRatio: Double)

/** يعتمد على user_behavior_profile.spending_pattern_by_weekday المحسوب على الخادم — يرصد أعلى يوم صرف لو زاد بشكل ملحوظ (1.3x+) عن باقي الأيام. */
fun detectWeekdaySpike(profile: com.example.data.UserBehaviorProfile?): WeekdaySpike? {
    val pattern = profile?.spendingPatternByWeekday ?: return null
    if (pattern.size < 2) return null
    val maxEntry = pattern.maxByOrNull { it.value } ?: return null
    val others = pattern.filterKeys { it != maxEntry.key }.values
    if (others.isEmpty()) return null
    val avgOthers = others.average()
    if (avgOthers <= 0.0) return null
    val ratio = maxEntry.value / avgOthers
    if (ratio < 1.3) return null
    return WeekdaySpike(maxEntry.key, maxEntry.value, avgOthers, ratio)
}

// ── Feature 6: Smart Buying Timing ────────────────────────────────────────────
data class BuyingTimingSuggestion(val itemName: String, val buyByDate: java.time.LocalDate, val daysUntil: Int, val dailyConsumptionRate: Double)

/** يعتمد على user_behavior_profile.inventory_consumption_rate المحسوب على الخادم — يعرض بس الأصناف اللي محتاجة شراء خلال 60 يوم. */
fun calculateBuyingTiming(inventory: List<ZadInventory>, profile: com.example.data.UserBehaviorProfile?): List<BuyingTimingSuggestion> {
    val rates = profile?.inventoryConsumptionRate ?: return emptyList()
    if (rates.isEmpty()) return emptyList()
    val today = java.time.LocalDate.now()
    return inventory.mapNotNull { item ->
        val rate = rates[item.itemName] ?: return@mapNotNull null
        if (rate <= 0.0) return@mapNotNull null
        val daysLeft = (item.quantity / rate).toInt()
        if (daysLeft > 60) return@mapNotNull null
        BuyingTimingSuggestion(item.itemName, today.plusDays(daysLeft.toLong()), daysLeft, rate)
    }.sortedBy { it.daysUntil }
}

// ════════════════════════════════════════════════════════════════
//  ZAD INTELLIGENCE: 6 NEW FEATURES — cards
// ════════════════════════════════════════════════════════════════

// ── Feature 1 card ─────────────────────────────────────────────────────────
@Composable
fun FinancialStressTestCard(transactions: List<ZadTransaction>, emergencyFund: Double, onUpdateEmergencyFund: (Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result = remember(transactions, emergencyFund) { calculateStressTest(transactions, emergencyFund) }
    var showEditDialog by remember { mutableStateOf(false) }
    var aiNarrative by remember(result) { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    val (statusColor, statusLabel) = when (result.status) {
        StressTestStatus.CRITICAL -> dangerColor to stringResource(R.string.stress_test_status_critical)
        StressTestStatus.LOW -> warningColor to stringResource(R.string.stress_test_status_low)
        StressTestStatus.HEALTHY -> successColor to stringResource(R.string.stress_test_status_healthy)
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.stress_test_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showEditDialog = true }) { Text(stringResource(R.string.stress_test_edit_fund_action), style = Typography.labelSmall) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.stress_test_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.stress_test_coverage_days, result.coverageDays), style = Typography.displaySmall, fontWeight = FontWeight.Bold, color = statusColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.stress_test_target_label, result.targetDays), style = Typography.labelSmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (result.coverageDays.toFloat() / result.targetDays.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.stress_test_emergency_fund_label), style = Typography.labelSmall, color = onSurfaceVariant)
                    Text(com.example.data.CurrencyFormatter.format(context, result.liquidSavings), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(statusLabel, style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            if (result.suggestedMonthlySaving > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.stress_test_suggested_saving_label, com.example.data.CurrencyFormatter.format(context, result.suggestedMonthlySaving)),
                    style = Typography.bodySmall, color = onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            AiNarrativeSection(
                narrative = aiNarrative,
                isLoading = isLoadingNarrative,
                onExplain = {
                    isLoadingNarrative = true
                    scope.launch {
                        aiNarrative = try {
                            ZadAiRepository.narrateStressTest(
                                result.coverageDays, result.avgDailySpend, result.liquidSavings,
                                result.targetDays, result.suggestedMonthlySaving, result.status.name
                            )
                        } catch (e: Exception) { null } finally { isLoadingNarrative = false }
                    }
                }
            )
        }
    }

    if (showEditDialog) {
        var fundStr by remember { mutableStateOf(if (emergencyFund > 0) emergencyFund.toString() else "") }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.stress_test_edit_fund_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = fundStr, onValueChange = { fundStr = it },
                    label = { Text(stringResource(R.string.stress_test_emergency_fund_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateEmergencyFund(fundStr.toDoubleOrNull() ?: 0.0)
                    showEditDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ── Feature 3 card ─────────────────────────────────────────────────────────
@Composable
fun InflationRadarCard(transactions: List<ZadTransaction>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = remember(transactions) { calculateInflationRadar(transactions, context) }
    var aiNarrative by remember(categories) { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.inflation_radar_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.inflation_radar_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (categories.isEmpty()) {
                Text(stringResource(R.string.inflation_radar_no_data), style = Typography.bodySmall, color = onSurfaceVariant)
            } else {
                categories.take(5).forEach { cat ->
                    val isIncrease = cat.deltaPct > 0
                    val pillColor = if (isIncrease) dangerColor else successColor
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.category, style = Typography.bodyMedium, color = onSurface, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(10.dp), color = pillColor.copy(alpha = 0.12f)) {
                            Text(
                                stringResource(
                                    if (isIncrease) R.string.inflation_radar_increase_pill else R.string.inflation_radar_decrease_pill,
                                    "%.0f".format(kotlin.math.abs(cat.deltaPct))
                                ),
                                style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = pillColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                AiNarrativeSection(
                    narrative = aiNarrative,
                    isLoading = isLoadingNarrative,
                    onExplain = {
                        isLoadingNarrative = true
                        scope.launch {
                            val summary = categories.take(5).joinToString("\n") {
                                "- ${it.category}: هذا الشهر ${it.currentMonthAvg}, المعتاد ${it.trailingAvg}, تغير ${"%.0f".format(it.deltaPct)}%"
                            }
                            aiNarrative = try { ZadAiRepository.narrateInflationRadar(summary) } catch (e: Exception) { null } finally { isLoadingNarrative = false }
                        }
                    }
                )
            }
        }
    }
}

// ── Feature 4 card ─────────────────────────────────────────────────────────
@Composable
fun BehavioralNudgeCard(profile: com.example.data.UserBehaviorProfile?) {
    val scope = rememberCoroutineScope()
    val spike = remember(profile) { detectWeekdaySpike(profile) } ?: return
    var aiNarrative by remember(spike) { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF7ED), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.nudge_card_title), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.nudge_weekday_spike_text, "%.0f".format((spike.spikeRatio - 1) * 100), spike.weekday),
                style = Typography.bodySmall, color = onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            AiNarrativeSection(
                narrative = aiNarrative,
                isLoading = isLoadingNarrative,
                onExplain = {
                    isLoadingNarrative = true
                    scope.launch {
                        aiNarrative = try {
                            ZadAiRepository.narrateNudge(spike.weekday, spike.amount, spike.avgOtherDays, spike.spikeRatio)
                        } catch (e: Exception) { null } finally { isLoadingNarrative = false }
                    }
                }
            )
        }
    }
}

// ── Feature 6 card ─────────────────────────────────────────────────────────
@Composable
fun SmartBuyingTimingCard(inventory: List<ZadInventory>, serverBehaviorProfile: com.example.data.UserBehaviorProfile?) {
    val scope = rememberCoroutineScope()
    val suggestions = remember(inventory, serverBehaviorProfile) { calculateBuyingTiming(inventory, serverBehaviorProfile) }
    var aiNarrative by remember { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }
    var narratingItem by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.buying_timing_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.buying_timing_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (suggestions.isEmpty()) {
                Text(stringResource(R.string.buying_timing_no_data), style = Typography.bodySmall, color = onSurfaceVariant)
            } else {
                suggestions.take(5).forEach { s ->
                    val urgencyColor = if (s.daysUntil <= 7) warningColor else onSurfaceVariant
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                            narratingItem = s.itemName
                            isLoadingNarrative = true
                            aiNarrative = null
                            scope.launch {
                                aiNarrative = try {
                                    ZadAiRepository.narrateBuyingTiming(s.itemName, s.daysUntil, s.dailyConsumptionRate)
                                } catch (e: Exception) { null } finally { isLoadingNarrative = false }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = urgencyColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.itemName, style = Typography.bodyMedium, color = onSurface, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.buying_timing_buy_by_label, s.buyByDate.toString(), s.daysUntil),
                            style = Typography.labelSmall, color = urgencyColor
                        )
                    }
                }
                if (narratingItem != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AiNarrativeSection(narrative = aiNarrative, isLoading = isLoadingNarrative, onExplain = null)
                }
            }
        }
    }
}

// ── Feature 5 card ─────────────────────────────────────────────────────────
@Composable
fun FinancialChallengesCard(familyViewModel: com.example.ui.viewmodels.FamilyViewModel) {
    val familyState by familyViewModel.state.collectAsState()
    val challenges by familyViewModel.financialChallenges.collectAsState()
    val progress by familyViewModel.challengeProgress.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { familyViewModel.loadFinancialChallenges() }

    val active = familyState
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.financial_challenges_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                if (active is com.example.ui.viewmodels.FamilyState.Active) {
                    TextButton(onClick = { showCreateDialog = true }) { Text(stringResource(R.string.financial_challenge_create_action), style = Typography.labelSmall) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (active !is com.example.ui.viewmodels.FamilyState.Active) {
                Text(stringResource(R.string.financial_challenge_join_family_hint), style = Typography.bodySmall, color = onSurfaceVariant)
            } else if (challenges.isEmpty()) {
                Text(stringResource(R.string.financial_challenge_empty_state), style = Typography.bodySmall, color = onSurfaceVariant)
            } else {
                val context = LocalContext.current
                val myMemberId = active.myMemberInfo.id
                challenges.forEach { challenge ->
                    val challengeProgressList = progress[challenge.id].orEmpty()
                    val myProgress = challengeProgressList.find { it.userId == active.myMemberInfo.userId }
                    val currentAmount = myProgress?.currentAmount ?: 0.0
                    val isCompleted = myProgress?.isCompleted == true
                    val fraction = (currentAmount / challenge.targetAmount.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(challenge.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                            if (isCompleted) {
                                Text(stringResource(R.string.financial_challenge_completed_label), style = Typography.labelSmall, color = successColor)
                            } else {
                                TextButton(onClick = { familyViewModel.contributeToChallenge(challenge.id, myMemberId, challenge.targetAmount - currentAmount) }) {
                                    Text(stringResource(R.string.financial_challenge_contribute_action), style = Typography.labelSmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (isCompleted) successColor else primary,
                            trackColor = primary.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(
                                    R.string.financial_challenge_progress_label,
                                    com.example.data.CurrencyFormatter.format(context, currentAmount),
                                    com.example.data.CurrencyFormatter.format(context, challenge.targetAmount)
                                ),
                                style = Typography.labelSmall, color = onSurfaceVariant
                            )
                            if (challenge.rewardAmount > 0) {
                                Text(
                                    stringResource(R.string.financial_challenge_reward_label, com.example.data.CurrencyFormatter.format(context, challenge.rewardAmount)),
                                    style = Typography.labelSmall, color = onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && active is com.example.ui.viewmodels.FamilyState.Active) {
        var title by remember { mutableStateOf("") }
        var targetStr by remember { mutableStateOf("") }
        var rewardStr by remember { mutableStateOf("") }
        var durationStr by remember { mutableStateOf("7") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.financial_challenge_create_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.financial_challenge_title_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = targetStr, onValueChange = { targetStr = it }, label = { Text(stringResource(R.string.financial_challenge_target_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rewardStr, onValueChange = { rewardStr = it }, label = { Text(stringResource(R.string.financial_challenge_reward_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text(stringResource(R.string.financial_challenge_duration_hint)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetStr.toDoubleOrNull() ?: return@Button
                    if (title.isNotBlank()) {
                        familyViewModel.createFinancialChallenge(title, target, rewardStr.toDoubleOrNull() ?: 0.0, durationStr.toIntOrNull() ?: 7)
                        showCreateDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ── Seasonal & Event Budget Forecasting: sinking funds card ────────────────
@Composable
fun SinkingFundsCard(familyViewModel: com.example.ui.viewmodels.FamilyViewModel) {
    val familyState by familyViewModel.state.collectAsState()
    val funds by familyViewModel.sinkingFunds.collectAsState()
    val upcomingEvents by familyViewModel.upcomingSeasonalEvents.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var contributeTarget by remember { mutableStateOf<com.example.data.SinkingFund?>(null) }

    LaunchedEffect(Unit) {
        familyViewModel.loadSinkingFunds()
        familyViewModel.loadUpcomingSeasonalEvents()
    }

    val active = familyState
    val context = LocalContext.current
    com.example.ui.components.GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f),
        contentPadding = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catSavingsBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(18.dp), tint = catSavingsIcon)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sinking_funds_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.weight(1f))
            if (active is com.example.ui.viewmodels.FamilyState.Active) {
                TextButton(onClick = { showCreateDialog = true }) { Text(stringResource(R.string.sinking_fund_create_action), style = Typography.labelSmall) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (active !is com.example.ui.viewmodels.FamilyState.Active) {
            Text(stringResource(R.string.financial_challenge_join_family_hint), style = Typography.bodySmall, color = onSurfaceVariant)
        } else if (funds.isEmpty()) {
            Text(stringResource(R.string.sinking_fund_empty_state), style = Typography.bodySmall, color = onSurfaceVariant)
        } else {
            funds.forEach { fund ->
                val fraction = (fund.currentAmount / fund.targetAmount.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                val isCompleted = fraction >= 1f

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fund.name, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                        if (isCompleted) {
                            Text(stringResource(R.string.financial_challenge_completed_label), style = Typography.labelSmall, color = successColor)
                        } else {
                            TextButton(onClick = { contributeTarget = fund }) {
                                Text(stringResource(R.string.sinking_fund_contribute_action), style = Typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (isCompleted) successColor else primary,
                        trackColor = primary.copy(alpha = 0.12f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.financial_challenge_progress_label,
                            com.example.data.CurrencyFormatter.format(context, fund.currentAmount),
                            com.example.data.CurrencyFormatter.format(context, fund.targetAmount)
                        ),
                        style = Typography.labelSmall, color = onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showCreateDialog && active is com.example.ui.viewmodels.FamilyState.Active) {
        var name by remember { mutableStateOf("") }
        var targetStr by remember { mutableStateOf("") }
        var selectedEventId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.sinking_fund_create_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sinking_fund_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = targetStr, onValueChange = { targetStr = it }, label = { Text(stringResource(R.string.sinking_fund_target_hint)) }, modifier = Modifier.fillMaxWidth())
                    if (upcomingEvents.isNotEmpty()) {
                        Text(stringResource(R.string.sinking_fund_link_event_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                        upcomingEvents.forEach { (event, window) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedEventId == event.id, onClick = { selectedEventId = event.id })
                                Text(seasonalEventDisplayNameOrRaw(event), style = Typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetStr.toDoubleOrNull() ?: return@Button
                    if (name.isNotBlank()) {
                        val linkedWindow = upcomingEvents.find { it.first.id == selectedEventId }?.second
                        familyViewModel.createSinkingFund(name, target, linkedWindow?.startDate, selectedEventId)
                        showCreateDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    contributeTarget?.let { fund ->
        var amountStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { contributeTarget = null },
            title = { Text(stringResource(R.string.sinking_fund_contribute_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(stringResource(R.string.sinking_fund_amount_hint)) }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: return@Button
                    familyViewModel.contributeToSinkingFund(fund.id, amount)
                    contributeTarget = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { contributeTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun seasonalEventDisplayNameOrRaw(event: com.example.data.SeasonalEvent): String = when (event.slug) {
    "ramadan" -> "رمضان"
    "eid_al_fitr" -> "عيد الفطر"
    "eid_al_adha" -> "عيد الأضحى"
    "back_to_school" -> "العودة للمدارس"
    else -> event.name
}

// ── Feature 2 card (Subscriptions tab) ────────────────────────────────────────
@Composable
fun DebtPayoffPlannerCard(debts: List<com.example.data.ZadDebt>, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strategy by remember { mutableStateOf(DebtStrategy.SNOWBALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var aiNarrative by remember { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    val plan = remember(debts, strategy) { calculateDebtPayoffPlan(debts, strategy) }

    LaunchedEffect(Unit) { viewModel.loadDebts() }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.debt_planner_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddDialog = true }) { Text(stringResource(R.string.debt_add_action), style = Typography.labelSmall) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.debt_planner_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (debts.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.debt_empty_state_title), style = Typography.titleSmall, color = onSurfaceVariant)
                    Text(stringResource(R.string.debt_empty_state_hint), style = Typography.bodySmall, color = onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = strategy == DebtStrategy.SNOWBALL,
                        onClick = { strategy = DebtStrategy.SNOWBALL },
                        label = { Text(stringResource(R.string.debt_strategy_snowball)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = strategy == DebtStrategy.AVALANCHE,
                        onClick = { strategy = DebtStrategy.AVALANCHE },
                        label = { Text(stringResource(R.string.debt_strategy_avalanche)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                plan.steps.sortedBy { it.order }.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = primaryContainer) {
                            Text("${step.order}", style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = primary, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(step.debtName, style = Typography.bodyMedium, color = onSurface, modifier = Modifier.weight(1f))
                        Text("${step.monthsToPayoff} " + stringResource(R.string.months_unit), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.debt_total_months_label, plan.totalMonths), style = Typography.bodySmall, fontWeight = FontWeight.SemiBold, color = onSurface)
                Text(stringResource(R.string.debt_total_interest_label, com.example.data.CurrencyFormatter.format(context, plan.totalInterestPaid)), style = Typography.bodySmall, color = onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))
                AiNarrativeSection(
                    narrative = aiNarrative,
                    isLoading = isLoadingNarrative,
                    onExplain = {
                        isLoadingNarrative = true
                        scope.launch {
                            val stepsSummary = plan.steps.sortedBy { it.order }.joinToString("\n") { "- ${it.debtName}: ترتيب ${it.order}, يُسدد خلال ${it.monthsToPayoff} شهر" }
                            aiNarrative = try {
                                ZadAiRepository.narrateDebtPlan(strategy.name, plan.totalMonths, plan.totalInterestPaid, stepsSummary)
                            } catch (e: Exception) { null } finally { isLoadingNarrative = false }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var remainingStr by remember { mutableStateOf("") }
        var rateStr by remember { mutableStateOf("") }
        var minPaymentStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.debt_add_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.debt_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = remainingStr, onValueChange = { remainingStr = it }, label = { Text(stringResource(R.string.debt_remaining_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rateStr, onValueChange = { rateStr = it }, label = { Text(stringResource(R.string.debt_interest_rate_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = minPaymentStr, onValueChange = { minPaymentStr = it }, label = { Text(stringResource(R.string.debt_minimum_payment_hint)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val remaining = remainingStr.toDoubleOrNull() ?: return@Button
                    if (name.isNotBlank()) {
                        viewModel.addDebt(
                            com.example.data.ZadDebt(
                                name = name,
                                principalAmount = remaining,
                                remainingBalance = remaining,
                                interestRate = rateStr.toDoubleOrNull() ?: 0.0,
                                minimumPayment = minPaymentStr.toDoubleOrNull() ?: 0.0
                            )
                        )
                        showAddDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ── Shared AI narrative section (loading spinner / result box / explain button) ──
@Composable
private fun AiNarrativeSection(narrative: String?, isLoading: Boolean, onExplain: (() -> Unit)?) {
    when {
        isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.ai_narrative_loading), style = Typography.labelSmall, color = onSurfaceVariant)
        }
        narrative != null -> Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF5F3FF), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(narrative, style = Typography.bodySmall, color = Color(0xFF4C1D95))
            }
        }
        onExplain != null -> TextButton(onClick = onExplain) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.ai_explain_action), style = Typography.labelSmall, color = primary)
        }
    }
}

// ── Live web search badge (shared by Deal Matcher / Price Shock Radar) ───────
@Composable
private fun LiveSearchBadge() {
    Surface(shape = RoundedCornerShape(8.dp), color = dangerColor.copy(alpha = 0.1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dangerColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.live_search_source_badge), style = Typography.labelSmall, color = dangerColor)
        }
    }
}

// ── Deal Matcher card ──────────────────────────────────────────────────────
@Composable
fun LiveDealsCard(shortageItems: List<String>, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val deals by viewModel.liveDeals.collectAsState()
    val fetchState by viewModel.dealsFetchState.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.live_deals_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                if (fetchState == ZadViewModel.LiveFetchState.Fetched && deals.isNotEmpty()) LiveSearchBadge()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.live_deals_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            when (fetchState) {
                ZadViewModel.LiveFetchState.NotFetchedYet ->
                    Text(stringResource(R.string.live_search_not_fetched_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                ZadViewModel.LiveFetchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.live_search_loading), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                ZadViewModel.LiveFetchState.Error ->
                    Text(stringResource(R.string.live_search_error_state), style = Typography.bodySmall, color = error)
                ZadViewModel.LiveFetchState.Fetched -> {
                    if (deals.isEmpty()) {
                        Text(stringResource(R.string.live_search_empty_state), style = Typography.bodySmall, color = onSurfaceVariant)
                    } else {
                        deals.take(6).forEach { deal ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(deal.item, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                                    if (deal.discountPercent > 0) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = successColor.copy(alpha = 0.12f)) {
                                            Text(
                                                stringResource(R.string.live_deals_discount_pill, "%.0f".format(deal.discountPercent)),
                                                style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = successColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    stringResource(R.string.live_deals_price_at_store, com.example.data.CurrencyFormatter.format(context, deal.price), deal.store),
                                    style = Typography.bodySmall, color = onSurfaceVariant
                                )
                                if (!deal.note.isNullOrBlank()) {
                                    Text(deal.note, style = Typography.labelSmall, color = onSurfaceVariant.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.refreshLiveDeals(shortageItems) },
                enabled = fetchState != ZadViewModel.LiveFetchState.Loading && shortageItems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.live_search_refresh_action))
            }
        }
    }
}

// ── Price Shock Predictor card ────────────────────────────────────────────
@Composable
fun PriceShockRadarCard(categories: List<String>, viewModel: ZadViewModel) {
    val warnings by viewModel.priceShockWarnings.collectAsState()
    val fetchState by viewModel.priceShockFetchState.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.price_shock_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                if (fetchState == ZadViewModel.LiveFetchState.Fetched && warnings.isNotEmpty()) LiveSearchBadge()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.price_shock_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            when (fetchState) {
                ZadViewModel.LiveFetchState.NotFetchedYet ->
                    Text(stringResource(R.string.live_search_not_fetched_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                ZadViewModel.LiveFetchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.live_search_loading), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                ZadViewModel.LiveFetchState.Error ->
                    Text(stringResource(R.string.live_search_error_state), style = Typography.bodySmall, color = error)
                ZadViewModel.LiveFetchState.Fetched -> {
                    if (warnings.isEmpty()) {
                        Text(stringResource(R.string.live_search_empty_state), style = Typography.bodySmall, color = onSurfaceVariant)
                    } else {
                        warnings.take(6).forEach { w ->
                            val isDown = w.direction == "down"
                            val trendColor = if (isDown) successColor else dangerColor
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isDown) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                        contentDescription = null, tint = trendColor, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(w.category, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                                    Text(
                                        stringResource(
                                            if (isDown) R.string.price_shock_down_label else R.string.price_shock_up_label,
                                            "%.0f".format(w.expectedChangePct)
                                        ),
                                        style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = trendColor
                                    )
                                }
                                if (w.reasoning.isNotBlank()) {
                                    Text(w.reasoning, style = Typography.bodySmall, color = onSurfaceVariant)
                                }
                                if (!w.sourceNote.isNullOrBlank()) {
                                    Text(w.sourceNote, style = Typography.labelSmall, color = onSurfaceVariant.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.refreshPriceShockWarnings(categories) },
                enabled = fetchState != ZadViewModel.LiveFetchState.Loading && categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.live_search_refresh_action))
            }
        }
    }
}

// ── Mini Stat Card ────────────────────────────────────────────────────────────
@Composable
fun MiniStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    bgColor: Color
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(bgColor), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  TAB 3: CHAT
// ════════════════════════════════════════════════════════════════

@Composable
fun ChatTab(
    messages: List<AiChatMessage>,
    isTyping: Boolean,
    inputText: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val quickPrompts = listOf(
        Icons.Default.Restaurant to stringResource(R.string.quick_prompt_recipe),
        Icons.Default.BarChart to stringResource(R.string.quick_prompt_analyze_spending),
        Icons.Default.ShoppingCart to stringResource(R.string.quick_prompt_shopping_missing),
        Icons.Default.MonetizationOn to stringResource(R.string.quick_prompt_save_more),
        Icons.Default.Assignment to stringResource(R.string.quick_prompt_subscriptions),
        Icons.Default.Eco to stringResource(R.string.quick_prompt_healthy_meal)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.size <= 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.ask_zad_hint_short),
                                style = Typography.labelMedium, color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
                        }
                        quickPrompts.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (icon, prompt) ->
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                            .background(primaryContainer)
                                            .clickable { onInputChange(prompt); onSend() }
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(prompt, style = Typography.labelSmall, color = primary, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg -> ZadIntChatBubble(msg) }

            if (isTyping) {
                item { ZadIntTypingIndicator() }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ask_zad_placeholder), color = onSurfaceVariant, style = Typography.bodySmall) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = outlineVariant,
                    focusedContainerColor = surfaceContainerLow,
                    unfocusedContainerColor = surfaceContainerLow
                ),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(primary)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_action), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZadIntChatBubble(msg: AiChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.75f).clip(RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            )).background(if (msg.isUser) primary else surfaceContainerHigh).padding(12.dp)
        ) {
            Column {
                if (!msg.isUser) {
                    Text(stringResource(R.string.app_name), fontSize = 10.sp, color = primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(msg.text, color = if (msg.isUser) Color.White else onSurface, style = Typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ZadIntTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  TOP BAR
// ════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceTopBar(onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.zad_ai), style = Typography.headlineMedium, fontWeight = FontWeight.Bold, color = primary)
            }
        },
        actions = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
    )
}

// ════════════════════════════════════════════════════════════════
//  LOCAL ML HELPERS
// ════════════════════════════════════════════════════════════════

fun computeMonthlyData(transactions: List<ZadTransaction>, context: android.content.Context): List<Pair<String, Double>> {
    if (transactions.isEmpty()) return emptyList()
    val monthNames = context.resources.getStringArray(R.array.month_names_short).toList()

    val grouped = transactions
        .filter { it.isExpense }
        .groupBy { tx ->
            try {
                val instant = java.time.Instant.parse(tx.createdAt ?: "")
                val date = instant.atZone(java.time.ZoneId.systemDefault())
                "${date.year}-${date.monthValue.toString().padStart(2,'0')}"
            } catch (e: Exception) { null }
        }

    val validGrouped = mutableMapOf<String, List<ZadTransaction>>()
    for ((k, v) in grouped) {
        if (k != null) validGrouped[k] = v
    }

    return validGrouped
        .toSortedMap()
        .entries.toList()
        .takeLast(6)
        .map { (key, txs) ->
            val monthIdx = (key.split("-").getOrNull(1)?.toIntOrNull() ?: 1) - 1
            monthNames.getOrElse(monthIdx) { key } to txs.sumOf { it.amount }
        }
}

fun predictNextMonth(monthlyData: List<Pair<String, Double>>): Double {
    if (monthlyData.isEmpty()) return 0.0
    if (monthlyData.size == 1) return monthlyData.first().second
    val values = monthlyData.map { it.second }
    val weights = values.indices.map { (it + 1).toDouble() }
    val weightedSum = values.zip(weights).sumOf { (v, w) -> v * w }
    val weightTotal = weights.sum()
    return if (weightTotal > 0) weightedSum / weightTotal else values.average()
}

// ════════════════════════════════════════════════════════════════
//  PREMIUM CARDS — قوة الصرف + مقارنة شهرية + تحليل سلوكي + تصدير
// ════════════════════════════════════════════════════════════════

@Composable
private fun SpendingPowerGaugeCard(power: com.example.data.ZadCentralBrain.SpendingPower) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gaugeColor = when {
        power.powerPct >= 60 -> Color(0xFF22C55E)
        power.powerPct >= 35 -> Color(0xFF84CC16)
        power.powerPct >= 15 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val animatedPct by animateFloatAsState(
        targetValue = power.powerPct / 100f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "gauge"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = gaugeColor, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.spending_power), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(10.dp), color = gaugeColor.copy(alpha = 0.12f)) {
                    Text(
                        power.status,
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = gaugeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // العداد نصف الدائري
            Box(contentAlignment = Alignment.BottomCenter) {
                Canvas(modifier = Modifier.size(width = 220.dp, height = 120.dp)) {
                    val strokeWidth = 20.dp.toPx()
                    val radius = (size.width - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height - strokeWidth / 4f)
                    val arcSize = Size(radius * 2, radius * 2)
                    val topLeft = Offset(center.x - radius, center.y - radius)

                    // خلفية مقسمة مناطق: أحمر → برتقالي → أخضر فاتح → أخضر
                    val zones = listOf(
                        Triple(180f, 27f, Color(0xFFEF4444).copy(alpha = 0.25f)),
                        Triple(207f, 36f, Color(0xFFF59E0B).copy(alpha = 0.25f)),
                        Triple(243f, 45f, Color(0xFF84CC16).copy(alpha = 0.25f)),
                        Triple(288f, 72f, Color(0xFF22C55E).copy(alpha = 0.25f))
                    )
                    zones.forEach { (start, sweep, color) ->
                        drawArc(
                            color = color,
                            startAngle = start, sweepAngle = sweep - 2f,
                            useCenter = false, topLeft = topLeft, size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    // قوس التقدم الفعلي
                    drawArc(
                        color = gaugeColor,
                        startAngle = 180f, sweepAngle = 180f * animatedPct,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    // المؤشر (الإبرة)
                    val needleAngle = Math.toRadians((180.0 + 180.0 * animatedPct))
                    val needleLen = radius - strokeWidth
                    val needleEnd = Offset(
                        center.x + (needleLen * kotlin.math.cos(needleAngle)).toFloat(),
                        center.y + (needleLen * kotlin.math.sin(needleAngle)).toFloat()
                    )
                    drawLine(
                        color = onSurface.copy(alpha = 0.75f),
                        start = center, end = needleEnd,
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                    )
                    drawCircle(color = gaugeColor, radius = 7.dp.toPx(), center = center)
                    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 26.dp)) {
                    Text(
                        com.example.data.CurrencyFormatter.formatNumber(context, power.dailySafeSpend),
                        style = Typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = gaugeColor
                    )
                    Text(stringResource(R.string.safe_per_day_suffix, com.example.data.CurrencyFormatter.symbol(context)), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${power.daysLeftInMonth}", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(stringResource(R.string.days_left_label), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(com.example.data.CurrencyFormatter.format(context, power.currentDailyAvg), style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (power.currentDailyAvg > power.dailySafeSpend && power.dailySafeSpend > 0) Color(0xFFEF4444) else onSurface)
                    Text(stringResource(R.string.actual_daily_rate), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${power.powerPct}%", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = gaugeColor)
                    Text(stringResource(R.string.budget_remaining_pct_label), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MonthComparisonCard(mc: com.example.data.ZadCentralBrain.MonthComparison) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val improved = mc.deltaPct <= 0
    val deltaColor = if (improved) Color(0xFF22C55E) else Color(0xFFEF4444)
    val maxSpend = maxOf(mc.thisMonthSpent, mc.lastMonthSpent, 1.0)
    val animatedProgress by animateFloatAsState(targetValue = 1f, animationSpec = tween(900), label = "mc")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.compare_last_month), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = deltaColor.copy(alpha = 0.12f)) {
                    Text(
                        "${if (mc.deltaPct >= 0) "+" else ""}${mc.deltaPct}%",
                        style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = deltaColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            listOf(
                Triple(stringResource(R.string.this_month_label), mc.thisMonthSpent, primary),
                Triple(stringResource(R.string.last_month_same_period_label), mc.lastMonthSpent, onSurfaceVariant.copy(alpha = 0.45f))
            ).forEach { (label, value, barColor) ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
                        Text(com.example.data.CurrencyFormatter.format(context, value), style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(surfaceContainer)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(((value / maxSpend) * animatedProgress).toFloat().coerceIn(0.02f, 1f))
                                .fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(barColor)
                        )
                    }
                }
            }

            if (mc.categoryDeltas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.biggest_changes), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                mc.categoryDeltas.take(3).forEach { (cat, thisM, lastM) ->
                    val diff = thisM - lastM
                    val up = diff > 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (up) Color(0xFFEF4444) else Color(0xFF22C55E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(cat, style = Typography.bodySmall, color = onSurface, modifier = Modifier.weight(1f))
                        Text(
                            "${if (up) "+" else ""}${com.example.data.CurrencyFormatter.format(context, diff)}",
                            style = Typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (up) Color(0xFFEF4444) else Color(0xFF22C55E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviorAnalysisCard(bp: com.example.data.ZadCentralBrain.BehaviorProfile) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFECFDF5),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.zad_knows_you), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
            }
            Spacer(modifier = Modifier.height(12.dp))

            @Composable
            fun factRow(emoji: String, text: String) {
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text(emoji, style = Typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, style = Typography.bodySmall, color = Color(0xFF064E3B), lineHeight = 18.sp)
                }
            }

            factRow("📅", stringResource(R.string.top_spending_day_fact, bp.topSpendingDay, com.example.data.CurrencyFormatter.format(context, bp.topSpendingDayAvg)))
            if (bp.weekendSharePct >= 30)
                factRow("🎉", stringResource(R.string.weekend_heavy_spending_fact, bp.weekendSharePct))
            else
                factRow("🧘", stringResource(R.string.balanced_week_spending_fact, bp.weekendSharePct))
            factRow("💳", stringResource(R.string.avg_transaction_fact, com.example.data.CurrencyFormatter.format(context, bp.avgTransaction), bp.biggestExpenseTitle, com.example.data.CurrencyFormatter.format(context, bp.biggestExpenseAmount)))
            if (bp.impulsePurchases >= 2)
                factRow("🛍️", stringResource(R.string.impulse_purchases_fact, bp.impulsePurchases))
            if (bp.eveningSharePct >= 50)
                factRow("🌙", stringResource(R.string.evening_spending_fact, bp.eveningSharePct))
        }
    }
}

@Composable
private fun ExportReportButton(report: com.example.data.ZadCentralBrain.BrainReport) {
    val context = LocalContext.current
    val exportSubject = stringResource(R.string.zad_export_report_subject)
    val shareTitle = stringResource(R.string.share_zad_report_title)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val text = com.example.data.ZadCentralBrain.buildExportText(context, report)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, exportSubject)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            try {
                context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
            } catch (e: Exception) { /* لا يوجد تطبيق مشاركة */ }
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.IosShare, contentDescription = null, tint = onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_monthly_report), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  BRAIN REPORT CARDS — تقرير العقل المركزي
// ════════════════════════════════════════════════════════════════

@Composable
private fun HealthScoreCard(report: com.example.data.ZadCentralBrain.BrainReport) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scoreColor = when {
        report.healthScore >= 85 -> Color(0xFF22C55E)
        report.healthScore >= 65 -> Color(0xFF84CC16)
        report.healthScore >= 40 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val animatedScore by animateFloatAsState(
        targetValue = report.healthScore / 100f,
        animationSpec = tween(900),
        label = "score"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                CircularProgressIndicator(
                    progress = { animatedScore },
                    modifier = Modifier.fillMaxSize(),
                    color = scoreColor,
                    strokeWidth = 8.dp,
                    trackColor = scoreColor.copy(alpha = 0.12f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${report.healthScore}",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = scoreColor
                    )
                    Text("/100", style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.financial_health_label),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                Text(
                    report.healthLabel,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.spent_remaining_summary, com.example.data.CurrencyFormatter.format(context, report.totalSpent), com.example.data.CurrencyFormatter.format(context, report.remaining)),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant
                )
                if (report.subscriptionsMonthlyCost > 0) {
                    Text(
                        stringResource(R.string.subscriptions_monthly_cost_label, com.example.data.CurrencyFormatter.format(context, report.subscriptionsMonthlyCost)),
                        style = Typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BrainInsightsCard(insights: List<String>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F3FF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.zad_notes_title),
                    style = Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6D28D9)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            insights.take(4).forEach { insight ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        insight,
                        style = Typography.bodySmall,
                        color = Color(0xFF4C1D95),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DepletionForecastCard(forecasts: List<com.example.data.ZadCentralBrain.DepletionForecast>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.depletion_forecast_title),
                    style = Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            forecasts.take(5).forEach { f ->
                val urgent = f.predictedDaysLeft <= 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        f.itemName,
                        style = Typography.bodyMedium,
                        color = onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (urgent) Color(0xFFFFF5F5) else Color(0xFFF0FDF4)
                    ) {
                        Text(
                            when {
                                f.predictedDaysLeft <= 0 -> stringResource(R.string.will_deplete_soon)
                                f.predictedDaysLeft == 1 -> stringResource(R.string.one_day_left_label)
                                else -> stringResource(R.string.days_left_count_label, f.predictedDaysLeft)
                            },
                            style = Typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (urgent) Color(0xFFEF4444) else Color(0xFF22C55E),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
