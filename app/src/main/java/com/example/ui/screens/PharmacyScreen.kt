package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.ZadPharmacyItem
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.GlassCard
import com.example.ui.components.HeroGradientCard
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val PHARMACY_CATEGORIES = listOf("عام", "مسكن", "مضاد حيوي", "فيتامين", "مزمن")

/** Task 17.2.3 — validate at entry, not just when the scheduler reads it back later. */
private fun isValidDoseTimesInput(raw: String): Boolean {
    if (raw.isBlank()) return true
    return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.all { t ->
        try {
            java.time.LocalTime.parse(if (t.length == 5) t else t.padStart(5, '0'))
            true
        } catch (e: Exception) { false }
    }
}

/** مواعيد افتراضية مقترحة لو المستخدم سايب حقل المواعيد فاضي — موزّعة على ساعات الصحيان (8ص-10م) */
private fun suggestDoseTimes(dailyDoseCount: Int): String {
    if (dailyDoseCount <= 0) return ""
    if (dailyDoseCount == 1) return "09:00"
    val startHour = 8
    val endHour = 22
    val stepMinutes = ((endHour - startHour) * 60) / (dailyDoseCount - 1)
    return (0 until dailyDoseCount).joinToString(", ") { i ->
        val totalMinutes = startHour * 60 + stepMinutes * i
        "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val items by viewModel.pharmacyItems.collectAsState()
    val monthlyCost by viewModel.monthlyPharmaCost.collectAsState()
    val weeklyAdherence by viewModel.weeklyAdherencePercent.collectAsState()
    val familyState by familyViewModel.state.collectAsState()
    val familyMembers = (familyState as? FamilyState.Active)?.members ?: emptyList()

    var showAddDialog by remember { mutableStateOf(false) }
    var refillTarget by remember { mutableStateOf<ZadPharmacyItem?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun daysUntilExpiry(item: ZadPharmacyItem): Int? = item.expiryDate?.let {
        try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.take(10))).toInt() } catch (e: Exception) { null }
    }

    val expiringSoon = items.filter { val d = daysUntilExpiry(it); d != null && d in 0..30 }.sortedBy { daysUntilExpiry(it) }
    val lowStock = items.filter { item ->
        val supply = item.daysOfSupplyLeft()
        supply != null && supply <= 5
    }
    val expired = items.filter { val d = daysUntilExpiry(it); d != null && d < 0 }

    val hasScheduledDoses = items.any { it.doseTimesList().isNotEmpty() }
    var exactAlarmGranted by remember { mutableStateOf(com.example.data.PharmacyReminderScheduler.canScheduleExact(context)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = com.example.data.PharmacyReminderScheduler.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ترتيب الأولوية: منتهي > قرب انتهاء > مخزون منخفض > الباقي
    val sortedItems = items.sortedWith(
        compareBy(
            { val d = daysUntilExpiry(it); if (d != null && d < 0) 0 else 1 },
            { val d = daysUntilExpiry(it); d ?: Int.MAX_VALUE },
            { it.daysOfSupplyLeft() ?: Int.MAX_VALUE }
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu), tint = onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pharmacy_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
                }
                Row {
                    IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.pressableScale()) {
                        Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = stringResource(R.string.toggle_view_action), tint = onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToCamera, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_medicine_action), tint = primary)
                    }
                }
            }

            if (hasScheduledDoses && !exactAlarmGranted) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(warningColor.copy(alpha = 0.14f))
                        .clickable {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = warningColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.exact_alarm_permission_hint),
                            style = Typography.bodySmall, color = warningColor, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = warningColor, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Quick Summary — Glassmorphism hero
            AppearOnEntry {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    HeroGradientCard(colors = listOf(catHealthIcon, primary)) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                PharmacySummaryStat(items.size.toString(), stringResource(R.string.total_medicines_label))
                                PharmacySummaryStat(expiringSoon.size.toString(), stringResource(R.string.expiring_soon_label))
                                PharmacySummaryStat(lowStock.size.toString(), stringResource(R.string.low_stock_label))
                            }
                            if (monthlyCost > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.monthly_pharma_cost_label, com.example.data.CurrencyFormatter.format(context, monthlyCost)),
                                        style = Typography.labelMedium, color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (expired.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(dangerColor.copy(alpha = 0.12f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.expired_medicine_warning, expired.size),
                            style = Typography.bodySmall, color = dangerColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            weeklyAdherence?.let { pct ->
                val adherenceColor = when {
                    pct >= 80 -> successColor
                    pct >= 50 -> warningColor
                    else -> dangerColor
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(adherenceColor.copy(alpha = 0.10f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = adherenceColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.weekly_adherence_label, pct),
                            style = Typography.bodySmall, color = adherenceColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Smart Expiry Tracker — قسم أفقي مخصص للأدوية القريبة من الانتهاء
            if (expiringSoon.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.smart_expiry_tracker_title),
                    style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(expiringSoon, key = { "expiry_${it.id}" }) { item ->
                        ExpiryTrackerCard(item = item, daysLeft = daysUntilExpiry(item) ?: 0)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (sortedItems.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ZadLottieAsset(resId = R.raw.lottie_empty_box, modifier = Modifier.size(140.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_medicines_hint),
                        style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = onSurface, textAlign = TextAlign.Center
                    )
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(sortedItems, key = { it.id }) { item ->
                        PharmacyItemGridCard(
                            item = item,
                            daysUntilExpiry = daysUntilExpiry(item),
                            familyMemberName = familyMembers.find { it.id == item.familyMemberId }?.alias,
                            onDelete = { viewModel.deletePharmacyItem(item.id) },
                            onConfirmQuantity = { qty -> viewModel.confirmPharmacyQuantity(item.id, qty) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(sortedItems, key = { _, it -> it.id }) { index, item ->
                        var itemVisible by remember(item.id) { mutableStateOf(false) }
                        LaunchedEffect(item.id) { itemVisible = true }
                        AnimatedVisibility(visible = itemVisible, enter = ZadTransitions.listItemEnter(index)) {
                            PharmacyItemCard(
                                item = item,
                                daysUntilExpiry = daysUntilExpiry(item),
                                familyMemberName = familyMembers.find { it.id == item.familyMemberId }?.alias,
                                onConsumeDose = { scheduledAt ->
                                    // Task 17.2.2 — used to call updatePharmacyQuantity() directly,
                                    // a THIRD path bypassing markPharmacyDoseTaken() entirely: no
                                    // history, always -1 regardless of unitsPerDose, no dedupe.
                                    viewModel.consumePharmacyDose(item.id, scheduledAt)
                                },
                                onConfirmQuantity = { qty -> viewModel.confirmPharmacyQuantity(item.id, qty) },
                                onRefill = { refillTarget = item },
                                onDelete = { viewModel.deletePharmacyItem(item.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 88.dp).pressableScale()
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_action))
        }

        if (showAddDialog) {
            AddPharmacyItemDialog(
                familyMembers = familyMembers,
                onDismiss = { showAddDialog = false },
                onSave = { item ->
                    viewModel.addPharmacyItem(item)
                    showAddDialog = false
                }
            )
        }

        refillTarget?.let { item ->
            RefillPharmacyItemDialog(
                item = item,
                onDismiss = { refillTarget = null },
                onSave = { addedQty, newPrice, newExpiry ->
                    viewModel.refillPharmacyItem(item.id, addedQty, newPrice, newExpiry)
                    refillTarget = null
                }
            )
        }
    }
}

@Composable
private fun PharmacySummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = Typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun ExpiryTrackerCard(item: ZadPharmacyItem, daysLeft: Int) {
    val urgencyColor = if (daysLeft <= 7) dangerColor else warningColor
    Box(
        modifier = Modifier.width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(urgencyColor.copy(alpha = 0.10f))
            .padding(12.dp)
    ) {
        Column {
            Icon(Icons.Default.Medication, contentDescription = null, tint = urgencyColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(item.name, style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                stringResource(R.string.expires_in_days, daysLeft),
                style = Typography.labelSmall, color = urgencyColor, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PharmacyItemCard(
    item: ZadPharmacyItem,
    daysUntilExpiry: Int?,
    familyMemberName: String?,
    onConsumeDose: (String?) -> Unit,
    onConfirmQuantity: (Int) -> Unit,
    onRefill: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDialog by remember(item.id) { mutableStateOf(false) }
    val supplyDays = item.daysOfSupplyLeft()
    val isExpired = daysUntilExpiry != null && daysUntilExpiry < 0
    val isExpiringSoon = daysUntilExpiry != null && daysUntilExpiry in 0..30
    val isLowStock = supplyDays != null && supplyDays <= 5

    val statusColor = when {
        isExpired -> dangerColor
        isLowStock && supplyDays!! <= 3 -> dangerColor
        isLowStock || isExpiringSoon -> warningColor
        else -> successColor
    }

    GlassCard(
        modifier = Modifier.shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), spotColor = statusColor.copy(alpha = 0.16f)).pressableScale(),
        containerColor = surface.copy(alpha = 0.85f),
        borderColor = statusColor.copy(alpha = 0.25f),
        contentPadding = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(statusColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(catHealthBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, tint = catHealthIcon, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        if (!item.dosage.isNullOrBlank()) {
                            Text(item.dosage, style = Typography.labelSmall, color = onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).pressableScale()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = dangerColor, modifier = Modifier.size(18.dp))
                    }
                }
                if (item.doseTimesList().isNotEmpty() || familyMemberName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.doseTimesList().isNotEmpty()) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(item.doseTimesList().joinToString(" · "), style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        }
                        if (familyMemberName != null) {
                            if (item.doseTimesList().isNotEmpty()) { Spacer(modifier = Modifier.width(8.dp)) }
                            Icon(Icons.Default.Person, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(familyMemberName, style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                if (item.hasInvalidDoseTime) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = dangerColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("وقت جرعة مش مفهوم — عدّله", style = Typography.labelSmall, color = dangerColor, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(
                            when {
                                isExpired -> stringResource(R.string.expired_days_ago, -daysUntilExpiry!!)
                                isExpiringSoon -> stringResource(R.string.expires_in_days, daysUntilExpiry!!)
                                else -> stringResource(R.string.remaining_quantity_label, item.remainingQuantity, item.unit)
                            },
                            style = Typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                        )
                    }
                    if (!isExpired && supplyDays != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(outlineVariant).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(
                                stringResource(R.string.days_supply_left, supplyDays),
                                style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 11.sp
                            )
                        }
                    } else if (!isExpired && !item.dosage.isNullOrBlank()) {
                        // Task 17.2.1 — never show a guessed days-left number. unitsPerDose is
                        // unknown for this item (free-text dosage was never confidently parsed),
                        // so this is an honest "we don't know" the user can resolve in one tap.
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(warningColor.copy(alpha = 0.14f))
                                .clickable { showConfirmDialog = true }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("الكمية محتاجة تأكيد", style = Typography.labelSmall, color = warningColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Task 17.2.2 — one button per scheduled dose time today instead of a single
                // generic "consume" button, so a specific time can be marked/retroactively
                // logged (and canonicalScheduledAt gives each slot a stable dedupe key, so
                // tapping this AND the notification's "Taken" for the same slot only counts once).
                val doseTimes = item.doseTimesList()
                if (doseTimes.isNotEmpty() && item.remainingQuantity > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        doseTimes.forEach { time ->
                            OutlinedButton(
                                onClick = { onConsumeDose(com.example.data.PharmacyReminderScheduler.canonicalScheduledAt(time)) },
                                modifier = Modifier.height(28.dp).pressableScale(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(time, style = Typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.remainingQuantity > 0 && doseTimes.isEmpty()) {
                        OutlinedButton(
                            onClick = { onConsumeDose(null) },
                            modifier = Modifier.height(30.dp).pressableScale(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.consume_dose_action), style = Typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = onRefill,
                        modifier = Modifier.height(30.dp).pressableScale(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.renew_order_action), style = Typography.labelSmall)
                    }
                    TextButton(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.height(30.dp).pressableScale(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("فاضل قد إيه فعلاً؟", style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmQuantityDialog(
            item = item,
            onDismiss = { showConfirmDialog = false },
            onConfirm = { qty -> onConfirmQuantity(qty); showConfirmDialog = false }
        )
    }
}

/** Task 17.2.2 — manual resync: counts drift no matter how good the logging is, so there
 * must be a way to fix remaining_quantity directly without deleting/re-adding the medication. */
@Composable
private fun ConfirmQuantityDialog(item: ZadPharmacyItem, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(item.remainingQuantity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فاضل قد إيه فعلاً؟") },
        text = {
            Column {
                Text("${item.name} — الكمية الحالية المسجلة: ${item.remainingQuantity} ${item.unit}", style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.all { c -> c.isDigit() }) text = it },
                    label = { Text(item.unit) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun PharmacyItemGridCard(
    item: ZadPharmacyItem,
    daysUntilExpiry: Int?,
    familyMemberName: String?,
    onDelete: () -> Unit,
    onConfirmQuantity: (Int) -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val supplyDays = item.daysOfSupplyLeft()
    val isExpired = daysUntilExpiry != null && daysUntilExpiry < 0
    val isExpiringSoon = daysUntilExpiry != null && daysUntilExpiry in 0..30
    val isLowStock = supplyDays != null && supplyDays <= 5
    val statusColor = when {
        isExpired -> dangerColor
        isLowStock && supplyDays!! <= 3 -> dangerColor
        isLowStock || isExpiringSoon -> warningColor
        else -> successColor
    }

    GlassCard(
        containerColor = surface.copy(alpha = 0.85f),
        borderColor = statusColor.copy(alpha = 0.25f),
        modifier = Modifier.pressableScale()
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).pressableScale()) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete_action), tint = onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(catHealthBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = catHealthIcon, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(item.name, style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1)
        if (familyMemberName != null) {
            Text(familyMemberName, style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(
                when {
                    isExpired -> stringResource(R.string.expired_days_ago, -daysUntilExpiry!!)
                    isExpiringSoon -> stringResource(R.string.expires_in_days, daysUntilExpiry!!)
                    else -> stringResource(R.string.remaining_quantity_label, item.remainingQuantity, item.unit)
                },
                style = Typography.labelSmall, color = statusColor, fontSize = 10.sp
            )
        }
        // Task 27.1(b) — نفس فولباك PharmacyItemCard (list view): units_per_dose مش معروف
        // فـ"أيام متبقية" رقم مخمّن مالوش معنى، أهون نقول "محتاجة تأكيد" قابلة للضغط بدل
        // ما نسكت. الجريد كارت كان ناقصه ده (سيناريو 17.2.1 كان مغطى في list view بس).
        if (!isExpired && supplyDays == null && !item.dosage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(warningColor.copy(alpha = 0.14f))
                    .clickable { showConfirmDialog = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("الكمية محتاجة تأكيد", style = Typography.labelSmall, color = warningColor, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmQuantityDialog(
            item = item,
            onDismiss = { showConfirmDialog = false },
            onConfirm = { qty -> onConfirmQuantity(qty); showConfirmDialog = false }
        )
    }
}

@Composable
private fun AddPharmacyItemDialog(
    familyMembers: List<com.example.data.FamilyMember>,
    onDismiss: () -> Unit,
    onSave: (ZadPharmacyItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var activeIngredient by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PHARMACY_CATEGORIES.first()) }
    var dosage by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("قرص") }
    var dailyDoseCount by remember { mutableStateOf("1") }
    var doseTimes by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_medicine_dialog_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.medicine_name_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = activeIngredient, onValueChange = { activeIngredient = it }, label = { Text(stringResource(R.string.active_ingredient_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text(stringResource(R.string.dosage_hint)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(stringResource(R.string.remaining_quantity_hint)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.unit_hint)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = dailyDoseCount, onValueChange = { dailyDoseCount = it }, label = { Text(stringResource(R.string.daily_dose_hint)) }, modifier = Modifier.fillMaxWidth())
                // Task 17.2.3 — dose_times used to be validated only when the scheduler read
                // it back, silently dropping a bad entry with no trace. Reject it at entry
                // instead, so a typo like "2o:00" can't reach the scheduler at all.
                val doseTimesValid = remember(doseTimes) { isValidDoseTimesInput(doseTimes) }
                OutlinedTextField(
                    value = doseTimes, onValueChange = { doseTimes = it },
                    label = { Text(stringResource(R.string.dose_times_hint)) },
                    placeholder = { Text(suggestDoseTimes(dailyDoseCount.toIntOrNull() ?: 1).ifBlank { "08:00, 20:00" }) },
                    isError = !doseTimesValid,
                    supportingText = if (!doseTimesValid) {
                        { Text("وقت مش مفهوم — استخدم صيغة HH:MM زي 08:00", color = dangerColor, style = Typography.labelSmall) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = expiryDate, onValueChange = { expiryDate = it }, label = { Text(stringResource(R.string.expiry_date_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text(stringResource(R.string.is_recurring_label), style = Typography.bodySmall)
                }

                if (familyMembers.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = memberMenuExpanded, onExpandedChange = { memberMenuExpanded = it }) {
                        OutlinedTextField(
                            value = familyMembers.find { it.id == selectedMemberId }?.alias ?: stringResource(R.string.none_option),
                            onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.assigned_family_member_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = memberMenuExpanded, onDismissRequest = { memberMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.none_option)) }, onClick = { selectedMemberId = null; memberMenuExpanded = false })
                            familyMembers.forEach { member ->
                                DropdownMenuItem(text = { Text(member.alias) }, onClick = { selectedMemberId = member.id; memberMenuExpanded = false })
                            }
                        }
                    }
                }

                Text(stringResource(R.string.category_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PHARMACY_CATEGORIES) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = Typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDoseTimes = doseTimes.ifBlank { suggestDoseTimes(dailyDoseCount.toIntOrNull() ?: 1).ifBlank { null } }
                    if (name.isNotBlank() && isValidDoseTimesInput(doseTimes)) {
                        onSave(
                            ZadPharmacyItem(
                                name = name,
                                activeIngredient = activeIngredient.ifBlank { null },
                                category = category,
                                dosage = dosage.ifBlank { null },
                                remainingQuantity = quantity.toIntOrNull() ?: 1,
                                unit = unit.ifBlank { "قرص" },
                                dailyDoseCount = dailyDoseCount.toIntOrNull() ?: 1,
                                doseTimes = finalDoseTimes,
                                expiryDate = expiryDate.ifBlank { null },
                                price = price.toDoubleOrNull() ?: 0.0,
                                isRecurring = isRecurring,
                                familyMemberId = selectedMemberId
                            )
                        )
                    }
                },
                enabled = isValidDoseTimesInput(doseTimes),
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RefillPharmacyItemDialog(
    item: ZadPharmacyItem,
    onDismiss: () -> Unit,
    onSave: (addedQuantity: Int, newPrice: Double?, newExpiryDate: String?) -> Unit
) {
    var addedQuantity by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var newExpiryDate by remember { mutableStateOf(item.expiryDate ?: "") }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.renew_order_dialog_title, item.name), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = addedQuantity, onValueChange = { addedQuantity = it },
                    label = { Text(stringResource(R.string.added_quantity_hint, item.unit)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPrice, onValueChange = { newPrice = it },
                    label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newExpiryDate, onValueChange = { newExpiryDate = it },
                    label = { Text(stringResource(R.string.expiry_date_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = addedQuantity.toIntOrNull() ?: 0
                    if (qty > 0) onSave(qty, newPrice.toDoubleOrNull(), newExpiryDate.ifBlank { null })
                },
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
