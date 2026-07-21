package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ZadSubscription
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showInactive by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val tabs = listOf("الكل", "اشتراكات", "فواتير", "أقساط")
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
                        Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الاشتراكات", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
                }
                Row {
                    FilterChip(
                        selected = !showInactive,
                        onClick = { showInactive = false },
                        label = { Text("النشطة", style = Typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryContainer),
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = showInactive,
                        onClick = { showInactive = true },
                        label = { Text("الكل", style = Typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = surfaceContainerHigh),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Summary Banner
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(listOf(primary, primaryLight))
                    ).padding(20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إجمالي الاشتراكات الشهرية", style = Typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(com.example.data.CurrencyFormatter.format(context, totalMonthly), style = Typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("اشتراكات نشطة", style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text("${activeSubs.size}", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                        Text("زاد يبحث عن اشتراكاتك من المعاملات المالية...", style = Typography.bodySmall, color = primary)
                    }
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
                        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Subscriptions, contentDescription = null, tint = onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(if (selectedTab == 0) "لا توجد اشتراكات" else "لا توجد عناصر في هذا التصنيف", style = Typography.titleMedium, color = onSurfaceVariant)
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { sub ->
                        SubScreenSubscriptionCardFull(
                            sub = sub,
                            onToggleActive = { viewModel.updateSubscriptionActive(sub.id, !sub.isActive) },
                            onDelete = { viewModel.deleteSubscription(sub.id) }
                        )
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 88.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة")
        }

        if (showAddDialog) {
            AddSubscriptionDialog(
                onDismiss = { showAddDialog = false },
                onSave = { title, amount, renewalDate, provider ->
                    viewModel.addSubscription(
                        ZadSubscription(
                            title = title,
                            amount = amount,
                            renewalDate = renewalDate,
                            provider = provider,
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

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (sub.isActive) surface else surfaceContainerLow)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                if (sub.provider != null) {
                    Text(sub.provider, style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
            Text(com.example.data.CurrencyFormatter.format(context, sub.amount),
                style = Typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (sub.isActive) onSurface else onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onToggleActive, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (sub.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (sub.isActive) "تعطيل" else "تفعيل",
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

@Composable
fun AddSubscriptionDialog(onDismiss: () -> Unit, onSave: (String, Double, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("اشتراك") }
    var renewalDate by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة اشتراك جديد", style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم الاشتراك (مثال: Netflix)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("المبلغ (${com.example.data.CurrencyFormatter.symbol(context)})") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("مزود الخدمة") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = renewalDate, onValueChange = { renewalDate = it }, label = { Text("تاريخ التجديد (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedAmount = amount.toDoubleOrNull() ?: return@Button
                if (title.isNotBlank()) {
                    onSave(title, parsedAmount,
                        if (renewalDate.isNotBlank()) renewalDate else LocalDate.now().plusMonths(1).toString(),
                        provider)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
