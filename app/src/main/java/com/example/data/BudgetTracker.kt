package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.flow.first

/**
 * متتبع الميزانية — كروت الفئات + تنبيهات
 *
 * Task 19.0: كان فيه رصيد إجمالي متراكم هنا (KEY_REMAINING) بيتحدث بمعاملة معاملة —
 * ده اتشال، لأنه كان مصدر تاني منفصل عن الحساب المشتق (BudgetMath) وبيدرفت بصمت (مزامنة
 * فاشلة، معاملة اتمسحت، تثبيت تاني). المتبقي الإجمالي دلوقتي بيتحسب دايماً من الصفر —
 * في ZadViewModel وقت التطبيق مفتوح (Room Flow reactive)، وهنا في checkOverallBudgetThreshold
 * لما معاملة توصل في الخلفية من غير ViewModel حي (bank listener).
 *
 * كروت الفئات (cat_budget_/cat_spent_) ميزة منفصلة تماماً — مالهاش علاقة بالبق ده،
 * فضلت زي ما هي.
 *
 * كل المفاتيح مربوطة بـ user_id الحالي — [readLegacyFloat] بيرجع لأي قيمة قديمة غير
 * مربوطة بمستخدم لو المفتاح الجديد لسه فاضي.
 */
object BudgetTracker {

    private const val TAG = "BudgetTracker"
    private const val PREFS_NAME = "zad_prefs"
    private const val KEY_BUDGET = "cached_budget"
    private const val KEY_MONTH = "budget_month"
    private const val CHANNEL_ID = "zad_budget_alerts"

    // مفاتيح كروت الفئات
    private const val CAT_BUDGET_PREFIX = "cat_budget_"
    private const val CAT_SPENT_PREFIX = "cat_spent_"

    // بادج تنبيه تخطي نسبة من الإجمالي — dedup بس، مش مصدر الرقم نفسه
    private const val OVERALL_ALERT_BUCKET_PREFIX = "overall_alert_bucket_"

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

    /** إعادة تعيين شهرية لمصاريف الفئات بس — الإجمالي مشتق دلوقتي، مالوش reset يتخزن */
    private fun checkCategoryMonthlyReset(context: Context, prefs: SharedPreferences) {
        val savedMonth = prefs.getInt(scopedKey(context, KEY_MONTH), prefs.getInt(KEY_MONTH, -1))
        val currentMonth = java.time.LocalDate.now().monthValue
        if (savedMonth != -1 && savedMonth != currentMonth) {
            val editor = prefs.edit().putInt(scopedKey(context, KEY_MONTH), currentMonth)
            STANDARD_CATEGORIES.forEach { editor.putFloat(scopedKey(context, CAT_SPENT_PREFIX + it), 0f) }
            editor.apply()
            Log.d(TAG, "Category monthly reset applied for month $currentMonth")
        } else if (savedMonth == -1) {
            prefs.edit().putInt(scopedKey(context, KEY_MONTH), currentMonth).apply()
        }
    }

    // ─── الحقن الدقيق للمصروف — كروت الفئات فقط، الإجمالي مالوش حقن هنا ─────

    /** خصم مصروف من كرت الفئة الصح + تنبيه لو الفئة تخطت حدها */
    fun deductExpense(context: Context, amount: Double, title: String, category: String = "أخرى") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkCategoryMonthlyReset(context, prefs)

        val cat = if (category in STANDARD_CATEGORIES) category else "أخرى"
        val catSpent = readLegacyFloat(prefs, scopedKey(context, CAT_SPENT_PREFIX + cat), CAT_SPENT_PREFIX + cat, 0f).toDouble() + amount
        prefs.edit().putFloat(scopedKey(context, CAT_SPENT_PREFIX + cat), catSpent.toFloat()).apply()
        Log.d(TAG, "deductExpense: $amount [$cat] | cat spent: $catSpent")

