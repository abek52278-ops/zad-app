package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.BudgetTracker
import com.example.data.SaBankParser
import com.example.data.SupabaseRepo
import com.example.data.TxDeduplicator
import com.example.data.TxType
import com.example.data.ZadTransaction
import com.example.data.local.ZadDatabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.R

class UnifiedSmsReceiver : BroadcastReceiver() {

    private val bankSmsSenders = listOf(
        "alrajhi", "rajhi", "snb", "alahli", "ncba",
        "riyad", "riyadh", "sabb", "alinma", "enmaa",
        "stcpay", "tabby", "tamara"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: continue
                val text = sms.displayMessageBody ?: continue

                if (isFinancialSms(sender, text)) {
                    Log.d("UnifiedSmsReceiver", "Financial SMS from: $sender")
                    // Use goAsync() — correct pattern for coroutines inside BroadcastReceiver
                    // This holds the wake lock while processing and releases it when done
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            processSms(context, sender, text)
                        } finally {
                            pendingResult.finish()  // always release, even on error
                        }
                    }
                }
            }
        }
    }

    private fun isFinancialSms(sender: String, text: String): Boolean {
        val isKnownBank = bankSmsSenders.any { sender.contains(it, ignoreCase = true) }
        val keywords = listOf(
            "ر.س", "رس", "ريال", "SAR",
            "خصم", "شراء", "دفع", "تم الدفع", "رصيد",
            "إيداع", "تحويل", "مبلغ", "بطاقة", "مشتريات",
            "pay", "purchase", "amount"
        )
        val containsKeyword = keywords.any { text.contains(it, ignoreCase = true) }
        val isLikelyBank = sender.length <= 12 && sender.matches(Regex("^[a-zA-Z]+[0-9]*$"))
        return isKnownBank || containsKeyword || isLikelyBank
    }

    private suspend fun processSms(context: Context, sender: String, text: String) {
        try {
            // فلترة الضجيج: OTP / مرفوضة / عروض — تتجاهل نهائياً
            if (SaBankParser.isNoise(text)) return

            val parsed = SaBankParser.detectAndParse(sender, "", text)
            if (parsed != null) {
                // منع الخصم المزدوج (نفس العملية توصل SMS + إشعار تطبيق البنك)
                if (!TxDeduplicator.isNewTransaction(context.applicationContext, parsed.amount, parsed.isExpense)) {
                    Log.d("UnifiedSmsReceiver", "Duplicate blocked: ${parsed.amount}")
                    return
                }
                val transaction = ZadTransaction(
                    title = parsed.title,
                    amount = parsed.amount,
                    isExpense = parsed.isExpense,
                    category = parsed.category,
                    createdAt = Instant.now().toString()
                )
                val db = ZadDatabase.getDatabase(context.applicationContext)
                db.zadDao().insertTransaction(transaction)
                try { SupabaseRepo.addTransaction(transaction) } catch (e: Exception) {}

                // الحقن الدقيق حسب نوع العملية
                when (parsed.txType) {
                    TxType.REFUND -> BudgetTracker.applyRefund(context.applicationContext, parsed.amount, parsed.title, parsed.category)
                    else -> if (parsed.isExpense) {
                        BudgetTracker.deductExpense(context.applicationContext, parsed.amount, parsed.title, parsed.category)
                    } else {
                        BudgetTracker.addIncome(context.applicationContext, parsed.amount, parsed.title)
                    }
                }

                if (parsed.category == "الراتب") {
                    try {
                        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                        if (userId != null) {
                            SupabaseRepo.sendAppNotification(
                                userId,
                                "تم إيداع الراتب (عبر SMS)!",
                                "تم إيداع راتبك بمبلغ ${com.example.data.CurrencyFormatter.format(context, parsed.amount)} وزيادة الرصيد المتبقي 🥳"
                            )
                        }
                        showSystemNotification(context, "تم إيداع الراتب!", "تم إضافة ${com.example.data.CurrencyFormatter.format(context, parsed.amount)} لرصيدك المتبقي في زاد 🥳")
                    } catch (e: Exception) {
                        Log.e("UnifiedSmsReceiver", "Salary notification failed: ${e.message}")
                    }
                } else if (parsed.category == "الاشتراكات") {
                    showSystemNotification(context, "تنبيه اشتراك", "تم خصم ${com.example.data.CurrencyFormatter.format(context, parsed.amount)} لاشتراك ${parsed.title}")
                }
                
                Log.d("UnifiedSmsReceiver", "Parsed SMS: ${parsed.bankName} - ${parsed.title} (${parsed.amount} SAR)")
            } else {
                // Fallback آمن: لازم مبلغ صحيح + نوع عملية صريح — غير كده نتجاهل
                val amount = SaBankParser.extractAmount(text)
                val txType = SaBankParser.detectTxType(text)

                if (amount != null && txType != null) {
                    if (!TxDeduplicator.isNewTransaction(context.applicationContext, amount, txType.isExpense)) return
                    val category = SaBankParser.classify(text, txType)
                    val transaction = ZadTransaction(
                        title = "$sender: ${txType.arabicLabel}",
                        amount = amount,
                        isExpense = txType.isExpense,
                        category = category,
                        createdAt = Instant.now().toString()
                    )
                    val db = ZadDatabase.getDatabase(context.applicationContext)
                    db.zadDao().insertTransaction(transaction)
                    try { SupabaseRepo.addTransaction(transaction) } catch (e: Exception) {}
                    if (txType.isExpense) {
                        BudgetTracker.deductExpense(context.applicationContext, amount, transaction.title, category)
                    } else {
                        BudgetTracker.addIncome(context.applicationContext, amount, transaction.title)
                    }
                    Log.d("UnifiedSmsReceiver", "Fallback transaction added: ${transaction.title} - $amount")
                }
            }
        } catch (e: Exception) {
            Log.e("UnifiedSmsReceiver", "Error processing SMS: ${e.message}")
        }
    }

    private fun showSystemNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zad_smart_alerts"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات زاد الذكية",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
