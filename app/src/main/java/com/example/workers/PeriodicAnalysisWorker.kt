package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.ZadAiRepository
import com.example.data.ZadCentralBrain
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger

class PeriodicAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ZadWorker", "PeriodicAnalysisWorker started!")
        try {
            val dao = ZadDatabase.getDatabase(applicationContext).zadDao()
            val inventory = dao.getAllInventory().first()
            val transactions = dao.getAllTransactions().first()
            val subscriptions = dao.getAllSubscriptions().first()
            val behaviorPatterns = dao.getBehaviorPatterns()

            // ====== RUN ENHANCED BRAIN ======
            // Read the budget from SharedPreferences (set by user in BudgetScreen)
            val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            val budget = prefs.getFloat("cached_budget", 3500f).toDouble()

            val brainResult = ZadCentralBrain.fullAnalysis(
                inventory = inventory,
                transactions = transactions,
                subscriptions = subscriptions,
                shoppingList = emptyList(),
                behaviorPatterns = behaviorPatterns,
                budget = budget
            )

            // Send smart notifications
            brainResult.smartNotifications.forEach { notif ->
                showNotification(
                    title = notif.title,
                    message = notif.body,
                    priority = when (notif.priority) {
                        "HIGH" -> NotificationCompat.PRIORITY_HIGH
                        "LOW" -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    }
                )
            }

            // Fallback: send AI insights if no smart notifications
            if (brainResult.smartNotifications.isEmpty()) {
                val insights = ZadAiRepository.generateBehavioralInsights(transactions, inventory)
                val criticalInsights = insights.filter { it.type == "Warning" || it.type == "Alert" }

                if (criticalInsights.isNotEmpty()) {
                    val insight = criticalInsights.first()
                    showNotification(insight.title, insight.description)
                } else if (insights.isNotEmpty()) {
                    val insight = insights.random()
                    showNotification(insight.title, insight.description)
                }
            }

            // Run legacy brain engine for compatibility
            try {
                com.example.data.ZadBrainEngine.evaluateStateAndAct(inventory, transactions)
            } catch (e: Exception) {
                Log.e("ZadWorker", "Legacy brain engine failed: ${e.message}")
            }

            if (brainResult.autoActions.isNotEmpty()) {
                Log.d("ZadWorker", "${brainResult.autoActions.size} auto-actions generated")
            }

            Log.d("ZadWorker", "Analysis complete — ${brainResult.smartNotifications.size} smart notifications sent")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ZadWorker", "Analysis Failed", e)
            return Result.failure()
        }
    }

    private fun showNotification(title: String, message: String, priority: Int = NotificationCompat.PRIORITY_DEFAULT) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_analysis_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "زاد — الإشعارات الذكية", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Use title+message hash to avoid Int overflow from currentTimeMillis().toInt()
        val notifId = (title + message).hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        manager.notify(notifId, notification)
        Log.d("ZadWorker", "Notification sent: $title — $message")
    }
}
