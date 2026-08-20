package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.BuildConfig
import com.example.data.MarketPrefs
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams natural speech through the authenticated Zad Edge Function. Provider secrets stay
 * server-side; Android TTS remains an offline fallback when the session or provider is unavailable.
 */
class ZadNaturalVoiceEngine(private val context: Context) {

    private val tag = "ZadNaturalVoiceEngine"

    enum class VoicePersona(
        val id: String,
        val displayNameAr: String,
        val descriptionAr: String,
        val pitch: Float,
        val speechRate: Float,
        val isPetPersona: Boolean = false
    ) {
        SARAH_STUDIO_WARM(
            id = "sarah_warm",
            displayNameAr = "👩 سارة (صوت استوديو دافئ)",
            descriptionAr = "صوت بشري طبيعي دافئ ومريح للأذن مع إيقاع متزن",
            pitch = 1.08f,
            speechRate = 1.02f
        ),
        KARIM_STUDIO_PRO(
            id = "karim_pro",
            displayNameAr = "👨 كريم (صوت مهني وودود)",
            descriptionAr = "نبرة واثقة، واضحة ومباشرة مثل المستشار الشخصي",
            pitch = 0.95f,
            speechRate = 1.05f
        ),
        PET_MASCOT_CUTE(
            id = "pet_mascot",
            displayNameAr = "🐾 زاد الأليف (رفيق مرح وكيوت)",
            descriptionAr = "صوت مرح ولطيف مع أصوات وحركات الحيوان الأليف المحبوب",
            pitch = 1.35f,
            speechRate = 1.10f,
            isPetPersona = true
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0L)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    @Volatile private var activeCall: Call? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var completion: (() -> Unit)? = null

    private val sampleRate = 24_000
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentPersona = MutableStateFlow(VoicePersona.SARAH_STUDIO_WARM)
    val currentPersona: StateFlow<VoicePersona> = _currentPersona.asStateFlow()

    init {
        initFallbackTtsEngine()
    }

    private fun initFallbackTtsEngine() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                setupVoicePersona(_currentPersona.value)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utteranceGeneration(utteranceId)?.let { id ->
                            if (id == generation.get()) _isSpeaking.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceGeneration(utteranceId)?.let(::finishSpeaking)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceGeneration(utteranceId)?.let(::finishSpeaking)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceGeneration(utteranceId)?.let(::finishSpeaking)
                    }
                })
            }
        }
    }

    fun setPersona(persona: VoicePersona) {
        _currentPersona.value = persona
        if (isTtsInitialized) setupVoicePersona(persona)
    }

    private fun setupVoicePersona(persona: VoicePersona) {
        val tts = textToSpeech ?: return
        try {
            val locale = MarketPrefs.getMarket(context).toLocale()
            tts.language = if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                locale
            } else {
                java.util.Locale.forLanguageTag(locale.language)
            }
            tts.setPitch(persona.pitch)
            tts.setSpeechRate(persona.speechRate)
        } catch (error: Exception) {
            Log.w(tag, "Failed configuring fallback TTS: ${error.message}")
        }
    }

    fun speakHumanLike(text: String, onDone: (() -> Unit)? = null) {
        stopInternal()
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        val requestGeneration = generation.incrementAndGet()
        completion = onDone
        _isSpeaking.value = true

        val persona = _currentPersona.value
        if (persona.isPetPersona) {
            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.MeowChirp, 0.40f)
        }
        val naturalText = prepareNaturalSpeechText(text, persona)

        scope.launch {
            try {
                val session = SupabaseRepo.client.auth.currentSessionOrNull()
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id?.toString()
                if (session == null || userId.isNullOrBlank()) {
                    startFallback(requestGeneration, naturalText)
                    return@launch
                }

                val body = JSONObject().apply {
                    put("action", "voice_synthesize")
                    put("user_id", userId)
                    put("payload", JSONObject().apply {
                        put("text", naturalText)
                        put("persona", persona.id)
                        put("locale", MarketPrefs.getMarket(context).localeTag)
                    })
                }.toString()
                val request = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/functions/v1/zad-core-intelligence")
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "audio/pcm")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val call = httpClient.newCall(request)
                activeCall = call
                call.execute().use { response ->
                    if (requestGeneration != generation.get()) return@use
                    if (!response.isSuccessful) {
                        Log.w(tag, "Voice proxy unavailable: HTTP ${response.code}")
                        startFallback(requestGeneration, naturalText)
                        return@use
                    }
                    val responseBody = response.body
                    if (responseBody == null) {
                        startFallback(requestGeneration, naturalText)
                        return@use
                    }
                    streamPcm(requestGeneration, responseBody.byteStream())
                }
            } catch (error: Exception) {
                if (requestGeneration == generation.get()) {
                    Log.w(tag, "Voice stream failed, using local TTS: ${error.message}")
                    startFallback(requestGeneration, naturalText)
                }
            } finally {
                if (requestGeneration == generation.get()) activeCall = null
            }
        }
    }

    private fun streamPcm(requestGeneration: Long, input: java.io.InputStream) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        try {
            track.play()
            val buffer = ByteArray(8_192)
            while (requestGeneration == generation.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) track.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
            }
            if (requestGeneration == generation.get()) finishSpeaking(requestGeneration)
        } finally {
            if (audioTrack === track) audioTrack = null
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    private fun startFallback(requestGeneration: Long, text: String) {
        mainHandler.post {
            if (requestGeneration != generation.get()) return@post
            if (!isTtsInitialized || textToSpeech == null) {
                finishSpeaking(requestGeneration)
                return@post
            }
            setupVoicePersona(_currentPersona.value)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val result = textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "zad_fallback_tts_$requestGeneration"
            )
            if (result == TextToSpeech.ERROR) finishSpeaking(requestGeneration)
        }
    }

    private fun utteranceGeneration(utteranceId: String?): Long? =
        utteranceId?.removePrefix("zad_fallback_tts_")?.toLongOrNull()

    private fun finishSpeaking(requestGeneration: Long) {
        mainHandler.post {
            if (requestGeneration != generation.get()) return@post
            _isSpeaking.value = false
            val callback = completion
            completion = null
            callback?.invoke()
        }
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        generation.incrementAndGet()
        completion = null
        _isSpeaking.value = false
        activeCall?.cancel()
        activeCall = null
        val track = audioTrack
        audioTrack = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: Exception) {
        }
        textToSpeech?.stop()
    }

    fun release() {
        stopInternal()
        scope.cancel()
        try {
            audioTrack?.release()
            audioTrack = null
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
        } catch (_: Exception) {
        }
    }

    private fun prepareNaturalSpeechText(rawText: String, persona: VoicePersona): String {
        val linkWord = if (MarketPrefs.getMarket(context).localeTag.startsWith("tr")) "bağlantı" else "الرابط"
        var cleaned = rawText
            .replace(Regex("""https?://\S+"""), linkWord)
            .replace(Regex("""[#*`_~>\[\]()]"""), " ")
            .replace(Regex("""[\p{So}\p{Cn}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        cleaned = cleaned.replace("،", "، ... ")
            .replace(".", ". ... ")
            .replace("!", "! ... ")
            .replace("؟", "؟ ... ")

        if (persona.isPetPersona) {
            val prefixes = if (MarketPrefs.getMarket(context).localeTag.startsWith("tr")) {
                listOf("Merhaba! ", "Tabii! ", "Tamam! ")
            } else {
                listOf("أهلاً يا صديقي! ", "من عيوني! ", "تمام حاضر! ", "يا سلام! ")
            }
            if (!cleaned.startsWith("أهل") && !cleaned.startsWith("مرحب")) {
                cleaned = prefixes.random() + cleaned
            }
        }
        return cleaned.take(1_200)
    }
}
