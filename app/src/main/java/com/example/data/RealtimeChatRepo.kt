package com.example.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.decodeFromJsonElement
import io.github.jan.supabase.realtime.decodeRecord

object RealtimeChatRepo {

    private var currentChannel: RealtimeChannel? = null

    suspend fun subscribeToChat(familyId: String): Flow<ChatMessage> {
        currentChannel?.unsubscribe()
        
        val channelName = "chat:family:$familyId"
        val channel = SupabaseRepo.client.channel(channelName)
        currentChannel = channel
        
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "chat_messages"
            filter("family_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, familyId)
        }
        
        channel.subscribe()
        
        // رسالة معطوبة واحدة ما توقفش الشات كله
        return changeFlow.mapNotNull { action ->
            try {
                action.decodeRecord<ChatMessage>()
            } catch (e: Exception) {
                android.util.Log.e("RealtimeChatRepo", "Skipped malformed message: ${e.message}")
                null
            }
        }.catch { e ->
            android.util.Log.e("RealtimeChatRepo", "Chat flow error (stream kept alive): ${e.message}")
        }
    }

    suspend fun unsubscribe() {
        currentChannel?.unsubscribe()
        currentChannel = null
    }

    suspend fun getChatHistory(familyId: String): List<ChatMessage> {
        return try {
            SupabaseRepo.client.postgrest["chat_messages"].select {
                filter { eq("family_id", familyId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<ChatMessage>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
