package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.ZadPharmacyItem
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.GlassCard
import com.example.ui.components.HeroGradientCard
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadLoadingState
import com.example.ui.components.ZadTransitions
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val PHARMACY_CATEGORIES = listOf("عام", "مسكن", "مضاد حيوي", "فيتامين", "مزمن")
private val PHARMACY_UNITS = listOf("حبة", "قرص", "كبسولة", "مل", "بخاخ", "نقطة", "كريم", "كيس", "أمبول", "علبة")

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
    onNavigateToCamera: () -> Unit = {}
) {
    val items by viewModel.pharmacyItems.collectAsState()
    val monthlyCost by viewModel.monthlyPharmaCost.collectAsState()
    val weeklyAdherence by viewModel.weeklyAdherencePercent.collectAsState()
    val familyState by familyViewModel.state.collectAsState()
    val activeFamilyState = familyState as? FamilyState.Active
    val familyMembers = activeFamilyState?.members ?: emptyList()
    // family_admin_read_pharmacy: الرؤية للوالدين بس، ومفيش داعي للزرار لو مفيش عيلة
    // حقيقية أصلاً (عضو واحد = العميل نفسه بس).
    val isFamilyPharmacyAdmin = activeFamilyState?.myMemberInfo?.role == "admin" && familyMembers.size > 1

    // UI_ARCHITECTURE_SPEC.md §2.4 / §4.3 — "أدوية العيلة" كان Nested Route منفصل
    // (FamilyPharmacyScreen)، دلوقتي Toggle داخل نفس الشاشة بدل تعقيد مسار ملاحة كامل.
    // رؤية بس، زي ما كانت — مفيش تعديل/حذف من هنا.
    var showFamilyView by remember { mutableStateOf(false) }
    var familyItems by remember { mutableStateOf<List<ZadPharmacyItem>>(emptyList()) }
    var familyItemsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(showFamilyView) {
        if (showFamilyView) {
            familyItemsLoading = true
            familyItems = com.example.data.SupabaseRepo.getFamilyPharmacyItems()
            familyItemsLoading = false
        }
    }
    val familyItemsByMember = remember(familyItems, familyMembers) {
        familyMembers.associateWith { member -> familyItems.filter { it.userId == member.userId } }
            .filterValues { it.isNotEmpty() }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    // كان بيودّي لشاشة "عقل زاد" (ZadRoutes.ASSISTANT) بدل ما يضيف الدوا هنا — العميل
    // كان بيحس إنه اتنقل لصفحة تانية مالهاش علاقة بالصيدلية. دلوقتي بيفتح حوار على نفس
    // الشاشة، والنص بيتبعت لنفس مسار العميل الحقيقي (agent_turn → أداة add_pharmacy_item)
    // اللي بيفهم الاسم والمواعيد من كلام حر ويضيفه فعلاً، من غير ما يسيب الصيدلية خالص.
    var showSmartAddDialog by remember { mutableStateOf(false) }
    var refillTarget by remember { mutableStateOf<ZadPharmacyItem?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun daysUntilExpiry(item: ZadPharmacyItem): Int? = item.expiryDate?.let {
        try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.take(10))).toInt() } catch (e: Exception) { null }
    }

    val expiringSoon = items.filter { val d = daysUntilExpiry(it); d != null && d in 0..30 }.sortedBy { daysUntilExpiry(it) }
    val lowStock = items.filter { it.isLowStock() }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isFamilyPharmacyAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showFamilyView = !showFamilyView }, modifier = Modifier.pressableScale()) {
                        Icon(
                            if (showFamilyView) Icons.Default.Person else Icons.Default.FamilyRestroom,
                            contentDescription = stringResource(R.string.family_pharmacy_action),
                            tint = if (showFamilyView) primary else onSurfaceVariant
                        )
                    }
                }
            }

            if (showFamilyView) {
                PharmacyFamilyBody(
                    itemsByMember = familyItemsByMember,
                    loading = familyItemsLoading,
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
                return@Column
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

            // ── The mockup's two pharmacy stats: dose adherence and monthly cost ──
            AppearOnEntry {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PharmacyStatCard(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.dose_adherence_label),
                            value = weeklyAdherence?.let { "$it%" } ?: "100%",
                            valueColor = when {
                                weeklyAdherence == null || weeklyAdherence!! >= 80 -> primary
                                weeklyAdherence!! >= 50 -> warningColor
                                else -> dangerColor
                            }
                        )
                        PharmacyStatCard(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.monthly_cost_label),
                            value = com.example.data.CurrencyFormatter.format(context, monthlyCost),
                            valueColor = textPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PharmacyCountLine(items.size, stringResource(R.string.total_medicines_label))
                        PharmacyCountLine(expiringSoon.size, stringResource(R.string.expiring_soon_label))
                        PharmacyCountLine(lowStock.size, stringResource(R.string.low_stock_label))
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
                ZadEmptyState(
                    icon = Icons.Default.Medication,
                    title = stringResource(R.string.no_medicines_hint),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ZadHubListHorizontalPadding, 8.dp, ZadHubListHorizontalPadding, ZadHubListBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(sortedItems, key = { it.id }) { item ->
                        PharmacyItemGridCard(
                            item = item,
                            daysUntilExpiry = daysUntilExpiry(item),
                            familyMemberName = familyMembers.find { it.id == item.familyMemberId }?.alias,
                            onConsumeDose = { scheduledAt -> viewModel.consumePharmacyDose(item.id, scheduledAt) },
                            onRefill = { refillTarget = item },
                            onDelete = { viewModel.deletePharmacyItem(item.id) },
                            onConfirmQuantity = { qty -> viewModel.confirmPharmacyQuantity(item.id, qty) },
                            onSetUnitsPerDose = { perDose -> viewModel.setPharmacyUnitsPerDose(item.id, perDose) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ZadHubListHorizontalPadding, 8.dp, ZadHubListHorizontalPadding, ZadHubListBottomPadding),
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
                                onSetUnitsPerDose = { perDose -> viewModel.setPharmacyUnitsPerDose(item.id, perDose) },
                                onRefill = { refillTarget = item },
                                onDelete = { viewModel.deletePharmacyItem(item.id) }
                            )
                        }
                    }
                }
            }
        }

        if (!showFamilyView) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp).pressableScale()
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

            if (showSmartAddDialog) {
                SmartAddMedicationDialog(
                    onDismiss = { showSmartAddDialog = false },
                    onSubmit = { text ->
                        viewModel.sendAiChatMessage(text)
                        showSmartAddDialog = false
                        android.widget.Toast.makeText(context, context.getString(R.string.smart_pharmacy_add_submitted), android.widget.Toast.LENGTH_LONG).show()
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
    onSetUnitsPerDose: (Double) -> Unit,
    onRefill: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDialog by remember(item.id) { mutableStateOf(false) }
    val supplyDays = item.daysOfSupplyLeft()
    val isExpired = daysUntilExpiry != null && daysUntilExpiry < 0
    val isExpiringSoon = daysUntilExpiry != null && daysUntilExpiry in 0..30
    val isLowStock = item.isLowStock()

    val statusColor = when {
        isExpired -> dangerColor
        item.remainingQuantity <= 0 || (supplyDays != null && supplyDays <= 3) -> dangerColor
        isLowStock || isExpiringSoon -> warningColor
        else -> successColor
    }

    GlassCard(
        modifier = Modifier.shadow(elevation = 6.dp, shape = com.example.ui.theme.ZadLuxe.squircle, spotColor = statusColor.copy(alpha = 0.16f)).pressableScale(),
        shape = com.example.ui.theme.ZadLuxe.squircle,
        containerColor = com.example.ui.theme.ZadLuxe.cardWhite,
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
                        // ملاحظة بس (تركيز/تعليمات). أي تكرار جواها بيتشال — التوقيت
                        // تحتها بـ4dp بيتعرض من doseTimes، والاتنين كانوا بيتناقضوا.
                        val dosageNote = com.example.data.PharmacyDoseText.sanitizeDosageNote(item.dosage)
                        if (dosageNote != null) {
                            Text(dosageNote, style = Typography.labelSmall, color = onSurfaceVariant)
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
                        Text(stringResource(R.string.pharmacy_invalid_dose_time), style = Typography.labelSmall, color = dangerColor, fontSize = 10.sp)
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
                    } else if (!isExpired && (!item.dosage.isNullOrBlank() || item.doseTimesList().isNotEmpty())) {
                        // Task 17.2.1 — never show a guessed days-left number. unitsPerDose is
                        // unknown for this item (free-text dosage was never confidently parsed),
                        // so this is an honest "we don't know" the user can resolve in one tap.
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(warningColor.copy(alpha = 0.14f))
                                .clickable { showConfirmDialog = true }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(stringResource(R.string.pharmacy_qty_needs_confirm), style = Typography.labelSmall, color = warningColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
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
                        Text(stringResource(R.string.pharmacy_how_many_left), style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmQuantityDialog(
            item = item,
            onDismiss = { showConfirmDialog = false },
            onConfirm = { qty -> onConfirmQuantity(qty); showConfirmDialog = false },
            onConfirmUnitsPerDose = onSetUnitsPerDose
        )
    }
}

/** Task 17.2.2 — manual resync: counts drift no matter how good the logging is, so there
 * must be a way to fix remaining_quantity directly without deleting/re-adding the medication.
 *
 * "الكمية محتاجة تأكيد" — the resolution path for an item whose days-of-supply
 * can't be computed.
 *
 * Two things are missing when that badge shows, not one: the real remaining
 * count *and* `units_per_dose` (how many tablets one dose actually is, which the
 * free-text dosage was never parsed into confidently). The dialog only asked for
 * the count, so `ZadViewModel.setPharmacyUnitsPerDose` — repo method, server
 * column, and all — had no caller anywhere in the app, and a two-tablet dose kept
 * being counted as one. That is the difference between "يكفي 10 أيام" and the
 * truth, which is 5.
 */
@Composable
private fun ConfirmQuantityDialog(
    item: ZadPharmacyItem,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onConfirmUnitsPerDose: (Double) -> Unit = {}
) {
    var text by remember { mutableStateOf(item.remainingQuantity.toString()) }
    var perDose by remember { mutableStateOf(item.unitsPerDose?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_quantity_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                Text("${item.name} — ${stringResource(R.string.current_recorded_quantity)}: ${item.remainingQuantity} ${item.unit}", style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.all { c -> c.isDigit() }) text = it },
                    label = { Text(item.unit) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = perDose,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("^\\d{0,2}(\\.\\d?)?$"))) perDose = v },
                    label = { Text(stringResource(R.string.units_per_dose_label, item.unit)) },
                    supportingText = { Text(stringResource(R.string.units_per_dose_hint), style = Typography.labelSmall) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                perDose.toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirmUnitsPerDose)
                text.toIntOrNull()?.let(onConfirm)
            }) { Text(stringResource(R.string.confirm_action_short)) }
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
    onConsumeDose: (String?) -> Unit,
    onRefill: () -> Unit,
    onDelete: () -> Unit,
    onConfirmQuantity: (Int) -> Unit,
    onSetUnitsPerDose: (Double) -> Unit = {}
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val supplyDays = item.daysOfSupplyLeft()
    val isExpired = daysUntilExpiry != null && daysUntilExpiry < 0
    val isExpiringSoon = daysUntilExpiry != null && daysUntilExpiry in 0..30
    val isLowStock = item.isLowStock()
    val statusColor = when {
        isExpired -> dangerColor
        item.remainingQuantity <= 0 || (supplyDays != null && supplyDays <= 3) -> dangerColor
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
        if (!isExpired && supplyDays == null && (!item.dosage.isNullOrBlank() || item.doseTimesList().isNotEmpty())) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(warningColor.copy(alpha = 0.14f))
                    .clickable { showConfirmDialog = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.pharmacy_qty_needs_confirm), style = Typography.labelSmall, color = warningColor, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Quick Action Buttons in Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.remainingQuantity > 0) {
                IconButton(
                    onClick = {
                        val firstDose = item.doseTimesList().firstOrNull()
                        onConsumeDose(firstDose?.let { com.example.data.PharmacyReminderScheduler.canonicalScheduledAt(it) })
                    },
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)).pressableScale()
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.pharm_take_dose), tint = primary, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(
                onClick = onRefill,
                modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF0F172A).copy(alpha = 0.06f)).pressableScale()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pharm_reorder), tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF0F172A).copy(alpha = 0.06f)).pressableScale()
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.pharm_edit_quantity), tint = onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmQuantityDialog(
            item = item,
            onDismiss = { showConfirmDialog = false },
            onConfirm = { qty -> onConfirmQuantity(qty); showConfirmDialog = false },
            onConfirmUnitsPerDose = onSetUnitsPerDose
        )
    }
}

