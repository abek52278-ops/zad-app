package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

private const val TAG_REPO = "ZadAiRepo"
private const val CENTRAL_FUNCTION = "zad-core-intelligence"

private val json = Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
data class AiParsedTransaction(
    val amount: Double,
    val title: String,
    val is_expense: Boolean,
    val category: String
)

@kotlinx.serialization.Serializable
data class AiParsedReceiptItem(
    val name: String,
    val price: Double,
    val quantity: Double = 1.0,
    val unit: String = "قطعة",
    val category: String = "عام"
)

@kotlinx.serialization.Serializable
data class AiParsedInventoryItem(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "قطعة",
    val category: String = "عام"
)

@kotlinx.serialization.Serializable
data class AiInventoryScanResult(
    val items: List<AiParsedInventoryItem>
)

@kotlinx.serialization.Serializable
data class AiParsedReceipt(
    val total: Double,
    val category: String,
    val storeName: String,
    val items: List<AiParsedReceiptItem>
)

@kotlinx.serialization.Serializable
data class AiInsight(
    val title: String,
    val description: String,
    val type: String
)

@kotlinx.serialization.Serializable
data class DetectedSubscription(
    val name: String,
    val amount: Double,
    val frequency: String,
    val confidence: Double,
    @kotlinx.serialization.SerialName("next_billing_date") val nextBillingDate: String
)

@kotlinx.serialization.Serializable
data class GrocerySuggestion(
    val name: String,
    val quantity: String,
    val reason: String
)

object ZadAiRepository {
    var geminiApiKey: String? = null

