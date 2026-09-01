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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.components.ZadBezierSpendChart
import com.example.ui.components.ZadStatTile
import com.example.ui.components.ZadLottieAsset
import com.airbnb.lottie.compose.LottieConstants
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.viewmodels.AiChatMessage
import com.example.data.SupabaseRepo
import com.example.data.ZadTransaction
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.AiInsight
import com.example.data.ZadAiRepository
import com.example.ads.RewardedBrainAdManager
import com.example.ui.components.ZadSmartBotAgent
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.ui.components.ZadBotEmotion
import com.example.voice.ZadCutePetSoundFx
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════
//  MAIN SCREEN — single-scroll analytics dashboard (Apple-style).
//  Replaces the old 3-tab layout (نظرة عامة / السلوك والتوقعات / أدوات ومحادثة):
//  same underlying cards and calc functions, reorganized into one scroll with
//  clear hierarchy — executive health at top, charts/radar in the middle, AI
//  projections + smart tools + chat at the bottom — instead of forcing the user
//  to switch tabs to compare related numbers.
// ════════════════════════════════════════════════════════════════

@Composable
fun ZadIntelligenceScreen(
    viewModel: ZadViewModel,
    familyViewModel: com.example.ui.viewmodels.FamilyViewModel,
    onNavigateToFamily: () -> Unit = {},
    onNavigateToStatementImport: () -> Unit = {},
    onNavigateToKnowledgeMap: () -> Unit = {}
) {
    val context = LocalContext.current
    val otherCategoryLabel = stringResource(R.string.other_category)

    val transactions by viewModel.transactions.collectAsState()
    val budgetState by viewModel.budgetState.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val messages by viewModel.aiChatMessages.collectAsState()
    val isTyping by viewModel.isAiTyping.collectAsState()
    val lastSpecialist by viewModel.lastActiveSpecialist.collectAsState()
    val patterns by viewModel.behaviorPatterns.collectAsState()
    val serverBehaviorProfile by viewModel.behaviorProfile.collectAsState()
    val isRefreshingBehaviorProfile by viewModel.isRefreshingBehaviorProfile.collectAsState()
    // predictNextMonthExpenses() below fires this AI call every time the screen opens.
    val expensePrediction by viewModel.expensePrediction.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val brainReport by viewModel.brainReport.collectAsState()
    val brainReportError by viewModel.brainReportError.collectAsState()
    val emergencyFund by viewModel.emergencyFund.collectAsState()
    val resilienceAvailableFigure by viewModel.availableFigure.collectAsState()
    val resilienceRemainingBalance by viewModel.remainingBalance.collectAsState()
    val weeklyAdherencePercent by viewModel.weeklyAdherencePercent.collectAsState()
    val companionState by viewModel.companionState.collectAsState()
    val pendingAgentProposals by viewModel.pendingAgentProposals.collectAsState()
    val familyState by familyViewModel.state.collectAsState()
    // لوحة الشركة الحية — بيانات من الـ ViewModel مباشرة (نفس مصدر الشاشات التانية)
    val pharmacyItems by viewModel.pharmacyItems.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var chatExpanded by remember { mutableStateOf(false) }
    var showSubscriptionPaywall by remember { mutableStateOf(false) }
    var showFamilyNeuralSheet by remember { mutableStateOf(false) }
    var showExecutiveDossier by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // ── بوابة عقل زاد بالإعلان (خطة الإعلانات الذكية) ──
    // مستخدم مجاني → دخول الشاشة محتاج إعلان واحد يفتحها 24 ساعة.
    // المشترك مدفوع، أو جلسة العقل (3 إعلانات) شغالة → مرور مباشر.
    var brainGateActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // زامن السيرفر الأول: لو الجلسة انتهت على السيرفر أو اتجددت من جهاز تاني/
        // تليجرام، الحكم المحلي لوحده كان بيرجّع البوابة رغم الموافقة (تكرار الإعلان)
        // أو يفتحها وجلسة السيرفر منتهية (فالعقل يحسب الرسائل على الحصة).
        RewardedBrainAdManager.syncServerState(context)
        val unlocked = RewardedBrainAdManager.isSessionUnlocked(context) ||
            try { SupabaseRepo.getEntitlementState()?.brainSessionActive == true } catch (_: Exception) { false }
        brainGateActive = !unlocked
    }
    if (brainGateActive) {
        ZadBrainAdGate(
            onUnlocked = { brainGateActive = false },
            onSubscribe = { showSubscriptionPaywall = true }
        )
        return
    }

    if (showSubscriptionPaywall) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSubscriptionPaywall = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ZadSubscriptionPaywallScreen(
                viewModel = viewModel,
                onBack = { showSubscriptionPaywall = false }
            )
        }
    }

    if (showFamilyNeuralSheet) {
        FamilyNeuralReportBottomSheet(
            familyState = familyState,
            onDismiss = { showFamilyNeuralSheet = false },
            onNavigateToFamily = onNavigateToFamily
        )
    }

    // نص جاهز جاي من شاشة تانية (مثلاً زر "إضافة ذكية بالشات 💬" في الصيدلية) — يتقرا
    // مرة واحدة بس ويتحط في صندوق الشات، ومفتوح مباشرة عشان المستخدم يشوفه ويبعته.
    LaunchedEffect(Unit) {
        viewModel.consumeChatPrefill()?.let {
            inputText = it
            chatExpanded = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // تشغيل تحليل شامل عند فتح الشاشة — detectSubscriptions() بقى مسؤولية
    // SubscriptionsScreen بس (تاسك ٦: الاشتراكات بقت شاشة مستقلة برا عقل زاد).
    LaunchedEffect(Unit) {
        viewModel.refreshAgentSummary()
        viewModel.predictNextMonthExpenses()
        viewModel.generateBrainReport()
        viewModel.loadBehaviorProfile()
        viewModel.refreshBehaviorProfile()
    }

    // نفس بق دونات الفئات القديم: كان بيجمع كل الوقت بينما الكروت فوقيه (totalExpense) بقت
    // بحدود الدورة — نفس حدود _cycleStart/_cycleEnd اللي الهوم/البادجت/الشات بيستخدموها.
    val cycleStart by viewModel.cycleStart.collectAsState()
    val cycleEnd by viewModel.cycleEnd.collectAsState()
    val totalExpense by viewModel.spentThisCycle.collectAsState()
    val totalIncome by viewModel.incomeThisCycle.collectAsState()
    val categoryMap = transactions.filter { it.txnKind == "expense" }
        .filter { tx -> com.example.data.BudgetMath.txDate(tx)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true }
        .groupBy { it.category ?: otherCategoryLabel }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    val monthlyData = computeMonthlyData(transactions, context)
    // All forecast surfaces use the same ViewModel result and confidence gate. Do not
    // substitute a second weighted-average implementation when the forecast is unavailable.
    val forecast = expensePrediction?.takeIf { it.predictedTotal > 0.0 && it.confidence > 0.0 }
    val lowStockCount = inventory.count { it.quantity <= (it.lowStockThreshold ?: 2) }
    val topExpenseCategories = transactions.filter { it.txnKind == "expense" }
        .groupBy { it.category ?: otherCategoryLabel }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }
        .map { it.first }
        .take(5)

    // رؤى زاد الذكية — ملاحظات التقرير النصية مدموجة مع الرؤى القابلة للتنفيذ في قايمة واحدة.
    val brainNotes = brainReport?.insights.orEmpty().map { note ->
        AiInsight(title = "ملاحظة من عقل زاد", description = note, type = "Tip")
    }
    val allInsights = brainNotes + insights

    if (showExecutiveDossier) {
        val activeFamily = familyState as? com.example.ui.viewmodels.FamilyState.Active
        com.example.ui.components.ZadExecutiveDossierSheet(
            onDismiss = { showExecutiveDossier = false },
            totalSpent = totalExpense,
            safeDailySpend = resilienceAvailableFigure?.value?.let { if (resilienceRemainingBalance != null) it / 14 else 120.0 } ?: 120.0,
            forecastNextMonth = forecast?.predictedTotal ?: (totalExpense * 1.08),
            familyMembersCount = activeFamily?.members?.size?.coerceAtLeast(1) ?: 1,
            pharmacyAdherencePct = weeklyAdherencePercent,
            lowStockItemCount = lowStockCount
        )
    }

    // Transparent, not `background`: MainScreen paints the mockup's canvas gradient
    // behind every screen. A white fill here is what made this screen's white cards
    // read as flat dead blocks (white card on white page, shadow invisible).
    Column(modifier = Modifier.fillMaxSize()) {
        ActiveSosBanner(familyViewModel = familyViewModel, onOpenFamilyChat = onNavigateToFamily)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 0. بطل عقل زاد ثلاثي الأبعاد والعيون الحية (3D SmartBot Living Orb) ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryContainer,
                                    primaryContainer.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 3D Living Orb with Interactive Tap
                    ZadSmartBotAgent(
                        sizeDp = 96.dp,
                        emotion = if (isTyping) ZadBotEmotion.THINKING else ZadBotEmotion.IDLE,
                        onClick = {
                            chatExpanded = true
                        }
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "عقل زاد الذكي",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isTyping) "زاد يحلل بياناتك الآن 🧠✨" else "متصل ومستعد لمساعدتك في إدارتك المالية والمنزلية ⚡",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Quick contextual suggestion chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickPrompts = listOf(
                            "حلل مصاريفي 📊",
                            "توقع مصاريف الشهر القادم 🔮",
                            "اقترح وجبة للغداء 🍲",
                            "هل وضعي المالي آمن؟ 💰"
                        )
                        quickPrompts.forEach { prompt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(onPrimaryContainer.copy(alpha = 0.12f))
                                    .clickable {
                                        ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                                        inputText = prompt
                                        chatExpanded = true
                                        viewModel.sendAiChatMessage(prompt)
                                    }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // ── 1. منحنى نبض الإنفاق الحي (Live Spending Bezier Sparkline) ──
            item {
                val weeklySpendData = remember(transactions) {
                    val arabicDays = listOf("أحد", "إثن", "ثلا", "أرب", "خمس", "جمع", "سبت")
                    val nowDay = java.time.LocalDate.now()
                    (6 downTo 0).map { daysAgo ->
                        val date = nowDay.minusDays(daysAgo.toLong())
                        val datePrefix = date.toString()
                        val dayOfWeekIndex = (date.dayOfWeek.value % 7)
                        val dayLabel = arabicDays.getOrElse(dayOfWeekIndex) { "يوم" }
                        val sum = transactions
                            .asSequence()
                            .filter { it.isExpense && it.createdAt?.startsWith(datePrefix) == true }
                            .sumOf { it.amount }
                        dayLabel to sum
                    }
                }
                ZadBezierSpendChart(
                    weeklySpend = weeklySpendData
                )
            }

            // ── 2. شبكة إحصائيات الميزانية الفاخرة (2x2 Stat Tiles) ──
            item {
                val last7 = remember(transactions) {
                    val now = java.time.LocalDate.now()
                    val sevenDaysAgo = now.minusDays(7).toString()
                    transactions.filter { it.isExpense && it.createdAt != null && it.createdAt >= sevenDaysAgo }.sumOf { it.amount }
                }
                val prev7 = remember(transactions) {
                    val now = java.time.LocalDate.now()
                    val sevenDaysAgo = now.minusDays(7).toString()
                    val fourteenDaysAgo = now.minusDays(14).toString()
                    transactions.filter { it.isExpense && it.createdAt != null && it.createdAt >= fourteenDaysAgo && it.createdAt < sevenDaysAgo }.sumOf { it.amount }
                }
                val deltaPct = if (prev7 > 0.0) ((last7 - prev7) / prev7 * 100).toInt() else null

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZadStatTile(
                            modifier = Modifier.weight(1f),
                            label = "قوة الإنفاق",
                            value = brainReport?.spendingPower?.powerPct?.let { "$it%" } ?: "—",
                            emoji = "⚡",
                            iconBg = Color(0xFFFEF3C7)
                        )
                        ZadStatTile(
                            modifier = Modifier.weight(1f),
                            label = "الصحة المالية",
                            value = brainReport?.healthScore?.let { "$it/100" } ?: "—",
                            emoji = "🛡️",
                            iconBg = Color(0xFFE0F2FE)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZadStatTile(
                            modifier = Modifier.weight(1f),
                            label = "صرف الشهر",
                            value = com.example.data.CurrencyFormatter.format(context, totalExpense),
                            emoji = "💳",
                            iconBg = Color(0xFFE6F4EC)
                        )
                        ZadStatTile(
                            modifier = Modifier.weight(1f),
                            label = "اتجاه 7 أيام",
                            value = when {
                                deltaPct == null -> "—"
                                deltaPct > 0 -> "↑ $deltaPct%"
                                deltaPct < 0 -> "↓ ${-deltaPct}%"
                                else -> "0%"
                            },
                            emoji = "📈",
                            iconBg = Color(0xFFF3E8FF)
                        )
                    }
                }
            }

            // لوحة الشركة الحية — أول ما العميل يدخل يشوف الوكلاء شغالين بأرقام حقيقية
            item { NeuralMeshLivePanelItem(transactions, inventory, pharmacyItems, subscriptions) }

            // ── العقل الثاني: الكرة العصبية المجسمة ثلاثية الأبعاد (3D Holographic Neural Sphere) ──
            item {
                com.example.ui.components.Zad3DNeuralSphereWidget(
                    onNodeClick = { onNavigateToKnowledgeMap() },
                    onViewFullMapClick = { onNavigateToKnowledgeMap() },
                    onOpenDossierClick = { showExecutiveDossier = true }
                )
            }

            item {
                var adWatchCount by remember { mutableStateOf(RewardedBrainAdManager.getAdWatchCount(context)) }
                var isSessionUnlocked by remember { mutableStateOf(RewardedBrainAdManager.isSessionUnlocked(context)) }

                LaunchedEffect(Unit) {
                    RewardedBrainAdManager.syncServerState(context)?.let { state ->
                        adWatchCount = state.adWatchCount
                        isSessionUnlocked = state.brainSessionActive
                    }
                }

                if (!isSessionUnlocked) {
                    var isAdLoading by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()
                    com.example.ui.components.AdEnergyBatteryCard(
                        adWatchCount = adWatchCount,
                        totalRequired = RewardedBrainAdManager.TOTAL_ADS_REQUIRED,
                        onWatchAdClick = {
                            if (isAdLoading) return@AdEnergyBatteryCard
                            isAdLoading = true
                            RewardedBrainAdManager.showRewardedEnergyAd(
                                context = context,
                                onAdWatched = { newCount, isFullyUnlocked ->
                                    isAdLoading = false
                                    adWatchCount = newCount
                                    isSessionUnlocked = isFullyUnlocked
                                },
                                onFailed = {
                                    // إعادة محاولة واحدة بعد ثانيتين — الإعلان ممكن يكون لسه بيتحمل
                                    coroutineScope.launch {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.ad_loading_retry_toast),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        kotlinx.coroutines.delay(2500)
                                        RewardedBrainAdManager.showRewardedEnergyAd(
                                            context = context,
                                            onAdWatched = { newCount, isFullyUnlocked ->
                                                isAdLoading = false
                                                adWatchCount = newCount
                                                isSessionUnlocked = isFullyUnlocked
                                            },
                                            onFailed = {
                                                isAdLoading = false
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.ad_failed_toast),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    }
                                }
                            )
                        },
                        onUpgradeClick = {
                            showSubscriptionPaywall = true
                        },
                        isLoading = isAdLoading
                    )
                } else {
                    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.ad_energy_unlocked_session),
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                            }
                            TextButton(onClick = { showSubscriptionPaywall = true }) {
                                Text(stringResource(R.string.auto_zadintelligence_74185), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ═══ شبكة عقل العائلة العصبية المشتركة (Multi-Agent Family Mesh) ═══
            item {
                FamilyNeuralMeshCard(
                    familyState = familyState,
                    onOpenReport = { showFamilyNeuralSheet = true },
                    onNavigateToFamily = onNavigateToFamily
                )
            }

            // ═══ TOP: executive financial health + daily spend velocity ═══
            item { SectionHeader(Icons.Default.Speed, stringResource(R.string.intel_section_executive)) }
            if (brainReport != null) {
                item { HealthScoreCard(brainReport!!) }
                item { DailySpendVelocityCard(brainReport!!.spendingPower) }
                item { SpendingPowerGaugeCard(brainReport!!.spendingPower) }
            } else if (brainReportError != null) {
                // كان سبينر أبدي هنا. التقرير حساب محلي بحت — لو وقع، مفيش أي سبب يخلي
                // العميل يستنى حاجة مش جاية، ولا يخلي سبع كروت تحته تختفي بصمت.
                item {
                    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = dangerColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.brain_report_failed_title),
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.brain_report_failed_subtitle),
                                style = Typography.bodySmall,
                                color = onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.generateBrainReport() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry_action))
                            }
                        }
                    }
                }
            } else {
                item {
                    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
                        com.example.ui.components.ZadLoadingState(modifier = Modifier.fillMaxWidth().height(140.dp))
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.expense_label),
                        value = com.example.data.CurrencyFormatter.format(context, totalExpense),
                        icon = Icons.Default.TrendingDown,
                        iconColor = dangerColor,
                        bgColor = dangerColor.copy(alpha = 0.08f)
                    )
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.income_label),
                        value = com.example.data.CurrencyFormatter.format(context, totalIncome),
                        icon = Icons.Default.TrendingUp,
                        iconColor = successColor,
                        bgColor = successColor.copy(alpha = 0.08f)
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
                        iconColor = catTransportIcon,
                        bgColor = catTransportBg
                    )
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.active_subscriptions_label),
                        value = "${subscriptions.count { it.isActive }}",
                        icon = Icons.Default.Subscriptions,
                        iconColor = catBillsIcon,
                        bgColor = catBillsBg
                    )
                }
            }

            // ═══ التقرير الشهري — ملخص مكتوب + نصايح، مش رقم لوحده. البيانات (المتحصّل/
            // المصروف/أعلى الفئات) نفسها المستخدمة في كروت الـ Executive فوق، فأي معاملة
            // اتسجلت يدوي أو جات من استيراد كشف حساب (StatementImportScreen) داخلة هنا
            // زي أي معاملة تانية — نفس مصدر transactions. ═══
            item { SectionHeader(Icons.Default.Assessment, stringResource(R.string.nav_reports)) }
            item {
                MonthlyReportCard(
                    transactions = transactions,
                    budget = budget,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    topCategories = categoryMap.take(5),
                    cycleStart = cycleStart,
                    onNavigateToStatementImport = onNavigateToStatementImport
                )
            }

            // ═══ MIDDLE: interactive charts + expense radar ═══
            item { SectionHeader(Icons.Default.BarChart, stringResource(R.string.intel_section_charts_radar)) }
            item { WeeklyTrendCard(transactions) }
            item { ExpenseDonutCard(categoryMap = categoryMap, total = totalExpense) }
            item { ConsumptionTickerCard(transactions) }
            brainReport?.monthComparison?.let { mc ->
                item { MonthComparisonCard(mc) }
            }
            item { InflationRadarCard(transactions) }
            item { PriceShockRadarCard(topExpenseCategories, viewModel) }
            if (detectWeekdaySpike(serverBehaviorProfile) != null) {
                item { BehavioralNudgeCard(serverBehaviorProfile) }
            }

            // ═══ BOTTOM: AI projections + smart tools ═══
            item { SectionHeader(Icons.Default.AutoAwesome, stringResource(R.string.intel_section_ai_tools)) }
            // نفس شرط HomeScreen بالظبط (توقع AI حقيقي + سقف معروف) — لو التوقع الحقيقي
            // مش جاهز لسه، الكارت المحلي (متوسط مرجّح بسيط) هو اللي بيظهر بدل ما الكارت يفضى.
            forecast?.let { prediction ->
                item {
                    PredictionCard(
                        predictedAmount = prediction.predictedTotal,
                        currentMonthAmount = monthlyData.lastOrNull()?.second ?: 0.0,
                        lowStockCount = lowStockCount,
                        subscriptionsCount = subscriptions.count { it.isActive }
                    )
                }
            }
            item { MonthlyBarChartCard(monthlyData = monthlyData, forecast = forecast) }
            // توزيع الصرف حسب الفئة (من مرجع "new ui ux" — renderAssistant):
            // كارت أبيض بعنوان "التوزيع حسب الفئة" وبارات ملوّنة لكل فئة.
            budgetState?.byCategory?.takeIf { it.isNotEmpty() }?.let { byCategory ->
                item { CategoryBreakdownCard(byCategory) }
            }
            item {
                FinancialStressTestCard(
                    transactions,
                    emergencyFund,
                    availableBalance = resilienceAvailableFigure?.value ?: resilienceRemainingBalance ?: 0.0,
                    onUpdateEmergencyFund = { viewModel.updateEmergencyFund(it) },
                )
            }
            brainReport?.depletionForecasts?.takeIf { it.isNotEmpty() }?.let { forecasts ->
                item { DepletionForecastCard(forecasts) }
            }
            item { SmartBuyingTimingCard(inventory, serverBehaviorProfile) }
            if (patterns.isNotEmpty()) {
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
            forecast?.let { item { WhatIfSimulatorCard(viewModel = viewModel, predictedMonthlySpend = it.predictedTotal) } }
            item {
                ServerBehaviorProfileCard(
                    profile = serverBehaviorProfile,
                    isRefreshing = isRefreshingBehaviorProfile,
                    onRefresh = { viewModel.refreshBehaviorProfile() }
                )
            }
            brainReport?.behaviorProfile?.let { bp ->
                item { BehaviorAnalysisCard(bp) }
            }
            // كان القسم كله (العنوان كمان) بيختفي تماماً لما allInsights فاضية — ميزة
            // اسمها "رؤى زاد الذكية" بتختفي بدل ما تقول "لسه مفيش" كانت بتقرا كأنها مش
            // موجودة أصلاً، مش كأنها لسه محتاجة بيانات أكتر.
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
            if (allInsights.isNotEmpty()) {
                items(allInsights.take(8)) { insight -> IntelligenceInsightCard(insight, viewModel) }
            } else {
                item {
                    com.example.ui.components.ZadEmptyState(
                        icon = Icons.Default.Lightbulb,
                        title = stringResource(R.string.no_insights_yet_title),
                        subtitle = stringResource(R.string.no_insights_yet_subtitle),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            }
            item { TasbihaSummaryCard(familyViewModel) }
            item { AmazonPicksSummaryCard(viewModel) }
            brainReport?.let { report ->
                item { ExportReportButton(report) }
            }

            item {
                ChatSectionCard(
                    expanded = chatExpanded,
                    onToggle = { chatExpanded = !chatExpanded },
                    messages = messages,
                    isTyping = isTyping,
                    companionState = companionState,
                    inputText = inputText,
                    listState = listState,
                    onInputChange = { inputText = it },
                    onSendText = { text, voiceMode ->
                        if (text.isNotBlank()) {
                            viewModel.sendAiChatMessage(text, voiceMode = voiceMode)
                            inputText = ""
                        }
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendAiChatMessage(inputText)
                            inputText = ""
                        }
                    },
                    onClearChat = { viewModel.clearChatHistory() },
                    onUndoCommit = { viewModel.undoInventoryCommit(it) },
                    pendingAgentProposals = pendingAgentProposals,
                    onConfirmAgentProposals = { viewModel.confirmPendingAgentProposals() },
                    onCancelAgentProposals = { viewModel.cancelPendingAgentProposals() },
                    lastSpecialist = lastSpecialist
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

/**
 * لوحة الوكلاء الحية — معزولة في composable مستقل عشان النصوص العربية الطويلة
 * جوه string templates ماتكسرش الـ parser في الـ LazyColumn الكبير.
 */
@Composable
private fun NeuralMeshLivePanelItem(
    transactions: List<ZadTransaction>,
    inventory: List<com.example.data.ZadInventory>,
    pharmacyItems: List<com.example.data.ZadPharmacyItem>,
    subscriptions: List<com.example.data.ZadSubscription>,
) {
    val todayDoses = pharmacyItems.sumOf { it.dailyDoseCount }
    val stats = listOf(
        com.example.ui.components.AgentLiveStat(
            "وكيل المال",
            "يراقب " + transactions.size + " معاملة (آخر 30 يوم)",
            transactions.isNotEmpty()
        ),
        com.example.ui.components.AgentLiveStat(
            "وكيل المخزون والمطبخ",
            "يتتبع " + inventory.size + " منتج",
            inventory.isNotEmpty()
        ),
        com.example.ui.components.AgentLiveStat(
            "وكيل الصيدلية",
            if (todayDoses > 0) todayDoses.toString() + " جرعة مجدولة اليوم" else "لا توجد أدوية مجدولة",
            todayDoses > 0
        ),
        com.example.ui.components.AgentLiveStat(
            "محلل الاستهلاك",
            if (subscriptions.isNotEmpty()) subscriptions.size.toString() + " اشتراك تحت المراقبة" else "لا اشتراكات بعد",
            subscriptions.isNotEmpty()
        ),
    )
    com.example.ui.components.NeuralMeshLivePanel(stats = stats)
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = primary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
    }
}

/**
 * تاسك ٧ — نداء الطوارئ (SosBubble) أصلاً رسالة شات عادية جوه FamilyScreen، مش overlay
 * ثابت منفصل زي ما كان متصوَّر. الإضافة هنا: كارت حالة صغير في أعلى عقل زاد يظهر لو
 * فيه نداء طوارئ "نشط" (آخر رسالة SOS في آخر ساعتين، مفيش resolved flag في الـ schema
 * فالحداثة الزمنية هي المؤشر البديل)، بيودي المستخدم لشات العائلة عند الضغط — إضافة
 * جنب النداء الأصلي، مش بدل منه.
 */
@Composable
private fun ActiveSosBanner(familyViewModel: com.example.ui.viewmodels.FamilyViewModel, onOpenFamilyChat: () -> Unit) {
    val familyState by familyViewModel.state.collectAsState()
    val active = familyState as? com.example.ui.viewmodels.FamilyState.Active ?: return
    val latestSos = active.messages.lastOrNull { it.messageType == "SOS" } ?: return

    val isRecent = try {
        java.time.Instant.parse(latestSos.createdAt ?: "")
            .isAfter(java.time.Instant.now().minusSeconds(2 * 3600))
    } catch (e: Exception) { false }
    if (!isRecent) return

    val senderAlias = active.members.find { it.id == latestSos.senderId }?.alias ?: return

    Surface(
        onClick = onOpenFamilyChat,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = dangerColor.copy(alpha = 0.12f)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                stringResource(R.string.sos_call_from, senderAlias),
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = dangerColor,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.open_chat), tint = dangerColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun IntelligenceInsightCard(insight: AiInsight, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val (bgColor, iconColor, icon) = when (insight.type) {
        "Alert" -> Triple(dangerColor.copy(alpha = 0.08f), dangerColor, Icons.Default.Warning)
        "Tip" -> Triple(successColor.copy(alpha = 0.08f), successColor, Icons.Default.Lightbulb)
        "Warning" -> Triple(secondary.copy(alpha = 0.08f), secondary, Icons.Default.WarningAmber)
        else -> Triple(primaryContainer, primary, Icons.Default.Info)
    }
    var actionDone by remember(insight.actionRefId, insight.actionType) { mutableStateOf(false) }
    var showCancelConfirm by remember(insight.actionRefId, insight.actionType) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bgColor).padding(16.dp)
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
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).pressableScale()
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
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).pressableScale()
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

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
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

// ── Donut Chart Card ─────────────────────────────────────────────────────────
@Composable
fun ExpenseDonutCard(categoryMap: List<Pair<String, Double>>, total: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = listOf(
        Color(0xFF16A34A), secondary, Color(0xFF3B82F6),
        dangerColor, Color(0xFF8B5CF6), Color(0xFF06B6D4)
    )
    // اختيار فئة (بالضغط على القوس نفسه أو على سطرها في القايمة) يبدّل مركز
    // الدونات من "الإجمالي" لتفاصيل الفئة دي — عرض أمرن بدل رقم إجمالي ثابت.
    var selectedIndex by remember(categoryMap) { mutableStateOf<Int?>(null) }

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.expense_distribution), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (categoryMap.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.PieChart,
                    title = stringResource(R.string.no_transactions),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
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
    // Was animateFloatAsState(targetValue = 1f): a constant target means the value starts
    // AT the target, so the 1200ms sweep in this file has never once played — the donut
    // has always appeared fully drawn. Keyed on the segments so it also re-sweeps when the
    // category split changes, not only on first entry.
    val animatedProgress by com.example.ui.components.drawProgressOnEntry(segments, durationMs = 1200)
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
                color = colors.getOrElse(i) { onSurfaceVariant }.copy(alpha = if (isDimmed) 0.3f else 1f),
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

// ── Daily Spend Velocity — how fast today's spend rate runs vs the safe daily cap ──
@Composable
fun DailySpendVelocityCard(power: com.example.data.ZadCentralBrain.SpendingPower?) {
    val context = LocalContext.current

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.daily_velocity_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.daily_velocity_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (power == null) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.daily_velocity_no_budget),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                val safePerDay = power.dailySafeSpend
                val actual = power.currentDailyAvg
                val ratio = if (safePerDay != null && safePerDay > 0) (actual / safePerDay).toFloat() else null
                val gaugeColor = when {
                    ratio == null -> onSurfaceVariant.copy(alpha = 0.35f)
                    ratio <= 0.8f -> successColor
                    ratio <= 1.0f -> secondary
                    else -> dangerColor
                }
                val animatedRatio by animateFloatAsState(
                    targetValue = (ratio ?: 0f).coerceIn(0f, 1.5f),
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "velocity"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidthPx = 12.dp.toPx()
                            val startAngle = 150f
                            val sweepMax = 240f
                            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                            val topLeftOffset = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
                            drawArc(
                                color = gaugeColor.copy(alpha = 0.15f),
                                startAngle = startAngle,
                                sweepAngle = sweepMax,
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = arcSize,
                                style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                            )
                            if (ratio != null) {
                                drawArc(
                                    color = gaugeColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweepMax * (animatedRatio / 1.5f).coerceIn(0f, 1f),
                                    useCenter = false,
                                    topLeft = topLeftOffset,
                                    size = arcSize,
                                    style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                ratio?.let { "${(it * 100).toInt()}%" } ?: "—",
                                style = Typography.titleLarge, fontWeight = FontWeight.Black, color = gaugeColor
                            )
                            Text(stringResource(R.string.daily_velocity_pct_suffix), style = Typography.labelSmall, color = onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(stringResource(R.string.actual_daily_rate), style = Typography.labelSmall, color = onSurfaceVariant)
                            Text(com.example.data.CurrencyFormatter.format(context, actual), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        }
                        Column {
                            Text(
                                stringResource(R.string.safe_per_day_suffix, com.example.data.CurrencyFormatter.symbol(context)),
                                style = Typography.labelSmall, color = onSurfaceVariant
                            )
                            Text(
                                safePerDay?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: stringResource(R.string.budget_unknown_value),
                                style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface
                            )
                        }
                        if (ratio != null && ratio > 1.0f) {
                            Text(
                                stringResource(R.string.daily_velocity_ahead_pct, ((ratio - 1f) * 100).toInt()),
                                style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = dangerColor
                            )
                        } else if (ratio != null) {
                            Text(stringResource(R.string.daily_velocity_safe), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = successColor)
                        }
                    }
                }
            }
        }
    }
}

