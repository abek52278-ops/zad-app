package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Recognized(val text: String) : VoiceState()
    object Thinking : VoiceState()
    data class Speaking(val text: String) : VoiceState()
    /**
     * [transient] = فشل عابر (ماسمعش حاجة / خلص الوقت) واللفة تقدر تستأنف صامت.
     * غير العابر (صلاحية، مايك مشغول، شبكة) لازم يوقف ويتعرض — الاستئناف عليه
     * بيعمل لوب صامت المستخدم مش فاهم ليه بيسمع نغمة كل شوية.
     */
    data class Error(val message: String, val transient: Boolean = false) : VoiceState()
}

/**
 * بوابة الصوت الموحدة لزاد.
 *
 * إدخال: SpeechRecognizer (جوجل STT) — متعدد اللهجات.
 * إخراج: ZadNaturalVoiceEngine فقط (ElevenLabs صوت بشري عبر سيرفرنا) — مفيش TTS آلي.
 * Wake word: "hey zad / يا زاد / hey زاد" بيبدأ جلسة استماع تلقائيًا (see onWakeWord).
 *
 * object مش class — كانت بتتعمل بـ `remember { ZadVoiceManager(context) }` في كل شيت/شاشة
 * لوحدها (ZadVoiceBottomSheet، ZadIntelligenceScreen)، يعني كل واحدة معاها voiceState منفصلة
 * تمامًا عن التانية، فمفيش حد بره الشيت يقدر يعرف حالة الصوت الحقيقية. singleton واحد على
 * نمط NetworkMonitor/SupabaseRepo الموجود فعلاً (init() مرة واحدة idempotent، مش Hilt —
 * المشروع مقرر ما يستخدمش DI framework) بيخلي أي مكان في التطبيق (زي المسكوت في HomeScreen)
 * يقدر يقرا نفس الـvoiceState الحقيقي.
 */
object ZadVoiceManager {
    private const val TAG = "ZadVoiceManager"

    private var appContext: Context? = null
    private var initialized = false
    private var engine: ZadNaturalVoiceEngine? = null

    private val _humanVoiceAvailable = MutableStateFlow(true)
    val humanVoiceAvailable: StateFlow<Boolean> = _humanVoiceAvailable.asStateFlow()

    /** لازم تتنادى مرة قبل أي استخدام — MainActivity.onCreate بينادّيها بأمان. */
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        val voiceEngine = ZadNaturalVoiceEngine(app)
        engine = voiceEngine
        initialized = true

