package com.example.data

import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ZadBrainEngine {
    private const val TAG = "ZAD_BRAIN"

    @Serializable
    data class AiAction(
        val type: String,
        val payload: String,
        val reason: String
    )

    @Serializable
    data class AiActionList(val actions: List<AiAction>)

    suspend fun evaluateStateAndAct(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        chores: List<Chore>? = null,
        context: android.content.Context
    ) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "evaluateStateAndAct() started")

            val inventoryStr = inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" }
            val txStr = transactions.takeLast(10).joinToString(", ") { "${it.title}:${it.amount}" }

            val today = LocalDate.now()
            val expiringSoon = inventory.filter { item ->
                item.expiryDate?.let {
                    try {
                        val days = ChronoUnit.DAYS.between(today, LocalDate.parse(it))
                        days in 0..3
                    } catch (e: Exception) { false }
                } ?: false
            }
            val lowStock = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }

            val sysPrompt = """
                You are 'Zad', the central AI brain of a family management app.
                Current state:
                - Inventory: $inventoryStr
                - Expiring soon (≤3 days): ${expiringSoon.joinToString(", ") { it.itemName }}
                - Low stock: ${lowStock.joinToString(", ") { it.itemName }}
                - Recent transactions: $txStr
                
                Decide if any proactive action is needed.
                Rules:
                - If items are expiring soon, suggest recipes that use them
                - If stock is low, add to shopping list
                - If spending is unusual (compare with normal), alert
                - If no action needed, return empty actions
                
                Return ONLY valid JSON: {"actions":[{"type":"ADD_TO_SHOPPING|SUGGEST_MEAL|ALERT_BUDGET|NOTIFY_FAMILY","payload":"...","reason":"Arabic reason"}]}
            """.trimIndent()

            val userPrompt = "Analyze and act based on current state."

            val jsonResponse = ZadAiRepository.brainEvaluate(sysPrompt, userPrompt) ?: return@withContext
            val cleanJson = extractJsonBlock(jsonResponse)
            Log.d(TAG, "AI Decision: ${cleanJson.take(200)}")

            try {
                val actionList = Json { ignoreUnknownKeys = true }.decodeFromString<AiActionList>(cleanJson)
                actionList.actions.forEach { action ->
                    executeAction(action, context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse AI actions: ${e.message}")
            }
        }
    }

    private suspend fun executeAction(action: AiAction, context: android.content.Context) {
        Log.d(TAG, "Executing: ${action.type} - ${action.payload}")
        when (action.type) {
            "ADD_TO_SHOPPING" -> {
                val item = ZadShoppingItem(itemName = action.payload, quantity = 1, priority = "medium")
                try {
                    SupabaseRepo.addShoppingItem(item)
                    Log.d(TAG, "ADD_TO_SHOPPING -> ${action.payload} (${action.reason})")
                } catch (e: Exception) {
                    Log.e(TAG, "ADD_TO_SHOPPING failed: ${e.message}")
                }
            }
            "SUGGEST_MEAL" -> {
                // real system notification (works even if the app is closed) plus the in-app
                // app_notifications feed, so both surfaces this session established get it.
                ZadNotifier.send(context, "🍽️ اقتراح وجبة", action.payload)
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) SupabaseRepo.sendAppNotification(userId, "🍽️ اقتراح وجبة", action.payload)
                Log.d(TAG, "SUGGEST_MEAL -> notified ${action.payload}")
            }
            "ALERT_BUDGET" -> {
                ZadNotifier.send(context, "⚠️ تنبيه ميزانية", action.payload, androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) SupabaseRepo.sendAppNotification(userId, "⚠️ تنبيه ميزانية", action.payload)
                Log.d(TAG, "ALERT_BUDGET -> notified ${action.payload}")
            }
            "NOTIFY_FAMILY" -> {
                val familyId = SupabaseRepo.getMyFamilyMember()?.familyId
                if (familyId != null) {
                    SupabaseRepo.sendMessage(familyId, "zad_ai", action.payload)
                    Log.d(TAG, "NOTIFY_FAMILY -> sent to family $familyId")
                } else {
                    Log.w(TAG, "NOTIFY_FAMILY skipped: user has no family")
                }
            }
            else -> Log.w(TAG, "Unknown action: ${action.type}")
        }
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1 && end >= start) text.substring(start, end + 1) else text
    }
}
