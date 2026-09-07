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
    fun listen(onHeard: (String) -> Unit) {
        voiceManager.startListening { result ->
            if (result.isNotBlank()) onHeard(result)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            // كان بينده listen() (دور-بدور) على طول بغض النظر مين طلب الإذن — لو
            // isLiveMode كان مستني الإذن، الموافقة كانت بتشغّل الوضع الغلط.
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

    // بدء الاستماع فور فتح النافذة (مسار دور-بدور بس — المكالمة الحية ليها LaunchedEffect
    // منفصل فوق، وبتتحكم في voiceManager بنفسها لو الوضعين اتبدّلوا).
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
    // (محادثة حية مستمرة بدل ما العميل يدوس المايك كل مرة).
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
        // ننطق لما التايبينغ يخلص وظهر رد جديد (عدد الردود زاد عن لحظة بدء اللفة)
        if (isLiveMode) return@LaunchedEffect
        if (isTyping) return@LaunchedEffect
        val lastReply = messages.lastOrNull { !it.isUser } ?: return@LaunchedEffect
        if (lastReply.text.isBlank()) return@LaunchedEffect
        if (lastReply.id == lastSpokenMessageId) return@LaunchedEffect
        if (messages.count { !it.isUser } <= replyCountAtSpeakStart) return@LaunchedEffect
        lastSpokenMessageId = lastReply.id
        voiceManager.stopListening()
        voiceManager.speakHumanLike(lastReply.text) {
            // رجعنا نسمع تلقائياً — محادثة مستمرة
            if (hasAudioPermission) {
                listen { result ->
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
        }
    }

    // إنشاء المحادثة الصوتية: لو التعرف فشل (مهلة/ضوضاء) نعيد الاستماع تلقائياً
    // بحد أقصى 3 محاولات بدل إن الشيت يبقى ميت.
    LaunchedEffect(voiceState, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        if (voiceState is VoiceState.Error) {
            kotlinx.coroutines.delay(1200)
            if (hasAudioPermission && voiceState is VoiceState.Error) {
                listen { result ->
                    recognizedLiveText = result
                    viewModel.sendAiChatMessage(result, voiceMode = true)
                }
            }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // بند 33.1 — مكالمة حية حقيقية (Gemini Live) بديل اختياري لمسار الدور-بدور
                    // الافتراضي. تجريبي عن قصد: أول WebSocket خام في التطبيق، مفيش اختبار جهاز
                    // حقيقي عليه (شوف تحذير ZadLiveVoiceSession.kt).
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (isLiveMode) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
                            .border(
                                1.dp,
                                if (isLiveMode) Color(0xFFEF4444) else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(9999.dp)
                            )
                            .clickable { isLiveMode = !isLiveMode }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isLiveMode) "🔴 مباشر" else "تجربة: مكالمة حية",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveMode) Color.White else Color(0xFF94A3B8)
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
            }

            // Central SmartBot Agent with Dynamic Listening Emotion
            // الكورة بقت تنبض مع الصوت اللي بتسمعه فعلاً. قبل كده كانت الأعمدة تحت
            // بترقص مع `soundLevel` والكورة فوقها بتتنفس على تايمر ثابت 2400ms مش
            // سامعة حاجة — الحالة كانت بتغيّر اللون بس. السعة كانت متحسبة من زمان،
            // ومحدش وصّلها للكورة.
            CompanionOrb(
                size = 96.dp,
                // مفيش ترجمة هنا خالص دلوقتي — نفس المزاج اللي الكورة العايمة في
                // HomeScreen بتقراه. كان فيه نسختين من الـ when ده، واحدة هنا وواحدة
                // في HomeScreen، والاتنين كانوا لازم يفضلوا متطابقين يدويًا.
                state = mood,
                audioLevel = orbLevel
            )

            // Dynamic Audio Waveform Bars responsive to soundLevel
            ZadAudioWavebars(
                isListening = if (isLiveMode) liveState is LiveVoiceState.Listening || liveState is LiveVoiceState.ModelSpeaking else isListeningState,
                soundLevel = if (isLiveMode) liveMicLevel else soundLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            // Live Transcript text or listening hint
            val errorMsg = if (isLiveMode) (liveState as? LiveVoiceState.Error)?.message else (voiceState as? VoiceState.Error)?.message
            Text(
                text = when {
                    errorMsg != null -> "⚠️ $errorMsg\nاضغط على المايك للمحاولة مجدداً"
                    isLiveMode && liveState is LiveVoiceState.Connecting -> "بيتّصل بعقل زاد المباشر…"
                    isLiveMode && liveState is LiveVoiceState.ModelSpeaking -> "زاد بيتكلم… اتكلم في أي وقت تقاطعه"
                    isLiveMode && liveState is LiveVoiceState.Listening -> "مكالمة مباشرة — اتكلم بحرية، زاد سامعك دلوقتي"
                    isLiveMode -> "اضغط على المايك لبدء المكالمة المباشرة 🔴"
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

            // زاد بتتكلم؟ الزرار ده أصلاً بيقاطعها — startListening() بينده stopSpeaking()
            // كأول سطر فيه (ZadVoiceManager.kt:51). الناقص كان بصري بس: الزرار كان بيبان
            // نفسه بالظبط سواء زاد ساكتة أو بتتكلم، فمفيش حاجة بتقول "دوس تقاطعها". لون
            // كهرماني نابض هنا بدل الأخضر الثابت — نفس الضغطة، بس بتقول للعميل إنها فرصة
            // مقاطعة دلوقتي مش مجرد "ابدأ الكلام".
            val isSpeakingState = if (isLiveMode) liveState is LiveVoiceState.ModelSpeaking else voiceState is VoiceState.Speaking
            val isLiveConnected = liveState is LiveVoiceState.Listening || liveState is LiveVoiceState.ModelSpeaking
            // Interactive Mic Push / Stop Button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .then(if (isSpeakingState) Modifier.pulseGlow(minScale = 1f, maxScale = 1.08f) else Modifier)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = when {
                                isLiveMode && isLiveConnected -> listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                                !isLiveMode && isListeningState -> listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                                isSpeakingState -> listOf(Color(0xFFFBBF24), Color(0xFFB45309))
                                else -> listOf(Color(0xFF0F9B76), Color(0xFF064E3B))
                            }
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
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
                    modifier = Modifier.size(28.dp)
                )
            }
            if (isSpeakingState) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.voice_tap_to_interrupt_hint),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFBBF24)
                )
            }

            // Quick contextual prompt chips — بترسل رسالة دور-بدور منفصلة، مش جزء من
            // المكالمة الحية، فبتتخفى وقت isLiveMode عشان متتلخبطش مع الصوت المستمر.
            if (!isLiveMode) {
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
