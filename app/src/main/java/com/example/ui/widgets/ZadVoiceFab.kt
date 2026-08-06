package com.example.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import com.example.R
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.CurrencyFormatter
import com.example.data.VoiceAgentResponse
import com.example.ui.viewmodels.ZadViewModel
import com.example.utils.AudioRecorderHelper
import kotlinx.coroutines.launch
import java.io.File

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
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Animation for pulse effect while recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(70.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
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
        if (isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Agent",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
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
                    tts?.speak("تم", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                pendingVoiceAction = null
            },
            onDismiss = {
                tts?.speak("تم الإلغاء", TextToSpeech.QUEUE_FLUSH, null, null)
                pendingVoiceAction = null
            }
        )
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
            tts?.speak("تعذر الاتصال، حاول مرة أخرى.", TextToSpeech.QUEUE_FLUSH, null, null)
            return null
        }

        return when (response.action) {
            // مبلغ حقيقي/مخزون حقيقي — يوقف للمراجعة، ما يتسجلش على لسان الذكاء الاصطناعي لوحده
            "add_expense", "add_income", "add_inventory" -> {
                if (response.data == null) {
                    tts?.speak("مسمعتش المبلغ كويس، حاول تاني.", TextToSpeech.QUEUE_FLUSH, null, null)
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
                tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
                null
            }
            // لا مبلغ مالي متضمن (التكلفة اتسجلت مرة واحدة وقت شراء الدواء، مش لكل جرعة) —
            // فمفيش داعي لتأكيد المستخدم، بيتنفذ فوراً زي check_budget بالظبط
            "log_pharmacy_dose" -> {
                val medName = response.data?.title
                if (medName.isNullOrBlank() || !viewModel.markPharmacyDoseTakenByName(medName)) {
                    tts?.speak("مش لاقي دواء بالاسم ده في قائمتك.", TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    tts?.speak("تمام، سجلت إن حضرتك خدت $medName.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                null
            }
            else -> {
                tts?.speak(response.message, TextToSpeech.QUEUE_FLUSH, null, null)
                null
            }
        }
    } catch (e: Exception) {
        Log.e("VoiceFab", "Error processing audio: ${e.message}")
        tts?.speak("حدث خطأ، حاول مرة أخرى.", TextToSpeech.QUEUE_FLUSH, null, null)
        return null
    } finally {
        file.delete()
    }
}
