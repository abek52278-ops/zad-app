package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voice.VoiceState
import com.example.voice.ZadCutePetSoundFx
import com.example.voice.ZadVoiceManager

/**
 * Siri / ChatGPT Advanced Voice style Live AI Voice Sheet.
 * يدعم الاستماع اللحظي بالميكروفون، الموجات الصوتية الحية المتفاعلة مع نبرة الصوت،
 * والتعرف التلقائي على الكلام وتحويله إلى ردود صوتية فورية.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { ZadVoiceManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val isListeningState by voiceManager.isListening.collectAsState()
    val soundLevel by voiceManager.soundLevel.collectAsState()

    var recognizedLiveText by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            voiceManager.startListening { result ->
                if (result.isNotBlank()) {
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
        }
    }

    // بدء الاستماع فور فتح النافذة
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            voiceManager.startListening { result ->
                if (result.isNotBlank()) {
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
        }
    }

    val quickChips = listOf(
        "حلل مصاريفي اليوم 📊",
        "اقترحي أكلة للغداء بالمخزون 🍲",
        "كم المتبقي في الميزانية؟ 💰",
        "سجلت مصروف 50 قهوة ☕"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.65f),
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isListeningState) Color(0xFF34D399) else Color(0xFFF59E0B)))
                    Text(
                        text = "مساعد زاد الصوتي الحي 🎙️",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
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

            // Central SmartBot Agent with Dynamic Listening Emotion
            ZadSmartBotAgent(
                sizeDp = 96.dp,
                emotion = when (voiceState) {
                    is VoiceState.Listening -> ZadBotEmotion.LISTENING
                    is VoiceState.Thinking -> ZadBotEmotion.THINKING
                    is VoiceState.Speaking -> ZadBotEmotion.SPEAKING
                    else -> ZadBotEmotion.IDLE
                }
            )

            // Dynamic Audio Waveform Bars responsive to soundLevel
            ZadAudioWavebars(
                isListening = isListeningState,
                soundLevel = soundLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            // Live Transcript text or listening hint
            Text(
                text = when {
                    recognizedLiveText.isNotBlank() -> recognizedLiveText
                    isListeningState -> "أنا أسمعك الآن… تكلّم مع زاد بحرية وسأجيبك فوراً"
                    voiceState is VoiceState.Thinking -> "عقل زاد يفكّر بالرد…"
                    else -> "اضغط على المايك لبدء التحدث 🎙️"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (recognizedLiveText.isNotBlank()) Color.White else Color(0xFF6EE7B7),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Interactive Mic Push / Stop Button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isListeningState) {
                                listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                            } else {
                                listOf(Color(0xFF0F9B76), Color(0xFF064E3B))
                            }
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable {
                        if (isListeningState) {
                            voiceManager.stopListening()
                        } else {
                            if (hasAudioPermission) {
                                voiceManager.startListening { result ->
                                    if (result.isNotBlank()) {
                                        recognizedLiveText = result
                                        viewModel.sendAiChatMessage(result, voiceMode = true)
                                    }
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListeningState) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Quick contextual prompt chips
            Text(
                text = "أو اختر سؤالاً جاهزاً:",
                fontSize = 11.5.sp,
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
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(9999.dp))
                            .clickable {
                                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                                recognizedLiveText = chip
                                viewModel.sendAiChatMessage(chip, voiceMode = true)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD9F2E6)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Animated Audio Wavebars simulating voice input responsive to microphone amplitude.
 */
@Composable
fun ZadAudioWavebars(
    isListening: Boolean,
    soundLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavebars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val count = 11
        val barWidth = 5.dp.toPx()
        val spacing = 7.dp.toPx()
        val totalWidth = count * barWidth + (count - 1) * spacing
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val dynamicWave = (kotlin.math.sin((phase + i * 0.6).toDouble()).toFloat().coerceIn(0.2f, 1f))
            val amplitudeBoost = (soundLevel * 1.5f).coerceIn(0f, 1f)
            val multiplier = if (isListening) {
                (dynamicWave * 0.5f + amplitudeBoost * 0.5f).coerceIn(0.25f, 1f)
            } else 0.18f

            val barHeight = (size.height * multiplier).coerceAtLeast(6.dp.toPx())
            val x = startX + i * (barWidth + spacing)
            val y = centerY - barHeight / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isListening) {
                        listOf(Color(0xFF6EE7B7), Color(0xFF0F9B76))
                    } else {
                        listOf(Color(0xFF64748B), Color(0xFF334155))
                    }
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
