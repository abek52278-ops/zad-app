package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadAiRepository
import com.example.data.ZadSubscription
import com.example.ui.components.GlassCard
import com.example.ui.components.ZadListCard
import com.example.ui.components.ZadScreenBanner
import com.example.ui.components.pressableScale
import com.example.ui.components.ZadTransitions
import com.example.ui.components.zadGlassBlur
import com.example.ui.components.subscriptionBrandFor
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/**
 * تاب "أقساط واشتراكات وفواتير" جوه بوابة Finances (UI_ARCHITECTURE_SPEC.md §2.2) —
 * كانت شاشة مستقلة (`ZadRoutes.SUBS`) بتحمل معاها كمان مخطط سداد الديون + العروض
 * الحية + تحديات العائلة + صناديق الادخار (تظهر بس في تاب "الكل" الداخلي القديم).
 * الأربعة دول اتنقلوا لتاب "ديون" المستقل في FinancesScreen — الفلترة الداخلية هنا
 * (الكل/اشتراكات/فواتير/أقساط عبر `type`/`category`) فضلت زي ما هي بالظبط.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: ZadViewModel
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val pendingSubscriptions by viewModel.pendingSubscriptions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showInactive by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val tabs = listOf(
        stringResource(R.string.filter_all),
        stringResource(R.string.filter_subscriptions),
        stringResource(R.string.bills_category),
        stringResource(R.string.installments_category)
    )
    val activeSubs = subscriptions.filter { it.isActive }
    val totalMonthly = activeSubs.sumOf { it.amount }

    val filtered: List<ZadSubscription> = when (selectedTab) {
        0 -> if (showInactive) subscriptions else activeSubs
        1 -> (if (showInactive) subscriptions else activeSubs).filter { it.type == "subscription" || it.category == "اشتراك" }
        2 -> (if (showInactive) subscriptions else activeSubs).filter { it.category == "فواتير" || it.type == "bill" }
        3 -> (if (showInactive) subscriptions else activeSubs).filter { it.category == "الأقساط" || it.type == "installment" }
        else -> activeSubs
    }

    // Auto-detect subscriptions on load
    LaunchedEffect(Unit) {
        viewModel.detectSubscriptions()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Everything used to be a static Column stacked above the LazyColumn — on short
        // screens that static stack (banner+tabs+AI banner+pending+debt card) could push
        // past the bottom nav/camera FAB with nothing to scroll it into view. All of it is
        // now items() in the one LazyColumn below so the whole screen scrolls together.
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, ZadHubListBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Title moved to ZadTopHeader — the active/all filter is all that stays.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        FilterChip(
                            selected = !showInactive,
                            onClick = { showInactive = false },
                            label = { Text(stringResource(R.string.active_filter), style = Typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer),
                            modifier = Modifier.height(32.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = showInactive,
                            onClick = { showInactive = true },
                            label = { Text(stringResource(R.string.filter_all), style = Typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = surfaceContainerHigh),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            item {
                // Summary Banner
                com.example.ui.components.AppearOnEntry {
                Box {
                // بقعة ضوء زجاجية — نفس أداة ZadCardHero، هنا بس لأن البانر ده عنصر واحد في
                // الشاشة (مش صف متكرر في LazyColumn زي كروت الاشتراكات تحت، فمفيش تكلفة أداء
                // من تكرار الـ blur على عناصر كتير في قايمة بتتمرر)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-20).dp, y = (-8).dp)
                        .zadGlassBlur(32.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                )
                ZadScreenBanner(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentPadding = 20.dp
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.total_monthly_subscriptions), style = Typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(com.example.data.CurrencyFormatter.format(context, totalMonthly), style = Typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.active_subs_count_label), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text("${activeSubs.size}", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                }
                }
            }

            item {
                com.example.ui.components.ZadSegmentedTabs(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            // AI Detection Banner
            if (selectedTab == 0 && activeSubs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp)).background(primaryContainer)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_detecting_subscriptions), style = Typography.bodySmall, color = primary)
                        }
                    }
                }
            }

            // اشتراكات اكتشفها الذكاء الاصطناعي — زر مسح سريع (الاشتراكات "الوهمية")
            val autoDetectedCount = subscriptions.count {
                it.category == "Auto-detected" || (it.category == "اشتراك" && it.title.isBlank())
            }
            if (autoDetectedCount > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${autoDetectedCount} اشتراك مكتشف تلقائياً",
                            style = Typography.labelMedium,
                            color = onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.deleteAllDetectedSubscriptions() }) {
                            Icon(Icons.Default.AutoDelete, contentDescription = null, modifier = Modifier.size(16.dp), tint = dangerColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح الكل", color = dangerColor, style = Typography.labelLarge)
                        }
                    }
                }
            }

            // اشتراكات اكتشفها الذكاء الاصطناعي، لسه محتاجة تأكيد المستخدم قبل ما تتسجل (AUDIT.md)
            if (pendingSubscriptions.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        com.example.ui.components.DetectedSubscriptionsSection(
                            pending = pendingSubscriptions,
                            onConfirm = { viewModel.confirmDetectedSubscription(it) },
                            onDismiss = { viewModel.dismissDetectedSubscription(it) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                    item {
                        com.example.ui.components.ZadEmptyState(
                            icon = Icons.Default.CreditCard,
                            title = if (selectedTab == 0) stringResource(R.string.no_subscriptions_any) else stringResource(R.string.no_items_in_category),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        )
                    }
                } else {
                    itemsIndexed(filtered, key = { _, sub -> sub.id }) { index, sub ->
                        var itemVisible by remember(sub.id) { mutableStateOf(false) }
                        LaunchedEffect(sub.id) { itemVisible = true }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = itemVisible,
                            enter = ZadTransitions.listItemEnter(index)
                        ) {
                            SubScreenSubscriptionCardFull(
                                sub = sub,
                                onToggleActive = { viewModel.updateSubscriptionActive(sub.id, !sub.isActive) },
                                onToggleAutoDeduct = { viewModel.updateSubscriptionAutoDeduct(sub.id, !sub.autoDeduct) },
                                onDelete = { viewModel.deleteSubscription(sub.id) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(ZadHubListBottomPadding)) }
        }

        // Add FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp).pressableScale()
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_action))
        }

        if (showAddDialog) {
            AddSubscriptionDialog(
                onDismiss = { showAddDialog = false },
                onSave = { title, amount, renewalDate, provider, category ->
                    viewModel.addSubscription(
                        ZadSubscription(
                            title = title,
                            amount = amount,
                            renewalDate = renewalDate,
                            provider = provider,
                            category = category,
                            isActive = true
                        )
                    )
                    showAddDialog = false
                }
            )
        }
    }
}

// internal مش private — Roborazzi capture tests (PreviewTest.kt) محتاجة توصله مباشرة،
// نفس نمط NearbyStoreCard (الشاشة الأب SubscriptionsScreen نفسها stateful)
@Composable
internal fun SubScreenSubscriptionCardFull(
    sub: ZadSubscription,
    onToggleActive: () -> Unit,
    onToggleAutoDeduct: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now()
    val renewal = try {
        if (!sub.renewalDate.isNullOrBlank()) LocalDate.parse(sub.renewalDate.take(10)) else null
    } catch (e: Exception) { null }

    val daysLeft = renewal?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val daysColor = when {
        daysLeft == null -> onSurfaceVariant
        daysLeft <= 3 -> dangerColor
        daysLeft <= 7 -> warningColor
        else -> successColor
    }

    // مرحلة ٥ب-٤ (docs/agent/PLAN_2026_08_06_rebuild.md) — 24dp بدل 16dp، نفس نصف قطر
    // باقي كروت الـ glass family. برند الخدمة (لو معروف) بيرجع أيقونة عائمة ملوّنة —
    // ده بالظبط اللي كان مبرر شيل الأيقونة قبل كده ("بتتكرر ومتفرقش")، دلوقتي الأيقونة
    // بتختلف فعلياً حسب الخدمة فرجعت لها لازمة.
    // ── صف الاشتراك بستايل المرجع (renderSubs): شريط لون جانبي 3px حسب إلحاح
    // التجديد، الاسم + سطر التجديد رمادي، والسعر bold على اليمين. أزرار التحكم
    // (إيقاف/حذف) بقت أيقونات رمادية هادية بدل تلات أزرار ملونة صارخة.
    val subCardShape = RoundedCornerShape(20.dp)
    val brand = subscriptionBrandFor(sub.title, sub.provider)
    val accent = when {
        !sub.isActive -> outlineVariant
        daysLeft != null && daysLeft <= 3 -> dangerColor
        daysLeft != null && daysLeft <= 7 -> ZadV2.warn
        else -> primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(subCardShape, elevation = 2.dp)
            .clip(subCardShape)
            .background(if (sub.isActive) surface else surfaceContainerLow)
            .border(1.dp, if (sub.isActive) outlineVariant.copy(alpha = 0.5f) else outlineVariant, subCardShape)
            .pressableScale()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (brand != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp), spotColor = brand.color.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(brand.color.copy(alpha = if (sub.isActive) 0.14f else 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            brand.icon,
                            contentDescription = null,
                            tint = brand.color.copy(alpha = if (sub.isActive) 1f else 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        sub.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sub.isActive) onSurface else onSurfaceVariant
                    )
                    Text(
                        when {
                            daysLeft == null -> sub.provider ?: ""
                            daysLeft == 0 -> stringResource(R.string.renews_today)
                            daysLeft < 0 -> stringResource(R.string.expired_days_ago, -daysLeft)
                            else -> stringResource(R.string.renews_in_days, daysLeft)
                        },
                        fontSize = 12.sp,
                        color = if (daysLeft != null && daysLeft <= 3 && sub.isActive) dangerColor else onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        com.example.data.CurrencyFormatter.format(context, sub.amount),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (sub.isActive) onSurface else outlineVariant
                    )
                    // التكلفة السنوية الحقيقية — ٢٠ جنيه/شهر = ٢٤٠/سنة صدمة مفيدة
                    // بتخلي العميل يراجع الاشتراكات الصغيرة المتراكمة.
                    if (sub.isActive) {
                        val yearly = when (sub.billingCycle?.uppercase()) {
                            "YEARLY" -> sub.amount
                            "WEEKLY" -> sub.amount * 52
                            else -> sub.amount * 12
                        }
                        Text(
                            "≈ " + com.example.data.CurrencyFormatter.format(context, yearly) + "/سنة",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = stringResource(R.string.auto_deduct_toggle_action),
                            tint = if (sub.autoDeduct) primary else outlineVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onToggleAutoDeduct() }
                                .padding(3.dp)
                                .size(18.dp)
                        )
                        Icon(
                            if (sub.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (sub.isActive) stringResource(R.string.disable) else stringResource(R.string.enable),
                            tint = onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onToggleActive() }
                                .padding(3.dp)
                                .size(18.dp)
                        )
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.delete_action),
                            tint = onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onDelete() }
                                .padding(3.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubscriptionDialog(onDismiss: () -> Unit, onSave: (String, Double, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("اشتراك") }
    var renewalDate by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    // بيصنّف الفاتورة تلقائياً (نوع/مزوّد/فئة) بعد ما المستخدم يكتب اسم ومبلغ حقيقيين —
    // debounce بسيط عن طريق delay قبل النداء عشان ميبعتش طلب AI مع كل حرف يتكتب.
    LaunchedEffect(title, amount) {
        val parsedAmount = amount.toDoubleOrNull()
        if (title.length >= 3 && parsedAmount != null && parsedAmount > 0) {
            kotlinx.coroutines.delay(600)
            val classification = com.example.data.ZadAiRepository.classifyBill(title, parsedAmount)
            if (classification != null) {
                category = classification.category
                if (provider.isBlank() && !classification.provider.isNullOrBlank()) {
                    provider = classification.provider
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subscription_dialog_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.subscription_name_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text(stringResource(R.string.service_provider_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = renewalDate, onValueChange = { renewalDate = it }, label = { Text(stringResource(R.string.renewal_date_hint)) }, modifier = Modifier.fillMaxWidth())
                if (title.length >= 3) {
                    Text(stringResource(R.string.subs_suggested_category, category), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: return@Button
                    if (title.isNotBlank()) {
                        onSave(title, parsedAmount,
                            if (renewalDate.isNotBlank()) renewalDate else LocalDate.now().plusMonths(1).toString(),
                            provider, category)
                    }
                },
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ════════════════════════════════════════════════════════════════
//  المحتوى المنقول من تاب "الاشتراكات والفرص" جوه عقل زاد (ZadIntelligenceScreen) —
//  تاسك ٦: الشاشة دي بقت المكان المستقل الوحيد لمخطط سداد الديون + العروض الحية +
//  تحديات العائلة + صناديق التجميع. AiNarrativeSection/LiveSearchBadge فضلوا جوه
//  ZadIntelligenceScreen.kt (لسه مستخدَمين هناك من كروت تانية)، وبقوا non-private
//  عشان الكروت هنا تقدر توصلهم.
// ════════════════════════════════════════════════════════════════

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

@Composable
fun DebtPayoffPlannerCard(debts: List<com.example.data.ZadDebt>, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strategy by remember { mutableStateOf(DebtStrategy.SNOWBALL) }
    var payTarget by remember { mutableStateOf<com.example.data.ZadDebt?>(null) }
    var deleteTarget by remember { mutableStateOf<com.example.data.ZadDebt?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var aiNarrative by remember { mutableStateOf<String?>(null) }
    var isLoadingNarrative by remember { mutableStateOf(false) }

    val plan = remember(debts, strategy) { calculateDebtPayoffPlan(debts, strategy) }

    LaunchedEffect(Unit) { viewModel.loadDebts() }

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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

                // كل دين بقى ليه صف كامل: المتبقي + تسجيل دفعة + حذف. الخطة كانت
                // بتعرض الترتيب بس، فالدين اللي يتضاف غلط أو يتسدد كان بيفضل محبوس
                // في القائمة — updateDebtBalance/deleteDebt كانوا موجودين من غير أي زر.
                plan.steps.sortedBy { it.order }.forEach { step ->
                    val debt = debts.firstOrNull { it.name == step.debtName }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = primaryContainer) {
                            Text("${step.order}", style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = primary, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.debtName, style = Typography.bodyMedium, color = onSurface)
                            if (debt != null) {
                                Text(
                                    stringResource(R.string.debt_remaining_label, com.example.data.CurrencyFormatter.format(context, debt.remainingBalance)),
                                    style = Typography.labelSmall,
                                    color = onSurfaceVariant
                                )
                            }
                        }
                        Text("${step.monthsToPayoff} " + stringResource(R.string.months_unit), style = Typography.labelSmall, color = onSurfaceVariant)
                        if (debt != null) {
                            IconButton(onClick = { payTarget = debt }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = stringResource(R.string.debt_pay_action),
                                    tint = primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { deleteTarget = debt }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.debt_delete_action),
                                    tint = dangerColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
                ) {
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

    // تسجيل دفعة: بيخصم من المتبقي بدل ما المستخدم يمسح الدين ويضيفه تاني بمبلغ جديد.
    payTarget?.let { debt ->
        var paidStr by remember(debt.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text(stringResource(R.string.debt_pay_title, debt.name), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.imePadding()
                ) {
                    Text(
                        stringResource(R.string.debt_remaining_label, com.example.data.CurrencyFormatter.format(context, debt.remainingBalance)),
                        style = Typography.bodySmall,
                        color = onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = paidStr,
                        onValueChange = { paidStr = it },
                        label = { Text(stringResource(R.string.debt_payment_amount_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val paid = paidStr.toDoubleOrNull() ?: return@Button
                    // ما ينفعش يبقى بالسالب — دفعة أكبر من المتبقي معناها الدين اتقفل.
                    viewModel.updateDebtBalance(debt.id, (debt.remainingBalance - paid).coerceAtLeast(0.0))
                    payTarget = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    deleteTarget?.let { debt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.debt_delete_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.debt_delete_confirm, debt.name), style = Typography.bodyMedium, color = onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDebt(debt.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) { Text(stringResource(R.string.debt_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ── Deal Matcher card ──────────────────────────────────────────────────────
@Composable
fun LiveDealsCard(shortageItems: List<String>, viewModel: ZadViewModel) {
    val context = LocalContext.current
    val deals by viewModel.liveDeals.collectAsState()
    val fetchState by viewModel.dealsFetchState.collectAsState()

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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

@Composable
fun FinancialChallengesCard(familyViewModel: FamilyViewModel) {
    val familyState by familyViewModel.state.collectAsState()
    val challenges by familyViewModel.financialChallenges.collectAsState()
    val progress by familyViewModel.challengeProgress.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { familyViewModel.loadFinancialChallenges() }

    val active = familyState
    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
                ) {
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
fun SinkingFundsCard(familyViewModel: FamilyViewModel) {
    val sinkingContext = LocalContext.current
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
    GlassCard(
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sinking_fund_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = targetStr, onValueChange = { targetStr = it }, label = { Text(stringResource(R.string.sinking_fund_target_hint)) }, modifier = Modifier.fillMaxWidth())
                    if (upcomingEvents.isNotEmpty()) {
                        Text(stringResource(R.string.sinking_fund_link_event_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                        upcomingEvents.forEach { (event, window) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedEventId == event.id, onClick = { selectedEventId = event.id })
                                Text(seasonalEventDisplayNameOrRaw(sinkingContext, event), style = Typography.bodySmall)
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
                OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(stringResource(R.string.sinking_fund_amount_hint)) }, modifier = Modifier.fillMaxWidth().imePadding())
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

/** The slug is the stable key; the label follows the app's language. `event.name` is the
 *  server's own text and is left alone — we have no translation for a custom event. */
private fun seasonalEventDisplayNameOrRaw(
    context: android.content.Context,
    event: com.example.data.SeasonalEvent,
): String = when (event.slug) {
    "ramadan" -> context.getString(R.string.season_ramadan)
    "eid_al_fitr" -> context.getString(R.string.season_eid_fitr)
    "eid_al_adha" -> context.getString(R.string.season_eid_adha)
    "back_to_school" -> context.getString(R.string.season_back_to_school)
    else -> event.name
}
