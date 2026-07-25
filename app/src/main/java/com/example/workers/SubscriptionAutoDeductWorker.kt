package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SupabaseRepo
import com.example.data.ZadNotifier
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/**
 * البنك ممكن يبعت SMS/إشعار بنفس خصم الاشتراك ده، ولو وصل الاتنين مفيش أي حاجة كانت
 * بتمنع الخصم يتسجل مرتين (نفس شكل مشكلة تكرار استيراد الـ CSV). بيتفحص على شهر التجديد
 * + مبلغ (سماحية ٠.٠٠٥) + (عنوان متطابق جزئياً أو نفس مزوّد الخدمة).
 */
internal fun isSubscriptionAlreadyCharged(tx: ZadTransaction, sub: ZadSubscription, renewal: LocalDate): Boolean {
    val txDate = try { LocalDate.parse((tx.createdAt ?: "").take(10)) } catch (e: Exception) { null } ?: return false
    if (txDate.year != renewal.year || txDate.monthValue != renewal.monthValue) return false
    if (kotlin.math.abs(tx.amount - sub.amount) >= 0.005) return false
    return tx.title.contains(sub.title, ignoreCase = true) ||
        sub.title.contains(tx.title, ignoreCase = true) ||
        (!tx.merchantName.isNullOrBlank() && !sub.provider.isNullOrBlank() &&
            tx.merchantName.equals(sub.provider, ignoreCase = true))
}

/**
 * يفحص الاشتراكات الفعّالة اللي معلّم عليها auto_deduct يومياً — أي اشتراك
 * وصل تاريخ تجديده (renewal_date <= اليوم) بيتسجل كمعاملة مصروف تلقائياً
 * وبيتحرك تاريخ التجديد للدورة الجاية (شهري/سنوي حسب billing_cycle)، بدل
 * ما ينتظر المستخدم يدخّله يدوياً أو ينتظر بنك يبعت SMS.
 */
class SubscriptionAutoDeductWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SubscriptionAutoDeductWorker started")
        return try {
            val dao = ZadDatabase.getDatabase(applicationContext).zadDao()
            val subs = dao.getAllSubscriptions().first()
            val today = LocalDate.now()
            var deductedCount = 0
            // نجيبها مرة واحدة قبل اللوب — البنك ممكن يبعت SMS/إشعار بنفس خصم الاشتراك ده،
            // ولو وصل الاتنين مفيش أي حاجة بتمنع الخصم يتسجل مرتين (نفس شكل مشكلة CSV فوق)
            val existingTransactions = try { dao.getAllTransactionsOnce() } catch (e: Exception) { emptyList() }

            subs.filter { it.isActive && it.autoDeduct && !it.renewalDate.isNullOrBlank() }.forEach { sub ->
                val renewal = try { LocalDate.parse(sub.renewalDate!!.take(10)) } catch (e: Exception) { null } ?: return@forEach
                if (renewal.isAfter(today)) return@forEach

                val alreadyCharged = existingTransactions.any { tx -> isSubscriptionAlreadyCharged(tx, sub, renewal) }

                if (!alreadyCharged) {
                    Log.d(TAG, "Auto-deducting subscription: ${sub.title}, amount=${sub.amount}")
                    val transaction = ZadTransaction(
                        amount = sub.amount,
                        title = sub.title,
                        category = sub.category ?: "فواتير",
                        isExpense = true,
                        createdAt = Instant.now().toString(),
                        merchantName = sub.provider,
                        sourceType = "auto_subscription",
                        isVerified = true
                    )
                    dao.insertTransaction(transaction)
                    if (!SupabaseRepo.addTransaction(transaction)) {
                        Log.w(TAG, "addTransaction sync failed for ${sub.title} — queued for retry")
                        com.example.data.SyncOutbox.enqueueTransaction(applicationContext, transaction)
                    }
                    deductedCount++
                } else {
                    Log.d(TAG, "Skipping auto-deduct for ${sub.title} — bank already reported this charge this cycle")
                }

                val nextRenewal = when (sub.billingCycle?.uppercase()) {
                    "YEARLY", "ANNUAL" -> renewal.plusYears(1)
                    "WEEKLY" -> renewal.plusWeeks(1)
                    else -> renewal.plusMonths(1)
                }.toString()
                dao.insertSubscription(sub.copy(renewalDate = nextRenewal))
                try { SupabaseRepo.updateSubscriptionRenewalDate(sub.id, nextRenewal) } catch (e: Exception) {
                    Log.e(TAG, "updateSubscriptionRenewalDate sync failed for ${sub.title}: ${e.message}")
                }

                if (!alreadyCharged) {
                    ZadNotifier.send(
                        applicationContext,
                        "تم خصم ${sub.title} تلقائياً",
                        "${com.example.data.CurrencyFormatter.format(applicationContext, sub.amount)} خُصمت من ميزانيتك — التجديد الجاي ${nextRenewal.take(10)}"
                    )
                }
            }

            Log.d(TAG, "SubscriptionAutoDeductWorker finished — deducted $deductedCount subscription(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SubscriptionAutoDeductWorker FAILED", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ZadWorker"
    }
}
