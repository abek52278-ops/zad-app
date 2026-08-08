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

    /** setMonthlyLimit push failed after updateBudget already committed locally — retry in [flush]. */
    suspend fun enqueueBudgetUpdate(context: Context, limit: Double) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "budget_update",
                    payloadJson = json.encodeToString(BudgetUpdatePayload(limit)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueBudgetUpdate: queued limit=$limit for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueBudgetUpdate failed: ${e.message}")
        }
    }

    /** syncMarketProfile's own 2-attempt in-process retry failed — this survives past that
     * (process death, longer outage) via the same durable queue [flush] already drains. */
    suspend fun enqueueMarketProfile(context: Context, currencyCode: String, countryCode: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "market_profile_update",
                    payloadJson = json.encodeToString(MarketProfilePayload(currencyCode, countryCode)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueMarketProfile: queued currency=$currencyCode country=$countryCode for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueMarketProfile failed: ${e.message}")
        }
    }

    /** addDebt push failed — retried as an insert; a duplicate insert on a retried-but-actually-
     * succeeded earlier attempt is the accepted tradeoff (same as inventory_upsert choosing
     * upsert over insert), since debt ids are client-generated UUIDs so a genuine dup would
     * conflict on the primary key rather than silently double-count. */
    suspend fun enqueueDebtUpsert(context: Context, debt: ZadDebt) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "debt_upsert",
                    payloadJson = json.encodeToString(debt),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueDebtUpsert: queued '${debt.name}' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueDebtUpsert failed: ${e.message}")
        }
    }

    suspend fun enqueueDebtDelete(context: Context, id: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "debt_delete",
                    payloadJson = json.encodeToString(DebtDeletePayload(id)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueDebtDelete: queued '$id' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueDebtDelete failed: ${e.message}")
        }
    }

    suspend fun enqueueDebtBalanceUpdate(context: Context, id: String, newBalance: Double) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "debt_balance_update",
                    payloadJson = json.encodeToString(DebtBalanceUpdatePayload(id, newBalance)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueDebtBalanceUpdate: queued '$id' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueDebtBalanceUpdate failed: ${e.message}")
        }
    }

    /** updateFamilyMemberBalance push failed — real money (chore/challenge rewards, approved
     * spend requests), so this can't just be logged and dropped like the lower-stakes ops. */
    suspend fun enqueueFamilyBalanceUpdate(context: Context, memberId: String, newBalance: Double) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "family_balance_update",
                    payloadJson = json.encodeToString(FamilyBalanceUpdatePayload(memberId, newBalance)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueFamilyBalanceUpdate: queued '$memberId' for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueFamilyBalanceUpdate failed: ${e.message}")
        }
    }

    /** avatarUri الفعلي (رابط storage عام) — الملف فعلاً موجود في bucket "avatars"، بس كتابة
     * zad_users.avatar_uri فشلت. retry بيعيد الكتابة دي بس، مش رفع الصورة تاني. */
    suspend fun enqueueAvatarUpdate(context: Context, avatarUri: String) {
        try {
            val dao = ZadDatabase.getDatabase(context).zadDao()
            dao.insertPendingSyncOp(
                PendingSyncOp(
                    opType = "avatar_update",
                    payloadJson = json.encodeToString(AvatarUpdatePayload(avatarUri)),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            Log.d(TAG, "enqueueAvatarUpdate: queued avatar_uri for retry")
        } catch (e: Exception) {
            Log.e(TAG, "enqueueAvatarUpdate failed: ${e.message}")
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
                    "budget_update" -> {
                        val payload = json.decodeFromString<BudgetUpdatePayload>(op.payloadJson)
                        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                        if (userId != null && SupabaseRepo.setMonthlyLimit(userId, payload.limit)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "market_profile_update" -> {
                        val payload = json.decodeFromString<MarketProfilePayload>(op.payloadJson)
                        if (SupabaseRepo.syncMarketProfile(payload.currencyCode, payload.countryCode)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "debt_upsert" -> {
                        if (SupabaseRepo.addDebt(json.decodeFromString<ZadDebt>(op.payloadJson))) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "debt_delete" -> {
                        val payload = json.decodeFromString<DebtDeletePayload>(op.payloadJson)
                        if (SupabaseRepo.deleteDebt(payload.id)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "debt_balance_update" -> {
                        val payload = json.decodeFromString<DebtBalanceUpdatePayload>(op.payloadJson)
                        if (SupabaseRepo.updateDebtRemainingBalance(payload.id, payload.newBalance)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "family_balance_update" -> {
                        val payload = json.decodeFromString<FamilyBalanceUpdatePayload>(op.payloadJson)
                        if (SupabaseRepo.updateFamilyMemberBalance(payload.memberId, payload.newBalance)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "avatar_update" -> {
                        val payload = json.decodeFromString<AvatarUpdatePayload>(op.payloadJson)
                        if (SupabaseRepo.updateUserProfile(null, payload.avatarUri)) {
                            dao.deletePendingSyncOp(op.id)
                            Log.d(TAG, "flush: synced+cleared op ${op.id} (${op.opType})")
                        } else {
                            Log.w(TAG, "flush: op ${op.id} (${op.opType}) still failing — left queued for next run")
                        }
                    }
                    "analyze_unparsed_notification" -> {
                        val payload = json.decodeFromString<UnparsedNotificationPayload>(op.payloadJson)
                        val parsed = ZadAiRepository.analyzeBankNotification(payload.title, payload.text)
                        if (parsed != null && TxDeduplicator.isNewTransaction(context, parsed.amount, parsed.isExpense, parsed.merchantName ?: parsed.title)) {
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
