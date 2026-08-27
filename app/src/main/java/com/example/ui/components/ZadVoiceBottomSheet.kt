package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.ZadCutePetSoundFx

/**
 * Siri-style AI Voice Sheet (Matching Image 5 & zad_premium_v5.html overlay).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    isListening: Boolean,
    recognizedText: String,
    onDismiss: () -> Unit,
    onSendPrompt: (String) -> Unit
) {
    val quickChips = listOf(
        "حلل مصاريفي 📊",
        "اقترحي أكلة للغداء 🍲",
        "كم المتبقي في الميزانية؟ 💰",
        "سجلت مصروف 50 قهوة ☕"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مساعد زاد الصوتي 🎙️",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Central 3D SmartBot Agent with Listening emotion
            ZadSmartBotAgent(
                sizeDp = 92.dp,
                emotion = if (isListening) ZadBotEmotion.LISTENING else ZadBotEmotion.IDLE
            )

            // Dynamic Audio Waveform Bars
            ZadAudioWavebars(
                isListening = isListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            // Transcript text or hint
            Text(
                text = if (recognizedText.isNotBlank()) recognizedText else "أنا أسمعك… تكلّم مع زاد بحرية",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (recognizedText.isNotBlank()) Color.White else Color(0xFF6EE7B7),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Quick contextual prompt chips
            Text(
                text = "أو اختر سؤالاً سريعاً:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSoundType.HappyChirp)
                                onSendPrompt(chip)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD9F2E6)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Animated Audio Wavebars simulating voice input.
 */
@Composable
fun ZadAudioWavebars(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavebars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val count = 9
        val barWidth = 6.dp.toPx()
        val spacing = 8.dp.toPx()
        val totalWidth = count * barWidth + (count - 1) * spacing
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val multiplier = if (isListening) {
                (Math.sin((phase + i * 0.7).toDouble()).toFloat().coerceIn(0.2f, 1f) * 0.9f)
            } else 0.25f
            val barHeight = (size.height * multiplier).coerceAtLeast(6.dp.toPx())
            val x = startX + i * (barWidth + spacing)
            val y = centerY - barHeight / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF6EE7B7), Color(0xFF0F9B76))),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}


/**
 * Overload connecting directly to ZadViewModel StateFlow.
 */
@Composable
fun ZadVoiceBottomSheet(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    onDismiss: () -> Unit
) {
    var recognizedText by remember { mutableStateOf("") }
    val isListening = true

    ZadVoiceBottomSheet(
        isListening = isListening,
        recognizedText = recognizedText,
        onDismiss = onDismiss,
        onSendPrompt = { prompt ->
            viewModel.sendMessage(prompt)
            onDismiss()
        }
    )
}
