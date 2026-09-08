package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
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

sealed class LiveVoiceState {
    object Idle : LiveVoiceState()
    object Connecting : LiveVoiceState()
    /** الميكروفون بيدفّق، مفيش صوت موديل بيتشغّل دلوقتي — مش بالضرورة "بيسمع كلام فعلي". */
    object Listening : LiveVoiceState()
    object ModelSpeaking : LiveVoiceState()
    data class Error(val message: String) : LiveVoiceState()
}

/**
 * جلسة صوت حية حقيقية — بند 33.1/33.2/33.3، عبر relay `zad-voice-live` لـGemini Live API
 * (BidiGenerateContent). أول WebSocket خام في التطبيق: كل مسارات الصوت التانية
 * (ZadNaturalVoiceEngine/ZadVoiceManager) HTTP دور-بدور (STT محلي → نداء شات → TTS)، مش
 * full-duplex حقيقي.
 *
 * الفرق الجوهري عن ZadNaturalVoiceEngine: هناك السيرفر بيرجّع WAV/PCM كامل الجملة عبر
 * REST، هنا العميل بيبعت صوت الميكروفون **مباشرة وباستمرار** لـGemini (عبر الـrelay) وبياخد
 * صوت الرد **قطعة قطعة** لحظة توليده — العميل بيتكلم مع Gemini الحقيقي مش مع نص بيتقرا.
 *
 * ⚠️ ملحوظة أمانة: شكل فريم `realtimeInput` (`mediaChunks`/`mimeType`) مبني على بروتوكول
 * Gemini Live API العام — `zad-voice-live/index.ts` (السيرفر) بيمرر أي فريم بيجي من
 * العميل زي ما هو لـGemini من غير ما يوثّق شكله في تعليقاته (شوف تعليقات index.ts:24-39:
 * موثّق فيها الـsetup/الموديل/responseModalities بس، مش شكل فريمات الصوت). معدش عندي
 * جهاز حقيقي أختبر بيه صوت فعلي، فده أعلى نقطة خطر في الملف ده — لو الصوت مش وصل
 * لجيميناي رغم اتصال الـWebSocket ناجح، هنا أول حاجة تتفحص.
 *
 * full-duplex حقيقي: الميكروفون فاضل شغال طول الوقت حتى وقت كلام الموديل — مفيش كتم
 * يدوي. Gemini نفسه عنده Voice Activity Detection سيرفر-سايد: لو العميل قاطع، بيرجع
 * `serverContent.interrupted=true` ونوقف تشغيل صوت الموديل بس (مش الميكروفون ولا الجلسة).
 */
object ZadLiveVoiceSession {

    private var appContext: Context? = null

    /**
     * لازم تتنادى مرة قبل أي استخدام — نفس نمط ZadVoiceManager.init، idempotent.
     *
     * بقى object مش class للسبب اللي خلّى ZadVoiceManager يبقى object قبله بالحرف:
     * كانت بتتعمل بـ `remember { ZadLiveVoiceSession(context) }` جوه
     * ZadVoiceBottomSheet، يعني نسخة جديدة كل فتحة وحالة مالهاش وجود والشيت مقفول،
     * ومحدش بره الشيت — ولا الـViewModel — يقدر يعرف حالة المكالمة الحقيقية.
     */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private val context: Context
        get() = appContext ?: error("ZadLiveVoiceSession.init(context) لازم تتنادى الأول")

    private val tag = "ZadLiveVoiceSession"

    // الـ scope بيترجّع لو اتلغى. كان `val` ثابت، وده كان بيبقى فخ قاتل بمجرد ما
    // الكلاس يبقى singleton: release() بتنادي scope.cancel()، وCoroutineScope متلغي
    // مايترجعش — فأول قفلة للشيت كانت هتقتل السينجلتون للأبد، وأي فتحة بعدها تبقى
    // صامتة من غير أي رسالة خطأ لأن الـ launch بيعدّي بلا أثر. ZadVoiceManager
    // مافيهوش الفخ ده لأنه مامسكش scope خاص بيه أصلاً، فالسابقة مكانتش كافية لوحدها.
    @Volatile private var _scope: CoroutineScope? = null
    // internal مش private عشان LiveVoiceSessionLifecycleTest يقدر يثبت إن الـscope
    // بيرجع شغال بعد release — الفخ ده مابيظهرش في أي بناء ناجح.
    internal val scope: CoroutineScope
        get() = synchronized(this) {
            _scope?.takeIf { it.isActive }
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { _scope = it }
        }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
    val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

