package com.example.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    // Persisted state for predictWeeklySpendingSmoothed()'s incremental exponential smoothing —
    // see that function for why this lives in SharedPreferences instead of being recomputed.
    private const val FORECAST_PREFS = "zad_spend_forecast"
    private const val FORECAST_LEVEL_KEY = "weekly_ewma_level"
    private const val FORECAST_LAST_WEEK_KEY = "weekly_ewma_last_week_start_epoch"
    private const val FORECAST_ALPHA = 0.3 // weight on the newest completed week — higher = more reactive to recent spend

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

    // Merged in from the old ZadBrainEngine (deleted — this was its only caller). This is the
    // LLM-decides -> tool-executes loop: unlike AutoAction above (deterministic rules, executed
    // immediately via executeAutoAction), these come from an actual model judgment call and are
    // reserved for things a fixed rule can't phrase well (family-facing notifications, nuanced
    // budget commentary) — see runAiProactiveActions().
    @Serializable
    data class AiAction(
        val type: String, // ADD_TO_SHOPPING | SUGGEST_MEAL | ALERT_BUDGET | NOTIFY_FAMILY | DEDUCT_PHARMACY_STOCK | TRIGGER_PHARMACY_ALARM
        val payload: String,
        val reason: String
    )

    @Serializable
    data class AiActionList(val actions: List<AiAction>)

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
        budget: Double,
        // Optional so existing callers aren't forced to thread pharmacy data through immediately —
        // pharmacy state was previously never included in fullAnalysis at all (see FamilyState.kt).
        pharmacyItems: List<ZadPharmacyItem> = emptyList(),
        // update-behavior-profile's server-computed average (٩٠ يوم، نفس نافذة زاد-برين) —
        // لما يكون موجود بيحل محل التوقع المحلي تحت (predictWeeklySpendingSmoothed) عشان
        // "توقع الأسبوع الجاي" يبقى نفس الرقم اللي العقل بيشوفه في محادثته (behavior_profile.
        // avg_weekly_spending في snapshot زاد-برين)، مش رقمين مختلفين لنفس السؤال. null (لسه
        // ما اتحملش، أو أوفلاين) = fallback للتوقع المحلي زي الأول بالظبط.
        behaviorProfile: UserBehaviorProfile? = null
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
        val nextWeekExpense = behaviorProfile?.avgWeeklySpending?.takeIf { it > 0 }
            ?: predictWeeklySpendingSmoothed(context, transactions)
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
                val action = AutoAction("ADD_TO_SHOPPING", item.itemName, "مخزون منخفض: ${item.itemName} (${item.quantity} متبقي)")
                autoActions.add(action)
                // Deterministic decision (low stock -> add to shopping) executed immediately here
                // instead of only being returned for a caller to maybe-act on later — the old
                // split (this rule vs. the LLM tool-loop below deciding the same thing) meant the
                // background worker's autoActions were logged but never actually inserted anywhere.
                executeAutoAction(action, context)
            }
            // Predictive: Suggest restock based on usage pattern
            val pattern = behaviorPatterns.find { it.category.contains(item.itemName, ignoreCase = true) }
            if (pattern != null) {
                predictions.add("عادة تشتري ${item.itemName} كل ${pattern.frequencyDays} يوم — حان وقت الشراء")
            }
        }

        if (lowStockItems.isNotEmpty()) {
            val names = lowStockItems.joinToString(", ") { it.itemName }
            alerts.add("مخزون منخفض: $names")
            if (com.example.ui.screens.AlertPrefs.isEnabled(context, com.example.ui.screens.AlertPrefs.KEY_LOW_INVENTORY)) {
                smartNotifications.add(SmartNotification(
                    type = "LOW_STOCK",
                    title = "📉 مخزون منخفض",
                    body = "أصناف أساسية أوشكت على النفاد: $names",
                    priority = "HIGH"
                ))
            }
        }

        if (expiringSoon.isNotEmpty()) {
            val names = expiringSoon.joinToString(", ") { it.itemName }
            suggestions.add("اقترح وصفات تستخدم: $names قبل انتهاء الصلاحية")
            val recipeAction = AutoAction("SUGGEST_RECIPE", names, "أصناف على وشك الانتهاء")
            autoActions.add(recipeAction)
            executeAutoAction(recipeAction, context)
            smartNotifications.add(SmartNotification(
                type = "PREDICTIVE",
                title = "⏳ منتجات على وشك الانتهاء",
                body = "${expiringSoon.size} أصناف تنتهي خلال 3 أيام: $names",
                priority = "HIGH"
            ))
        }

        // ====== 3ب. صيدلية — دواء قارب على النفاد ======
        // نفس عتبة daysOfSupplyLeft() <= 5 المستخدمة أصلاً كبادج في PharmacyScreen (Models.kt)
        // — هنا كانت العتبة دي معروضة بس لما المستخدم يفتح شاشة الصيدلية بنفسه، من غير أي
        // تنبيه استباقي زي مخزون الأكل فوق بالظبط. pharmacyItems أصلاً بارامتر موجود في
        // fullAnalysis (كان بيتغذّى بس لـ runAiProactiveActions كسياق للـ LLM، مش لقرار حتمي).
        val lowStockPharmacy = pharmacyItems.filter { it.isLowStock() }
        if (lowStockPharmacy.isNotEmpty()) {
            val names = lowStockPharmacy.joinToString(", ") { it.name }
            alerts.add("💊 دواء قارب على النفاد: $names")
            smartNotifications.add(SmartNotification(
                type = "PHARMACY_LOW_STOCK",
                title = "💊 دواء قارب على النفاد",
                body = "راجع مخزون: $names",
                priority = "HIGH"
            ))
            // رسالة شات العائلة مرة واحدة في اليوم لكل صنف بس (مفتاح SharedPreferences بتاريخ
            // اليوم) — الـ worker ده كل ٦ ساعات، وده كان هيبعت نفس الرسالة ٤ مرات يومياً
            // من غير الحد ده.
            val prefs = context.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            val alertedKey = "pharmacy_low_stock_alerted_$today"
            val alerted = prefs.getStringSet(alertedKey, emptySet())?.toMutableSet() ?: mutableSetOf()
            val newlyAlerted = lowStockPharmacy.filter { it.id !in alerted }
            if (newlyAlerted.isNotEmpty()) {
                sendFamilyAlert("💊 دواء قارب على النفاد: ${newlyAlerted.joinToString(", ") { it.name }}")
                alerted.addAll(newlyAlerted.map { it.id })
                prefs.edit().putStringSet(alertedKey, alerted).apply()
            }
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
        // Task 19.0 — كان مصروف كل العمر مقابل سقف شهري، فبيتخطى 100% دايماً بعد أول شهر
        // ويفضل "🚨 تجاوزت الميزانية" ظاهر للأبد. دلوقتي شهري عبر BudgetMath.
        val totalSpent = BudgetMath.spentThisMonth(transactions)
        if (budget > 0) {
            val pct = (totalSpent / budget * 100).toInt()
            val remaining = BudgetMath.remaining(budget, transactions) ?: (budget - totalSpent)
            when {
                pct >= 100 -> alerts.add("🚨 تجاوزت الميزانية! أنفقت ${CurrencyFormatter.format(context, totalSpent)} من ${CurrencyFormatter.format(context, budget)}")
                pct >= 85 -> {
                    alerts.add("⚠️ الميزانية على وشك النفاد: $pct% مستخدم")
                    if (com.example.ui.screens.AlertPrefs.isEnabled(context, com.example.ui.screens.AlertPrefs.KEY_BUDGET_OVERRUN)) {
                        smartNotifications.add(SmartNotification("BUDGET_OVERRUN", "⚠️ الميزانية تنفد", "استخدمت $pct% من ميزانيتك (متبقي ${CurrencyFormatter.format(context, remaining)})", "HIGH"))
                    }
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

        // ====== 8. GROQ AI ENHANCED SUMMARY — RETIRED (one-brain) ======
        // كان لفة LLM تانية على الموبايل (Groq مباشر) بتضارب رؤيتها مع رؤية العقل
        // السيرفر (zad-brain) لنفس البيانات — وده مصدر "الأرقام بتختلف بين الشاشة
        // والشات". الملخص المحلي الحتمي (generateLocalSummary) بيفضل هو مصدر الشاشة،
        // والاستنتاج والإشعارات من zad-brain بس.
        val summary = generateLocalSummary(inventory, transactions, budget, totalSpent)

        // ====== 9. AI PROACTIVE TOOL-LOOP — RETIRED (one-brain) ======
        // نفس السبب: حلقة أدوات موازية كانت تبعت إشعارات ZadNotifier من غير ما
        // العقل السيرفر يعرف. كل الاستنتاج الاستباقي بقى من zad-brain (snapshot +
        // proactive scan + dream). الدوال محفوظة لو احتاجها مسار صوتي محلي،
        // بس مش بتتنادى من fullAnalysis تاني.

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

    // Simple exponential smoothing over completed-week expense totals, replacing the old flat
    // 14-day-average×0.5 rule. State (smoothed level + last-processed week) is persisted so each
    // call only folds in whatever completed week(s) are new since the last run — never rescans
    // full transaction history to recompute the average from scratch.
    private fun predictWeeklySpendingSmoothed(context: Context, transactions: List<ZadTransaction>): Double {
        if (transactions.isEmpty()) return 0.0
        val prefs = context.getSharedPreferences(FORECAST_PREFS, Context.MODE_PRIVATE)
        val currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY)

        fun txDate(tx: ZadTransaction): LocalDate? = tx.createdAt?.let {
            try { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() } catch (e: Exception) { null }
        }
        fun weekSpend(weekStart: LocalDate): Double = transactions
            .filter { it.txnKind == "expense" && (txDate(it)?.let { d -> d >= weekStart && d < weekStart.plusWeeks(1) } ?: false) }
            .sumOf { it.amount }

        val lastWeekEpoch = prefs.getLong(FORECAST_LAST_WEEK_KEY, -1L)
        var level = prefs.getFloat(FORECAST_LEVEL_KEY, -1f).toDouble()

        if (lastWeekEpoch == -1L) {
            // First run ever for this device — seed from the most recent completed week
            // (one bounded read, not a full-history average) instead of starting from zero.
            val lastCompletedWeek = currentWeekStart.minusWeeks(1)
            level = weekSpend(lastCompletedWeek)
            prefs.edit().putFloat(FORECAST_LEVEL_KEY, level.toFloat()).putLong(FORECAST_LAST_WEEK_KEY, lastCompletedWeek.toEpochDay()).apply()
            return kotlin.math.round(level * 100) / 100
        }

        var cursor = LocalDate.ofEpochDay(lastWeekEpoch).plusWeeks(1)
        // Catch-up loop for a device that hasn't run analysis in a while — still one smoothing
        // update per completed week, bounded by actual elapsed weeks, not the whole tx history.
        while (cursor < currentWeekStart) {
            val spend = weekSpend(cursor)
            level = if (level < 0) spend else FORECAST_ALPHA * spend + (1 - FORECAST_ALPHA) * level
            cursor = cursor.plusWeeks(1)
        }
        prefs.edit().putFloat(FORECAST_LEVEL_KEY, level.toFloat()).putLong(FORECAST_LAST_WEEK_KEY, currentWeekStart.minusWeeks(1).toEpochDay()).apply()
        return kotlin.math.round(level.coerceAtLeast(0.0) * 100) / 100
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

    /**
     * Single source of truth for "a dose was taken" — deducts stock, marks/creates the dose
     * log, and chains into ADD_TO_SHOPPING if that leaves the medication low. Used by
     * PharmacyReminderReceiver (notification "Taken" button), PharmacyScreen's per-dose
     * buttons, and the voice-command path (executeAiAction's DEDUCT_PHARMACY_STOCK).
     *
     * Task 17.2.1/17.2.2: deducts by `unitsPerDose` (not a flat 1 — a dose can be 2 tablets),
     * and is idempotent per `scheduledAt` via zad_pharmacy_doses' unique index — calling this
     * twice for the same scheduled dose (notification tap + screen tap) decrements once, not
     * twice. Deliberately does NOT log an expense here — the medication's cost is already
     * recorded once at purchase/refill time (see ZadViewModel.addPharmacyItem/refillPharmacyItem).
     *
     * @param scheduledAt canonical dose-slot instant (PharmacyReminderScheduler.canonicalScheduledAt)
     *   for a scheduled dose, or null for an ad-hoc "I took it" outside any schedule — ad-hoc
     *   calls are never deduped against each other, each is a genuinely new event.
     */
    /**
     * مشترك بين وصول ميعاد الجرعة (PharmacyReminderReceiver)، أخذها (markPharmacyDoseTaken
     * تحت)، وفواتها (PeriodicAnalysisWorker) — نفس نمط NOTIFY_FAMILY (executeAiAction فوق):
     * senderId="zad_ai" (FamilyScreen.kt بيتعرف عليه ويعرضه كرسالة زاد، مش عضو حقيقي).
     * Best effort — فشلها (مفيش عائلة، أو الشبكة واقعة) ميوقفش الفعل الأساسي.
     */
    suspend fun sendFamilyAlert(message: String) {
        try {
            val familyId = SupabaseRepo.getMyFamilyMember()?.familyId
            if (familyId != null) {
                SupabaseRepo.sendMessage(familyId, "zad_ai", message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendFamilyAlert() failed: ${e.message}")
        }
    }

    suspend fun markPharmacyDoseTaken(context: Context, itemId: String, doseLogId: String? = null, scheduledAt: String? = null): Boolean = withContext(Dispatchers.IO) {
        val dao = com.example.data.local.ZadDatabase.getDatabase(context).zadDao()
        val item = dao.getAllPharmacyItemsOnce().find { it.id == itemId } ?: return@withContext false
        val nowIso = Instant.now().toString()

        val mutation = SupabaseRepo.logPharmacyDoseAtomic(itemId, scheduledAt, nowIso)
        if (mutation == null || !mutation.ok) {
            Log.e(TAG, "markPharmacyDoseTaken() atomic mutation failed: ${mutation?.reason ?: "network"}")
            return@withContext false
        }
        if (mutation.duplicate) {
            Log.d(TAG, "markPharmacyDoseTaken() → already logged for scheduledAt=$scheduledAt, skipping stock deduction")
            return@withContext false
        }
        // The server row lock is the authority. Updating Room with the returned value avoids
        // a second client-side calculation drifting across two phones or the Telegram bot.
        dao.insertPharmacyItem(item.copy(remainingQuantity = mutation.remainingQuantity))

        if (doseLogId != null) {
            val log = dao.getDoseLogById(doseLogId)
            if (log != null) {
                dao.insertDoseLog(log.copy(takenAt = nowIso))
                try { SupabaseRepo.markDoseLogTaken(doseLogId, nowIso) } catch (e: Exception) {
                    Log.e(TAG, "markPharmacyDoseTaken() dose log sync failed: ${e.message}")
                }
            }
        } else {
            val log = ZadDoseLog(pharmacyItemId = itemId, itemName = item.name, scheduledAt = scheduledAt ?: nowIso, takenAt = nowIso, createdAt = nowIso)
            dao.insertDoseLog(log)
            try { SupabaseRepo.addDoseLog(log) } catch (e: Exception) {
                Log.e(TAG, "markPharmacyDoseTaken() ad-hoc dose log sync failed: ${e.message}")
            }
        }
        sendFamilyAlert("✅ ${item.name} — تم أخذ الجرعة")
        true
    }

    /** Executes a deterministic AutoAction immediately (no LLM involved in the decision). */
    private suspend fun executeAutoAction(action: AutoAction, context: Context) {
        try {
            when (action.type) {
                "ADD_TO_SHOPPING" -> {
                    SupabaseRepo.addShoppingItem(ZadShoppingItem(itemName = action.payload, quantity = 1, priority = "medium"))
                    Log.d(TAG, "executeAutoAction ADD_TO_SHOPPING -> ${action.payload}")
                }
                "SUGGEST_RECIPE" -> {
                    ZadNotifier.send(context, "🍽️ اقترب انتهاء الصلاحية", "استخدم قبل ما يخلص: ${action.payload}")
                    Log.d(TAG, "executeAutoAction SUGGEST_RECIPE -> ${action.payload}")
                }
                else -> Log.w(TAG, "executeAutoAction: unhandled type ${action.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeAutoAction(${action.type}) failed: ${e.message}")
        }
    }

    /**
     * LLM-decides -> tool-executes loop, merged in from the old ZadBrainEngine.evaluateStateAndAct.
     * Reuses fullAnalysis()'s already-computed lowStockItems/expiringSoon instead of re-scanning
     * inventory independently (the old engine did its own separate scan — real duplication).
     */
    private suspend fun runAiProactiveActions(
        context: Context,
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        lowStockItems: List<ZadInventory>,
        expiringSoon: List<ZadInventory>,
        pharmacyItems: List<ZadPharmacyItem>
    ) {
        val inventoryStr = inventory.joinToString(", ") { "${it.itemName}(${it.quantity})" }
        val txStr = transactions.takeLast(10).joinToString(", ") { "${it.title}:${it.amount}" }
        val pharmacyStr = pharmacyItems.joinToString(", ") {
            "${it.name}(${it.remainingQuantity} ${it.unit} left, ${it.dailyDoseCount}/day)"
        }

        val sysPrompt = """
            You are 'Zad', the central AI brain of a family management app.
            Current state:
            - Inventory: $inventoryStr
            - Expiring soon (≤3 days): ${expiringSoon.joinToString(", ") { it.itemName }}
            - Low stock: ${lowStockItems.joinToString(", ") { it.itemName }}
            - Recent transactions: $txStr
            - Pharmacy/medications: ${pharmacyStr.ifBlank { "none tracked" }}

            Low-stock and expiring-soon items are ALREADY handled automatically (added to the
            shopping list / notified) — do not repeat that decision. Medication stock deduction
            on dose-taken is also already automatic — do not issue DEDUCT_PHARMACY_STOCK here for
            routine doses. Only decide on things a fixed rule can't judge:
            - If spending looks unusual compared to normal, alert the family (NOTIFY_FAMILY)
            - If a genuinely creative meal suggestion fits the inventory, propose one (SUGGEST_MEAL)
            - If a budget concern needs nuanced, non-generic phrasing, raise it (ALERT_BUDGET)
            - If nothing meets this bar, return empty actions

            Return ONLY valid JSON: {"actions":[{"type":"SUGGEST_MEAL|ALERT_BUDGET|NOTIFY_FAMILY","payload":"...","reason":"Arabic reason"}]}
        """.trimIndent()

        val jsonResponse = ZadAiRepository.brainEvaluate(
            sysPrompt, "Analyze and act based on current state.", appContext = context,
        ) ?: return
        val cleanJson = extractJsonBlock(jsonResponse)
        try {
            val actionList = Json { ignoreUnknownKeys = true }.decodeFromString<AiActionList>(cleanJson)
            actionList.actions.forEach { executeAiAction(it, context) }
        } catch (e: Exception) {
            Log.e(TAG, "runAiProactiveActions: failed to parse AI actions: ${e.message}")
        }
    }

    private suspend fun executeAiAction(action: AiAction, context: Context) {
        Log.d(TAG, "executeAiAction: ${action.type} - ${action.payload}")
        when (action.type) {
            "ADD_TO_SHOPPING" -> {
                try {
                    SupabaseRepo.addShoppingItem(ZadShoppingItem(itemName = action.payload, quantity = 1, priority = "medium"))
                } catch (e: Exception) {
                    Log.e(TAG, "ADD_TO_SHOPPING failed: ${e.message}")
                }
            }
            "SUGGEST_MEAL" -> {
                ZadNotifier.send(context, "🍽️ اقتراح وجبة", action.payload)
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) SupabaseRepo.sendAppNotification(userId, "🍽️ اقتراح وجبة", action.payload)
            }
            "ALERT_BUDGET" -> {
                ZadNotifier.send(context, "⚠️ تنبيه ميزانية", action.payload, androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) SupabaseRepo.sendAppNotification(userId, "⚠️ تنبيه ميزانية", action.payload)
            }
            "NOTIFY_FAMILY" -> {
                val familyId = SupabaseRepo.getMyFamilyMember()?.familyId
                if (familyId != null) {
                    SupabaseRepo.sendMessage(familyId, "zad_ai", action.payload)
                } else {
                    Log.w(TAG, "NOTIFY_FAMILY skipped: user has no family")
                }
            }
            "DEDUCT_PHARMACY_STOCK" -> {
                // payload = medication name (the LLM only ever sees names, never DB ids)
                val dao = com.example.data.local.ZadDatabase.getDatabase(context).zadDao()
                val item = dao.getAllPharmacyItemsOnce().find { it.name.contains(action.payload, ignoreCase = true) }
                if (item != null) {
                    markPharmacyDoseTaken(context, item.id)
                } else {
                    Log.w(TAG, "DEDUCT_PHARMACY_STOCK: no pharmacy item matching '${action.payload}'")
                }
            }
            "TRIGGER_PHARMACY_ALARM" -> {
                // payload = medication name — an ad-hoc one-off reminder from a chat/voice
                // request ("ذكرني بعد ساعة"), not the recurring per-dose alarms which are
                // already fully scheduled deterministically by PharmacyReminderScheduler.
                val dao = com.example.data.local.ZadDatabase.getDatabase(context).zadDao()
                val item = dao.getAllPharmacyItemsOnce().find { it.name.contains(action.payload, ignoreCase = true) }
                if (item != null) {
                    PharmacyReminderScheduler.scheduleSnooze(context, item.id, item.name, "voice_reminder", minutesFromNow = 60)
                } else {
                    Log.w(TAG, "TRIGGER_PHARMACY_ALARM: no pharmacy item matching '${action.payload}'")
                }
            }
            else -> Log.w(TAG, "executeAiAction: unknown action ${action.type}")
        }
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1 && end >= start) text.substring(start, end + 1) else text
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

    /**
     * قوة الصرف — كم يقدر يصرف يومياً بأمان حتى نهاية الشهر.
     *
     * `powerPct` نسبة المتبقي من البادجت، و**بيبقى null لما مفيش بادجت متسجّل** — قبل كده
     * كان بيرجع 100 في الحالة دي، يعني مستخدم جديد ما حددش سقف كان بيتقاله "قوي 💪"
     * على أساس رقم اتخترع من العدم. null بيجبر كل شاشة تعرض "—" بدل رقم مالوش أصل.
     */
    data class SpendingPower(
        val dailySafeSpend: Double?,    // المسموح يومياً من المتبقي — null = مفيش سقف متسجل
        val currentDailyAvg: Double,    // معدل صرفه الفعلي يومياً
        val daysLeftInMonth: Int,
        val powerPct: Int?,             // 0-100: المتبقي كنسبة من البادجت — null = مفيش بادجت
        val status: String              // قوي / متوازن / ضعيف / خطر / غير محدد
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
        /** null = السقف الشهري مش متسجل — مفيش "متبقي" أصلاً، مش متبقي = صفر */
        val remaining: Double?,
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
        val monthComparison: MonthComparison?,      // مقارنة شهرية (null لو مفيش شهر سابق)
        /** false = مفيش بادجت ولا معاملات في الشهر ده، فـ healthScore رقم افتراضي مش تقييم */
        val hasEnoughData: Boolean = true
    )

    /**
     * التزام ثابت (إيجار/قسط/فاتورة) مقابل اشتراك اختياري (نتفلكس، جيم، إلخ) —
     * الأول مينفعش يتقال عليه "إلغيه لو مش مستخدمه"، التاني ممكن فعلاً.
     * `type` مش موثوق بيه لوحده (AddSubscriptionDialog كان بيسيب type="subscription"
     * الافتراضي حتى للإيجار/الأقساط قبل التصحيح)، فبنشيك على category والاسم كمان —
     * نفس أسلوب كشف خدمات البث المكرر تحت (keyword matching).
     */
    private val fixedObligationKeywords = listOf(
        "إيجار", "ايجار", "قسط", "أقساط", "اقساط", "rent", "installment", "mortgage", "loan"
    )
    private fun isFixedObligation(sub: ZadSubscription): Boolean {
        if (sub.type == "bill" || sub.type == "installment" || sub.type == "rent") return true
        val haystack = "${sub.category.orEmpty()} ${sub.title}".lowercase()
        return fixedObligationKeywords.any { haystack.contains(it) }
    }

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
        // Task 19.3 convention: totalSpent/totalIncome (المعروضين في "الإنفاق الشهري" على
        // الرئيسية) لازم يتفقوا مع BudgetMath.spentThisMonth/incomeThisMonth — سحب ATM
        // (txnKind=transfer) مابيتحسبش مصروف هنا كمان، وإلا يفضل الرقم يختلف عن باقي
        // الشاشة رغم إنه نفس الشهر. كروت الفئات تحت (spentByCategory) لسه isExpense-based
        // عمداً — دي تصنيف حركة الفلوس، مش رقم "الإنفاق" نفسه.
        val totalSpent = BudgetMath.spentThisMonth(transactions, today)
        val totalIncome = BudgetMath.incomeThisMonth(transactions, today)
        // Task 19.3 — remaining هو الرقم اللي المستخدم بيشوفه (BrainReport.remaining)،
        // فلازم يتفق مع BudgetMath.remaining بالظبط، بما فيها استبعاد التحويلات (سحب ATM).
        val remaining = BudgetMath.remaining(budget, transactions, today)

        // 1) كروت الفئات: المصروف الفعلي من المعاملات + البادجت من BudgetTracker
        //
        // txnKind مش isExpense — التعليق فوق كان بيقول إن دي "تصنيف حركة الفلوس مش رقم
        // الإنفاق"، وده اتضح إنه تبرير ما بيصمدش قدام اللي العميل بيقراه فعلاً: تسوية
        // الكاش اللي العقل بيكتبها (txn_kind=transfer, is_expense=true) كانت بتطلع في
        // التقرير الشهري كـ "حسب الفئة: تحويلات 5,000"، فالعميل يقرا إنه صرف ٥ آلاف على
        // حاجة اسمها "تحويلات" وهو ما صرفش حاجة. نفس استبعاد قاعدة 19.3 بالظبط.
        val spentByCategory = monthTx.filter { it.txnKind == "expense" }
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
            val amount = transactions.filter { it.txnKind == "expense" && txDate(it) == day }.sumOf { it.amount }
            DailySpend(day, amount)
        }

        // 3) أعلى التجار — تحويلات مستبعدة. "تسوية كاش أسبوعية (تقريبية)" مش تاجر،
        // ومع ذلك كانت بتتصدّر القايمة كأعلى جهة صرف عند العميل.
        val topMerchants = monthTx.filter { it.txnKind == "expense" }
            .groupBy { it.merchantName ?: it.title }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5)
            .map { it.key to it.value }

        // 4) الاشتراكات الشهرية
        // subsMonthlyCost = كل حاجة متكررة (بما فيها الإيجار والأقساط) — ده الرقم اللي
        // BrainReport.subscriptionsMonthlyCost بيتعرّف بيه ومحدش بيقيس عليه حكم.
        // discretionaryMonthlyCost = الاشتراكات الاختيارية بس. كل نسبة أو خصم نقاط تحت
        // بيتحسب من ده، مش من الأول: "اشتراكاتك ٦٠٪ من ميزانيتك" لمستخدم إيجاره ٥٥٪
        // منها كانت جملة صح حسابياً وغلط تماماً في معناها — ودي بالظبط اللي بتخلي
        // كارت التحليلات يقول رقم مالوش لازمة.
        val activeSubs = subscriptions.filter { it.isActive }
        val subsMonthlyCost = activeSubs.sumOf { it.amount }
        val discretionarySubs = activeSubs.filter { !isFixedObligation(it) }
        val discretionaryMonthlyCost = discretionarySubs.sumOf { it.amount }

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
        // كل الخصومات تحت شرطية: مفيش بادجت ومفيش معاملات ومفيش مخزون يعني مفيش خصم
        // واحد يتطبّق، فالنتيجة بتفضل 100 = "ممتاز 🌟" لحساب فاضي تماماً. النتيجة نفسها
        // سليمة كحساب، بس عرضها كتقييم كذب — hasEnoughData هي اللي بتخلي الشاشة تعرض
        // "—" بدل ما تدّي مستخدم يومه الأول شهادة صحة مالية.
        val hasEnoughData = budget > 0 || monthTx.isNotEmpty()
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
        // الخصم ده معناه "اشتراكاتك الاختيارية واكلة ربع الميزانية" — الإيجار والأقساط
        // مش سلوك ينتقد، فما بيدخلوش في الحساب.
        if (budget > 0 && discretionaryMonthlyCost > budget * 0.25) score -= 10
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
        if (discretionaryMonthlyCost > 0) {
            insights.add("اشتراكاتك النشطة تكلفك ${CurrencyFormatter.format(context, discretionaryMonthlyCost)} شهرياً (${CurrencyFormatter.format(context, discretionaryMonthlyCost * 12)} سنوياً)")

            // اقتراح إلغاء: نسبة الاشتراكات من الميزانية مرتفعة — بس من الاشتراكات
            // الاختيارية فعلاً، بسطاً ومقاماً. الإيجار والأقساط والفواتير التزامات ثابتة،
            // مينفعش الاقتراح يقول "راجعه لو مش مستخدمه" عن حاجة زي الإيجار، ولا حتى
            // يعدّها في النسبة اللي بتبرّر الاقتراح.
            if (budget > 0 && discretionaryMonthlyCost > budget * 0.2) {
                val mostExpensive = discretionarySubs.maxByOrNull { it.amount }
                if (mostExpensive != null) {
                    insights.add("💡 اشتراكاتك ${(discretionaryMonthlyCost / budget * 100).toInt()}% من ميزانيتك — راجع ${mostExpensive.title} (${CurrencyFormatter.format(context, mostExpensive.amount)}) لو مش مستخدمه")
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
        if (avgDaily > 0 && remaining != null && remaining > 0) {
            insights.add("بمعدل إنفاقك الحالي (${CurrencyFormatter.format(context, avgDaily)}/يوم)، الرصيد يكفي ${(remaining / avgDaily).toInt()} يوم")
        }

        // 8) قوة الصرف — كم يقدر يصرف يومياً بأمان
        val daysLeftInMonth = (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        val dailySafeSpend = remaining?.let { (it / daysLeftInMonth).coerceAtLeast(0.0) }
        val currentDailyAvg = if (today.dayOfMonth > 0) totalSpent / today.dayOfMonth else 0.0
        val powerPct = if (budget > 0 && remaining != null) ((remaining / budget) * 100).toInt().coerceIn(0, 100) else null
        val spendingPower = SpendingPower(
            dailySafeSpend = dailySafeSpend,
            currentDailyAvg = currentDailyAvg,
            daysLeftInMonth = daysLeftInMonth,
            powerPct = powerPct,
            status = when {
                powerPct == null -> "غير محدد"
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
        if (dailySafeSpend != null && dailySafeSpend > 0 && currentDailyAvg > dailySafeSpend) {
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
            monthComparison = monthComparison,
            hasEnoughData = hasEnoughData
        )
    }

    /** تقرير نصي كامل جاهز للمشاركة/التصدير (واتساب، إيميل، ملف) */
    fun buildExportText(context: Context, report: BrainReport): String = buildString {
        val today = LocalDate.now()
        appendLine("📊 تقرير زاد المالي — ${today.month.value}/${today.year}")
        appendLine("═══════════════════════════")
        appendLine("الصحة المالية: ${report.healthScore}/100 (${report.healthLabel})")
        appendLine("قوة الصرف: ${report.spendingPower.status} — الآمن يومياً: ${report.spendingPower.dailySafeSpend?.let { CurrencyFormatter.format(context, it) } ?: context.getString(com.example.R.string.budget_unknown_value)}")
        appendLine()
        appendLine("💰 الأرقام:")
        appendLine("• الميزانية: ${CurrencyFormatter.format(context, report.budget)}")
        appendLine("• المصروف: ${CurrencyFormatter.format(context, report.totalSpent)}")
        appendLine("• الدخل: ${CurrencyFormatter.format(context, report.totalIncome)}")
        appendLine("• المتبقي: ${report.remaining?.let { CurrencyFormatter.format(context, it) } ?: context.getString(com.example.R.string.budget_unknown_value)}")
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
        context: Context,
        inventory: List<ZadInventory>,
        transactions: List<ZadTransaction>,
        subscriptions: List<ZadSubscription>,
        behaviorPatterns: List<ZadBehaviorPattern>
    ): List<SmartNotification> {
        val result = fullAnalysis(
            context = context,
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
