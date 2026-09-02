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

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Recognized(val text: String) : VoiceState()
    object Thinking : VoiceState()
    data class Speaking(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
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

    private lateinit var appContext: Context
    private var initialized = false

    /** لازم تتنادى مرة قبل أي استخدام — MainActivity.onCreate بينادّيها زي NetworkMonitor.register. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        val savedId = personaPrefs.getString("persona_id", null)
        savedId?.let { id ->
            ZadNaturalVoiceEngine.VoicePersona.values()
                .firstOrNull { it.id == id }
                ?.let { naturalVoiceEngine.setPersona(it) }
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

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(onResult: (String) -> Unit) {
        stopSpeaking()
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _voiceState.value = VoiceState.Error(appContext.getString(R.string.voice_error_unavailable))
                return@post
            }

            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null

            // كان create+startListening بينفذوا في نفس الـtick بعد destroy() على طول —
            // خدمة التعرف بتاعة أندرويد (بروسس نظام منفصل، مش نفس الأوبچكت العميل) محتاجة
            // لحظة تفرّج فيها الجلسة القديمة قبل ما جلسة جديدة تقدر تاخدها؛ من غير فاصل،
            // النتيجة المتكررة على أجهزة حقيقية كانت ERROR_RECOGNIZER_BUSY فورية — مش
            // مشكلة في المايك نفسه ولا في صلاحيته، مجرد سباق توقيت. ١٥٠ مللي كافية عمليًا.
            mainHandler.postDelayed({ startListeningInternal(onResult) }, 150)
        }
    }

    private fun startListeningInternal(onResult: (String) -> Unit) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)

                val marketLocale = com.example.data.MarketPrefs.getMarket(appContext).toLocale()
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
                        _voiceState.value = VoiceState.Listening
                        _isListening.value = true
                        com.example.ui.components.ZadChime.play(com.example.ui.components.ZadChime.Tone.Tap)
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _soundLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.Thinking
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> appContext.getString(R.string.voice_error_permission)
                            SpeechRecognizer.ERROR_AUDIO -> appContext.getString(R.string.voice_error_audio)
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> appContext.getString(R.string.voice_error_network)
                            SpeechRecognizer.ERROR_NO_MATCH -> appContext.getString(R.string.voice_error_no_match)
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> appContext.getString(R.string.voice_error_timeout)
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> appContext.getString(R.string.voice_error_busy)
                            else -> appContext.getString(R.string.voice_error_generic)
                        }
                        Log.w(TAG, "SpeechRecognizer error: $error ($msg)")
                        // أخطاء التعرف حالة واجهة، مش كلام المستخدم — عمرها ما تتبعت للوكيل.
                        _voiceState.value = VoiceState.Error(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        _voiceState.value = VoiceState.Idle
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            _voiceState.value = VoiceState.Recognized(text)
                            com.example.ui.components.ZadChime.play(com.example.ui.components.ZadChime.Tone.Success)
                            onResult(text)
                        } else {
                            _voiceState.value = VoiceState.Error(appContext.getString(R.string.voice_error_no_match))
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            _voiceState.value = VoiceState.Recognized(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                _voiceState.value = VoiceState.Listening
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer error: ${e.message}")
                _voiceState.value = VoiceState.Error(appContext.getString(R.string.voice_error_microphone))
            }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening error: ${e.message}")
        }
        _isListening.value = false
        _soundLevel.value = 0f
    }

    fun markThinking() {
        _voiceState.value = VoiceState.Thinking
        _isListening.value = false
        _soundLevel.value = 0f
    }

    // by lazy — appContext لسه مش متعين وقت أول تحميل للـobject، بيتحل أول ما init() تتنادى
    // وأي method فيها يتلمس فعليًا بعد كده.
    private val naturalVoiceEngine by lazy { ZadNaturalVoiceEngine(appContext) }

    // حفظ الشخصية المختارة بين الجلسات
    private val personaPrefs by lazy { appContext.getSharedPreferences("zad_voice_persona", Context.MODE_PRIVATE) }

    fun setVoicePersona(persona: ZadNaturalVoiceEngine.VoicePersona) {
        naturalVoiceEngine.setPersona(persona)
        personaPrefs.edit().putString("persona_id", persona.id).apply()
    }

    fun getCurrentPersona(): ZadNaturalVoiceEngine.VoicePersona = naturalVoiceEngine.currentPersona.value

    /** حالة توفر الصوت البشري — لو ElevenLاس فشل، الواجهة تعرض النص بدل صمت.
     *  by lazy زي naturalVoiceEngine بالظبط — val عادي هنا كان بيجبر تقييم naturalVoiceEngine
     *  وقت تحميل الـ object (static init)، يعني قبل ما init(context) تتنادى أصلاً، فـ appContext
     *  لسه lateinit مش متعين → UninitializedPropertyAccessException بتتلف كـ
     *  ExceptionInInitializerError عند أول لمسة للـ object (MainActivity.onCreate). */
    val humanVoiceAvailable: StateFlow<Boolean> by lazy { naturalVoiceEngine.humanVoiceAvailable }

    /** نطق بصوت بشري. onDone بعد نجاح التشغيل، onFailed لو الصوت البشري مش متاح. */
    fun speakHumanLike(text: String, onDone: () -> Unit = {}, onFailed: () -> Unit = {}) {
        if (text.isBlank()) {
            onDone()
            return
        }

        _voiceState.value = VoiceState.Speaking(text)
        _isSpeaking.value = true
        naturalVoiceEngine.speakHumanLike(
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
            naturalVoiceEngine.stop()
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
            naturalVoiceEngine.release()
            _isListening.value = false
            _isSpeaking.value = false
            _soundLevel.value = 0f
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "release error: ${e.message}")
        }
    }
}