    suspend fun analyzeReceipt(bitmap: Bitmap): AiParsedReceipt? {
        // Use direct Gemini API if key is available
        geminiApiKey?.takeIf { it.isNotEmpty() }?.let { apiKey ->
            val result = ZadAiGeminiClient.analyzeReceipt(apiKey, bitmap)
            Log.d(TAG_REPO, "analyzeReceipt Gemini result: $result")
            if (result != null) return result
        }

        // Fallback to Edge function — Edge Function returns receipt fields directly
        return callVisionEdge("analyze_receipt", bitmap) { response ->
            Log.d(TAG_REPO, "analyzeReceipt Edge RAW response: $response")
            @Suppress("UNCHECKED_CAST")
            val itemsRaw = response["items"] as? List<*>
            val total = (response["total"] as? Number)?.toDouble() ?: 0.0
            val category = response["category"] as? String ?: ""
            val storeName = response["storeName"] as? String ?: ""
            val items = itemsRaw?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                AiParsedReceiptItem(
                    name = map["name"] as? String ?: return@mapNotNull null,
                    price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                    quantity = (map["quantity"] as? Number)?.toDouble() ?: 1.0,
                    unit = map["unit"] as? String ?: "قطعة",
                    category = map["category"] as? String ?: "عام"
                )
            } ?: emptyList()
            AiParsedReceipt(total = total, category = category, storeName = storeName, items = items)
        } as? AiParsedReceipt
    }

    suspend fun analyzeInventoryImage(bitmap: Bitmap): AiInventoryScanResult? {
        // Use direct Gemini API if key is available
        geminiApiKey?.takeIf { it.isNotEmpty() }?.let { apiKey ->
            val result = ZadAiGeminiClient.analyzeInventoryImage(apiKey, bitmap)
            Log.d(TAG_REPO, "analyzeInventoryImage Gemini result: $result")
            if (result != null) return result
        }

        // Fallback to Edge function — Edge Function returns {items:[...]} directly
        return callVisionEdge("analyze_inventory_image", bitmap) { response ->
            Log.d(TAG_REPO, "analyzeInventoryImage Edge RAW response: $response")
            @Suppress("UNCHECKED_CAST")
            val itemsRaw = response["items"] as? List<*>
            if (itemsRaw != null) {
                val items = itemsRaw.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    AiParsedInventoryItem(
                        name = map["name"] as? String ?: return@mapNotNull null,
                        quantity = (map["quantity"] as? Number)?.toDouble() ?: 1.0,
                        unit = map["unit"] as? String ?: "قطعة",
                        category = map["category"] as? String ?: "عام"
                    )
                }
                Log.d(TAG_REPO, "analyzeInventoryImage parsed ${items.size} items")
                AiInventoryScanResult(items)
            } else null
        } as? AiInventoryScanResult
    }

    suspend fun suggestMeals(inventory: List<ZadInventory>): String {
        val itemsList = if (inventory.isEmpty()) "لا يوجد مخزون حاليا"
        else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val response = callAction("meal_suggestions", mapOf("items" to itemsList))
        return response["text"] as? String ?: "لم أتمكن من إيجاد اقتراحات حالياً."
    }

    suspend fun getRecipeDetails(recipeName: String, inventory: List<ZadInventory>): String {
        val itemsList = if (inventory.isEmpty()) "لا يوجد مخزون حاليا"
        else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val response = callAction("recipe_details", mapOf("recipe_name" to recipeName, "inventory" to itemsList))
        return response["text"] as? String ?: "لم أتمكن من إيجاد تفاصيل الوصفة حالياً."
    }

    suspend fun suggestGroceries(inventory: List<ZadInventory>, familySize: Int = 4): List<GrocerySuggestion> {
        val itemsList = if (inventory.isEmpty()) "لا يوجد"
        else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val response = callAction("grocery_suggestions", mapOf("inventory" to itemsList, "family_size" to familySize))
        val suggestionsRaw = response["suggestions"] as? List<*> ?: return emptyList()
        return suggestionsRaw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            GrocerySuggestion(
                name = map["name"] as? String ?: "",
                quantity = map["quantity"] as? String ?: "",
                reason = map["reason"] as? String ?: ""
            )
        }
    }

    suspend fun generateBehavioralInsights(transactions: List<ZadTransaction>, inventory: List<ZadInventory>): List<AiInsight> {
        val txStr = transactions.joinToString(", ") { "${it.title}: ${it.amount} (${if (it.isExpense) "خصم" else "إيداع"})" }
        val response = callAction("spending_insights", mapOf("transactions" to txStr, "budget" to 3500))
        val insightsRaw = response["insights"] as? List<*> ?: return emptyList()
        return insightsRaw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            AiInsight(
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                type = map["type"] as? String ?: "Tip"
            )
        }
    }

    suspend fun analyzeBankNotification(title: String, text: String): ZadTransaction? {
        val response = callAction("analyze_bank_notification", mapOf("bank" to title, "sms_text" to text))
        val amount = (response["amount"] as? Number)?.toDouble() ?: return null
        val parsedTitle = response["title"] as? String ?: return null
        return ZadTransaction(
            amount = amount,
            title = parsedTitle,
            isExpense = response["is_expense"] as? Boolean ?: true,
            category = response["category"] as? String ?: "عام"
        )
    }

    suspend fun detectSubscriptions(transactions: List<ZadTransaction>): List<DetectedSubscription> {
        if (transactions.isEmpty()) return emptyList()
        val txPayload = transactions.map { tx ->
            mapOf(
                "title" to tx.title,
                "amount" to tx.amount,
                "date" to (tx.createdAt ?: "")
            )
        }
        val response = callAction("detect_subscriptions", mapOf("transactions" to txPayload))
        val subsRaw = response["subscriptions"] as? List<*> ?: return emptyList()
        return subsRaw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            DetectedSubscription(
                name = map["name"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                frequency = map["frequency"] as? String ?: "monthly",
                confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0,
                nextBillingDate = map["next_billing_date"] as? String ?: ""
            )
        }
    }

    suspend fun askFamilyAssistant(message: String): String {
        val response = callAction("family_assistant", mapOf("message" to message))
        return response["text"] as? String ?: "عفواً، تعذر الاتصال."
    }

    suspend fun estimatePrice(itemName: String, store: String = ""): AiPriceEstimate? {
        val response = callAction("estimate_price", mapOf("item_name" to itemName, "store" to store))
        val itemNameOut = response["item_name"] as? String ?: return null
        return AiPriceEstimate(
            itemName = itemNameOut,
            lowPrice = (response["low_price"] as? Number)?.toDouble() ?: 0.0,
            avgPrice = (response["avg_price"] as? Number)?.toDouble() ?: 0.0,
            highPrice = (response["high_price"] as? Number)?.toDouble() ?: 0.0,
            store = response["store"] as? String,
            currency = response["currency"] as? String ?: "SAR"
        )
    }

    suspend fun predictExpenses(
        transactions: List<ZadTransaction>,
        budget: Double,
        patterns: List<ZadBehaviorPattern>
    ): AiExpensePrediction? {
        val txPayload = transactions.map { tx ->
            mapOf(
                "title" to tx.title,
                "amount" to tx.amount,
                "category" to (tx.category ?: "عام"),
                "date" to (tx.createdAt ?: ""),
                "is_expense" to tx.isExpense
            )
        }
        val patternsPayload = patterns.map { p ->
            mapOf("category" to p.category, "avg_amount" to p.avgAmount, "frequency_days" to p.frequencyDays)
        }
        val response = callAction("expense_prediction", mapOf(
            "transactions" to txPayload,
            "budget" to budget,
            "patterns" to patternsPayload
        ))
        val predictedTotal = (response["predicted_total"] as? Number)?.toDouble() ?: return null
        val breakdownRaw = response["breakdown"] as? List<*> ?: emptyList<Any>()
        val warningsRaw = response["warnings"] as? List<*> ?: emptyList<Any>()
        val tipsRaw = response["tips"] as? List<*> ?: emptyList<Any>()
        return AiExpensePrediction(
            predictedTotal = predictedTotal,
            confidence = (response["confidence"] as? Number)?.toDouble() ?: 0.0,
            breakdown = breakdownRaw.mapNotNull { b ->
                val bm = b as? Map<*, *> ?: return@mapNotNull null
                AiPredictionBreakdown(
                    category = bm["category"] as? String ?: "",
                    predicted = (bm["predicted"] as? Number)?.toDouble() ?: 0.0,
                    avgMonthly = (bm["avg_monthly"] as? Number)?.toDouble() ?: 0.0
                )
            },
            warnings = warningsRaw.map { it.toString() },
            tips = tipsRaw.map { it.toString() }
        )
    }

    suspend fun classifyBill(title: String, amount: Double): AiBillClassification? {
        val response = callAction("bill_classification", mapOf("title" to title, "amount" to amount))
        return AiBillClassification(
            type = response["type"] as? String ?: "other",
            provider = response["provider"] as? String,
            category = response["category"] as? String ?: "عام",
            confidence = (response["confidence"] as? Number)?.toDouble() ?: 0.0,
            isRecurring = response["is_recurring"] as? Boolean ?: false,
            suggestedFrequencyDays = (response["suggested_frequency_days"] as? Number)?.toInt()
        )
    }

    suspend fun getAgentSummary(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        budget: Double,
        shopping: List<ZadShoppingItem>,
        patterns: List<ZadBehaviorPattern>
    ): AiAgentSummary? {
        val response = callAction("agent_summary", mapOf(
            "inventory" to inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" },
            "transactions" to transactions.takeLast(20).joinToString(", ") { "${it.title}:${it.amount}" },
            "subscriptions" to subscriptions.filter { it.isActive }.joinToString(", ") { "${it.title}(${it.amount}/month)" },
            "budget" to budget,
            "shopping" to shopping.filter { !it.isPurchased }.joinToString(", ") { "${it.itemName}(${it.quantity})" },
            "patterns" to patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount},freq=${it.frequencyDays}d" }
        ))
        val summary = response["summary"] as? String ?: return null
        val alertsRaw = response["alerts"] as? List<*> ?: emptyList<Any>()
        val suggestionsRaw = response["suggestions"] as? List<*> ?: emptyList<Any>()
        val statsRaw = response["stats"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return AiAgentSummary(
            summary = summary,
            alerts = alertsRaw.mapNotNull { a ->
                val am = a as? Map<*, *> ?: return@mapNotNull null
                AiAgentAlert(
                    type = am["type"] as? String ?: "info",
                    title = am["title"] as? String ?: "",
                    description = am["description"] as? String ?: ""
                )
            },
            suggestions = suggestionsRaw.mapNotNull { s ->
                val sm = s as? Map<*, *> ?: return@mapNotNull null
                AiAgentSuggestion(
                    action = sm["action"] as? String ?: "",
                    item = sm["item"] as? String ?: "",
                    reason = sm["reason"] as? String ?: ""
                )
            },
            stats = AiAgentStats(
                inventoryCount = (statsRaw["inventory_count"] as? Number)?.toInt() ?: 0,
                expiringSoon = (statsRaw["expiring_soon"] as? Number)?.toInt() ?: 0,
                subscriptionsActive = (statsRaw["subscriptions_active"] as? Number)?.toInt() ?: 0,
                daysUntilBudgetEnd = (statsRaw["days_until_budget_end"] as? Number)?.toInt() ?: 30
            )
        )
    }

    // ── AI Text (generic, used by chat and other screens) ──

    suspend fun callGeminiText(systemPrompt: String, userPrompt: String): String? {
        val response = callAction("ai_text", mapOf(
            "system_prompt" to systemPrompt,
            "user_prompt" to userPrompt,
            "response_mime_type" to "text/plain"  // use plain text — json causes double-encoded response
        ))
        return response["text"] as? String
    }

    suspend fun brainEvaluate(systemPrompt: String, userPrompt: String): String? {
        val response = callAction("brain_evaluate", mapOf(
            "system_prompt" to systemPrompt,
            "user_prompt" to userPrompt
        ))
        return response["text"] as? String
    }

    suspend fun processVoiceCommand(audioBase64: String): VoiceAgentResponse? {
        val response = callAction("voice_agent", mapOf("audio_base64" to audioBase64))
        val action = response["action"] as? String ?: return null
        val message = response["message"] as? String ?: ""
        val dataRaw = response["data"] as? Map<*, *>
        val data = dataRaw?.let {
            VoiceAgentData(
                amount = (it["amount"] as? Number)?.toDouble() ?: 0.0,
                title = it["title"] as? String ?: "",
                category = it["category"] as? String ?: "عام"
            )
        }
        return VoiceAgentResponse(action = action, message = message, data = data)
    }

    // ── New AI Family Features ──

    data class FamilyAnalysisResult(
        val familySummary: String = "",
        val memberHighlights: List<MemberHighlight> = emptyList(),
        val familyHealthScore: Int = 50,
        val suggestedGoal: String = "",
        val funFact: String = ""
    )

    data class MemberHighlight(
        val name: String = "",
        val achievement: String = "",
        val suggestion: String = ""
    )

    data class AutoSuggestion(
        val action: String = "",
        val title: String = "",
        val description: String = "",
        val priority: String = "medium",
        val emoji: String = "💡"
    )

    data class FamilyGoalSuggestion(
        val goalTitle: String = "",
        val targetAmount: Double = 0.0,
        val rewardSuggestion: String = "",
        val durationDays: Int = 30,
        val emoji: String = "🎯"
    )

    data class BehaviorAnalysis(
        val insight: String = "",
        val avgSpending: Double = 0.0,
        val trend: String = "stable",
        val tip: String = "",
        val predictedNext: Double = 0.0,
        val confidence: Double = 0.0
    )

    suspend fun analyzeFamily(
        members: List<FamilyMember>,
        tasks: List<Chore>,
        goals: List<FamilyGoal>,
        tasbihaTrees: List<TasbihaTree>,
        transactions: List<ZadTransaction>
    ): FamilyAnalysisResult {
        val membersStr = members.joinToString(", ") { "${it.alias}(${it.role})" }
        val tasksStr = tasks.joinToString(", ") { "${it.title}:${if (it.isCompleted) "done" else "pending"}" }
        val goalsStr = goals.joinToString(", ") { "${it.monthYear}:${it.currentAmount}/${it.targetAmount}" }
        val tasbihaStr = tasbihaTrees.groupBy { it.userId }.map { (userId, trees) ->
            val name = members.find { it.id == userId }?.alias ?: "Unknown"
            "$name:${trees.sumOf { it.score }}"
        }.joinToString(", ")
        val txStr = transactions.takeLast(20).joinToString(", ") { "${it.title}:${it.amount}" }
        val response = callAction("family_analysis", mapOf(
            "members" to membersStr,
            "tasks" to tasksStr,
            "goals" to goalsStr,
            "tasbiha" to tasbihaStr,
            "transactions" to txStr
        ))
        val highlightsRaw = response["member_highlights"] as? List<*> ?: emptyList<Any>()
        val highlights = highlightsRaw.mapNotNull { h ->
            val hm = h as? Map<*, *> ?: return@mapNotNull null
            MemberHighlight(
                name = hm["name"] as? String ?: "",
                achievement = hm["achievement"] as? String ?: "",
                suggestion = hm["suggestion"] as? String ?: ""
            )
        }
        return FamilyAnalysisResult(
            familySummary = response["family_summary"] as? String ?: "",
            memberHighlights = highlights,
            familyHealthScore = (response["family_health_score"] as? Number)?.toInt() ?: 50,
            suggestedGoal = response["suggested_goal"] as? String ?: "",
            funFact = response["fun_fact"] as? String ?: ""
        )
    }

    suspend fun getAutoSuggestions(
        context: String,
        inventory: List<ZadInventory> = emptyList(),
        transactions: List<ZadTransaction> = emptyList(),
        patterns: List<ZadBehaviorPattern> = emptyList()
    ): List<AutoSuggestion> {
        val invStr = inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" }
        val txStr = transactions.takeLast(15).joinToString(", ") { "${it.title}:${it.amount}" }
        val patStr = patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount}" }
        val response = callAction("auto_suggest", mapOf(
            "context" to context,
            "inventory" to invStr,
            "transactions" to txStr,
            "patterns" to patStr
        ))
        val suggestionsRaw = response["suggestions"] as? List<*> ?: return emptyList()
        return suggestionsRaw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            AutoSuggestion(
                action = map["action"] as? String ?: "",
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                priority = map["priority"] as? String ?: "medium",
                emoji = map["emoji"] as? String ?: "💡"
            )
        }
    }

    suspend fun suggestFamilyGoal(
        members: List<FamilyMember>,
        totalBalance: Double,
        completedTasks: Int,
        tasbihaScore: Int
    ): FamilyGoalSuggestion {
        val membersStr = members.joinToString(", ") { "${it.alias}(${it.role})" }
        val response = callAction("family_goals_suggest", mapOf(
            "members" to membersStr,
            "total_balance" to totalBalance,
            "completed_tasks" to completedTasks,
            "tasbiha_score" to tasbihaScore
        ))
        return FamilyGoalSuggestion(
            goalTitle = response["goal_title"] as? String ?: "",
            targetAmount = (response["target_amount"] as? Number)?.toDouble() ?: 0.0,
            rewardSuggestion = response["reward_suggestion"] as? String ?: "",
            durationDays = (response["duration_days"] as? Number)?.toInt() ?: 30,
            emoji = response["emoji"] as? String ?: "🎯"
        )
    }

    suspend fun analyzeBehavior(
        category: String,
        transactions: List<ZadTransaction>,
        patterns: List<ZadBehaviorPattern>
    ): BehaviorAnalysis {
        val txStr = transactions.filter { it.category == category }.takeLast(15).joinToString(", ") { "${it.title}:${it.amount}" }
        val patStr = patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount},freq=${it.frequencyDays}d" }
        val response = callAction("behavior_analysis", mapOf(
            "category" to category,
            "transactions" to txStr,
            "current_patterns" to patStr
        ))
        return BehaviorAnalysis(
            insight = response["insight"] as? String ?: "",
            avgSpending = (response["avg_spending"] as? Number)?.toDouble() ?: 0.0,
            trend = response["trend"] as? String ?: "stable",
            tip = response["tip"] as? String ?: "",
            predictedNext = (response["predicted_next"] as? Number)?.toDouble() ?: 0.0,
            confidence = (response["confidence"] as? Number)?.toDouble() ?: 0.0
        )
    }

    // ══════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════

    private fun encodeBitmap(bitmap: Bitmap): String {
        val maxWidth = 800
        val maxHeight = 800
        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val resizedBitmap = if (ratio < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun getUserId(): String {
        return try {
            SupabaseRepo.client.auth.currentUserOrNull()?.id ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun callAction(action: String, payload: Map<String, Any?>): Map<String, Any?> {
        // Try Groq direct if key is set
        geminiApiKey?.takeIf { it.isNotEmpty() }?.let { apiKey ->
            try {
                val prompt = """
                    You are ZAD AI, a family budget app assistant.
                    Perform the following action: "$action"
                    Input data: $payload
                    Output ONLY valid JSON that matches the required schema for this action. No markdown.
                """.trimIndent()
                val responseText = ZadAiGeminiClient.generateText(apiKey, prompt, jsonFormat = true)
                if (responseText != null) {
                    fun parseElement(element: JsonElement): Any? = when (element) {
                        is JsonPrimitive -> {
                            if (element.isString) element.content
                            else element.intOrNull ?: element.doubleOrNull ?: element.booleanOrNull ?: element.content
                        }
                        is JsonArray -> element.map { parseElement(it) }
                        is JsonObject -> element.mapValues { parseElement(it.value) }
                        is JsonNull -> null
                    }
                    val decoded = json.decodeFromString<JsonObject>(responseText)
                    val parsed = parseElement(decoded) as? Map<String, Any?>
                    if (!parsed.isNullOrEmpty()) return parsed
                }
            } catch (e: Exception) {
                Log.w(TAG_REPO, "callAction($action) Groq failed, falling back to edge: ${e.message}")
            }
        }

        // Fallback to Edge function
        return try {
            val userId = getUserId()
            SupabaseRepo.callEdgeFunction(CENTRAL_FUNCTION, mapOf(
                "action" to action,
                "user_id" to userId,
                "payload" to payload
            ))
        } catch (e: Exception) {
            Log.e(TAG_REPO, "callAction($action) FAILED: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun callVision(
        action: String,
        bitmap: Bitmap,
        parser: suspend (Map<String, Any?>) -> Any?
    ): Any? {
        return try {
            val base64 = encodeBitmap(bitmap)
            val payload = mapOf("image_base64" to base64, "mime_type" to "image/jpeg")
            val response = callAction(action, payload)
            parser(response)
        } catch (e: Exception) {
            Log.e(TAG_REPO, "callVision($action) FAILED: ${e.message}")
            null
        }
    }

    // callVisionEdge: calls Edge Function directly (bypasses geminiApiKey text fallback)
    // Use this for vision tasks so the image is always sent properly
    private suspend fun callVisionEdge(
        action: String,
        bitmap: Bitmap,
        parser: suspend (Map<String, Any?>) -> Any?
    ): Any? {
        return try {
            val base64 = encodeBitmap(bitmap)
            val base64SizeBytes = base64.length * 3 / 4
            Log.d(TAG_REPO, "callVisionEdge($action): image $base64SizeBytes bytes -> Edge Function")
            val userId = getUserId()
            val response = SupabaseRepo.callEdgeFunction(CENTRAL_FUNCTION, mapOf(
                "action" to action,
                "user_id" to userId,
                "payload" to mapOf("image_base64" to base64, "mime_type" to "image/jpeg")
            ))
            Log.d(TAG_REPO, "callVisionEdge($action) response keys: ${response.keys}")
            parser(response)
        } catch (e: Exception) {
            Log.e(TAG_REPO, "callVisionEdge($action) FAILED: ${e.message}", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getNestedRawText(response: Map<String, Any?>): String? {
        try {
            return toJsonElement(response).toString()
        } catch (e: Exception) {
            return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                (value as Map<String, Any?>).forEach { (k, v) ->
                    put(k, toJsonElement(v))
                }
            }
            is List<*> -> buildJsonArray {
                value.forEach { add(toJsonElement(it)) }
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}
