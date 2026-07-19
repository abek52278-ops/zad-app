package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * ZadCentralBrain — العقل المركزي الموحد للتطبيق
 * يدير كل الذكاءات: تحليل، تنبؤ، اكتشاف أنماط، إشعارات ذكية، توصيات
 * يتعلم من سلوك المستخدم ويتنبأ بالتصرفات المستقبلية
 */
object ZadCentralBrain {
    private const val TAG = "ZadCentralBrain"

    data class BrainOutput(
        val summary: String = "",
        val alerts: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val predictions: List<String> = emptyList(),
        val autoActions: List<AutoAction> = emptyList(),
        val smartNotifications: List<SmartNotification> = emptyList()
    )

    data class AutoAction(
        val type: String, // ADD_TO_SHOPPING | UPDATE_SUBSCRIPTION | SEND_NOTIFICATION | SUGGEST_RECIPE
        val payload: String,
        val reason: String
    )

    data class SmartNotification(
        val type: String, // PREDICTIVE | BEHAVIOR_ALERT | TIP | MILESTONE | FAMILY
        val title: String,
        val body: String,
        val priority: String = "NORMAL", // HIGH | NORMAL | LOW
        val actionPayload: String? = null
    )

    suspend fun fullAnalysis(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        shoppingList: List<ZadShoppingItem>,
        behaviorPatterns: List<ZadBehaviorPattern>,
        budget: Double
    ): BrainOutput = withContext(Dispatchers.IO) {
        Log.d(TAG, "fullAnalysis() started — learning user behavior...")
        val alerts = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val predictions = mutableListOf<String>()
        val autoActions = mutableListOf<AutoAction>()
        val smartNotifications = mutableListOf<SmartNotification>()
        val today = LocalDate.now()

        // ====== 1. BEHAVIOR LEARNING ENGINE ======
        val behaviorInsights = learnBehavior(transactions, behaviorPatterns)
        predictions.addAll(behaviorInsights.predictions)
        if (behaviorInsights.anomalyAlert != null) {
            alerts.add(behaviorInsights.anomalyAlert)
            smartNotifications.add(SmartNotification(
                type = "BEHAVIOR_ALERT",
                title = "⚠️ نشاط غير معتاد",
                body = behaviorInsights.anomalyAlert,
                priority = "HIGH"
            ))
        }

        // ====== 2. PREDICTIVE ANALYTICS ======
        val nextWeekExpense = predictWeeklySpending(transactions)
        if (nextWeekExpense > 0) {
            predictions.add("توقع إنفاق الأسبوع القادم: $nextWeekExpense ر.س")
            if (budget > 0 && nextWeekExpense > budget * 0.3) {
                smartNotifications.add(SmartNotification(
                    type = "PREDICTIVE",
                    title = "📊 توقع إنفاق مرتفع",
                    body = "نتوقع إنفاق $nextWeekExpense ر.س الأسبوع القادم (أكثر من 30% من ميزانيتك)",
                    priority = "HIGH"
                ))
            }
        }

        // ====== 3. INVENTORY INTELLIGENCE ======
        val lowStockItems = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }
        val expiringSoon = inventory.filter { item ->
            item.expiryDate?.let {
                try { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) in 0..3 }
                catch (e: Exception) { false }
            } ?: false
        }

        lowStockItems.forEach { item ->
            val alreadyInShopping = shoppingList.any { it.itemName == item.itemName && !it.isPurchased }
            if (!alreadyInShopping) {
                autoActions.add(AutoAction("ADD_TO_SHOPPING", item.itemName, "مخزون منخفض: ${item.itemName} (${item.quantity} متبقي)"))
            }
            // Predictive: Suggest restock based on usage pattern
            val pattern = behaviorPatterns.find { it.category.contains(item.itemName, ignoreCase = true) }
            if (pattern != null) {
                predictions.add("عادة تشتري ${item.itemName} كل ${pattern.frequencyDays} يوم — حان وقت الشراء")
            }
        }

        if (lowStockItems.isNotEmpty()) {
            alerts.add("مخزون منخفض: ${lowStockItems.joinToString(", ") { it.itemName }}")
        }

        if (expiringSoon.isNotEmpty()) {
            val names = expiringSoon.joinToString(", ") { it.itemName }
            suggestions.add("اقترح وصفات تستخدم: $names قبل انتهاء الصلاحية")
            autoActions.add(AutoAction("SUGGEST_RECIPE", names, "أصناف على وشك الانتهاء"))
            smartNotifications.add(SmartNotification(
                type = "PREDICTIVE",
                title = "⏳ منتجات على وشك الانتهاء",
                body = "${expiringSoon.size} أصناف تنتهي خلال 3 أيام: $names",
                priority = "HIGH"
            ))
        }

        // ====== 4. SUBSCRIPTION INTELLIGENCE ======
        val activeSubs = subscriptions.filter { it.isActive }
        activeSubs.forEach { sub ->
            if (!sub.renewalDate.isNullOrBlank()) {
                try {
                    val renewDate = LocalDate.parse(sub.renewalDate.take(10))
                    val daysLeft = ChronoUnit.DAYS.between(today, renewDate)
                    when {
                        daysLeft == 0L -> {
                            alerts.add("🔔 ${sub.title} يُجدد اليوم! (${sub.amount.toInt()} ر.س)")
                            smartNotifications.add(SmartNotification("PREDICTIVE", "🔔 تجديد اليوم", "${sub.title} يُجدد اليوم بمبلغ ${sub.amount.toInt()} ر.س", "HIGH"))
                        }
                        daysLeft in 1..3 -> alerts.add("🔔 ${sub.title} يتجدد بعد $daysLeft أيام (${sub.amount.toInt()} ر.س)")
                        daysLeft in 4..7 -> suggestions.add("تذكير: ${sub.title} سيتجدد بعد أسبوع (وفّر ${sub.amount.toInt()} ر.س)")
                    }
                } catch (e: Exception) { }
            }
        }

        // ====== 5. FINCIAL ANOMALY DETECTION ======
        if (transactions.size >= 5) {
            val expenses = transactions.filter { it.isExpense }.map { it.amount }
            val mean = expenses.average()
            val stdDev = kotlin.math.sqrt(expenses.map { (it - mean).let { d -> d * d } }.average())
            val recentWeekExpense = expenses.takeLast(7).sum()
            val weeklyMean = mean * 7

            if (recentWeekExpense > weeklyMean + 2 * stdDev) {
                val pctOver = ((recentWeekExpense - weeklyMean) / weeklyMean * 100).toInt()
                val msg = "💰 إنفاق غير مألوف: $pctOver% زيادة هذا الأسبوع (${recentWeekExpense.toInt()} ر.س)"
                alerts.add(msg)
                smartNotifications.add(SmartNotification("BEHAVIOR_ALERT", "💰 نشاط إنفاق غير معتاد", msg, "HIGH"))
            }
        }

        // ====== 6. BUDGET TRACKING ======
        val totalSpent = transactions.filter { it.isExpense }.sumOf { it.amount }
        if (budget > 0) {
            val pct = (totalSpent / budget * 100).toInt()
            val remaining = budget - totalSpent
            when {
                pct >= 100 -> alerts.add("🚨 تجاوزت الميزانية! أنفقت ${totalSpent.toInt()} ر.س من $budget ر.س")
                pct >= 85 -> {
                    alerts.add("⚠️ الميزانية على وشك النفاد: $pct% مستخدم")
                    smartNotifications.add(SmartNotification("PREDICTIVE", "⚠️ الميزانية تنفد", "استخدمت $pct% من ميزانيتك (متبقي $remaining ر.س)", "HIGH"))
                }
                pct >= 70 -> suggestions.add("أنفقت $pct% من الميزانية (متبقي $remaining ر.س)")
            }
            predictions.add("بناءً على إنفاقك الحالي، الميزانية تكفي لـ ${(budget / (totalSpent / 30.0)).toInt()} يوم إضافي")
        }

        // ====== 7. MILESTONES & ACHIEVEMENTS ======
        val savingsTransactions = transactions.filter { !it.isExpense }
        if (savingsTransactions.isNotEmpty()) {
            val totalSavings = savingsTransactions.sumOf { it.amount }
            if (totalSavings > 5000) {
                smartNotifications.add(SmartNotification("MILESTONE", "🏆 إنجاز توفير كبير",
                    "وفرت $totalSavings ر.س حتى الآن! استمر 👏", "HIGH"))
            }
        }

        if (inventory.size >= 10) {
            smartNotifications.add(SmartNotification("MILESTONE", "📦 مخزون ممتاز",
                "لديك ${inventory.size} صنف في المخزون — منظم جداً!", "LOW"))
        }

        // ====== 8. GROQ AI ENHANCED SUMMARY ======
        var summary = generateLocalSummary(inventory, transactions, budget, totalSpent)
        try {
            val aiSummary = ZadAiRepository.getAgentSummary(
                inventory, transactions, subscriptions, budget, shoppingList, behaviorPatterns
            )
            if (aiSummary != null) {
                summary = aiSummary.summary.ifBlank { summary }
                aiSummary.suggestions.forEach { s ->
                    suggestions.add("${s.action}: ${s.item} — ${s.reason}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed: ${e.message}")
        }

        Log.d(TAG, "fullAnalysis() done → ${alerts.size} alerts, ${suggestions.size} suggestions, ${predictions.size} predictions, ${smartNotifications.size} smart notifs")
        BrainOutput(
            summary = summary,
            alerts = alerts,
            suggestions = suggestions,
            predictions = predictions,
            autoActions = autoActions,
            smartNotifications = smartNotifications
        )
    }

    // ===================================================================
    // BEHAVIOR LEARNING ENGINE — يتعلم أنماط المستخدم ويتنبأ بالسلوك
    // ===================================================================
    private data class BehaviorInsights(
        val predictions: List<String> = emptyList(),
        val anomalyAlert: String? = null
    )

    private fun learnBehavior(
        transactions: List<ZadTransaction>,
        existingPatterns: List<ZadBehaviorPattern>
    ): BehaviorInsights {
        val predictions = mutableListOf<String>()
        var anomalyAlert: String? = null
        val today = LocalDate.now()

        if (transactions.size < 3) return BehaviorInsights()

        // Group transactions by category
        val byCategory = transactions.filter { it.isExpense }.groupBy { it.category ?: "عام" }

        byCategory.forEach { (category, txs) ->
            if (txs.size >= 3) {
                val amounts = txs.map { it.amount }
                val avg = amounts.average()
                val recent = amounts.last()
                val stdDev = kotlin.math.sqrt(amounts.map { (it - avg).let { d -> d * d } }.average())

                // Detect spending anomaly
                if (recent > avg + 2 * stdDev && stdDev > 0) {
                    val pctUp = ((recent - avg) / avg * 100).toInt()
                    anomalyAlert = "📈 إنفاق غير معتاد في $category: $pctUp% زيادة عن المعدل (المعدل: $avg ر.س، الآن: $recent ر.س)"
                }

                // Predict next spending
                val dates = txs.mapNotNull { tx ->
                    tx.createdAt?.let {
                        try { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                        catch (e: Exception) { null }
                    }
                }
                if (dates.size >= 2) {
                    val intervals = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
                    val avgInterval = intervals.average().toInt()
                    if (avgInterval in 1..60 && avgInterval > 0) {
                        val lastDate = dates.maxOrNull()
                        val nextExpected = lastDate?.plusDays(avgInterval.toLong())
                        if (nextExpected != null && nextExpected <= today.plusDays(3)) {
                            predictions.add("عادة تشتري $category كل $avgInterval يوم — قد تحتاجها قريباً")
                        }
                    }
                }
            }
        }

        // Check existing patterns for restock predictions
        existingPatterns.forEach { pattern ->
            val lastUpdated = pattern.lastUpdated?.let {
                try { LocalDate.parse(it) } catch (e: Exception) { null }
            }
            if (lastUpdated != null && pattern.frequencyDays > 0) {
                val nextExpected = lastUpdated.plusDays(pattern.frequencyDays.toLong())
                val daysUntilNext = ChronoUnit.DAYS.between(today, nextExpected)
                if (daysUntilNext in 0..3) {
                    predictions.add("🔄 حان وقت مراجعة ${pattern.category} — اعتدت فعل ذلك كل ${pattern.frequencyDays} يوم")
                }
            }
        }

        return BehaviorInsights(predictions = predictions, anomalyAlert = anomalyAlert)
    }

    private fun predictWeeklySpending(transactions: List<ZadTransaction>): Double {
        if (transactions.size < 7) return 0.0
        val weeklyExpenses = transactions.filter { it.isExpense }.takeLast(14)
        if (weeklyExpenses.isEmpty()) return 0.0
        val weeklyAvg = weeklyExpenses.sumOf { it.amount } / 2.0
        return kotlin.math.round(weeklyAvg * 100) / 100
    }

    private fun generateLocalSummary(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        budget: Double,
        totalSpent: Double
    ): String {
        val invCount = inventory.size
        val txCount = transactions.filter { it.isExpense }.size
        val budgetPct = if (budget > 0) (totalSpent / budget * 100).toInt() else 0
        return "📊 عندك $invCount صنف في المخزون، $txCount معاملة مصروفات، $budgetPct% من الميزانية مستخدمة."
    }

    suspend fun quickHealthCheck(inventory: List<ZadInventory>): List<String> {
        val issues = mutableListOf<String>()
        val today = LocalDate.now()
        inventory.forEach { item ->
            item.expiryDate?.let {
                try {
                    val days = ChronoUnit.DAYS.between(today, LocalDate.parse(it))
                    if (days == 0L) issues.add("${item.itemName} ينتهي اليوم!")
                    else if (days < 0) issues.add("${item.itemName} منتهي منذ ${-days} يوم!")
                } catch (e: Exception) { }
            }
            if (item.quantity <= (item.lowStockThreshold ?: 2)) {
                issues.add("${item.itemName}: مخزون منخفض (${item.quantity})")
            }
        }
        return issues
    }

    /**
     * Quick analysis for smart notifications from any part of the app
     */
    suspend fun generateSmartNotifications(
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        behaviorPatterns: List<ZadBehaviorPattern>
    ): List<SmartNotification> {
        val result = fullAnalysis(
            inventory = inventory,
            transactions = transactions,
            subscriptions = subscriptions,
            shoppingList = emptyList(),
            behaviorPatterns = behaviorPatterns,
            budget = 0.0
        )
        return result.smartNotifications
    }
}
