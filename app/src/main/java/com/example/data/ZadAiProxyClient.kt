package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG_PROXY = "ZadAiProxy"

object ZadAiProxyClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // prevent hang on large image uploads
        .build()

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

    private suspend fun invokeEdgeFunction(requestType: String, payload: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("request_type", requestType)
                put("payload", payload)
            }

            val functionUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/zad-ai-proxy"
            val anonKey = BuildConfig.SUPABASE_ANON_KEY

            Log.d(TAG_PROXY, "invokeEdgeFunction() → type=$requestType url=$functionUrl")

            val request = Request.Builder()
                .url(functionUrl)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseText = response.body?.string()

            if (response.isSuccessful && responseText != null) {
                Log.d(TAG_PROXY, "invokeEdgeFunction() SUCCESS ← ${responseText.take(1000)}")
                return@withContext responseText
            } else {
                val errorMsg = "HTTP ${response.code}: ${responseText?.take(1000)}"
                Log.e(TAG_PROXY, "invokeEdgeFunction() $errorMsg")
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "invokeEdgeFunction() Exception: ${e.message}")
            throw e
        }
    }

    suspend fun analyzeReceipt(bitmap: Bitmap): AiParsedReceipt? {
        return try {
            val base64 = encodeBitmap(bitmap)
            val payload = buildJsonObject {
                put("image_base64", base64)
                put("mime_type", "image/jpeg")
            }
            val responseText = invokeEdgeFunction("receipt_analysis", payload)
            json.decodeFromString<AiParsedReceipt>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "analyzeReceipt() FAILED: ${e.message}")
            null
        }
    }

    suspend fun analyzeInventoryImage(bitmap: Bitmap): AiInventoryScanResult? {
        return try {
            val base64 = encodeBitmap(bitmap)
            val payload = buildJsonObject {
                put("image_base64", base64)
                put("mime_type", "image/jpeg")
            }
            val responseText = invokeEdgeFunction("inventory_scan", payload)
            json.decodeFromString<AiInventoryScanResult>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "analyzeInventoryImage() FAILED: ${e.message}")
            null
        }
    }

    suspend fun suggestMeals(inventory: List<ZadInventory>): String {
        return try {
            val itemsList = if (inventory.isEmpty()) "لا يوجد مخزون حاليا"
            else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
            val payload = buildJsonObject { put("items", itemsList) }
            val responseText = invokeEdgeFunction("meal_suggestions", payload)
            val element = json.parseToJsonElement(responseText)
            element.jsonObject["text"]?.jsonPrimitive?.content ?: "لم أتمكن من إيجاد اقتراحات حالياً."
        } catch(e: Exception) {
            Log.e(TAG_PROXY, "suggestMeals() FAILED: ${e.message}")
            "تعذّر الاتصال: ${e.message}"
        }
    }

    suspend fun getRecipeDetails(recipeName: String, inventory: List<ZadInventory>): String {
        return try {
            val itemsList = if (inventory.isEmpty()) "لا يوجد مخزون حاليا"
            else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
            val payload = buildJsonObject {
                put("recipe_name", recipeName)
                put("inventory", itemsList)
            }
            val responseText = invokeEdgeFunction("recipe_details", payload)
            val element = json.parseToJsonElement(responseText)
            element.jsonObject["text"]?.jsonPrimitive?.content ?: "لم أتمكن من إيجاد تفاصيل الوصفة حالياً."
        } catch(e: Exception) {
            Log.e(TAG_PROXY, "getRecipeDetails() FAILED: ${e.message}")
            "تعذّر الاتصال: ${e.message}"
        }
    }

    suspend fun suggestGroceries(inventory: List<ZadInventory>, familySize: Int = 4): List<GrocerySuggestion> {
        return try {
            val itemsList = if (inventory.isEmpty()) "لا يوجد"
            else inventory.joinToString(", ") { "${it.itemName} (${it.quantity})" }
            val payload = buildJsonObject {
                put("inventory", itemsList)
                put("family_size", familySize)
            }
            val responseText = invokeEdgeFunction("grocery_suggestions", payload)
            val element = json.parseToJsonElement(responseText)
            val suggestionsArray = element.jsonObject["suggestions"] ?: JsonArray(emptyList())
            json.decodeFromJsonElement<List<GrocerySuggestion>>(suggestionsArray)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "suggestGroceries() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun generateBehavioralInsights(
        transactions: List<ZadTransaction>,
        inventory: List<ZadInventory>
    ): List<AiInsight> {
        return try {
            val txStr = transactions.joinToString(", ") { "${it.title}: ${it.amount} (${if (it.isExpense) "خصم" else "إيداع"})" }
            val payload = buildJsonObject {
                put("transactions", buildJsonArray { txStr.split(", ").forEach { add(it) } })
                put("budget", 3500)
            }
            val responseText = invokeEdgeFunction("spending_insights", payload)
            val element = json.parseToJsonElement(responseText)
            val array = element.jsonObject["insights"] ?: JsonArray(emptyList())
            json.decodeFromJsonElement<List<AiInsight>>(array)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "generateBehavioralInsights() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun analyzeBankNotification(title: String, text: String): ZadTransaction? {
        return try {
            val payload = buildJsonObject {
                put("bank", title)
                put("sms_text", text)
            }
            val responseText = invokeEdgeFunction("bank_sms_parsing", payload)
            val parsed = json.decodeFromString<AiParsedTransaction>(responseText)
            ZadTransaction(
                amount = parsed.amount,
                title = parsed.title,
                isExpense = parsed.is_expense,
                category = parsed.category
            )
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "analyzeBankNotification() FAILED: ${e.message}")
            null
        }
    }

    suspend fun detectSubscriptions(transactions: List<ZadTransaction>): List<DetectedSubscription> {
        if (transactions.isEmpty()) return emptyList()
        return try {
            val payload = buildJsonObject {
                put("transactions", buildJsonArray { transactions.forEach { tx ->
                    add(buildJsonObject {
                        put("title", tx.title)
                        put("amount", tx.amount)
                        put("date", tx.createdAt ?: "")
                    })
                }})
            }
            val responseText = invokeEdgeFunction("subscription_detection", payload)
            val element = json.parseToJsonElement(responseText)
            val array = element.jsonObject["subscriptions"] ?: JsonArray(emptyList())
            json.decodeFromJsonElement<List<DetectedSubscription>>(array)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "detectSubscriptions() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun processVoiceCommand(audioBase64: String): VoiceAgentResponse? {
        return try {
            val payload = buildJsonObject {
                put("audio_base64", audioBase64)
            }
            val responseText = invokeEdgeFunction("voice_agent", payload)
            json.decodeFromString<VoiceAgentResponse>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "processVoiceCommand() FAILED: ${e.message}")
            null
        }
    }

    suspend fun askFamilyAssistant(message: String): String {
        return try {
            val payload = buildJsonObject { put("message", message) }
            val responseText = invokeEdgeFunction("chat", payload)
            val element = json.parseToJsonElement(responseText)
            element.jsonObject["text"]?.jsonPrimitive?.content ?: responseText
        } catch(e: Exception) {
            "عفواً، تعذر الاتصال: ${e.message}"
        }
    }

    suspend fun callGeminiText(systemPrompt: String, userPrompt: String): String? {
        return try {
            val payload = buildJsonObject {
                put("system_prompt", systemPrompt)
                put("user_prompt", userPrompt)
                put("response_mime_type", "text/plain")  // plain text to avoid double-encoded JSON
            }
            val responseText = invokeEdgeFunction("ai_text", payload)
            val element = json.parseToJsonElement(responseText)
            if (element is kotlinx.serialization.json.JsonObject) {
                element["text"]?.jsonPrimitive?.content ?: responseText
            } else {
                responseText
            }
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "callGeminiText() FAILED: ${e.message}")
            "تعذر الاتصال بالذكاء الاصطناعي: ${e.message}"
        }
    }

    suspend fun brainEvaluate(systemPrompt: String, userPrompt: String): String? {
        return try {
            val payload = buildJsonObject {
                put("system_prompt", systemPrompt)
                put("user_prompt", userPrompt)
            }
            val responseText = invokeEdgeFunction("brain_evaluate", payload)
            val element = json.parseToJsonElement(responseText)
            element.jsonObject["text"]?.jsonPrimitive?.content ?: responseText
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "brainEvaluate() FAILED: ${e.message}")
            "تعذر التقييم: ${e.message}"
        }
    }

    suspend fun estimatePrice(itemName: String, store: String = ""): AiPriceEstimate? {
        return try {
            val payload = buildJsonObject {
                put("item_name", itemName)
                put("store", store)
            }
            val responseText = invokeEdgeFunction("price_estimation", payload)
            json.decodeFromString<AiPriceEstimate>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "estimatePrice() FAILED: ${e.message}")
            null
        }
    }

    suspend fun predictExpenses(
        transactions: List<ZadTransaction>,
        budget: Double,
        patterns: List<ZadBehaviorPattern>
    ): AiExpensePrediction? {
        return try {
            val payload = buildJsonObject {
                put("transactions", buildJsonArray { transactions.forEach { tx ->
                    add(buildJsonObject {
                        put("title", tx.title)
                        put("amount", tx.amount)
                        put("category", tx.category ?: "عام")
                        put("date", tx.createdAt ?: "")
                        put("is_expense", tx.isExpense)
                    })
                }})
                put("budget", budget)
                put("patterns", buildJsonArray { patterns.forEach { p ->
                    add(buildJsonObject {
                        put("category", p.category)
                        put("avg_amount", p.avgAmount)
                        put("frequency_days", p.frequencyDays)
                    })
                }})
            }
            val responseText = invokeEdgeFunction("expense_prediction", payload)
            json.decodeFromString<AiExpensePrediction>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "predictExpenses() FAILED: ${e.message}")
            null
        }
    }

    suspend fun classifyBill(title: String, amount: Double): AiBillClassification? {
        return try {
            val payload = buildJsonObject {
                put("title", title)
                put("amount", amount)
            }
            val responseText = invokeEdgeFunction("bill_classification", payload)
            json.decodeFromString<AiBillClassification>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "classifyBill() FAILED: ${e.message}")
            null
        }
    }

    suspend fun getAgentSummary(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        budget: Double,
        shopping: List<com.example.data.ZadShoppingItem>,
        patterns: List<ZadBehaviorPattern>
    ): AiAgentSummary? {
        return try {
            val payload = buildJsonObject {
                put("inventory", inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" })
                put("transactions", transactions.takeLast(20).joinToString(", ") { "${it.title}:${it.amount}" })
                put("subscriptions", subscriptions.filter { it.isActive }.joinToString(", ") { "${it.title}(${it.amount}/month)" })
                put("budget", budget)
                put("shopping", shopping.filter { !it.isPurchased }.joinToString(", ") { "${it.itemName}(${it.quantity})" })
                put("patterns", patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount},freq=${it.frequencyDays}d" })
            }
            val responseText = invokeEdgeFunction("agent_summary", payload)
            json.decodeFromString<AiAgentSummary>(responseText)
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "getAgentSummary() FAILED: ${e.message}")
            null
        }
    }

    // ── New AI Family Features ──

    suspend fun analyzeFamily(
        members: List<com.example.data.FamilyMember>,
        tasks: List<Chore>,
        goals: List<FamilyGoal>,
        tasbihaTrees: List<TasbihaTree>,
        transactions: List<ZadTransaction>
    ): com.example.data.ZadAiRepository.FamilyAnalysisResult {
        return try {
            val membersStr = members.joinToString(", ") { "${it.alias}(${it.role})" }
            val tasksStr = tasks.joinToString(", ") { "${it.title}:${if (it.isCompleted) "done" else "pending"}" }
            val goalsStr = goals.joinToString(", ") { "${it.monthYear}:${it.currentAmount}/${it.targetAmount}" }
            val tasbihaStr = tasbihaTrees.groupBy { it.userId }.map { (userId, trees) ->
                val name = members.find { it.id == userId }?.alias ?: "Unknown"
                "$name:${trees.sumOf { it.score }}"
            }.joinToString(", ")
            val txStr = transactions.takeLast(20).joinToString(", ") { "${it.title}:${it.amount}" }

            val payload = buildJsonObject {
                put("members", membersStr)
                put("tasks", tasksStr)
                put("goals", goalsStr)
                put("tasbiha", tasbihaStr)
                put("transactions", txStr)
            }
            val responseText = invokeEdgeFunction("family_analysis", payload)
            val obj = json.parseToJsonElement(responseText).jsonObject
            val highlights = try {
                val arr = obj["member_highlights"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
                arr.map { it.jsonObject }.map { o ->
                    com.example.data.ZadAiRepository.MemberHighlight(
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        achievement = o["achievement"]?.jsonPrimitive?.content ?: "",
                        suggestion = o["suggestion"]?.jsonPrimitive?.content ?: ""
                    )
                }
            } catch (e: Exception) { emptyList() }

            com.example.data.ZadAiRepository.FamilyAnalysisResult(
                familySummary = obj["family_summary"]?.jsonPrimitive?.content ?: "",
                memberHighlights = highlights,
                familyHealthScore = obj["family_health_score"]?.jsonPrimitive?.intOrNull ?: 50,
                suggestedGoal = obj["suggested_goal"]?.jsonPrimitive?.content ?: "",
                funFact = obj["fun_fact"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "analyzeFamily() FAILED: ${e.message}")
            com.example.data.ZadAiRepository.FamilyAnalysisResult()
        }
    }

    suspend fun getAutoSuggestions(
        context: String,
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        patterns: List<ZadBehaviorPattern>
    ): List<com.example.data.ZadAiRepository.AutoSuggestion> {
        return try {
            val invStr = inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" }
            val txStr = transactions.takeLast(15).joinToString(", ") { "${it.title}:${it.amount}" }
            val patStr = patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount}" }

            val payload = buildJsonObject {
                put("context", context)
                put("inventory", invStr)
                put("transactions", txStr)
                put("patterns", patStr)
            }
            val responseText = invokeEdgeFunction("auto_suggest", payload)
            val element = json.parseToJsonElement(responseText)
            val arr = element.jsonObject["suggestions"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            arr.map { it.jsonObject }.mapNotNull { obj ->
                try {
                    com.example.data.ZadAiRepository.AutoSuggestion(
                        action = obj["action"]?.jsonPrimitive?.content ?: "",
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                        priority = obj["priority"]?.jsonPrimitive?.content ?: "medium",
                        emoji = obj["emoji"]?.jsonPrimitive?.content ?: "💡"
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "getAutoSuggestions() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun suggestFamilyGoal(
        members: List<com.example.data.FamilyMember>,
        totalBalance: Double,
        completedTasks: Int,
        tasbihaScore: Int
    ): com.example.data.ZadAiRepository.FamilyGoalSuggestion {
        return try {
            val membersStr = members.joinToString(", ") { "${it.alias}(${it.role})" }
            val payload = buildJsonObject {
                put("members", membersStr)
                put("total_balance", totalBalance)
                put("completed_tasks", completedTasks)
                put("tasbiha_score", tasbihaScore)
            }
            val responseText = invokeEdgeFunction("family_goals_suggest", payload)
            val obj = json.parseToJsonElement(responseText).jsonObject
            com.example.data.ZadAiRepository.FamilyGoalSuggestion(
                goalTitle = obj["goal_title"]?.jsonPrimitive?.content ?: "",
                targetAmount = obj["target_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                rewardSuggestion = obj["reward_suggestion"]?.jsonPrimitive?.content ?: "",
                durationDays = obj["duration_days"]?.jsonPrimitive?.intOrNull ?: 30,
                emoji = obj["emoji"]?.jsonPrimitive?.content ?: "🎯"
            )
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "suggestFamilyGoal() FAILED: ${e.message}")
            com.example.data.ZadAiRepository.FamilyGoalSuggestion()
        }
    }

    suspend fun analyzeBehavior(
        category: String,
        transactions: List<ZadTransaction>,
        patterns: List<ZadBehaviorPattern>
    ): com.example.data.ZadAiRepository.BehaviorAnalysis {
        return try {
            val txStr = transactions.filter { it.category == category }.takeLast(15).joinToString(", ") { "${it.title}:${it.amount}" }
            val patStr = patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount},freq=${it.frequencyDays}d" }
            val payload = buildJsonObject {
                put("category", category)
                put("transactions", txStr)
                put("current_patterns", patStr)
            }
            val responseText = invokeEdgeFunction("behavior_learning", payload)
            val obj = json.parseToJsonElement(responseText).jsonObject
            com.example.data.ZadAiRepository.BehaviorAnalysis(
                insight = obj["insight"]?.jsonPrimitive?.content ?: "",
                avgSpending = obj["avg_spending"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                trend = obj["trend"]?.jsonPrimitive?.content ?: "stable",
                tip = obj["tip"]?.jsonPrimitive?.content ?: "",
                predictedNext = obj["predicted_next"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG_PROXY, "analyzeBehavior() FAILED: ${e.message}")
            com.example.data.ZadAiRepository.BehaviorAnalysis()
        }
    }
}
