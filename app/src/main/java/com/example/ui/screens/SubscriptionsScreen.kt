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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.workers.nextRenewalDate
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    var editingSubscription by remember { mutableStateOf<ZadSubscription?>(null) }
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

    val today = LocalDate.now()
    val rawFiltered: List<ZadSubscription> = when (selectedTab) {
        0 -> if (showInactive) subscriptions else activeSubs
        1 -> (if (showInactive) subscriptions else activeSubs).filter { it.type == "subscription" || it.category == "اشتراك" }
        2 -> (if (showInactive) subscriptions else activeSubs).filter { it.category == "فواتير" || it.type == "bill" || it.type == "utility" }
        3 -> (if (showInactive) subscriptions else activeSubs).filter { it.category == "الأقساط" || it.category == "أقساط" || it.category == "التزامات" || it.type == "installment" || it.type == "rent" }
        else -> activeSubs
    }
    val filtered = remember(rawFiltered, today) {
        rawFiltered.sortedWith(
            compareBy<ZadSubscription> { if (!it.isActive) 1 else 0 }
                .thenBy { sub ->
                    val next = com.example.data.BudgetMath.nextRenewalDate(sub, today)
                    next?.let { ChronoUnit.DAYS.between(today, it).toInt() } ?: 999
                }
        )
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
            contentPadding = PaddingValues(ZadHubListHorizontalPadding, 8.dp, ZadHubListHorizontalPadding, ZadHubListBottomPadding),
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
                            Text(stringResource(R.string.subscriptions_clear_all), color = dangerColor, style = Typography.labelLarge)
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
                                onEdit = { editingSubscription = sub },
                                onDelete = { viewModel.deleteSubscription(sub.id) },
                                onMarkAsPaid = {
                                    viewModel.markSubscriptionAsPaid(sub)
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.sub_paid_success, sub.title),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }

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
            AddEditSubscriptionDialog(
                subscription = null,
                onDismiss = { showAddDialog = false },
                onSave = { title, amount, renewalDate, provider, category, billingCycle, type ->
                    val calculatedDueDay = try { LocalDate.parse(renewalDate).dayOfMonth } catch (e: Exception) { null }
                    viewModel.addSubscription(
                        ZadSubscription(
                            title = title,
                            amount = amount,
                            renewalDate = renewalDate,
                            dueDay = calculatedDueDay,
                            provider = provider,
                            category = category,
                            isActive = true,
                            billingCycle = billingCycle,
                            type = type
                        )
                    )
                    showAddDialog = false
                }
            )
        }

        editingSubscription?.let { existing ->
            AddEditSubscriptionDialog(
                subscription = existing,
                onDismiss = { editingSubscription = null },
                onSave = { title, amount, renewalDate, provider, category, billingCycle, type ->
                    val calculatedDueDay = try { LocalDate.parse(renewalDate).dayOfMonth } catch (e: Exception) { null }
                    viewModel.updateSubscription(
                        existing.copy(
                            title = title,
                            amount = amount,
                            renewalDate = renewalDate,
                            dueDay = calculatedDueDay,
                            provider = provider,
                            category = category,
                            billingCycle = billingCycle,
                            type = type
                        )
                    )
                    editingSubscription = null
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsPaid: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now()
    val renewal = try {
        if (!sub.renewalDate.isNullOrBlank()) LocalDate.parse(sub.renewalDate.take(10)) else null
    } catch (e: Exception) { null }

    val nextRenewal = com.example.data.BudgetMath.nextRenewalDate(sub, today) ?: renewal
    val daysLeft = nextRenewal?.let { ChronoUnit.DAYS.between(today, it).toInt() }
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
        // كان ZadV2.warn (#B45309 ثابت). الفروع التلاتة التانية في الـwhen ده كلها
        // واعية بالثيم، وده كان الشاذ الوحيد — فكان بيفضل بنفس درجته في الوضع الغامق
        // بينما جيرانه بيقلبوا. `secondary` هو المكافئ الدلالي كمان: Color.kt موصّفه
        // بالنص كـ"Due dates, budget warnings, pending actions".
        daysLeft != null && daysLeft <= 7 -> warningColor
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .border(0.8.dp, primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onMarkAsPaid() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.sub_mark_as_paid),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                            }
                        }
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
                            Icons.Default.EditNote,
                            contentDescription = stringResource(R.string.edit_subscription_dialog_title),
                            tint = onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onEdit() }
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

