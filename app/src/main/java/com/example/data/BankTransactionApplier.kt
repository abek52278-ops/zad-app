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

    suspend fun apply(context: Context, transaction: ZadTransaction) {
        val dao = ZadDatabase.getDatabase(context).zadDao()
        dao.insertTransaction(transaction)
        BankReadingStatus.recordParsed(context)

        if (!SupabaseRepo.addTransaction(transaction)) {
            Log.w(TAG, "Supabase sync failed (offline?) — queued for retry")
            SyncOutbox.enqueueTransaction(context, transaction)
        }

        if (transaction.isExpense) {
            BudgetTracker.deductExpense(context, transaction.amount, transaction.title, transaction.category ?: "أخرى")
        } else {
            BudgetTracker.addIncome(context, transaction.amount, transaction.title)
        }
    }
}
