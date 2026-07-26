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
 * يبلّغ الأدمن لحظياً لما أي معاملة جديدة تتضاف لأي عضو في عيلته — نفس نمط RealtimeChatRepo،
 * بس هنا مش محتاجين محتوى الصف نفسه (RLS أصلاً بيقرر مين يشوف إيه)، مجرد إشارة "فيه جديد"
 * كافية عشان الشاشة تعيد تحميل ملخص مصاريف الأبناء.
 */
object RealtimeFamilySpendingRepo {

    private var currentChannel: RealtimeChannel? = null

    suspend fun subscribeToFamilyTransactions(familyId: String): Flow<Unit> {
        currentChannel?.unsubscribe()

        val channel = SupabaseRepo.client.channel("family_spending:$familyId")
        currentChannel = channel

        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "zad_transactions"
            filter("family_id", FilterOperator.EQ, familyId)
        }

        channel.subscribe()

        return changeFlow.map { }.catch { e ->
            android.util.Log.e("RealtimeFamilySpendingRepo", "Spending flow error (stream kept alive): ${e.message}")
        }
    }

    suspend fun unsubscribe() {
        currentChannel?.unsubscribe()
        currentChannel = null
    }
}
