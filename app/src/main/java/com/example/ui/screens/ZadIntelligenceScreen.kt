package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.viewmodels.AiChatMessage
import com.example.data.ZadTransaction
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.AiInsight

// ════════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ════════════════════════════════════════════════════════════════

@Composable
fun ZadIntelligenceScreen(
    viewModel: ZadViewModel,
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
    }

    val brainReport by viewModel.brainReport.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        IntelligenceTopBar(onOpenDrawer)

        val tabs = listOf(
            Icons.Default.Psychology to "التحليلات",
            Icons.Default.BarChart to "الاشتراكات",
            Icons.Default.Chat to "شات زاد"
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
                    report = brainReport
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
    report: com.example.data.ZadCentralBrain.BrainReport? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val expenses = transactions.filter { it.isExpense }
    val income = transactions.filter { !it.isExpense }
    val totalExpense = expenses.sumOf { it.amount }
    val totalIncome = income.sumOf { it.amount }

    val categoryMap = expenses
        .groupBy { it.category ?: "أخرى" }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    val monthlyData = computeMonthlyData(transactions)
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

        // إحصائيات سريعة
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "المصروفات",
                    value = com.example.data.CurrencyFormatter.format(context, totalExpense),
                    icon = Icons.Default.TrendingDown,
                    iconColor = Color(0xFFEF4444),
                    bgColor = Color(0xFFFFF5F5)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "الدخل",
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
                    label = "المخزون",
                    value = "${inventory.size} صنف",
                    icon = Icons.Default.Inventory2,
                    iconColor = Color(0xFF8B5CF6),
                    bgColor = Color(0xFFF5F3FF)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "اشتراكات نشطة",
                    value = "${subscriptions.count { it.isActive }}",
                    icon = Icons.Default.Subscriptions,
                    iconColor = Color(0xFF0891B2),
                    bgColor = Color(0xFFECFEFF)
                )
            }
        }

        // توزيع المصروفات
        item { ExpenseDonutCard(categoryMap = categoryMap, total = totalExpense) }

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

        // الأنماط السلوكية
        if (patterns.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "أنماط سلوكية مكتشفة",
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
                        Text("المتوسط: ${com.example.data.CurrencyFormatter.format(context, pattern.avgAmount)} | كل ${pattern.frequencyDays} يوم", style = Typography.bodySmall, color = onSurfaceVariant)
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
                        "رؤى زاد الذكية",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                }
            }
            items(insights.take(5)) { insight -> IntelligenceInsightCard(insight) }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun IntelligenceInsightCard(insight: AiInsight) {
    val (bgColor, iconColor, icon) = when (insight.type) {
        "Alert" -> Triple(Color(0xFFFFF5F5), Color(0xFFEF4444), Icons.Default.Warning)
        "Tip" -> Triple(Color(0xFFF0FDF4), Color(0xFF16A34A), Icons.Default.Lightbulb)
        "Warning" -> Triple(Color(0xFFFFFBEB), Color(0xFFF59E0B), Icons.Default.WarningAmber)
        else -> Triple(primaryContainer, primary, Icons.Default.Info)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(insight.title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(insight.description, style = Typography.bodySmall, color = onSurfaceVariant)
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
                    Text("إجمالي الاشتراكات الشهرية", style = Typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(com.example.data.CurrencyFormatter.format(context, totalMonthly), style = Typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الاشتراكات", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Row {
                    FilterChip(
                        selected = !showInactive,
                        onClick = { showInactive = false },
                        label = { Text("النشطة") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = showInactive,
                        onClick = { showInactive = true },
                        label = { Text("الكل") },
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
                    Text("لا توجد اشتراكات ${if (showInactive) "" else "نشطة"}", style = Typography.titleMedium, color = onSurfaceVariant)
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
            Icon(Icons.Default.Add, contentDescription = "إضافة")
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
                            Text("ملغى", style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                if (daysLeft != null) {
                    Text(
                        when {
                            daysLeft == 0 -> "يُجدد اليوم"
                            daysLeft < 0 -> "منتهي منذ ${-daysLeft} يوم"
                            else -> "يُجدد بعد $daysLeft يوم"
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
                    contentDescription = if (sub.isActive) "إلغاء" else "تفعيل",
                    tint = if (sub.isActive) warningColor else successColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = dangerColor, modifier = Modifier.size(18.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("توزيع المصروفات", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (categoryMap.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد معاملات بعد", color = onSurfaceVariant)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                        ZadDonutChart(
                            segments = categoryMap.map { it.second.toFloat() },
                            colors = colors.take(categoryMap.size),
                            modifier = Modifier.size(160.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الإجمالي", style = Typography.labelSmall, color = onSurfaceVariant)
                            Text(com.example.data.CurrencyFormatter.formatNumber(context, total), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                            Text(com.example.data.CurrencyFormatter.symbol(context), style = Typography.labelSmall, color = onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryMap.take(5).forEachIndexed { i, (cat, amount) ->
                            val pct = if (total > 0) (amount / total * 100).toInt() else 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.getOrElse(i) { primary }))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(cat, style = Typography.labelSmall, color = onSurface)
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
fun ZadDonutChart(segments: List<Float>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = segments.sum()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donut"
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 28.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        segments.forEachIndexed { i, value ->
            val sweep = (value / total) * 360f * animatedProgress
            drawArc(
                color = colors.getOrElse(i) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweep - 2f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweep
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
                    Text("الإنفاق الشهري", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("توقع: ${com.example.data.CurrencyFormatter.format(context, predictedNextMonth)}", style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = maxOf(monthlyData.maxOfOrNull { it.second } ?: 1.0, predictedNextMonth, 1.0)
            val allData = monthlyData + Pair("توقع", predictedNextMonth)

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
                Text("فعلي", style = Typography.labelSmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFACC15)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("توقع زاد", style = Typography.labelSmall, color = onSurfaceVariant)
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
                Text("توقعات ذكاء زاد", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("الشهر القادم", style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text(com.example.data.CurrencyFormatter.format(context, predictedAmount), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (isUp) "↑ زيادة ${com.example.data.CurrencyFormatter.format(context, diff)}" else "↓ توفير ${com.example.data.CurrencyFormatter.format(context, -diff)}",
                        style = Typography.labelSmall,
                        color = if (isUp) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المخزون", style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text("$lowStockCount ناقص", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (subscriptionsCount > 0) "$subscriptionsCount اشتراك نشط" else "لا اشتراكات",
                        style = Typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
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
        Icons.Default.Restaurant to "اقترح وصفة من الثلاجة",
        Icons.Default.BarChart to "حلل مصاريفي هذا الشهر",
        Icons.Default.ShoppingCart to "ما الذي ينقصني للتسوق؟",
        Icons.Default.MonetizationOn to "كيف أوفر أكثر هذا الشهر؟",
        Icons.Default.Assignment to "اكتشف اشتراكاتي",
        Icons.Default.Eco to "وجبة صحية سريعة"
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
                                "اسأل زاد عن أي شيء",
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
                placeholder = { Text("اسأل زاد عن ثلاجتك أو ميزانيتك...", color = onSurfaceVariant, style = Typography.bodySmall) },
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
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = Color.White)
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
                    Text("زاد", fontSize = 10.sp, color = primary, fontWeight = FontWeight.Bold)
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
                Text("ذكاء زاد", style = Typography.headlineMedium, fontWeight = FontWeight.Bold, color = primary)
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

fun computeMonthlyData(transactions: List<ZadTransaction>): List<Pair<String, Double>> {
    if (transactions.isEmpty()) return emptyList()
    val monthNames = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")

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
                Text("قوة الصرف", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
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
                    Text("${com.example.data.CurrencyFormatter.symbol(context)} / يوم بأمان", style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${power.daysLeftInMonth}", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Text("يوم متبقي", style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(com.example.data.CurrencyFormatter.format(context, power.currentDailyAvg), style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (power.currentDailyAvg > power.dailySafeSpend && power.dailySafeSpend > 0) Color(0xFFEF4444) else onSurface)
                    Text("معدلك الفعلي/يوم", style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${power.powerPct}%", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = gaugeColor)
                    Text("من الميزانية باقي", style = Typography.labelSmall, color = onSurfaceVariant)
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
                Text("مقارنة بالشهر الماضي", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
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
                Triple("هذا الشهر", mc.thisMonthSpent, primary),
                Triple("الشهر الماضي (نفس الفترة)", mc.lastMonthSpent, onSurfaceVariant.copy(alpha = 0.45f))
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
                Text("أكبر التغييرات:", style = Typography.labelMedium, fontWeight = FontWeight.SemiBold, color = onSurfaceVariant)
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
                Text("زاد يعرفك 🧠", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
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

            factRow("📅", "أكثر يوم تصرف فيه: ${bp.topSpendingDay} (متوسط ${com.example.data.CurrencyFormatter.format(context, bp.topSpendingDayAvg)} للمعاملة)")
            if (bp.weekendSharePct >= 30)
                factRow("🎉", "${bp.weekendSharePct}% من صرفك في الويكند — خطط لطلعاتك مسبقاً توفر أكثر")
            else
                factRow("🧘", "صرفك متوزن خلال الأسبوع (الويكند ${bp.weekendSharePct}% فقط)")
            factRow("💳", "متوسط معاملتك: ${com.example.data.CurrencyFormatter.format(context, bp.avgTransaction)} • أكبر مصروف: ${bp.biggestExpenseTitle} (${com.example.data.CurrencyFormatter.format(context, bp.biggestExpenseAmount)})")
            if (bp.impulsePurchases >= 2)
                factRow("🛍️", "${bp.impulsePurchases} مشتريات اندفاعية آخر 30 يوم — جرب قاعدة الـ24 ساعة قبل الشراء الكبير")
            if (bp.eveningSharePct >= 50)
                factRow("🌙", "${bp.eveningSharePct}% من صرفك بعد 6 مساءً — وقت المطاعم والتوصيل غالباً")
        }
    }
}

@Composable
private fun ExportReportButton(report: com.example.data.ZadCentralBrain.BrainReport) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val text = com.example.data.ZadCentralBrain.buildExportText(context, report)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "تقرير زاد المالي")
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            try {
                context.startActivity(android.content.Intent.createChooser(intent, "مشاركة تقرير زاد"))
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
            Text("تصدير التقرير الشهري", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
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
                    "الصحة المالية",
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
                    "صرفت ${com.example.data.CurrencyFormatter.format(context, report.totalSpent)} • متبقي ${com.example.data.CurrencyFormatter.format(context, report.remaining)}",
                    style = Typography.bodySmall,
                    color = onSurfaceVariant
                )
                if (report.subscriptionsMonthlyCost > 0) {
                    Text(
                        "اشتراكات: ${com.example.data.CurrencyFormatter.format(context, report.subscriptionsMonthlyCost)}/شهر",
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
                    "ملاحظات زاد",
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
                    "تنبؤات النفاد — من تعلم استهلاكك",
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
                                f.predictedDaysLeft <= 0 -> "خلص غالباً!"
                                f.predictedDaysLeft == 1 -> "يوم واحد"
                                else -> "${f.predictedDaysLeft} أيام"
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
