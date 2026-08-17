package com.example.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.PharmacyReminderScheduler
import com.example.data.SupabaseRepo
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "PharmacyReminder"
private const val CHANNEL_ID = "zad_pharmacy_reminders"
private const val ACTION_MARK_TAKEN = "com.example.PHARMACY_REMINDER_MARK_TAKEN"
private const val ACTION_SNOOZE = "com.example.PHARMACY_REMINDER_SNOOZE"

/**
 * بيستقبل 3 أنواع نداءات: تنبيه الموعد نفسه (من AlarmManager) وزراير الإشعار
 * (تم الأخذ / أجّل). التنبيه بيتقال بالصوت (TTS) مش بس notification صامت — طلب
 * المستخدم صراحة إشعار بصوت لمواعيد الدواء.
 */
class PharmacyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra("item_id") ?: return
        val itemName = intent.getStringExtra("item_name") ?: ""
        val doseTime = intent.getStringExtra("dose_time") ?: return
        val doseLogId = intent.getStringExtra("dose_log_id")
        val scheduledAt = intent.getStringExtra("scheduled_at")
        val notificationId = "$itemId::$doseTime".hashCode()

        when (intent.action) {
            PharmacyReminderScheduler.ACTION_TRIGGER -> handleTrigger(context, itemId, itemName, doseTime, notificationId)
            ACTION_MARK_TAKEN -> handleMarkTaken(context, itemId, doseLogId, scheduledAt, notificationId)
            ACTION_SNOOZE -> handleSnooze(context, itemId, itemName, doseTime, notificationId)
        }
    }

    private fun handleTrigger(context: Context, itemId: String, itemName: String, doseTime: String, notificationId: Int) {
        val pendingResult = goAsync()
        val doseLogId = java.util.UUID.randomUUID().toString()
        // Canonical "today's HH:mm dose" instant, not Instant.now() — this is what lets the
        // screen's per-time-slot button and this fired-alarm path dedupe against each other
        // via the zad_pharmacy_doses unique index (Task 17.2.2) instead of minting two
        // near-identical-but-different timestamps for what is really the same dose.
        val scheduledAtIso = PharmacyReminderScheduler.canonicalScheduledAt(doseTime) ?: java.time.Instant.now().toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = ZadDatabase.getDatabase(context.applicationContext).zadDao()
                val log = com.example.data.ZadDoseLog(
                    id = doseLogId, pharmacyItemId = itemId, itemName = itemName,
                    scheduledAt = scheduledAtIso, createdAt = scheduledAtIso
                )
                dao.insertDoseLog(log)
                try { SupabaseRepo.addDoseLog(log) } catch (e: Exception) {
                    Log.e(TAG, "handleTrigger() dose log sync failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleTrigger() dose log insert failed: ${e.message}")
            }
            showReminderNotification(context, itemId, itemName, doseTime, notificationId, doseLogId, scheduledAtIso)
            // sendFamilyAlert is itself best-effort (catches internally) — no family or a
            // network failure here must not block the notification/TTS below.
            com.example.data.ZadCentralBrain.sendFamilyAlert("⏰ حان موعد جرعة $itemName")
            speakReminder(context, itemName, pendingResult)
        }
        // بيتجدد يومياً لنفس الميعاد فور ما يطلق — عشان يفضل شغال من غير ما يحتاج تدخل يدوي
        PharmacyReminderScheduler.rescheduleForTomorrow(context, itemId, itemName, doseTime)
    }

    private fun handleMarkTaken(context: Context, itemId: String, doseLogId: String?, scheduledAt: String?, notificationId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Shared with the voice-command path (ZadCentralBrain.executeAiAction's
                // DEDUCT_PHARMACY_STOCK) so "Taken" tap and "خدت الدواء" voice command chain
                // into the same stock-deduct -> low-stock-check -> shopping-list logic once.
                com.example.data.ZadCentralBrain.markPharmacyDoseTaken(context.applicationContext, itemId, doseLogId, scheduledAt)
            } catch (e: Exception) {
                Log.e(TAG, "handleMarkTaken() FAILED: ${e.message}")
            } finally {
                dismissNotification(context, notificationId)
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, itemId: String, itemName: String, doseTime: String, notificationId: Int) {
        PharmacyReminderScheduler.scheduleSnooze(context, itemId, itemName, doseTime, minutesFromNow = 10)
        dismissNotification(context, notificationId)
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
    }

    private fun showReminderNotification(context: Context, itemId: String, itemName: String, doseTime: String, notificationId: Int, doseLogId: String, scheduledAt: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // showBadge=true افتراضي أصلاً لقناة جديدة، بس بنحدده صراحة عشان أيقونة التطبيق
            // تعرض نقطة/عدد التنبيهات المعلقة (native per-channel badge API — مفيش API عام
            // ثابت لكل اللانشرات في أندرويد، ده هو المدعوم رسمياً من النظام).
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تذكير مواعيد الدواء", NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(true)
                }
            )
        }

        fun actionIntent(action: String): PendingIntent {
            val intent = Intent(context, PharmacyReminderReceiver::class.java).apply {
                this.action = action
                putExtra("item_id", itemId)
                putExtra("item_name", itemName)
                putExtra("dose_time", doseTime)
                putExtra("dose_log_id", doseLogId)
                putExtra("scheduled_at", scheduledAt)
            }
            return PendingIntent.getBroadcast(
                context, "$action::$itemId::$doseTime".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.pharmacy_reminder_title))
            .setContentText(context.getString(R.string.pharmacy_reminder_body, itemName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.mark_taken_action), actionIntent(ACTION_MARK_TAKEN))
            .addAction(0, context.getString(R.string.snooze_10_min_action), actionIntent(ACTION_SNOOZE))
            .build()

        manager.notify(notificationId, notification)
    }

    private fun speakReminder(context: Context, itemName: String, pendingResult: PendingResult) {
        if (!com.example.ui.screens.AlertPrefs.isEnabled(context, com.example.ui.screens.AlertPrefs.KEY_VOICE_SPOKEN_ALERTS)) {
            pendingResult.finish()
            return
        }

        val text = context.getString(R.string.pharmacy_reminder_voice_text, itemName)
        var tts: TextToSpeech? = null
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
            pendingResult.finish()
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed, status=$status")
                finishOnce()
                return@TextToSpeech
            }
            val voiceLocale = com.example.data.MarketPrefs.currentMarket.toLocale()
            val result = tts?.setLanguage(voiceLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(java.util.Locale("ar"))
            }
            tts?.setPitch(1.10f)
            tts?.setSpeechRate(0.96f)

            val voices = tts?.voices
            if (voices != null) {
                val femaleVoice = voices.firstOrNull { v ->
                    v.locale.language == "ar" && (
                        v.name.contains("female", ignoreCase = true) ||
                        v.name.contains("fem", ignoreCase = true) ||
                        v.name.contains("ar-x-", ignoreCase = true)
                    )
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { finishOnce() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { finishOnce() }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pharmacy_reminder")
        }

        // شبكة أمان: TTS ممكن يفشل يبعت callback خالص (جهاز بدون محرك صوت) — سقف 15 ثانية
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finishOnce() }, 15_000)
    }
}
