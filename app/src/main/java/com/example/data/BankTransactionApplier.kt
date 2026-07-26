package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase

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
    }
}
