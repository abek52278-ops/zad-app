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
import com.example.data.BudgetTracker
import com.example.data.ConsumptionLearner
import com.example.data.ZadCentralBrain
import com.example.data.local.ZadDatabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * الملخص الصباحي الذكي — يجمع خلاصة العقل المركزي في إشعار واحد كل صباح:
 * الصحة المالية + قوة الصرف اليوم + تنبيه عاجل واحد (مخزون/ميزانية) + اقتراح وصفة لو فيه صنف هيضيع
 */
class MorningSummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ZadMorningWorker", "MorningSummaryWorker started!")
        try {
            val dao = ZadDatabase.getDatabase(applicationContext).zadDao()
            val inventory = dao.getAllInventory().first()
            val transactions = dao.getAllTransactions().first()
            val subscriptions = dao.getAllSubscriptions().first()
            val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            // 0 = سقف لسه مش متسجل. كان الافتراضي 3500، فملخص الصبح كان بيقول "قوة صرفك
            // النهاردة كذا" لمستخدم عمره ما حدد سقف — رقم مخترع في إشعار بيوصل كل يوم.
            val budget = prefs.getFloat("cached_budget", 0f).toDouble()

            val report = ZadCentralBrain.generateReport(
                context = applicationContext,
                inventory = inventory,
                transactions = transactions,
                subscriptions = subscriptions,
                budget = budget
            )

            val today = LocalDate.now()
            val greeting = when (today.dayOfWeek.value) {
                in 1..4 -> "صباح الخير! ☀️"
                else -> "صباح الجمعة المبارك! 🌿"
            }

            val body = buildString {
                // من غير سقف متسجل، dailySafeSpend مبني على صفر — رقم مالوش معنى. الإشعار
                // بيطلب السقف بدل ما يعرض رقم متحسب على معلومة ناقصة.
                val safePerDay = report.spendingPower.dailySafeSpend
                if (budget > 0 && safePerDay != null) {
                    append("قوة صرفك اليوم: ${com.example.data.CurrencyFormatter.format(applicationContext, safePerDay)} بأمان (${report.spendingPower.status})")
                } else {
                    append("لسه ما حددتش سقف الشهر — حدده عشان أقدر أقولك قوة صرفك اليومية")
                }

                val expiring = inventory.filter { item ->
                    item.expiryDate?.let {
                        try { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) in 0..1 } catch (e: Exception) { false }
                    } ?: false
                }
                if (expiring.isNotEmpty()) {
                    append(" • ${expiring.first().itemName} هيخلص بكرة 🍅")
                } else {
                    val urgentForecast = report.depletionForecasts.firstOrNull { it.predictedDaysLeft <= 1 }
                    if (urgentForecast != null) {
                        append(" • ${urgentForecast.itemName} متوقع يخلص اليوم/بكرة 📦")
                    } else if (report.healthScore < 50) {
                        append(" • صحتك المالية محتاجة انتباه (${report.healthScore}/100) ⚠️")
                    } else {
                        append(" • كل حاجة تمام، استمر 👏")
                    }
                }
            }

            showNotification("$greeting ملخص زاد اليومي", body)
            Log.d("ZadMorningWorker", "Morning summary sent — score=${report.healthScore}")

            // zad-brain (the server-side "brain" that emits insights to zad_insights,
            // read on Home/bell/voice) was fully built and deployed but never actually
            // invoked from the app — no cron, no client call, nothing. This daily worker
            // already runs once a day, so it's the natural place to trigger a real run.
            try {
                val userId = com.example.data.SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    com.example.data.SupabaseRepo.callEdgeFunction(
                        "zad-brain",
                        mapOf("user_id" to userId, "trigger" to "daily")
                    )
                    Log.d("ZadMorningWorker", "zad-brain daily run triggered")

                    // بعد ما العقل يشغّل، اسحب رؤاه الـ pending وحوّلها لإشعارات نظام + صوت.
                    // الـ worker ده دايماً كان بينبه لكنه مكانش بيقرا رؤى العقل نهائياً.
                    com.zad.agent.ZadAlertRouter.sync(applicationContext, userId)
                    Log.d("ZadMorningWorker", "zad_insights synced → notifications")
                }
            } catch (e: Exception) {
                Log.e("ZadMorningWorker", "zad-brain trigger failed: ${e.message}")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("ZadMorningWorker", "Morning summary failed", e)
            return Result.failure()
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_morning_summary"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "زاد — الملخص الصباحي", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(919191, notification)
    }
}
