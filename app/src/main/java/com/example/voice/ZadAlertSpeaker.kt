package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * نطق التنبيهات بنفس صوت زاد البشري (ElevenLabs عبر سيرفرنا) — بدل TTS الروبوتي
 * اللي كان بيشتغل في خيال منفصل عن صوت الوكيل. يُستخدم من BroadcastReceiver و
 * Service (سياقات قصيرة العمر): بننشئ محرك مؤقت ونحرقه بعد الكلام.
 *
 * لو المستخدم مقفل التنبيهات الصوتية أو فشل الشبكة → إشعار صامت، مفيش صوت آلي أبدًا.
 */
object ZadAlertSpeaker {

    fun speakAlert(context: Context, text: String, onFinished: () -> Unit) {
        if (!com.example.ui.screens.AlertPrefs.isEnabled(context, com.example.ui.screens.AlertPrefs.KEY_VOICE_SPOKEN_ALERTS)) {
            onFinished()
            return
        }
        val engine = ZadNaturalVoiceEngine(context.applicationContext)
        var finished = false
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        fun finishOnce() {
            if (finished) return
            finished = true
            mainHandler.post { engine.release() }
            onFinished()
        }

        engine.speakHumanLike(
            text,
            onDone = { finishOnce() },
            onFailed = { finishOnce() } // إشعار صامت — عمرها ما نستخدم صوت آلي
        )

        // شبكة أمان لو الاتصال علّق: سقف 20 ثانية
        mainHandler.postDelayed({ finishOnce() }, 20_000)
    }

    /** طلب Audio Focus سريع للتحقق إن الجهاز مش في وضع عدم الإزعاج الحرج. */
    fun canSpeakNow(context: Context): Boolean {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        // AudioFocusRequest متاح من API 26 — الأقدم بيتجاهل فحص الفوكس (سلوك مقبول:
        // التنبيه هينطق برضه، بس من غير ما يطلب الأولوية)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        val granted = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        am.abandonAudioFocusRequest(request)
        return granted
    }
}
