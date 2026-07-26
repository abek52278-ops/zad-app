package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

/**
 * متتبع الميزانية — إجمالي + كروت فئات
 *
 * كل فئة ليها بادجت خاص (يدوي أو AI) والمصروف بيتحقن في الكرت الصح تلقائياً
 * من UnifiedBankListener / UnifiedSmsReceiver بعد التحليل الدقيق.
 *
 * كل المفاتيح دلوقتي مربوطة بـ user_id الحالي — قبل كده كانت SharedPreferences مشتركة
 * لأي حساب مسجّل دخول على نفس الجهاز (لو أكتر من فرد في العيلة بيستخدموا نفس التابلت
 * مثلاً، ميزانية حد كانت ممكن تتعرض/تتخلط مع التاني). [readLegacyFloat] بيرجع لأي قيمة
 * قديمة غير مربوطة بمستخدم لو المفتاح الجديد لسه فاضي — عشان مستخدمين موجودين قبل كده
 * ما يفقدوش رصيدهم المسجل فجأة.
 */
object BudgetTracker {

    private const val TAG = "BudgetTracker"
    private const val PREFS_NAME = "zad_prefs"
    private const val KEY_REMAINING = "remaining_balance"
    private const val KEY_BUDGET = "cached_budget"
    private const val KEY_MONTH = "budget_month"
    private const val CHANNEL_ID = "zad_budget_alerts"

    // مفاتيح كروت الفئات
    private const val CAT_BUDGET_PREFIX = "cat_budget_"
    private const val CAT_SPENT_PREFIX = "cat_spent_"

    /** الفئات القياسية في زاد — نفس أسماء SaBankParser */
    val STANDARD_CATEGORIES = listOf(
        "البقالة", "المطاعم", "الفواتير", "المواصلات", "الوقود",
        "الاشتراكات", "الأقساط", "الرعاية الصحية", "التعليم", "تحويلات", "أخرى"
    )

    /** "" لو مفيش مستخدم مسجل دخول (نادراً — المسارات اللي بتنده هنا محتاجة auth أصلاً) */
    private fun userSuffix(context: Context): String =
        CurrentUser.get(context)?.let { "_$it" } ?: ""

    private fun scopedKey(context: Context, prefix: String) = prefix + userSuffix(context)

    private fun readLegacyFloat(prefs: SharedPreferences, scopedKey: String, legacyKey: String, default: Float): Float =
        if (prefs.contains(scopedKey)) prefs.getFloat(scopedKey, default)
        else prefs.getFloat(legacyKey, default)

    // ─── الميزانية الإجمالية ─────────────────────────────────────

    fun getRemaining(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = readLegacyFloat(prefs, scopedKey(context, KEY_BUDGET), KEY_BUDGET, 3500.0f).toDouble()
        return readLegacyFloat(prefs, scopedKey(context, KEY_REMAINING), KEY_REMAINING, budget.toFloat()).toDouble()
    }

    /** إعادة تعيين شهرية: الرصيد يرجع للبادجت + كل مصاريف الفئات تتصفر */
    private fun checkMonthlyReset(context: Context, prefs: SharedPreferences): Double {
        val budget = readLegacyFloat(prefs, scopedKey(context, KEY_BUDGET), KEY_BUDGET, 3500.0f).toDouble()
        val savedMonth = prefs.getInt(scopedKey(context, KEY_MONTH), prefs.getInt(KEY_MONTH, -1))
        val currentMonth = java.time.LocalDate.now().monthValue
        var current = readLegacyFloat(prefs, scopedKey(context, KEY_REMAINING), KEY_REMAINING, budget.toFloat()).toDouble()

        if (savedMonth != -1 && savedMonth != currentMonth) {
            current = budget
            val editor = prefs.edit().putInt(scopedKey(context, KEY_MONTH), currentMonth)
            // تصفير مصاريف كل الفئات للشهر الجديد
            STANDARD_CATEGORIES.forEach { editor.putFloat(scopedKey(context, CAT_SPENT_PREFIX + it), 0f) }
            editor.putFloat(scopedKey(context, KEY_REMAINING), budget.toFloat()).apply()
            Log.d(TAG, "Monthly reset applied for month $currentMonth")
        } else if (savedMonth == -1) {
            prefs.edit().putInt(scopedKey(context, KEY_MONTH), currentMonth).apply()
        }
        return current
    }

    // ─── كروت الفئات ─────────────────────────────────────────────

