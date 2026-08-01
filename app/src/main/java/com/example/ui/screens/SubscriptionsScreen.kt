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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadSubscription
import com.example.ui.components.ZadListCard
import com.example.ui.components.ZadScreenBanner
import com.example.ui.components.pressableScale
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        Column(modifier = Modifier.fillMaxSize()) {
            // Title moved to ZadTopHeader — the active/all filter is all that stays.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

            // Summary Banner
            com.example.ui.components.AppearOnEntry {
            ZadScreenBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        Text(com.example.data.CurrencyFormatter.format(context, totalMonthly), style = Typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.active_subs_count_label), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text("${activeSubs.size}", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = background,
                contentColor = primary,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = {
                            Text(title, fontWeight = FontWeight.Bold, style = Typography.labelLarge,
                                color = if (selectedTab == i) primary else onSurfaceVariant)
                        }
                    )
                }
            }

            // AI Detection Banner
            if (selectedTab == 0 && activeSubs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
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

            // اشتراكات اكتشفها الذكاء الاصطناعي، لسه محتاجة تأكيد المستخدم قبل ما تتسجل (AUDIT.md)
            if (pendingSubscriptions.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    com.example.ui.components.DetectedSubscriptionsSection(
                        pending = pendingSubscriptions,
                        onConfirm = { viewModel.confirmDetectedSubscription(it) },
                        onDismiss = { viewModel.dismissDetectedSubscription(it) }
                    )
                }
            }

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        // بديل الأيقونة الثابتة بـ Lottie متحركة — نفس تسلسل ZadEmptyState (عنوان بولد وسط الشاشة)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        ) {
                            ZadLottieAsset(
                                resId = R.raw.lottie_empty_box,
                                modifier = Modifier.size(140.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedTab == 0) stringResource(R.string.no_subscriptions_any) else stringResource(R.string.no_items_in_category),
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
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
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }

        // Add FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 88.dp).pressableScale()
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

@Composable
private fun SubScreenSubscriptionCardFull(
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

    val subCardShape = RoundedCornerShape(16.dp)
    ZadListCard(
        modifier = Modifier.pressableScale(),
        shape = subCardShape,
        containerColor = if (sub.isActive) surface else surfaceContainerLow,
        contentPadding = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // مؤشر لوني بحسب إلحاحية التجديد — نفس لغة التصميم اللي استخدمناها في مؤشر مخزون InventoryScreen
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (sub.isActive) daysColor else outlineVariant)
            )
            Row(modifier = Modifier.padding(16.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(catBillsBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = catBillsIcon)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (sub.isActive) onSurface else onSurfaceVariant)
                    if (!sub.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(outlineVariant).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(stringResource(R.string.cancelled_badge), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                if (daysLeft != null) {
                    Text(
                        when {
                            daysLeft == 0 -> stringResource(R.string.renews_today)
                            daysLeft < 0 -> stringResource(R.string.expired_days_ago, -daysLeft)
                            else -> stringResource(R.string.renews_in_days, daysLeft)
                        },
                        style = Typography.labelSmall, color = daysColor, fontWeight = FontWeight.SemiBold
                    )
                }
                if (sub.provider != null) {
                    Text(sub.provider, style = Typography.labelSmall, color = onSurfaceVariant)
                }
                if (sub.autoDeduct) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(stringResource(R.string.auto_deduct_badge), style = Typography.labelSmall, color = primary, fontSize = 10.sp)
                    }
                }
            }
            Text(com.example.data.CurrencyFormatter.format(context, sub.amount),
                style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (sub.isActive) onSurface else onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onToggleAutoDeduct, modifier = Modifier.size(32.dp).pressableScale()) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.auto_deduct_toggle_action),
                    tint = if (sub.autoDeduct) primary else outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onToggleActive, modifier = Modifier.size(32.dp).pressableScale()) {
                Icon(
                    if (sub.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (sub.isActive) stringResource(R.string.disable) else stringResource(R.string.enable),
                    tint = if (sub.isActive) warningColor else successColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).pressableScale()) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = dangerColor, modifier = Modifier.size(18.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.subscription_name_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text(stringResource(R.string.service_provider_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = renewalDate, onValueChange = { renewalDate = it }, label = { Text(stringResource(R.string.renewal_date_hint)) }, modifier = Modifier.fillMaxWidth())
                if (title.length >= 3) {
                    Text("الفئة المقترحة: $category", style = Typography.labelSmall, color = onSurfaceVariant)
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
