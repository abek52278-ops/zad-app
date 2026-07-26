package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase
import io.github.jan.supabase.auth.auth

/**
 * تطبيق معاملة بنكية اتفهمت (Room + Supabase + حقن البادجت) — نفس الخطوات سواء المعاملة
 * جاية فوري من UnifiedBankListener أو من retry مؤجل في SyncOutbox.flush()، كانت مكررة
 * بين الاتنين قبل كده.
 */
object BankTransactionApplier {

    private const val TAG = "BankTransactionApplier"

    /**
     * [txType] اختياري — لازم بس لو المعاملة ممكن تكون REFUND (بتترد للبادجت بدل ما تتخصم
     * منه). المسارات اللي مفيهاش نوع صريح (AI fallback) سايباه null، فبيتعامل كمصروف/دخل عادي.
     *
     * Task 19.2 — هنا التصنيف الوحيد لسحب ATM كـ transfer/cash بدل expense (بق السحب
     * النقدي من 19.1). كل نوع تاني (بما فيهم AI fallback) بيسيب txn_kind على الـ default
     * المشتق من isExpense في ZadTransaction نفسها — مالوش داعي يتصلح هنا.
     */
    suspend fun apply(context: Context, transaction: ZadTransaction, txType: TxType? = null) {
        val classified = if (txType == TxType.WITHDRAWAL) {
            transaction.copy(txnKind = "transfer", transferTo = "cash")
        } else {
            transaction
        }

        val dao = ZadDatabase.getDatabase(context).zadDao()
        dao.insertTransaction(classified)
        BankReadingStatus.recordParsed(context)

        if (!SupabaseRepo.addTransaction(classified)) {
            Log.w(TAG, "Supabase sync failed (offline?) — queued for retry")
            SyncOutbox.enqueueTransaction(context, classified)
        }

        when (txType) {
            TxType.REFUND -> BudgetTracker.applyRefund(context, classified.amount, classified.title, classified.category ?: "أخرى")
            else -> if (classified.isExpense) {
                BudgetTracker.deductExpense(context, classified.amount, classified.title, classified.category ?: "أخرى")
            } else {
                BudgetTracker.addIncome(context, classified.amount, classified.title)
            }
        }
        // Task 19.0 — تنبيه تخطي 75/90/100% من الإجمالي، محسوب لحظياً بعد إدراج المعاملة
        BudgetTracker.checkOverallBudgetThreshold(context)

        if (txType == TxType.WITHDRAWAL) {
            maybeShowCashEducationOnce(context, classified.amount)
        }
    }

    private const val PREFS_CASH_EDU = "zad_cash_education"
    private const val KEY_SHOWN_ONCE = "shown_once"

    /**
     * Task 19.4 — "أول سحب ATM، رسالة تعليمية واحدة، مرة واحدة طول العمر" (EPIC_1_4.md).
     * نفس مسار sendAppNotification المستخدم أصلاً في UnifiedSmsReceiver لإشعار الراتب —
     * ده مش المسار المثالي (ZadAlertRouter من المفروض يبقى نقطة العبور الوحيدة لكل
     * إشعار، حسب معيار Task 24)، لكنه المسار الموجود فعلياً لإشعارات مبنية على SMS بنكي،
     * ومش هدف هذا التاسك إعادة هيكلة الإشعارات كلها — Task 24 هو اللي هيوحّدها.
     */
    private suspend fun maybeShowCashEducationOnce(context: Context, amount: Double) {
        val prefs = context.getSharedPreferences(PREFS_CASH_EDU, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHOWN_ONCE, false)) return
        prefs.edit().putBoolean(KEY_SHOWN_ONCE, true).apply()
        try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id ?: return
            SupabaseRepo.sendAppNotification(
                userId,
                "سحبت ${CurrencyFormatter.format(context, amount)}",
                "دي مش محسوبة كمصروف لسه — لما تصرف منها قوللي. ولو نسيت، هسألك آخر الأسبوع."
            )
        } catch (e: Exception) {
            Log.e(TAG, "maybeShowCashEducationOnce() FAILED: ${e.message}")
        }
    }
}