/** يعرض تاريخ الانتهاء بصيغة محلية ("٩ أغسطس ٢٠٢٦") بدل الـ ISO الخام المخزّن — نفس نمط
 *  BudgetScreen's date formatting. الخام (yyyy-MM-dd) لما يترسم في حقل RTL بيختلط أرقامه
 *  اللاتينية مع خط التسمية العربي بشكل مش متسق، وهو ده اللي بيبان كأنه "خط غلط". */
private fun formatExpiryForDisplay(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        LocalDate.parse(iso).format(
            java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", com.example.data.MarketPrefs.currentMarket.toLocale())
        )
    } catch (e: Exception) {
        iso
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
    var unit by remember { mutableStateOf(PHARMACY_UNITS.first()) }
    var dailyDoseCount by remember { mutableStateOf("1") }
    var doseTimesList by remember { mutableStateOf(listOf<String>()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var expiryDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    var showAdditionalDetails by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_medicine_dialog_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).imePadding()
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.medicine_name_hint)) }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it },
                        label = { Text(stringResource(R.string.remaining_quantity_hint)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.unit_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PHARMACY_UNITS.forEach { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u, style = Typography.labelSmall) })
                        }
                    }
                }

                OutlinedTextField(
                    value = dailyDoseCount, onValueChange = { dailyDoseCount = it },
                    label = { Text(stringResource(R.string.daily_dose_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.dose_times_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf("08:00" to "🌅 صباحاً", "14:00" to "☀️ ظهراً", "20:00" to "🌙 مساءً", "23:00" to "🛌 قبل النوم")
                        items(presets) { (slot, label) ->
                            val selected = doseTimesList.contains(slot)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    doseTimesList = if (selected) doseTimesList - slot else (doseTimesList + slot).sorted()
                                    dailyDoseCount = doseTimesList.size.coerceAtLeast(1).toString()
                                },
                                label = { Text("$label $slot", style = Typography.labelSmall) }
                            )
                        }
                    }
                    if (doseTimesList.isEmpty()) {
                        Text(
                            stringResource(R.string.dose_times_empty_hint, suggestDoseTimes(dailyDoseCount.toIntOrNull() ?: 1).ifBlank { "09:00" }),
                            style = Typography.labelSmall, color = textTertiary
                        )
                    }
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(doseTimesList) { time ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(time, style = Typography.labelSmall) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close, contentDescription = stringResource(R.string.delete_action),
                                        modifier = Modifier.size(16.dp).clickable { doseTimesList = doseTimesList - time }
                                    )
                                }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showTimePicker = true },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.add_dose_time_action), style = Typography.labelSmall) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = formatExpiryForDisplay(expiryDate), onValueChange = {}, readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.expiry_date_hint)) },
                    placeholder = { Text(stringResource(R.string.pick_expiry_date_placeholder)) },
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showAdditionalDetails = !showAdditionalDetails },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.additional_details_label), style = Typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                    Icon(
                        if (showAdditionalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showAdditionalDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = activeIngredient, onValueChange = { activeIngredient = it }, label = { Text(stringResource(R.string.active_ingredient_hint)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text(stringResource(R.string.dosage_hint)) }, modifier = Modifier.fillMaxWidth())

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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDoseTimes = doseTimesList.ifEmpty {
                        suggestDoseTimes(dailyDoseCount.toIntOrNull() ?: 1).split(",").map { it.trim() }.filter { it.isNotBlank() }
                    }.joinToString(",").ifBlank { null }
                    if (name.isNotBlank()) {
                        onSave(
                            ZadPharmacyItem(
                                name = name,
                                activeIngredient = activeIngredient.ifBlank { null },
                                category = category,
                                dosage = com.example.data.PharmacyDoseText.sanitizeDosageNote(dosage),
                                remainingQuantity = quantity.toIntOrNull() ?: 1,
                                unit = unit,
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
                enabled = name.isNotBlank(),
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showTimePicker) {
        DoseTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                if (time !in doseTimesList) doseTimesList = doseTimesList + time
                showTimePicker = false
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        expiryDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm_action_short)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** إضافة دواء بالكلام الحر — النص بيتبعت لنفس مسار الوكيل الحقيقي (agent_turn)، فالعقل
 *  هو اللي بيفهم الاسم والجرعة والمواعيد ويستخدم أداة add_pharmacy_item، مش تحليل محلي
 *  هنا. الحوار بيقفل فور الإرسال — التأكيد بييجي إما هنا (Toast) أو في الشات لو العقل
 *  احتاج يسأل توضيح (زي أي معاملة مالية غامضة). */
@Composable
internal fun SmartAddMedicationDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smart_pharmacy_add_dialog_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                Text(stringResource(R.string.smart_pharmacy_add_dialog_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.smart_pharmacy_add_dialog_placeholder)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSubmit(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.smart_pharmacy_add_dialog_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** M3 مالوش TimePickerDialog جاهز — بنلفه بنفسنا حوالين TimePicker جوه AlertDialog. */
@Composable
private fun DoseTimePickerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_dose_time_dialog_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text(stringResource(R.string.confirm_action_short)) }
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
    var showDatePicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.renew_order_dialog_title, item.name), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(
                    value = addedQuantity, onValueChange = { addedQuantity = it },
                    label = { Text(stringResource(R.string.added_quantity_hint, item.unit)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPrice, onValueChange = { newPrice = it },
                    label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formatExpiryForDisplay(newExpiryDate), onValueChange = {}, readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.expiry_date_hint)) },
                    placeholder = { Text(stringResource(R.string.pick_expiry_date_placeholder)) },
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        newExpiryDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm_action_short)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** The mockup's pharmacy stat card: white, 16dp, small grey label over a bold value. */
@Composable
private fun PharmacyStatCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = com.example.ui.theme.ZadLuxe.squircle
    Column(
        modifier = modifier
            .clip(shape)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textTertiary, maxLines = 1)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

/** Inventory counts, demoted from the old gradient hero to one quiet line. */
@Composable
private fun PharmacyCountLine(count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$count", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = textSecondary)
        Text(label, fontSize = 11.5.sp, color = textTertiary)
    }
}

/**
 * "أدوية العيلة" — رؤية بس (family_admin_read_pharmacy migration، 2026-09-01)، متاحة
 * للوالدين (role=admin) بس. مفيش تعديل/حذف هنا عن قصد: الدوا شخصي وحساس.
 *
 * كانت شاشة/route منفصل (`FamilyPharmacyScreen`), دُمجت هنا كـ Toggle داخل PharmacyScreen
 * نفسها — UI_ARCHITECTURE_SPEC.md §2.4/§4.3.
 */
@Composable
private fun PharmacyFamilyBody(
    itemsByMember: Map<com.example.data.FamilyMember, List<ZadPharmacyItem>>,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        loading -> ZadLoadingState(modifier = modifier)
        itemsByMember.isEmpty() -> ZadEmptyState(
            icon = Icons.Default.LocalPharmacy,
            title = stringResource(R.string.family_pharmacy_empty_title),
            subtitle = stringResource(R.string.family_pharmacy_empty_subtitle),
            modifier = modifier
        )
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = ZadHubListBottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsByMember.forEach { (member, memberItems) ->
                item(key = "header_${member.id}") { FamilyPharmacyMemberHeader(member) }
                items(memberItems, key = { it.id }) { med -> FamilyPharmacyItemRow(med) }
            }
        }
    }
}

@Composable
private fun FamilyPharmacyMemberHeader(member: com.example.data.FamilyMember) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                member.alias.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟",
                style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(member.alias, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
    }
}

@Composable
private fun FamilyPharmacyItemRow(item: ZadPharmacyItem) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(com.example.ui.theme.ZadLuxe.squircle)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, com.example.ui.theme.ZadLuxe.squircle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = onSurface)
            val dosageNote = com.example.data.PharmacyDoseText.sanitizeDosageNote(item.dosage)
            if (dosageNote != null) {
                Spacer(Modifier.height(2.dp))
                Text(dosageNote, style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
        val lowStock = item.isLowStock()
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background((if (lowStock) dangerColor else successColor).copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "${item.remainingQuantity} ${item.unit}",
                style = Typography.labelSmall,
                color = if (lowStock) dangerColor else successColor,
                fontWeight = FontWeight.Bold, fontSize = 11.sp
            )
        }
    }
}
