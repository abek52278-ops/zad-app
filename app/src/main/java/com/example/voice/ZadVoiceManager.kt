package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Recognized(val text: String) : VoiceState()
    object Thinking : VoiceState()
    data class Speaking(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class ZadVoiceManager(private val context: Context) {
    private val TAG = "ZadVoiceManager"

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                configureFemaleArabicVoice()
            } else {
                Log.w(TAG, "TextToSpeech init failed: $status")
            }
        }
    }

    private fun configureFemaleArabicVoice() {
        val tts = textToSpeech ?: return
        try {
            val marketLocale = com.example.data.MarketPrefs.getMarket(context).toLocale()
            val locale = if (tts.isLanguageAvailable(marketLocale) >= TextToSpeech.LANG_AVAILABLE) {
                marketLocale
            } else {
                Locale("ar")
            }

            tts.language = locale
            // Set pleasant female pitch & speed
            tts.setPitch(1.20f)
            tts.setSpeechRate(1.05f)

            // Try to find a female voice among available voices
            val voices = tts.voices
            if (voices != null) {
                val femaleVoice = voices.firstOrNull { v ->
                    v.locale.language == "ar" && (
                        v.name.contains("female", ignoreCase = true) ||
                        v.name.contains("fem", ignoreCase = true) ||
                        v.name.contains("ar-x-", ignoreCase = true)
                    )
                }
                if (femaleVoice != null) {
                    tts.voice = femaleVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice configuration exception: ${e.message}")
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        stopSpeaking()
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _voiceState.value = VoiceState.Error("التعرف على الصوت غير مدعوم على هذا الجهاز")
                    return@post
                }

                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

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
                        _voiceState.value = VoiceState.Idle
                        _isListening.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح إذن استخدام الميكروفون"
                            SpeechRecognizer.ERROR_AUDIO -> "تعذر الوصول للميكروفون"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تأكد من الاتصال بالإنترنت"
                            SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع شيئاً، اضغط الميكروفون وحاول مرة أخرى"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "جاهزة، اضغط الميكروفون وتحدث"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "جاري إعادة التهيئة..."
                            else -> "حدث خطأ (${error})"
                        }
                        Log.w(TAG, "SpeechRecognizer error: $error ($msg)")
                        // Recognition errors are UI state, never user speech. Passing msg to
                        // onResult used to send "تأكد من الاتصال بالإنترنت" to the AI agent
                        // as if the customer had spoken it.
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
                            _voiceState.value = VoiceState.Error("لم أسمع شيئاً، اضغط وتحدث ثانية")
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
                _voiceState.value = VoiceState.Error("تعذر تفعيل الميكروفون: ${e.message}")
            }
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

    private val naturalVoiceEngine = ZadNaturalVoiceEngine(context)

    fun setVoicePersona(persona: ZadNaturalVoiceEngine.VoicePersona) {
        naturalVoiceEngine.setPersona(persona)
    }

    fun getCurrentPersona(): ZadNaturalVoiceEngine.VoicePersona = naturalVoiceEngine.currentPersona.value

    fun speakHumanLike(text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) {
            onDone()
            return
        }

        _voiceState.value = VoiceState.Speaking(text)
        naturalVoiceEngine.speakHumanLike(text) {
            _voiceState.value = VoiceState.Idle
            onDone()
        }
    }

    fun speakFemaleVoice(text: String, onDone: () -> Unit = {}) {
        speakHumanLike(text, onDone)
    }

    fun stopSpeaking() {
        try {
            naturalVoiceEngine.stop()
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopSpeaking error: ${e.message}")
        }
        if (_voiceState.value is VoiceState.Speaking) {
            _voiceState.value = VoiceState.Idle
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            naturalVoiceEngine.release()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            _isListening.value = false
            _soundLevel.value = 0f
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "release error: ${e.message}")
        }
    }
}
