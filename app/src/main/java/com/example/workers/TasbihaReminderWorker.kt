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
import com.example.data.SupabaseRepo
import com.example.ui.screens.AlertPrefs

/**
 * تذكير يومي لطيف بالتسبيح — يبعت بس لو المستخدم لسه ما سبّحش النهاردة،
 * عشان يبقى تذكير مفيد مش إزعاج لحد سبّح فعلاً.
 */
class TasbihaReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ZadTasbihaReminder", "TasbihaReminderWorker started!")
        try {
            if (!AlertPrefs.isEnabled(applicationContext, AlertPrefs.KEY_TASBIH_REMINDER)) {
                Log.d("ZadTasbihaReminder", "disabled by user — skipping")
                return Result.success()
            }

            val tree = SupabaseRepo.getMyTasbiha() ?: return Result.success()
            val doneToday = tree.lastTasbihAt?.let { last ->
                try {
                    java.time.LocalDate.parse(last.take(10)) == java.time.LocalDate.now()
                } catch (e: Exception) { false }
            } ?: false

            if (doneToday) {
                Log.d("ZadTasbihaReminder", "already tasbih'd today — skipping")
                return Result.success()
            }

            showNotification()
            Log.d("ZadTasbihaReminder", "reminder sent")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ZadTasbihaReminder", "failed", e)
            return Result.failure()
        }
    }

    private fun showNotification() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_tasbih_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "زاد — تذكير التسبيح", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("🌱 وقت التسبيح")
            .setContentText("شجرتك مستنياك — سبّح شوية ونمّيها")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(929292, notification)
    }
}