/**
 * دورات الفوترة المخزّنة — **قيم مطابقة مش نصوص عرض**. بتتكتب في
 * `zad_subscriptions.billing_cycle` وبيقارن بيها `nextRenewalDate` و`monthlyEquivalentCost`
 * وكارت الاشتراك بـ`uppercase()`، فتفضل إنجليزي حتى لو الواجهة اتترجمت (قاعدة i18n في
 * CLAUDE.md). اللي بيتترجم هو `billingCycleLabel` بس.
 */
private val subscriptionBillingCycles = listOf("MONTHLY", "YEARLY", "WEEKLY")

@Composable
private fun billingCycleLabel(cycle: String): String = when (cycle.uppercase()) {
    "YEARLY", "ANNUAL" -> stringResource(R.string.billing_cycle_yearly)
    "WEEKLY" -> stringResource(R.string.billing_cycle_weekly)
    else -> stringResource(R.string.billing_cycle_monthly)
}

/**
 * فورم واحد للإضافة والتعديل، على نفس شكل `AddEditObligationDialog` — ده بند P2
 * "توحيد فورم الاشتراكات/الأقساط". الفورمين كانوا متفاوتين: فورم الالتزامات فيه
 * `enabled = canSave` و`isError` وكيبورد أرقام وشرايح تكرار، وفورم الاشتراكات مالوش
 * ولا واحدة — زرار الحفظ كان دايماً مفعّل وبيعمل `return@Button` صامت لو المبلغ غلط.
 *
 * **والعطل الحقيقي:** `billing_cycle` ماكانش ليه ولا كاتب واحد في التطبيق كله
 * (`grep "billingCycle ="` = صفر)، فكل اشتراك كان بيفضل MONTHLY — قيمة الموديل
 * الافتراضية — للأبد، ومفيش أي طريق يغيّرها. وتلات مستهلكين بيقروها:
 * `SubscriptionAutoDeductWorker.nextRenewalDate` (اشتراك سنوي كان بيتخصم ١٢ مرة
 * في السنة)، كارت الاشتراك (تكلفة سنوية بـ١٢×)، و`ZadViewModel.monthlyEquivalentCost`.
 * الآلية كانت مبنية بالكامل — `nextRenewalDate` بيدعم YEARLY/ANNUAL/WEEKLY أصلاً —
 * والفورم بس هو اللي مكانش يقدر ينتج غير MONTHLY.
 */
private data class SubscriptionPresetItem(
    val name: String,
    val provider: String,
    val category: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val type: String = "subscription"
)

private val commonSubscriptionPresets = listOf(
    SubscriptionPresetItem("Netflix", "Netflix", "ترفيه", Icons.Default.Movie, BrandNetflix, "subscription"),
    SubscriptionPresetItem("Shahid", "MBC Shahid", "ترفيه", Icons.Default.LiveTv, BrandShahid, "subscription"),
    SubscriptionPresetItem("Spotify", "Spotify", "موسيقى", Icons.Default.MusicNote, BrandSpotify, "subscription"),
    SubscriptionPresetItem("YouTube Premium", "Google", "ترفيه", Icons.Default.SmartDisplay, BrandYouTube, "subscription"),
    SubscriptionPresetItem("TOD", "TOD TV", "رياضة وترفيه", Icons.Default.LiveTv, BrandTod, "subscription"),
    SubscriptionPresetItem("Watch IT", "Watch IT", "ترفيه", Icons.Default.Movie, BrandWatchIt, "subscription"),
    SubscriptionPresetItem("تابي Tabby", "Tabby", "أقساط", Icons.Default.ShoppingBag, BrandTabby, "installment"),
    SubscriptionPresetItem("تمارا Tamara", "Tamara", "أقساط", Icons.Default.ShoppingBag, BrandTamara, "installment"),
    SubscriptionPresetItem("فاتورة كهرباء", "شركة الكهرباء", "فواتير", Icons.Default.Bolt, BrandElectricity, "utility"),
    SubscriptionPresetItem("فاتورة مياه", "شركة المياه", "فواتير", Icons.Default.WaterDrop, BrandWater, "utility"),
    SubscriptionPresetItem("فاتورة إنترنت", "شركة الاتصالات", "اتصالات", Icons.Default.Wifi, BrandInternet, "utility"),
    SubscriptionPresetItem("إيجار البيت", "إيجار المنزل", "سكن", Icons.Default.Home, BrandRent, "rent")
)

