package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase
import io.github.jan.supabase.auth.auth
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
    private const val MAX_UNPARSED_ATTEMPTS = 3
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

    /** addInventory/upsertInventory push failed — retried as an upsert either way, since by the
     * time [flush] runs the row may or may not already exist remotely from an earlier partial
     * success. */
    suspend fun enqueueInventoryUpsert(context: Context, item: ZadInventory) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "inventory_upsert",
                    payloadJson = json.encodeToString(item),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueInventoryUpsert: queued '${item.itemName}' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueInventoryUpsert failed: ${e.message}")
        }
    }

    suspend fun enqueueInventoryDelete(context: Context, id: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "inventory_delete",
                    payloadJson = json.encodeToString(InventoryDeletePayload(id)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueInventoryDelete: queued '$id' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueInventoryDelete failed: ${e.message}")
        }
    }

    suspend fun enqueueInventoryObservation(context: Context, itemName: String, qty: Int, source: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "inventory_observation",
                    payloadJson = json.encodeToString(InventoryObservationPayload(itemName, qty, source)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueInventoryObservation: queued '$itemName' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueInventoryObservation failed: ${e.message}")
        }
    }

    /** يقيّد نص/عنوان بنكي فشل تحليله الفوري (SaBankParser + AI) — retry في [flush] القادم */
    suspend fun enqueueUnparsedNotification(context: Context, source: String, title: String, text: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "analyze_unparsed_notification",
                    payloadJson = json.encodeToString(UnparsedNotificationPayload(source, title, text)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueUnparsedNotification: queued '$title' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueUnparsedNotification failed: ${e.message}")
        }
    }

    suspend fun flush(context: Context) {
        val dao = ZadDatabase.getDatabase(context).zadDao()
        val pending = dao.getAllPendingSyncOps()
        if (pending.isEmpty()) return
        Log.d(TAG, "flush: ${pending.size} pending op(s)")
        pending.forEach { op ->
            try {
                when (op.opType) {
                    "add_transaction" -> {
                        if (SupabaseRepo.addTransaction(json.decodeFromString<ZadTransaction>(op.payloadJson))) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "inventory_upsert" -> {
                        if (SupabaseRepo.upsertInventory(json.decodeFromString<ZadInventory>(op.payloadJson))) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "inventory_delete" -> {
                        val payload = json.decodeFromString<InventoryDeletePayload>(op.payloadJson)
                        if (SupabaseRepo.deleteInventory(payload.id)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "inventory_observation" -> {
                        val payload = json.decodeFromString<InventoryObservationPayload>(op.payloadJson)
                        if (SupabaseRepo.recordInventoryObservation(payload.itemName, payload.qty, payload.source)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "analyze_unparsed_notification" -> {
                        val payload = json.decodeFromString<UnparsedNotificationPayload>(op.payloadJson)
                        val parsed = ZadAiRepository.analyzeBankNotification(payload.title, payload.text)
                        if (parsed != null && TxDeduplicator.isNewTransaction(context, parsed.amount, parsed.isExpense)) {
                            BankTransactionApplier.apply(context, parsed)
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: retry parsed '${parsed.title}' — cleared op ${op.id}")
                        } else if (op.attempts + 1 >= MAX_UNPARSED_ATTEMPTS) {
                            dao.deletePendingSyncOp(op.id)
                            Log.w(TAG, "flush: op ${op.id} gave up after ${op.attempts + 1} attempts — genuinely unparseable")
                            notifyGaveUp(payload)
                        } else {
                            dao.insertPendingSyncOp(op.copy(attempts = op.attempts + 1))
                            Log.w(TAG, "flush: op ${op.id} still unparsed (attempt ${op.attempts + 1}/$MAX_UNPARSED_ATTEMPTS) — left queued")
                        }
                    }
                    else -> {
                        Log.w(TAG, "flush: unknown opType '${op.opType}', dropping")
                        dao.deletePendingSyncOp(op.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "flush: op ${op.id} threw: ${e.message}")
            }
        }
    }

    /** بعد ما كل محاولات الفهم فشلت — تنبيه واحد بس (مش لكل محاولة) عشان المستخدم يراجعها يدوياً */
    private suspend fun notifyGaveUp(payload: UnparsedNotificationPayload) {
        try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id ?: return
            SupabaseRepo.sendAppNotification(
                userId,
                "معاملة بنكية محتاجة مراجعة",
                "وصل إشعار من ${payload.source} شكله عملية بنكية بس مقدرناش نفهمه تلقائياً. تقدر تضيفه يدوياً من شاشة المعاملات."
            )
        } catch (e: Exception) {
            Log.e(TAG, "notifyGaveUp failed: ${e.message}")
        }
    }
}
