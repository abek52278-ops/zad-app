package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadMaintenanceItem
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.components.ZadListCard
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel

private val MAINTENANCE_CATEGORIES = listOf("عام", "تكييف", "سخان", "غسالة", "سيارة", "فلتر مياه")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit = {}
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
            Row(
                modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu), tint = onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.maintenance_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
            }

            AppearOnEntry {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = infoColor.copy(alpha = 0.20f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(infoColor, tertiary)))
                        .padding(20.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MaintenanceSummaryStat(items.size.toString(), stringResource(R.string.total_appliances_label))
                        MaintenanceSummaryStat(dueSoon.size.toString(), stringResource(R.string.due_soon_label))
                        MaintenanceSummaryStat(warrantyExpiring.size.toString(), stringResource(R.string.warranty_expiring_label))
                    }
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
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sortedItems.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        ) {
                            ZadLottieAsset(resId = R.raw.lottie_empty_box, modifier = Modifier.size(140.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.no_maintenance_items_hint),
                                style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = onSurface, textAlign = TextAlign.Center
                            )
                        }
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
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 88.dp).pressableScale()
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
private fun MaintenanceSummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = Typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
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
    ZadListCard(
        modifier = Modifier.pressableScale(),
        shape = cardShape,
        contentPadding = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(statusColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(catTransportBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = catTransportIcon, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(item.category, style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).pressableScale()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = dangerColor, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (daysUntilService != null) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(
                                when {
                                    isOverdue -> stringResource(R.string.service_overdue_days, -daysUntilService)
                                    else -> stringResource(R.string.service_due_in_days, daysUntilService)
                                },
                                style = Typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                            )
                        }
                    }
                    if (daysUntilWarranty != null && daysUntilWarranty in 0..30) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(warningColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(
                                stringResource(R.string.warranty_expires_in_days, daysUntilWarranty),
                                style = Typography.labelSmall, color = warningColor, fontSize = 11.sp
                            )
                        }
                    }
                }
                if (item.serviceIntervalDays != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onMarkServiced,
                        modifier = Modifier.height(30.dp).pressableScale(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.mark_serviced_action), style = Typography.labelSmall)
                    }
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
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
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