// ── Weekly Trend Chart — Canvas grouped bars, this week vs previous week ──────
@Composable
fun WeeklyTrendCard(transactions: List<ZadTransaction>) {
    val context = LocalContext.current
    val today = java.time.LocalDate.now()
    val lastWeekEnd = today.minusDays(7)

    val currentWeek = remember(transactions) { computeDailySpendData(transactions, days = 7) }
    val lastWeek = remember(transactions) { computeDailySpendData(transactions, days = 7, endDate = lastWeekEnd) }
    val currentTotal = currentWeek.sumOf { it.second }
    val lastTotal = lastWeek.sumOf { it.second }
    val hasData = currentTotal > 0.0 || lastTotal > 0.0
    val changePct = if (lastTotal > 0.0) ((currentTotal - lastTotal) / lastTotal) * 100.0 else if (currentTotal > 0.0) 100.0 else 0.0
    val trendColor = if (changePct > 0.5) dangerColor else if (changePct < -0.5) successColor else onSurfaceVariant
    val drawProgress by animateFloatAsState(targetValue = if (hasData) 1f else 0f, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "weekly_trend")

    val shortDay = mapOf(
        java.time.DayOfWeek.SATURDAY to "سبت", java.time.DayOfWeek.SUNDAY to "أحد",
        java.time.DayOfWeek.MONDAY to "اثن", java.time.DayOfWeek.TUESDAY to "ثلا",
        java.time.DayOfWeek.WEDNESDAY to "أرب", java.time.DayOfWeek.THURSDAY to "خمي",
        java.time.DayOfWeek.FRIDAY to "جمع"
    )

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(20.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.weekly_trend_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(stringResource(R.string.weekly_trend_subtitle), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
                if (hasData) {
                    Surface(shape = RoundedCornerShape(10.dp), color = trendColor.copy(alpha = 0.12f)) {
                        Text(
                            "${if (changePct > 0) "+" else ""}${"%.0f".format(changePct)}%",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = trendColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasData) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.CalendarViewWeek,
                    title = stringResource(R.string.weekly_trend_no_data),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                val maxVal = maxOf(currentWeek.maxOfOrNull { it.second } ?: 0.0, lastWeek.maxOfOrNull { it.second } ?: 0.0, 1.0)
                Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    val groupWidth = size.width / 7
                    val barWidth = groupWidth * 0.28f
                    val gap = barWidth * 0.3f
                    for (i in 0 until 7) {
                        val cx = groupWidth * i + groupWidth / 2
                        val curVal = currentWeek.getOrNull(i)?.second ?: 0.0
                        val lastVal = lastWeek.getOrNull(i)?.second ?: 0.0
                        val curH = (((curVal / maxVal) * drawProgress).toFloat().coerceIn(0f, 1f)) * size.height
                        val lastH = (((lastVal / maxVal) * drawProgress).toFloat().coerceIn(0f, 1f)) * size.height
                        drawRoundRect(
                            color = onSurfaceVariant.copy(alpha = 0.25f),
                            topLeft = Offset(cx - barWidth - gap / 2, size.height - lastH),
                            size = Size(barWidth, lastH),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(primary, primaryDark)),
                            topLeft = Offset(cx + gap / 2, size.height - curH),
                            size = Size(barWidth, curH),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    currentWeek.forEach { (date, _) ->
                        Text(
                            shortDay[date.dayOfWeek] ?: "",
                            style = Typography.labelSmall.copy(fontSize = 9.sp),
                            color = onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(primary, primaryDark))))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.weekly_trend_this_week), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(onSurfaceVariant.copy(alpha = 0.25f)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.weekly_trend_last_week), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Monthly Bar Chart ─────────────────────────────────────────────────────────
@Composable
fun MonthlyBarChartCard(monthlyData: List<Pair<String, Double>>, forecast: com.example.data.AiExpensePrediction?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Same dead-constant animation as the donut had — see drawProgressOnEntry.
    val animatedProgress by com.example.ui.components.drawProgressOnEntry(durationMs = 1000)

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(22.dp), tint = onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.monthly_spending), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                }
                forecast?.let {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.predicted_label, com.example.data.CurrencyFormatter.format(context, it.predictedTotal)), style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (monthlyData.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.TrendingUp,
                    title = stringResource(R.string.no_transactions),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                val maxVal = maxOf(monthlyData.maxOfOrNull { it.second } ?: 1.0, forecast?.predictedTotal ?: 0.0, 1.0)
                val allData = forecast?.let { monthlyData + Pair(stringResource(R.string.prediction_bar_label), it.predictedTotal) } ?: monthlyData

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
                                        if (isPredict) Brush.verticalGradient(listOf(secondaryLight, secondary))
                                        else Brush.verticalGradient(listOf(primary, primaryContainer))
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(month.take(3), style = Typography.labelSmall.copy(fontSize = 9.sp), color = if (isPredict) secondary else onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.actual_label), style = Typography.labelSmall, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(secondaryLight))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.zad_forecast_label), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        }
    }
}

