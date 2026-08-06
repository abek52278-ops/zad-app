package com.example.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import com.example.R
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import com.example.data.CurrencyFormatter
import com.example.data.VoiceAgentResponse
import com.example.ui.viewmodels.ZadViewModel
import com.example.utils.AudioRecorderHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * مرحلة ٥ (docs/agent/PLAN_2026_08_06_rebuild.md) — حالات الـ Live Agent. الاسم/الـ API
 * العام لـ [ZadVoiceFab] فضل زي ما هو عمداً (نقطة استدعاء واحدة في HomeScreen) — اللي
 * اتغيّر جوّاها بس: دايرة + أيقونة مايك ثابتة استُبدلت بـ blob عضوي حي بعينين بتتغيّر
 * حسب الحالة.
 */
internal enum class ZadAgentState { IDLE, LISTENING, THINKING, SPEAKING }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZadVoiceFab(
    modifier: Modifier = Modifier,
    viewModel: ZadViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    // TTS بيشغّل الـ callbacks دي من thread داخلي بتاعه، مش الـ main thread — Compose
    // MutableState آمنة تتكتب من أي thread (نظام الـ Snapshot بيتولى التزامن)، فمفيش
    // داعي نلف كل مرة بـ Handler.post زي ما ممكن تتوقع من كود View القديم.
    var isSpeaking by remember { mutableStateOf(false) }
    // أي فعل بيلمس فلوس أو مخزون بيتوقف هنا لحد ما المستخدم يأكّده — الذكاء الاصطناعي ممكن
    // يفهم رقم أو كلمة غلط من الصوت، ومفيش داعي يتسجل أي حاجة تلقائي بدون مراجعة بشرية.
    var pendingVoiceAction by remember { mutableStateOf<VoiceAgentResponse?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val recorderHelper = remember { AudioRecorderHelper(context) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    // Init TTS
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = com.example.data.MarketPrefs.currentMarket.toLocale()
                // utteranceId لازم يكون مش null عشان onStart/onDone يتفعّلوا خالص — كل
                // نداءات .speak() تحت بقت بتمرر "zad_agent_speech" بدل null لنفس السبب.
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) { isSpeaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { isSpeaking = false }
                    override fun onError(utteranceId: String?, errorCode: Int) { isSpeaking = false }
                })
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val agentState = when {
        isRecording -> ZadAgentState.LISTENING
        isProcessing -> ZadAgentState.THINKING
        isSpeaking -> ZadAgentState.SPEAKING
        else -> ZadAgentState.IDLE
    }

    Box(
        modifier = modifier
            .size(76.dp)
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (!isProcessing) {
                            isRecording = true
                            recorderHelper.startRecording()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isRecording) {
                            isRecording = false
                            val file = recorderHelper.stopRecording()
                            if (file != null) {
                                isProcessing = true
                                coroutineScope.launch {
                                    val needsConfirmation = processAudioFile(file, viewModel, tts, context)
                                    if (needsConfirmation != null) pendingVoiceAction = needsConfirmation
                                    isProcessing = false
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        ZadLiveAgentBlob(state = agentState, size = 76.dp)
    }

    pendingVoiceAction?.let { pending ->
        VoiceConfirmationDialog(
            response = pending,
            onConfirm = {
                val data = pending.data
                if (data != null) {
                    when (pending.action) {
                        "add_expense" -> viewModel.addTransaction(amount = data.amount, title = data.title, isExpense = true, category = data.category)
                        "add_income" -> viewModel.addTransaction(amount = data.amount, title = data.title, isExpense = false, category = data.category)
                        "add_inventory" -> viewModel.addInventory(
                            com.example.data.ZadInventory(itemName = data.title, quantity = if (data.amount > 0) data.amount.toInt() else 1)
                        )
                    }
                    tts?.speak("تم", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                }
                pendingVoiceAction = null
            },
            onDismiss = {
                tts?.speak("تم الإلغاء", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                pendingVoiceAction = null
            }
        )
    }
}

/**
 * الـ Live Agent — blob عضوي متدرّج (نفس مسح ألوان ZadHeroGradient بتاع كارت الميزانية
 * الرئيسي، مش لوحة ألوان جديدة منفصلة عن هوية التطبيق) بحركة تنفس مستمرة + عينين
 * بيتغيّروا حسب [ZadAgentState]:
 * - IDLE: تنفس هادي بطيء + رمشة عين دورية كل ~3.5 ثانية
 * - LISTENING: تنفس أسرع وأعمق + حلقة صوت متوسّعة حوالين الـ blob + عيون واسعة ثابتة
 * - THINKING: دوران داخلي أسرع + عيون بتتحرك يمين شمال (بتفكر)
 * - SPEAKING: نبضات سريعة متزامنة مع فتح/قفل العين (بيتكلم)
 */
@Composable
internal fun ZadLiveAgentBlob(state: ZadAgentState, size: androidx.compose.ui.unit.Dp) {
    val infinite = rememberInfiniteTransition(label = "zadAgent")

    val breathDurationMs = when (state) {
        ZadAgentState.IDLE -> 2600
        ZadAgentState.LISTENING -> 700
        ZadAgentState.THINKING -> 1300
        ZadAgentState.SPEAKING -> 420
    }
    val breathFloor = when (state) {
        ZadAgentState.IDLE -> 0.95f
        ZadAgentState.LISTENING, ZadAgentState.SPEAKING -> 0.90f
        ZadAgentState.THINKING -> 0.93f
    }
    val breath by infinite.animateFloat(
        initialValue = breathFloor, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breathDurationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    val rotationDurationMs = if (state == ZadAgentState.THINKING) 2000 else 10000
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(rotationDurationMs, easing = LinearEasing)),
        label = "rotation"
    )

    // ثلاث فصوص متداخلة بتنجرف بشكل مستقل — إحساس "سائل" عضوي بدل دايرة صلبة ثابتة
    val lobeA by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lobeA")
    val lobeB by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(4100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lobeB")
    val lobeC by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lobeC")

    // حلقة صوت متوسّعة أثناء الاستماع بس
    val ringProgress by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "ring"
    )

    // رمشة العين — بس وقت IDLE: طويل مفتوحة، رمشة سريعة كل دورة
    val idleBlink by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3300
                0.15f at 3450
                1f at 3600
            }
        ),
        label = "idleBlink"
    )

    // نبضة العين وقت الكلام — فتح/قفل سريع بيحاكي إيقاع الكلام
    val speakPulse by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse),
        label = "speakPulse"
    )

    // نظرة يمين-شمال وقت التفكير
    val thinkGlance by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinkGlance"
    )

    val eyeOpenness = when (state) {
        ZadAgentState.IDLE -> idleBlink
        ZadAgentState.LISTENING -> 1.25f
        ZadAgentState.THINKING -> 0.7f
        ZadAgentState.SPEAKING -> speakPulse
    }
    val eyeGlanceOffsetPx = if (state == ZadAgentState.THINKING) thinkGlance else 0f

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = breath; scaleY = breath }
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseRadius = this.size.minDimension * 0.30f

        if (state == ZadAgentState.LISTENING) {
            drawCircle(
                color = com.example.ui.theme.primaryLight.copy(alpha = (1f - ringProgress) * 0.45f),
                radius = baseRadius * (1.15f + ringProgress * 0.9f),
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
            )
        }

        rotate(degrees = rotation, pivot = Offset(cx, cy)) {
            // درفت صغير عمداً — الفصوص تفضل متداخلة جداً، إحساس "سائل بيموج" على شكل
            // واحد موحّد، مش ثلاث دوائر منفصلة باينة (اللي كانت بتديله شكل فول سوداني)
            val drift = baseRadius * 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(com.example.ui.theme.primaryLight, com.example.ui.theme.primary),
                    center = Offset(cx, cy), radius = baseRadius * 1.5f
                ),
                radius = baseRadius,
                center = Offset(cx + lerp(-drift, drift, lobeA), cy + lerp(-drift * 0.7f, drift * 0.7f, lobeB))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF0B6B4E).copy(alpha = 0.75f), com.example.ui.theme.primaryDark.copy(alpha = 0.75f)),
                    center = Offset(cx, cy), radius = baseRadius * 1.4f
                ),
                radius = baseRadius * 0.98f,
                center = Offset(cx - lerp(-drift * 0.8f, drift * 0.8f, lobeB), cy + lerp(drift * 0.6f, -drift * 0.6f, lobeC))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(com.example.ui.theme.primaryLight.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx - baseRadius * 0.28f, cy - baseRadius * 0.28f), radius = baseRadius * 0.9f
                ),
                radius = baseRadius * 0.95f,
                center = Offset(cx + lerp(drift * 0.5f, -drift * 0.5f, lobeC), cy - lerp(drift * 0.5f, -drift * 0.5f, lobeA))
            )
        }

        // العينين بيترسموا برّه الـ rotate{} عشان يفضلوا واقفين ناحية المستخدم دايماً
        val eyeWidth = baseRadius * 0.22f
        val eyeMaxHeight = baseRadius * 0.34f
        val eyeHeight = (eyeMaxHeight * eyeOpenness).coerceIn(eyeMaxHeight * 0.08f, eyeMaxHeight * 1.3f)
        val eyeSpacing = baseRadius * 0.42f
        val eyeY = cy - baseRadius * 0.06f
        val glanceDx = eyeGlanceOffsetPx * baseRadius * 0.12f
        listOf(cx - eyeSpacing, cx + eyeSpacing).forEach { eyeX ->
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(eyeX - eyeWidth / 2f + glanceDx, eyeY - eyeHeight / 2f),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
            )
        }
    }
}