    /** مستوى صوت الميكروفون اللحظي (0..1) — لنفس مكوّن الموجات اللي بيستخدمه المسار
     *  دور-بدور، عشان الموجات تفضل حية بردو في المكالمة المباشرة. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    // بلا read timeout — سوكيت دايم، مش نداء له نهاية. pingInterval يمنع أي بروكسي/gateway
    // وسيط يقفل الاتصال لعدم النشاط بين فريمات الصوت.
    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    private val sessionActive = AtomicBoolean(false)
    private val recordingActive = AtomicBoolean(false)
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioManager get() =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // معدلات Gemini Live API الثابتة (مش نفس مسار TTS REST بتاع zad-core-intelligence،
    // رغم إن الإخراج بالصدفة نفس رقم ZadNaturalVoiceEngine's 24kHz).
    private val inputSampleRate = 16_000
    private val outputSampleRate = 24_000

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** بيبدأ الجلسة: اتصال WebSocket بـzad-voice-live، وبمجرد ما يتفتح، تدفيق الميكروفون
     *  يبدأ على طول. onError بيتنده مرة واحدة بسبب مختصر (permission/no_session/http
     *  status) — الواجهة تعرض رسالة عربية مفهومة بدلها. */
    fun start(onError: (String) -> Unit = {}) {
        if (!hasMicPermission()) {
            _state.value = LiveVoiceState.Error("محتاج إذن الميكروفون")
            onError("permission")
            return
        }
        if (sessionActive.getAndSet(true)) return // جلسة شغالة أصلاً
        _state.value = LiveVoiceState.Connecting
        scope.launch { connect(onError) }
    }

