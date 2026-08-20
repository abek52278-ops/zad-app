package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.primary
import com.example.ui.theme.secondary
import com.example.ui.viewmodels.ZadViewModel
import com.example.voice.VoiceState
import com.example.voice.ZadVoiceManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    viewModel: ZadViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { ZadVoiceManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val soundLevel by voiceManager.soundLevel.collectAsState()
    val chatMessages by viewModel.aiChatMessages.collectAsState()

    var lastSpokenResponseId by remember {
        mutableStateOf(chatMessages.lastOrNull { !it.isUser }?.id)
    }
    var awaitingVoiceReply by remember { mutableStateOf(false) }
    var currentTranscription by remember { mutableStateOf("") }

    fun submitVoiceQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        currentTranscription = clean
        lastSpokenResponseId = chatMessages.lastOrNull { !it.isUser }?.id
        awaitingVoiceReply = true
        voiceManager.markThinking()
        viewModel.sendAiChatMessage(clean, voiceMode = true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(::submitVoiceQuery)
        }
    }

    fun startListeningWithPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            voiceManager.startListening(::submitVoiceQuery)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start listening on launch
    LaunchedEffect(Unit) {
        startListeningWithPermission()
    }

    var selectedPersona by remember { mutableStateOf(voiceManager.getCurrentPersona()) }

    // When agent responds, speak with selected natural studio persona and resume listening afterwards
    LaunchedEffect(chatMessages, awaitingVoiceReply) {
        if (!awaitingVoiceReply) return@LaunchedEffect
        val lastAssistantMessage = chatMessages.lastOrNull { !it.isUser }
        if (lastAssistantMessage != null && lastAssistantMessage.id != lastSpokenResponseId) {
            lastSpokenResponseId = lastAssistantMessage.id
            awaitingVoiceReply = false
            voiceManager.speakHumanLike(lastAssistantMessage.text) {
                // المحادثة الحية المستمرة — يستمع تلقائياً بعد انتهاء الرد مثل ChatGPT Voice و Gemini Live
                startListeningWithPermission()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.release()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.voice_chat_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.voice_chat_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Voice Personas Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.05f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                com.example.voice.ZadNaturalVoiceEngine.VoicePersona.values().forEach { persona ->
                    val isSel = selectedPersona == persona
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable {
                                selectedPersona = persona
                                voiceManager.setVoicePersona(persona)
                                if (persona.isPetPersona) {
                                    com.example.voice.ZadCutePetSoundFx.play(com.example.voice.ZadCutePetSoundFx.PetSound.MeowChirp)
                                } else {
                                    com.example.voice.ZadCutePetSoundFx.play(com.example.voice.ZadCutePetSoundFx.PetSound.HappyChirp)
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            persona.displayNameAr.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3D Animated Voice Orb / Wave
            val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f + (soundLevel * 0.25f),
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            val companionMood = when (voiceState) {
                is VoiceState.Listening -> CompanionState.Happy
                is VoiceState.Thinking -> CompanionState.Focused
                is VoiceState.Speaking -> CompanionState.Celebrating
                is VoiceState.Error -> CompanionState.Alert
                else -> CompanionState.Idle
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    companionMood.skyColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                CompanionOrb(
                    state = companionMood,
                    size = 96.dp,
                    animated = true
                )
            }

            Spacer(Modifier.height(14.dp))

            // Audio Waveform Equalizer Bars
            if (voiceState is VoiceState.Listening) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(24.dp)
                ) {
                    val heights = listOf(
                        10.dp + (soundLevel * 14).dp,
                        16.dp + (soundLevel * 20).dp,
                        8.dp + (soundLevel * 24).dp,
                        14.dp + (soundLevel * 18).dp,
                        10.dp + (soundLevel * 12).dp
                    )
                    heights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(h)
                                .clip(RoundedCornerShape(2.dp))
                                .background(primary)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Status & transcription
            val statusText = when (val state = voiceState) {
                is VoiceState.Listening -> "أنا سامعاك، اتفضل قول اللي عاوزه..."
                is VoiceState.Recognized -> "فهمت: \"${state.text}\""
                is VoiceState.Thinking -> "جاري التفكير والتنفيذ في عقل زاد..."
                is VoiceState.Speaking -> "تفضل الإجابة:"
                is VoiceState.Error -> state.message
                else -> "جاهزة لسماع طلبك"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (voiceState is VoiceState.Error) MaterialTheme.colorScheme.error else primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Display latest answer or recognized text
            val displayText = when (val state = voiceState) {
                is VoiceState.Speaking -> state.text
                is VoiceState.Recognized -> state.text
                else -> {
                    val last = chatMessages.lastOrNull { !it.isUser }
                    last?.text ?: currentTranscription
                }
            }

            if (displayText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp),
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick suggestion chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf("صرفت ٥٠ قهوة", "اقترحي أكلة", "حلل مصاريفي")
                suggestions.forEach { prompt ->
                    SuggestionChip(
                        onClick = {
                            voiceManager.stopSpeaking()
                            submitVoiceQuery(prompt)
                        },
                        label = { Text(prompt, fontSize = 11.sp) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mic Action Button
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FloatingActionButton(
                    onClick = {
                        if (voiceState is VoiceState.Speaking) {
                            voiceManager.stopSpeaking()
                        } else if (voiceState is VoiceState.Listening) {
                            voiceManager.stopListening()
                        } else {
                            startListeningWithPermission()
                        }
                    },
                    containerColor = if (voiceState is VoiceState.Listening) MaterialTheme.colorScheme.error else primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .pressableScale()
                ) {
                    Icon(
                        if (voiceState is VoiceState.Listening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.voice_speak_button),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
