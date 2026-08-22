package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.screens.AlertPrefs

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
        // NotificationManager.notify() بترجع بهدوء لو الإذن مترفض — مش بترمي، ومش بترمي، ومش بتسيب
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

        // النطق بصوت زاد البشري (ElevenLabs) — فقط إذا التنبيهات الصوتية مفعلة في الإعدادات.
        // مفيش TTS آلي أبدًا: لو الشبكة مش متاحة → إشعار صامت.
        val isVoiceSpokenEnabled = AlertPrefs.isEnabled(context, AlertPrefs.KEY_VOICE_SPOKEN_ALERTS)
        if (speak && priority >= NotificationCompat.PRIORITY_HIGH && isVoiceSpokenEnabled) {
            com.example.voice.ZadAlertSpeaker.speakAlert(context, "$title. $message") {}
        }
    }
}
