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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.voice.LiveVoiceState
import com.example.voice.VoiceState
import com.example.voice.ZadCutePetSoundFx
import com.example.voice.ZadLiveVoiceSession
import com.example.voice.ZadVoiceManager
import com.example.ui.theme.*

/**
 * Siri / ChatGPT Advanced Voice style Live AI Voice Sheet.
 * يدعم الاستماع اللحظي بالميكروفون، الموجات الصوتية الحية المتفاعلة مع نبرة الصوت،
 * والتعرف التلقائي على الكلام وتحويله إلى ردود صوتية فورية.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    onDismiss: () -> Unit,
    initialLiveMode: Boolean = false
) {
    val context = LocalContext.current
    // ZadVoiceManager بقى object مشترك (singleton) مش instance منفصل لكل شيت — init()
    // idempotent، آمنة تتنادى من أكتر من مكان.
    val voiceManager = remember { ZadVoiceManager.apply { init(context) } }
    val voiceState by voiceManager.voiceState.collectAsState()
    val isListeningState by voiceManager.isListening.collectAsState()
    val soundLevel by voiceManager.soundLevel.collectAsState()
    val mood by viewModel.companionMood.collectAsState()

    // بند 33.1/33.2/33.3 — مكالمة حية حقيقية (Gemini Live API عبر zad-voice-live)، بديل
    // اختياري للمسار دور-بدور (STT محلي → نداء شات → TTS) اللي فوق. افتراضياً off عشان
    // المسار المُتحقق منه يفضل هو الافتراضي — ده أول اتصال WebSocket خام في التطبيق،
    // ومعملش عليه اختبار جهاز حقيقي (شوف تحذير ZadLiveVoiceSession.kt عن شكل الفريمات).
    var isLiveMode by remember { mutableStateOf(initialLiveMode) }
    val liveSession = remember { ZadLiveVoiceSession.apply { init(context) } }
    val liveState by liveSession.state.collectAsState()
    val liveMicLevel by liveSession.micLevel.collectAsState()
    // بياخد الـflow مش القيمة — شوف rememberOrbAudioLevel لسبب ده بالتفصيل.
    val orbLevel = rememberOrbAudioLevel(
        if (isLiveMode) liveSession.micLevel else com.example.voice.ZadVoiceManager.soundLevel
    )

    LaunchedEffect(isLiveMode) {
        if (isLiveMode) {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            // فاصل صغير قبل ما AudioRecord يحاول ياخد المايك — نفس سبب التأخيرة جوه
            // ZadVoiceManager.startListening(): SpeechRecognizer.stopListening() مش
            // متزامن، وخدمة النظام محتاجة لحظة تفرّج المايك قبل ما جلسة تانية تاخده.
            kotlinx.coroutines.delay(200)
            liveSession.start { /* liveState بيتحدث تلقائي برسالة الخطأ */ }
        } else {
            liveSession.stop()
        }
    }

    var recognizedLiveText by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // إعادة الاستماع التلقائي: أخطاء التعرف (مهلة صمت/لا تطابق/تعرف مشغول) شائعة جداً —
    // من غير retry العميل يشوف "خطأ" والشيت يبان ميت (معلق) رغم إنه سليم.
    // إعادة الاستماع التلقائي: أخطاء التعرف (مهلة صمت/لا تطابق) —
    // صامت تماماً بدون صوت رنين أو إزعاج مع مؤشر بصري فقط.
    fun listen(silent: Boolean = false, onHeard: (String) -> Unit) {
        voiceManager.startListening(silent = silent) { result ->
            if (result.isNotBlank()) onHeard(result)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            if (isLiveMode) {
                liveSession.start { }
            } else {
                listen { result ->
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
        }
    }

    // بدء الاستماع فور فتح النافذة
    LaunchedEffect(hasAudioPermission, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        if (hasAudioPermission) {
            listen { result ->
                recognizedLiveText = result
                viewModel.sendAiChatMessage(result, voiceMode = true)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // "مش بيرد": الرد كان بيتعرض نصاً بس — مفيش نطق نهائياً في النافذة دي.
    // آخر رد من الوكيل ينطق بصوت سارة البشري، وبعد انتهاء الرّد يرجع يستمع تلقائياً
    val isTyping by viewModel.isAiTyping.collectAsState()
    val messages by viewModel.aiChatMessages.collectAsState()
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    var replyCountAtSpeakStart by remember { mutableStateOf(0) }
    LaunchedEffect(isTyping) {
        if (isTyping) {
            replyCountAtSpeakStart = messages.count { !it.isUser }
        }
    }
    LaunchedEffect(isTyping, messages, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        if (isTyping) return@LaunchedEffect
        val lastReply = messages.lastOrNull { !it.isUser } ?: return@LaunchedEffect
        if (lastReply.text.isBlank()) return@LaunchedEffect
        if (lastReply.id == lastSpokenMessageId) return@LaunchedEffect
        if (messages.count { !it.isUser } <= replyCountAtSpeakStart) return@LaunchedEffect
        lastSpokenMessageId = lastReply.id
        voiceManager.stopListening()
        voiceManager.speakHumanLike(lastReply.text) {
            if (hasAudioPermission) {
                listen { result ->
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
        }
    }

    // إعادة الاستماع التلقائي بصمت وبدون أي صوت إزعاج: مؤشر بصري فقط
    var autoRetryCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(voiceState, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        if (voiceState is VoiceState.Error) {
            if (autoRetryCount < 2) {
                autoRetryCount++
                kotlinx.coroutines.delay(1200)
                if (hasAudioPermission && voiceState is VoiceState.Error) {
                    listen(silent = true) { result ->
                        autoRetryCount = 0
                        recognizedLiveText = result
                        viewModel.sendAiChatMessage(result, voiceMode = true)
                    }
                }
            }
        } else if (voiceState is VoiceState.Recognized || voiceState is VoiceState.Listening) {
            autoRetryCount = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            // stop() مش release(): الاتنين بيحرروا المايك والسماعة وتركيز الصوت
            // ويقفلوا الـWebSocket — الفرق الوحيد إن release بتلغي الـscope كمان،
            // وده كان بيقتل السينجلتون للأبد فتبقى الفتحة التانية صامتة.
            liveSession.stop()
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
        containerColor = ZadVoiceDarkSheetBg,
        scrimColor = Color.Black.copy(alpha = 0.70f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.20f))
            )
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Status indicator + Live Mode Toggle + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val indicatorColor = if (isLiveMode) ZadVoiceWaveMint else if (isListeningState) ZadVoiceCyan else ZadVoiceTextSoft
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                            .then(if (isLiveMode || isListeningState) Modifier.pulseGlow(minScale = 0.85f, maxScale = 1.35f) else Modifier)
                    )
                    Text(
                        text = if (isLiveMode) "عقل زاد • مباشر" else "مساعد زاد الصوتي",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Toggle Live Mode Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (isLiveMode) ZadVoiceWaveEmerald.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (isLiveMode) ZadVoiceWaveEmerald.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(9999.dp)
                            )
                            .clickable { isLiveMode = !isLiveMode }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isLiveMode) "⚡ وضع مباشر" else "مكالمة حية",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveMode) ZadVoiceWaveMint else ZadVoiceTextSoft
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Central ElevenLabs-style Living Orb
            CompanionOrb(
                size = 110.dp,
                state = mood,
                audioLevel = orbLevel
            )

            // Dynamic Audio Waveform Bars responsive to soundLevel (26 thin bars)
            ZadAudioWavebars(
                isListening = if (isLiveMode) liveState is LiveVoiceState.Listening || liveState is LiveVoiceState.ModelSpeaking else isListeningState,
                soundLevel = if (isLiveMode) liveMicLevel else soundLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            // Live Transcript text or listening hint
            val errorMsg = if (isLiveMode) (liveState as? LiveVoiceState.Error)?.message else (voiceState as? VoiceState.Error)?.message
            Text(
                text = when {
                    errorMsg != null -> "⚠️ $errorMsg\nاضغط على المايك للمحاولة مجدداً"
                    isLiveMode && liveState is LiveVoiceState.Connecting -> "بيتّصل بعقل زاد المباشر…"
                    isLiveMode && liveState is LiveVoiceState.ModelSpeaking -> "زاد بيتكلم… اتكلم في أي وقت تقاطعه"
                    isLiveMode && liveState is LiveVoiceState.Listening -> "مكالمة مباشرة — اتكلم بحرية، زاد سامعك دلوقتي"
                    isLiveMode -> "اضغط على المايك لبدء المكالمة المباشرة 🎙️"
                    recognizedLiveText.isNotBlank() -> recognizedLiveText
                    isListeningState -> "أنا أسمعك الآن… تكلّم مع زاد بحرية وسأجيبك فوراً"
                    voiceState is VoiceState.Thinking -> "عقل زاد يفكّر بالرد…"
                    else -> "اضغط على المايك لبدء التحدث 🎙️"
                },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (recognizedLiveText.isNotBlank()) Color.White else ZadVoiceWaveMint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            val isSpeakingState = if (isLiveMode) liveState is LiveVoiceState.ModelSpeaking else voiceState is VoiceState.Speaking
            val isLiveConnected = liveState is LiveVoiceState.Listening || liveState is LiveVoiceState.ModelSpeaking

            // Glassmorphic Glowing Central Mic Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .then(if (isSpeakingState || isLiveConnected || isListeningState) Modifier.pulseGlow(minScale = 1f, maxScale = 1.07f) else Modifier)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = when {
                                isLiveMode && isLiveConnected -> listOf(ZadVoiceDangerStart, ZadVoiceDangerEnd)
                                !isLiveMode && isListeningState -> listOf(ZadVoiceDangerStart, ZadVoiceDangerEnd)
                                isSpeakingState -> listOf(ZadVoiceAlertAmber, ZadVoiceAlertBrown)
                                else -> listOf(ZadVoiceWaveEmerald, ZadVoiceWaveTealDark)
                            }
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable {
                        if (isLiveMode) {
                            if (isLiveConnected || liveState is LiveVoiceState.Connecting) {
                                liveSession.stop()
                            } else if (hasAudioPermission) {
                                liveSession.start { }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else if (isListeningState) {
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
                    imageVector = if ((isLiveMode && isLiveConnected) || (!isLiveMode && isListeningState)) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (isSpeakingState) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.voice_tap_to_interrupt_hint),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZadVoiceAlertAmber
                )
            }

            // Quick contextual prompt chips
            if (!isLiveMode) {
                Text(
                    text = "أو اختر سؤالاً جاهزاً:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZadVoiceTextSoft
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
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9999.dp))
                                .clickable {
                                    recognizedLiveText = chip
                                    viewModel.sendAiChatMessage(chip, voiceMode = true)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZadVoiceTextMint
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Animated Audio Wavebars — مجموعة من 26 بار نحيف مع حركة تموج ونبض طبيعي متناسق الارتفاعات
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
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val count = 26
        val barWidth = 3.2.dp.toPx()
        val spacing = 3.6.dp.toPx()
        val totalWidth = count * barWidth + (count - 1) * spacing
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        for (i in 0 until count) {
            // Symmetrical parabolic envelope (taller in center, tapering to sides)
            val normDist = (i - (count - 1) / 2f) / ((count - 1) / 2f)
            val envelope = (1f - normDist * normDist * 0.72f).coerceIn(0.25f, 1f)

            val wave = kotlin.math.sin((phase + i * 0.42f).toDouble()).toFloat()
            val ampBoost = (soundLevel * 1.8f).coerceIn(0f, 1f)
            val dynamicScale = (0.28f + 0.35f * wave + 0.95f * ampBoost).coerceIn(0.12f, 1f)

            val multiplier = if (isListening) {
                (envelope * dynamicScale).coerceIn(0.18f, 1f)
            } else {
                (0.12f + 0.06f * wave).coerceIn(0.08f, 0.20f)
            }

            val barHeight = (size.height * 0.88f * multiplier).coerceAtLeast(4.dp.toPx())
            val x = startX + i * (barWidth + spacing)
            val y = centerY - barHeight / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isListening) {
                        listOf(ZadVoiceWaveMint, ZadVoiceWaveEmerald, ZadVoiceWaveTealDark)
                    } else {
                        listOf(ZadVoiceWaveInactiveTop.copy(alpha = 0.5f), ZadVoiceWaveInactiveBottom.copy(alpha = 0.3f))
                    }
                ),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}


/**
 * Helper to match an AI reply to an active voice turn.
 */
fun voiceReplyForTurn(
    messages: List<com.example.ui.viewmodels.AiChatMessage>,
    activeTurnMessageId: String?
): com.example.ui.viewmodels.AiChatMessage? {
    if (activeTurnMessageId.isNullOrBlank()) return null
    return messages.lastOrNull { !it.isUser && it.replyToMessageId == activeTurnMessageId }
}
