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
import com.example.ui.viewmodels.AiChatMessage
import com.example.ui.viewmodels.ZadViewModel
import com.example.voice.VoiceState
import com.example.voice.ZadVoiceManager
import kotlinx.coroutines.delay

internal fun voiceReplyForTurn(
    messages: List<AiChatMessage>,
    userMessageId: String?
): AiChatMessage? = userMessageId?.let { id ->
    messages.lastOrNull { !it.isUser && it.replyToMessageId == id }
}

/**
 * أسماء الوكلاء المتخصصين للعرض. الـ id بيجي من رد `agent_turn` السيرفر نفسه —
 * الدالة دي بس بتترجمه لاسم بشري، وأي id مش معروف بيرجع للافتراضي بدل ما يتعرض غلط.
 */
internal object SpecialistRegistry {
    fun displayNameAr(id: String): String = when (id) {
        "finance" -> "💰 وكيل المال"
        "pantry" -> "🧺 وكيل المخزون والمطبخ"
        "pharmacy" -> "💊 وكيل الصيدلية"
        "family" -> "👨‍👩‍👧 وكيل العائلة والمهام"
        else -> "🤖 زاد"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    viewModel: ZadViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { ZadVoiceManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val isListening by voiceManager.isListening.collectAsState()
    val soundLevel by voiceManager.soundLevel.collectAsState()
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    // اقتراحات معلّقة محتاجة تأكيد — بتظهر ككارت تأكيد صريح جوه شاشة الصوت
    val pendingAgentProposals by viewModel.pendingAgentProposals.collectAsState()
    // الوكيل المتخصص اللي عالج آخر لفة — إيصال من السيرفر، بيتعرض بعد اكتمال الرد
    val lastSpecialist by viewModel.lastActiveSpecialist.collectAsState()

    var awaitingVoiceReplyId by remember { mutableStateOf<String?>(null) }
    var voiceTurnGeneration by remember { mutableLongStateOf(0L) }
    var currentTranscription by remember { mutableStateOf("") }

    fun submitVoiceQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        voiceTurnGeneration += 1
        voiceManager.stopSpeaking()
        currentTranscription = clean
        voiceManager.markThinking()
        awaitingVoiceReplyId = viewModel.sendAiChatMessage(clean, voiceMode = true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(::submitVoiceQuery)
        }
    }

    fun startListeningWithPermission(invalidateCurrentTurn: Boolean = false) {
        if (invalidateCurrentTurn) {
            voiceTurnGeneration += 1
            awaitingVoiceReplyId = null
            voiceManager.stopSpeaking()
        }
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
    LaunchedEffect(chatMessages, awaitingVoiceReplyId) {
        val reply = voiceReplyForTurn(chatMessages, awaitingVoiceReplyId) ?: return@LaunchedEffect
        awaitingVoiceReplyId = null
        val spokenTurnGeneration = voiceTurnGeneration
        voiceManager.speakHumanLike(reply.text) {
            if (spokenTurnGeneration == voiceTurnGeneration) {
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

    // تعارض المايك: الخدمة الدائمة توقف الاستماع لما الشاشة دي تفتح، وترجع لما تقفل
    DisposableEffect(Unit) {
        com.example.voice.HeyZadWakeService.pause(context)
        onDispose {
            if (com.example.data.WakePrefs.isEnabled(context)) {
                com.example.voice.HeyZadWakeService.resume(context)
            }
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
                    val personaName = when (persona) {
                        com.example.voice.ZadNaturalVoiceEngine.VoicePersona.SARAH_STUDIO_WARM ->
                            stringResource(R.string.voice_persona_sarah)
                        com.example.voice.ZadNaturalVoiceEngine.VoicePersona.KARIM_STUDIO_PRO ->
                            stringResource(R.string.voice_persona_karim)
                        com.example.voice.ZadNaturalVoiceEngine.VoicePersona.PET_MASCOT_CUTE ->
                            stringResource(R.string.voice_persona_zad)
                    }
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
                            personaName,
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

            // ── Orb احترافي بأسلوب ChatGPT Voice: كرة متوهجة واحدة كبيرة، تتنفس
            //    مع الصوت، لونها بيتغير حسب الحالة — بدون وجه كرتوني. ──
            val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
            val breathe by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1f + 0.06f + (soundLevel * 0.22f),
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "orb_breathe"
            )
            // دوران بطيء للهالة الخارجية — إحساس "حية" حتى في السكون
            val haloRotate by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(24_000, easing = androidx.compose.animation.core.LinearEasing)),
                label = "halo_rotate"
            )

            val orbState = when {
                voiceState is VoiceState.Speaking -> OrbState.Speaking
                voiceState is VoiceState.Thinking -> OrbState.Thinking
                isListening -> OrbState.Listening
                voiceState is VoiceState.Error -> OrbState.Error
                else -> OrbState.Idle
            }

            ZadVoiceOrb(
                state = orbState,
                size = 180.dp,
                breathe = breathe,
                haloRotationDegrees = haloRotate,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // V4 — real Lottie ripple rings (zad_v4_ai_orb.json) layered behind the
            // orb while listening; Canvas rings live inside ZadVoiceOrb itself.
            if (orbState == OrbState.Listening) {
                com.example.ui.v2.AiOrbLottie(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .offset(y = (-92).dp)
                        .size(220.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // حالة الوكيل الحالية (من trace السيرفر) أو الحالة العامة
            val specialistLine = when (val s = voiceState) {
                is VoiceState.Thinking -> stringResource(R.string.voice_status_thinking)
                else -> null
            }
            if (specialistLine != null) {
                Text(
                    specialistLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }

            // Audio Waveform Equalizer Bars
            if (isListening) {
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
                is VoiceState.Listening -> stringResource(R.string.voice_status_listening)
                is VoiceState.Recognized -> stringResource(R.string.voice_status_recognized, state.text)
                is VoiceState.Thinking -> stringResource(R.string.voice_status_thinking)
                is VoiceState.Speaking -> stringResource(R.string.voice_status_speaking)
                is VoiceState.Error -> state.message
                else -> stringResource(R.string.voice_status_ready)
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

            // كارت تأكيد الاقتراحات المالية — نفس المسار المستخدم في الشات النصي،
            // عشان تأكيد صوتي ("أيوه") أو زر الاتنين ينفذوا نفس `agent_confirm`
            if (pendingAgentProposals.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AgentProposalsCard(
                    proposals = pendingAgentProposals,
                    onConfirm = {
                        viewModel.confirmPendingAgentProposals()
                        voiceManager.speakHumanLike("تمام، نفذتها ✅") {
                            startListeningWithPermission()
                        }
                    },
                    onCancel = {
                        viewModel.cancelPendingAgentProposals()
                        voiceManager.speakHumanLike("تمام، ملغيتها.") {
                            startListeningWithPermission()
                        }
                    }
                )
            }

            // كارت الوكيل المتخصص الحي — الاسم بيجي من إيصال السيرفر (specialist في
            // رد agent_turn)، فمفيش أي اسم بيترسم من غير تنفيذ حقيقي حصل فعلاً.
            if (lastSpecialist != null && voiceState is VoiceState.Speaking) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = SpecialistRegistry.displayNameAr(lastSpecialist!!),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    stringResource(R.string.voice_suggestion_spend),
                    stringResource(R.string.voice_suggestion_meal),
                    stringResource(R.string.voice_suggestion_analyze)
                )
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
                            startListeningWithPermission(invalidateCurrentTurn = true)
                        } else if (isListening) {
                            voiceManager.stopListening()
                        } else {
                            startListeningWithPermission(invalidateCurrentTurn = true)
                        }
                    },
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .pressableScale()
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.voice_speak_button),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
