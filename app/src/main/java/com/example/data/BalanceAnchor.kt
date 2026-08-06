package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase
import java.time.Instant
import kotlin.math.abs

private const val TAG = "BalanceAnchor"

/**
 * الرصيد التراكمي المحلي (دخل - مصروف على كل المعاملات، نفس حساب TransactionWidget)
 * بينحرف عن رصيد البنك الحقيقي لو أي إشعار اتفوت أو اتفسّر غلط، ومفيش قبل كده أي آلية
 * تصححه — SaBankParser كان بيرمي رقم "الرصيد: X" اللي البنك نفسه بيبعته بدل ما يستخدمه.
 *
 * الفكرة هنا: لما رسالة بنكية فيها رقم رصيد صريح، نقارنه بالمجموع المحسوب محلياً ونضيف
 * معاملة "تصحيح" لو الفرق معقول. مش بنستبدل الرقم المحسوب مباشرة (ده كان هيكسر أي تقرير
 * شهري مبني على مجموع المعاملات) — معاملة تصحيح بتفضل قابلة للتتبع (isVerified=false،
 * sourceType="balance_anchor") زي أي معاملة تانية، وبتظهر في السجل بدل ما تختفي جوه رقم.
 *
 * حماية أساسية (sanity check): التطبيق بيتابع معاملات من أكتر من بنك/محفظة على نفس
 * المستخدم، ورقم "الرصيد" في أي رسالة هو رصيد الحساب/المحفظة دي بس، مش المجموع الكلي
 * اللي التطبيق بيحسبه. لو انحراف كبير (رصيد حساب تاني تماماً، أو رقم اتفسّر غلط)، أفضل
 * نرفض التصحيح بدل ما نفسد الرصيد الكلي بمصادفة رقم بعيد — "الرفض أفضل من التخمين"،
 * نفس مبدأ SaBankParser الأساسي. سقف القبول: مضاعف من قيمة المعاملة اللي وصلت مع نفس
 * الرسالة (تغطي كام معاملة متفوتة بحجم مشابه)، مش رقم ثابت عبر كل العملات.
 */
object BalanceAnchor {

    private const val SANITY_MULTIPLIER = 3.0
    private const val MIN_DELTA_TO_CORRECT = 0.01

    /**
     * منطق القبول/الرفض المجرّد (بدون DB/شبكة) — قابل للاختبار مباشرة زي
     * TxDeduplicator.isAmountMatch. بيرجع مقدار التصحيح المطلوب (موجب = زيادة، سالب =
     * نقصان)، أو null لو مفيش انحراف يستاهل تصحيح أو الانحراف أكبر من سقف الأمان.
     */
    internal fun computeCorrection(computedBalance: Double, bankBalance: Double, transactionAmount: Double): Double? {
        val delta = (bankBalance - computedBalance).asMoney()
        if (abs(delta) < MIN_DELTA_TO_CORRECT) return null

        val sanityCap = abs(transactionAmount) * SANITY_MULTIPLIER
        if (abs(delta) > sanityCap) return null

        return delta
    }

    suspend fun reconcile(
        context: Context,
        bankBalance: Double,
        transactionAmount: Double,
        bankName: String,
        currency: String?
    ) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            val all = dao.getAllTransactionsOnce()
            val computed = all.sumOf { if (it.isExpense) -it.amount else it.amount }.asMoney()

            val delta = computeCorrection(computed, bankBalance, transactionAmount) ?: run {
                val rejectedDelta = (bankBalance - computed).asMoney()
                if (abs(rejectedDelta) >= MIN_DELTA_TO_CORRECT) {
                    Log.w(TAG, "delta=$rejectedDelta exceeds sanity cap for $bankName — skipping auto-correction (possible other account or misparse)")
                }
                return
            }

            val correction = ZadTransaction(
                title = "تصحيح رصيد تلقائي ($bankName)",
                amount = abs(delta),
                isExpense = delta < 0,
                category = "تصحيح رصيد",
                createdAt = Instant.now().toString(),
                bankName = bankName,
                sourceType = "balance_anchor",
                isVerified = false,
                currency = currency
            )
            BankTransactionApplier.apply(context, correction)
            Log.d(TAG, "Corrected local balance by $delta to match $bankName balance=$bankBalance")
        } catch (e: Exception) {
            Log.e(TAG, "reconcile() failed: ${e.message}")
        }
    }
}
