package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SupabaseRepo
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadLoadingState
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * W5 — "سجل تعديلات زاد": شاشة عرض agent_actions (W1) + زرار تراجع لكل فعل قابل
 * للتراجع. نفس مبدأ Claude Code بيعرض الـ diff بتاعه قبل ما ينفّذه — هنا الفعل
 * اتنفذ خلاص (auto/confirm tiers)، فده هو خط الشفافية البديل: تعرف زاد عمل إيه
 * بالظبط وترجع فيه لو غلط.
 *
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط HelpSupportScreen: قراءة/كتابة
 * Supabase مباشرة من غير أي نداء LLM هنا خالص، فمفيش داعي لدورة حياة ViewModel.
 */
@Composable
fun AgentActionLogScreen(onBack: () -> Unit) {
    var actions by remember { mutableStateOf<List<SupabaseRepo.AgentAction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var undoingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun refresh() {
        loading = true
        try {
            actions = SupabaseRepo.getAgentActions()
        } catch (e: Exception) {
            android.util.Log.e("AgentActionLogScreen", "Failed to get agent actions: ${e.message}")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun onUndo(action: SupabaseRepo.AgentAction) {
        if (undoingId != null) return
        undoingId = action.id
        scope.launch {
            val result = SupabaseRepo.undoAgentAction(action.id)
            undoingId = null
            if (result.ok) {
                refresh()
                snackbarHostState.showSnackbar("تم التراجع عن \"${SupabaseRepo.toolLabel(action.toolName)}\"")
            } else {
                snackbarHostState.showSnackbar(undoErrorMessage(result.error))
            }
        }
    }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.agent_action_log_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
        }
    ) { padding ->
        when {
            loading -> ZadLoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            actions.isEmpty() -> ZadEmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.agent_action_log_empty_title),
                subtitle = stringResource(R.string.agent_action_log_empty_subtitle),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, com.example.ui.theme.ZadHubListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(actions, key = { it.id }) { action ->
                    AgentActionRow(
                        action = action,
                        isUndoing = undoingId == action.id,
                        onUndo = { onUndo(action) }
                    )
                }
            }
        }
    }
}

private fun undoErrorMessage(error: String?): String = when (error) {
    "newer_action_exists" -> "فيه تعديل أحدث على نفس العنصر — تراجع عنه هو الأول"
    "already_undone" -> "الفعل ده اتراجع عنه بالفعل"
    "action_never_applied" -> "الفعل ده كان مرفوض أصلاً، مفيش حاجة تتراجع عنها"
    "action_not_undoable", "table_not_undoable" -> "الفعل ده مش قابل للتراجع"
    "target_row_missing" -> "العنصر ده اتحذف أو مش موجود دلوقتي"
    "not_authenticated", "ownership_mismatch" -> "حصلت مشكلة صلاحيات — جرب تسجل دخول تاني"
    else -> "معرفتش أتراجع عن الفعل ده، جرب تاني"
}

@Composable
private fun statusLabel(status: String): Pair<String, Color> = when (status) {
    "applied" -> "اتنفذ" to successColor
    "undone" -> "اترجع عنه" to onSurfaceVariant
    "rejected" -> "اترفض" to dangerColor
    else -> status to onSurfaceVariant
}

private fun sourceLabel(source: String): String = when (source) {
    "app_chat" -> "شات التطبيق"
    "telegram" -> "تليجرام"
    "confirm" -> "تأكيد المستخدم"
    "daily" -> "مراجعة يومية"
    "event" -> "حدث تلقائي"
    else -> source
}

@Composable
private fun AgentActionRow(
    action: SupabaseRepo.AgentAction,
    isUndoing: Boolean,
    onUndo: () -> Unit,
) {
    val (statusText, statusColor) = statusLabel(action.status)
    val at = remember(action.createdAt) { formatActionTime(action.createdAt) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.example.ui.theme.ZadLuxe.squircle)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, com.example.ui.theme.ZadLuxe.squircle)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    SupabaseRepo.toolLabel(action.toolName),
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${sourceLabel(action.source)} · $at",
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(statusText, style = Typography.labelSmall, color = statusColor, fontWeight = FontWeight.Medium)
            }
        }

        if (!action.resultSummary.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                action.resultSummary,
                style = Typography.bodyMedium.copy(lineHeight = 20.sp),
                color = onSurfaceVariant
            )
        }

        if (action.isUndoable) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onUndo,
                enabled = !isUndoing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isUndoing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.agent_action_log_undo))
                }
            }
        }
    }
}

private fun formatActionTime(iso: String): String = try {
    val odt = java.time.OffsetDateTime.parse(iso)
    odt.atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm"))
} catch (e: Exception) {
    iso
}
