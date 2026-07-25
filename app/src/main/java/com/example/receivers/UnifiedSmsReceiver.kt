package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.BankTransactionApplier
import com.example.data.MerchantCategoryOverrides
import com.example.data.SaBankParser
import com.example.data.SupabaseRepo
import com.example.data.SyncOutbox
import com.example.data.TxDeduplicator
import com.example.data.TxType
import com.example.data.ZadTransaction
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

/**
 * بيجمع أجزاء رسالة SMS الطويلة (multipart) بنفس المرسل في نص واحد كامل، بالترتيب اللي
 * وصلوا بيه — بدل ما كل جزء يتفحص/يتحلل لوحده كأنه رسالة كاملة (نص ناقص، تحليل غالباً غلط).
 * دالة صريحة (مش داخل onReceive) عشان تتختبر من غير الحاجة لـ android.telephony.SmsMessage.
 */
internal fun concatenateMultipartSms(parts: List<Pair<String, String>>): Map<String, String> {
    val grouped = linkedMapOf<String, MutableList<String>>()
    for ((sender, body) in parts) {
        if (sender.isBlank()) continue
        grouped.getOrPut(sender) { mutableListOf() }.add(body)
    }
    return grouped.mapValues { (_, bodies) -> bodies.joinToString(separator = "") }
        .filterValues { it.isNotBlank() }
}

class UnifiedSmsReceiver : BroadcastReceiver() {

    private val bankSmsSenders = listOf(
        "alrajhi", "rajhi", "snb", "alahli", "ncba",
        "riyad", "riyadh", "sabb", "alinma", "enmaa",
        "stcpay", "tabby", "tamara",
        // بنوك تركيا — أفضل معرفة، لسه محتاجة اختبار على SMS حقيقي
        "isbank", "garanti", "akbank", "yapikredi", "ziraat",
        "halkbank", "vakifbank", "qnb", "finansbank", "denizbank", "teb", "papara",
        // بنوك ومحافظ مصر — أفضل معرفة، لسه محتاجة اختبار على SMS حقيقي
        "cib", "qnbalahli", "nbe", "banquemisr", "alexbank", "hsbc",
        "vodafonecash", "instapay", "fawry"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            // رسايل البنوك الطويلة بتوصل multipart — كل جزء SmsMessage منفصل بنفس
            // originatingAddress. قبل كده كل جزء كان بيتفحص ويتحلل لوحده (نص ناقص، غالباً
            // بيفشل التحليل أو الأسوأ يتفهم غلط) — لازم نجمع كل الأجزاء بنفس المرسل أولاً.
            val parts = messages.map { (it.displayOriginatingAddress ?: "") to (it.displayMessageBody ?: "") }
            val bySender = concatenateMultipartSms(parts)
            for ((sender, text) in bySender) {
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
            "pay", "purchase", "amount",
            "TL", "₺", "TRY", "ödeme", "harcama", "bakiye", "kartınızdan",
            "EGP", "ج.م", "جنيه"
        )
        val containsKeyword = keywords.any { text.contains(it, ignoreCase = true) }
        val isLikelyBank = sender.length <= 12 && sender.matches(Regex("^[a-zA-Z]+[0-9]*$"))
        return isKnownBank || containsKeyword || isLikelyBank
    }

    private suspend fun processSms(context: Context, sender: String, text: String) {
        try {
            // فلترة الضجيج: OTP / مرفوضة / منتهية / عروض — تتجاهل نهائياً، مع تسجيل السبب
            SaBankParser.rejectionReason(text)?.let { reason ->
                SaBankParser.logRejection(context.applicationContext, reason, sender, text)
                return
            }

            val parsed = SaBankParser.detectAndParse(sender, "", text, context.applicationContext)
            if (parsed != null) {
                // منع الخصم المزدوج (نفس العملية توصل SMS + إشعار تطبيق البنك)، مع اسم التاجر
                // كمُميّز عشان عمليتين مختلفتين بنفس المبلغ والاتجاه في نفس النافذة الزمنية
                // (زي شرائين بنفس القيمة من محلين مختلفين) متتحسبش مكررة غلط
                if (!TxDeduplicator.isNewTransaction(context.applicationContext, parsed.amount, parsed.isExpense, parsed.merchantName ?: parsed.bankName, parsed.externalRef, parsed.confidence)) {
                    Log.d("UnifiedSmsReceiver", "Duplicate blocked: ${parsed.amount}")
                    return
                }
                val transaction = ZadTransaction(
                    title = parsed.title,
                    amount = parsed.amount,
                    isExpense = parsed.isExpense,
                    category = MerchantCategoryOverrides.get(context.applicationContext, parsed.merchantName) ?: parsed.category,
                    createdAt = Instant.now().toString()
                )
                BankTransactionApplier.apply(context.applicationContext, transaction, parsed.txType)

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
                // نفس مسار الـ AI fallback المستخدم في UnifiedBankListener — قبل كده مسار الـ SMS
                // كان بيستخدم regex-retry بس بينما مسار الإشعارات بيستخدم AI، فنفس الرسالة كانت
                // ممكن تتسجل من قناة وتتفقد من التانية حسب أي fallback مسكها
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
                    BankTransactionApplier.apply(context.applicationContext, transaction, txType)
                    Log.d("UnifiedSmsReceiver", "Fallback transaction added: ${transaction.title} - $amount")
                } else {
                    val aiParsed = com.example.data.ZadAiRepository.analyzeBankNotification(sender, text)
                    if (aiParsed != null) {
                        if (!TxDeduplicator.isNewTransaction(context.applicationContext, aiParsed.amount, aiParsed.isExpense)) return
                        BankTransactionApplier.apply(context.applicationContext, aiParsed)
                        Log.d("UnifiedSmsReceiver", "AI-fallback transaction saved: ${aiParsed.title}")
                    } else {
                        // شكلها رسالة بنكية (عدّت isFinancialSms) بس محدش من المسارات فهمها —
                        // بيانات خام لإضافة rule جديدة في bank_rules.json لاحقاً
                        SaBankParser.logRejection(context.applicationContext, SaBankParser.RejectReason.UNPARSED, sender, text)

                        // نفس منطق UnifiedBankListener: لو فيه مبلغ واضح، الأرجح معاملة حقيقية
                        // فشل تحليلها مؤقتاً (شبكة/AI) مش ضجيج — retry عبر TransactionSyncWorker
                        if (SaBankParser.extractAmount(text) != null) {
                            SyncOutbox.enqueueUnparsedNotification(context.applicationContext, sender, "", text)
                        }
                    }
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