private fun parseFlexibleRenewalDate(input: String): LocalDate? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    // تحويل الأرقام العربية والفارسية إلى أرقام إنجليزية قياسية
    val normalized = buildString {
        for (ch in trimmed) {
            when (ch) {
                in '٠'..'٩' -> append(ch - '٠')
                in '۰'..'۹' -> append(ch - '۰')
                else -> append(ch)
            }
        }
    }
    val standardPatterns = listOf(
        "yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd", "dd/MM/yyyy",
        "d-M-yyyy", "d/M/yyyy", "yyyy-M-d", "yyyy/M/d"
    )
    for (pattern in standardPatterns) {
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern))
        } catch (_: Exception) {}
    }
    // تحليل الأنماط غير القياسية (مثل 2026_30-9 أو 30_9_2026)
    val digits = normalized.split(Regex("[^0-9]+")).filter { it.isNotBlank() }
    if (digits.size == 3) {
        val p0 = digits[0].toIntOrNull() ?: return null
        val p1 = digits[1].toIntOrNull() ?: return null
        val p2 = digits[2].toIntOrNull() ?: return null
        val year: Int
        val month: Int
        val day: Int
        if (p0 >= 1000) {
            // السنة أولاً: e.g. 2026_30-9 أو 2026-9-30
            year = p0
            if (p1 > 12) {
                day = p1
                month = p2
            } else if (p2 > 12) {
                month = p1
                day = p2
            } else {
                month = p1
                day = p2
            }
        } else if (p2 >= 1000) {
            // السنة أخيراً: e.g. 30-9-2026 أو 9-30-2026
            year = p2
            if (p0 > 12) {
                day = p0
                month = p1
            } else if (p1 > 12) {
                month = p0
                day = p1
            } else {
                day = p0
                month = p1
            }
        } else {
            val y = if (p0 in 20..99) p0 + 2000 else if (p2 in 20..99) p2 + 2000 else LocalDate.now().year
            year = y
            if (p0 >= 1000 || (p0 in 20..99 && y == p0 + 2000)) {
                month = p1
                day = p2
            } else {
                day = p0
                month = p1
            }
        }
        return try {
            val safeYear = year.coerceIn(2000, 2100)
            val safeMonth = month.coerceIn(1, 12)
            val maxDay = java.time.YearMonth.of(safeYear, safeMonth).lengthOfMonth()
            val safeDay = day.coerceIn(1, maxDay)
            LocalDate.of(safeYear, safeMonth, safeDay)
        } catch (_: Exception) { null }
    } else if (digits.size == 2) {
        val p0 = digits[0].toIntOrNull() ?: return null
        val p1 = digits[1].toIntOrNull() ?: return null
        val year = LocalDate.now().year
        val (day, month) = if (p0 > 12) p0 to p1 else p1 to p0
        return try {
            val safeMonth = month.coerceIn(1, 12)
            val maxDay = java.time.YearMonth.of(year, safeMonth).lengthOfMonth()
            val safeDay = day.coerceIn(1, maxDay)
            LocalDate.of(year, safeMonth, safeDay)
        } catch (_: Exception) { null }
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubscriptionDialog(
    subscription: ZadSubscription?,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, renewalDate: String, provider: String, category: String, billingCycle: String, type: String) -> Unit
) {
    var title by remember { mutableStateOf(subscription?.title ?: "") }
    var selectedType by remember { mutableStateOf(subscription?.type ?: "subscription") }
    var amountStr by remember {
        mutableStateOf(
            subscription?.amount?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var totalInstallmentStr by remember { mutableStateOf("") }
    var remainingInstallmentsStr by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(subscription?.provider ?: "") }
    var category by remember { mutableStateOf(subscription?.category ?: "اشتراك") }
    var renewalDate by remember { mutableStateOf(subscription?.renewalDate?.take(10) ?: LocalDate.now().toString()) }
    var billingCycle by remember { mutableStateOf(subscription?.billingCycle?.uppercase() ?: "MONTHLY") }
    var showDatePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val amount = amountStr.toDoubleOrNull()
    val parsedDate = remember(renewalDate) { parseFlexibleRenewalDate(renewalDate) }
    val canSave = title.isNotBlank() && amount != null && amount > 0.0

    // Auto-calculate monthly installment amount if total and count are entered
    LaunchedEffect(totalInstallmentStr, remainingInstallmentsStr) {
        val total = totalInstallmentStr.toDoubleOrNull()
        val count = remainingInstallmentsStr.toIntOrNull()
        if (total != null && total > 0 && count != null && count > 0 && amountStr.isBlank()) {
            val calcMonthly = total / count
            amountStr = if (calcMonthly == calcMonthly.toLong().toDouble()) calcMonthly.toLong().toString() else "%.2f".format(calcMonthly)
        }
    }

    LaunchedEffect(title, amount, subscription) {
        if (subscription != null) return@LaunchedEffect
        if (title.length >= 3 && amount != null && amount > 0) {
            kotlinx.coroutines.delay(600)
            val classification = ZadAiRepository.classifyBill(title, amount)
            if (classification != null) {
                category = classification.category
                if (provider.isBlank() && !classification.provider.isNullOrBlank()) {
                    provider = classification.provider
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        renewalDate = picked.toString()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm_action_short)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (subscription == null) R.string.add_subscription_dialog_title
                    else R.string.edit_subscription_dialog_title
                ),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
                // 1. شريط الخدمات الشهيرة السريع
                Text(
                    text = "الخدمات والاشتراكات المقترحة:",
                    style = Typography.labelSmall,
                    color = onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonSubscriptionPresets) { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (title == preset.name) preset.color.copy(alpha = 0.18f) else surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (title == preset.name) preset.color else outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .pressableScale()
                                .clickable {
                                    title = preset.name
                                    provider = preset.provider
                                    category = preset.category
                                    selectedType = preset.type
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(preset.color.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        preset.icon,
                                        contentDescription = null,
                                        tint = preset.color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    preset.name,
                                    style = Typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. الفصل الصريح بين الأنواع (Segmented Type Selector)
                Text(
                    text = "تصنيف الالتزام:",
                    style = Typography.labelSmall,
                    color = onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                val typeOptions = listOf(
                    "subscription" to stringResource(R.string.sub_type_subscription_clean),
                    "installment" to stringResource(R.string.sub_type_installment_clean),
                    "utility" to stringResource(R.string.sub_type_bill_clean),
                    "rent" to stringResource(R.string.sub_type_obligation_clean)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeOptions.forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(typeLabel, style = Typography.labelSmall) }
                        )
                    }
                }

                // 3. حقول القسط الإضافية إذا تم اختيار "قسط"
                if (selectedType == "installment") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = totalInstallmentStr,
                            onValueChange = { totalInstallmentStr = it },
                            label = { Text(stringResource(R.string.installment_total_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = remainingInstallmentsStr,
                            onValueChange = { remainingInstallmentsStr = it },
                            label = { Text(stringResource(R.string.installment_remaining_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                // 4. الحقول الأساسية
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.subscription_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = {
                        Text(
                            stringResource(
                                R.string.amount_with_currency_hint,
                                com.example.data.CurrencyFormatter.symbol(context)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountStr.isNotBlank() && (amount == null || amount <= 0.0)
                )
                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text(stringResource(R.string.service_provider_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 5. حقل التاريخ مع DatePicker Button ودعم الإدخال المرن
                OutlinedTextField(
                    value = renewalDate,
                    onValueChange = { renewalDate = it },
                    label = { Text(stringResource(R.string.renewal_date_hint)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = false,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.pick_date_action), tint = primary)
                        }
                    }
                )

                Text(
                    stringResource(R.string.billing_cycle_label),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subscriptionBillingCycles.forEach { cycle ->
                        FilterChip(
                            selected = billingCycle == cycle,
                            onClick = { billingCycle = cycle },
                            label = { Text(billingCycleLabel(cycle), style = Typography.labelSmall) }
                        )
                    }
                }

                if (title.length >= 3) {
                    Text(
                        stringResource(R.string.subs_suggested_category, category),
                        style = Typography.labelSmall,
                        color = onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val finalDate = (parsedDate ?: parseFlexibleRenewalDate(renewalDate) ?: LocalDate.now()).toString()
                    val finalCategory = when (selectedType) {
                        "installment" -> if (category == "اشتراك" || category.isBlank()) "أقساط" else category
                        "utility" -> if (category == "اشتراك" || category.isBlank()) "فواتير" else category
                        "rent" -> if (category == "اشتراك" || category.isBlank()) "التزامات" else category
                        else -> if (category.isBlank()) "اشتراك" else category
                    }
                    val finalTitle = if (selectedType == "installment" && remainingInstallmentsStr.isNotBlank() && !title.contains("قسط")) {
                        "$title ($remainingInstallmentsStr أقساط)"
                    } else title.trim()

                    onSave(
                        finalTitle,
                        amount ?: 0.0,
                        finalDate,
                        provider.trim(),
                        finalCategory,
                        billingCycle,
                        selectedType
                    )
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
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.AccountBalance,
                    title = stringResource(R.string.debt_empty_state_title),
                    subtitle = stringResource(R.string.debt_empty_state_hint),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
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
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
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
                    com.example.ui.components.ZadEmptyState(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.live_search_error_state),
                        subtitle = stringResource(R.string.live_search_error_state_hint),
                        iconTint = error.copy(alpha = 0.6f),
                        iconBackground = error.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(stringResource(R.string.sinking_fund_amount_hint)) }, modifier = Modifier.fillMaxWidth())
                }
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