// ── Consumption Ticker — stock-exchange-style daily spend line chart ──────────
@Composable
fun ConsumptionTickerCard(transactions: List<ZadTransaction>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dailyData = remember(transactions) { computeDailySpendData(transactions, days = 30) }
    val previousPeriodTotal = remember(transactions) {
        computeDailySpendData(transactions, days = 30, endDate = java.time.LocalDate.now().minusDays(30))
            .sumOf { it.second }
    }
    val currentTotal = dailyData.sumOf { it.second }
    val hasData = currentTotal > 0.0 || previousPeriodTotal > 0.0

    val changePct = if (previousPeriodTotal > 0.0) {
        ((currentTotal - previousPeriodTotal) / previousPeriodTotal) * 100.0
    } else if (currentTotal > 0.0) 100.0 else 0.0
    // spending more than before = red (bad), spending less = green (good) — inverted vs a real stock ticker
    val trendColor = if (changePct > 0.5) dangerColor else if (changePct < -0.5) successColor else onSurfaceVariant
    val trendIcon = if (changePct > 0.5) Icons.Default.TrendingUp else if (changePct < -0.5) Icons.Default.TrendingDown else Icons.AutoMirrored.Filled.TrendingFlat

    val drawProgress by animateFloatAsState(targetValue = if (hasData) 1f else 0f, animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "ticker_draw")

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(20.dp), tint = onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.consumption_ticker_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(stringResource(R.string.consumption_ticker_period_30d), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                if (hasData) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(trendColor.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${if (changePct > 0) "+" else ""}${"%.1f".format(changePct)}%",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = trendColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                com.example.data.CurrencyFormatter.format(context, currentTotal),
                style = Typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = onSurface
            )
            Text(stringResource(R.string.consumption_ticker_vs_prev_period), style = Typography.labelSmall, color = onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasData) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.ShowChart,
                    title = stringResource(R.string.no_transactions_for_analysis),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                )
            } else {
                val maxVal = (dailyData.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
                val lineColor = trendColor
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = if (dailyData.size > 1) w / (dailyData.size - 1) else w
                    val points = dailyData.mapIndexed { i, (_, value) ->
                        Offset(i * stepX, h - ((value / maxVal) * h).toFloat().coerceIn(0f, h))
                    }
                    val visiblePointCount = (points.size * drawProgress).toInt().coerceIn(1, points.size)
                    val visiblePoints = points.take(visiblePointCount)

                    if (visiblePoints.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(visiblePoints.first().x, visiblePoints.first().y)
                            for (p in visiblePoints.drop(1)) lineTo(p.x, p.y)
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(visiblePoints.last().x, h)
                            lineTo(visiblePoints.first().x, h)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)))
                        )
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // نقطة نهاية متوهجة — إحساس "مباشر" زي شاشة الأسعار الحية
                        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = visiblePoints.last())
                        drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = visiblePoints.last())
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d/M")
                    Text(dailyData.first().first.format(dateFormatter), style = Typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVariant)
                    Text(dailyData[dailyData.size / 2].first.format(dateFormatter), style = Typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVariant)
                    Text(dailyData.last().first.format(dateFormatter), style = Typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVariant)
                }
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
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp), spotColor = primary.copy(alpha = 0.20f))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(primaryDark, primary)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = secondaryLight, modifier = Modifier.size(20.dp))
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
                        color = if (isUp) dangerColor else successColor
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

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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
enum class StressTestStatus { UNKNOWN, CRITICAL, LOW, HEALTHY }

