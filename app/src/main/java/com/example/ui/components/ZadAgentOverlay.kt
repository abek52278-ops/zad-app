package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodels.AiChatMessage
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

/**
 * W6 — سطح محادثة سريع فوق أي شاشة (Phase 4 من السبيك: "persistent chat entry point
 * available on every screen — not just a separate chat screen"). قبل كده الشات كان
 * شاشة كاملة بس (ZadIntelligenceScreen عبر ZadRoutes.ASSISTANT)، يعني سؤال سريع وأنت
 * في المخزون أو الميزانية بيحتاج خروج كامل من الشاشة.
 *
 * ده مش فقاعة عائمة تانية — `FloatingMascotCompanion` أصلاً موجود على كل شاشة
 * chromeVisible (MainScreen.kt) وله شكل ومزاج وحركة متقنة. بدل تكرارها، الضغط الطويل
 * على المسكوت بيفتح الـ sheet ده بدل ما ينقل الشاشة بالكامل (`onQuickChat` في
 * FloatingMascot.kt). فتفاعل واحد، مش اتنين متنافسين.
 *
 * `ZadAgentOverlaySheet` **واجهة بس** — نفس منطق الشات بالظبط
 * (`viewModel.sendAiChatMessage`/`aiChatMessages`/`isAiTyping`)، مفيش تنفيذ تاني ولا
 * نسخة موازية من الحلقة. زرار "الشات الكامل" جوه الـ sheet بينقل لـ ZadIntelligenceScreen
 * (تاريخ أطول، صوت، كاميرا) — الـ sheet ده للسؤال السريع بس.
 */
@Composable
fun ZadAgentOverlay(
    visible: Boolean,
    viewModel: ZadViewModel,
    onDismiss: () -> Unit,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            ZadAgentOverlaySheet(
                viewModel = viewModel,
                onDismiss = onDismiss,
                onOpenFullChat = onOpenFullChat,
            )
        }
    }
}

/** الكارت المنبثق: تاريخ آخر رسايل + حقل كتابة. */
@Composable
private fun ZadAgentOverlaySheet(
    viewModel: ZadViewModel,
    onDismiss: () -> Unit,
    onOpenFullChat: () -> Unit,
) {
    val messages by viewModel.aiChatMessages.collectAsState()
    val isTyping by viewModel.isAiTyping.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        viewModel.sendAiChatMessage(text)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 420.dp)
            .zadCardShadow(RoundedCornerShape(20.dp), elevation = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            // يمنع الضغط جوه الكارت إنه يوصل للـ Box اللي وراه (اللي بيقفل عند اللمس بره)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("زاد", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenFullChat, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "فتح الشات الكامل", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).heightIn(min = 120.dp, max = 280.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg -> OverlayMessageBubble(msg) }
            if (isTyping) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceContainerLow)
                            .padding(10.dp)
                    ) {
                        Text("زاد بيكتب...", style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider(color = outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اسأل زاد...", style = Typography.bodyMedium) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = outlineVariant,
                    focusedBorderColor = primary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { scope.launch { send() } },
                modifier = Modifier.size(40.dp).clip(CircleShape).background(primary).pressableScale(),
            ) {
                Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OverlayMessageBubble(msg: AiChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (msg.isUser) primary else surfaceContainerLow)
                .padding(10.dp)
        ) {
            Text(
                msg.text,
                style = Typography.bodySmall.copy(lineHeight = 18.sp),
                color = if (msg.isUser) Color.White else onSurface,
            )
        }
    }
}
