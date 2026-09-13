package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrainFailureKind
import com.example.data.BrainHealth
import com.example.data.BrainHealthLimits
import com.example.data.BrainHealthReason
import com.example.data.BrainHealthStatus
import com.example.data.SupabaseRepo
import com.example.ui.components.ZadErrorState
import com.example.ui.components.ZadLoadingState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * المرحلة 4 — "صحة عقل زاد": شاشة المراقبة اللي بتخلّي أعطال العقل الاستباقي **تبان**.
 *
 * الحكم نفسه مش هنا — في `data/BrainHealth.kt` (نسخة ٢، منطق صافي متغطّى بـ
 * `BrainHealthTest`). الشاشة بتعرض بس، والأسباب جاية كـenum بتتترجم هنا، فقاعدة
 * i18n في CLAUDE.md مابتتكسرش وطبقة البيانات مابتعرفش لغة.
 *
 * قرارات مأخوذة من مراجعة عدائية سابقة على نسخة قديمة من الشاشة دي (اتشالت):
 * - أول تحميل بس بيوريّ سبينر ملء الشاشة؛ أي تحديث بعد كده بيسيب آخر لقطة ظاهرة
 *   ويوريّ مؤشر صغير في التوب بار، وفشل التحديث بيتعرض Snackbar عابر مش شاشة خطأ
 *   كاملة — شاشة الخطأ الكاملة محجوزة لـ"لسه معرفتش أقرا حاجة خالص".
 * - `getBrainHealth()` بترجّع `null` لو القراءة فشلت، ومفيش أي لقطة أصفار/تمام
 *   وهمية بتتعرض بدالها.
 * - نص الخطأ الخام من السيرفر مش بيتعرض أبدًا — بس تصنيف [BrainFailureKind] مترجم.
 * - حالة QUIET نصها "فيه حاجة تستاهل نظرة" مش "هادي" — لأن أسباب زيّ اقتراب حد
 *   الشات أو تصرف غير متوقع معناها العقل شغال/مشغول، مش ساكن.
 * - "drift" اتترجمت "تصرفات غير متوقعة من زاد" — عن سلوك الذكاء الاصطناعي نفسه،
 *   مش حكم على المستخدم.
 *
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط [AgentActionLogScreen]: قراءة Supabase
 * مباشرة من غير أي نداء LLM، فمفيش داعي لدورة حياة ViewModel.
 */
@Composable
fun BrainHealthScreen(onBack: () -> Unit) {
    var health by remember { mutableStateOf<BrainHealth?>(null) }
    var initialLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.brain_health_error_message)

    suspend fun refresh() {
        val isFirstLoad = health == null
        if (isFirstLoad) initialLoading = true else refreshing = true
        val result = SupabaseRepo.getBrainHealth()
        if (result != null) {
            health = result
        } else if (!isFirstLoad) {
            // مش أول تحميل: عندنا لقطة قديمة شغالة، فمنمسحهاش — بس نعرّف المستخدم
            // إن التحديث نفسه فشل من غير ما نكدب بشاشة تمام أو نخبّي البيانات اللي معانا.
            snackbarHostState.showSnackbar(errorMessage)
        }
        if (isFirstLoad) initialLoading = false else refreshing = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.brain_health_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (!refreshing && !initialLoading) scope.launch { refresh() } },
                    enabled = !refreshing && !initialLoading
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = onSurfaceVariant
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh_cd),
                            tint = onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        val snapshot = health
        when {
            snapshot == null && initialLoading -> ZadLoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            snapshot == null -> ZadErrorState(
                message = errorMessage,
                modifier = Modifier.fillMaxSize().padding(padding),
                retryLabel = stringResource(R.string.brain_health_retry),
                onRetry = { scope.launch { refresh() } }
            )
            else -> BrainHealthBody(health = snapshot, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun BrainHealthBody(health: BrainHealth, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, ZadHubListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusBanner(health) }
        item { ActivityCard(health) }
        item { TasksCard(health) }
        item { ChatUsageCard(health) }
        item { LearningCard(health) }
    }
}