data class StressTestResult(
    /** null = لا يمكن الحساب (مفيش معدل صرف معروف) — مختلفة تماماً عن 0 (رصيد طوارئ فاضي). */
    val coverageDays: Int?,
    val avgDailySpend: Double,
    val liquidSavings: Double,
    val targetDays: Int,
    val suggestedMonthlySaving: Double,
    val status: StressTestStatus
)

/**
 * أيام التغطية = رصيد الطوارئ ÷ متوسط الصرف اليومي. التوفير الشهري المقترح يقفل الفجوة
 * حتى الهدف على مدار 6 أشهر.
 *
 * قسمة المصروف على [windowDays] ثابتة كانت بتغلط في اتجاهين لمستخدم لسه جديد: تاريخه
 * أقصر من النافذة، فمعدله اليومي بيطلع مخفّف بنسبة (عمره ÷ 30) وأيام التغطية بتتضخم.
 * دلوقتي المقام هو المدى المرصود فعلاً (من أقدم معاملة في النافذة لحد النهارده)، بحد
 * أدنى يوم واحد.
 *
 * والأهم: لما مفيش أي مصروف مرصود، معدل الصرف = 0 والقسمة مالهاش معنى. قبل كده ده كان
 * بيرجع `coverageDays = 0` — نفس رقم "مفيش رصيد طوارئ خالص" بالظبط — فمستخدم عنده 50,000
 * جنيه كان بيتقاله "0 يوم تغطية" وحالته CRITICAL. دلوقتي بترجع null وحالة UNKNOWN،
 * والواجهة بتقول إن الرقم لسه مش محسوب بدل ما تخترع صفر.
 */
