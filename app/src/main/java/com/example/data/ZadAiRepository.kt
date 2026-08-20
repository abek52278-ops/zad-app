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

/** العقل — بقى نقطة الدخول للمحادثة كمان (agent_turn/agent_confirm)، مش التحليل الخلفي بس. */
private const val BRAIN_FUNCTION = "zad-brain"

/**
 * لفة المحادثة ممكن تعمل لفتين نداء موديل + عدة كتابات داتابيز، فمهلتها أطول من الـ ٣٠
 * ثانية الافتراضية. لسه محدودة — في عميل مستني قدام الشاشة.
 */
private const val AGENT_TURN_TIMEOUT_MS = 60_000L

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
    val items: List<AiParsedReceiptItem>,
    // "pharmacy" | "grocery" | "general" — pharmacy routes items to zad_pharmacy_items
    // instead of general inventory (see CameraScreen's receipt confirm handler).
    val receiptType: String = "grocery"
)

@kotlinx.serialization.Serializable
data class AiInsight(
    val title: String,
    val description: String,
    val type: String,
    // بطاقة الرؤية القابلة للتنفيذ: زر مباشر بدل نصيحة نصية بس.
    // actionType: "cancel_subscription" (actionRefId = subscription id, actionAmount = التوفير السنوي)
    //           | "increase_budget" (actionRefId = اسم الفئة, actionAmount = الميزانية المقترحة)
    val actionType: String? = null,
    val actionRefId: String? = null,
    val actionAmount: Double? = null
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
    // Exposed so callers (SmartChefSection) can tell "AI genuinely failed" apart from
    // "AI succeeded but wrote something callAction's caller doesn't recognize" without
    // fragile prefix-matching on the model's prose formatting.
    const val MEAL_SUGGESTIONS_FALLBACK = "لم أتمكن من إيجاد اقتراحات حالياً."
    // ZadViewModel._mealSuggestions' initial value before suggestMeals() resolves — exposed so
    // SmartChefSection can exclude it from "is this a real dish" the same way it already
    // excludes MEAL_SUGGESTIONS_FALLBACK, instead of treating the loading text as a clickable dish.
    const val MEAL_SUGGESTIONS_LOADING = "جاري تحليل المخزون..."
    /**
     * مخزون فيه أصناف بس كلها كميتها صفر. الحالة دي مختلفة تماماً عن مخزون فاضي —
     * الأولى معناها "خلص، اشتري"، والتانية "ابدأ سجّل". الاتنين كانوا بيدّوا نفس الجملة
     * ("ضيف أصناف عشان أقترحلك")، فالعميل اللي مخزونه اتصفّر كان شايف رسالة بتقوله
     * يعمل حاجة هو عاملها خلاص.
     */
    const val MEAL_SUGGESTIONS_ALL_DEPLETED = "__all_depleted__"

    // Accurate again as of 2026-08-01: ZadAiGeminiClient really does call Google Gemini,
    // so the name, the SharedPreferences key ("gemini_api_key") and the destination finally
    // agree. May hold several comma-separated keys — the client rotates through them on
    // quota errors. A stale Groq key left here by an older build just fails and falls
    // through to the edge function.
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
            val total = (response["total"] as? Number)?.toDouble()?.asMoney() ?: 0.0
            val category = response["category"] as? String ?: ""
            val storeName = response["storeName"] as? String ?: ""
            val receiptType = response["receiptType"] as? String ?: "grocery"
            val items = itemsRaw?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                AiParsedReceiptItem(
                    name = map["name"] as? String ?: return@mapNotNull null,
                    price = (map["price"] as? Number)?.toDouble()?.asMoney() ?: 0.0,
                    quantity = (map["quantity"] as? Number)?.toDouble() ?: 1.0,
                    unit = map["unit"] as? String ?: "قطعة",
                    category = map["category"] as? String ?: "عام"
                )
            } ?: emptyList()
            AiParsedReceipt(total = total, category = category, storeName = storeName, items = items, receiptType = receiptType)
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

    suspend fun suggestMeals(inventory: List<ZadInventory>): ChefSuggestion {
        // شيف زاد يقترح بس من صنف فعلاً موجود — صفر بالكمية يعني خلص، مش "متاح"
        val available = inventory.filter { it.quantity > 0 }
        val itemsList = if (available.isEmpty()) "لا يوجد مخزون حاليا"
        else available.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val response = callAction("meal_suggestions", mapOf("items" to itemsList))
        val text = response["text"] as? String ?: MEAL_SUGGESTIONS_FALLBACK
        // نفس أسلوب agent_summary فوق: تفكيك يدوي للـ Map مش decodeFromString، عشان
        // `callAction` بترجّع Map<String, Any?> أصلاً. حقل ناقص في وصفة واحدة بيدي قيمة
        // افتراضية بدل ما يوقّع تفكيك الرد كله — الرد جاي من نموذج، والصرامة هنا معناها
        // إن وصفة واحدة ناقصة حقل تضيّع الخمسة.
        val recipesRaw = response["recipes"] as? List<*> ?: emptyList<Any>()
        val recipes = recipesRaw.mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val name = (m["recipe_name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            ZadRecipe(
                recipeName = name,
                imageKeywordEn = (m["image_keyword_en"] as? String).orEmpty(),
                imageUrl = (m["image_url"] as? String)?.takeIf { it.isNotBlank() },
                imageThumbUrl = (m["image_thumb_url"] as? String)?.takeIf { it.isNotBlank() },
                prepTimeMinutes = (m["prep_time_minutes"] as? Number)?.toInt() ?: 0,
                costEstimate = (m["cost_estimate"] as? Number)?.toDouble() ?: 0.0,
                availableIngredientsUsed = (m["available_ingredients_used"] as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList(),
                missingIngredientsToBuy = (m["missing_ingredients_to_buy"] as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList(),
                cookingInstructions = (m["cooking_instructions"] as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList(),
            )
        }
        val finalRecipes = recipes.ifEmpty {
            if (available.isNotEmpty()) generateDeterministicChefRecipes(available) else emptyList()
        }
        val finalText = if (text == MEAL_SUGGESTIONS_FALLBACK && finalRecipes.isNotEmpty()) {
            "جمعتلك أفكار وصفات شهية تقدر تطبخها النهاردة من المخزون المتاح عندك! 🍳"
        } else text
        return ChefSuggestion(text = finalText, recipes = finalRecipes)
    }

    fun generateDeterministicChefRecipes(available: List<ZadInventory>): List<ZadRecipe> {
        val names = available.map { it.itemName.lowercase().trim() }
        val recipes = mutableListOf<ZadRecipe>()

        fun hasAny(vararg keywords: String): Boolean = keywords.any { kw -> names.any { it.contains(kw) } }

        if (hasAny("فراخ", "دجاج", "chicken") && hasAny("رز", "أرز", "ارز", "rice")) {
            recipes.add(
                ZadRecipe(
                    recipeName = "كبسة دجاج شهية بالبهارات",
                    imageKeywordEn = "chicken kabsa rice spiced delicious",
                    prepTimeMinutes = 35,
                    costEstimate = 65.0,
                    availableIngredientsUsed = available.filter { it.itemName.contains("دجاج") || it.itemName.contains("فراخ") || it.itemName.contains("رز") || it.itemName.contains("أرز") }.map { it.itemName },
                    missingIngredientsToBuy = listOf("بهارات كبسة", "مكسرات للتزيين"),
                    cookingInstructions = listOf(
                        "حمري قطع الدجاج في قدر عميق مع البصل والبهارات حتى تأخذ لوناً ذهبياً.",
                        "أضيفي الماء الساخن واتركي الدجاج ينضج على نار متوسطة لمدة ٢٥ دقيقة.",
                        "أضيفي الأرز المغسول فوق مرق الدجاج واتركيه يغلي ثم هدئي النار تماماً.",
                        "قدمي الكبسة ساخنة مع رشة مكسرات محمصة وبالهناء والشفاء."
                    )
                )
            )
        }

        if (hasAny("مكرونة", "معكرونة", "pasta") && hasAny("طماطم", "صلصة", "جبن", "جبنة", "cheese", "لحم", "لحمة")) {
            recipes.add(
                ZadRecipe(
                    recipeName = "مكرونة باستا بصلصة الطماطم والجبن",
                    imageKeywordEn = "pasta tomato sauce basil cheese delicious",
                    prepTimeMinutes = 20,
                    costEstimate = 35.0,
                    availableIngredientsUsed = available.filter { it.itemName.contains("مكرونة") || it.itemName.contains("طماطم") || it.itemName.contains("جبن") }.map { it.itemName },
                    missingIngredientsToBuy = listOf("ريحان طازج"),
                    cookingInstructions = listOf(
                        "اسلقي المكرونة في ماء مغلي مملح حتى تصبح طرية ومتماسكة.",
                        "جهزي صلصة الطماطم مع الثوم والزيت والملح والفلفل الأسود.",
                        "اخلطي المكرونة مع الصلصة الساخنة ورشي الجبن على الوجه.",
                        "قدمي الطبق ساخناً ومزيناً بأوراق الريحان."
                    )
                )
            )
        }

        if (hasAny("بيض", "eggs") && hasAny("طماطم", "بصل", "فلفل", "جبن", "جبنة")) {
            recipes.add(
                ZadRecipe(
                    recipeName = "شكشوكة بيض بالخضار والجبن",
                    imageKeywordEn = "shakshuka eggs tomato breakfast pan",
                    prepTimeMinutes = 15,
                    costEstimate = 25.0,
                    availableIngredientsUsed = available.filter { it.itemName.contains("بيض") || it.itemName.contains("طماطم") || it.itemName.contains("بصل") || it.itemName.contains("فلفل") }.map { it.itemName },
                    missingIngredientsToBuy = listOf("خبز بلدي طازج"),
                    cookingInstructions = listOf(
                        "شوحي البصل والفلفل المفروم في مقلاة مع قليل من الزيت حتى يذبل.",
                        "أضيفي الطماطم المفرومة والبهارات واتركيها تتسبك لمدة ٥ دقائق.",
                        "اصنعي فجوات في الصلصة واكسري حبات البيض بداخلها.",
                        "غطي المقلاة على نار هادئة حتى ينضج البيض ورشي رشة فلفل وجبن."
                    )
                )
            )
        }

        if (hasAny("لحم", "لحمة", "كفتة", "meat", "beef", "burger") && hasAny("بصل", "خبز", "عيش", "بطاطس")) {
            recipes.add(
                ZadRecipe(
                    recipeName = "كفتة مشوية شهية مع البطاطس",
                    imageKeywordEn = "kofta grilled kebab plate salad",
                    prepTimeMinutes = 30,
                    costEstimate = 80.0,
                    availableIngredientsUsed = available.filter { it.itemName.contains("لحم") || it.itemName.contains("كفتة") || it.itemName.contains("بصل") || it.itemName.contains("بطاطس") }.map { it.itemName },
                    missingIngredientsToBuy = listOf("بقدونس", "طحينة"),
                    cookingInstructions = listOf(
                        "تبلي اللحم المفروم بالبصل المبشور والبهارات واعجنيه جيداً.",
                        "شكلي الكفتة على أسياخ أو أصابع متساوية الحجم.",
                        "اشوي الكفتة في الفرن أو على الشواية حتى تنضج وتكتسب نكهة الشواء.",
                        "قدميها مع البطاطس وسلطة الطحينة والخبز الساخن."
                    )
                )
            )
        }

        if (hasAny("تونة", "تونا", "tuna") || hasAny("سلطة", "خيار", "طماطم", "خس", "salad")) {
            recipes.add(
                ZadRecipe(
                    recipeName = "سلطة تونة صحية ومنعشة",
                    imageKeywordEn = "fresh tuna salad bowl vegetables",
                    prepTimeMinutes = 10,
                    costEstimate = 30.0,
                    availableIngredientsUsed = available.filter { it.itemName.contains("تونة") || it.itemName.contains("خيار") || it.itemName.contains("طماطم") || it.itemName.contains("خس") }.map { it.itemName },
                    missingIngredientsToBuy = listOf("ليمون", "زيت زيتون"),
                    cookingInstructions = listOf(
                        "صفي التونة من الزيت أو الماء وضعيها في وعاء عميق.",
                        "قطعي الخيار والطماطم والخس وضعيهم فوق التونة.",
                        "تبلي بعصير الليمون وزيت الزيتون ورشة ملح وكمون.",
                        "قلبي المكونات برفق وقدمي السلطة باردة ولذيذة."
                    )
                )
            )
        }

        if (recipes.isEmpty() && available.isNotEmpty()) {
            val firstThree = available.take(3).map { it.itemName }
            recipes.add(
                ZadRecipe(
                    recipeName = "وجبة منزلية سريعة بـ ${firstThree.joinToString(" و ")}",
                    imageKeywordEn = "home cooked delicious food plate dinner",
                    prepTimeMinutes = 20,
                    costEstimate = 30.0,
                    availableIngredientsUsed = firstThree,
                    missingIngredientsToBuy = listOf("توابل وزيت طهي"),
                    cookingInstructions = listOf(
                        "جهزي المكونات المتاحة وقومي بتقطيعها بحجم مناسب للطهي.",
                        "سخني ملعقة زيت في المقلاة وشوحي المكونات بالتتابع على نار متوسطة.",
                        "أضيفي الملح والبهارات المفضلة مع نصف كوب ماء لتكتمل التسوية.",
                        "قدمي الطبق ساخناً مع الخبز أو الأرز."
                    )
                )
            )
        }
        return recipes
    }

    /**
     * وصفات مركزة على أصناف هتخلص/تنتهي — مربوطة بالعقل المركزي.
     * Task 23 — [stagnantItems] (راكدة من ٣٠ يوم، مفيش استهلاك خالص) بتتحط أول الطلب،
     * بصياغة مختلفة عن [urgentItems] (هتخلص/هتنتهي قريب) — السبب مختلف تماماً: دول مش
     * على وشك النفاد، العكس، حد نسيهم.
     */
    suspend fun suggestMealsForUrgentItems(
        urgentItems: List<String>,
        inventory: List<ZadInventory>,
        stagnantItems: List<String> = emptyList()
    ): String {
        // نفس مبدأ suggestMeals — "باقي المخزون المتاح" لازم يكون فعلاً متاح (كمية > 0)
        val available = inventory.filter { it.quantity > 0 }
        val invList = if (available.isEmpty()) "لا يوجد مخزون"
        else available.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val parts = mutableListOf<String>()
        if (stagnantItems.isNotEmpty()) {
            parts += "عندك من فترة (شهر تقريباً) وماستخدمتهاش خالص: " + stagnantItems.joinToString("، ") + " — استخدمها الأول قبل أي حاجة تانية"
        }
        if (urgentItems.isNotEmpty()) {
            parts += "على وشك الانتهاء أو النفاد: " + urgentItems.joinToString("، ")
        }
        val itemsPayload = "مهم جداً: اقترح 3 وصفات. " + parts.joinToString(". ثم ") + ". باقي المخزون المتاح: " + invList
        val response = callAction("meal_suggestions", mapOf("items" to itemsPayload))
        return response["text"] as? String ?: MEAL_SUGGESTIONS_FALLBACK
    }

    suspend fun getRecipeDetails(recipeName: String, inventory: List<ZadInventory>): String {
        val available = inventory.filter { it.quantity > 0 }
        val itemsList = if (available.isEmpty()) "لا يوجد مخزون حاليا"
        else available.joinToString(", ") { "${it.itemName} (${it.quantity})" }
        val response = callAction("recipe_details", mapOf("recipe_name" to recipeName, "inventory" to itemsList))
        // a failed/timed-out upstream call used to fall back to a placeholder string that was
        // then rendered as if it were a real recipe — throw instead so the caller can show a
        // retry state (see RecipeDetailDialog's isLoading/errorMessage handling)
        return response["text"] as? String
            ?: throw IllegalStateException("recipe_details: upstream call failed for \"$recipeName\"")
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

    suspend fun generateBehavioralInsights(transactions: List<ZadTransaction>, inventory: List<ZadInventory>, budget: Double): List<AiInsight> {
        val txStr = transactions.joinToString(", ") { "${it.title}: ${it.amount} (${if (it.isExpense) "خصم" else "إيداع"})" }
        val response = callAction("spending_insights", mapOf("transactions" to txStr, "budget" to budget))
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

    // نفس مجموعة الفئات اللي SaBankParser.classify() فعلياً بيرجعها — لو الـ AI رجّع
    // تصنيف برا القائمة دي (اختراع/هلوسة)، بترجع لـ "أخرى" بدل ما تدخل فئة وهمية
    // ميزانيات الفئات في BudgetTracker مش عارفاها أصلاً.
    private val VALID_BANK_CATEGORIES = setOf(
        "الراتب", "البقالة", "المطاعم", "الفواتير", "الرعاية الصحية", "المواصلات",
        "التعليم", "الأقساط", "الاشتراكات", "الوقود", "تحويلات", "أخرى"
    )

    /**
     * مسار AI الاحتياطي بس — SaBankParser.detectAndParse بيتنادى الأول دايماً وبيغطي أغلب
     * الحالات المعروفة مجاناً وبدقة أعلى (regex محدد ومختبر)، ده بيتنادى بس لما ده يفشل.
     * الرفض أفضل من التخمين (نفس مبدأ SaBankParser نصاً): تحت عتبة الثقة، يرجع null
     * ويتعامل زي أي رسالة مش مفهومة — بيتحط في outbox لإعادة المحاولة، مش بيتسجل بثقة واهية.
     */
    suspend fun analyzeBankNotification(title: String, text: String): ZadTransaction? {
        val response = callAction("analyze_bank_notification", mapOf("bank" to title, "sms_text" to text))
        val amount = (response["amount"] as? Number)?.toDouble()?.asMoney() ?: return null
        if (amount <= 0) return null

        val confidence = (response["confidence"] as? Number)?.toFloat() ?: 0f
        if (confidence < 0.6f) return null

        val isIncome = (response["type"] as? String) == "INCOME"
        val merchant = (response["merchant_or_sender"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val category = (response["category"] as? String)?.takeIf { it in VALID_BANK_CATEGORIES } ?: "أخرى"
        val currency = (response["currency"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        return ZadTransaction(
            amount = amount,
            title = merchant ?: (if (isIncome) "دخل" else "مصروف"),
            isExpense = !isIncome,
            category = category,
            merchantName = merchant,
            currency = currency,
            createdAt = java.time.Instant.now().toString()
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
                amount = (map["amount"] as? Number)?.toDouble()?.asMoney() ?: 0.0,
                frequency = map["frequency"] as? String ?: "monthly",
                confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0,
                nextBillingDate = map["next_billing_date"] as? String ?: ""
            )
        }
    }

    suspend fun askFamilyAssistant(message: String, senderRole: String = "member"): String {
        val response = callAction("family_assistant", mapOf("message" to message, "role" to senderRole))
        return response["text"] as? String ?: "الذكاء الاصطناعي مشغول شوي دلوقتي 🙏 جرب تاني بعد لحظات."
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
        patterns: List<ZadBehaviorPattern>,
        appContext: android.content.Context? = null
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
            // نفس سبب getAgentSummary: سقف غير معروف مينفعش يتبعت كرقم يتقارن بيه التوقع.
            "budget" to (if (budget > 0) budget else "غير معروف"),
            "patterns" to patternsPayload
        ), appContext = appContext)
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

    suspend fun getSeasonalForecast(
        events: List<Pair<SeasonalEvent, SeasonalEventWindow?>>
    ): List<AiSeasonalForecast> {
        val eventsPayload = events.map { (e, w) ->
            mapOf(
                "id" to e.id,
                "slug" to e.slug,
                "name" to e.name,
                "category_tags" to e.categoryTags,
                "event_start" to (w?.startDate ?: e.startDate ?: ""),
                "event_end" to (w?.endDate ?: e.endDate ?: "")
            )
        }
        val response = callAction("seasonal_forecast", mapOf("events" to eventsPayload))
        val forecastsRaw = response["forecasts"] as? List<*> ?: return emptyList()
        return forecastsRaw.mapNotNull { f ->
            val fm = f as? Map<*, *> ?: return@mapNotNull null
            AiSeasonalForecast(
                eventId = fm["event_id"] as? String ?: "",
                slug = fm["slug"] as? String,
                daysUntil = (fm["days_until"] as? Number)?.toInt() ?: 0,
                predictedTotal = (fm["predicted_total"] as? Number)?.toDouble() ?: 0.0,
                confidence = (fm["confidence"] as? Number)?.toDouble() ?: 0.0,
                breakdown = ((fm["breakdown"] as? List<*>) ?: emptyList<Any>()).mapNotNull { b ->
                    val bm = b as? Map<*, *> ?: return@mapNotNull null
                    AiSeasonalForecastBreakdown(
                        category = bm["category"] as? String ?: "",
                        predicted = (bm["predicted"] as? Number)?.toDouble() ?: 0.0,
                        baselineMonthlyAvg = (bm["baseline_monthly_avg"] as? Number)?.toDouble() ?: 0.0,
                        multiplierUsed = (bm["multiplier_used"] as? Number)?.toDouble() ?: 0.0,
                        source = bm["source"] as? String ?: "fallback"
                    )
                },
                tip = fm["tip"] as? String ?: ""
            )
        }
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
        patterns: List<ZadBehaviorPattern>,
        obligations: List<ZadObligation> = emptyList(),
        appContext: android.content.Context? = null
    ): AiAgentSummary? {
        val response = callAction("agent_summary", mapOf(
            "inventory" to inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" },
            "transactions" to transactions.takeLast(20).joinToString(", ") { "${it.title}:${it.amount}" },
            "subscriptions" to subscriptions.filter { it.isActive }.joinToString(", ") { "${it.title}(${it.amount}/month)" },
            // سقف مش متسجل (<= 0) بيتبعت كنص "غير معروف" مش كرقم. لو اتبعت رقم، الموديل
            // بيقراه على إنه سقف المستخدم الحقيقي ويقتبسه في الملخص بالحرف.
            "budget" to (if (budget > 0) budget else "غير معروف"),
            "shopping" to shopping.filter { !it.isPurchased }.joinToString(", ") { "${it.itemName}(${it.quantity})" },
            "patterns" to patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount},freq=${it.frequencyDays}d" },
            // كان الملخص ده مش عارف حاجة عن الإيجار/الفواتير/الأقساط الثابتة خالص — "المحجوز"
            // بيظهر كرقم في مكان تاني بس، من غير أي مصدر هنا يسمّي الالتزام نفسه.
            "obligations" to obligations.joinToString(", ") { "${it.title}(${it.amount}/${it.recurrence})" }
        ), appContext = appContext)
        // كان `as? String ?: return null` بس — والسيرفر بيرجّع summary: "" (مش null) لما
        // النموذج يفشل (شوف zad-core-intelligence، case "agent_summary"، فرع `if (!result)`).
        // النتيجة: العميل بيبني AiAgentSummary بملخص فاضي، والكارت الأخضر على الشاشة
        // الرئيسية بيرسم Text("") — كارت أخضر فاضي تماماً، وهو بالظبط اللي العميل شافه.
        // فراغ = فشل، والفشل لازم يرجّع null عشان الطبقة اللي فوق تعرف تعمل fallback محلي.
        val summary = (response["summary"] as? String)?.takeIf { it.isNotBlank() } ?: return null
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
                daysUntilBudgetEnd = (statsRaw["days_until_budget_end"] as? Number)?.toInt()
            )
        )
    }

    // ── AI Text (generic, used by chat and other screens) ──

    /**
     * @param thinkingBudget caps the model's reasoning tokens for this call. Chat
     *   passes a small number: with no streaming, every reasoning token is a second
     *   the user spends watching a typing dot. Null keeps the server's default
     *   (unbounded on the brain tier), which is right for background analysis where
     *   nobody is waiting on the screen.
     */
    suspend fun callGeminiText(
        systemPrompt: String,
        userPrompt: String,
        thinkingBudget: Int? = null
    ): String? {
        val payload = buildMap<String, Any?> {
            put("system_prompt", systemPrompt)
            put("user_prompt", userPrompt)
            put("response_mime_type", "text/plain")  // json causes a double-encoded response
            if (thinkingBudget != null) put("thinking_budget", thinkingBudget)
        }
        val response = callAction("ai_text", payload)
        return response["text"] as? String
    }

    /** محاكي القرارات المالية (What-If): يبني نص عربي قصير يفسّر أثر التزام شهري جديد على الميزانية، بالاعتماد على الحكم المحلي المحسوب مسبقاً (زاد ما يخترعش أرقام، بس يفسرها). */
    suspend fun evaluateWhatIf(
        monthlyBudget: Double,
        predictedMonthlySpend: Double,
        purchaseAmount: Double,
        monthlyInstallment: Double,
        installmentMonths: Int,
        verdict: String,
        firstExceedMonth: Int?
    ): String? {
        val systemPrompt = "أنت محلل مالي شخصي داخل تطبيق زاد. حلل بيانات محاكاة القرار المالي أدناه وقدم جملة أو جملتين بالعربي فقط توضحان تأثير هذا القرار على ميزانية المستخدم خلال الأشهر القادمة، بأسلوب مباشر وودود بدون مبالغة. لا تخترع أرقاماً غير موجودة في البيانات، فسّرها فقط."
        val userPrompt = """
            === بيانات المحاكاة ===
            الميزانية الشهرية: $monthlyBudget
            متوسط الصرف الشهري المتوقع: $predictedMonthlySpend
            سعر الشراء المطروح: $purchaseAmount
            القسط الشهري: $monthlyInstallment لمدة $installmentMonths شهر
            الحكم المحلي (SAFE/RISKY/EXCEEDS): $verdict
            أول شهر يتجاوز فيه الميزانية: ${firstExceedMonth ?: "لا يوجد"}
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  المرحلة ٢-ج — المحادثة عبر zad-brain (استدعاء أدوات حقيقي)
    //
    //  بديل بروتوكول [[ACTION]] النصي: زاد بينادي أدوات فعلية سيرفر-سايد، والرد اللي
    //  بيتعرض مبني على نتيجة التنفيذ مش على كلام الموديل. الفرق العملي إن "ضفتلك
    //  اللحمة" مابقاش ينفع يتقال من غير ما اللحمة تتضاف فعلاً.
    // ══════════════════════════════════════════════════════════════════════

    /** أداة اتنفذت فعلاً على السيرفر (مخزون/صيدلية/تسوق/بلد وعملة). */
    data class AgentExecuted(val tool: String, val summary: String)

    /** كتابة على فلوس حقيقية مستنية تأكيد صريح — لسه ماحصلتش. */
    data class AgentProposal(val tool: String, val summary: String, val input: Map<String, Any?>)

    data class AgentTurnResult(
        val reply: String,
        val executed: List<AgentExecuted>,
        val proposals: List<AgentProposal>,
        /** الموديل حاول ينادي أداة (حتى لو اترفضت) — بيفرق عن رد كلام عادي. */
        val toolAttempted: Boolean,
        /** لفة فشلت بعد ما نفّذت كتابات فعلاً. الوقوع على بروتوكول [[ACTION]] هنا بيكرر
         *  نفس الكتابة، فده لازم يتعرض ويترفض معاملته كفشل عادي — حقل صريح بدل ما نستنتجه
         *  من إن [executed]/[proposals] مش فاضيين. */
        val partial: Boolean
    )

    /**
     * لفة محادثة كاملة. بترجع null لو النداء نفسه فشل، عشان الكولر يقدر يقع على مسار
     * الشات القديم بدل ما المستخدم يشوف رسالة خطأ.
     *
     * مفيش `user_id` في الجسم عن قصد: `agent_turn` بياخد هوية المستخدم من الـ JWT اللي
     * `callEdgeFunction` بيبعته أصلاً. الحاجة دي بتكتب معاملات مالية، فهوية من الجسم
     * كانت هتخلي أي حد معاه توكن صالح يكتب في دفتر حد تاني.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun agentTurn(
        message: String,
        history: List<Pair<String, String>>,
        voiceMode: Boolean = false
    ): AgentTurnResult? {
        return try {
            val response = SupabaseRepo.callEdgeFunction(
                BRAIN_FUNCTION,
                mapOf(
                    "action" to "agent_turn",
                    "message" to message,
                    "voice_mode" to voiceMode,
                    "history" to history.map { (role, text) -> mapOf("role" to role, "text" to text) }
                ),
                timeoutMs = AGENT_TURN_TIMEOUT_MS
            )
            if (response["ok"] != true) {
                Log.e(TAG_REPO, "agentTurn() server reported failure: ${response["error"]}")
                return null
            }
            val executed = (response["executed"] as? List<Map<String, Any?>> ?: emptyList()).mapNotNull { row ->
                val summary = row["summary"] as? String ?: return@mapNotNull null
                AgentExecuted(tool = row["tool"] as? String ?: "", summary = summary)
            }
            val proposals = (response["proposals"] as? List<Map<String, Any?>> ?: emptyList()).mapNotNull { row ->
                val tool = row["tool"] as? String ?: return@mapNotNull null
                val input = row["input"] as? Map<String, Any?> ?: return@mapNotNull null
                AgentProposal(tool = tool, summary = row["summary"] as? String ?: tool, input = input)
            }
            AgentTurnResult(
                reply = (response["reply"] as? String).orEmpty().trim(),
                executed = executed,
                proposals = proposals,
                toolAttempted = response["tool_attempted"] == true,
                partial = response["partial"] == true
            )
        } catch (e: Exception) {
            Log.e(TAG_REPO, "agentTurn() FAILED: ${e.message}")
            null
        }
    }

    /**
     * تنفيذ اقتراح بعد موافقة المستخدم. الكلاينت مبيكتبش في الداتابيز بنفسه — بيرجّع
     * الاقتراح للسيرفر اللي بيعيد التحقق منه وينفذه بنفس مسار أي أداة تانية.
     */
    suspend fun agentConfirm(proposal: AgentProposal): Pair<Boolean, String> {
        return try {
            val response = SupabaseRepo.callEdgeFunction(
                BRAIN_FUNCTION,
                mapOf("action" to "agent_confirm", "tool" to proposal.tool, "input" to proposal.input)
            )
            val ok = response["ok"] == true
            ok to ((response["summary"] as? String).orEmpty())
        } catch (e: Exception) {
            Log.e(TAG_REPO, "agentConfirm() FAILED: ${e.message}")
            false to ""
        }
    }

    // ── Zad Intelligence: 6 new features (narrative layer only — every
    // number below is already computed locally in ZadIntelligenceScreen.kt;
    // these calls never invent figures, only phrase them in Arabic) ──

    /** [coverageDays] = null معناها مفيش معدل صرف يومي معروف، فالقسمة مالهاش معنى — بيتبعت
     *  للموديل كـ "غير محسوبة" عشان مايقراش صفر ويقول للمستخدم إنه مكشوف وهو مش مكشوف. */
    suspend fun narrateStressTest(
        coverageDays: Int?,
        avgDailySpend: Double,
        liquidSavings: Double,
        targetDays: Int,
        suggestedMonthlySaving: Double,
        status: String
    ): String? {
        val systemPrompt = "أنت محلل مالي شخصي داخل تطبيق زاد. لخص وضع صمود المستخدم المالي في جملة أو جملتين بالعربي، بدون اختراع أرقام غير الموجودة في البيانات. لو أيام التغطية 'غير محسوبة'، قول إنها لسه محتاجة مصروفات مسجلة أكتر — وممنوع تعتبرها صفر أو تقول إن المستخدم مكشوف."
        val userPrompt = """
            === بيانات اختبار الصمود المالي ===
            أيام التغطية عند الطوارئ: ${coverageDays?.toString() ?: "غير محسوبة (مفيش معدل صرف يومي مرصود)"}
            متوسط الصرف اليومي: $avgDailySpend
            رصيد الطوارئ الحالي: $liquidSavings
            الهدف: $targetDays يوم تغطية
            التوفير الشهري المقترح للوصول للهدف: $suggestedMonthlySaving
            الحالة: $status
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    suspend fun narrateDebtPlan(
        strategy: String,
        totalMonths: Int,
        totalInterestPaid: Double,
        stepsSummary: String
    ): String? {
        val systemPrompt = "أنت مستشار ديون داخل تطبيق زاد. اشرح خطة السداد أدناه بجملتين بالعربي، بدون اختراع أرقام غير الموجودة في البيانات."
        val userPrompt = """
            === بيانات خطة السداد ===
            الاستراتيجية: $strategy
            المدة الكلية: $totalMonths شهر
            إجمالي الفوائد المدفوعة: $totalInterestPaid
            الخطوات:
            $stepsSummary
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    suspend fun narrateInflationRadar(categoriesSummary: String): String? {
        val systemPrompt = "أنت محلل تضخم شخصي داخل تطبيق زاد. لخص أكثر فئة ارتفع صرفها بجملة أو جملتين بالعربي، بدون اختراع أرقام غير الموجودة في البيانات."
        val userPrompt = """
            === بيانات رادار التضخم الشخصي ===
            $categoriesSummary
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    suspend fun narrateNudge(weekday: String, amount: Double, avgOtherDays: Double, spikeRatio: Double): String? {
        val systemPrompt = "أنت مدرب سلوك مالي داخل تطبيق زاد. قدم نصيحة قصيرة وودودة بالعربي حول نمط الصرف أدناه، بدون اختراع أرقام غير الموجودة في البيانات."
        val userPrompt = """
            === بيانات النمط السلوكي ===
            يوم $weekday: صرف $amount, متوسط باقي الأيام $avgOtherDays, نسبة الزيادة ${spikeRatio}x
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    suspend fun narrateBuyingTiming(itemName: String, daysUntil: Int, dailyConsumptionRate: Double): String? {
        val systemPrompt = "أنت مساعد تسوق ذكي داخل تطبيق زاد. اقترح توقيت الشراء المناسب بجملة بالعربي، بدون اختراع أرقام غير الموجودة في البيانات."
        val userPrompt = """
            === بيانات توقيت الشراء ===
            الصنف: $itemName
            الأيام المتبقية: $daysUntil
            معدل الاستهلاك اليومي: $dailyConsumptionRate
            === نهاية البيانات ===
        """.trimIndent()
        return callGeminiText(systemPrompt, userPrompt)
    }

    // ── Deal Matcher / Price Shock Predictor: live web search only, zero mock data.
    // Edge function uses groq/compound (Tavily-backed web_search tool) — every result
    // here is grounded in a real page or it doesn't appear at all; no fallback figures
    // are synthesized anywhere in this path. ──

    suspend fun fetchLiveDealsForInventory(
        shortageItems: List<String>,
        location: String = MarketPrefs.currentMarket.displayNameAr
    ): List<LiveDeal> {
        if (shortageItems.isEmpty()) return emptyList()
        val response = callAction("fetch_live_deals", mapOf("items" to shortageItems, "location" to location))
        // ok:false = real search failure (timeout/HTTP error/unparsable reply), not "genuinely no deals" —
        // throw so the ViewModel's existing catch surfaces LiveFetchState.Error instead of a silent empty list
        if (response["ok"] == false) throw IllegalStateException("fetch_live_deals: upstream search failed")
        val dealsRaw = response["deals"] as? List<*> ?: return emptyList()
        return dealsRaw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            LiveDeal(
                item = map["item"] as? String ?: return@mapNotNull null,
                store = map["store"] as? String ?: return@mapNotNull null,
                price = (map["price"] as? Number)?.toDouble() ?: return@mapNotNull null,
                discountPercent = (map["discount_percent"] as? Number)?.toDouble() ?: 0.0,
                note = map["note"] as? String
            )
        }
    }

    suspend fun fetchLivePriceShockWarnings(
        categories: List<String>,
        location: String = MarketPrefs.currentMarket.displayNameAr
    ): List<PriceShockWarning> {
        if (categories.isEmpty()) return emptyList()
        val response = callAction("fetch_price_shock_warnings", mapOf("categories" to categories, "location" to location))
        if (response["ok"] == false) throw IllegalStateException("fetch_price_shock_warnings: upstream search failed")
        val warningsRaw = response["warnings"] as? List<*> ?: return emptyList()
        return warningsRaw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            PriceShockWarning(
                category = map["category"] as? String ?: return@mapNotNull null,
                expectedChangePct = (map["expected_change_pct"] as? Number)?.toDouble() ?: 0.0,
                direction = map["direction"] as? String ?: "up",
                reasoning = map["reasoning"] as? String ?: "",
                sourceNote = map["source_note"] as? String
            )
        }
    }

    /**
     * شريط أسعار زاد الحي — أسعار سلع أساسية حقيقية (بنزين، طماطم، ذهب...) عبر بحث حي،
     * بكاش 12 ساعة على السيرفر. swallowErrors=false عشان فشل الشبكة يوصل للـ ViewModel
     * كـ Error بدل ما يتقرا "نجح، صفر نتايج". المهلة أطول من الافتراضي لأن أسوأ حالة على
     * السيرفر (إعادة محاولة البحث الحي) بتوصل ~100 ثانية، أطول من الـ 30 ثانية الافتراضية.
     */
    suspend fun fetchLiveMarketPrices(
        location: String = MarketPrefs.currentMarket.displayNameAr
    ): List<MarketPriceItem> {
        val response = callAction(
            "fetch_live_market_prices",
            mapOf("location" to location),
            swallowErrors = false,
            timeoutMs = 110_000
        )
        if (response["ok"] == false) throw IllegalStateException("fetch_live_market_prices: upstream search failed")
        val pricesRaw = response["prices"] as? List<*> ?: return emptyList()
        return pricesRaw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            MarketPriceItem(
                symbol = map["symbol"] as? String ?: return@mapNotNull null,
                price = (map["price"] as? Number)?.toDouble() ?: return@mapNotNull null,
                unit = map["unit"] as? String ?: "",
                changePercent = (map["change_percent"] as? Number)?.toDouble() ?: 0.0,
                trend = map["trend"] as? String ?: "flat"
            )
        }
    }

    suspend fun brainEvaluate(systemPrompt: String, userPrompt: String, appContext: android.content.Context? = null): String? {
        val response = callAction("brain_evaluate", mapOf(
            "system_prompt" to systemPrompt,
            "user_prompt" to userPrompt
        ), appContext = appContext)
        return response["text"] as? String
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

    data class MonthlyExpenseReport(
        val summary: String = "",
        val insights: List<String> = emptyList(),
        val recommendations: List<String> = emptyList(),
        val healthLabel: String = ""
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

    /**
     * تقرير شهري مكتوب — النقلة من "زاد بيقول أرقام" (المتاح جوا ZadIntelligenceScreen
     * أصلاً كروت منفصلة) لـ"زاد بيقرا الأرقام دي ويقول رأيه فيها". كل الأرقام (المتحصّل،
     * المصروف، أعلى فئات) بتتحسب هنا من transactions الحقيقية قبل ما تتبعت — نفس قاعدة
     * auto_suggest إن الموديل ممنوع يخترع رقم مش جاله في المدخلات، هو بس بيكتب الملخص/النصايح.
     */
    suspend fun generateMonthlyExpenseReport(
        transactions: List<ZadTransaction>,
        budget: Double,
        totalIncome: Double,
        totalExpense: Double,
        topCategories: List<Pair<String, Double>>,
        cycleLabel: String
    ): MonthlyExpenseReport {
        val txStr = transactions.takeLast(60).joinToString(", ") { "${it.title}:${it.amount}:${it.category ?: "أخرى"}" }
        val categoriesStr = topCategories.joinToString(", ") { "${it.first}=${it.second}" }
        val response = callAction("monthly_expense_report", mapOf(
            "cycle" to cycleLabel,
            "budget" to budget,
            "total_income" to totalIncome,
            "total_expense" to totalExpense,
            "top_categories" to categoriesStr,
            "transaction_count" to transactions.size,
            "transactions" to txStr
        ))
        val insightsRaw = response["insights"] as? List<*> ?: emptyList<Any>()
        val recsRaw = response["recommendations"] as? List<*> ?: emptyList<Any>()
        return MonthlyExpenseReport(
            summary = response["summary"] as? String ?: "",
            insights = insightsRaw.mapNotNull { it as? String },
            recommendations = recsRaw.mapNotNull { it as? String },
            healthLabel = response["health_label"] as? String ?: ""
        )
    }

    suspend fun getAutoSuggestions(
        context: String,
        inventory: List<ZadInventory> = emptyList(),
        transactions: List<ZadTransaction> = emptyList(),
        patterns: List<ZadBehaviorPattern> = emptyList(),
        appContext: android.content.Context? = null
    ): List<AutoSuggestion> {
        val invStr = inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" }
        val txStr = transactions.takeLast(15).joinToString(", ") { "${it.title}:${it.amount}" }
        val patStr = patterns.joinToString(", ") { "${it.category}:avg=${it.avgAmount}" }
        val response = callAction("auto_suggest", mapOf(
            "context" to context,
            "inventory" to invStr,
            "transactions" to txStr,
            "patterns" to patStr
        ), appContext = appContext)
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
        // 1024px — receipts/medicine-bottle text needs more resolution than 800px gave the
        // vision model to read reliably; still compressed (JPEG q70 below) to stay well under
        // request-size limits.
        val maxWidth = 1024
        val maxHeight = 1024
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

    // geminiApiKey is a vision-only fallback (see analyzeReceipt/analyzeInventoryImage above) —
    // this generic text-action dispatcher always goes straight to zad-core-intelligence so every
    // action gets its proper per-action system prompt and the injected-data delimiting that
    // prompt lives behind, instead of the flat, undelimited prompt a client-side path would need.
    /**
     * swallowErrors=true (الافتراضي) بيرجع emptyMap() على أي فشل — ده كان بيخلي الفشل الحقيقي
     * (شبكة/تايم أوت) مش مميز عن "نجح بس مالقاش نتايج"، لأن اللي بيفحص response["ok"] == false
     * بيلاقي null مش false فيعدّي. أي نداء محتاج يعرض حالة خطأ حقيقية للمستخدم لازم يبعت
     * swallowErrors=false عشان الاستثناء يوصله.
     */
    /**
     * @param appContext غير null بس للأكشنات اللي الشاشة الرئيسية بتنده كل فتحة. وجوده
     *   بيشغّل [AiLocalCache]: نفس المدخلات = نفس البصمة = رد محلي من غير ما نلمس الشبكة.
     *   السيرفر عنده الكاش بتاعه بالفعل (ai_response_cache) فالكوتة محميّة من غير ده — اللي
     *   بيوفّره الكاش المحلي هو الأربع رحلات الشبكة نفسها في كل فتحة للتطبيق: استدعاءات
     *   edge function محسوبة، وانتظار ظاهر للمستخدم لو الدالة باردة. الأكشنات التانية
     *   (المسح، الشات، البحث) بتعدّي زي ما هي لأن `appContext` بيفضل null عندها.
     */
    private suspend fun callAction(
        action: String,
        payload: Map<String, Any?>,
        swallowErrors: Boolean = true,
        timeoutMs: Long = SupabaseRepo.DEFAULT_EDGE_TIMEOUT_MS,
        appContext: android.content.Context? = null
    ): Map<String, Any?> {
        val userId = getUserId()
        if (appContext != null) {
            AiLocalCache.get(appContext, userId, action, payload)?.let { return it }
        }
        return try {
            val response = SupabaseRepo.callEdgeFunction(CENTRAL_FUNCTION, mapOf(
                "action" to action,
                "user_id" to userId,
                "dialect" to MarketPrefs.currentMarket.dialectInstruction,
                "payload" to payload
            ), timeoutMs = timeoutMs)
            // فشل بيرجع emptyMap من الـ catch تحت — والكاش بيرفض يخزّن رد فاضي، فخطأ
            // شبكة عابر مايتخزنش ست ساعات جاية.
            if (appContext != null) AiLocalCache.put(appContext, userId, action, payload, response)
            response
        } catch (e: Exception) {
            Log.e(TAG_REPO, "callAction($action) FAILED: ${e.message}")
            if (!swallowErrors) throw e
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
