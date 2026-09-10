package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.example.BuildConfig
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * ZadVoiceController — Clean audio pipeline for the AI Voice Agent.
 * 
 * Architecture:
 * 1. Microphone (16kHz mono PCM) → WebSocket → Gemini Live API (via zad-voice-live relay)
 * 2. Gemini response (audio chunks base64) → AudioTrack (24kHz mono PCM) → Speaker
 * 3. Full-duplex: Mic stays open while model speaks; Gemini VAD handles interruption
 * 4. Explicit mic pause during TTS playback to prevent echo/self-trigger loops
 * 
 * Key fixes over ZadLiveVoiceSession:
 * - No auto-restart loops on error
 * - Explicit mic mute during model speech
 * - Single WebSocket lifecycle (connect once, clean disconnect)
 * - Proper audio focus management
 * - Generation counter for clean interruption
 */
sealed class VoiceControllerState {
    object Idle : VoiceControllerState()
    object Connecting : VoiceControllerState()
    object Listening : VoiceControllerState()
    object ModelSpeaking : VoiceControllerState()
    object MicrophoneMuted : VoiceControllerState() // Mic explicitly muted while model speaks
    data class Error(val message: String) : VoiceControllerState()
}

object ZadVoiceController {
    private var appContext: Context? = null
    
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private val context: Context
        get() = appContext ?: error("ZadVoiceController.init(context) must be called first")

    private val tag = "ZadVoiceController"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Coroutine scope
    @Volatile private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() = synchronized(this) {
            _scope?.takeIf { it.coroutineContext[Job]?.isActive == true }
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { _scope = it }
        }

    // State
    private val _state = MutableStateFlow<VoiceControllerState>(VoiceControllerState.Idle)
    val state: StateFlow<VoiceControllerState> = _state.asStateFlow()

    // Mic level for visualization (0..1)
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    // WebSocket and audio
    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    private var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    
    // Session control
    private val sessionActive = AtomicBoolean(false)
    private val recordingActive = AtomicBoolean(false)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var micMutedByController = false // true when we explicitly mute for model speech
    
    // Audio constants
    private val inputSampleRate = 16_000
    private val outputSampleRate = 24_000
    private val generation = java.util.concurrent.atomic.AtomicLong(0L)

    private val audioManager get() =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun hasMicPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Start the voice session: connect WebSocket, then begin mic streaming.
     * onError called once with reason if startup fails.
     */
    fun start(onError: (String) -> Unit = {}) {
        if (!hasMicPermission()) {
            _state.value = VoiceControllerState.Error("محتاج إذن الميكروفون")
            onError("permission")
            return
        }
        if (sessionActive.getAndSet(true)) return
        
        _state.value = VoiceControllerState.Connecting
        generation.incrementAndGet()

        // 1. AudioManager setup for voice communication
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.w(tag, "AudioManager mode error: ${e.message}")
        }

        requestAudioFocus()

        // 2. Pre-create and start AudioTrack for immediate playback
        try {
            val track = buildPlaybackTrack()
            audioTrack = track
            track.play()
        } catch (e: Exception) {
            Log.w(tag, "Early AudioTrack play error: ${e.message}")
        }

