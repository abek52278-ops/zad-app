package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieConstants
import com.example.R
import com.example.data.ZadTransaction
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel

enum class TxFilter(val label: String, val icon: ImageVector) {
    ALL("الكل", Icons.Outlined.AccountBalanceWallet),
    PURCHASES("مشتريات", Icons.Outlined.ShoppingCart),
    SUBSCRIPTIONS("اشتراكات", Icons.Outlined.Subscriptions),
    TRANSFERS("تحويلات", Icons.Outlined.SwapHoriz),
    SALARY("رواتب", Icons.Outlined.AccountBalance),
    FOOD("مطاعم", Icons.Outlined.Restaurant)
}

data class TransactionGroup(val date: String, val transactions: List<ZadTransaction>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val habitChips by viewModel.habitChips.collectAsState()

    var selectedFilter by remember { mutableStateOf(TxFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<ZadTransaction?>(null) }
    var showEditCategory by remember { mutableStateOf<ZadTransaction?>(null) }

    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalSpent = transactions.filter { it.isExpense }.sumOf { it.amount }
    val balance = totalIncome - totalSpent

    val filteredTx = remember(transactions, selectedFilter, searchQuery) {
        val filtered = when (selectedFilter) {
            TxFilter.ALL -> transactions
            TxFilter.PURCHASES -> transactions.filter { it.isExpense && (it.category == "بقالة" || it.category == "تسوق" || it.category == "مشتريات" || it.category == null) }
            TxFilter.SUBSCRIPTIONS -> transactions.filter { it.category == "اشتراكات" || it.category == "فواتير" }
            TxFilter.TRANSFERS -> transactions.filter { !it.isExpense || it.category == "تحويل" }
            TxFilter.SALARY -> transactions.filter { !it.isExpense && it.category == "راتب" }
            TxFilter.FOOD -> transactions.filter { it.category == "مطاعم" || it.category == "مقاهي" }
        }
        if (searchQuery.isBlank()) filtered
        else filtered.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.merchantName?.contains(searchQuery, ignoreCase = true) == true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        TransactionTopBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onOpenDrawer = onOpenDrawer
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Summary header
            item {
                TransactionSummaryHeader(
                    income = totalIncome,
                    expense = totalSpent,
                    balance = balance,
                    budget = budget
                )
            }

            // Filter chips
            item {
                FilterChipsRow(
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it }
                )
            }

            // Task 22 — habit chips: one tap logs a recurring, amount-consistent expense
            // (e.g. "قهوة ٢٥") without opening the add dialog. Empty when the user has no
            // stable pattern yet — zad_habit_chips() already filters that server-side.
            if (habitChips.isNotEmpty()) {
                item {
                    HabitChipsRow(
                        chips = habitChips,
                        onChipTap = { chip ->
                            viewModel.addTransaction(
                                ZadTransaction(
                                    amount = chip.amount,
                                    title = chip.label,
                                    category = chip.category,
                                    isExpense = true,
                                    wallet = "cash",
                                    createdAt = java.time.Instant.now().toString()
                                )
                            )
                        }
                    )
                }
            }

            // Transactions list header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "المعاملات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(
                        "${filteredTx.size} معاملة",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceVariant
                    )
                }
            }

            if (filteredTx.isEmpty()) {
                item {
                    EmptyTransactionsPlaceholder(searchQuery.isNotBlank() || selectedFilter != TxFilter.ALL)
                }
            } else {
                val grouped = groupByDate(filteredTx.sortedByDescending { it.createdAt ?: "" })
                grouped.forEach { group ->
                    item {
                        DateHeader(group.date)
                    }
                    itemsIndexed(group.transactions, key = { _, tx -> tx.id }) { index, tx ->
                        androidx.compose.animation.AnimatedVisibility(visible = true, enter = ZadTransitions.listItemEnter(index)) {
                            TransactionCard(
                                transaction = tx,
                                onClick = { showEditCategory = tx },
                                onDelete = { showDeleteConfirm = tx }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // FABs
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = Color(0xFFFACC15),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "تصوير فاتورة")
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primary,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة معاملة")
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { amount, title, isExpense, category ->
                viewModel.addTransaction(amount, title, isExpense, category)
                showAddDialog = false
            }
        )
    }

    showDeleteConfirm?.let { tx ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف المعاملة") },
            text = { Text("هل أنت متأكد من حذف معاملة \"${tx.title}\"؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(tx.id)
                    showDeleteConfirm = null
                }) { Text("حذف", color = dangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("إلغاء") }
            }
        )
    }

    showEditCategory?.let { tx ->
        EditCategoryDialog(
            transaction = tx,
            onDismiss = { showEditCategory = null },
            onSave = { newCategory ->
                viewModel.updateTransactionCategory(tx.id, newCategory)
                showEditCategory = null
            }
        )
    }
}

