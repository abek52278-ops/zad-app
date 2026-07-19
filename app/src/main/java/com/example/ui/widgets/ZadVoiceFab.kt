package com.example.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.viewmodels.ZadViewModel
import com.example.utils.AudioRecorderHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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
                tts?.language = Locale("ar") // Set Arabic
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
                                    processAudioFile(file, viewModel, tts)
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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Agent",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private suspend fun processAudioFile(
    file: File,
    viewModel: ZadViewModel,
    tts: TextToSpeech?
) {
    try {
        Log.d("VoiceFab", "Processing audio file: ${file.length()} bytes")
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        
        val response = viewModel.processVoiceCommand(base64)
        if (response != null) {
            // Speak the response
            tts?.speak(response.message, TextToSpeech.QUEUE_FLUSH, null, null)

            // Execute the intent
            when (response.action) {
                "add_expense" -> {
                    response.data?.let { data ->
                        viewModel.addTransaction(
                            amount = data.amount,
                            title = data.title,
                            isExpense = true,
                            category = data.category
                        )
                    }
                }
                "add_chore" -> {
                    // Gamification feature
                }
            }
        }
    } catch (e: Exception) {
        Log.e("VoiceFab", "Error processing audio: ${e.message}")
    } finally {
        file.delete()
    }
}
