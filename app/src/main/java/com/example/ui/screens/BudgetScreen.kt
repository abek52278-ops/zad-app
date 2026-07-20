package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.data.ZadTransaction
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Transactions Screen (STC Pay style) ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val showBudgetDialog by viewModel.showBudgetDialog.collectAsState()
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("الكل") }
    val context = LocalContext.current
    var categoryCardsRefresh by remember { mutableIntStateOf(0) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    // المصروف الفعلي بيتحسب من المعاملات مباشرة (يشمل اليدوية + البنكية) — الميزانية من BudgetTracker
    val categoryCards = remember(transactions, categoryCardsRefresh) {
        val now = java.time.LocalDate.now()
        val spentByCategory = transactions.filter { tx ->
            if (!tx.isExpense) return@filter false
            try {
                val d = Instant.parse(tx.createdAt ?: "").atZone(ZoneId.systemDefault()).toLocalDate()
                d.monthValue == now.monthValue && d.year == now.year
            } catch (e: Exception) { false }
        }.groupBy { it.category ?: "أخرى" }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

        com.example.data.BudgetTracker.STANDARD_CATEGORIES
            .map { cat ->
                Triple(cat, com.example.data.BudgetTracker.getCategoryBudget(context, cat), spentByCategory[cat] ?: 0.0)
            }
            .filter { (_, catBudget, spent) -> catBudget > 0 || spent > 0 }
            .sortedByDescending { it.third }
    }

    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalSpent = transactions.filter { it.isExpense }.sumOf { it.amount }
    val currentBalance = remainingBalance

    // Animate balance changes
    val animatedBalance by animateFloatAsState(
        targetValue = currentBalance.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    )

    val filteredTx = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "المصروفات" -> transactions.filter { it.isExpense }
            "الدخل" -> transactions.filter { !it.isExpense }
            "البنك" -> transactions.filter { it.sourceType == "bank_sms" || it.sourceType == "bank_notification" }
            else -> transactions
        }.sortedByDescending { it.createdAt ?: "" }
    }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Header card ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(primary, Color(0xFF094730))
                            )
                        )
                ) {
                    // Decorative circles
                    Box(
                        modifier = Modifier
                            .offset(x = (-30).dp, y = (-30).dp)
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.04f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 40.dp, y = (-20).dp)
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.04f))
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        // TopBar row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                            Text(
                                "المعاملات",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { viewModel.showBudgetDialog() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Budget", tint = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Balance display (animated)
                        Text(
                            "الرصيد المتبقي",
                            style = Typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${String.format("%,.0f", animatedBalance)} ر.س",
                            style = Typography.displayLarge.copy(fontSize = 40.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Income / Spent summary row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TxSummaryItem(
                                label = "الدخل",
                                amount = totalIncome,
                                icon = Icons.Default.TrendingUp,
                                color = Color(0xFF34C77B)
                            )
                            Box(
                                modifier = Modifier
                                    .height(50.dp)
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                            TxSummaryItem(
                                label = "المصروفات",
                                amount = totalSpent,
                                icon = Icons.Default.TrendingDown,
                                color = Color(0xFFFF6B6B)
                            )
                            Box(
                                modifier = Modifier
                                    .height(50.dp)
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                            TxSummaryItem(
                                label = "الميزانية",
                                amount = budget,
                                icon = Icons.Default.AccountBalanceWallet,
                                color = Color(0xFFE8BC6A)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // ── AI insight strip ────────────────────────────────────────────
            item {
                val spentPct = if (budget > 0) (totalSpent / budget * 100).toInt() else 0
                val (aiIcon, aiMsg) = when {
                    spentPct >= 100 -> Icons.Default.Block to "تجاوزت الميزانية! حاول تقليل المصاريف"
                    spentPct >= 85 -> Icons.Default.Warning to "وصلت لـ $spentPct% من ميزانيتك. كن حذراً"
                    spentPct >= 60 -> Icons.Default.Lightbulb to "صرفت $spentPct% من ميزانيتك. أداء جيد"
                    else -> Icons.Default.CheckCircle to "رائع! أنت في المسار الصحيح. صرفت $spentPct% فقط"
                }
                val stripColor = when {
                    spentPct >= 100 -> Color(0xFFFF4444)
                    spentPct >= 85 -> Color(0xFFF59E0B)
                    else -> primary
                }

                val pressed = remember { MutableInteractionSource() }
                val isPressed by pressed.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.97f else 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .background(stripColor.copy(alpha = 0.08f))
                        .clickable(interactionSource = pressed, indication = null, onClick = onNavigateToAssistant)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        aiIcon,
                        contentDescription = null,
                        tint = stripColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        aiMsg,
                        style = Typography.bodyMedium,
                        color = stripColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = stripColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Category Budget Cards ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ميزانيات الفئات", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    TextButton(onClick = { editingCategory = "__NEW__" }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحديد فئة", style = Typography.labelMedium, color = primary)
                    }
                }
            }
            if (categoryCards.isEmpty()) {
                item {
                    Text(
                        "لسه ما حددتش ميزانية لأي فئة. اضغط \"تحديد فئة\" عشان زاد يتابعلك كل فئة لوحدها.",
                        style = Typography.bodySmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryCards.forEach { (cat, catBudget, spent) ->
                            CategoryBudgetCard(
                                category = cat,
                                budget = catBudget,
                                spent = spent,
                                onClick = { editingCategory = cat }
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ── Filter chips (Material3) ─────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("الكل", "المصروفات", "الدخل", "البنك").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
            }

            // ── Month total strip ───────────────────────────────────────────
            item {
                val thisMonthTx = filteredTx.filter { tx ->
                    try {
                        val inst = Instant.parse(tx.createdAt ?: "")
                        val txDate = inst.atZone(ZoneId.systemDefault()).toLocalDate()
                        val now = java.time.LocalDate.now()
                        txDate.year == now.year && txDate.month == now.month
                    } catch (e: Exception) { false }
                }
                val monthSpent = thisMonthTx.filter { it.isExpense }.sumOf { it.amount }
                val monthIncome = thisMonthTx.filter { !it.isExpense }.sumOf { it.amount }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        "هذا الشهر",
                        style = Typography.labelMedium,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (monthIncome > 0) {
                            Text(
                                "+${String.format("%,.0f", monthIncome)} ر.س",
                                style = Typography.bodyMedium,
                                color = successColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (monthSpent > 0) {
                            Text(
                                "−${String.format("%,.0f", monthSpent)} ر.س",
                                style = Typography.bodyMedium,
                                color = dangerColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Transactions grouped by date ────────────────────────────────
            if (filteredTx.isEmpty()) {
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "لا توجد معاملات بعد",
                                style = Typography.titleMedium,
                                color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "أضف معاملة أو اربط البنك لتتبع مصاريفك تلقائياً",
                                style = Typography.bodySmall,
                                color = onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                val grouped = filteredTx.groupBy { tx ->
                    try {
                        val inst = Instant.parse(tx.createdAt ?: "")
                        inst.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    } catch (e: Exception) { "اليوم" }
                }

                grouped.forEach { (dateStr, txList) ->
                    item {
                        TxDateHeader(dateStr = dateStr, txList = txList)
                    }
                    items(txList, key = { it.id }) { tx ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            TxRowItem(
                                tx = tx,
                                onDelete = { viewModel.deleteTransaction(tx.id) }
                            )
                        }
                    }
                }
            }
        }

        // ── FABs with micro-interactions ───────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            val scanSource = remember { MutableInteractionSource() }
            val scanPressed by scanSource.collectIsPressedAsState()
            val scanScale by animateFloatAsState(if (scanPressed) 0.92f else 1f)

            SmallFloatingActionButton(
                onClick = onNavigateToCamera,
                modifier = Modifier.scale(scanScale),
                interactionSource = scanSource,
                containerColor = secondary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = "Scan", modifier = Modifier.size(20.dp))
            }

            val addSource = remember { MutableInteractionSource() }
            val addPressed by addSource.collectIsPressedAsState()
            val addScale by animateFloatAsState(if (addPressed) 0.95f else 1f)

            FloatingActionButton(
                onClick = { showAddTransactionDialog = true },
                modifier = Modifier.scale(addScale),
                interactionSource = addSource,
                containerColor = primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                    Text("معاملة", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            onDismiss = { showAddTransactionDialog = false },
            onSave = { amount, title, isExpense, category ->
                viewModel.addTransaction(
                    com.example.data.ZadTransaction(
                        amount = amount, title = title,
                        isExpense = isExpense, category = category
                    )
                )
                showAddTransactionDialog = false
            }
        )
    }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentBudget = budget,
            onDismiss = { viewModel.hideBudgetDialog() },
            onSave = { newBudget ->
                viewModel.updateBudget(newBudget)
                viewModel.hideBudgetDialog()
            }
        )
    }

    editingCategory?.let { cat ->
        val isNew = cat == "__NEW__"
        CategoryBudgetEditDialog(
            category = if (isNew) null else cat,
            currentBudget = if (isNew) 0.0 else com.example.data.BudgetTracker.getCategoryBudget(context, cat),
            onDismiss = { editingCategory = null },
            onSave = { chosenCategory, amount ->
                com.example.data.BudgetTracker.setCategoryBudget(context, chosenCategory, amount)
                categoryCardsRefresh++
                editingCategory = null
            }
        )
    }
}

@Composable
private fun CategoryBudgetCard(category: String, budget: Double, spent: Double, onClick: () -> Unit) {
    val pct = if (budget > 0) (spent / budget * 100).toInt() else 0
    val overBudget = budget > 0 && spent > budget
    val barColor = when {
        budget == 0.0 -> onSurfaceVariant.copy(alpha = 0.4f)
        overBudget -> dangerColor
        pct >= 85 -> warningColor
        else -> primary
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700), label = "catBudgetBar"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, style = Typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                Text(
                    if (budget > 0) "${String.format("%,.0f", spent)} / ${String.format("%,.0f", budget)} ر.س"
                    else "${String.format("%,.0f", spent)} ر.س — بدون حد",
                    style = Typography.labelSmall,
                    color = if (overBudget) dangerColor else onSurfaceVariant,
                    fontWeight = if (overBudget) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(surfaceContainer)
            ) {
                if (budget > 0) {
                    Box(
                        modifier = Modifier.fillMaxWidth(animatedFraction).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp)).background(barColor)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryBudgetEditDialog(
    category: String?,
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(category ?: com.example.data.BudgetTracker.STANDARD_CATEGORIES.first()) }
    var amountText by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toInt().toString() else "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category != null) "تعديل ميزانية $category" else "تحديد ميزانية فئة", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (category == null) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الفئة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            com.example.data.BudgetTracker.STANDARD_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; expanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("الميزانية الشهرية (ر.س)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                onSave(category ?: selectedCategory, amount)
            }) { Text("حفظ", fontWeight = FontWeight.Bold, color = primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun TxSummaryItem(label: String, amount: Double, icon: ImageVector, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(
            "${String.format("%,.0f", amount)}",
            style = Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(label, style = Typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun TxDateHeader(dateStr: String, txList: List<ZadTransaction>) {
    val dayTotal = txList.sumOf { if (it.isExpense) -it.amount else it.amount }
    val label = try {
        val date = java.time.LocalDate.parse(dateStr)
        val today = java.time.LocalDate.now()
        when {
            date == today -> "اليوم"
            date == today.minusDays(1) -> "أمس"
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ar")))
        }
    } catch (e: Exception) { dateStr }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = Typography.labelMedium,
            color = onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${if (dayTotal >= 0) "+" else ""}${String.format("%,.0f", dayTotal)} ر.س",
            style = Typography.labelMedium,
            color = if (dayTotal >= 0) successColor else dangerColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TxRowItem(tx: ZadTransaction, onDelete: () -> Unit) {
    val isExpense = tx.isExpense
    val categoryIcon = when (tx.category?.lowercase()) {
        "طعام", "مطاعم", "المطاعم" -> Icons.Default.Restaurant
        "تسوق", "مشتريات", "البقالة" -> Icons.Default.ShoppingCart
        "نقل", "مواصلات" -> Icons.Default.DirectionsCar
        "صحة", "مستشفى" -> Icons.Default.LocalHospital
        "ترفيه" -> Icons.Default.Movie
        "الاشتراكات", "اشتراك" -> Icons.Default.Subscriptions
        "راتب", "الراتب", "دخل" -> Icons.Default.Payments
        "فواتير" -> Icons.Default.Receipt
        "تحويل" -> Icons.Default.SwapHoriz
        else -> if (isExpense) Icons.Default.Remove else Icons.Default.Add
    }

    val categoryBg = when (tx.category?.lowercase()) {
        "طعام", "مطاعم" -> catFoodBg
        "تسوق", "مشتريات" -> catDailyBg
        "نقل", "مواصلات" -> catTransportBg
        "الاشتراكات", "اشتراك" -> catBillsBg
        "راتب", "الراتب", "دخل" -> catBankingBg
        "ترفيه" -> catEntertainBg
        "صحة" -> catHealthBg
        else -> if (isExpense) catDailyBg else catBankingBg
    }

    val categoryiconColor = when (tx.category?.lowercase()) {
        "طعام", "مطاعم" -> catFoodIcon
        "تسوق", "مشتريات" -> catDailyIcon
        "نقل", "مواصلات" -> catTransportIcon
        "الاشتراكات", "اشتراك" -> catBillsIcon
        "راتب", "الراتب", "دخل" -> catBankingIcon
        "ترفيه" -> catEntertainIcon
        "صحة" -> catHealthIcon
        else -> if (isExpense) catDailyIcon else catBankingIcon
    }

    val formattedTime = try {
        val inst = Instant.parse(tx.createdAt ?: "")
        inst.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "" }

    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.03f))
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .clickable { showDelete = !showDelete }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(categoryBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(categoryIcon, contentDescription = null, tint = categoryiconColor, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                tx.title,
                style = Typography.titleMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!tx.category.isNullOrBlank()) {
                    Text(
                        tx.category,
                        style = Typography.labelSmall,
                        color = onSurfaceVariant
                    )
                }
                if (tx.bankName != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(catBankingBg)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(tx.bankName, style = Typography.labelSmall.copy(fontSize = 10.sp), color = catBankingIcon)
                    }
                }
                if (formattedTime.isNotBlank()) {
                    Text(formattedTime, style = Typography.labelSmall.copy(fontSize = 10.sp), color = onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (isExpense) "−" else "+"} ${String.format("%,.2f", tx.amount)}",
                style = Typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = if (isExpense) dangerColor else successColor
            )
            Text(
                "ر.س",
                style = Typography.labelSmall.copy(fontSize = 10.sp),
                color = onSurfaceVariant
            )
        }

        if (showDelete) {
            IconButton(
                onClick = {
                    onDelete()
                    showDelete = false
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = dangerColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