@Composable
private fun EditCategoryDialog(transaction: ZadTransaction, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf(transaction.category ?: "أخرى") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_category_dialog_title)) },
        text = {
            Column {
                if (!transaction.merchantName.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.category_will_be_remembered_hint, transaction.merchantName),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(com.example.data.BudgetTracker.STANDARD_CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedCategory) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ز", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("المعاملات", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = onSurface)
                        Text("بنك زاد", fontSize = 11.sp, color = onSurfaceVariant)
                    }
                }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = "بحث", tint = onSurfaceVariant)
                }
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = onSurfaceVariant)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
        )

        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("ابحث عن معاملة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onSurfaceVariant) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = outlineVariant,
                    focusedBorderColor = primary
                )
            )
        }
    }
}

@Composable
private fun TransactionSummaryHeader(income: Double, expense: Double, balance: Double, budget: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val usagePercent = if (budget > 0) (expense / budget * 100).toFloat().coerceIn(0f, 100f) else 0f
    val isOverBudget = expense > budget

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = primary.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isOverBudget) listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                    else listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الرصيد الحالي", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (balance >= 0) successColor else dangerColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (balance >= 0) "إيجابي" else "سلبي",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    com.example.data.CurrencyFormatter.formatNumber(context, balance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(com.example.data.CurrencyFormatter.symbol(context), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat(label = "الدخل", amount = income, color = successColor)
                SummaryStat(label = "المصروفات", amount = expense, color = Color(0xFFFF6B6B))
                SummaryStat(label = "الميزانية", amount = budget, color = Color(0xFF64B5F6))
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                val animatedProgress by animateFloatAsState(
                    targetValue = usagePercent / 100f,
                    animationSpec = tween(1000)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isOverBudget) dangerColor else successColor)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${usagePercent.toInt()}% من الميزانية",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            String.format("%,.0f", amount),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun FilterChipsRow(selected: TxFilter, onSelected: (TxFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TxFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) primary else surface)
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = outlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        filter.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        filter.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else onSurface
                    )
                }
            }
        }
    }
}

