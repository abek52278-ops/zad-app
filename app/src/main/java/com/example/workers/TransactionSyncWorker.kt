package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SupabaseRepo
import com.example.data.SyncOutbox
import com.example.data.local.ZadDatabase

/**
 * مزامنة دورية: يسحب المعاملات والاشتراكات من Supabase لـ Room حتى لو التطبيق
 * مفتوحش لفترة — عشان لو المستخدم عنده أكتر من جهاز أو معاملة اتسجلت من مصدر تاني،
 * البادجت يبقى صحيح من أول ما يفتح التطبيق بدل ما ينتظر مزامنة يدوية (syncData()
 * في ZadViewModel بتشتغل بس وقت ما التطبيق يتفتح).
 */
class TransactionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "TransactionSyncWorker started")
        return try {
            // Push queued offline writes before pulling — a pending outbox entry represents
            // data that already exists locally and just failed its Supabase push earlier
            // (see SyncOutbox), so it should land before this same run reconciles from remote.
            SyncOutbox.flush(applicationContext)

            val dao = ZadDatabase.getDatabase(applicationContext).zadDao()

            val remoteTransactions = SupabaseRepo.getTransactions()
            if (remoteTransactions.isNotEmpty()) dao.insertTransactions(remoteTransactions)

            val remoteSubscriptions = SupabaseRepo.getSubscriptions()
            if (remoteSubscriptions.isNotEmpty()) dao.insertSubscriptions(remoteSubscriptions)

            Log.d(TAG, "TransactionSyncWorker finished — txs=${remoteTransactions.size}, subs=${remoteSubscriptions.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TransactionSyncWorker FAILED", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ZadWorker"
    }
}