        try {
            val prefs = app.getSharedPreferences("zad_voice_persona", Context.MODE_PRIVATE)
            val savedId = prefs.getString("persona_id", null)
            savedId?.let { id ->
                ZadNaturalVoiceEngine.VoicePersona.values()
                    .firstOrNull { it.id == id }
                    ?.let { voiceEngine.setPersona(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading voice persona prefs", e)
        }
    }

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()
    
    val currentPersona: StateFlow<ZadNaturalVoiceEngine.VoicePersona>
        get() = engine?.currentPersona ?: MutableStateFlow(ZadNaturalVoiceEngine.VoicePersona.SARAH_STUDIO_WARM).asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private var pendingListeningRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeningGeneration = AtomicLong(0L)

    fun startListening(onResult: (String) -> Unit) = startListening(silent = false, onResult = onResult)

    fun startListening(silent: Boolean = false, onResult: (String) -> Unit) {
        stopSpeaking()
        val requestGeneration = listeningGeneration.incrementAndGet()
        val context = appContext ?: run {
            _voiceState.value = VoiceState.Error("خدمة الصوت لم تبدأ بعد")
            return
        }
        mainHandler.post {
            if (requestGeneration != listeningGeneration.get()) return@post
            pendingListeningRunnable?.let { mainHandler.removeCallbacks(it) }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _voiceState.value = VoiceState.Error(context.getString(R.string.voice_error_unavailable))
                return@post
            }

            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null

            val runnable = Runnable {
                if (requestGeneration == listeningGeneration.get()) {
                    startListeningInternal(context, silent, onResult, requestGeneration)
                }
            }
            pendingListeningRunnable = runnable
            mainHandler.postDelayed(runnable, 200)
        }
    }

    private fun startListeningInternal(
        context: Context,
        silent: Boolean,
        onResult: (String) -> Unit,
        requestGeneration: Long
    ) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val marketLocale = com.example.data.MarketPrefs.getMarket(context).toLocale()
                val localeTag = marketLocale.toLanguageTag()
                val additionalLanguages = buildList {
                    add(localeTag)
                    if (marketLocale.language == "ar") add("ar")
                    add("en-US")
                    add("tr-TR")
                }.distinct().toTypedArray()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", additionalLanguages)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (requestGeneration != listeningGeneration.get()) return
                        _voiceState.value = VoiceState.Listening
                        _isListening.value = true
                        if (!silent) {
                            com.example.ui.components.ZadChime.play(com.example.ui.components.ZadChime.Tone.Tap)
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        if (requestGeneration != listeningGeneration.get()) return
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (requestGeneration != listeningGeneration.get()) return
                        _soundLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (requestGeneration != listeningGeneration.get()) return
                        _voiceState.value = VoiceState.Thinking
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        if (requestGeneration != listeningGeneration.get()) return
                        _isListening.value = false
                        listeningGeneration.incrementAndGet()
                        try { speechRecognizer?.destroy() } catch (_: Exception) {}
                        speechRecognizer = null
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_error_permission)
                            SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_error_audio)
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_error_network)
                            SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.voice_error_no_match)
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_error_timeout)
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.voice_error_busy)
                            else -> context.getString(R.string.voice_error_generic)
                        }
                        Log.w(TAG, "SpeechRecognizer error: $error ($msg)")
                        val transient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        // أخطاء التعرف حالة واجهة، مش كلام المستخدم — عمرها ما تتبعت للوكيل.
                        _voiceState.value = VoiceState.Error(msg, transient)
                    }

                    override fun onResults(results: Bundle?) {
                        if (requestGeneration != listeningGeneration.get()) return
                        _voiceState.value = VoiceState.Idle
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        listeningGeneration.incrementAndGet()
                        try { speechRecognizer?.destroy() } catch (_: Exception) {}
                        speechRecognizer = null
                        if (text.isNotEmpty()) {
                            _voiceState.value = VoiceState.Recognized(text)
                            com.example.ui.components.ZadChime.play(com.example.ui.components.ZadChime.Tone.Success)
                            onResult(text)
                        } else {
                            _voiceState.value = VoiceState.Error(context.getString(R.string.voice_error_no_match))
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        // المعاينة الجزئية ليست طلباً صالحاً للإرسال. إبقاء حالة
                        // Listening يمنع نجاحاً كاذباً ثم إعادة محاولة دائرية.
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                _voiceState.value = VoiceState.Listening
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer error: ${e.message}")
                _voiceState.value = VoiceState.Error(context.getString(R.string.voice_error_microphone))
            }
    }

    fun stopListening() {
        listeningGeneration.incrementAndGet()
        pendingListeningRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingListeningRunnable = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening error: ${e.message}")
        }
        speechRecognizer = null
        _isListening.value = false
        _soundLevel.value = 0f
    }

    fun markThinking() {
        _voiceState.value = VoiceState.Thinking
        _isListening.value = false
        _soundLevel.value = 0f
    }

    fun setVoicePersona(persona: ZadNaturalVoiceEngine.VoicePersona) {
        engine?.setPersona(persona)
        appContext?.getSharedPreferences("zad_voice_persona", Context.MODE_PRIVATE)
            ?.edit()?.putString("persona_id", persona.id)?.apply()
    }

    fun getCurrentPersona(): ZadNaturalVoiceEngine.VoicePersona =
        engine?.currentPersona?.value ?: ZadNaturalVoiceEngine.VoicePersona.SARAH_STUDIO_WARM

    /** نطق بصوت بشري مع fallback ذكي. */
    fun speakHumanLike(text: String, onDone: () -> Unit = {}, onFailed: () -> Unit = {}) {
        if (text.isBlank()) {
            onDone()
            return
        }

        // المقايضة العكسية لـ startListening(): وقت الكلام لازم المايك يتقفل صراحةً،
        // وإلا صيد صوت الـTTS نفسه ويتحوّل لـSTT (echo/self-trigger loop). التماثل ده
        // هو اللي يخلي الكلام والاستماع حالتين متعاقبتين مش متزامنتين على طول عمر الشيت.
        stopListening()

        _voiceState.value = VoiceState.Speaking(text)
        _isSpeaking.value = true
        val voiceEngine = engine
        if (voiceEngine == null) {
            _isSpeaking.value = false
            _voiceState.value = VoiceState.Idle
            onFailed()
            return
        }

        voiceEngine.speakHumanLike(
            text,
            onDone = {
                _isSpeaking.value = false
                _voiceState.value = VoiceState.Idle
                onDone()
            },
            onFailed = {
                _isSpeaking.value = false
                _voiceState.value = VoiceState.Idle
                onFailed()
            }
        )
    }

    /** توافق مع الاستدعاءات القديمة — نفس speakHumanLike. */
    fun speakFemaleVoice(text: String, onDone: () -> Unit = {}) {
        speakHumanLike(text, onDone)
    }

    fun stopSpeaking() {
        try {
            engine?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopSpeaking error: ${e.message}")
        }
        _isSpeaking.value = false
        if (_voiceState.value is VoiceState.Speaking) {
            _voiceState.value = VoiceState.Idle
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            engine?.release()
            engine = null
            _isListening.value = false
            _isSpeaking.value = false
            _soundLevel.value = 0f
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "release error: ${e.message}")
        }
    }
}
