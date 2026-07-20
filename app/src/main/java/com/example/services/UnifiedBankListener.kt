package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.*
import com.example.data.local.ZadDatabase
import com.example.data.SaBankParser
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Instant
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R

class UnifiedBankListener : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val trackedPackages = listOf(
        "com.alrajhi.bank", "com.snb", "com.riyadbank",
        "com.sabb", "com.alinma.bank",
        "com.stcpay", "com.tabby", "com.tamara",
        "com.fawry", "com.vodafone",
        "alrajhi", "snb", "riyad", "sabb", "alinma",
        "stcpay", "tabby", "tamara"
    )

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            val packageName = notification.packageName
            if (packageName == "android" || packageName.startsWith("com.android") ||
                packageName.startsWith("com.google")) return

            val extras = notification.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            if (isFinancialNotification(packageName, title, text)) {
                Log.d("UnifiedBankListener", "Financial notification: $packageName - $title")
                serviceScope.launch {
                    processAndTrackNotification(packageName, title, text)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun isFinancialNotification(packageName: String, title: String, text: String): Boolean {
        val isBankApp = trackedPackages.any { packageName.contains(it, ignoreCase = true) }
        val keywords = listOf(
            "ر.س", "رس", "ريال", "SAR", "خصم", "شراء", "دفع", "تم الدفع",
            "رصيد", "إيداع", "تحويل", "pay", "purchase", "amount",
            "مبلغ", "بطاقة", "مشتريات", "سحب", "راتب", "مرتب",
            "مدين", "دائن", "قسط", "فاتورة", "اشتراك"
        )
        return isBankApp || keywords.any {
            text.contains(it, ignoreCase = true) || title.contains(it, ignoreCase = true)
        }
    }

    private suspend fun processAndTrackNotification(packageName: String, title: String, text: String) {
        try {
            // فلترة الضجيج أولاً: OTP / مرفوضة / عروض — تتجاهل نهائياً
            if (SaBankParser.isNoise("$title $text")) return

            val parsed = SaBankParser.detectAndParse(packageName, title, text)

            if (parsed != null) {
                // منع الخصم المزدوج (نفس العملية توصل SMS + إشعار)
                if (!TxDeduplicator.isNewTransaction(applicationContext, parsed.amount, parsed.isExpense)) {
                    Log.d("UnifiedBankListener", "Duplicate blocked: ${parsed.amount}")
                    return
                }
                Log.d("UnifiedBankListener", "Bank parsed: ${parsed.bankName} - ${parsed.title} (${parsed.amount} SAR, type=${parsed.txType})")

                val transaction = ZadTransaction(
                    title = parsed.title,
                    amount = parsed.amount,
                    isExpense = parsed.isExpense,
                    category = parsed.category,
                    createdAt = Instant.now().toString()
                )

                val db = ZadDatabase.getDatabase(applicationContext)
                val dao = db.zadDao()
                dao.insertTransaction(transaction)

                try {
                    SupabaseRepo.addTransaction(transaction)
                } catch (e: Exception) {
                    Log.e("UnifiedBankListener", "Supabase sync failed (offline): ${e.message}")
                }

                // الحقن الدقيق حسب نوع العملية
                when (parsed.txType) {
                    TxType.REFUND -> BudgetTracker.applyRefund(applicationContext, parsed.amount, parsed.title, parsed.category)
                    else -> if (parsed.isExpense) {
                        BudgetTracker.deductExpense(applicationContext, parsed.amount, parsed.title, parsed.category)
                    } else {
                        BudgetTracker.addIncome(applicationContext, parsed.amount, parsed.title)
                    }
                }

                // Salary detection: ADD to budget (not replace)
                if (parsed.category == "الراتب" && !parsed.isExpense) {
                    try {
                        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                        if (userId != null) {
                            SupabaseRepo.sendAppNotification(
                                userId,
                                "تم إيداع الراتب!",
                                "تم إيداع راتبك بمبلغ ${parsed.amount} ر.س وزيادة الرصيد المتبقي 🥳"
                            )
                        }
                        showSystemNotification(
                            "تم إيداع الراتب!",
                            "تم إضافة ${parsed.amount} ر.س لرصيدك المتبقي في زاد 🥳"
                        )
                    } catch (e: Exception) {
                        Log.e("UnifiedBankListener", "Salary notification failed: ${e.message}")
                    }
                }

                // Subscription detection notification
                if (parsed.category == "الاشتراكات") {
                    try {
                        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                        if (userId != null) {
                            SupabaseRepo.sendAppNotification(
                                userId,
                                "تم خصم اشتراك",
                                "تم خصم اشتراك: ${parsed.title} بمبلغ ${parsed.amount} ر.س"
                            )
                        }
                        showSystemNotification(
                            "تنبيه اشتراك",
                            "تم خصم ${parsed.amount} ر.س لاشتراك ${parsed.title}"
                        )
                    } catch (e: Exception) {
                        Log.e("UnifiedBankListener", "Subscription notification failed: ${e.message}")
                    }
                }
            } else {
                val aiParsed = ZadAiRepository.analyzeBankNotification(title, text)
                if (aiParsed != null) {
                    // نفس الحماية من التكرار على مسار الـ AI
                    if (!TxDeduplicator.isNewTransaction(applicationContext, aiParsed.amount, aiParsed.isExpense)) return
                    val db = ZadDatabase.getDatabase(applicationContext)
                    val dao = db.zadDao()
                    dao.insertTransaction(aiParsed)
                    try { SupabaseRepo.addTransaction(aiParsed) } catch (e: Exception) {}
                    if (aiParsed.isExpense) {
                        BudgetTracker.deductExpense(applicationContext, aiParsed.amount, aiParsed.title, aiParsed.category ?: "أخرى")
                    } else {
                        BudgetTracker.addIncome(applicationContext, aiParsed.amount, aiParsed.title)
                    }
                    Log.d("UnifiedBankListener", "AI-fallback transaction saved: ${aiParsed.title}")
                }
            }
        } catch (e: Exception) {
            Log.e("UnifiedBankListener", "Error processing: ${e.message}")
        }
    }
    private fun showSystemNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_smart_alerts"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات زاد الذكية",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
