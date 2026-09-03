package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadMaintenanceItem
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadTransitions
import com.example.ui.components.ZadListCard
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel

private val MAINTENANCE_CATEGORIES = listOf("عام", "تكييف", "سخان", "غسالة", "سيارة", "فلتر مياه")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    viewModel: ZadViewModel
) {
    val items by viewModel.maintenanceItems.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val dueSoon = items.filter { val d = it.daysUntilService(); d != null && d in 0..14 }
    val overdue = items.filter { val d = it.daysUntilService(); d != null && d < 0 }
    val warrantyExpiring = items.filter { val d = it.daysUntilWarrantyExpiry(); d != null && d in 0..30 }

    val sortedItems = items.sortedWith(
        compareBy(
            { val d = it.daysUntilService(); if (d != null && d < 0) 0 else 1 },
            { it.daysUntilService() ?: Int.MAX_VALUE }
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The mockup keeps this screen to plain white cards — the blue→indigo
            // gradient banner that stood here was the only place in the app using
            // that pair, and it fought the green identity for the top of the page.
            AppearOnEntry {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MaintenanceSummaryStat(Modifier.weight(1f), items.size.toString(), stringResource(R.string.total_appliances_label), textPrimary)
                    MaintenanceSummaryStat(Modifier.weight(1f), dueSoon.size.toString(), stringResource(R.string.due_soon_label), secondaryDark)
                    MaintenanceSummaryStat(Modifier.weight(1f), warrantyExpiring.size.toString(), stringResource(R.string.warranty_expiring_label), dangerColor)
                }
            }

            if (overdue.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(dangerColor.copy(alpha = 0.12f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.overdue_maintenance_warning, overdue.size),
                            style = Typography.bodySmall, color = dangerColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, ZadHubListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sortedItems.isEmpty()) {
                    item {
                        ZadEmptyState(
                            icon = Icons.Default.Build,
                            title = stringResource(R.string.no_maintenance_items_hint),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        )
                    }
                } else {
                    itemsIndexed(sortedItems, key = { _, it -> it.id }) { index, item ->
                        var itemVisible by remember(item.id) { mutableStateOf(false) }
                        LaunchedEffect(item.id) { itemVisible = true }
                        AnimatedVisibility(visible = itemVisible, enter = ZadTransitions.listItemEnter(index)) {
                            MaintenanceItemCard(
                                item = item,
                                onMarkServiced = { viewModel.markMaintenanceServicedToday(item.id) },
                                onDelete = { viewModel.deleteMaintenanceItem(item.id) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp).pressableScale()
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_action))
        }

        if (showAddDialog) {
            AddMaintenanceItemDialog(
                onDismiss = { showAddDialog = false },
                onSave = { item ->
                    viewModel.addMaintenanceItem(item)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun MaintenanceSummaryStat(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    valueColor: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textTertiary, maxLines = 2)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun MaintenanceItemCard(
    item: ZadMaintenanceItem,
    onMarkServiced: () -> Unit,
    onDelete: () -> Unit
) {
    val daysUntilService = item.daysUntilService()
    val daysUntilWarranty = item.daysUntilWarrantyExpiry()
    val isOverdue = daysUntilService != null && daysUntilService < 0
    val isDueSoon = daysUntilService != null && daysUntilService in 0..14

    val statusColor = when {
        isOverdue -> dangerColor
        isDueSoon -> warningColor
        else -> successColor
    }

    // نفس كارت القائمة المشترك (ZadListCard) اللي باقي الشاشات بتستخدمه — بدل
    // Card + shadow يدوي بنصف قطر وارتفاع مختلفين في كل شاشة.
    val cardShape = RoundedCornerShape(16.dp)
    // ── صف الصيانة بستايل المرجع (renderMaintenance): كارت أبيض 16dp، الاسم +
    // سطر الضمان رمادي على الشمال، وchip الحالة الملون على اليمين. من غير
    // الشريط الجانبي الملون ولا أيقونة الدايرة — المرجع بيشتغل بالكلمات والـ chip.
    ZadListCard(
        modifier = Modifier.pressableScale(),
        shape = cardShape,
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Text(
                    when {
                        daysUntilWarranty != null && daysUntilWarranty < 0 -> stringResource(R.string.warranty_expired_label)
                        daysUntilWarranty != null -> stringResource(R.string.warranty_expires_in_days, daysUntilWarranty)
                        else -> item.category
                    },
                    fontSize = 11.5.sp,
                    color = textTertiary
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val (pillBg, pillFg) = when {
                    isOverdue -> dangerColor.copy(alpha = 0.12f) to dangerColor
                    isDueSoon -> ZadV2.warn.copy(alpha = 0.10f) to ZadV2.warn
                    else -> primary.copy(alpha = 0.06f) to primary
                }
                Box(
                    modifier = Modifier
                        .clip(ZadV2.rPill)
                        .background(pillBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        when {
                            isOverdue -> stringResource(R.string.service_overdue_days, -(daysUntilService ?: 0))
                            daysUntilService != null -> stringResource(R.string.service_due_in_days, daysUntilService)
                            else -> stringResource(R.string.due_soon_label)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillFg
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.serviceIntervalDays != null) {
                        Text(
                            stringResource(R.string.mark_serviced_action),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            modifier = Modifier
                                .clip(ZadV2.rPill)
                                .clickable { onMarkServiced() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_action),
                        tint = textTertiary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDelete() }
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMaintenanceItemDialog(onDismiss: () -> Unit, onSave: (ZadMaintenanceItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MAINTENANCE_CATEGORIES.first()) }
    var purchaseDate by remember { mutableStateOf("") }
    var warrantyExpiryDate by remember { mutableStateOf("") }
    var serviceIntervalDays by remember { mutableStateOf("") }
    var estimatedCost by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_maintenance_dialog_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).imePadding()
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.appliance_name_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = { Text(stringResource(R.string.purchase_date_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = warrantyExpiryDate, onValueChange = { warrantyExpiryDate = it }, label = { Text(stringResource(R.string.warranty_expiry_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serviceIntervalDays, onValueChange = { serviceIntervalDays = it }, label = { Text(stringResource(R.string.service_interval_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = estimatedCost, onValueChange = { estimatedCost = it }, label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())

                Text(stringResource(R.string.category_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MAINTENANCE_CATEGORIES) { cat ->
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
                    if (name.isNotBlank()) {
                        onSave(
                            ZadMaintenanceItem(
                                name = name,
                                category = category,
                                purchaseDate = purchaseDate.ifBlank { null },
                                warrantyExpiryDate = warrantyExpiryDate.ifBlank { null },
                                serviceIntervalDays = serviceIntervalDays.toIntOrNull(),
                                estimatedCost = estimatedCost.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                },
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
