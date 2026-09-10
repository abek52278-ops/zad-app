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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.flow.StateFlow
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
import com.example.voice.ZadVoiceController
import com.example.voice.ZadNaturalVoiceEngine
import com.example.voice.VoiceControllerState
import com.example.voice.ZadVoiceManager
import com.example.ui.theme.*
import com.example.ui.viewmodels.AiChatMessage
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * New Zad Voice Bottom Sheet using ZadVoicePet and ZadVoiceController.
 * Clean audio pipeline: no echo loops, proper mic mute during model speech,
 * Gemini Live API via WebSocket relay with female Arabic voice (Aoede).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ZadVoiceBottomSheet(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    onDismiss: () -> Unit,
    initialLiveMode: Boolean = false
) {
    val context = LocalContext.current
    
    // Initialize controllers
    val voiceManager = remember { ZadVoiceManager.apply { init(context) } }
    val voiceController = remember { ZadVoiceController.apply { init(context) } }
    
    // State
    val voiceState by voiceManager.voiceState.collectAsState()
    val isListeningState by voiceManager.isListening.collectAsState()
    val soundLevel by voiceManager.soundLevel.collectAsState()
    val controllerState by voiceController.state.collectAsState()
    val controllerMicLevel by voiceController.micLevel.collectAsState()
    val companionMood by viewModel.companionMood.collectAsState()
    val currentPersona by voiceManager.currentPersona.collectAsState()
    
    // Pet audio level (smoothed, read in draw scope)
    val petAudioLevel = rememberPetAudioLevel(
        if (initialLiveMode) voiceController.micLevel else voiceManager.soundLevel
    )
    
    var recognizedLiveText by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var activeVoiceTurnId by remember { mutableStateOf<String?>(null) }
    var isLiveMode by remember { mutableStateOf(initialLiveMode) }
    var showSettings by remember { mutableStateOf(false) }
    
    // Submit voice turn to AI
    fun submitVoiceTurn(result: String) {
        if (result.isBlank()) return
        retryAttempt = 0
        recognizedLiveText = result
        if (isLiveMode) {
            voiceController.stop() // Stop live mode, fall back to turn-based
            isLiveMode = false
        }
        voiceManager.markThinking()
        activeVoiceTurnId = viewModel.sendAiChatMessage(result, voiceMode = true)
    }
    
    // Start listening (turn-based mode)
    fun listen(silent: Boolean = false, resetRetry: Boolean = false) {
        if (resetRetry) retryAttempt = 0
        voiceManager.startListening(silent = silent) { result -> submitVoiceTurn(result) }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            if (isLiveMode) {
                voiceController.start()
            } else {
                listen(resetRetry = true)
            }
        }
    }
    
    // Handle live mode toggle
    LaunchedEffect(isLiveMode) {
        if (isLiveMode) {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            kotlinx.coroutines.delay(200)
            voiceController.start { /* state updates automatically */ }
        } else {
            voiceController.stop()
        }
    }
    
    // Auto-start listening on permission grant
    LaunchedEffect(hasAudioPermission, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        if (hasAudioPermission) {
            listen(resetRetry = true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // Auto-speak AI replies (turn-based mode)
    val isTyping by viewModel.isAiTyping.collectAsState()
    val messages by viewModel.aiChatMessages.collectAsState()
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isTyping, messages, isLiveMode, activeVoiceTurnId) {
        if (isLiveMode) return@LaunchedEffect
        if (isTyping) return@LaunchedEffect
        val lastReply = voiceReplyForTurn(messages, activeVoiceTurnId) ?: return@LaunchedEffect
        if (lastReply.text.isBlank()) return@LaunchedEffect
        if (lastReply.id == lastSpokenMessageId) return@LaunchedEffect
        lastSpokenMessageId = lastReply.id
        activeVoiceTurnId = null
        voiceManager.stopListening()
        val resumeListening = {
            if (hasAudioPermission) {
                listen(resetRetry = true)
            }
        }
        voiceManager.speakHumanLike(lastReply.text, onDone = resumeListening, onFailed = resumeListening)
    }
    
    // Error retry (once)
    LaunchedEffect(voiceState, isLiveMode) {
        if (isLiveMode) return@LaunchedEffect
        val error = voiceState as? VoiceState.Error ?: return@LaunchedEffect
        if (retryAttempt == 0) {
            retryAttempt = 1
            kotlinx.coroutines.delay(1200)
            if (hasAudioPermission && voiceManager.voiceState.value == error) {
                listen(silent = true)
            }
        }
    }
    
    // Controller error handling
    LaunchedEffect(controllerState, isLiveMode) {
        if (!isLiveMode) return@LaunchedEffect
        val error = controllerState as? VoiceControllerState.Error ?: return@LaunchedEffect
        // Controller errors are final - don't auto-retry to avoid loops
    }
    
    // Pause HeyZad wake service while sheet is open
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.example.voice.HeyZadWakeService.pause(context)
        onDispose {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            voiceController.stop()
            com.example.voice.HeyZadWakeService.resume(context)
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
            // Header: Status + Live Mode Toggle + Settings + Close
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
                    val indicatorColor = when {
                        isLiveMode && controllerState is VoiceControllerState.Listening -> ZadVoiceWaveMint
                        isLiveMode && controllerState is VoiceControllerState.ModelSpeaking -> ZadVoiceAlertAmber
                        isLiveMode && controllerState is VoiceControllerState.Connecting -> ZadVoiceCyan
                        !isLiveMode && isListeningState -> ZadVoiceCyan
                        else -> ZadVoiceTextSoft
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                            .then(if (isLiveMode && (controllerState is VoiceControllerState.Listening || controllerState is VoiceControllerState.ModelSpeaking) || (!isLiveMode && isListeningState)) 
                                Modifier.pulseGlow(minScale = 0.85f, maxScale = 1.35f) 
                                else Modifier)
                    )
                    Text(
                        text = when {
                            isLiveMode && controllerState is VoiceControllerState.Connecting -> "جاري الاتصال..."
                            isLiveMode && controllerState is VoiceControllerState.ModelSpeaking -> "المساعد يتكلم..."
                            isLiveMode && controllerState is VoiceControllerState.Listening -> "عقل زاد • مباشر"
                            isLiveMode && controllerState is VoiceControllerState.MicrophoneMuted -> "الميكروفون مكتوم..."
                            !isLiveMode && isListeningState -> "مساعد زاد الصوتي"
                            voiceState is VoiceState.Thinking -> "جاري التفكير..."
                            voiceState is VoiceState.Speaking -> "يتحدث..."
                            else -> "مساعد زاد الصوتي"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Live Mode Toggle
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
                    
                    // Settings
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        M3Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "إعدادات الصوت",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    
                    // Close
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        M3Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            
            // Settings Panel
            if (showSettings) {
                SettingsPanel(
                    voiceManager = voiceManager,
                    voiceController = voiceController,
                    currentPersonaFlow = voiceManager.currentPersona,
                    onDismiss = { showSettings = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Central ZadVoicePet - the main visual character
            ZadVoicePet(
                state = when {
                    isLiveMode -> when (controllerState) {
                        is VoiceControllerState.Listening -> VoicePetState.Listening
                        is VoiceControllerState.ModelSpeaking -> VoicePetState.Speaking
                        is VoiceControllerState.MicrophoneMuted -> VoicePetState.Listening
                        is VoiceControllerState.Connecting -> VoicePetState.Thinking
                        is VoiceControllerState.Error -> VoicePetState.Sleeping
                        else -> VoicePetState.Idle
                    }
                    !isLiveMode -> when (voiceState) {
                        is VoiceState.Listening -> VoicePetState.Listening
                        is VoiceState.Thinking -> VoicePetState.Thinking
                        is VoiceState.Speaking -> VoicePetState.Speaking
                        is VoiceState.Recognized -> VoicePetState.Speaking // Happy state
                        is VoiceState.Idle -> VoicePetState.Idle
                        is VoiceState.Error -> VoicePetState.Sleeping
                    }
                    else -> VoicePetState.Idle
                },
                size = 120.dp,
                audioLevel = petAudioLevel,
                onClick = {
                    // Tap triggers happy reaction
                    ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.5f)
                }
            )
            
            // Dynamic Audio Waveform Bars
            ZadAudioWavebars(
                isListening = when {
                    isLiveMode -> controllerState is VoiceControllerState.Listening || controllerState is VoiceControllerState.ModelSpeaking
                    else -> isListeningState
                },
                soundLevel = if (isLiveMode) controllerMicLevel else soundLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
            
            // Transcript / Status Text
            val controllerError = (controllerState as? VoiceControllerState.Error)?.message
            val voiceError = (voiceState as? VoiceState.Error)?.message
            val errorMsg = if (isLiveMode) controllerError else voiceError
            
            Text(
                text = when {
                    errorMsg != null -> "⚠️ $errorMsg\nاضغط على المايك للمحاولة مجدداً"
                    isLiveMode && controllerState is VoiceControllerState.Connecting -> "بيتّصل بعقل زاد المباشر…"
                    isLiveMode && controllerState is VoiceControllerState.ModelSpeaking -> "زاد بيتكلم… اتكلم في أي وقت تقاطعه"
                    isLiveMode && controllerState is VoiceControllerState.Listening -> "مكالمة مباشرة — اتكلم بحرية، زاد سامعك دلوقتي"
                    isLiveMode && controllerState is VoiceControllerState.MicrophoneMuted -> "الميكروفون مكتوم مؤقتاً… جاري تشغيل رد زاد"
                    isLiveMode -> "اضغط على المايك لبدء المكالمة المباشرة 🎙️"
                    recognizedLiveText.isNotBlank() -> recognizedLiveText
                    isListeningState -> "أنا أسمعك الآن… تكلّم مع زاد بحرية وسأجيبك فوراً"
                    voiceState is VoiceState.Thinking -> "عقل زاد يفكّر بالرد…"
                    voiceState is VoiceState.Speaking -> "زاد يتكلم…"
                    else -> "اضغط على المايك لبدء التحدث 🎙️"
                },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (recognizedLiveText.isNotBlank()) Color.White else ZadVoiceWaveMint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            
            val isSpeakingState = when {
                isLiveMode -> controllerState is VoiceControllerState.ModelSpeaking
                else -> voiceState is VoiceState.Speaking
            }
            val isLiveConnected = controllerState is VoiceControllerState.Listening || controllerState is VoiceControllerState.ModelSpeaking
            
            // Central Mic Button
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
                            if (isLiveConnected || controllerState is VoiceControllerState.Connecting) {
                                voiceController.stop()
                            } else if (hasAudioPermission) {
                                voiceController.start()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else if (isListeningState) {
                            voiceManager.stopListening()
                        } else {
                            if (hasAudioPermission) {
                                listen(resetRetry = true)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                M3Icon(
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
            
            // Quick prompt chips (turn-based mode only)
            if (!isLiveMode) {
                Spacer(modifier = Modifier.height(12.dp))
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
                                .clickable { submitVoiceTurn(chip) }
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
 * Settings panel for voice preferences
 */
@Composable
private fun SettingsPanel(
    voiceManager: com.example.voice.ZadVoiceManager,
    voiceController: ZadVoiceController,
    currentPersonaFlow: StateFlow<ZadNaturalVoiceEngine.VoicePersona>,
    onDismiss: () -> Unit
) {
    val currentPersona = currentPersonaFlow.collectAsState()
    val personas = ZadNaturalVoiceEngine.VoicePersona.values()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إعدادات الصوت", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = onDismiss) {
                M3Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Persona selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("شخصية الصوت", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZadVoiceTextSoft)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                personas.forEach { persona ->
                    val isSelected = currentPersona.value.id == persona.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ZadVoiceWaveEmerald.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (isSelected) ZadVoiceWaveEmerald.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .clickable { voiceManager.setVoicePersona(persona) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) ZadVoiceWaveMint else Color.White.copy(alpha = 0.3f))
                            )
                            Text(
                                persona.displayNameAr,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ZadVoiceWaveMint else ZadVoiceTextSoft
                            )
                        }
                        if (isSelected) {
                            M3Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = ZadVoiceWaveMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Divider(color = Color.White.copy(alpha = 0.1f))
      
        // Live mode info
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(ZadVoiceCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                M3Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = ZadVoiceCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("وضع المكالمة المباشرة", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZadVoiceTextSoft)
                Text(
                    "مكالمة صوتية مباشرة مع Gemini Live API — الميكروفون مفتوح باستمرار، وتستطيع مقاطعته في أي وقت.",
                    fontSize = 11.sp,
                    color = ZadVoiceTextSoft.copy(alpha = 0.8f)
                )
            }
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
            
            val wave = sin((phase + i * 0.42f).toDouble()).toFloat()
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
    messages: List<AiChatMessage>,
    activeTurnMessageId: String?
): AiChatMessage? {
    if (activeTurnMessageId.isNullOrBlank()) return null
    return messages.lastOrNull { !it.isUser && it.replyToMessageId == activeTurnMessageId }
}