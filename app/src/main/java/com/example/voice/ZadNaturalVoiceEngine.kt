package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 🎙️ محرك الصوت البشري الطبيعي الذكي (Zad Google AI Studio Natural Voice Engine)
 * يوفر تجربة محادثة صوتية واقعية وانسيابية مثل ChatGPT Voice و Google AI Studio / Gemini Live
 * مع دعم لكنات عربية طبيعية وتخصيص نبرات واقعية وشخصية الحيوان الأليف اللطيف.
 */
class ZadNaturalVoiceEngine(private val context: Context) {

    private val TAG = "ZadNaturalVoiceEngine"

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

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentPersona = MutableStateFlow(VoicePersona.SARAH_STUDIO_WARM)
    val currentPersona: StateFlow<VoicePersona> = _currentPersona.asStateFlow()

    var onSpeechCompletedListener: (() -> Unit)? = null

    init {
        initTtsEngine()
    }

    private fun initTtsEngine() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupVoicePersona(_currentPersona.value)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        onSpeechCompletedListener?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.w(TAG, "TTS Initialization failed with code: $status")
            }
        }
    }

    fun setPersona(persona: VoicePersona) {
        _currentPersona.value = persona
        if (isInitialized) {
            setupVoicePersona(persona)
        }
    }

    private fun setupVoicePersona(persona: VoicePersona) {
        val tts = textToSpeech ?: return
        try {
            val marketLocale = com.example.data.MarketPrefs.getMarket(context).toLocale()
            val locale = if (tts.isLanguageAvailable(marketLocale) >= TextToSpeech.LANG_AVAILABLE) {
                marketLocale
            } else {
                Locale("ar")
            }
            tts.language = locale
            tts.setPitch(persona.pitch)
            tts.setSpeechRate(persona.speechRate)

            // بحث عن أفضل صوت عربي عصبي (Neural / Wavenet / Studio / Natural)
            val availableVoices = tts.voices
            if (availableVoices != null && availableVoices.isNotEmpty()) {
                val bestVoice = when (persona) {
                    VoicePersona.SARAH_STUDIO_WARM -> {
                        availableVoices.firstOrNull { v ->
                            v.locale.language == "ar" && (
                                v.name.contains("natural", ignoreCase = true) ||
                                v.name.contains("neural", ignoreCase = true) ||
                                v.name.contains("wavenet", ignoreCase = true) ||
                                v.name.contains("female", ignoreCase = true) ||
                                v.name.contains("ar-x-", ignoreCase = true)
                            )
                        }
                    }
                    VoicePersona.KARIM_STUDIO_PRO -> {
                        availableVoices.firstOrNull { v ->
                            v.locale.language == "ar" && (
                                v.name.contains("male", ignoreCase = true) &&
                                !v.name.contains("female", ignoreCase = true)
                            )
                        }
                    }
                    VoicePersona.PET_MASCOT_CUTE -> {
                        availableVoices.firstOrNull { v ->
                            v.locale.language == "ar" && (
                                v.name.contains("female", ignoreCase = true) ||
                                v.name.contains("ar-x-", ignoreCase = true)
                            )
                        }
                    }
                }

                if (bestVoice != null) {
                    tts.voice = bestVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed configuring voice persona: ${e.message}")
        }
    }

    /**
     * نطق النص بأسلوب بشري طبيعي مع إضافة وقفات تنفسية وتنسيق إنساني للكلام
     */
    fun speakHumanLike(text: String, onDone: (() -> Unit)? = null) {
        val tts = textToSpeech
        if (tts == null || !isInitialized) {
            Log.w(TAG, "TTS not ready to speak")
            return
        }

        onSpeechCompletedListener = onDone

        // تشغيل صوت لطيف إذا كانت شخصية الحيوان الأليف
        if (_currentPersona.value.isPetPersona) {
            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.MeowChirp, 0.40f)
        }

        val naturalHumanText = prepareNaturalSpeechText(text, _currentPersona.value)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts.speak(naturalHumanText, TextToSpeech.QUEUE_FLUSH, params, "zad_natural_human_dialogue")
    }

    fun stop() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        } catch (_: Exception) {}
    }

    /**
     * تحسين النص لغوياً وعاطفياً وإضافة وقفات طبيعية للإلقاء البشري
     */
    private fun prepareNaturalSpeechText(rawText: String, persona: VoicePersona): String {
        var cleaned = rawText
            // إزالة الرموز ومحددات المارك داون
            .replace(Regex("[#*`_~>\\[\\]()]"), " ")
            .replace(Regex("https?://\\S+"), "الرابط")
            .replace(Regex("[\\p{So}\\p{Cn}]"), " ") // تنظيف الإيموجيز المعقدة
            .replace(Regex("\\s+"), " ")
            .trim()

        // إضافة وقفات تنفسية طبيعية حول الفواصل والنقاط
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
            // إذا لم تكن تبدأ بترحيب
            if (!cleaned.startsWith("أهل") && !cleaned.startsWith("مرحب")) {
                cleaned = cutePrefixes.random() + cleaned
            }
        }

        return cleaned
    }
}
