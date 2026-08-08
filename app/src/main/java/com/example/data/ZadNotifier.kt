package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.R
import java.util.Locale

// Real, system-level push notification — used by both ZadCentralBrain's deterministic rules
// and its merged AI tool-loop (formerly the separate ZadBrainEngine) so proactive notifications
// surface even when the app is closed, instead of only writing to the in-app app_notifications
// feed (SupabaseRepo.sendAppNotification, which is a separate, DB-only mechanism that needs
// HomeScreen open to be seen). Extracted from PeriodicAnalysisWorker's private showNotification()
// so all callers share one channel/pending-intent implementation instead of duplicating it.
object ZadNotifier {
    private const val CHANNEL_ID = "zad_analysis_channel"

    fun send(context: Context, title: String, message: String, priority: Int = NotificationCompat.PRIORITY_DEFAULT, speak: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "زاد — الإشعارات الذكية", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // title+message hash to avoid Int overflow from currentTimeMillis().toInt()
        val notifId = (title + message).hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        manager.notify(notifId, notification)

        // "المفروض الإشعارات تكون ذكية وبصوت" — الـ HIGH/الحرجة بتتنطق بالعربي، الباقي صامت.
        // نفس نمط TTS المستخدم في ZadAlertRouter/ChatNotificationService. الـ TTS بيشتغل
        // لحظي (بصوت النداء مش بصوت التنبيه الميت)، والـ notification اتعرضت فوق أيوا.
        if (speak && priority >= NotificationCompat.PRIORITY_HIGH) {
            speakArabic(context, "$title. $message")
        }
    }

    /**
     * كان الـ TextToSpeech instance عمره ما بيتعمله shutdown() — كل نداء هنا (من
     * PeriodicAnalysisWorker كل ٦ ساعات، أو أي HIGH priority notification) بيفتح محرك TTS
     * جديد وسيبه معلّق للأبد؛ "الـ worker قصير العمر" في التعليق القديم مش حجة — محرك
     * TTS نفسه بيربط بـ TTS service منفصل عن عمر الـ caller. نفس نمط
     * PharmacyReminderReceiver.speakReminder: shutdown في onDone/onError + سقف أمان.
     */
    private fun speakArabic(context: Context, text: String) {
        var tts: TextToSpeech? = null
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                finishOnce()
                return@TextToSpeech
            }
            val arabic = Locale("ar")
            val available = tts?.isLanguageAvailable(arabic) ?: TextToSpeech.LANG_MISSING_DATA
            if (available < TextToSpeech.LANG_AVAILABLE) {
                finishOnce()
                return@TextToSpeech
            }
            tts?.language = arabic
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { finishOnce() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { finishOnce() }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zad_notifier")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finishOnce() }, 15_000)
    }
}
