package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import com.example.ui.components.ZadSprings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadAiRepository
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

@Composable
fun WeeklyReportScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel
) {
    val familyState by familyViewModel.state.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val scope = rememberCoroutineScope()

    var analysis by remember { mutableStateOf<ZadAiRepository.FamilyAnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    val weekAgo = remember { java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS) }

    fun generateReport(active: FamilyState.Active) {
        scope.launch {
            isLoading = true
            loadError = false
            try {
                val weeklyTx = transactions.filter { tx ->
                    val d = tx.createdAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
                    d != null && d >= weekAgo
                }
                analysis = ZadAiRepository.analyzeFamily(
                    members = active.members,
                    tasks = active.chores,
                    goals = active.goals,
                    tasbihaTrees = familyViewModel.familyTasbiha,
                    transactions = weeklyTx
                )
            } catch (e: Exception) {
                loadError = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(familyState) {
        val active = familyState
        if (active is FamilyState.Active && analysis == null && !isLoading) {
            familyViewModel.loadTasbiha()
            generateReport(active)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            val active = familyState
            if (active is FamilyState.Active) {
                IconButton(onClick = { generateReport(active) }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = primary)
                }
            }
        }

        when (val state = familyState) {
            is FamilyState.Active -> {
                if (isLoading && analysis == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = primary)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.report_generating), color = onSurfaceVariant)
                        }
                    }
                } else if (loadError && analysis == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = dangerColor, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.report_error), color = onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { generateReport(state) }) { Text(stringResource(R.string.retry_action)) }
                        }
                    }
                } else if (analysis != null) {
                    WeeklyReportContent(
                        analysis = analysis!!,
                        state = state,
                        transactions = transactions,
                        weekAgo = weekAgo
                    )
                }
            }
            is FamilyState.Loading -> {
                com.example.ui.components.ZadLoadingState()
            }
            else -> {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.no_family_for_report),
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun WeeklyReportContent(
    analysis: ZadAiRepository.FamilyAnalysisResult,
    state: FamilyState.Active,
    transactions: List<com.example.data.ZadTransaction>,
    weekAgo: java.time.Instant
) {
    val context = LocalContext.current
    val children = state.members.filter { it.role == "child" }

    val weeklyTx = remember(transactions, weekAgo) {
        transactions.filter { tx ->
            val d = tx.createdAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
            // txnKind مش isExpense — سحب ATM (transfer) ميتحسبش مصروف هنا (نفس تصحيح 19.3)
            tx.txnKind == "expense" && d != null && d >= weekAgo
        }
    }
    val categoryTotals = remember(weeklyTx) {
        weeklyTx.groupBy { it.category ?: "أخرى" }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { HealthScoreGauge(analysis.familyHealthScore) }

        item {
            Spacer(Modifier.height(16.dp))
            // anything the assistant *says* goes on the dark #052E16 panel in this
            // design, not on another white card
            com.example.ui.components.ZadDarkPanel(
                title = stringResource(R.string.ai_family_summary_title)
            ) {
                Text(
                    analysis.familySummary.ifBlank { "—" },
                    style = Typography.bodyMedium,
                    color = Color.White,
                    lineHeight = 22.sp
                )
            }
        }

        if (children.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.children_weekly_spending_title), fontWeight = FontWeight.Bold, color = onSurface, style = Typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(children) { child ->
                val spent = com.example.data.approvedSpendSince(state.messages, child.id, weekAgo)
                val choresDone = state.chores.count { it.assignedTo == child.id && it.isCompleted }
                val choresTotal = state.chores.count { it.assignedTo == child.id }
                com.example.ui.components.ZadListCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    contentPadding = 0.dp
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KidAvatar(seed = child.id.ifBlank { child.alias }, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(child.alias, fontWeight = FontWeight.Bold, color = onSurface)
                                Text(
                                    stringResource(R.string.chores_completed_ratio_pill, choresDone, choresTotal),
                                    style = Typography.labelSmall, color = onSurfaceVariant
                                )
                            }
                        }
                        if (child.weeklyLimit != null && child.weeklyLimit > 0) {
                            Spacer(Modifier.height(8.dp))
                            SpendLimitBar(stringResource(R.string.weekly_limit_label), spent, child.weeklyLimit, context)
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.spent_this_week_colon, com.example.data.CurrencyFormatter.format(context, spent)),
                                style = Typography.labelSmall, color = onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.household_spending_title), fontWeight = FontWeight.Bold, color = onSurface, style = Typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (categoryTotals.isEmpty()) {
            item {
                com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_expenses_this_week))
            }
        } else {
            val maxTotal = categoryTotals.maxOf { it.second }
            items(categoryTotals.take(6)) { (category, total) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category, style = Typography.labelMedium, color = onSurface, modifier = Modifier.width(90.dp))
                    LinearProgressIndicator(
                        progress = { (total / maxTotal).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = secondary,
                        trackColor = onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(com.example.data.CurrencyFormatter.format(context, total), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        }

        if (analysis.memberHighlights.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.member_highlights_title), fontWeight = FontWeight.Bold, color = onSurface, style = Typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(analysis.memberHighlights) { h ->
                com.example.ui.components.ZadListCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    contentPadding = 0.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(h.name, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9), fontSize = 13.sp)
                        if (h.achievement.isNotBlank()) Text("🌟 ${h.achievement}", style = Typography.bodySmall, color = Color(0xFF4C1D95))
                        if (h.suggestion.isNotBlank()) Text("💡 ${h.suggestion}", style = Typography.bodySmall, color = Color(0xFF4C1D95))
                    }
                }
            }
        }

        if (analysis.suggestedGoal.isNotBlank() || analysis.funFact.isNotBlank()) {
            item {
                Spacer(Modifier.height(16.dp))
                if (analysis.suggestedGoal.isNotBlank()) {
                    Text(stringResource(R.string.ai_suggested_goal_colon, analysis.suggestedGoal), style = Typography.bodyMedium, color = onSurface, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                if (analysis.funFact.isNotBlank()) {
                    Text(stringResource(R.string.fun_fact_colon, analysis.funFact), style = Typography.bodySmall, color = onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HealthScoreGauge(score: Int) {
    val scoreColor = when {
        score >= 85 -> Color(0xFF22C55E)
        score >= 65 -> Color(0xFF84CC16)
        score >= 40 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val animatedScore by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = ZadSprings.Screen,
        label = "familyHealthScore"
    )
    Spacer(Modifier.height(4.dp))
    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp), contentPadding = 0.dp) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                CircularProgressIndicator(
                    progress = { animatedScore },
                    modifier = Modifier.fillMaxSize(),
                    color = scoreColor,
                    strokeWidth = 8.dp,
                    trackColor = scoreColor.copy(alpha = 0.12f)
                )
                Text("$score", fontWeight = FontWeight.Bold, color = scoreColor, style = Typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.family_health_score_label), fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.out_of_100), style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
    }
}
