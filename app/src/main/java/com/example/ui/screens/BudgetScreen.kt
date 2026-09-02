package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.data.ZadTransaction
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.pressableScale
import com.airbnb.lottie.compose.LottieConstants
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Transactions Screen (STC Pay style) ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BudgetScreen(
    viewModel: ZadViewModel,
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val behaviorPatterns by viewModel.behaviorPatterns.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val availableFigure by viewModel.availableFigure.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val nextObligationDue by viewModel.nextObligationDue.collectAsState()
    val obligations by viewModel.obligations.collectAsState()
    val showBudgetDialog by viewModel.showBudgetDialog.collectAsState()
    val suggestedBudget by viewModel.suggestedBudget.collectAsState()
    val cycleStart by viewModel.cycleStart.collectAsState()
    val cycleEnd by viewModel.cycleEnd.collectAsState()

    // Task 0ب — remainingBalance/availableFigure بقوا nullable (null = السقف لسه مش
    // معروف). الشاشة دي عملياً ما بتتعرضش من غير سقف مؤكد (بوابة MainScreen)، بس الشرط
    // ده دفاعي لفجوة اللحظة الأولى بعد فتح التطبيق قبل ما loadBudget() يخلّص.
    val remainingBalanceValue = remainingBalance
    val availableFigureValue = availableFigure
    if (remainingBalanceValue == null || availableFigureValue == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = primary)
        }
        return
    }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showWhySheet by remember { mutableStateOf(false) } // Task 27.2 — طول الضغط على "متاح"
    var selectedFilter by remember { mutableStateOf("الكل") }
    val context = LocalContext.current
    var categoryCardsRefresh by remember { mutableIntStateOf(0) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var insightCategory by remember { mutableStateOf<String?>(null) }
    var editingTransaction by remember { mutableStateOf<ZadTransaction?>(null) }
    var showAddObligationDialog by remember { mutableStateOf(false) }
    var editingObligation by remember { mutableStateOf<com.example.data.ZadObligation?>(null) }
    var deletingObligation by remember { mutableStateOf<com.example.data.ZadObligation?>(null) }
    // المصروف الفعلي بيتحسب من المعاملات مباشرة (يشمل اليدوية + البنكية) — الميزانية من BudgetTracker
    val categoryCards = remember(transactions, cycleStart, cycleEnd, categoryCardsRefresh) {
        val spentByCategory = transactions.filter { tx ->
            tx.txnKind == "expense" &&
                com.example.data.BudgetMath.txDate(tx)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true
        }.groupBy { it.category ?: "أخرى" }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

        com.example.data.BudgetTracker.STANDARD_CATEGORIES
            .map { cat ->
                Triple(cat, com.example.data.BudgetTracker.getCategoryBudget(context, cat), spentByCategory[cat] ?: 0.0)
            }
            .filter { (_, catBudget, spent) -> catBudget > 0 || spent > 0 }
            .sortedByDescending { it.third }
    }

    // كارت الملخص تحت بيعرض الدخل والمصروف جنب "الميزانية" (سقف شهري)، وشريط التنبيه
    // بيقسم المصروف على نفس السقف ده. لما الرقمين دول كانوا totalIncome/totalExpense
    // (كل المعاملات من أول يوم في التطبيق)، القسمة كانت بتعدّي ١٠٠٪ بعد شهرين استخدام
    // وتفضل هناك للأبد — يعني الشريط بيقول "تجاوزت ميزانيتك" دايماً بغض النظر عن الشهر.
    // الاتنين بقوا بحدود دورة الراتب، نفس حدود "متاح" فوق بالظبط.
    val totalIncome by viewModel.incomeThisCycle.collectAsState()
    val totalSpent by viewModel.spentThisCycle.collectAsState()
    // Task 26 — "متاح" (available) بقى الرقم الأساسي، مش remainingBalance الخام —
    // available بيخصم الالتزامات الثابتة المؤكدة (إيجار/قسط/اشتراكات) القادمة قبل نهاية
    // الدورة. لمستخدم من غير التزامات مسجلة available == remainingBalance بالظبط.
    val currentBalance = availableFigureValue.value

    // Animate balance changes
    val animatedBalance by animateFloatAsState(
        targetValue = currentBalance.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    )

    val filteredTx = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "المصروفات" -> transactions.filter { it.txnKind == "expense" }
            "الدخل" -> transactions.filter { it.txnKind == "income" }
            "البنك" -> transactions.filter { it.sourceType == "bank_sms" || it.sourceType == "bank_notification" }
            else -> transactions
        }.sortedByDescending { it.createdAt ?: "" }
    }
    val todayFallbackLabel = stringResource(R.string.today_label)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Available (mockup `renderBudget`'s first block) ──────────────
            // Solid #064E3B at 22dp, label over figure, nothing else. What stood
            // here was a 24dp gradient hero with two decorative circles and a
            // nested glass panel repeating income/spent/budget — three numbers
            // that now live in their own card below, where they don't compete
            // with the one figure this screen exists to state.
            //
            // Product logic kept verbatim: the "≈" prefix + its explanation when
            // the figure isn't confident (Task 27.1a), long-press for "آخر
            // التغييرات" (Task 27.2), and the "متبقي X · محجوز Y" line (Task 26).
            item {
                var showAvailableReason by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(primary)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.available_label),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.showBudgetDialog() }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_monthly_budget),
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Text(
                        (if (!availableFigureValue.confident) "≈ " else "") +
                            com.example.data.CurrencyFormatter.format(context, animatedBalance.toDouble()),
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        // dangerColor (#DC5B4B) reads at ~1.75:1 against this dark green card —
                        // fails contrast; coralLight (#FFA69E) is the same alarm-red family at
                        // ~3.5:1, which clears WCAG's large-text 3:1 bar. Same fix as ZadCardHero.
                        color = if (currentBalance < 0) coralLight else Color.White,
                        modifier = Modifier.combinedClickable(
                            onClick = { if (!availableFigureValue.confident) showAvailableReason = true },
                            onLongClick = { showWhySheet = true }
                        )
                    )
                    if (committed > 0) {
                        val nextText = nextObligationDue?.let { (ob, due) ->
                            val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due).toInt()
                            stringResource(R.string.obligation_due_in_days, ob.title, days)
                        }
                        Text(
                            if (nextText != null) {
                                stringResource(
                                    R.string.available_breakdown_with_next,
                                    com.example.data.CurrencyFormatter.format(context, remainingBalanceValue),
                                    com.example.data.CurrencyFormatter.format(context, committed),
                                    nextText
                                )
                            } else {
                                stringResource(
                                    R.string.available_breakdown,
                                    com.example.data.CurrencyFormatter.format(context, remainingBalanceValue),
                                    com.example.data.CurrencyFormatter.format(context, committed)
                                )
                            },
                            style = Typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                if (showAvailableReason && availableFigureValue.reason != null) {
                    AlertDialog(
                        onDismissRequest = { showAvailableReason = false },
                        confirmButton = { TextButton(onClick = { showAvailableReason = false }) { Text(stringResource(R.string.close_action)) } },
                        title = { Text(stringResource(R.string.available_label) + " ≈") },
                        text = { Text(availableFigureValue.reason!!) }
                    )
                }
            }

            // ── Total obligations (mockup's `totalObligations` strip) ─────────
            item {
                val obligationsTotal = obligations.sumOf { it.amount }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .zadCardShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(surface)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.total_obligations_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            com.example.data.CurrencyFormatter.format(context, obligationsTotal),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryDark
                        )
                        IconButton(onClick = { showAddObligationDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_obligation_action), tint = secondaryDark, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Obligation cards (mockup's `OBLIGATIONS` list) ────────────────
            // `viewModel.obligations` is the real zad_obligations table; the
            // mockup's four rows are hardcoded. Status is derived from the due
            // date rather than invented: past due date = paid this cycle, due
            // within a week = pending, anything further out = scheduled.
            if (obligations.isEmpty()) {
                item {
                    com.example.ui.components.ZadEmptyState(
                        icon = Icons.Default.EventRepeat,
                        title = stringResource(R.string.no_obligations_title),
                        subtitle = stringResource(R.string.no_obligations_hint),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(obligations, key = { it.id }) { obligation ->
                    ObligationCard(
                        obligation = obligation,
                        onEdit = { editingObligation = obligation },
                        onDelete = { deletingObligation = obligation }
                    )
                }
            }

            // ── Income / spent / budget strip ────────────────────────────────
            // Was nested inside the hero above. Same three numbers, its own card.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .zadCardShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(surface)
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TxSummaryItem(
                        label = stringResource(R.string.income_label),
                        amount = totalIncome,
                        icon = Icons.Default.TrendingUp,
                        color = successColor
                    )
                    Box(modifier = Modifier.height(50.dp).width(1.dp).background(outlineVariant))
                    TxSummaryItem(
                        label = stringResource(R.string.expense_label),
                        amount = totalSpent,
                        icon = Icons.Default.TrendingDown,
                        color = dangerColor
                    )
                    Box(modifier = Modifier.height(50.dp).width(1.dp).background(outlineVariant))
                    TxSummaryItem(
                        label = stringResource(R.string.budget_label),
                        amount = budget,
                        icon = Icons.Default.AccountBalanceWallet,
                        color = secondaryDark
                    )
                }
            }

            // ── AI insight strip ────────────────────────────────────────────
            item {
                val spentPct = if (budget > 0) (totalSpent / budget * 100).toInt() else 0
                val (aiIcon, aiMsg) = when {
                    spentPct >= 100 -> Icons.Default.Block to stringResource(R.string.budget_insight_over)
                    spentPct >= 85 -> Icons.Default.Warning to stringResource(R.string.budget_insight_high, spentPct)
                    spentPct >= 60 -> Icons.Default.Lightbulb to stringResource(R.string.budget_insight_medium, spentPct)
                    else -> Icons.Default.CheckCircle to stringResource(R.string.budget_insight_good, spentPct)
                }
                val stripColor = when {
                    spentPct >= 100 -> dangerColor
                    spentPct >= 85 -> secondary
                    else -> primary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(stripColor.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNavigateToAssistant
                        )
                        .pressableScale()
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

            // ── Budget suggestion card (advisory — needs explicit user approval) ──
            suggestedBudget?.let { suggestion ->
                item {
                    com.example.ui.components.AppearOnEntry {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(14.dp)).background(infoColor.copy(alpha = 0.10f))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Insights, contentDescription = null, tint = infoColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.budget_suggestion_text, com.example.data.CurrencyFormatter.format(context, suggestion)),
                                        style = Typography.bodyMedium, color = onSurface, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { viewModel.applySuggestedBudget() },
                                        modifier = Modifier.height(34.dp).pressableScale(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(50)
                                    ) { Text(stringResource(R.string.apply_suggestion_action), style = Typography.labelSmall) }
                                    OutlinedButton(
                                        onClick = { viewModel.dismissBudgetSuggestion() },
                                        modifier = Modifier.height(34.dp).pressableScale(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(50)
                                    ) { Text(stringResource(R.string.dismiss_action), style = Typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }

            // ── Category Budget Cards ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.category_budgets), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    TextButton(onClick = { editingCategory = "__NEW__" }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.set_category), style = Typography.labelMedium, color = primary)
                    }
                }
            }
            if (categoryCards.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_category_budgets_hint),
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
                        categoryCards.forEachIndexed { index, (cat, catBudget, spent) ->
                            com.example.ui.components.AppearOnEntry(delayMs = (index * 40).coerceAtMost(400)) {
                                CategoryBudgetCard(
                                    category = cat,
                                    budget = catBudget,
                                    spent = spent,
                                    onClick = { editingCategory = cat },
                                    onInsightClick = { insightCategory = cat }
                                )
                            }
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
                // نفس المجاميع المعتمدة التي في كارت الميزانية: دورة الراتب وtxnKind.
                // لا نعيد جمع المعاملات المرشحة هنا، لأن الفلتر قد يخفي صفوفاً ويحوّل
                // التحويلات البنكية إلى مصروف بالاعتماد على isExpense القديم.
                val monthSpent = totalSpent
                val monthIncome = totalIncome

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        stringResource(R.string.this_month_label),
                        style = Typography.labelMedium,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (monthIncome > 0) {
                            Text(
                                "+${com.example.data.CurrencyFormatter.format(context, monthIncome)}",
                                style = Typography.bodyMedium,
                                color = successColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (monthSpent > 0) {
                            Text(
                                "−${com.example.data.CurrencyFormatter.format(context, monthSpent)}",
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
                            ZadLottieAsset(
                                resId = R.raw.lottie_empty_box,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.size(140.dp),
                                contentDescription = stringResource(R.string.no_transactions)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_transactions),
                                style = Typography.titleMedium,
                                color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.no_transactions_bank_hint),
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
                    } catch (e: Exception) { todayFallbackLabel }
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
                                onDelete = { viewModel.deleteTransaction(tx.id) },
                                onEdit = { editingTransaction = tx }
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
                .padding(end = 20.dp, bottom = 16.dp),
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
                    Text(stringResource(R.string.add_transaction_fab), style = Typography.labelMedium, fontWeight = FontWeight.Bold)
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
                        isExpense = isExpense, category = category, isVerified = true
                    )
                )
                showAddTransactionDialog = false
            }
        )
    }

    if (showWhySheet) {
        com.example.ui.components.WhyChangedSheet(onDismiss = { showWhySheet = false })
    }

    if (showBudgetDialog) {
        // نفس تحوّل الدفتر اللي في HomeScreen.BudgetEditSheet: الرقم المكتوب هو الرصيد
        // اللي العميل عايز يشوفه، مش سقف بيتحسب منه رصيد.
        BudgetEditDialog(
            currentBudget = remainingBalance ?: 0.0,
            onDismiss = { viewModel.hideBudgetDialog() },
            onSave = { newBalance ->
                viewModel.setBalanceTo(newBalance)
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

    editingTransaction?.let { tx ->
        TransactionEditDialog(
            tx = tx,
            onDismiss = { editingTransaction = null },
            onSave = { title, amount, category, isExpense ->
                viewModel.updateTransaction(tx.id, title, amount, category, isExpense)
                editingTransaction = null
            }
        )
    }

    insightCategory?.let { cat ->
        CategoryInsightDialog(
            category = cat,
            transactions = transactions,
            patterns = behaviorPatterns,
            onDismiss = { insightCategory = null }
        )
    }

    if (showAddObligationDialog) {
        AddEditObligationDialog(
            obligation = null,
            onDismiss = { showAddObligationDialog = false },
            onSave = { title, amount, kind, dueDay, recurrence ->
                viewModel.addObligation(
                    com.example.data.ZadObligation(
                        title = title,
                        amount = amount,
                        kind = kind,
                        dueDay = dueDay,
                        recurrence = recurrence
                    )
                )
                showAddObligationDialog = false
            }
        )
    }

    editingObligation?.let { obligation ->
        AddEditObligationDialog(
            obligation = obligation,
            onDismiss = { editingObligation = null },
            onSave = { title, amount, kind, dueDay, recurrence ->
                viewModel.updateObligation(obligation.id, title, amount, kind, dueDay, recurrence)
                editingObligation = null
            }
        )
    }

    deletingObligation?.let { obligation ->
        AlertDialog(
            onDismissRequest = { deletingObligation = null },
            title = { Text(stringResource(R.string.delete_action), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_obligation_confirm, obligation.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteObligation(obligation.id)
                    deletingObligation = null
                }) { Text(stringResource(R.string.delete_action), color = dangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { deletingObligation = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CategoryInsightDialog(
    category: String,
    transactions: List<com.example.data.ZadTransaction>,
    patterns: List<com.example.data.ZadBehaviorPattern>,
    onDismiss: () -> Unit
) {
    var analysis by remember { mutableStateOf<com.example.data.ZadAiRepository.BehaviorAnalysis?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(category) {
        analysis = com.example.data.ZadAiRepository.analyzeBehavior(category, transactions, patterns)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_budget_47701), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.auto_budget_21822), style = Typography.bodyMedium, color = onSurfaceVariant)
                }
            } else {
                val result = analysis
                if (result == null || result.insight.isBlank()) {
                    Text(stringResource(R.string.auto_budget_71478), style = Typography.bodyMedium, color = onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(result.insight, style = Typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val (trendIcon, trendLabel) = when (result.trend) {
                                "increasing" -> Icons.Default.TrendingUp to "في ازدياد"
                                "decreasing" -> Icons.Default.TrendingDown to "في انخفاض"
                                else -> Icons.AutoMirrored.Filled.TrendingFlat to "مستقر"
                            }
                            Icon(trendIcon, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                            Text(trendLabel, style = Typography.labelMedium, color = primary)
                        }
                        if (result.predictedNext > 0) {
                            Text("المتوقع الشهر القادم: ${com.example.data.CurrencyFormatter.format(context, result.predictedNext)}", style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                        if (result.tip.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = warningColor, modifier = Modifier.size(14.dp))
                                Text(result.tip, style = Typography.bodySmall, color = onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun CategoryBudgetCard(category: String, budget: Double, spent: Double, onClick: () -> Unit, onInsightClick: () -> Unit) {
    val context = LocalContext.current
    val pct = if (budget > 0) (spent / budget * 100).toInt() else 0
    val overBudget = budget > 0 && spent > budget
    val barColor = when {
        kotlin.math.abs(budget) < 0.005 -> onSurfaceVariant.copy(alpha = 0.4f)
        overBudget -> dangerColor
        pct >= 85 -> warningColor
        else -> primary
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = com.example.ui.components.ZadSprings.Screen, label = "catBudgetBar"
    )
    val catCardShape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = catCardShape, spotColor = barColor.copy(alpha = 0.16f))
            .clip(catCardShape)
            .background(surface)
            .clickable(onClick = onClick)
            .pressableScale()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, style = Typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                Text(
                    if (budget > 0) "${com.example.data.CurrencyFormatter.formatNumber(context, spent)} / ${com.example.data.CurrencyFormatter.format(context, budget)}"
                    else stringResource(R.string.spent_no_limit, com.example.data.CurrencyFormatter.format(context, spent)),
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
        IconButton(onClick = onInsightClick, modifier = Modifier.size(28.dp).pressableScale()) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "تحليل ذكي", tint = primary, modifier = Modifier.size(16.dp))
        }
        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_cd), tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (category != null) stringResource(R.string.edit_category_budget_title, category) else stringResource(R.string.set_category_budget_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (category == null) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.category_label)) },
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
                    label = { Text(stringResource(R.string.monthly_budget_with_currency, com.example.data.CurrencyFormatter.symbol(context))) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                onSave(category ?: selectedCategory, amount)
            }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, color = primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
            color = textPrimary
        )
        Text(label, style = Typography.labelSmall, color = textSecondary)
    }
}

@Composable
private fun TxDateHeader(dateStr: String, txList: List<ZadTransaction>) {
    val context = LocalContext.current
    val todayLabel = stringResource(R.string.today_label)
    val yesterdayLabel = stringResource(R.string.yesterday_label)
    val dayTotal = txList.sumOf { if (it.isExpense) -it.amount else it.amount }
    val label = try {
        val date = java.time.LocalDate.parse(dateStr)
        val today = java.time.LocalDate.now()
        when {
            date == today -> todayLabel
            date == today.minusDays(1) -> yesterdayLabel
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM", com.example.data.MarketPrefs.currentMarket.toLocale()))
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
            "${if (dayTotal >= 0) "+" else ""}${com.example.data.CurrencyFormatter.format(context, dayTotal)}",
            style = Typography.labelMedium,
            color = if (dayTotal >= 0) successColor else dangerColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TxRowItem(tx: ZadTransaction, onDelete: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
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

    // ضغطة على الصف بتفتح أزرار التعديل والمسح مع بعض. المسح لوحده كان بيخلي تصحيح رقم
    // غلط يعني مسح المعاملة وإعادة إدخالها من الأول (وضياع تاريخها ومصدرها البنكي معاها).
    var showActions by remember { mutableStateOf(false) }
    val txRowShape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .shadow(elevation = 4.dp, shape = txRowShape, spotColor = categoryiconColor.copy(alpha = 0.14f))
            .clip(txRowShape)
            .background(surface)
            .clickable { showActions = !showActions }
            .pressableScale()
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
                "${if (isExpense) "−" else "+"} ${com.example.data.CurrencyFormatter.formatNumber(context, tx.amount)}",
                style = Typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = if (isExpense) dangerColor else successColor
            )
            Text(
                com.example.data.CurrencyFormatter.symbol(context),
                style = Typography.labelSmall.copy(fontSize = 10.sp),
                color = onSurfaceVariant
            )
        }

        if (showActions) {
            IconButton(
                onClick = {
                    onEdit()
                    showActions = false
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.tx_edit_cd),
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    onDelete()
                    showActions = false
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.tx_delete_cd),
                    tint = dangerColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * تعديل معاملة موجودة: المبلغ، الوصف، الفئة، ونوعها (مصروف/دخل).
 *
 * الفئات المعروضة هي [com.example.data.BudgetTracker.STANDARD_CATEGORIES] نفسها اللي
 * كروت ميزانيات الفئات بتستخدمها، مع الفئة الحالية للمعاملة لو كانت خارجها (معاملات
 * بنكية بتيجي بفئات مقروءة من الإشعار) — عشان فتح التعديل ميغيّرش تصنيفها من غير قصد.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionEditDialog(
    tx: ZadTransaction,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: String?, isExpense: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(tx.title) }
    var amountStr by remember { mutableStateOf(if (tx.amount > 0) tx.amount.toString() else "") }
    var category by remember { mutableStateOf(tx.category) }
    var isExpense by remember { mutableStateOf(tx.isExpense) }

    val categories = remember(tx.category) {
        (com.example.data.BudgetTracker.STANDARD_CATEGORIES +
            listOfNotNull(tx.category?.takeIf { it.isNotBlank() })).distinct()
    }
    val amount = amountStr.toDoubleOrNull()
    val canSave = title.isNotBlank() && amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.tx_edit_title),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.tx_edit_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input -> amountStr = input.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.tx_edit_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = amountStr.isNotBlank() && amount == null
                )

                Text(
                    stringResource(R.string.tx_edit_kind_label),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text(stringResource(R.string.tx_edit_kind_expense)) }
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text(stringResource(R.string.tx_edit_kind_income)) }
                    )
                }

                Text(
                    stringResource(R.string.tx_edit_category_label),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = if (category == cat) null else cat },
                            label = { Text(cat, style = Typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(title.trim(), amount ?: 0.0, category, isExpense) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * One obligation, drawn as the mockup's `OBLIGATIONS` card: name + amount, due
 * line + status pill, then a progress bar tinted to the status.
 *
 * Status is derived, not stored — `ZadObligation` has no status column, and
 * inventing one to match the mockup's hardcoded `statusKind` would mean showing
 * a state the data never sets. Days until the next occurrence decide it, and the
 * bar shows how far through the cycle that occurrence is.
 */
@Composable
internal fun ObligationCard(
    obligation: com.example.data.ZadObligation,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val today = java.time.LocalDate.now()
    // BudgetMath.nextDueDate هي نفس الدالة اللي "محجوز" و"الاستحقاق الجاي" في الهيرو
    // بيتحسبوا بيها. الكارت ده كان بيحسب التاريخ بنفسه بمنطق مختلف شوية (بيتجاهل
    // recurrence تماماً، ويعامل due_date اللي فات كأنه استحقاق حالي)، فكان ممكن يقول
    // "مستحق بعد ٣ أيام" لالتزام مش داخل أصلاً في رقم "محجوز" اللي فوقه على نفس الشاشة.
    val dueDate = remember(obligation.id, obligation.dueDay, obligation.dueDate, obligation.recurrence) {
        com.example.data.BudgetMath.nextDueDate(obligation, today)
    }
    val daysUntil = dueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() }

    // nextDueDate بترجع null في حالتين بس: التزام "once" فات معاده (بنفترض إنه اتدفع،
    // نفس افتراض zad-brain)، أو التزام دوري من غير due_day فمفيش معاد يتقال أصلاً.
    // الفرق بينهم لازم يبان — "مدفوع" لالتزام ناقصه تاريخ كذبة، والعكس كمان.
    val statusColor: Color
    val statusLabel: String
    val progress: Float
    when {
        daysUntil == null && obligation.recurrence == "once" -> {
            statusColor = primary
            statusLabel = stringResource(R.string.obligation_status_paid)
            progress = 1f
        }
        daysUntil == null -> {
            statusColor = secondaryDark
            statusLabel = stringResource(R.string.obligation_status_scheduled)
            progress = 0f
        }
        daysUntil <= 7 -> {
            statusColor = dangerColor
            statusLabel = stringResource(R.string.obligation_status_pending)
            progress = ((7 - daysUntil) / 7f).coerceIn(0f, 1f)
        }
        else -> {
            statusColor = secondaryDark
            statusLabel = stringResource(R.string.obligation_status_scheduled)
            progress = (1f - (daysUntil / 30f)).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .zadCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .clickable(onClick = onEdit)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                obligation.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                com.example.data.CurrencyFormatter.format(context, obligation.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_action),
                    tint = textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (daysUntil) {
                    null -> obligation.recurrence
                    else -> stringResource(R.string.obligation_due_in_days, "", daysUntil).trim()
                },
                fontSize = 12.5.sp,
                color = textTertiary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                statusLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary.copy(alpha = 0.06f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFF1F4F3))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(statusColor)
            )
        }
    }
}

private val obligationKinds = listOf("rent", "installment", "debt", "tuition", "utility", "other")

@Composable
private fun obligationKindLabel(kind: String): String = when (kind) {
    "rent" -> stringResource(R.string.obligation_kind_rent)
    "installment" -> stringResource(R.string.obligation_kind_installment)
    "debt" -> stringResource(R.string.obligation_kind_debt)
    "tuition" -> stringResource(R.string.obligation_kind_tuition)
    "utility" -> stringResource(R.string.obligation_kind_utility)
    else -> stringResource(R.string.obligation_kind_other)
}

private val obligationRecurrences = listOf("monthly", "quarterly", "yearly", "once")

@Composable
private fun obligationRecurrenceLabel(recurrence: String): String = when (recurrence) {
    "monthly" -> stringResource(R.string.obligation_recurrence_monthly)
    "quarterly" -> stringResource(R.string.obligation_recurrence_quarterly)
    "yearly" -> stringResource(R.string.obligation_recurrence_yearly)
    else -> stringResource(R.string.obligation_recurrence_once)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddEditObligationDialog(
    obligation: com.example.data.ZadObligation?,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, kind: String, dueDay: Int?, recurrence: String) -> Unit
) {
    var title by remember { mutableStateOf(obligation?.title ?: "") }
    var amountStr by remember { mutableStateOf(obligation?.amount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var kind by remember { mutableStateOf(obligation?.kind ?: "rent") }
    var dueDayStr by remember { mutableStateOf(obligation?.dueDay?.toString() ?: "") }
    var recurrence by remember { mutableStateOf(obligation?.recurrence ?: "monthly") }
    val context = LocalContext.current
    val amount = amountStr.toDoubleOrNull()
    val canSave = title.isNotBlank() && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (obligation == null) R.string.add_obligation_dialog_title else R.string.edit_obligation_dialog_title),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.obligation_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.amount_with_currency_hint, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountStr.isNotBlank() && amount == null
                )
                OutlinedTextField(
                    value = dueDayStr,
                    onValueChange = { dueDayStr = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.obligation_due_day_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text(stringResource(R.string.obligation_kind_label), style = Typography.labelMedium, color = onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    obligationKinds.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(obligationKindLabel(k), style = Typography.labelSmall) }
                        )
                    }
                }

                Text(stringResource(R.string.obligation_recurrence_label), style = Typography.labelMedium, color = onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    obligationRecurrences.forEach { r ->
                        FilterChip(
                            selected = recurrence == r,
                            onClick = { recurrence = r },
                            label = { Text(obligationRecurrenceLabel(r), style = Typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(title.trim(), amount ?: 0.0, kind, dueDayStr.toIntOrNull(), recurrence) },
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