@Composable
private fun VoiceConfirmationDialog(
    response: VoiceAgentResponse,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val data = response.data
    val actionLabel = when (response.action) {
        "add_expense" -> "تسجيل مصروف"
        "add_income" -> "تسجيل دخل"
        "add_inventory" -> "إضافة للمخزون"
        else -> "تأكيد"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(actionLabel, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (data != null) {
                    if (response.action == "add_inventory") {
                        Text(data.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Text("${data.title} — ${CurrencyFormatter.format(context, data.amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الفئة: ${data.category}", color = Color.Gray, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("سمعت كده من الصوت — أكّد لو صح.", fontSize = 13.sp, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// Returns non-null only for actions that need user confirmation before committing
// (add_expense/add_income/add_inventory) — everything else (chat, check_budget) is
// handled immediately inside this function since it's read-only/no financial risk.
private suspend fun processAudioFile(
    file: File,
    viewModel: ZadViewModel,
    tts: TextToSpeech?,
    context: android.content.Context
): VoiceAgentResponse? {
    try {
        Log.d("VoiceFab", "Processing audio file: ${file.length()} bytes")
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val response = viewModel.processVoiceCommand(base64)
        if (response == null) {
            // processVoiceCommand returns null on an upstream/network failure (as opposed to a
            // real "couldn't understand" reply, which comes back as a normal non-null response
            // with its own spoken message) — without this, a failed request just resets the mic
            // icon with zero feedback.
            Log.e("VoiceFab", "processVoiceCommand returned null — upstream call failed")
            tts?.speak("تعذر الاتصال، حاول مرة أخرى.", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
            return null
        }

        return when (response.action) {
            // مبلغ حقيقي/مخزون حقيقي — يوقف للمراجعة، ما يتسجلش على لسان الذكاء الاصطناعي لوحده
            "add_expense", "add_income", "add_inventory" -> {
                if (response.data == null) {
                    tts?.speak("مسمعتش المبلغ كويس، حاول تاني.", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                    null
                } else {
                    response
                }
            }
            // قراءة فقط — تُقال فوراً برقم حقيقي من الرصيد الفعلي، مش من تخمين الذكاء الاصطناعي
            "check_budget" -> {
                val remaining = viewModel.remainingBalance.value
                val speech = remaining?.let { "باقي من ميزانيتك ${CurrencyFormatter.format(context, it)}" }
                    ?: context.getString(R.string.budget_unknown_speech)
                tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                null
            }
            // لا مبلغ مالي متضمن (التكلفة اتسجلت مرة واحدة وقت شراء الدواء، مش لكل جرعة) —
            // فمفيش داعي لتأكيد المستخدم، بيتنفذ فوراً زي check_budget بالظبط
            "log_pharmacy_dose" -> {
                val medName = response.data?.title
                if (medName.isNullOrBlank() || !viewModel.markPharmacyDoseTakenByName(medName)) {
                    tts?.speak("مش لاقي دواء بالاسم ده في قائمتك.", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                } else {
                    tts?.speak("تمام، سجلت إن حضرتك خدت $medName.", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                }
                null
            }
            else -> {
                tts?.speak(response.message, TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("VoiceFab", "Error processing audio: ${e.message}")
        tts?.speak("حدث خطأ، حاول مرة أخرى.", TextToSpeech.QUEUE_FLUSH, null, "zad_agent_speech")
        return null
    } finally {
        file.delete()
    }
}
