package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import com.example.data.MarketPrefs
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * محرك صوت زاد — Gemini TTS فقط عبر الـ Edge Function (المفتاح سيرفر-سايد).
 *
 * مبادئ إعادة الهيكلة:
 * - مفيش TTS روبوتي: لو Gemini TTS فشل، بنعمل retry بسيط ثم نبلّغ بالفشل بدل ما
 *   نشغّل صوت آلي يكسر إحساس "صوت بشري". (الـ fallback المحلي اتمسح نهائيًا.)
 * - Audio Focus دايمًا قبل الكلام — مفيش خدمة تتكلم فوق زاد.
 * - PCM 16-bit بحدود عينات محفوظة — لا تشويش ولا "صرير راديو".
 * - generation counter يلغي أي تشغيل قديم فورًا عند طلب جديد (مقاطعة حقيقية).
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
            displayNameAr = "👩 سارة (صوت بشري دافئ)",
            descriptionAr = "صوت بشري طبيعي دافئ ومريح للأذن مع إيقاع متزن",
            pitch = 1.08f,
            speechRate = 1.02f
        ),
        KARIM_STUDIO_PRO(
            id = "karim_pro",
            displayNameAr = "👨 كريم (صوت بشري واثق)",
            descriptionAr = "نبرة واثقة، واضحة ومباشرة مثل المستشار الشخصي",
            pitch = 0.95f,
            speechRate = 1.05f
        ),
        PET_MASCOT_CUTE(
            id = "pet_mascot",
            displayNameAr = "🐾 زاد الأليف (رفيق مرح)",
            descriptionAr = "صوت مرح ولطيف مع أصوات الحيوان الأليف المحبوب",
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

    // ممكن يبقى فيه أكتر من نداء شبكة شغال في نفس الوقت (الجملة الحالية بتتشغّل
    // واللي بعدها بتتجاب مقدمًا) — لازم كلهم يتلغوا مع أي stop() جديد.
    private val activeCalls = CopyOnWriteArrayList<Call>()
    // نفس الفكرة لكن على مستوى الـcoroutine نفسها مش بس نداء الشبكة جواها — لو
    // stop() جه قبل ما الـcoroutine توصل أصلاً لـactiveCalls.add()، إلغاء الـDeferred
    // بيمنعها تبدأ نداء شبكة مدفوع من الأول، مش بس تتجاهل نتيجته لما يخلص.
    private val pendingFetches = CopyOnWriteArrayList<Deferred<java.io.InputStream?>>()
    @Volatile private var audioTrack: AudioTrack? = null
    // مين آخر جيل فعليًا استحوذ على Audio Focus — بدون ده أي coroutine قديمة اتقاطعت
    // ولسه بتتنفذ ممكن تسحب الـfocus بتاع جيل أحدث لسه بيتكلم فعليًا (finding حقيقي
    // في المراجعة: abandonAudioFocus كان عام مش مربوط بجيل معيّن).
    @Volatile private var audioFocusOwnerGeneration: Long = -1L
    @Volatile private var completion: (() -> Unit)? = null
    @Volatile private var failedCompletion: (() -> Unit)? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioManager get() =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** يطلب Audio Focus قبل أي نطق — بدون ده أي مشغل تاني بيتكلم فوق زاد. */
    private fun requestAudioFocus(): Boolean {
        // AudioFocusRequest متاح من API 26 — الأجهزة الأقدم بتستخدم API القديم المكافئ
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

    private val sampleRate = 24_000
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentPersona = MutableStateFlow(VoicePersona.SARAH_STUDIO_WARM)
    val currentPersona: StateFlow<VoicePersona> = _currentPersona.asStateFlow()

    /** حالة توفر الصوت البشري — الواجهة تعرض بيها رسالة صادقة بدل صمت أو صوت آلي. */
    private val _humanVoiceAvailable = MutableStateFlow(true)
    val humanVoiceAvailable: StateFlow<Boolean> = _humanVoiceAvailable.asStateFlow()

    fun setPersona(persona: VoicePersona) {
        _currentPersona.value = persona
    }

    /**
     * ينطق النص بصوت سارة/كريم البشري عبر Gemini TTS (streaming PCM). مع retry واحد
     * للأخطاء العابرة (نت/5xx). لو فشل نهائيًا: onDone مش هيتنده — onFailed هو اللي
     * هيشتغل، عشان الواجهة تعرض النص مكتوبًا وتقول الحقيقة بدل صوت روبوت.
     */
    fun speakHumanLike(
        text: String,
        onDone: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        stopInternal()
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        val requestGeneration = generation.incrementAndGet()
        completion = onDone
        failedCompletion = onFailed
        _isSpeaking.value = true

        val persona = _currentPersona.value
        if (persona.isPetPersona) {
            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.MeowChirp, 0.40f)
        }
        val chunks = buildSpeechChunks(text, persona)

        scope.launch {
            speakChunksPipelined(requestGeneration, chunks, persona)
        }
    }

    /**
     * بيقسم الرد لجمل ويشغّلها الواحدة ورا التانية على AudioTrack واحد مستمر،
     * وبيبدأ تجهيز صوت الجملة التالية وهو لسه بيشغّل الحالية (one-ahead prefetch).
     * ده اللي بيقصّر "وقت أول صوت" لأول جملة بدل ما ننتظر توليد الرد كله صوتيًا —
     * قبل كده كان النص الكامل بيتبعت لـvoice_synthesize في نداء واحد، فالمستخدم
     * كان يسمع أول صوت بعد ما التوليد الصوتي للرد **كله** يخلص (Gemini TTS مالوش
     * streaming حقيقي، بيرجع الصوت كامل في نداء واحد — التقسيم هنا هو اللي بيعوّض ده).
     */
    private suspend fun speakChunksPipelined(
        requestGeneration: Long,
        chunks: List<String>,
        persona: VoicePersona
    ) {
        if (chunks.isEmpty()) {
            if (requestGeneration == generation.get()) finishSpeaking(requestGeneration)
            return
        }
        var nextFetch: Deferred<java.io.InputStream?> = launchFetch(requestGeneration, chunks[0], persona)
        var track: AudioTrack? = null
        var anySpoken = false
        try {
            for (i in chunks.indices) {
                if (requestGeneration != generation.get()) break
                val awaited = nextFetch
                val stream = awaited.await()
                pendingFetches.remove(awaited)
                // اجهّز الجملة التالية فورًا قبل ما نشغّل الحالية — كده وقت شبكتها
                // بيتغطى بوقت تشغيل الحالية بدل ما يتضاف عليه.
                nextFetch = if (i + 1 < chunks.size && requestGeneration == generation.get()) {
                    launchFetch(requestGeneration, chunks[i + 1], persona)
                } else {
                    scope.async { null }
                }
                if (stream == null) continue // جملة واحدة فشلت — كمّل الباقي بدل ما توقف كل الرد
                if (requestGeneration != generation.get()) {
                    try { stream.close() } catch (_: Exception) {}
                    break
                }
                if (track == null) {
                    track = buildAudioTrack()
                    audioTrack = track
                    if (!requestAudioFocus()) {
                        Log.w(tag, "Audio focus denied — playing at reduced priority")
                    }
                    audioFocusOwnerGeneration = requestGeneration
                    track.play()
                }
                anySpoken = true
                writePcmStream(requestGeneration, track, stream)
            }
        } finally {
            // بس لو أنا لسه صاحب الـfocus — جيل أحدث ممكن يكون استحوذ عليه بعدي
            // ولسه بيتكلم فعليًا، وسحبه هنا كان بيسكته من غير سبب.
            if (audioFocusOwnerGeneration == requestGeneration) abandonAudioFocus()
            val finished = track
            if (audioTrack === finished) audioTrack = null
            if (finished != null) {
                try { finished.stop() } catch (_: Exception) {}
                try { finished.release() } catch (_: Exception) {}
            }
        }
        if (!anySpoken) {
            Log.w(tag, "Human voice unavailable after retries")
            _humanVoiceAvailable.value = false
            _isSpeaking.value = false
            mainHandler.post {
                if (requestGeneration != generation.get()) return@post
                val cb = failedCompletion
                failedCompletion = null
                completion = null
                cb?.invoke()
            }
            return
        }
        _humanVoiceAvailable.value = true
        if (requestGeneration == generation.get()) finishSpeaking(requestGeneration)
    }

    private fun launchFetch(
        requestGeneration: Long,
        text: String,
        persona: VoicePersona
    ): Deferred<java.io.InputStream?> {
        val deferred = scope.async { synthesizeWithRetry(requestGeneration, text, persona) }
        pendingFetches.add(deferred)
        return deferred
    }

    /** بيقسم النص لجمل (.!؟) وبيجمع الجمل القصيرة مع بعض لحد ~٢٠٠ حرف — توازن بين
     *  "أول صوت بسرعة" و"مكالمات شبكة كتير عديمة الفايدة" لرد فيه جمل قصيرة كتير. */
    private fun splitIntoSpeechChunks(text: String): List<String> {
        val sentences = Regex("""(?<=[.!؟])\s+""").split(text).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= 1) return if (text.isBlank()) emptyList() else listOf(text)
        val merged = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length > 200) {
                merged.add(current.toString().trim())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) merged.add(current.toString().trim())
        return merged
    }

    /** جلب الصوت من الـ Edge Function مع retry واحد للأخطاء العابرة. */
    private suspend fun synthesizeWithRetry(
        requestGeneration: Long,
        text: String,
        persona: VoicePersona
    ): java.io.InputStream? {
        repeat(2) { attempt ->
            try {
                val session = SupabaseRepo.client.auth.currentSessionOrNull()
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id?.toString()
                if (session == null || userId.isNullOrBlank()) {
                    Log.w(tag, "No auth session for voice synthesis")
                    return null
                }
                val body = JSONObject().apply {
                    put("action", "voice_synthesize")
                    put("user_id", userId)
                    put("payload", JSONObject().apply {
                        put("text", text)
                        put("persona", persona.id)
                        put("locale", MarketPrefs.getMarket(context).localeTag)
                        // لهجة العميل توصل للـ style prompt — الصوت ينطق بنفس
                        // لهجته مش فصحى محايدة (المصري «إزيك»، الخليجي «شخبارك»...)
                        put("dialect_instruction", com.example.data.MarketPrefs.getMarket(context).let { m ->
                            when (m.localeTag.substringBefore("-")) {
                                "ar" -> when (m.localeTag) {
                                    "ar-EG" -> "تحدث باللهجة المصرية العامية"
                                    "ar-SA" -> "تحدث باللهجة السعودية"
                                    else -> ""
                                }
                                else -> ""
                            }
                        })
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
                activeCalls.add(call)
                try {
                    val response = call.execute()
                    if (requestGeneration != generation.get()) {
                        response.close()
                        return null
                    }
                    if (!response.isSuccessful || response.body == null) {
                        Log.w(tag, "Voice proxy attempt ${attempt + 1}: HTTP ${response.code}")
                        response.close()
                        if (attempt == 0 && (response.code >= 500)) {
                            Thread.sleep(600)
                            return@repeat
                        }
                        return null
                    }
                    return response.body!!.byteStream()
                } finally {
                    activeCalls.remove(call)
                }
            } catch (error: Exception) {
                if (requestGeneration != generation.get()) return null
                Log.w(tag, "Voice stream attempt ${attempt + 1} failed: ${error.message}")
                if (attempt == 0 && error !is kotlinx.coroutines.CancellationException) {
                    try { Thread.sleep(600) } catch (_: InterruptedException) {}
                    return@repeat
                }
                return null
            }
        }
        return null
    }

    private fun buildAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)
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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** بيكتب جملة واحدة (stream) على track مستمر من غير ما يقفله — القفل بتاع
     *  آخر جملة بس، عشان مفيش فجوة/طقطقة بين الجمل المتتالية. */
    private fun writePcmStream(requestGeneration: Long, track: AudioTrack, input: java.io.InputStream) {
        try {
            val buffer = ByteArray(8_192)
            var carry = 0
            while (requestGeneration == generation.get()) {
                val read = input.read(buffer, carry, buffer.size - carry)
                if (read < 0) break
                var available = read + carry
                // PCM 16-bit: كل كتابة لازم تكون عدد بايتات زوجي — نص عينة = تشويش
                val usable = available - (available % 2)
                if (usable > 0) {
                    track.write(buffer, 0, usable, AudioTrack.WRITE_BLOCKING)
                }
                available -= usable
                if (available == 1) {
                    buffer[0] = buffer[usable]
                    carry = 1
                } else {
                    carry = 0
                }
            }
            if (carry == 1 && requestGeneration == generation.get()) {
                track.write(byteArrayOf(buffer[0], 0), 0, 2, AudioTrack.WRITE_BLOCKING)
            }
        } catch (error: Exception) {
            Log.w(tag, "Playback failed mid-stream: ${error.message}")
        } finally {
            try { input.close() } catch (_: Exception) {}
        }
    }

    private fun finishSpeaking(requestGeneration: Long) {
        mainHandler.post {
            if (requestGeneration != generation.get()) return@post
            _isSpeaking.value = false
            val callback = completion
            completion = null
            failedCompletion = null
            callback?.invoke()
        }
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        generation.incrementAndGet()
        completion = null
        failedCompletion = null
        _isSpeaking.value = false
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        pendingFetches.forEach { it.cancel() }
        pendingFetches.clear()
        val track = audioTrack
        audioTrack = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopInternal()
        scope.cancel()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {
        }
    }

    /**
     * نظافة + تقطيع لجمل + وقفة الفاصلة لكل جملة لوحدها + بادئة الشخصية الأليفة
     * للجملة الأولى بس. **ترتيب مقصود**: وقفة الفاصلة (اللي بتحط "...") لازم
     * تتحط **بعد** التقطيع لجمل مش قبله — لو اتحطت قبل، الـ"..." نفسها فيها نقط
     * بيتبعها مسافة، فبتنضرب مع regex حدود الجملة في splitIntoSpeechChunks
     * وتقسّم عند كل فاصلة كمان، مش بس عند آخر الجملة (كان بق حاصل فعليًا قبل
     * الفيكس ده — كل فاصلة كانت بتفتح نداء TTS منفصل من غير داعي).
     */
    private fun buildSpeechChunks(rawText: String, persona: VoicePersona): List<String> {
        val cleaned = cleanRawText(rawText).take(1_200)
        val rawChunks = splitIntoSpeechChunks(cleaned)
        if (rawChunks.isEmpty()) return emptyList()
        val chunks = rawChunks.map { it.replace("،", "، ... ") }.toMutableList()

        if (persona.isPetPersona) {
            val prefixes = if (MarketPrefs.getMarket(context).localeTag.startsWith("tr")) {
                listOf("Merhaba! ", "Tabii! ", "Tamam! ")
            } else {
                listOf("أهلاً يا صديقي! ", "من عيوني! ", "تمام حاضر! ", "يا سلام! ")
            }
            if (!rawChunks[0].startsWith("أهل") && !rawChunks[0].startsWith("مرحب")) {
                chunks[0] = prefixes.random() + chunks[0]
            }
        }
        return chunks
    }

    private fun cleanRawText(rawText: String): String {
        val linkWord = if (MarketPrefs.getMarket(context).localeTag.startsWith("tr")) "bağlantı" else "الرابط"
        return rawText
            .replace(Regex("""https?://\S+"""), linkWord)
            .replace(Regex("""[#*`_~\[\]()]"""), " ")
            .replace(Regex("""[\p{So}\p{Cn}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