        scope.launch { connect(onError) }
    }

    private suspend fun connect(onError: (String) -> Unit) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        val token = session?.accessToken?.takeIf { it.isNotBlank() }
            ?: SupabaseRepo.client.supabaseKey
            ?: BuildConfig.SUPABASE_ANON_KEY

        val cachedId = appContext?.let { com.example.data.CurrentUser.get(it) }
        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
            ?: cachedId?.takeIf { it.isNotBlank() }
            ?: "guest_voice_user"

        val baseUrl = if (BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.SUPABASE_URL.contains("your-project-ref")) {
            BuildConfig.SUPABASE_URL
        } else {
            "https://auuftqncrjsnyylolhbu.supabase.co"
        }
        val wsUrl = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/functions/v1/zad-voice-live"

        val apiKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { SupabaseRepo.client.supabaseKey }

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("apikey", apiKey)
            .addHeader("x-user-id", userId)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "zad-voice-live connected")
                startMicStreaming(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "zad-voice-live closed: $code $reason")
                teardown(toIdle = true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "zad-voice-live failure: ${t.message} (http=${response?.code})")
                val reason = when (response?.code) {
                    401 -> "محتاج تسجّل دخول تاني"
                    402 -> "خلص رصيدك من المكالمات الصوتية الحية"
                    503 -> "الخدمة الصوتية مش متاحة دلوقتي، جرّب تاني بعد شوية"
                    else -> "تعذّر الاتصال بالمساعد الصوتي"
                }
                mainHandler.post { _state.value = VoiceControllerState.Error(reason) }
                onError("ws_failure:${response?.code}")
                teardown(toIdle = false)
            }
        })
    }

    /**
     * Start microphone streaming. Runs once per session.
     * Full-duplex: mic stays open even during model speech.
     * We mute locally when model speaks (via muteMicrophone/unmuteMicrophone).
     */
    private fun startMicStreaming(connectedSocket: WebSocket) {
        if (webSocket !== connectedSocket || !sessionActive.get()) {
            Log.w(tag, "Cannot start mic streaming: connection not ready")
            return
        }
        if (recordingActive.getAndSet(true)) return
        
        scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                inputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(inputSampleRate / 2)
            
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    inputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                )
            } catch (e: SecurityException) {
                Log.w(tag, "AudioRecord init denied: ${e.message}")
                recordingActive.set(false)
                failSession("محتاج إذن الميكروفون")
                return@launch
            } catch (e: Exception) {
                Log.w(tag, "AudioRecord VOICE_COMMUNICATION failed: ${e.message}, trying MIC fallback")
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                try { record?.release() } catch (_: Exception) {}
                try {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        inputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                    )
                } catch (e: Exception) {
                    Log.w(tag, "AudioRecord MIC fallback failed: ${e.message}")
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(tag, "AudioRecord not initialized, state=${record?.state}")
                try { record?.release() } catch (_: Exception) {}
                recordingActive.set(false)
                failSession("تعذّر تجهيز الميكروفون")
                return@launch
            }
            audioRecord = record

            // Check if session still valid
            if (!sessionActive.get() || webSocket !== connectedSocket) {
                recordingActive.set(false)
                try { record.release() } catch (_: Exception) {}
                if (audioRecord === record) {
                    audioRecord = null
                    recordingActive.set(false)
                }
                return@launch
            }

            // Enable AEC and noise suppression
            val sessionId = record.audioSessionId
            if (sessionId != 0) {
                if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "AEC enable failed: ${e.message}")
                    }
                }
                if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "NoiseSuppressor enable failed: ${e.message}")
                    }
                }
            }

            val buffer = ByteArray(minBuf)
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.w(tag, "AudioRecord failed to start recording: ${record.recordingState}")
                    recordingActive.set(false)
                    failSession("تعذّر بدء التقاط الصوت")
                    return@launch
                }
                mainHandler.post {
                    if (sessionActive.get() && webSocket === connectedSocket) {
                        _state.value = VoiceControllerState.Listening
                    }
                }
                
                while (sessionActive.get() && recordingActive.get() && webSocket === connectedSocket) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        if (read < 0) kotlinx.coroutines.delay(10)
                        continue
                    }
                    // Skip sending if mic is muted by controller (model speaking)
                    if (!micMutedByController) {
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        updateMicLevel(chunk)
                        sendAudioChunk(connectedSocket, chunk)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "mic loop failed: ${e.message}")
            } finally {
                try { echoCanceler?.release() } catch (_: Exception) {}
                echoCanceler = null
                try { noiseSuppressor?.release() } catch (_: Exception) {}
                noiseSuppressor = null
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
                if (audioRecord === record) audioRecord = null
                recordingActive.set(false)
            }
        }
    }

    /** RMS level calculation for visualization */
    private fun updateMicLevel(pcm: ByteArray) {
        if (pcm.size < 2) return
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
            sumSquares += (sample * sample).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return
        val rms = sqrt(sumSquares / samples) / Short.MAX_VALUE
        _micLevel.value = (rms * 4.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** Send PCM chunk as base64 to Gemini Live via relay */
    private fun sendAudioChunk(ws: WebSocket, pcm: ByteArray) {
        if (!sessionActive.get() || webSocket !== ws) return
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        val frame = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=$inputSampleRate")
                        put("data", b64)
                    })
                })
            })
        }
        try {
            ws.send(frame.toString())
        } catch (e: Exception) {
            Log.w(tag, "send audio chunk failed: ${e.message}")
        }
    }

    /** Handle incoming frames from Gemini Live relay */
    private fun handleServerFrame(text: String) {
        try {
            val json = JSONObject(text)
            val serverContent = json.optJSONObject("serverContent") ?: return
            
            // Model interrupted (user spoke over it)
            if (serverContent.optBoolean("interrupted", false)) {
                stopModelPlaybackOnly()
                return
            }
            
            // Audio chunks from model
            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
                ?: serverContent.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val inline = part.optJSONObject("inlineData")
                    val data = inline?.optString("data", "") ?: ""
                    if (data.isNotEmpty()) {
                        val pcm = Base64.decode(data, Base64.DEFAULT)
                        playAudioChunk(pcm)
                    }
                }
            }
            
            // Turn complete - model finished speaking
            if (serverContent.optBoolean("turnComplete", false)) {
                mainHandler.post { 
                    if (sessionActive.get()) {
                        unmuteMicrophone()
                        _state.value = VoiceControllerState.Listening 
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "failed to parse server frame: ${e.message}")
        }
    }

    /** Play audio chunk from model */
    private fun playAudioChunk(pcm: ByteArray) {
        mainHandler.post { 
            if (sessionActive.get()) {
                muteMicrophone() // Explicitly mute mic while model speaks
                _state.value = VoiceControllerState.ModelSpeaking 
            }
        }
        var track = audioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            track = buildPlaybackTrack()
            audioTrack = track
            track.play()
        } else if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try { track.play() } catch (_: Exception) {}
        }
        try {
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.w(tag, "playback write failed: ${e.message}")
        }
    }

    /** Mute microphone locally (don't send audio to Gemini) */
    private fun muteMicrophone() {
        if (!micMutedByController) {
            micMutedByController = true
            mainHandler.post { 
                if (sessionActive.get() && _state.value == VoiceControllerState.Listening) {
                    _state.value = VoiceControllerState.MicrophoneMuted
                }
            }
        }
    }

    /** Unmute microphone (resume sending audio to Gemini) */
    private fun unmuteMicrophone() {
        if (micMutedByController) {
            micMutedByController = false
            mainHandler.post { 
                if (sessionActive.get() && _state.value == VoiceControllerState.MicrophoneMuted) {
                    _state.value = VoiceControllerState.Listening
                }
            }
        }
    }

    /** Model interrupted - flush playback buffer */
    private fun stopModelPlaybackOnly() {
        val track = audioTrack
        try {
            track?.pause()
            track?.flush()
            track?.play()
        } catch (_: Exception) {}
        unmuteMicrophone()
        mainHandler.post { if (sessionActive.get()) _state.value = VoiceControllerState.Listening }
    }

    private fun buildPlaybackTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            outputSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(outputSampleRate / 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = request
            return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        return audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    /** Stop the voice session completely */
    fun stop() {
        if (!sessionActive.getAndSet(false)) return
        recordingActive.set(false)
        
        // Close WebSocket
        try { webSocket?.close(1000, "client stop") } catch (_: Exception) {}
        webSocket = null
        
        teardown(toIdle = true)
    }

    private fun failSession(message: String) {
        sessionActive.set(false)
        try { webSocket?.close(1000, "mic init failed") } catch (_: Exception) {}
        webSocket = null
        teardown(toIdle = false)
        mainHandler.post { _state.value = VoiceControllerState.Error(message) }
    }

    private fun teardown(toIdle: Boolean) {
        recordingActive.set(false)
        micMutedByController = false
        
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        
        val track = audioTrack
        audioTrack = null
        try {
            track?.pause(); track?.flush(); track?.stop(); track?.release()
        } catch (_: Exception) {}
        
        abandonAudioFocus()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}
        
        _micLevel.value = 0f
        if (toIdle) {
            mainHandler.post {
                if (_state.value !is VoiceControllerState.Error) _state.value = VoiceControllerState.Idle
            }
        }
    }

    /** Full release - for app shutdown */
    fun release() {
        stop()
        synchronized(this) {
            _scope?.cancel()
            _scope = null
        }
    }
}

/**
 * Lifecycle-aware wrapper for ZadVoiceController.
 * Automatically stops voice session when lifecycle is destroyed.
 */
class VoiceControllerLifecycleWrapper : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        ZadVoiceController.stop()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        // Optionally stop when backgrounded, or keep running for background voice
        // ZadVoiceController.stop()
    }
}