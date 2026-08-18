package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.screens.AlertPrefs
import java.util.Locale

// Real, system-level push notification — used by both ZadCentralBrain's deterministic rules
// and its merged AI tool-loop (formerly the separate ZadBrainEngine) so proactive notifications
// surface even when the app is closed, instead of only writing to the in-app app_notifications
// feed (SupabaseRepo.sendAppNotification, which is a separate, DB-only mechanism that needs
// HomeScreen open to be seen). Extracted from PeriodicAnalysisWorker's private showNotification()
// so all callers share one channel/pending-intent implementation instead of duplicating it.
object ZadNotifier {
    private const val CHANNEL_ID_BASE = "zad_analysis_channel"

    fun send(context: Context, title: String, message: String, priority: Int = NotificationCompat.PRIORITY_DEFAULT, speak: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // NotificationManager.notify() بترجع بهدوء لو الإذن مترفض — مش بترمي، ومش بتسيب
        // أي أثر. كل نداء هنا كان بيعدي كأنه نجح. اللوج ده هو اللي بيخلي "الإشعارات مش
        // بتوصل" سؤال ليه إجابة بدل ما يبقى تخمين.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.w("ZadNotifier", "POST_NOTIFICATIONS not granted — \"$title\" will not be shown")
            return
        }
        // القناة بتتسمى بحسب نسخة الصوت المختار — NotificationChannel.sound مينفعش يتغيّر
        // بعد الإنشاء، فتغيير الصوت من AssistantAlertsScreen بيزوّد النسخة (AlertPrefs)
        // فتتعمل قناة جديدة بالصوت الجديد بدل ما نحاول نعدّل واحدة قديمة (بيتجاهله أندرويد بصمت).
        val soundVersion = AlertPrefs.getNotificationSoundVersion(context)
        val channelId = "${CHANNEL_ID_BASE}_v$soundVersion"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "زاد — الإشعارات الذكية", NotificationManager.IMPORTANCE_DEFAULT)
            val soundUriStr = AlertPrefs.getNotificationSoundUri(context)
            val soundUri = if (soundUriStr != null) Uri.parse(soundUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            channel.setSound(soundUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            manager.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, channelId)
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

        // النطق الصوتي العربي الهادئ — يعمل فقط إذا كانت التنبيهات الصوتية مفعلة في الإعدادات
        val isVoiceSpokenEnabled = AlertPrefs.isEnabled(context, AlertPrefs.KEY_VOICE_SPOKEN_ALERTS)
        if (speak && priority >= NotificationCompat.PRIORITY_HIGH && isVoiceSpokenEnabled) {
            speakArabic(context, "$title. $message")
        }
    }

    private fun speakArabic(context: Context, text: String) {
        var tts: TextToSpeech? = null
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
        }

        // Clean text for natural human speech (strip markdown & URL, add breath pauses)
        val naturalSpeechText = text
            .replace(Regex("[#*`_~>\\[\\]()]"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[\\p{So}\\p{Cn}]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace("،", "، ... ")
            .replace(".", ". ... ")
            .replace("!", "! ... ")
            .replace("؟", "؟ ... ")
            .trim()

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                finishOnce()
                return@TextToSpeech
            }
            val marketLocale = com.example.data.MarketPrefs.getMarket(context).toLocale()
            val locale = when {
                tts?.isLanguageAvailable(marketLocale)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true -> marketLocale
                else -> Locale("ar")
            }
            val available = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_MISSING_DATA
            if (available < TextToSpeech.LANG_AVAILABLE) {
                finishOnce()
                return@TextToSpeech
            }
            tts?.language = locale
            // نبرة دافئة وطبيعية جداً مثل التحدث البشري المباشر
            tts?.setPitch(1.05f)
            tts?.setSpeechRate(0.98f)

            // دقة اختيار أعلى وأحدث صوت عصبي بشري (Neural / Wavenet / Studio / Natural)
            val voices = tts?.voices
            if (voices != null && voices.isNotEmpty()) {
                val neuralVoice = voices.firstOrNull { v ->
                    v.locale.language == "ar" && (
                        v.name.contains("neural", ignoreCase = true) ||
                        v.name.contains("wavenet", ignoreCase = true) ||
                        v.name.contains("studio", ignoreCase = true) ||
                        v.name.contains("natural", ignoreCase = true) ||
                        v.name.contains("ar-x-", ignoreCase = true) ||
                        v.name.contains("female", ignoreCase = true)
                    )
                }
                if (neuralVoice != null) {
                    tts?.voice = neuralVoice
                }
            }

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { finishOnce() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { finishOnce() }
            })
            tts?.speak(naturalSpeechText, TextToSpeech.QUEUE_FLUSH, null, "zad_notifier")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finishOnce() }, 15_000)
    }
}