/** Task 22 — كارت لكل عادة صرف ثابتة، Tap واحد يسجّلها كمصروف كاش فوري */
@Composable
private fun HabitChipsRow(chips: List<com.example.data.HabitChip>, onChipTap: (com.example.data.HabitChip) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            "عادات صرفك",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = onSurfaceVariant
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chips, key = { it.label + it.category }) { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(surface)
                        .border(1.dp, outlineVariant, RoundedCornerShape(20.dp))
                        .clickable { onChipTap(chip) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bolt, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                com.example.data.CurrencyFormatter.formatNumber(context, chip.amount),
                                fontSize = 11.sp,
                                color = onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(outlineVariant))
        Spacer(Modifier.width(12.dp))
        Text(date, fontSize = 12.sp, color = onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(outlineVariant))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TransactionCard(
    transaction: ZadTransaction,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize()
            .combinedClickable(onClick = onClick, onLongClick = onDelete),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val (icon, iconBg, iconColor) = getCategoryMeta(transaction)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.merchantName != null) {
                        Text(transaction.merchantName, fontSize = 11.sp, color = onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Dot()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        transaction.category ?: "عام",
                        fontSize = 11.sp,
                        color = onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (transaction.isExpense) "-" else "+"}${String.format("%,.2f", transaction.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (transaction.isExpense) dangerColor else successColor
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.isVerified) {
                        Icon(Icons.Default.Verified, contentDescription = "موثقة", tint = primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (transaction.bankName != null) {
                        Text(
                            transaction.bankName,
                            fontSize = 10.sp,
                            color = onSurfaceVariant
                        )
                    } else {
                        Text(
                            formatTime(transaction.createdAt),
                            fontSize = 10.sp,
                            color = onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot() {
    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(onSurfaceVariant.copy(alpha = 0.4f)))
}

private fun getCategoryMeta(tx: ZadTransaction): Triple<ImageVector, Color, Color> {
    val cat = tx.category ?: "عام"
    return when {
        cat == "راتب" || cat == "دخل" -> Triple(Icons.Default.AccountBalance, catBankingBg, catBankingIcon)
        cat == "بقالة" || cat == "سوبرماركت" -> Triple(Icons.Default.ShoppingCart, catFoodBg, catFoodIcon)
        cat == "مطاعم" || cat == "مقاهي" -> Triple(Icons.Default.Restaurant, tertiaryContainer, onTertiaryContainer)
        cat == "فواتير" || cat == "اشتراكات" -> Triple(Icons.Default.Subscriptions, catBillsBg, catBillsIcon)
        cat == "مواصلات" || cat == "بنزين" -> Triple(Icons.Default.DirectionsCar, catTransportBg, catTransportIcon)
        cat == "تحويل" -> Triple(Icons.Default.SwapHoriz, secondaryContainer, onSecondaryContainer)
        cat == "تسوق" || cat == "ملابس" -> Triple(Icons.Default.ShoppingBag, Color(0xFFFFF3E0), Color(0xFFF57C00))
        cat == "صحة" || cat == "دواء" -> Triple(Icons.Default.MedicalServices, Color(0xFFFCE4EC), Color(0xFFD81B60))
        cat == "تعليم" || cat == "مدرسة" -> Triple(Icons.Default.School, Color(0xFFE8EAF6), Color(0xFF3F51B5))
        !tx.isExpense -> Triple(Icons.Default.ArrowDownward, catSavingsBg, catSavingsIcon)
        else -> Triple(Icons.Default.Receipt, surfaceContainerHigh, onSurfaceVariant)
    }
}

private fun formatTime(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        val instant = java.time.Instant.parse(createdAt)
        val zdt = instant.atZone(java.time.ZoneId.of("Asia/Riyadh"))
        val hour = zdt.hour
        val min = zdt.minute
        val amPm = if (hour < 12) "ص" else "م"
        val h = if (hour % 12 == 0) 12 else hour % 12
        "$h:$min$amPm"
    } catch (e: Exception) {
        ""
    }
}

private fun groupByDate(transactions: List<ZadTransaction>): List<TransactionGroup> {
    val map = linkedMapOf<String, MutableList<ZadTransaction>>()
    transactions.forEach { tx ->
        val date = try {
            val instant = java.time.Instant.parse(tx.createdAt ?: "")
            val zdt = instant.atZone(java.time.ZoneId.of("Asia/Riyadh"))
            val monthNames = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
            "${zdt.dayOfMonth} ${monthNames[zdt.monthValue - 1]}"
        } catch (e: Exception) {
            "تاريخ غير معروف"
        }
        map.getOrPut(date) { mutableListOf() }.add(tx)
    }
    return map.map { TransactionGroup(it.key, it.value) }
}

@Composable
private fun EmptyTransactionsPlaceholder(isFiltered: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ZadLottieAsset(
                resId = R.raw.lottie_empty_box,
                modifier = Modifier.size(140.dp),
                iterations = LottieConstants.IterateForever,
                contentDescription = null
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isFiltered) "لا توجد معاملات مطابقة" else "لا توجد معاملات بعد",
                fontSize = 16.sp,
                color = onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isFiltered) "حاول تغيير الفلتر أو البحث" else "سيتم عرض المعاملات البنكية هنا",
                fontSize = 13.sp,
                color = onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