        val catBudget = readLegacyFloat(prefs, scopedKey(context, CAT_BUDGET_PREFIX + cat), CAT_BUDGET_PREFIX + cat, 0f).toDouble()
        if (catBudget > 0) {
            val catPct = (catSpent / catBudget * 100).toInt()
            if (catSpent > catBudget) {
                sendAlert(context, "⛔ تجاوزت ميزانية $cat", "صرفت ${CurrencyFormatter.format(context, catSpent)} من أصل ${CurrencyFormatter.format(context, catBudget)} المخصصة لـ$cat")
            } else if (catPct >= 85) {
                sendAlert(context, "⚠️ ميزانية $cat توشك على النفاد", "استخدمت $catPct% من كرت $cat")
            }
        }
    }

    /** استرداد (Refund): يُخصم من كرت الفئة بس — الإجمالي بيتصحح لوحده من كونه مشتق */
    fun applyRefund(context: Context, amount: Double, title: String, category: String = "أخرى") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkCategoryMonthlyReset(context, prefs)

        val cat = if (category in STANDARD_CATEGORIES) category else "أخرى"
        val catSpent = (readLegacyFloat(prefs, scopedKey(context, CAT_SPENT_PREFIX + cat), CAT_SPENT_PREFIX + cat, 0f).toDouble() - amount).coerceAtLeast(0.0)
        prefs.edit().putFloat(scopedKey(context, CAT_SPENT_PREFIX + cat), catSpent.toFloat()).apply()

        sendAlert(context, "↩️ تم استرداد مبلغ", "$title: +${CurrencyFormatter.format(context, amount)} رجعت لرصيدك")
        Log.d(TAG, "applyRefund: $amount [$cat]")
    }

    /** إشعار إيداع بس — مفيش فئة للدخل، ومفيش رصيد إجمالي يتحدث هنا */
    fun addIncome(context: Context, amount: Double, title: String) {
        sendAlert(context, "💰 تمت إضافة إيداع", "$title: +${CurrencyFormatter.format(context, amount)}")
        Log.d(TAG, "addIncome: $amount")
    }

    /**
     * Task 19.0 خطوة ٧ — تنبيه تخطي 75%/90%/100% من الإجمالي، محسوب لحظياً من Room +
     * السقف المخزّن محلياً (بيتحدث من ZadViewModel.loadBudget/updateBudget)، مش من
     * رصيد متراكم مخزّن هنا. لازم يشتغل من مسار الخلفية (UnifiedBankListener عبر
     * BankTransactionApplier) اللي مفيهوش ViewModel حي، فمينفعش يعتمد على
     * _remainingBalance اللي الـ ViewModel بس بتحسبها وقت التطبيق مفتوح.
     *
     * bucket في SharedPreferences هنا dedup بس (منع تكرار نفس التنبيه) — مايستخدمش
     * كمصدر للرقم نفسه، البادجت المشتق دايماً من BudgetMath.
     */
    suspend fun checkOverallBudgetThreshold(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = readLegacyFloat(prefs, scopedKey(context, KEY_BUDGET), KEY_BUDGET, 0f).toDouble()
        if (budget <= 0.0) return

        val dao = ZadDatabase.getDatabase(context).zadDao()
        val transactions = dao.getAllTransactions().first()
        val spent = BudgetMath.spentThisMonth(transactions)
        // budget > 0 مضمون فوق، فـ remaining مش هترجع null هنا — الـ return حماية للنوع بس
        val remaining = BudgetMath.remaining(budget, transactions) ?: return
        val spentPct = ((budget - remaining) / budget * 100).toInt()

        val monthKey = java.time.YearMonth.now().toString() // "2026-07"
        val bucketKey = scopedKey(context, OVERALL_ALERT_BUCKET_PREFIX + monthKey)
        val alreadyAlertedPct = prefs.getInt(bucketKey, 0)

        val bucket = when {
            remaining <= 0 -> 100
            spentPct >= 90 -> 90
            spentPct >= 75 -> 75
            else -> 0
        }
        if (bucket == 0 || bucket <= alreadyAlertedPct) return

        prefs.edit().putInt(bucketKey, bucket).apply()
        when (bucket) {
            100 -> sendAlert(context, "⛔ الميزانية منتهية!", "تم استنفاذ الميزانية بالكامل. راجع مصاريفك.")
            90 -> sendAlert(context, "⚠️ الميزانية أوشكت على الانتهاء", "استخدمت $spentPct% من ميزانيتك. المتبقي: ${CurrencyFormatter.format(context, remaining)}")
            75 -> sendAlert(context, "💡 تذكير بالميزانية", "صرفت $spentPct% من ميزانيتك. المتبقي: ${CurrencyFormatter.format(context, remaining)}")
        }
        Log.d(TAG, "checkOverallBudgetThreshold → spent=$spent, remaining=$remaining, bucket=$bucket")
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
