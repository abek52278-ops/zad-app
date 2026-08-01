package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import android.util.Log

private const val TAG_NOTIF = "NotificationCenter"

/**
 * مركز تنبيهات موحد — يجمع كل مصادر التنبيهات الحقيقية في مكان واحد بدل ما تكون
 * مبعترة بين bottom-sheet في الرئيسية وتابات جوة ذكاء زاد:
 * 1) تنبيهات الذكاء الاصطناعي الاستباقية (viewModel.insights من نوع "Alert") — حية، بلا حالة قراءة.
 * 2) التنبيهات المحفوظة (viewModel.appNotifications) — مولّدة فعليًا من ZadViewModel.generateSmartNotifications
 *    (ميزانية/اشتراكات/مخزون منخفض/متابعة استهلاك) ومحفوظة في Supabase، بحالة مقروء/غير مقروء حقيقية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val insights by viewModel.insights.collectAsState()
    val notifications by viewModel.appNotifications.collectAsState()
    val zadInsights by viewModel.zadInsights.collectAsState()
    val alertInsights = insights.filter { it.type == "Alert" }
    // zad-brain's emit_insight(surface="bell") output — was written to zad_insights
    // and never surfaced anywhere; this is its bell-side home now.
    val brainAlerts = zadInsights.filter { it.surface == "bell" }
    val unreadCount = notifications.count { !it.isRead } + brainAlerts.size

    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale("ar")
            }
        }
        onDispose { tts?.shutdown() }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG_NOTIF, "NotificationCenterScreen loaded — refreshing notifications")
        viewModel.loadNotifications()
        viewModel.loadZadInsights()
    }

    fun readAloud() {
        val spoken = buildList {
            alertInsights.forEach { add("${it.title}. ${it.description}") }
            brainAlerts.forEach { add("${it.title}. ${it.body}") }
            notifications.filter { !it.isRead }.sortedByDescending { it.createdAt }.forEach { add("${it.title}. ${it.message}") }
        }
        if (spoken.isEmpty()) {
            tts?.speak("مفيش تنبيهات جديدة", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            tts?.speak(spoken.joinToString(". "), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (unreadCount > 0) {
                    Text("$unreadCount غير مقروء", style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { readAloud() }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "قراءة التنبيهات صوتيًا", tint = primary)
                }
            }
        }
    ) { padding ->
        if (alertInsights.isEmpty() && notifications.isEmpty() && brainAlerts.isEmpty()) {
            com.example.ui.components.ZadEmptyState(
                icon = Icons.Default.NotificationsNone,
                title = stringResource(R.string.no_notifications_yet),
                subtitle = "هنعلمك أول ما يحصل حاجة تستاهل انتباهك",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (alertInsights.isNotEmpty()) {
                item {
                    Text("تنبيهات ذكاء زاد", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = primary)
                    Spacer(Modifier.height(8.dp))
                }
                items(alertInsights) { alert ->
                    NotificationCard(
                        title = alert.title,
                        message = alert.description,
                        icon = Icons.Default.AutoAwesome,
                        color = primary,
                        isRead = true,
                        onClick = {}
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (brainAlerts.isNotEmpty()) {
                item {
                    Text("تنبيهات عقل زاد", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = primary)
                    Spacer(Modifier.height(8.dp))
                }
                items(brainAlerts) { alert ->
                    if (alert.kind == "question") {
                        com.example.ui.widgets.ZadQuestionCard(
                            insight = alert,
                            onAnswer = { answer -> viewModel.answerBrainQuestion(alert, answer) },
                            onDismiss = { viewModel.dismissInsight(alert.id) }
                        )
                    } else {
                        // Task 28 — "رفض بمعنى": التاب على كارت التنبيه بيفتح خيارات الرفض
                        // الثلاثة بدل ما يرفض صامت فورًا.
                        var showDismissMenu by remember(alert.id) { mutableStateOf(false) }
                        NotificationCard(
                            title = alert.title,
                            message = alert.body,
                            icon = if (alert.priority == "critical") Icons.Default.Warning else Icons.Default.AutoAwesome,
                            color = if (alert.priority == "critical") dangerColor else primary,
                            isRead = false,
                            onClick = { showDismissMenu = true }
                        )
                        com.example.ui.components.DismissReasonMenu(
                            expanded = showDismissMenu,
                            onDismissRequest = { showDismissMenu = false },
                            onReasonSelected = { reason -> viewModel.dismissInsightWithReason(alert, reason) }
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (notifications.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.app_notifications_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Spacer(Modifier.height(8.dp))
                }
                items(notifications.sortedByDescending { it.createdAt }) { notif ->
                    NotificationCard(
                        title = notif.title,
                        message = notif.message,
                        icon = Icons.Default.NotificationsActive,
                        color = if (notif.isRead) onSurfaceVariant else dangerColor,
                        isRead = notif.isRead,
                        onClick = { viewModel.markNotificationRead(notif.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * The mockup's notification row (`NOTIFS`): a white 16dp card, a 10dp dot in the
 * severity colour, then title over body. The 40dp icon circle it used to lead
 * with is gone — the icon repeated what the colour already said, and the mockup
 * spends that width on the text instead.
 *
 * Unread still reads as unread: the dot is solid on unread rows and drops to 35%
 * once read, which is the same signal without tinting the whole card.
 */
@Composable
private fun NotificationCard(
    title: String,
    message: String,
    icon: ImageVector,
    color: Color,
    isRead: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isRead) color.copy(alpha = 0.35f) else color)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Text(message, fontSize = 12.5.sp, color = onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}
