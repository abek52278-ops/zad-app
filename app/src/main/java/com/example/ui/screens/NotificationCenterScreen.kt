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
import com.example.R
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
    val alertInsights = insights.filter { it.type == "Alert" }
    val unreadCount = notifications.count { !it.isRead }

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
    }

    fun readAloud() {
        val spoken = buildList {
            alertInsights.forEach { add("${it.title}. ${it.description}") }
            notifications.filter { !it.isRead }.sortedByDescending { it.createdAt }.forEach { add("${it.title}. ${it.message}") }
        }
        if (spoken.isEmpty()) {
            tts?.speak("مفيش تنبيهات جديدة", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            tts?.speak(spoken.joinToString(". "), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.Bold)
                        if (unreadCount > 0) {
                            Text("$unreadCount غير مقروء", style = Typography.labelSmall, color = onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { readAloud() }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "قراءة التنبيهات صوتيًا", tint = primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        if (alertInsights.isEmpty() && notifications.isEmpty()) {
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

@Composable
private fun NotificationCard(
    title: String,
    message: String,
    icon: ImageVector,
    color: Color,
    isRead: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) surface else color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(title, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
                    if (!isRead) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dangerColor))
                    }
                }
                Text(message, style = Typography.bodySmall, color = onSurfaceVariant)
            }
        }
    }
}
