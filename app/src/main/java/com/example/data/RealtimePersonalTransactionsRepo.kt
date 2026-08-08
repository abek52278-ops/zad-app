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
 * لحظياً بيبلّغ أي INSERT جديد على zad_transactions لنفس المستخدم — مصدره ممكن يكون بنك
 * listener على جهاز تاني، أو معاملة اتسجلت عبر بوت تليجرام (@ZadhApp_bot)، مش بس نفس
 * الجهاز. من غيره، CashCard كانت بتفضل قديمة لحد أول فتح تطبيق أو TransactionSyncWorker
 * (كل ٣ ساعات). نفس نمط RealtimeFamilySpendingRepo، بس بفلتر user_id مش family_id.
 */
object RealtimePersonalTransactionsRepo {

    private var currentChannel: RealtimeChannel? = null

    suspend fun subscribeToOwnTransactions(userId: String): Flow<Unit> {
        currentChannel?.unsubscribe()

        val channel = SupabaseRepo.client.channel("own_transactions:$userId")
        currentChannel = channel

        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "zad_transactions"
            filter("user_id", FilterOperator.EQ, userId)
        }

        channel.subscribe()

        return changeFlow.map { }.catch { e ->
            android.util.Log.e("RealtimePersonalTxRepo", "Own-transactions flow error (stream kept alive): ${e.message}")
        }
    }

    suspend fun unsubscribe() {
        currentChannel?.unsubscribe()
        currentChannel = null
    }
}
