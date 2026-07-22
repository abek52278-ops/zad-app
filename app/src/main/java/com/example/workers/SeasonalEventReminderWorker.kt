package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.SeasonalEvent
import com.example.data.SupabaseRepo

/**
 * تنبيه استباقي بمناسبة موسمية قادمة (رمضان/عيد الفطر/عيد الأضحى/العودة للمدارس)
 * قبل 30 يوم من بدايتها — يشتغل مرة كل يوم، ويتجنب تكرار نفس الإشعار لنفس السنة
 * عبر cache محلي في SharedPreferences.
 */
class SeasonalEventReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val events = SupabaseRepo.getUpcomingSeasonalEvents(withinDays = 30)
            val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            val notified = prefs.getStringSet("seasonal_notified_events", emptySet())!!.toMutableSet()

            events.forEach { (event, window) ->
                val key = "${event.id}_${window?.year ?: "oneoff"}"
                if (key !in notified) {
                    val displayName = seasonalEventDisplayName(event)
                    showNotification(
                        title = "📅 $displayName قريباً",
                        message = "استعد لمصاريف $displayName — ابدأ التوفير الآن"
                    )
                    notified += key
                }
            }
            prefs.edit().putStringSet("seasonal_notified_events", notified).apply()
            Log.d("ZadSeasonalWorker", "Seasonal reminder check done — ${events.size} upcoming event(s)")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ZadSeasonalWorker", "Seasonal reminder failed", e)
            return Result.failure()
        }
    }

    private fun seasonalEventDisplayName(event: SeasonalEvent): String = when (event.slug) {
        "ramadan" -> "رمضان"
        "eid_al_fitr" -> "عيد الفطر"
        "eid_al_adha" -> "عيد الأضحى"
        "back_to_school" -> "العودة للمدارس"
        else -> event.name
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_seasonal_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "زاد — المناسبات الموسمية", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = (title + message).hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        manager.notify(notifId, notification)
    }
}
