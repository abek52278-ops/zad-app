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
     */
    suspend fun apply(context: Context, transaction: ZadTransaction, txType: TxType? = null) {
        val dao = ZadDatabase.getDatabase(context).zadDao()
        dao.insertTransaction(transaction)
        BankReadingStatus.recordParsed(context)

        if (!SupabaseRepo.addTransaction(transaction)) {
            Log.w(TAG, "Supabase sync failed (offline?) — queued for retry")
            SyncOutbox.enqueueTransaction(context, transaction)
        }

        when (txType) {
            TxType.REFUND -> BudgetTracker.applyRefund(context, transaction.amount, transaction.title, transaction.category ?: "أخرى")
            else -> if (transaction.isExpense) {
                BudgetTracker.deductExpense(context, transaction.amount, transaction.title, transaction.category ?: "أخرى")
            } else {
                BudgetTracker.addIncome(context, transaction.amount, transaction.title)
            }
        }
        // Task 19.0 — تنبيه تخطي 75/90/100% من الإجمالي، محسوب لحظياً بعد إدراج المعاملة
        BudgetTracker.checkOverallBudgetThreshold(context)
    }
}