fun calculateStressTest(
    transactions: List<ZadTransaction>,
    liquidSavings: Double,
    targetDays: Int = 90,
    windowDays: Int = 30,
    savingHorizonMonths: Int = 6
): StressTestResult {
    val now = java.time.Instant.now()
    val cutoff = now.minusSeconds(windowDays * 86400L)
    val recentExpenses = transactions
        .filter { it.txnKind == "expense" }
        .mapNotNull { tx ->
            val at = try { java.time.Instant.parse(tx.createdAt ?: "") } catch (e: Exception) { null }
            if (at != null && at >= cutoff) at to tx.amount else null
        }
    val recentExpenseTotal = recentExpenses.sumOf { it.second }
    // المدى المرصود فعلاً، مش طول النافذة الاسمية.
    val observedDays = recentExpenses.minOfOrNull { it.first }
        ?.let { java.time.Duration.between(it, now).toDays() + 1 }
        ?.coerceIn(1L, windowDays.toLong())
        ?: windowDays.toLong()
    val avgDailySpend = if (recentExpenseTotal > 0) recentExpenseTotal / observedDays else 0.0

    if (avgDailySpend <= 0.0) {
        return StressTestResult(
            coverageDays = null,
            avgDailySpend = 0.0,
            liquidSavings = liquidSavings,
            targetDays = targetDays,
            suggestedMonthlySaving = 0.0,
            status = StressTestStatus.UNKNOWN
        )
    }

    val coverageDays = (liquidSavings / avgDailySpend).toInt().coerceAtLeast(0)
    val gapDays = (targetDays - coverageDays).coerceAtLeast(0)
    val suggestedMonthlySaving = if (gapDays > 0) (gapDays * avgDailySpend) / savingHorizonMonths else 0.0
    val status = when {
        coverageDays < 30 -> StressTestStatus.CRITICAL
        coverageDays < targetDays -> StressTestStatus.LOW
        else -> StressTestStatus.HEALTHY
    }
    return StressTestResult(coverageDays, avgDailySpend, liquidSavings, targetDays, suggestedMonthlySaving, status)
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
    val buckets = transactions.filter { it.txnKind == "expense" }.mapNotNull { tx ->
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
fun FinancialStressTestCard(transactions: List<ZadTransaction>, emergencyFund: Double, availableBalance: Double = 0.0, onUpdateEmergencyFund: (Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // لو المستخدم مادّيش رصيد طوارئ مخصص (الحالة الشائعة — الحقل ده نادراً ما بيتملى)، بنرجع
    // للرصيد المتاح الفعلي (zad_budget_state) بدل ما نعرض "0 يوم تغطية" لمستخدم عنده فلوس
    // فعلاً. usingFallbackBalance بيتحكم في تسمية الرقم في الواجهة تحت.
    val usingFallbackBalance = emergencyFund <= 0.0 && availableBalance > 0.0
    val effectiveSavings = if (emergencyFund > 0.0) emergencyFund else availableBalance
    val result = remember(transactions, effectiveSavings) { calculateStressTest(transactions, effectiveSavings) }
    var showEditDialog by remember { mutableStateOf(false) }
    var aiNarrative by remember(result) { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    val (statusColor, statusLabel) = when (result.status) {
        StressTestStatus.UNKNOWN -> onSurfaceVariant to stringResource(R.string.stress_test_status_unknown)
        StressTestStatus.CRITICAL -> dangerColor to stringResource(R.string.stress_test_status_critical)
        StressTestStatus.LOW -> warningColor to stringResource(R.string.stress_test_status_low)
        StressTestStatus.HEALTHY -> successColor to stringResource(R.string.stress_test_status_healthy)
    }

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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

            val coverage = result.coverageDays
            if (coverage == null) {
                // مفيش معدل صرف معروف — نقول كده صراحة بدل ما نعرض "0 يوم تغطية"، اللي كان
                // بيقرا كإفلاس لمستخدم رصيد طوارئه مليان.
                Text(stringResource(R.string.stress_test_coverage_unknown), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = statusColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.stress_test_coverage_unknown_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.stress_test_coverage_days, coverage), style = Typography.displaySmall, fontWeight = FontWeight.Bold, color = statusColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.stress_test_target_label, result.targetDays), style = Typography.labelSmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (coverage.toFloat() / result.targetDays.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        stringResource(if (usingFallbackBalance) R.string.stress_test_available_balance_label else R.string.stress_test_emergency_fund_label),
                        style = Typography.labelSmall, color = onSurfaceVariant
                    )
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

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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

    Surface(shape = RoundedCornerShape(20.dp), color = secondary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp))
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

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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

/**
 * بستان التسبيح جوه عقل زاد. النموذج الحقيقي تراكمي مدى الحياة (٥ مراحل × ٩٩ نقطة)
 * مش هدف يومي بيتصفّر — فالكارت بيعرض المستوى والتقدم للمستوى الجاي والسلسلة زي ما
 * family_tasbiha مخزّنة بالظبط، مش نسبة يومية متخترعة.
 */
@Composable
fun TasbihaSummaryCard(familyViewModel: com.example.ui.viewmodels.FamilyViewModel) {
    LaunchedEffect(Unit) { familyViewModel.loadTasbiha() }
    val tree = familyViewModel.myTasbiha ?: return

    GlassCard(shape = RoundedCornerShape(18.dp), contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.tasbiha_garden_title),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Text(tree.stageEmoji(), fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "${tree.stageName()} · ${stringResource(R.string.tasbiha_level_of, tree.level)}",
            style = Typography.bodyMedium,
            color = onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { tree.progressToNext().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = kidsPrimary,
            trackColor = kidsPrimary.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.tasbiha_total_count, tree.totalClicks),
                style = Typography.labelMedium,
                color = textSecondary
            )
            if (tree.streakDays > 0) {
                Text(
                    "🔥 " + stringResource(R.string.tasbiha_streak_days, tree.streakDays),
                    style = Typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = secondaryDark
                )
            }
        }
    }
}

/**
 * ترشيحات أمازون جوه عقل زاد. بيقرأ نفس affiliateProducts اللي الرئيسية وقائمة
 * التسوق بيعرضوا منه — مصدر واحد، عشان الشاشة والشات ما يقولوش حاجتين مختلفتين.
 */
@Composable
fun AmazonPicksSummaryCard(viewModel: ZadViewModel) {
    val products by viewModel.affiliateProducts.collectAsState()
    val active = products.filter { it.isActive }
    if (active.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current

    GlassCard(shape = RoundedCornerShape(18.dp), contentPadding = 16.dp) {
        Text(
            stringResource(R.string.amazon_picks_title),
            style = Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        active.take(3).forEach { product ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.productNameAr,
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        maxLines = 1
                    )
                    product.category?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = Typography.labelSmall, color = textTertiary)
                    }
                }
                if (product.averagePriceSar > 0) {
                    Text(
                        com.example.data.CurrencyFormatter.format(context, product.averagePriceSar),
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = secondaryDark
                    )
                }
            }
        }
    }
}

// ── Feature 5 card ─────────────────────────────────────────────────────────
// ── Shared AI narrative section (loading spinner / result box / explain button) ──
@Composable
// internal مش private — SubscriptionsScreen.kt's DebtPayoffPlannerCard بيستخدمها (تاسك ٦)
fun AiNarrativeSection(narrative: String?, isLoading: Boolean, onExplain: (() -> Unit)?) {
    when {
        isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            com.example.ui.components.CompanionOrb(
                state = com.example.ui.components.CompanionState.Focused,
                size = 20.dp,
                animated = true
            )
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
// internal مش private — SubscriptionsScreen.kt's LiveDealsCard بيستخدمها (تاسك ٦)
fun LiveSearchBadge() {
    Surface(shape = RoundedCornerShape(8.dp), color = dangerColor.copy(alpha = 0.1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dangerColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.live_search_source_badge), style = Typography.labelSmall, color = dangerColor)
        }
    }
}

// ── Price Shock Predictor card ────────────────────────────────────────────
@Composable
fun PriceShockRadarCard(categories: List<String>, viewModel: ZadViewModel) {
    val warnings by viewModel.priceShockWarnings.collectAsState()
    val fetchState by viewModel.priceShockFetchState.collectAsState()

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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
    // Mockup's overview stat card: 16dp radius, the two-layer card shadow shared with
    // every other list surface, and the small-grey-label-over-bold-value stack. The
    // icon keeps its colored chip but moved next to the label instead of sitting on
    // its own row above it, which is what made these tiles taller than the mockup's
    // and pushed the 2×2 grid off a phone screen.
    val miniStatShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .zadCardShadow(miniStatShape)
            .clip(miniStatShape)
            .background(surface)
            .pressableScale()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(bgColor), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
            }
            Text(label, style = Typography.labelSmall, color = textTertiary, maxLines = 1)
        }
        Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1)
    }
}

// ════════════════════════════════════════════════════════════════
//  CHAT — collapsible card at the bottom of the dashboard scroll
// ════════════════════════════════════════════════════════════════

@Composable
fun ChatSectionCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    messages: List<AiChatMessage>,
    isTyping: Boolean,
    companionState: com.example.ui.components.CompanionState,
    inputText: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onSendText: (String, Boolean) -> Unit,
    onSend: () -> Unit,
    onClearChat: () -> Unit,
    onUndoCommit: (String) -> Unit,
    pendingAgentProposals: List<com.example.data.ZadAiRepository.AgentProposal> = emptyList(),
    onConfirmAgentProposals: () -> Unit = {},
    onCancelAgentProposals: () -> Unit = {},
    lastSpecialist: String? = null
) {
    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.ui.components.CompanionOrb(state = companionState, size = 40.dp, animated = false)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_chat_card_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(stringResource(R.string.ai_chat_card_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse_chat_action else R.string.open_chat_action),
                    tint = onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
                    ChatTab(
                        messages = messages,
                        isTyping = isTyping,
                        companionState = companionState,
                        inputText = inputText,
                        listState = listState,
                        onInputChange = onInputChange,
                        onSendText = onSendText,
                        onSend = onSend,
                        onClearChat = onClearChat,
                        onUndoCommit = onUndoCommit,
                        pendingAgentProposals = pendingAgentProposals,
                        onConfirmAgentProposals = onConfirmAgentProposals,
                        onCancelAgentProposals = onCancelAgentProposals,
                        lastSpecialist = lastSpecialist
                    )
                }
            }
        }
    }
}