    fun getCategoryBudget(context: Context, category: String): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readLegacyFloat(prefs, scopedKey(context, CAT_BUDGET_PREFIX + category), CAT_BUDGET_PREFIX + category, 0f).toDouble()
    }

    /** تحديد بادجت فئة — يدوياً من الشاشة أو تلقائياً من AI */
    fun setCategoryBudget(context: Context, category: String, amount: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(scopedKey(context, CAT_BUDGET_PREFIX + category), amount.toFloat()).apply()
        Log.d(TAG, "Category budget set: $category = $amount")
    }

    fun getCategorySpent(context: Context, category: String): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readLegacyFloat(prefs, scopedKey(context, CAT_SPENT_PREFIX + category), CAT_SPENT_PREFIX + category, 0f).toDouble()
    }

    /** كل الكروت دفعة واحدة لشاشة البادجت: (الفئة، البادجت، المصروف) */
    fun getAllCategoryCards(context: Context): List<Triple<String, Double, Double>> =
        STANDARD_CATEGORIES.map { cat ->
            Triple(cat, getCategoryBudget(context, cat), getCategorySpent(context, cat))
        }

    // ─── الحقن الدقيق للمصروف ────────────────────────────────────

    /**
     * خصم مصروف: من الإجمالي + من كرت الفئة الصح
     * التنبيهات على مستويين: الفئة (تجاوز كرت) والإجمالي (75%/90%/نفاد)
     */
    fun deductExpense(context: Context, amount: Double, title: String, category: String = "أخرى") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = readLegacyFloat(prefs, scopedKey(context, KEY_BUDGET), KEY_BUDGET, 3500.0f).toDouble()
        val current = checkMonthlyReset(context, prefs)

        val newRemaining = current - amount
        prefs.edit().putFloat(scopedKey(context, KEY_REMAINING), newRemaining.toFloat()).apply()

        // حقن في كرت الفئة
        val cat = if (category in STANDARD_CATEGORIES) category else "أخرى"
        val catSpent = readLegacyFloat(prefs, scopedKey(context, CAT_SPENT_PREFIX + cat), CAT_SPENT_PREFIX + cat, 0f).toDouble() + amount
        prefs.edit().putFloat(scopedKey(context, CAT_SPENT_PREFIX + cat), catSpent.toFloat()).apply()
        Log.d(TAG, "deductExpense: $amount [$cat] | total: $current → $newRemaining | cat spent: $catSpent")

        // تنبيه تجاوز كرت الفئة
        val catBudget = readLegacyFloat(prefs, scopedKey(context, CAT_BUDGET_PREFIX + cat), CAT_BUDGET_PREFIX + cat, 0f).toDouble()
        if (catBudget > 0) {
            val catPct = (catSpent / catBudget * 100).toInt()
            if (catSpent > catBudget) {
                sendAlert(context, "⛔ تجاوزت ميزانية $cat", "صرفت ${CurrencyFormatter.format(context, catSpent)} من أصل ${CurrencyFormatter.format(context, catBudget)} المخصصة لـ$cat")
            } else if (catPct >= 85) {
                sendAlert(context, "⚠️ ميزانية $cat توشك على النفاد", "استخدمت $catPct% من كرت $cat")
            }
        }

        // تنبيهات الإجمالي
        val spentPct = if (budget > 0) ((budget - newRemaining) / budget * 100).toInt() else 0
        when {
            newRemaining <= 0 -> sendAlert(context, "⛔ الميزانية منتهية!", "تم استنفاذ الميزانية بالكامل. راجع مصاريفك.")
            spentPct >= 90 -> sendAlert(context, "⚠️ الميزانية أوشكت على الانتهاء", "استخدمت $spentPct% من ميزانيتك. المتبقي: ${CurrencyFormatter.format(context, newRemaining)}")
            spentPct >= 75 -> sendAlert(context, "💡 تذكير بالميزانية", "صرفت $spentPct% من ميزانيتك. المتبقي: ${CurrencyFormatter.format(context, newRemaining)}")
        }
    }

    /** استرداد (Refund): يرجع للإجمالي ويُخصم من كرت الفئة */
    fun applyRefund(context: Context, amount: Double, title: String, category: String = "أخرى") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = checkMonthlyReset(context, prefs)
        prefs.edit().putFloat(scopedKey(context, KEY_REMAINING), (current + amount).toFloat()).apply()

        val cat = if (category in STANDARD_CATEGORIES) category else "أخرى"
        val catSpent = (readLegacyFloat(prefs, scopedKey(context, CAT_SPENT_PREFIX + cat), CAT_SPENT_PREFIX + cat, 0f).toDouble() - amount).coerceAtLeast(0.0)
        prefs.edit().putFloat(scopedKey(context, CAT_SPENT_PREFIX + cat), catSpent.toFloat()).apply()

        sendAlert(context, "↩️ تم استرداد مبلغ", "$title: +${CurrencyFormatter.format(context, amount)} رجعت لرصيدك")
        Log.d(TAG, "applyRefund: $amount [$cat]")
    }

    fun addIncome(context: Context, amount: Double, title: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = checkMonthlyReset(context, prefs)
        val newRemaining = current + amount
        prefs.edit().putFloat(scopedKey(context, KEY_REMAINING), newRemaining.toFloat()).apply()
        Log.d(TAG, "addIncome: $amount | $current → $newRemaining")
        sendAlert(context, "💰 تمت إضافة إيداع", "$title: +${CurrencyFormatter.format(context, amount)} — الرصيد المتبقي: ${CurrencyFormatter.format(context, newRemaining)}")
    }

    private fun sendAlert(context: Context, title: String, message: String) {
        try {
            // احترام إعداد المستخدم: تنبيهات الميزانية ممكن يقفلها من البروفايل
            val alertsEnabled = context.getSharedPreferences("zad_alert_prefs", Context.MODE_PRIVATE)
                .getBoolean("alert_budget_overrun", true)
            if (!alertsEnabled) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "تنبيهات الميزانية", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "sendAlert failed: ${e.message}")
        }
    }
}