// ─── شريط الحالة ─────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(health: BrainHealth) {
    val tint = statusColor(health.status)
    val icon = when (health.status) {
        BrainHealthStatus.HEALTHY -> Icons.Default.CheckCircle
        BrainHealthStatus.QUIET -> Icons.Default.WarningAmber
        BrainHealthStatus.STALLED -> Icons.Default.ErrorOutline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadLuxe.squircle)
            .background(tint.copy(alpha = 0.08f))
            .border(0.5.dp, tint.copy(alpha = 0.25f), ZadLuxe.squircle)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(statusTitleRes(health.status)),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
        }

        Spacer(Modifier.height(12.dp))

        if (health.reasons.isEmpty()) {
            Text(
                stringResource(R.string.brain_health_all_clear),
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                health.reasons.forEach { reason ->
                    ReasonLine(reason = reason, health = health)
                }
            }
        }
    }
}

@Composable
private fun ReasonLine(reason: BrainHealthReason, health: BrainHealth) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(statusColor(reason.severity))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            reasonText(reason, health),
            style = Typography.bodyMedium,
            color = onSurfaceVariant
        )
    }
}

// ─── الكروت ──────────────────────────────────────────────────────────────────

@Composable
private fun ActivityCard(health: BrainHealth) {
    HealthCard(title = stringResource(R.string.brain_health_section_activity)) {
        MetricRow(stringResource(R.string.brain_health_last_proactive), durationSinceText(health.hoursSinceLastProactive))
        MetricRow(stringResource(R.string.brain_health_runs_7d), num(health.runsLast7Days))
        MetricRow(
            stringResource(R.string.brain_health_failed_runs),
            num(health.failedRunsLast7Days),
            alarming = health.failedRunsLast7Days > 0
        )
        MetricRow(stringResource(R.string.brain_health_insights_7d), num(health.insightsLast7Days))
        MetricRow(stringResource(R.string.brain_health_pending_insights), num(health.pendingInsights))
        val failureKind = health.lastFailureKind
        if (failureKind != null) {
            MetricRow(
                stringResource(R.string.brain_health_failure_kind_label),
                stringResource(failureKindLabelRes(failureKind))
            )
        }
    }
}

@Composable
private fun TasksCard(health: BrainHealth) {
    HealthCard(title = stringResource(R.string.brain_health_section_tasks)) {
        MetricRow(
            stringResource(R.string.brain_health_tasks_backed_up),
            num(health.tasksBackedUp),
            alarming = health.tasksBackedUp > 0
        )
        MetricRow(stringResource(R.string.brain_health_pending_tasks), num(health.pendingTasksCount))
        MetricRow(
            stringResource(R.string.brain_health_failed_tasks_7d),
            num(health.failedTasksLast7Days),
            alarming = health.failedTasksLast7Days > 0
        )
        MetricRow(
            stringResource(R.string.brain_health_requests_not_retried),
            num(health.queueRowsLast7Days),
            alarming = health.queueRowsLast7Days > 0
        )
    }
}

@Composable
private fun ChatUsageCard(health: BrainHealth) {
    HealthCard(title = stringResource(R.string.brain_health_section_chat_usage)) {
        MetricRow(
            stringResource(R.string.brain_health_requests_label),
            stringResource(R.string.brain_health_used_of_limit, num(health.requestsToday), num(BrainHealthLimits.DAILY_REQUESTS))
        )
        MetricRow(
            stringResource(R.string.brain_health_tokens_label),
            stringResource(R.string.brain_health_used_of_limit, num(health.tokensToday), num(BrainHealthLimits.DAILY_TOKENS))
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { health.chatQuotaRatio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = chatQuotaColor(health.chatQuotaRatio),
            trackColor = surfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.brain_health_quota_note),
            style = Typography.labelSmall,
            color = textTertiary
        )
    }
}

@Composable
private fun LearningCard(health: BrainHealth) {
    HealthCard(title = stringResource(R.string.brain_health_section_learning)) {
        MetricRow(stringResource(R.string.brain_health_active_goals), num(health.activeGoals))
        MetricRow(
            stringResource(R.string.brain_health_unexpected_behavior_7d),
            num(health.driftLast7Days),
            alarming = health.driftLast7Days > 0
        )
    }
}

// ─── مكوّنات مشتركة ──────────────────────────────────────────────────────────

