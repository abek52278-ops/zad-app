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

        val syncedToCloud = SupabaseRepo.addTransaction(classified)
        if (!syncedToCloud) {
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

        // The phone is the only place that can hear a bank notification, but it is
        // not a second decision-maker. Once the transaction reached the shared
        // database, wake the server brain with an event so Telegram, app insights,
        // commitments and forecasts all reason from the same updated snapshot.
        // Offline writes are deliberately deferred: the brain must never analyse a
        // transaction it cannot yet see in the source of truth.
        if (syncedToCloud) {
            notifySharedBrain(classified, txType)
        }
    }

    private suspend fun notifySharedBrain(transaction: ZadTransaction, txType: TxType?) {
        try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id ?: return
            val kind = transaction.txnKind ?: if (transaction.isExpense) "expense" else "income"
            val message = buildString {
                append("وصل إشعار بنك وتمت مزامنة معاملة موثوقة: ")
                append(transaction.title.take(80))
                append("، مبلغ ").append(transaction.amount)
                append("، النوع ").append(kind)
                if (txType != null) append("، تصنيف البنك ").append(txType.name)
                append(". حلّل الأثر على الميزانية والالتزامات والمخزون إن كان مناسباً، ونبّه فقط لو فيه إجراء مفيد.")
            }
            SupabaseRepo.callEdgeFunction(
                "zad-brain",
                mapOf("user_id" to userId, "trigger" to "event", "user_message" to message)
            )
        } catch (e: Exception) {
            // The money record is already safely synced. Brain analysis is best-effort
            // and must not cause a bank transaction to be retried or duplicated.
            Log.e(TAG, "Shared brain bank-event trigger failed: ${e.message}")
        }
    }

    // Task 19.4's one-time "سحبت X، دي مش محسوبة كمصروف لسه" notification used to fire
    // here on the first ATM withdrawal. Removed at the customer's request: they talk to
    // the bot anyway, so a one-shot explanation of the cash ledger arrives as noise rather
    // than as help. The cash ledger behaviour itself is untouched — a withdrawal is still
    // classified transfer→cash and still stays out of `spent` (BudgetMath.cashOnHand); the
    // only thing that went is the notification announcing it.
}
