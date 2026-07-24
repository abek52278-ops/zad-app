package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Retry queue for a Supabase write that failed while offline. Local-first paths already write
 * to Room immediately and only best-effort push to Supabase (SaBankParser's offline parse,
 * UnifiedBankListener/UnifiedSmsReceiver, ...) — before this, a failed push was just logged and
 * dropped, relying on some other sync elsewhere to reconcile it, which doesn't always happen.
 * TransactionSyncWorker (already a periodic WorkManager job) calls [flush] each run instead of
 * a new dedicated job.
 */
object SyncOutbox {
    private const val TAG = "SyncOutbox"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueTransaction(context: Context, transaction: ZadTransaction) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "add_transaction",
                    payloadJson = json.encodeToString(transaction),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueTransaction: queued '${transaction.title}' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueTransaction failed: ${e.message}")
        }
    }

    suspend fun flush(context: Context) {
        val dao = ZadDatabase.getDatabase(context).zadDao()
        val pending = dao.getAllPendingSyncOps()
        if (pending.isEmpty()) return
        Log.d(TAG, "flush: ${pending.size} pending op(s)")
        pending.forEach { op ->
            try {
                val synced = when (op.opType) {
                    "add_transaction" -> SupabaseRepo.addTransaction(json.decodeFromString<ZadTransaction>(op.payloadJson))
                    else -> {
                        Log.w(TAG, "flush: unknown opType '${op.opType}', dropping")
                        true // drop unrecognized ops rather than retry forever
                    }
                }
                if (synced) {
                    dao.deletePendingSyncOp(op.id)
                    Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                } else {
                    Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                }
            } catch (e: Exception) {
                Log.e(TAG, "flush: op ${op.id} threw: ${e.message}")
            }
        }
    }
}