    private suspend fun connect(onError: (String) -> Unit) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        if (false) {
            mainHandler.post { _state.value = LiveVoiceState.Error("محتاج تسجّل دخول الأول") }
            onError("no_session")
            sessionActive.set(false)
            return
        }
        val wsUrl = "https://auuftqncrjsnyylolhbu.supabase.co"
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/functions/v1/zad-voice-live"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "zad-voice-live connected")
                mainHandler.post { _state.value = LiveVoiceState.Listening }
                startMicStreaming()
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
                mainHandler.post { _state.value = LiveVoiceState.Error(reason) }
                onError("ws_failure:${response?.code}")
                teardown(toIdle = false)
            }
        })
    }

    /** full-duplex حقيقي: الميكروفون بيفضل شغال طول عمر الجلسة، حتى وقت كلام الموديل —
     *  VOICE_COMMUNICATION عشان echo cancellation الهاردوير (لو متاح) يمنع الميكروفون
     *  يسمع سماعة الجهاز نفسه كمقاطعة وهمية. */
    private fun startMicStreaming() {
        if (recordingActive.getAndSet(true)) return
        scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                inputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(inputSampleRate / 2)
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    inputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                )
            } catch (e: SecurityException) {
                Log.w(tag, "AudioRecord init denied: ${e.message}")
                recordingActive.set(false)
                failSession("محتاج إذن الميكروفون")
                return@launch
            } catch (e: IllegalArgumentException) {
                // كان مش متلقّط — لو الجهاز رفض المعاملات (نادر لـ16kHz mono PCM16، لكن
                // مش مستحيل)، الجلسة كانت تفضل "Listening" بصريًا رغم إن المايك ميت فعليًا.
                Log.w(tag, "AudioRecord bad params: ${e.message}")
                recordingActive.set(false)
                failSession("تعذّر تجهيز الميكروفون")
                return@launch
            }
            // نفس الملاحظة — كانت بتسجّل تحذير في الـlog وتسيب الحالة على Listening، فالشاشة
            // كانت تبان "شغالة" رغم إن المايك مقفول فعليًا من غير أي إشارة للعميل.
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(tag, "AudioRecord not initialized, state=${record.state}")
                try { record.release() } catch (_: Exception) {}
                recordingActive.set(false)
                failSession("تعذّر تجهيز الميكروفون")
                return@launch
            }
            audioRecord = record
            val buffer = ByteArray(minBuf)
            try {
                record.startRecording()
                while (sessionActive.get() && recordingActive.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    updateMicLevel(chunk)
                    sendAudioChunk(chunk)
                }
            } catch (e: Exception) {
                Log.w(tag, "mic loop failed: ${e.message}")
            } finally {
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
                if (audioRecord === record) audioRecord = null
            }
        }
    }

    /** RMS بسيط على عينات PCM16 موقّعة، متطبّع لـ0..1 — نفس فكرة ZadVoiceManager's
     *  onRmsChanged بس محسوبة يدوياً من البايتات الخام مش من SpeechRecognizer. */
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

    private fun sendAudioChunk(pcm: ByteArray) {
        val ws = webSocket ?: return
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

    /** كل رسالة من جيميناي (عبر الـrelay) JSON نصي — الصوت نفسه base64 جوه inlineData،
     *  مفيش binary frames خام أبداً (نفس افتراض السيرفر، index.ts:262-264). */
    private fun handleServerFrame(text: String) {
        try {
            val json = JSONObject(text)
            val serverContent = json.optJSONObject("serverContent") ?: return
            if (serverContent.optBoolean("interrupted", false)) {
                stopModelPlaybackOnly()
                return
            }
            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    val data = inline.optString("data", "")
                    if (data.isNotEmpty()) playAudioChunk(Base64.decode(data, Base64.DEFAULT))
                }
            }
            if (serverContent.optBoolean("turnComplete", false)) {
                mainHandler.post { if (sessionActive.get()) _state.value = LiveVoiceState.Listening }
            }
        } catch (e: Exception) {
            Log.w(tag, "failed to parse server frame: ${e.message}")
        }
    }

    private fun playAudioChunk(pcm: ByteArray) {
        mainHandler.post { if (sessionActive.get()) _state.value = LiveVoiceState.ModelSpeaking }
        var track = audioTrack
        if (track == null) {
            track = buildPlaybackTrack()
            audioTrack = track
            if (!requestAudioFocus()) {
                Log.w(tag, "Audio focus denied — playing at reduced priority")
            }
            track.play()
        }
        try {
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.w(tag, "playback write failed: ${e.message}")
        }
    }

    /** مقاطعة اكتشفها Gemini نفسه (VAD سيرفر-سايد) — نوقف صوت الموديل بس، الميكروفون
     *  فاضل شغال (full-duplex حقيقي، مش إعادة تشغيل جلسة). */
    private fun stopModelPlaybackOnly() {
        val track = audioTrack
        audioTrack = null
        try {
            track?.pause(); track?.flush(); track?.stop(); track?.release()
        } catch (_: Exception) {
        }
        abandonAudioFocus()
        mainHandler.post { if (sessionActive.get()) _state.value = LiveVoiceState.Listening }
    }

    private fun buildPlaybackTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            outputSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(outputSampleRate / 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = request
            return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        return audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
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

    fun stop() {
        if (!sessionActive.getAndSet(false)) return
        recordingActive.set(false)
        try { webSocket?.close(1000, "client stop") } catch (_: Exception) {}
        webSocket = null
        teardown(toIdle = true)
    }

    /** فشل المايك بعد ما الـWebSocket اتفتح فعلاً — الجلسة كلها بلا معنى من غيره، فبنقفلها
     *  بدل ما نسيبها "Listening" بصريًا وميت فعليًا. */
    private fun failSession(message: String) {
        sessionActive.set(false)
        try { webSocket?.close(1000, "mic init failed") } catch (_: Exception) {}
        webSocket = null
        teardown(toIdle = false)
        mainHandler.post { _state.value = LiveVoiceState.Error(message) }
    }

    private fun teardown(toIdle: Boolean) {
        recordingActive.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        val track = audioTrack
        audioTrack = null
        try {
            track?.pause(); track?.flush(); track?.stop(); track?.release()
        } catch (_: Exception) {
        }
        abandonAudioFocus()
        _micLevel.value = 0f
        if (toIdle) {
            mainHandler.post {
                if (_state.value !is LiveVoiceState.Error) _state.value = LiveVoiceState.Idle
            }
        }
    }

    /**
     * تحرير نهائي — للخروج من التطبيق، مش لقفل الشيت.
     *
     * قفل الشيت بينادي stop()، وهي بتحرر كل مورد فعلاً: audioRecord.release()،
     * audioTrack.release()، abandonAudioFocus()، وقفل الـWebSocket. الفرق الوحيد
     * هنا هو إلغاء scope خامل مش ماسك ولا مورد. يعني نقل الشيت من release لـ stop
     * مابيسيبش المايك مفتوح ولا الاتصال شغال.
     */
    fun release() {
        stop()
        synchronized(this) {
            _scope?.cancel()
            _scope = null
        }
    }
}
