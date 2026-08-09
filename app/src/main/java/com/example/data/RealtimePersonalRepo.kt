package com.example.data

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * لحظياً بيبلّغ أي INSERT/UPDATE/DELETE على zad_transactions/zad_inventory/zad_pharmacy_items
 * لنفس المستخدم — مصدره ممكن يكون بنك listener على جهاز تاني، بوت تليجرام (@ZadhApp_bot)،
 * أو تعديل/حذف لاحق، مش بس نفس الجهاز. من غيره، الكروت المعتمدة على البيانات دي كانت بتفضل
 * قديمة لحد أول فتح تطبيق أو TransactionSyncWorker (كل ٣ ساعات). نفس نمط
 * RealtimeFamilySpendingRepo، بس بفلتر user_id مش family_id، وبالنوع الأساسي PostgresAction
 * (مش .Insert) عشان يسمع الثلاث أنواع في تدفق واحد.
 *
 * كل الثلاث جداول عندها RLS بنفس الشكل (auth.uid() = user_id)، مفيش عمود family_id —
 * يعني ده مزامنة لحظية لنفس الحساب بين أجهزته، مش مشاركة بيانات بين أفراد عيلة مختلفين
 * (ده محتاج family_id-scoped RLS مش موجود على الجداول دي حالياً).
 */
object RealtimePersonalRepo {

    private var transactionsChannel: RealtimeChannel? = null
    private var inventoryChannel: RealtimeChannel? = null
    private var pharmacyChannel: RealtimeChannel? = null

    suspend fun subscribeToOwnTransactions(userId: String): Flow<Unit> = subscribe(
        channelName = "own_transactions:$userId", table = "zad_transactions", userId = userId,
        existing = transactionsChannel, store = { transactionsChannel = it }
    )

    suspend fun subscribeToOwnInventory(userId: String): Flow<Unit> = subscribe(
        channelName = "own_inventory:$userId", table = "zad_inventory", userId = userId,
        existing = inventoryChannel, store = { inventoryChannel = it }
    )

    suspend fun subscribeToOwnPharmacyItems(userId: String): Flow<Unit> = subscribe(
        channelName = "own_pharmacy:$userId", table = "zad_pharmacy_items", userId = userId,
        existing = pharmacyChannel, store = { pharmacyChannel = it }
    )

    private suspend fun subscribe(
        channelName: String,
        table: String,
        userId: String,
        existing: RealtimeChannel?,
        store: (RealtimeChannel) -> Unit
    ): Flow<Unit> {
        existing?.unsubscribe()

        val channel = SupabaseRepo.client.channel(channelName)
        store(channel)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            this.table = table
            filter("user_id", FilterOperator.EQ, userId)
        }
        channel.subscribe()

        return changeFlow.map { }.catch { e ->
            android.util.Log.e("RealtimePersonalRepo", "$table flow error (stream kept alive): ${e.message}")
        }
    }

    suspend fun unsubscribeAll() {
        transactionsChannel?.unsubscribe()
        inventoryChannel?.unsubscribe()
        pharmacyChannel?.unsubscribe()
        transactionsChannel = null
        inventoryChannel = null
        pharmacyChannel = null
    }
}