@Composable
fun ChatTab(
    messages: List<AiChatMessage>,
    isTyping: Boolean,
    companionState: com.example.ui.components.CompanionState,
    inputText: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onSendText: (String, Boolean) -> Unit,
    onSend: () -> Unit,
    onClearChat: () -> Unit = {},
    onUndoCommit: (String) -> Unit = {},
    pendingAgentProposals: List<com.example.data.ZadAiRepository.AgentProposal> = emptyList(),
    onConfirmAgentProposals: () -> Unit = {},
    onCancelAgentProposals: () -> Unit = {},
    lastSpecialist: String? = null
) {
    val quickPrompts = listOf(
        Icons.Default.Restaurant to stringResource(R.string.quick_prompt_recipe),
        Icons.Default.BarChart to stringResource(R.string.quick_prompt_analyze_spending),
        Icons.Default.ShoppingCart to stringResource(R.string.quick_prompt_shopping_missing),
        Icons.Default.MonetizationOn to stringResource(R.string.quick_prompt_save_more),
        Icons.Default.Assignment to stringResource(R.string.quick_prompt_subscriptions),
        Icons.Default.Eco to stringResource(R.string.quick_prompt_healthy_meal)
    )

    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_chat_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.clear_chat_confirm)) },
            confirmButton = {
                TextButton(onClick = { onClearChat(); confirmClear = false }) {
                    Text(stringResource(R.string.clear_chat_action), color = dangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }

    val context = LocalContext.current
    val voiceManager = remember { com.example.voice.ZadVoiceManager(context) }
    val isListening by voiceManager.isListening.collectAsState()
    var lastSpokenResponseId by remember { mutableStateOf(messages.lastOrNull { !it.isUser }?.id) }
    var awaitingVoiceReply by remember { mutableStateOf(false) }
    // الرد الصوتي التلقائي: ON افتراضياً (طلب العميل: الإيجنت يرد بصوت)، ومحفوظ بين الجلسات
    // بدل remember{} اللي كان بيمسح الاختيار مع كل خروج من الشاشة — فكان الصوت "مش شغال".
    val autoTtsEnabled = remember {
        mutableStateOf(
            context.getSharedPreferences("zad_voice", android.content.Context.MODE_PRIVATE)
                .getBoolean("auto_tts", true)
        )
    }

    fun submitVoiceQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        lastSpokenResponseId = messages.lastOrNull { !it.isUser }?.id
        awaitingVoiceReply = true
        voiceManager.markThinking()
        onInputChange("")
        onSendText(clean, true)
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(::submitVoiceQuery)
        }
    }

    fun startListeningWithPermission() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            voiceManager.startListening(::submitVoiceQuery)
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages, awaitingVoiceReply) {
        if (!awaitingVoiceReply) return@LaunchedEffect
        val lastAssistantMessage = messages.lastOrNull { !it.isUser }
        if (lastAssistantMessage != null && lastAssistantMessage.id != lastSpokenResponseId) {
            lastSpokenResponseId = lastAssistantMessage.id
            awaitingVoiceReply = false
            voiceManager.speakHumanLike(
                lastAssistantMessage.text,
                onDone = {
                    // المحادثة الحية المستمرة — يستمع تلقائياً بعد انتهاء الرد
                    startListeningWithPermission()
                },
                onFailed = {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.voice_unavailable_toast),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceManager.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            com.example.ui.components.CompanionOrb(state = companionState, size = 64.dp)
        }
        if (messages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clear_chat_action), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        }
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
                                            .clickable { onSendText(prompt, false) }
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

            items(messages, key = { it.id }) { msg ->
                // ChatGPT-style entrance: user bubbles slide from the end,
                // assistant bubbles rise from the bottom with a spring bounce.
                var visible by remember(msg.id) { mutableStateOf(false) }
                LaunchedEffect(msg.id) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = if (msg.isUser)
                        slideInHorizontally(
                            initialOffsetX = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = 300f
                            )
                        ) + fadeIn(animationSpec = spring(stiffness = 300f))
                    else
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = spring(
                                dampingRatio = 0.65f,
                                stiffness = 250f
                            )
                        ) + expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = spring(stiffness = 250f)
                        ) + fadeIn(animationSpec = spring(stiffness = 250f))
                ) {
                    ZadIntChatBubble(msg, onUndo = onUndoCommit, onSpeak = { voiceManager.speakHumanLike(msg.text) })
                }
            }

            if (isTyping) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ZadIntTypingIndicator()
                        com.example.ui.components.NeuralMeshBadge(
                            status = com.example.ui.components.NeuralMeshStatus.THINKING,
                            specialistId = lastSpecialist
                        )
                    }
                }
            } else {
                // مؤشر الشبكة الحي — يظهر بعد كل رد مكتمل بالوكيل اللي عالجه فعلاً
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        com.example.ui.components.NeuralMeshBadge(
                            status = if (lastSpecialist != null) com.example.ui.components.NeuralMeshStatus.ACTIVE
                                     else com.example.ui.components.NeuralMeshStatus.IDLE,
                            specialistId = lastSpecialist
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        if (pendingAgentProposals.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                com.example.ui.components.AgentProposalsCard(
                    proposals = pendingAgentProposals,
                    onConfirm = onConfirmAgentProposals,
                    onCancel = onCancelAgentProposals
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggleButton(
                checked = autoTtsEnabled.value,
                onCheckedChange = {
                    autoTtsEnabled.value = it
                    context.getSharedPreferences("zad_voice", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("auto_tts", it).apply()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (autoTtsEnabled.value) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Auto TTS",
                    tint = if (autoTtsEnabled.value) primary else outlineVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
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
                onClick = {
                    if (inputText.isNotBlank()) {
                        if (autoTtsEnabled.value) awaitingVoiceReply = true
                        onSend()
                    }
                    else startListeningWithPermission()
                },
                modifier = Modifier.size(46.dp).clip(CircleShape).background(if (isListening) dangerColor else primary)
            ) {
                Icon(
                    if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = stringResource(R.string.send_action), tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ZadIntChatBubble(msg: AiChatMessage, onUndo: (String) -> Unit = {}, onSpeak: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.75f).clip(RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            )).background(if (msg.isUser) primary else surfaceContainerHigh)
                .then(if (!msg.isUser) Modifier.border(0.5.dp, outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = 4.dp, bottomEnd = 16.dp
                )) else Modifier)
                .padding(12.dp)
        ) {
            Column {
                if (!msg.isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.CompanionOrb(
                            state = com.example.ui.components.companionStateForMessage(msg.text),
                            size = 18.dp,
                            animated = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.app_name), fontSize = 10.sp, color = primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        // 🔊 زر سماع الرد — يشتغل بـ ElevenLabs عبر ZadNaturalVoiceEngine
                        onSpeak?.let { speak ->
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.listen_action),
                                tint = onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { speak() }
                                    .padding(2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(msg.text, color = if (msg.isUser) Color.White else onSurface, style = Typography.bodyMedium)
                // كتابة فعلية حصلت في المخزون — الزرار ده بيرجّعها. موجود جوه نفس
                // الفقاعة عشان التراجع يبقى في نفس مكان التأكيد، من غير ما المستخدم
                // يضطر يفتح شاشة المخزون ويصلّح بإيده.
                msg.undoableCommitId?.let { commitId ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { onUndo(commitId) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_undo_action), style = Typography.labelMedium, color = primary)
                    }
                }
                // شفافية الذاكرة — "متاحة وقت الرد" مش "اتستخدمت أكيد"، نفس التحفّظ من
                // السيرفر. مطوية افتراضيًا عشان مايزحمش الفقاعة لمين مش مهتم.
                if (!msg.isUser && msg.memoryAvailable.isNotEmpty()) {
                    var memoryExpanded by remember(msg.id) { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { memoryExpanded = !memoryExpanded }
                            .heightIn(min = 32.dp)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(13.dp), tint = onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.chat_memory_hint_label, msg.memoryAvailable.size),
                            style = Typography.labelSmall,
                            color = onSurfaceVariant
                        )
                    }
                    if (memoryExpanded) {
                        Column(modifier = Modifier.padding(top = 2.dp, start = 17.dp)) {
                            msg.memoryAvailable.forEach { hint ->
                                Text(
                                    "• " + hint.note,
                                    style = Typography.labelSmall,
                                    color = onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 1.dp)
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
private fun ZadIntTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val delay = index * 150
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -8f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 900
                                0f at 0
                                -8f at 300
                                0f at 600
                                0f at 900
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "dot_$index"
                    )
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 900
                                0.35f at 0
                                1f at 300
                                0.35f at 600
                                0.35f at 900
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "dot_alpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  LOCAL ML HELPERS
// ════════════════════════════════════════════════════════════════

fun computeMonthlyData(transactions: List<ZadTransaction>, context: android.content.Context): List<Pair<String, Double>> {
    if (transactions.isEmpty()) return emptyList()
    val monthNames = context.resources.getStringArray(R.array.month_names_short).toList()

    val grouped = transactions
        .filter { it.txnKind == "expense" }
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

/**
 * إنفاق يومي آخر [days] يوم منتهية بـ [endDate] (بما فيها الأيام بدون معاملات = صفر) —
 * لرسم خط زمني متصل بالتواريخ بدل تجميع شهري، أسلوب شاشة بورصة. [endDate] الافتراضي
 * اليوم؛ نمرر تاريخ أقدم لحساب الفترة السابقة للمقارنة (% تغير).
 */
fun computeDailySpendData(
    transactions: List<ZadTransaction>,
    days: Int = 30,
    endDate: java.time.LocalDate = java.time.LocalDate.now()
): List<Pair<java.time.LocalDate, Double>> {
    val today = endDate
    val startDate = today.minusDays((days - 1).toLong())

    val byDay = mutableMapOf<java.time.LocalDate, Double>()
    transactions.filter { it.txnKind == "expense" }.forEach { tx ->
        val date = tx.createdAt?.let {
            runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        } ?: return@forEach
        if (!date.isBefore(startDate) && !date.isAfter(today)) {
            byDay[date] = (byDay[date] ?: 0.0) + tx.amount
        }
    }

    return (0 until days).map { offset ->
        val date = startDate.plusDays(offset.toLong())
        date to (byDay[date] ?: 0.0)
    }
}

// ════════════════════════════════════════════════════════════════
//  PREMIUM CARDS — قوة الصرف + مقارنة شهرية + تحليل سلوكي + تصدير
// ════════════════════════════════════════════════════════════════

/**
 * Spending power, as the mockup's Zad Mind overview card: the dark `#052E16` panel with a
 * mint title, the percentage at 30/800, and one flat 8dp meter.
 */
@Composable
private fun SpendingPowerGaugeCard(power: com.example.data.ZadCentralBrain.SpendingPower) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // powerPct = null means no budget is on record, so there is no percentage to state.
    val pct = power.powerPct
    val meterColor = when {
        pct == null -> Color.White.copy(alpha = 0.35f)
        pct >= 60 -> primaryFixed
        pct >= 35 -> Color(0xFF84CC16)
        pct >= 15 -> secondary
        else -> dangerColor
    }

    com.example.ui.components.ZadDarkPanel(title = stringResource(R.string.spending_power)) {
        Text(
            pct?.let { "$it%" } ?: "—",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        com.example.ui.components.ZadMeterBar(
            progress = (pct ?: 0) / 100f,
            color = meterColor,
            height = 8.dp,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // dailySafeSpend = null معناه مفيش سقف متسجل — الرقم مش موجود أصلاً، فبنعرض
            // "لسه غير محدد" بدل ما نطبع صفر بعملة كأنه حصيلة حساب.
            val safePerDay = power.dailySafeSpend
            SpendingPowerFigure(
                value = safePerDay?.let { com.example.data.CurrencyFormatter.format(context, it) }
                    ?: stringResource(R.string.budget_unknown_value),
                label = stringResource(R.string.safe_per_day_suffix, com.example.data.CurrencyFormatter.symbol(context))
            )
            SpendingPowerFigure(
                value = com.example.data.CurrencyFormatter.format(context, power.currentDailyAvg),
                label = stringResource(R.string.actual_daily_rate),
                valueColor = if (safePerDay != null && safePerDay > 0 && power.currentDailyAvg > safePerDay) secondaryLight else Color.White
            )
            SpendingPowerFigure(
                value = "${power.daysLeftInMonth}",
                label = stringResource(R.string.days_left_label)
            )
        }
    }
}

@Composable
private fun SpendingPowerFigure(value: String, label: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        Text(label, style = Typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
    }
}

@Composable
private fun MonthComparisonCard(mc: com.example.data.ZadCentralBrain.MonthComparison) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val improved = mc.deltaPct <= 0
    val deltaColor = if (improved) successColor else dangerColor
    val maxSpend = maxOf(mc.thisMonthSpent, mc.lastMonthSpent, 1.0)
    // Same dead-constant animation as the donut had — see drawProgressOnEntry. Keyed on
    // the figures so switching month re-runs the bars instead of snapping.
    val animatedProgress by com.example.ui.components.drawProgressOnEntry(
        key = mc.thisMonthSpent to mc.lastMonthSpent, durationMs = 900,
    )

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
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
                            tint = if (up) dangerColor else successColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(cat, style = Typography.bodySmall, color = onSurface, modifier = Modifier.weight(1f))
                        Text(
                            "${if (up) "+" else ""}${com.example.data.CurrencyFormatter.format(context, diff)}",
                            style = Typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (up) dangerColor else successColor
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
        color = successColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = successColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.zad_knows_you), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = primaryDark)
            }
            Spacer(modifier = Modifier.height(12.dp))

            @Composable
            fun factRow(emoji: String, text: String) {
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text(emoji, style = Typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, style = Typography.bodySmall, color = onSurface, lineHeight = 18.sp)
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
    var isBuildingPdf by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (!isBuildingPdf) {
                isBuildingPdf = true
                scope.launch {
                    try {
                        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.ZadReportPdfBuilder.build(context, report)
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, exportSubject)
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
                    } catch (e: Exception) {
                        android.util.Log.e("ExportReportButton", "PDF export failed: ${e.message}", e)
                    } finally {
                        isBuildingPdf = false
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isBuildingPdf) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onPrimaryContainer)
            } else {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_monthly_report), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
        }
    }
}

@Composable
internal fun MonthlyReportCard(
    transactions: List<ZadTransaction>,
    budget: Double,
    totalIncome: Double,
    totalExpense: Double,
    topCategories: List<Pair<String, Double>>,
    cycleStart: java.time.LocalDate,
    onNavigateToStatementImport: () -> Unit = {}
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<com.example.data.ZadAiRepository.MonthlyExpenseReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cycleLabel = remember(cycleStart) {
        cycleStart.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ar")))
    }

    fun generate() {
        scope.launch {
            isLoading = true
            loadError = false
            try {
                report = com.example.data.ZadAiRepository.generateMonthlyExpenseReport(
                    transactions = transactions,
                    budget = budget,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    topCategories = topCategories,
                    cycleLabel = cycleLabel
                )
            } catch (e: Exception) {
                loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_reports), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Text(cycleLabel, style = Typography.labelSmall, color = onSurfaceVariant)
            }
            val current = report
            if (current != null) {
                IconButton(onClick = {
                    val shareText = buildString {
                        append(current.summary)
                        if (current.insights.isNotEmpty()) {
                            append("\n\n")
                            current.insights.forEach { append("• $it\n") }
                        }
                        if (current.recommendations.isNotEmpty()) {
                            append("\n")
                            current.recommendations.forEach { append("✓ $it\n") }
                        }
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_zad_report_title)))
                }) {
                    Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.share_zad_report_title), tint = primary)
                }
            }
            IconButton(onClick = { generate() }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = primary)
            }
        }
        TextButton(onClick = onNavigateToStatementImport, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.statement_import_title), style = Typography.labelSmall, color = primary)
        }

        when {
            isLoading -> {
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.components.ZadLoadingState(modifier = Modifier.fillMaxWidth().height(80.dp))
            }
            loadError -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.changes_save_failed), style = Typography.bodySmall, color = dangerColor)
            }
            report == null -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.monthly_report_empty_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.generate_monthly_report_action))
                }
            }
            else -> {
                val current = report!!
                Spacer(modifier = Modifier.height(8.dp))
                if (current.healthLabel.isNotBlank()) {
                    Text(current.healthLabel, style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = primary)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (current.summary.isNotBlank()) {
                    Text(current.summary, style = Typography.bodyMedium, color = onSurface)
                }
                if (current.insights.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    current.insights.forEach { insight ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• ", color = onSurfaceVariant)
                            Text(insight, style = Typography.bodySmall, color = onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (current.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.recommendations_label), style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    current.recommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("✓ ", color = successColor)
                            Text(rec, style = Typography.bodySmall, color = onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthScoreCard(report: com.example.data.ZadCentralBrain.BrainReport) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scoreColor = when {
        report.healthScore >= 85 -> successColor
        report.healthScore >= 65 -> Color(0xFF84CC16)
        report.healthScore >= 40 -> secondary
        else -> dangerColor
    }
    val animatedScore by animateFloatAsState(
        targetValue = report.healthScore / 100f,
        animationSpec = tween(900),
        label = "score"
    )

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
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
                    stringResource(
                        R.string.spent_remaining_summary,
                        com.example.data.CurrencyFormatter.format(context, report.totalSpent),
                        report.remaining?.let { com.example.data.CurrencyFormatter.format(context, it) }
                            ?: stringResource(R.string.budget_unknown_value)
                    ),
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
private fun DepletionForecastCard(forecasts: List<com.example.data.ZadCentralBrain.DepletionForecast>) {
    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
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
                        color = if (urgent) dangerColor.copy(alpha = 0.08f) else successColor.copy(alpha = 0.08f)
                    ) {
                        Text(
                            when {
                                f.predictedDaysLeft <= 0 -> stringResource(R.string.will_deplete_soon)
                                f.predictedDaysLeft == 1 -> stringResource(R.string.one_day_left_label)
                                else -> stringResource(R.string.days_left_count_label, f.predictedDaysLeft)
                            },
                            style = Typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (urgent) dangerColor else successColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FamilyNeuralMeshCard(
    familyState: com.example.ui.viewmodels.FamilyState,
    onOpenReport: () -> Unit,
    onNavigateToFamily: () -> Unit
) {
    val activeState = familyState as? com.example.ui.viewmodels.FamilyState.Active
    val memberCount = activeState?.members?.size ?: 1
    val completedChores = activeState?.chores?.count { it.isCompleted } ?: 0
    val totalChores = activeState?.chores?.size ?: 0
    val pendingGroceries = activeState?.groceries?.count { !it.isPurchased } ?: 0

    com.example.ui.components.ZadListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                            .background(Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFF6C63FF)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.auto_zadintelligence_52264), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Multi-Agent Family Neural Mesh", style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF9333EA).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.auto_zadintelligence_37663), style = Typography.labelSmall, color = Color(0xFF9333EA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            if (!activeState?.members.isNullOrEmpty()) {
                Spacer(Modifier.height(14.dp))
                FamilyNeuralNetworkCanvas(members = activeState.members)
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(stringResource(R.string.auto_zadintelligence_61227), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.auto_zadintelligence_70890), style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = primary)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(stringResource(R.string.auto_zadintelligence_15566), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.auto_zadintelligence_95376), style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = tertiary)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenReport,
                    modifier = Modifier.weight(1f).height(38.dp).pressableScale(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auto_zadintelligence_16928), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                OutlinedButton(
                    onClick = onNavigateToFamily,
                    modifier = Modifier.height(38.dp).pressableScale(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.auto_zadintelligence_62225), style = Typography.labelSmall)
                }
            }
        }
    }
}

/**
 * تصوّر "الشبكة العصبية العائلية" بصريًا — مفيش رسمة زي دي كانت موجودة أصلاً، الكارت كان
 * أرقام وكارتات إحصاء بس. عمق زائف (parallax) بدل محرك 3D حقيقي: كل عقدة على طور مختلف
 * من موجة تنفّس، فمقياسها وشفافيتها بتتغيّر بمرور الوقت بدل ما تبقى ثابتة — إحساس "حي"
 * من غير أي مكتبة رسوميات جديدة أو تكلفة أداء تُذكر.
 *
 * العقدة المركزية = العقل (zad-brain)، وكل فرد بيتوصل بيها بخط. الأونلاين بس بياخد نبضة
 * بيانات متحركة على خطه — ده فرق حقيقي مش زخرفة، بيعكس فعليًا مين متصل دلوقتي.
 */
@Composable
private fun FamilyNeuralNetworkCanvas(
    members: List<com.example.data.FamilyMember>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "neuralMesh")

    val rotationDeg by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "neuralMeshRotation"
    )
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "neuralMeshPulse"
    )
    val breathePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "neuralMeshBreathe"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = size.minDimension * 0.42f
        val hubRadius = 16.dp.toPx()
        val nodeRadius = 13.dp.toPx()

        members.forEachIndexed { index, member ->
            val angleDeg = (360f / members.size) * index + rotationDeg
            val angleRad = Math.toRadians(angleDeg.toDouble())
            // طور تنفّس مختلف لكل عقدة عشان مايتحركوش كتلة واحدة — إحساس عمق مش نبض جماعي.
            val depthPhase = breathePhase + index * (Math.PI.toFloat() / members.size.coerceAtLeast(1))
            val depth = (kotlin.math.sin(depthPhase) + 1f) / 2f // 0..1
            val depthScale = 0.76f + depth * 0.36f
            val depthAlpha = 0.55f + depth * 0.45f

            // تسطيح رأسي (×0.55) = إحساس منظور "بنشوف الحلقة من فوق شوية" بدل دايرة مسطحة.
            val nodePos = Offset(
                x = center.x + ringRadius * kotlin.math.cos(angleRad).toFloat(),
                y = center.y + ringRadius * kotlin.math.sin(angleRad).toFloat() * 0.55f,
            )

            val baseLineColor = if (member.isOnline) Color(0xFF9333EA) else Color(0xFF9333EA).copy(alpha = 0.28f)
            drawLine(
                color = baseLineColor.copy(alpha = baseLineColor.alpha * (0.4f + depth * 0.4f)),
                start = center,
                end = nodePos,
                strokeWidth = 1.6.dp.toPx(),
            )

            if (member.isOnline) {
                val t = (pulsePhase + index * 0.19f) % 1f
                drawCircle(
                    color = Color(0xFFC084FC),
                    radius = 3.dp.toPx(),
                    center = Offset(
                        x = center.x + (nodePos.x - center.x) * t,
                        y = center.y + (nodePos.y - center.y) * t,
                    ),
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9333EA), Color(0xFF6C63FF)),
                    center = nodePos,
                    radius = nodeRadius * depthScale,
                ),
                radius = nodeRadius * depthScale,
                center = nodePos,
                alpha = depthAlpha,
            )
            drawCircle(
                color = Color.White.copy(alpha = depthAlpha * 0.7f),
                radius = nodeRadius * depthScale,
                center = nodePos,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            val initial = member.alias.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟"
            val label = textMeasurer.measure(
                initial,
                style = TextStyle(fontSize = (10 * depthScale).sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(nodePos.x - label.size.width / 2f, nodePos.y - label.size.height / 2f),
            )
        }

        // العقل المركزي — نفس ثابت طول الوقت، الأفراد هم اللي بيلفوا حواليه.
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF9333EA), Color(0xFF6C63FF))),
            radius = hubRadius,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = hubRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyNeuralReportBottomSheet(
    familyState: com.example.ui.viewmodels.FamilyState,
    onDismiss: () -> Unit,
    onNavigateToFamily: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeState = familyState as? com.example.ui.viewmodels.FamilyState.Active
    val context = LocalContext.current
    val currency = com.example.data.MarketPrefs.getMarket(context).currencySymbol

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9333EA).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = Color(0xFF9333EA), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.auto_zadintelligence_36332), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.auto_zadintelligence_96391), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (activeState == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👨‍👩‍👧‍👦", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.auto_zadintelligence_23852), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.auto_zadintelligence_85865), style = Typography.bodySmall, color = onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { onDismiss(); onNavigateToFamily() }, colors = ButtonDefaults.buttonColors(containerColor = primary)) {
                            Text(stringResource(R.string.auto_zadintelligence_82314), style = Typography.labelMedium, color = Color.White)
                        }
                    }
                }
            } else {
                val children = activeState.members.filter { it.role == "child" }

                Text(stringResource(R.string.auto_zadintelligence_1763), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = primary)
                Spacer(Modifier.height(8.dp))
                if (children.isEmpty()) {
                    Text(stringResource(R.string.auto_zadintelligence_65857), style = Typography.bodySmall, color = onSurfaceVariant)
                } else {
                    children.forEach { child ->
                        val childChores = activeState.chores.filter { it.assignedTo == child.id }
                        val done = childChores.count { it.isCompleted }
                        val total = childChores.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👦", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(child.alias ?: "ابن", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("أنجز $done من $total مهام · رصيد المصروف: ${child.balance ?: 0} $currency", style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (done == total && total > 0) successColor.copy(alpha = 0.12f) else primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(if (done == total && total > 0) "مكتمل ⭐" else "نشط", style = Typography.labelSmall, color = if (done == total && total > 0) successColor else primary, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                // 2. تدبير مقاضي البيت
                Text(stringResource(R.string.auto_zadintelligence_65720), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = tertiary)
                Spacer(Modifier.height(8.dp))
                val pendingGroceries = activeState.groceries.filter { !it.isPurchased }
                if (pendingGroceries.isEmpty()) {
                    Text(stringResource(R.string.auto_zadintelligence_22228), style = Typography.bodySmall, color = onSurfaceVariant)
                } else {
                    Text(
                        "نواقص العائلة المطلوبة: ${pendingGroceries.take(5).joinToString("، ") { it.itemName }}",
                        style = Typography.bodyMedium,
                        color = onSurface
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 3. توصيات مبنية على البيانات الفعلية — لا نصوص ثابتة وهمية
                Text(stringResource(R.string.auto_zadintelligence_53103), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF9333EA))
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF9333EA).copy(alpha = 0.08f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sheetChores = activeState.chores
                        val choreDone = sheetChores.count { it.isCompleted }
                        val choreTotal = sheetChores.size
                        val notes = buildList {
                            // مهام: بيانات حقيقية فقط — نتجاهل لو مفيش مهام أصلاً
                            if (choreTotal > 0) {
                                val ratio = choreDone.toFloat() / choreTotal
                                when {
                                    ratio >= 0.8f -> add("إنجاز المهام ممتاز ($choreDone من $choreTotal) — استحقاق مكافأة مناسبة هذا الأسبوع.")
                                    ratio >= 0.4f -> add("المهام في المنتصف ($choreDone من $choreTotal) — تذكير خفيف قد يكمل القائمة.")
                                    else -> add("$choreDone من $choreTotal مهمة فقط مكتملة — يستحق جلسة ترتيب للمهام المتعثرة.")
                                }
                            }
                            if (pendingGroceries.isNotEmpty()) {
                                add("${pendingGroceries.size} صنف ناقص في قائمة المشتريات العائلية — تجميعها في زيارة واحدة يقلل المصروف العشوائي.")
                            }
                            if (children.isNotEmpty()) {
                                val totalChildBalance = children.sumOf { (it.balance ?: 0).toDouble() }
                                add("إجمالي رصيد مصروف الأبناء الحالي: ${totalChildBalance.toInt()} $currency — راجعه معهم كدرس ادخار عملي.")
                            }
                        }
                        if (notes.isEmpty()) {
                            Text(stringResource(R.string.auto_zadintelligence_40342), style = Typography.bodySmall, color = onSurfaceVariant)
                        } else {
                            notes.forEach { Text("• $it", style = Typography.bodySmall, color = onSurface) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


/**
 * توزيع الصرف حسب الفئة — من مرجع "new ui ux" (renderAssistant / CATEGORY_SPEND):
 * كارت أبيض 18dp، عنوان 13sp bold، وبارات أفقية 7dp مدوّرة لكل فئة بنسبة إنفاقها
 * من أعلى فئة. الألوان بتلف على palette المرجع (أخضر/أزرق/عنبري/مرجاني/بنفسجي).
 */
@Composable
fun CategoryBreakdownCard(byCategory: Map<String, Double>) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val sorted = byCategory.entries.sortedByDescending { it.value }
    val max = sorted.firstOrNull()?.value?.takeIf { it > 0 } ?: return
    val palette = listOf(
        com.example.ui.theme.ZadV2.green600,
        com.example.ui.theme.ZadV2.info,
        com.example.ui.theme.ZadV2.warn,
        com.example.ui.theme.ZadV2.danger,
        com.example.ui.theme.ZadV2.violet,
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    com.example.ui.components.ZadListCard(shape = shape, contentPadding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.category_breakdown_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            sorted.forEachIndexed { idx, (category, amount) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            category,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZadV2.slate,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            com.example.data.CurrencyFormatter.format(context, amount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                            .background(Color(0xFFF1F4F3))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((amount / max).toFloat())
                                .height(7.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                                .background(palette[idx % palette.size])
                        )
                    }
                }
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════
//  بوابة عقل زاد بالإعلان — إعلان واحد يفتح الشاشة 24 ساعة.
//  نفس روح RewardedBrainAdManager (3 إعلانات = جلسة العقل) لكن أخف:
//  بوابة الدخول إعلان واحد. المشترك مدفوع بيمر من غير بوابة أصلاً.
// ════════════════════════════════════════════════════════════════
@Composable
private fun ZadBrainAdGate(
    onUnlocked: () -> Unit,
    onSubscribe: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // العد بيتزامن من التخزين المحلي + السيرفر عند الفتح — كان بيبدأ صفر دايماً
    // فالبطارية بتظهر فاضية حتى لو العميل شاف إعلانين قبل كده.
    var adsWatched by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.getAdWatchCount(context)) }
    var isShowingAd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scope.launch {
            com.example.ads.RewardedBrainAdManager.syncServerState(context)?.let {
                adsWatched = it.adWatchCount
            }
        }
    }
    val adsRequired = com.example.ads.RewardedBrainAdManager.TOTAL_ADS_REQUIRED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF064E3B), Color(0xFF010604))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🧠", fontSize = 56.sp)
            Text(
                text = stringResource(R.string.brain_gate_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.brain_gate_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6EE7B7),
                textAlign = TextAlign.Center
            )

            // بطارية الإعلانات — كل إعلان قطعة من البطارية
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(adsRequired) { idx ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (idx < adsWatched) Color(0xFF10B981) else Color(0x33FFFFFF))
                    )
                }
            }

            Button(
                onClick = {
                    if (isShowingAd) return@Button
                    isShowingAd = true
                    com.example.ads.RewardedBrainAdManager.showRewardedEnergyAd(
                        context = context,
                        onAdWatched = { newCount, fullyUnlocked ->
                            adsWatched = newCount
                            if (fullyUnlocked) onUnlocked()
                        },
                        onFailed = { isShowingAd = false }
                    )
                },
                enabled = !isShowingAd,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    text = when {
                        isShowingAd -> stringResource(R.string.brain_gate_loading)
                        RewardedBrainAdManager.isAdReady().not() -> "جاري تحميل الإعلان… ثواني وجرب تاني ⏳"
                        else -> stringResource(R.string.brain_gate_watch_ad)
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = onSubscribe) {
                Text(
                    text = stringResource(R.string.brain_gate_subscribe),
                    color = Color(0xFF6EE7B7)
                )
            }
        }
    }
}
