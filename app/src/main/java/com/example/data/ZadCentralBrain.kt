package com.example.data

import android.content.Context
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
        context: Context,
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
        val behaviorInsights = learnBehavior(context, transactions, behaviorPatterns)
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
            predictions.add("توقع إنفاق الأسبوع القادم: ${CurrencyFormatter.format(context, nextWeekExpense)}")
            if (budget > 0 && nextWeekExpense > budget * 0.3) {
                smartNotifications.add(SmartNotification(
                    type = "PREDICTIVE",
                    title = "📊 توقع إنفاق مرتفع",
                    body = "نتوقع إنفاق ${CurrencyFormatter.format(context, nextWeekExpense)} الأسبوع القادم (أكثر من 30% من ميزانيتك)",
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
                            alerts.add("🔔 ${sub.title} يُجدد اليوم! (${CurrencyFormatter.format(context, sub.amount)})")
                            smartNotifications.add(SmartNotification("PREDICTIVE", "🔔 تجديد اليوم", "${sub.title} يُجدد اليوم بمبلغ ${CurrencyFormatter.format(context, sub.amount)}", "HIGH"))
                        }
                        daysLeft in 1..3 -> alerts.add("🔔 ${sub.title} يتجدد بعد $daysLeft أيام (${CurrencyFormatter.format(context, sub.amount)})")
                        daysLeft in 4..7 -> suggestions.add("تذكير: ${sub.title} سيتجدد بعد أسبوع (وفّر ${CurrencyFormatter.format(context, sub.amount)})")
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
                val msg = "💰 إنفاق غير مألوف: $pctOver% زيادة هذا الأسبوع (${CurrencyFormatter.format(context, recentWeekExpense)})"
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
                pct >= 100 -> alerts.add("🚨 تجاوزت الميزانية! أنفقت ${CurrencyFormatter.format(context, totalSpent)} من ${CurrencyFormatter.format(context, budget)}")
                pct >= 85 -> {
                    alerts.add("⚠️ الميزانية على وشك النفاد: $pct% مستخدم")
                    smartNotifications.add(SmartNotification("PREDICTIVE", "⚠️ الميزانية تنفد", "استخدمت $pct% من ميزانيتك (متبقي ${CurrencyFormatter.format(context, remaining)})", "HIGH"))
                }
                pct >= 70 -> suggestions.add("أنفقت $pct% من الميزانية (متبقي ${CurrencyFormatter.format(context, remaining)})")
            }
            predictions.add("بناءً على إنفاقك الحالي، الميزانية تكفي لـ ${(budget / (totalSpent / 30.0)).toInt()} يوم إضافي")
        }

        // ====== 7. MILESTONES & ACHIEVEMENTS ======
        val savingsTransactions = transactions.filter { !it.isExpense }
        if (savingsTransactions.isNotEmpty()) {
            val totalSavings = savingsTransactions.sumOf { it.amount }
            if (totalSavings > 5000) {
                smartNotifications.add(SmartNotification("MILESTONE", "🏆 إنجاز توفير كبير",
                    "وفرت ${CurrencyFormatter.format(context, totalSavings)} حتى الآن! استمر 👏", "HIGH"))
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
        context: Context,
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
                    anomalyAlert = "📈 إنفاق غير معتاد في $category: $pctUp% زيادة عن المعدل (المعدل: ${CurrencyFormatter.format(context, avg)}، الآن: ${CurrencyFormatter.format(context, recent)})"
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

    // ===================================================================
    // BRAIN REPORT — التقرير المهيكل لصفحة ذكاء زاد (أرقام حقيقية للجرافات)
    // ===================================================================

    data class CategorySpend(
        val category: String,
        val spent: Double,
        val budget: Double
    ) {
        val pctUsed: Int get() = if (budget > 0) ((spent / budget) * 100).toInt() else 0
        val isOverBudget: Boolean get() = budget > 0 && spent > budget
    }

    data class DailySpend(val date: LocalDate, val amount: Double)

    data class DepletionForecast(val itemName: String, val quantity: Int, val predictedDaysLeft: Int)

    /** قوة الصرف — كم يقدر يصرف يومياً بأمان حتى نهاية الشهر */
    data class SpendingPower(
        val dailySafeSpend: Double,     // المسموح يومياً من المتبقي
        val currentDailyAvg: Double,    // معدل صرفه الفعلي يومياً
        val daysLeftInMonth: Int,
        val powerPct: Int,              // 0-100: المتبقي كنسبة من البادجت
        val status: String              // قوي / متوازن / ضعيف / خطر
    )

    /** البروفايل السلوكي العميق — محسوب محلياً من كل المعاملات */
    data class BehaviorProfile(
        val topSpendingDay: String,         // أكثر يوم أسبوع صرفاً
        val topSpendingDayAvg: Double,
        val weekendSharePct: Int,           // نسبة صرف الويكند (جمعة+سبت)
        val avgTransaction: Double,
        val impulsePurchases: Int,          // معاملات > ضعف المتوسط خلال 30 يوم
        val biggestExpenseTitle: String,
        val biggestExpenseAmount: Double,
        val eveningSharePct: Int            // نسبة الصرف بعد 6 مساءً
    )

    /** مقارنة هذا الشهر بالشهر الماضي */
    data class MonthComparison(
        val thisMonthSpent: Double,
        val lastMonthSpent: Double,         // للنفس اليوم من الشهر الماضي (مقارنة عادلة)
        val lastMonthTotal: Double,
        val deltaPct: Int,                  // موجب = صرف أكثر
        val categoryDeltas: List<Triple<String, Double, Double>> // (فئة، هذا الشهر، الماضي)
    )

    data class BrainReport(
        val healthScore: Int,                       // 0-100 صحة مالية عامة
        val healthLabel: String,                    // ممتاز / جيد / يحتاج انتباه / خطر
        val totalSpent: Double,
        val totalIncome: Double,
        val budget: Double,
        val remaining: Double,
        val categoryBreakdown: List<CategorySpend>, // للدونات والبارات
        val dailyTrend: List<DailySpend>,           // آخر 14 يوم للجراف الخطي
        val topMerchants: List<Pair<String, Double>>,
        val subscriptionsMonthlyCost: Double,
        val inventoryTotal: Int,
        val inventoryLowStock: Int,
        val inventoryExpiringSoon: Int,
        val depletionForecasts: List<DepletionForecast>, // تنبؤات النفاد من التعلم
        val insights: List<String>,                 // ملاحظات جاهزة للعرض
        val spendingPower: SpendingPower,           // عداد قوة الصرف
        val behaviorProfile: BehaviorProfile?,      // التحليل السلوكي العميق (null لو البيانات قليلة)
        val monthComparison: MonthComparison?       // مقارنة شهرية (null لو مفيش شهر سابق)
    )

    /**
     * التقرير الشامل — يجمع كل المحركات في مخرج واحد مهيكل:
     * BudgetTracker (كروت الفئات) + ConsumptionLearner (تنبؤ النفاد) + المعاملات + المخزون
     */
    suspend fun generateReport(
        context: Context,
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        budget: Double
    ): BrainReport = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)

        fun txDate(tx: ZadTransaction): LocalDate? = tx.createdAt?.let {
            try { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            catch (e: Exception) { try { LocalDate.parse(it.take(10)) } catch (e2: Exception) { null } }
        }

        val monthTx = transactions.filter { (txDate(it) ?: today) >= monthStart }
        val totalSpent = monthTx.filter { it.isExpense }.sumOf { it.amount }
        val totalIncome = monthTx.filter { !it.isExpense }.sumOf { it.amount }
        val remaining = BudgetTracker.getRemaining(context)

        // 1) كروت الفئات: المصروف الفعلي من المعاملات + البادجت من BudgetTracker
        val spentByCategory = monthTx.filter { it.isExpense }
            .groupBy { it.category ?: "أخرى" }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        val categoryBreakdown = BudgetTracker.STANDARD_CATEGORIES
            .map { cat ->
                CategorySpend(
                    category = cat,
                    spent = spentByCategory[cat] ?: BudgetTracker.getCategorySpent(context, cat),
                    budget = BudgetTracker.getCategoryBudget(context, cat)
                )
            }
            .filter { it.spent > 0 || it.budget > 0 }
            .sortedByDescending { it.spent }

        // 2) الاتجاه اليومي — آخر 14 يوم
        val dailyTrend = (0..13).map { offset ->
            val day = today.minusDays((13 - offset).toLong())
            val amount = transactions.filter { it.isExpense && txDate(it) == day }.sumOf { it.amount }
            DailySpend(day, amount)
        }

        // 3) أعلى التجار
        val topMerchants = monthTx.filter { it.isExpense }
            .groupBy { it.merchantName ?: it.title }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5)
            .map { it.key to it.value }

        // 4) الاشتراكات الشهرية
        val subsMonthlyCost = subscriptions.filter { it.isActive }.sumOf { it.amount }

        // 5) صحة المخزون + تنبؤات النفاد من ConsumptionLearner
        val lowStock = inventory.count { it.quantity <= (it.lowStockThreshold ?: 2) }
        val expiringSoon = inventory.count { item ->
            item.expiryDate?.let {
                try { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) in 0..3 }
                catch (e: Exception) { false }
            } ?: false
        }
        val depletionForecasts = inventory.mapNotNull { item ->
            val daysLeft = ConsumptionLearner.predictDaysLeft(context, item.itemName, item.quantity)
                ?: return@mapNotNull null
            DepletionForecast(item.itemName, item.quantity, daysLeft)
        }.sortedBy { it.predictedDaysLeft }.take(8)

        // 6) نقاط الصحة المالية (0-100)
        var score = 100
        if (budget > 0) {
            val pctUsed = (totalSpent / budget * 100).toInt()
            score -= when {
                pctUsed >= 100 -> 40
                pctUsed >= 90 -> 30
                pctUsed >= 75 -> 15
                else -> 0
            }
        }
        score -= categoryBreakdown.count { it.isOverBudget } * 8
        if (inventory.isNotEmpty()) {
            score -= ((lowStock.toDouble() / inventory.size) * 20).toInt()
            score -= (expiringSoon * 3).coerceAtMost(10)
        }
        if (budget > 0 && subsMonthlyCost > budget * 0.25) score -= 10
        val healthScore = score.coerceIn(0, 100)
        val healthLabel = when {
            healthScore >= 85 -> "ممتاز 🌟"
            healthScore >= 65 -> "جيد 👍"
            healthScore >= 40 -> "يحتاج انتباه ⚠️"
            else -> "خطر 🚨"
        }

        // 7) ملاحظات جاهزة
        val insights = mutableListOf<String>()
        categoryBreakdown.firstOrNull()?.let {
            insights.add("أعلى إنفاقك هذا الشهر: ${it.category} (${CurrencyFormatter.format(context, it.spent)})")
        }
        categoryBreakdown.filter { it.isOverBudget }.forEach {
            insights.add("⛔ تجاوزت ميزانية ${it.category} بـ${CurrencyFormatter.format(context, it.spent - it.budget)}")
        }
        if (subsMonthlyCost > 0) {
            insights.add("اشتراكاتك النشطة تكلفك ${CurrencyFormatter.format(context, subsMonthlyCost)} شهرياً (${CurrencyFormatter.format(context, subsMonthlyCost * 12)} سنوياً)")

            // اقتراح إلغاء: نسبة الاشتراكات من الميزانية مرتفعة
            if (budget > 0 && subsMonthlyCost > budget * 0.2) {
                val mostExpensive = subscriptions.filter { it.isActive }.maxByOrNull { it.amount }
                if (mostExpensive != null) {
                    insights.add("💡 اشتراكاتك ${(subsMonthlyCost / budget * 100).toInt()}% من ميزانيتك — راجع ${mostExpensive.title} (${CurrencyFormatter.format(context, mostExpensive.amount)}) لو مش مستخدمه")
                }
            }

            // كشف خدمات البث المكررة
            val streamingKeywords = listOf("netflix", "نتفلكس", "شاهد", "shahid", "osn", "prime", "disney")
            val streamingSubs = subscriptions.filter { sub ->
                sub.isActive && streamingKeywords.any { sub.title.lowercase().contains(it) }
            }
            if (streamingSubs.size >= 2) {
                val cheapest = streamingSubs.minByOrNull { it.amount }
                insights.add("📺 عندك ${streamingSubs.size} خدمات بث — إلغاء واحدة يوفر لك ${CurrencyFormatter.format(context, cheapest?.amount ?: 0.0)} شهرياً")
            }
        }
        depletionForecasts.firstOrNull { it.predictedDaysLeft <= 2 }?.let {
            insights.add("🛒 ${it.itemName} متوقع يخلص خلال ${it.predictedDaysLeft.coerceAtLeast(0)} يوم")
        }
        val avgDaily = dailyTrend.map { it.amount }.filter { it > 0 }.ifEmpty { listOf(0.0) }.average()
        if (avgDaily > 0 && remaining > 0) {
            insights.add("بمعدل إنفاقك الحالي (${CurrencyFormatter.format(context, avgDaily)}/يوم)، الرصيد يكفي ${(remaining / avgDaily).toInt()} يوم")
        }

        // 8) قوة الصرف — كم يقدر يصرف يومياً بأمان
        val daysLeftInMonth = (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        val dailySafeSpend = (remaining / daysLeftInMonth).coerceAtLeast(0.0)
        val currentDailyAvg = if (today.dayOfMonth > 0) totalSpent / today.dayOfMonth else 0.0
        val powerPct = if (budget > 0) ((remaining / budget) * 100).toInt().coerceIn(0, 100) else 100
        val spendingPower = SpendingPower(
            dailySafeSpend = dailySafeSpend,
            currentDailyAvg = currentDailyAvg,
            daysLeftInMonth = daysLeftInMonth,
            powerPct = powerPct,
            status = when {
                powerPct >= 60 -> "قوي 💪"
                powerPct >= 35 -> "متوازن ⚖️"
                powerPct >= 15 -> "ضعيف ⚠️"
                else -> "خطر 🚨"
            }
        )

        // 9) البروفايل السلوكي العميق
        val allExpenses = transactions.filter { it.isExpense }
        val behaviorProfile = if (allExpenses.size >= 5) {
            val arabicDays = mapOf(
                DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد",
                DayOfWeek.MONDAY to "الاثنين", DayOfWeek.TUESDAY to "الثلاثاء",
                DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس",
                DayOfWeek.FRIDAY to "الجمعة"
            )
            fun txDateTime(tx: ZadTransaction) = tx.createdAt?.let {
                try { Instant.parse(it).atZone(ZoneId.systemDefault()) } catch (e: Exception) { null }
            }
            val byDay = allExpenses.mapNotNull { tx -> txDateTime(tx)?.let { it.dayOfWeek to tx.amount } }
                .groupBy({ it.first }, { it.second })
            val topDay = byDay.maxByOrNull { it.value.sum() }
            val weekendSpent = allExpenses.mapNotNull { tx ->
                txDateTime(tx)?.takeIf { it.dayOfWeek == DayOfWeek.FRIDAY || it.dayOfWeek == DayOfWeek.SATURDAY }
                    ?.let { tx.amount }
            }.sum()
            val totalAll = allExpenses.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0
            val avgTx = allExpenses.map { it.amount }.average()
            val last30 = allExpenses.filter { tx ->
                txDateTime(tx)?.toLocalDate()?.isAfter(today.minusDays(30)) == true
            }
            val eveningSpent = allExpenses.mapNotNull { tx ->
                txDateTime(tx)?.takeIf { it.hour >= 18 }?.let { tx.amount }
            }.sum()
            val biggest = allExpenses.maxByOrNull { it.amount }
            BehaviorProfile(
                topSpendingDay = topDay?.let { arabicDays[it.key] } ?: "غير معروف",
                topSpendingDayAvg = topDay?.value?.average() ?: 0.0,
                weekendSharePct = (weekendSpent / totalAll * 100).toInt(),
                avgTransaction = avgTx,
                impulsePurchases = last30.count { it.amount > avgTx * 2 },
                biggestExpenseTitle = biggest?.title ?: "",
                biggestExpenseAmount = biggest?.amount ?: 0.0,
                eveningSharePct = (eveningSpent / totalAll * 100).toInt()
            )
        } else null

        // 10) مقارنة هذا الشهر بالشهر الماضي (لنفس اليوم — مقارنة عادلة)
        val lastMonthStart = monthStart.minusMonths(1)
        val lastMonthTx = transactions.filter {
            val d = txDate(it) ?: return@filter false
            d >= lastMonthStart && d < monthStart
        }
        val monthComparison = if (lastMonthTx.any { it.isExpense }) {
            val sameDayCutoff = lastMonthStart.plusDays((today.dayOfMonth - 1).toLong())
            val lastMonthSameDay = lastMonthTx.filter { it.isExpense && (txDate(it) ?: lastMonthStart) <= sameDayCutoff }
                .sumOf { it.amount }
            val lastMonthTotal = lastMonthTx.filter { it.isExpense }.sumOf { it.amount }
            val lastByCat = lastMonthTx.filter { it.isExpense }
                .groupBy { it.category ?: "أخرى" }.mapValues { (_, t) -> t.sumOf { it.amount } }
            val allCats = (spentByCategory.keys + lastByCat.keys).distinct()
            MonthComparison(
                thisMonthSpent = totalSpent,
                lastMonthSpent = lastMonthSameDay,
                lastMonthTotal = lastMonthTotal,
                deltaPct = if (lastMonthSameDay > 0)
                    (((totalSpent - lastMonthSameDay) / lastMonthSameDay) * 100).toInt() else 0,
                categoryDeltas = allCats
                    .map { cat -> Triple(cat, spentByCategory[cat] ?: 0.0, lastByCat[cat] ?: 0.0) }
                    .sortedByDescending { kotlin.math.abs(it.second - it.third) }
                    .take(5)
            )
        } else null

        // ملاحظات إضافية من المحركات الجديدة
        if (dailySafeSpend > 0 && currentDailyAvg > dailySafeSpend) {
            insights.add(0, "⚡ معدل صرفك اليومي (${CurrencyFormatter.format(context, currentDailyAvg)}) أعلى من الآمن (${CurrencyFormatter.format(context, dailySafeSpend)}) — خفف شوية")
        }
        monthComparison?.let { mc ->
            if (mc.deltaPct <= -10) insights.add(0, "🎉 صرفت ${-mc.deltaPct}% أقل من نفس الفترة الشهر الماضي — وفرت ${CurrencyFormatter.format(context, mc.lastMonthSpent - mc.thisMonthSpent)}!")
            else if (mc.deltaPct >= 15) insights.add(0, "📈 صرفك زاد ${mc.deltaPct}% عن نفس الفترة الشهر الماضي")
        }
        behaviorProfile?.let { bp ->
            if (bp.impulsePurchases >= 3) insights.add("🛍️ ${bp.impulsePurchases} مشتريات اندفاعية آخر 30 يوم (أكبر من ضعف متوسطك)")
        }

        Log.d(TAG, "generateReport() → score=$healthScore, categories=${categoryBreakdown.size}, forecasts=${depletionForecasts.size}")
        BrainReport(
            healthScore = healthScore,
            healthLabel = healthLabel,
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            budget = budget,
            remaining = remaining,
            categoryBreakdown = categoryBreakdown,
            dailyTrend = dailyTrend,
            topMerchants = topMerchants,
            subscriptionsMonthlyCost = subsMonthlyCost,
            inventoryTotal = inventory.size,
            inventoryLowStock = lowStock,
            inventoryExpiringSoon = expiringSoon,
            depletionForecasts = depletionForecasts,
            insights = insights,
            spendingPower = spendingPower,
            behaviorProfile = behaviorProfile,
            monthComparison = monthComparison
        )
    }

    /** تقرير نصي كامل جاهز للمشاركة/التصدير (واتساب، إيميل، ملف) */
    fun buildExportText(context: Context, report: BrainReport): String = buildString {
        val today = LocalDate.now()
        appendLine("📊 تقرير زاد المالي — ${today.month.value}/${today.year}")
        appendLine("═══════════════════════════")
        appendLine("الصحة المالية: ${report.healthScore}/100 (${report.healthLabel})")
        appendLine("قوة الصرف: ${report.spendingPower.status} — الآمن يومياً: ${CurrencyFormatter.format(context, report.spendingPower.dailySafeSpend)}")
        appendLine()
        appendLine("💰 الأرقام:")
        appendLine("• الميزانية: ${CurrencyFormatter.format(context, report.budget)}")
        appendLine("• المصروف: ${CurrencyFormatter.format(context, report.totalSpent)}")
        appendLine("• الدخل: ${CurrencyFormatter.format(context, report.totalIncome)}")
        appendLine("• المتبقي: ${CurrencyFormatter.format(context, report.remaining)}")
        if (report.subscriptionsMonthlyCost > 0)
            appendLine("• الاشتراكات: ${CurrencyFormatter.format(context, report.subscriptionsMonthlyCost)}/شهر")
        report.monthComparison?.let { mc ->
            appendLine()
            appendLine("📅 مقارنة بالشهر الماضي (نفس الفترة):")
            appendLine("• هذا الشهر: ${CurrencyFormatter.format(context, mc.thisMonthSpent)} | الماضي: ${CurrencyFormatter.format(context, mc.lastMonthSpent)} (${if (mc.deltaPct >= 0) "+" else ""}${mc.deltaPct}%)")
        }
        if (report.categoryBreakdown.isNotEmpty()) {
            appendLine()
            appendLine("🗂️ حسب الفئة:")
            report.categoryBreakdown.forEach { c ->
                append("• ${c.category}: ${CurrencyFormatter.format(context, c.spent)}")
                if (c.budget > 0) append(" من ${CurrencyFormatter.formatNumber(context, c.budget)} (${c.pctUsed}%)")
                appendLine()
            }
        }
        if (report.topMerchants.isNotEmpty()) {
            appendLine()
            appendLine("🏪 أعلى الجهات:")
            report.topMerchants.forEach { (name, amt) -> appendLine("• $name: ${CurrencyFormatter.format(context, amt)}") }
        }
        report.behaviorProfile?.let { bp ->
            appendLine()
            appendLine("🧠 سلوكك المالي:")
            appendLine("• أكثر يوم صرف: ${bp.topSpendingDay}")
            appendLine("• صرف الويكند: ${bp.weekendSharePct}% من الإجمالي")
            appendLine("• متوسط المعاملة: ${CurrencyFormatter.format(context, bp.avgTransaction)}")
            if (bp.impulsePurchases > 0) appendLine("• مشتريات اندفاعية (30 يوم): ${bp.impulsePurchases}")
        }
        if (report.insights.isNotEmpty()) {
            appendLine()
            appendLine("💡 ملاحظات زاد:")
            report.insights.take(6).forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("— تقرير من تطبيق زاد 🥕")
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
