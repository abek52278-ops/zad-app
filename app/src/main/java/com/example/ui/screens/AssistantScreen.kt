package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.viewmodels.AiChatMessage
import kotlinx.coroutines.delay

@Composable
fun AssistantScreen(viewModel: ZadViewModel, onOpenDrawer: () -> Unit = {}) {
    val messages by viewModel.aiChatMessages.collectAsState()
    val isTyping by viewModel.isAiTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + if (isTyping) 0 else -1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        AssistantTopBar(onOpenDrawer)

        // Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data class Suggestion(val icon: ImageVector, val text: String)
            val suggestions = listOf(
                Suggestion(Icons.Default.Coffee, "هل يمكنني شراء قهوة اليوم؟"),
                Suggestion(Icons.Default.Restaurant, "اقترح لي طبخة سريعة من ثلاجتي"),
                Suggestion(Icons.Default.PieChart, "لخص لي مصاريفي لهذا الشهر"),
                Suggestion(Icons.Default.HourglassTop, "ما هي المنتجات التي أوشكت على الانتهاء؟")
            )
            suggestions.forEach { suggestion ->
                val chipSource = remember { MutableInteractionSource() }
                val chipPressed by chipSource.collectIsPressedAsState()
                val chipScale by animateFloatAsState(if (chipPressed) 0.95f else 1f)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = primaryContainer,
                    modifier = Modifier.scale(chipScale),
                    interactionSource = chipSource,
                    onClick = { viewModel.sendAiChatMessage(suggestion.text) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            suggestion.icon,
                            contentDescription = null,
                            tint = onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = suggestion.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Chat Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id ?: messages.indexOf(it) }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 3 }
                ) {
                    ChatBubble(msg)
                }
            }
            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("تحدث مع زاد...", color = onSurfaceVariant) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = outlineVariant,
                        focusedContainerColor = surfaceContainerLow,
                        unfocusedContainerColor = surfaceContainerLow
                    ),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(12.dp))

                val sendSource = remember { MutableInteractionSource() }
                val sendPressed by sendSource.collectIsPressedAsState()
                val sendScale by animateFloatAsState(if (sendPressed) 0.9f else 1f)

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendAiChatMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .scale(sendScale)
                        .clip(CircleShape)
                        .background(primary),
                    interactionSource = sendSource
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: AiChatMessage) {
    val isUser = msg.isUser
    val bubbleColor = if (isUser) primary else surfaceContainerHigh
    val textColor = if (isUser) onPrimary else onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "Zad AI", tint = primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 0.dp else 16.dp,
                    bottomEnd = if (isUser) 16.dp else 0.dp
                ))
                .background(bubbleColor)
                .padding(14.dp)
        ) {
            Text(text = msg.text, color = textColor, style = Typography.bodyLarge, lineHeight = 24.sp)
        }
    }
}

@Composable
fun TypingIndicator() {
    val dotCount = 3
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = "Zad AI", tint = primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
                .background(surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(dotCount) { index ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, delayMillis = index * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantTopBar(onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "مساعد زاد",
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = background
        )
    )
}
