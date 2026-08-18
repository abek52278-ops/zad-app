import re

with open('app/src/main/java/com/example/voice/ZadNaturalVoiceEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I will replace the entire file content cleanly
new_content = """package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 🎙️ محرك الصوت البشري الطبيعي الذكي (Zad Natural Voice Engine)
 * يستخدم ElevenLabs WebSockets Streaming API للاستجابة اللحظية (Real-time).
 * يشمل Fallback محلي باستخدام Android TTS في حالة عدم توفر الاتصال.
 */
class ZadNaturalVoiceEngine(private val context: Context) {

    private val TAG = "ZadNaturalVoiceEngine"

    enum class VoicePersona(
        val id: String,
        val elevenLabsVoiceId: String,
        val displayNameAr: String,
        val descriptionAr: String,
        val pitch: Float,
        val speechRate: Float,
        val isPetPersona: Boolean = false
    ) {
        SARAH_STUDIO_WARM(
            id = "sarah_warm",
            elevenLabsVoiceId = "EXAVITQu4vr4xnSDxMaL", // Sarah / Bella Voice ID
            displayNameAr = "👩 سارة (صوت استوديو دافئ)",
            descriptionAr = "صوت بشري طبيعي دافئ ومريح للأذن مع إيقاع متزن",
            pitch = 1.08f,
            speechRate = 1.02f
        ),
        KARIM_STUDIO_PRO(
            id = "karim_pro",
            elevenLabsVoiceId = "pNInz6obpgDQGcFmaJgB", // Adam / Karim Voice ID
            displayNameAr = "👨 كريم (صوت مهني وودود)",
            descriptionAr = "نبرة واثقة، واضحة ومباشرة مثل المستشار الشخصي",
            pitch = 0.95f,
            speechRate = 1.05f
        ),
        PET_MASCOT_CUTE(
            id = "pet_mascot",
            elevenLabsVoiceId = "MF3mGyEYCl7XYWbV9V6O", // Cute / Mascot Voice ID
            displayNameAr = "🐾 زاد الأليف (رفيق مرح وكيوت)",
            descriptionAr = "صوت مرح ولطيف مع أصوات وحركات الحيوان الأليف المحبوب",
            pitch = 1.35f,
            speechRate = 1.10f,
            isPetPersona = true
        )
    }

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // ElevenLabs WebSocket & AudioTrack for Real-time Streaming
    private var webSocket: WebSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var audioTrack: AudioTrack? = null
    private val SAMPLE_RATE = 24000

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentPersona = MutableStateFlow(VoicePersona.SARAH_STUDIO_WARM)
    val currentPersona: StateFlow<VoicePersona> = _currentPersona.asStateFlow()

    var onSpeechCompletedListener: (() -> Unit)? = null

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
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        finishSpeaking()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishSpeaking()
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finishSpeaking()
                    }
                })
            }
        }
    }

    fun setPersona(persona: VoicePersona) {
        _currentPersona.value = persona
        if (isTtsInitialized) {
            setupVoicePersona(persona)
        }
    }

    private fun setupVoicePersona(persona: VoicePersona) {
        val tts = textToSpeech ?: return
        try {
            val locale = Locale("ar")
            tts.language = locale
            tts.setPitch(persona.pitch)
            tts.setSpeechRate(persona.speechRate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed configuring TTS persona: ${e.message}")
        }
    }

    private fun initAudioTrack() {
        audioTrack?.release()
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    /**
     * نطق النص بأسلوب بشري طبيعي باستخدام ElevenLabs WebSockets (لو فشل بيستخدم TTS)
     */
    fun speakHumanLike(text: String, onDone: (() -> Unit)? = null) {
        onSpeechCompletedListener = onDone
        val persona = _currentPersona.value

        if (persona.isPetPersona) {
            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.MeowChirp, 0.40f)
        }

        val naturalHumanText = prepareNaturalSpeechText(text, persona)

        // إغلاق أي اتصال WebSocket أو AudioTrack شغال حالياً
        stop()

        _isSpeaking.value = true
        initAudioTrack()

        // استبدل هذا المفتاح بمفتاح ElevenLabs الحقيقي (يفضل جلبه من BuildConfig أو الخادم)
        val apiKey = com.example.data.MarketPrefs.getElevenLabsKey(context) ?: "YOUR_ELEVENLABS_API_KEY"
        val url = "wss://api.elevenlabs.io/v1/text-to-speech/${persona.elevenLabsVoiceId}/stream-input?model_id=eleven_multilingual_v2&output_format=pcm_24000"

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                try {
                    // 1. Initial settings message
                    val initialMessage = JSONObject().apply {
                        put("text", " ")
                        put("voice_settings", JSONObject().apply {
                            put("stability", 0.5)
                            put("similarity_boost", 0.8)
                        })
                    }
                    webSocket.send(initialMessage.toString())

                    // 2. Stream the actual text (We can also chunk the text here if needed)
                    val textMessage = JSONObject().apply {
                        put("text", naturalHumanText)
                    }
                    webSocket.send(textMessage.toString())

                    // 3. Signal end of input
                    val endMessage = JSONObject().apply {
                        put("text", "")
                    }
                    webSocket.send(endMessage.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending text to ElevenLabs WebSocket", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("audio")) {
                        val base64Audio = json.getString("audio")
                        if (base64Audio.isNotEmpty() && base64Audio != "null") {
                            val pcmData = Base64.decode(base64Audio, Base64.DEFAULT)
                            audioTrack?.write(pcmData, 0, pcmData.size)
                        }
                    }
                    if (json.has("isFinal") && json.getBoolean("isFinal")) {
                        finishSpeaking()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ElevenLabs message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ElevenLabs WebSocket Failure, falling back to local TTS", t)
                speakWithFallbackTTS(naturalHumanText)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finishSpeaking()
            }
        })
    }

    private fun speakWithFallbackTTS(text: String) {
        if (textToSpeech == null || !isTtsInitialized) {
            finishSpeaking()
            return
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        _isSpeaking.value = true
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "zad_fallback_tts")
    }

    private fun finishSpeaking() {
        _isSpeaking.value = false
        onSpeechCompletedListener?.invoke()
        onSpeechCompletedListener = null
    }

    fun stop() {
        _isSpeaking.value = false
        webSocket?.close(1000, "Stopped by user")
        webSocket = null
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (_: Exception) {}
        textToSpeech?.stop()
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        try {
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
        } catch (_: Exception) {}
    }

    private fun prepareNaturalSpeechText(rawText: String, persona: VoicePersona): String {
        var cleaned = rawText
            .replace(Regex("[#*`_~>\\[\\]()]"), " ")
            .replace(Regex("https?://\\\\S+"), "الرابط")
            .replace(Regex("[\\\\p{So}\\\\p{Cn}]"), " ")
            .replace(Regex("\\\\s+"), " ")
            .trim()

        cleaned = cleaned.replace("،", "، ... ")
            .replace(".", ". ... ")
            .replace("!", "! ... ")
            .replace("؟", "؟ ... ")

        if (persona.isPetPersona) {
            val cutePrefixes = listOf(
                "أهلاً يا صديقي! ",
                "من عيوني! ",
                "تمام حاضر! ",
                "يا سلام! "
            )
            if (!cleaned.startsWith("أهل") && !cleaned.startsWith("مرحب")) {
                cleaned = cutePrefixes.random() + cleaned
            }
        }
        return cleaned
    }
}
"""

with open('app/src/main/java/com/example/voice/ZadNaturalVoiceEngine.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