@Composable
private fun HealthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadLuxe.squircle)
            .background(ZadLuxe.cardWhite)
            .border(0.5.dp, ZadLuxe.hairline, ZadLuxe.squircle)
            .padding(16.dp)
    ) {
        Text(title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MetricRow(label: String, value: String, alarming: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Typography.bodyMedium, color = onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (alarming) dangerColor else onSurface
        )
    }
}

/** رقم واحد بس — كل عدد صحيح على الشاشة دي بيعدّي من هنا عشان الأرقام العربية والغربية مايتخلطوش. */
@Composable
private fun num(n: Int): String = stringResource(R.string.brain_health_number, n)

/**
 * نص المدة من آخر نشاط استباقي — بيتستخدم في شريط الحالة (سبب PROACTIVE_SILENT)
 * وفي كارت النشاط (آخر تنبيه استباقي). الجمع العربي (واحد/اتنين/قليل/كتير) بييجي
 * من `<plurals>` في strings.xml.
 */
@Composable
private fun durationSinceText(hoursSince: Long?): String {
    if (hoursSince == null) return stringResource(R.string.brain_health_never)
    if (hoursSince < 1) return stringResource(R.string.brain_health_just_now)
    if (hoursSince < 24) {
        val hours = hoursSince.toInt()
        return pluralStringResource(R.plurals.brain_health_hours, hours, hours)
    }
    val days = (hoursSince / 24).toInt()
    return pluralStringResource(R.plurals.brain_health_days, days, days)
}

// ─── ترجمة الـenums لنصوص الواجهة ────────────────────────────────────────────
// `data/BrainHealth.kt` مابيعرفش لغة عن قصد؛ التحويل كله هنا.

private fun statusTitleRes(status: BrainHealthStatus): Int = when (status) {
    BrainHealthStatus.HEALTHY -> R.string.brain_health_status_healthy
    BrainHealthStatus.QUIET -> R.string.brain_health_status_quiet
    BrainHealthStatus.STALLED -> R.string.brain_health_status_stalled
}

@Composable
private fun statusColor(status: BrainHealthStatus): Color = when (status) {
    BrainHealthStatus.HEALTHY -> successColor
    BrainHealthStatus.QUIET -> warningColor
    BrainHealthStatus.STALLED -> dangerColor
}

@Composable
private fun chatQuotaColor(ratio: Float): Color = when {
    ratio >= BrainHealthLimits.CHAT_QUOTA_WARN_RATIO -> dangerColor
    ratio >= BrainHealthLimits.CHAT_QUOTA_WARN_RATIO / 2 -> warningColor
    else -> successColor
}

private fun failureKindLabelRes(kind: BrainFailureKind): Int = when (kind) {
    BrainFailureKind.PROVIDER_BUSY -> R.string.brain_health_failure_provider_busy
    BrainFailureKind.CONFIGURATION -> R.string.brain_health_failure_configuration
    BrainFailureKind.OTHER -> R.string.brain_health_failure_other
}

@Composable
private fun reasonText(reason: BrainHealthReason, health: BrainHealth): String = when (reason) {
    BrainHealthReason.NO_PROACTIVE_YET ->
        stringResource(R.string.brain_health_reason_no_proactive_yet)

    BrainHealthReason.PROACTIVE_SILENT ->
        stringResource(R.string.brain_health_reason_proactive_silent, durationSinceText(health.hoursSinceLastProactive))

    BrainHealthReason.TASKS_BACKED_UP ->
        stringResource(R.string.brain_health_reason_tasks_backed_up, health.tasksBackedUp)

    BrainHealthReason.RUNS_FAILING ->
        stringResource(
            R.string.brain_health_reason_runs_failing,
            health.failedRunsLast7Days,
            health.runsLast7Days
        )

    BrainHealthReason.TASKS_FAILED ->
        stringResource(R.string.brain_health_reason_tasks_failed, health.failedTasksLast7Days)

    BrainHealthReason.REQUESTS_NOT_RETRIED ->
        stringResource(R.string.brain_health_reason_requests_not_retried, health.queueRowsLast7Days)

    BrainHealthReason.CHAT_QUOTA_NEAR_LIMIT ->
        stringResource(R.string.brain_health_reason_chat_quota_near_limit, (health.chatQuotaRatio * 100).toInt())
}
