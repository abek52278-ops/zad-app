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
    private var shoppingChannel: RealtimeChannel? = null
    private var subscriptionsChannel: RealtimeChannel? = null
    private var obligationsChannel: RealtimeChannel? = null
    private var debtsChannel: RealtimeChannel? = null
    private var maintenanceChannel: RealtimeChannel? = null
    private var userChannel: RealtimeChannel? = null
    private var transactionProposalsChannel: RealtimeChannel? = null

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

    suspend fun subscribeToOwnShoppingList(userId: String): Flow<Unit> = subscribe(
        channelName = "own_shopping:$userId", table = "zad_shopping_list", userId = userId,
        existing = shoppingChannel, store = { shoppingChannel = it }
    )

    suspend fun subscribeToOwnSubscriptions(userId: String): Flow<Unit> = subscribe(
        channelName = "own_subscriptions:$userId", table = "zad_subscriptions", userId = userId,
        existing = subscriptionsChannel, store = { subscriptionsChannel = it }
    )

    suspend fun subscribeToOwnObligations(userId: String): Flow<Unit> = subscribe(
        channelName = "own_obligations:$userId", table = "zad_obligations", userId = userId,
        existing = obligationsChannel, store = { obligationsChannel = it }
    )

    suspend fun subscribeToOwnDebts(userId: String): Flow<Unit> = subscribe(
        channelName = "own_debts:$userId", table = "zad_debts", userId = userId,
        existing = debtsChannel, store = { debtsChannel = it }
    )

    suspend fun subscribeToOwnMaintenanceItems(userId: String): Flow<Unit> = subscribe(
        channelName = "own_maintenance:$userId", table = "zad_maintenance_items", userId = userId,
        existing = maintenanceChannel, store = { maintenanceChannel = it }
    )

    suspend fun subscribeToOwnUserProfile(userId: String): Flow<Unit> = subscribe(
        channelName = "own_user:$userId", table = "zad_users", column = "id", value = userId,
        existing = userChannel, store = { userChannel = it }
    )

    // بند 32.2 — zad_transaction_proposals متضافة لـsupabase_realtime من زمان
    // (20260820004901_transaction_proposals.sql) بتعليق بيوعد إن كارت البنك يتحدث لحظيًا،
    // بس مفيش حد استخدم الاشتراك ده على الكلاينت — الكارت كان بيتحدث بس عند فتح الشاشة
    // أو ON_RESUME. نفس النمط بالظبط، مفيش حاجة جديدة.
    suspend fun subscribeToOwnTransactionProposals(userId: String): Flow<Unit> = subscribe(
        channelName = "own_transaction_proposals:$userId", table = "zad_transaction_proposals", userId = userId,
        existing = transactionProposalsChannel, store = { transactionProposalsChannel = it }
    )

    private suspend fun subscribe(
        channelName: String,
        table: String,
        userId: String,
        existing: RealtimeChannel?,
        store: (RealtimeChannel) -> Unit
    ): Flow<Unit> = subscribe(channelName, table, "user_id", userId, existing, store)

    private suspend fun subscribe(
        channelName: String,
        table: String,
        column: String,
        value: String,
        existing: RealtimeChannel?,
        store: (RealtimeChannel) -> Unit
    ): Flow<Unit> {
        existing?.unsubscribe()

        val channel = SupabaseRepo.client.channel(channelName)
        store(channel)

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            this.table = table
            filter(column, FilterOperator.EQ, value)
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
        shoppingChannel?.unsubscribe()
        subscriptionsChannel?.unsubscribe()
        obligationsChannel?.unsubscribe()
        debtsChannel?.unsubscribe()
        maintenanceChannel?.unsubscribe()
        userChannel?.unsubscribe()
        transactionProposalsChannel?.unsubscribe()
        transactionsChannel = null
        inventoryChannel = null
        pharmacyChannel = null
        shoppingChannel = null
        subscriptionsChannel = null
        obligationsChannel = null
        debtsChannel = null
        maintenanceChannel = null
        userChannel = null
        transactionProposalsChannel = null
    }
}
