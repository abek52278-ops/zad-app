package com.example.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Task 19.0 — الحساب الوحيد لـ "المصروف الشهري" و"المتبقي" في التطبيق كله. أي مكان
 * محتاج الرقمين دول بينده هنا بدل ما يعيد الفلترة بنفسه — عشان اختلاف بسيط في منطق
 * الفلترة (تنسيق تاريخ مختلف، حد شهر مختلف) بين مكانين ما يبقاش معناه رقمين مختلفين
 * لنفس المفهوم. مقصود يكون شهر تقويمي عادي لحد ما Task 25 (دورة الراتب) يحل محله —
 * كل استدعاء هنا موثّق ليه بديل مؤقت، مش نهائي.
 *
 * الرصيد نفسه (monthlyLimit) مايتخزّنش هنا ولا في أي مكان — بييجي من
 * SupabaseRepo.getMonthlyLimit() في كل مرة، عشان يفضل مشتق مش متراكم.
 */
object BudgetMath {

    /** مش private — ZadIntelligenceScreen's category donut محتاج نفس منطق الفلترة بالتاريخ
     * ده بالظبط، مش نسخة تالتة منه، عشان يطابق spentInCycle/incomeInCycle. */
    fun txDate(tx: ZadTransaction): LocalDate? = tx.createdAt?.let {
        try {
            Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (e: Exception) {
            try { LocalDate.parse(it.take(10)) } catch (e2: Exception) { null }
        }
    }

    /**
     * Task 19.3 — بيفلتر على txnKind == "expense"، مش isExpense. الفرق يظهر بس في حالة
     * واحدة دلوقتي: سحب ATM (isExpense=true، txnKind="transfer") مابقاش بيتحسب مصروف —
     * ده بالظبط بق 19.1 (سحب+صرف كانوا بيتحسبوا مرتين). لازم backfill الأعمدة
     * القديمة يشتغل الأول (20260726060000) قبل ما الفلتر ده يتفعّل، وإلا صفوف دخل
     * قديمة كانت لسه txnKind="expense" (default العمود قبل التصحيح) كانت هتتحسب مصروف.
     */
    fun spentThisMonth(transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double {
        val monthStart = asOf.withDayOfMonth(1)
        return transactions
            .filter { it.txnKind == "expense" && (txDate(it) ?: asOf) >= monthStart }
            .sumOf { it.amount }
    }

    fun incomeThisMonth(transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double {
        val monthStart = asOf.withDayOfMonth(1)
        return transactions
            .filter { it.txnKind == "income" && (txDate(it) ?: asOf) >= monthStart }
            .sumOf { it.amount }
    }

    /**
     * إجمالي الدخل/المصروف لكل الوقت — كان بيتحسب بشكل منفصل ومكرر في 5 شاشات
     * (Home/Transactions/Budget/ZadIntelligence's OverviewTab) كل واحدة بمنطق فلترة
     * isExpense خاص بيها (AUDIT.md، "Rule 1"). نفس مبدأ 19.0: رقم واحد مشتق، مش خمس
     * نسخ ممكن تنحرف عن بعض. بيستخدم txnKind زي باقي الدوال هنا (مش isExpense) —
     * نفس تصحيح 19.3، عشان سحب ATM (transfer) ميتحسبش مصروف هنا كمان.
     */
    fun totalIncome(transactions: List<ZadTransaction>): Double =
        transactions.filter { it.txnKind == "income" }.sumOf { it.amount }

    fun totalExpense(transactions: List<ZadTransaction>): Double =
        transactions.filter { it.txnKind == "expense" }.sumOf { it.amount }

    /**
     * monthlyLimit من zad_users.monthly_limit — سقف <= 0 معناه "مش معروف"، والدالة بترجع
     * **null** مش 0.0.
     *
     * كان بيرجع 0.0، وده كان بيتعرض للمستخدم كرقم حقيقي: "متبقي ٠ ر.س" لمستخدم عمره ما
     * حدد سقف. صفر رقم له معنى (خلصت فلوسك) مختلف تماماً عن غياب السقف، والاتنين كانوا
     * بيتخلطوا في كل شاشة ماليّة. null بيجبر كل مستهلك يقرر يعرض إيه بدل ما يرث كدبة.
     */
    fun remaining(monthlyLimit: Double, transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double? {
        if (monthlyLimit <= 0.0) return null
        return monthlyLimit - spentThisMonth(transactions, asOf) + incomeThisMonth(transactions, asOf)
    }

    // ─── Task 25 — نفس الحسابات فوق، بس بحدود دورة الراتب (CycleMath) مش الشهر التقويمي ───
    // الفرق الوحيد عن spentThisMonth/incomeThisMonth: النطاق [cycleStart, cycleEnd) بدل
    // [أول الشهر, أول الشهر الجاي). لو cycleStartDay=null، CycleMath.cycleStart/cycleEnd
    // نفسها بترجع حدود شهر تقويمي عادي — يعني الاستدعاء هنا آمن حتى قبل ما يتحدد للمستخدم.

    fun spentInCycle(transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double =
        transactions.filter { it.txnKind == "expense" && txDate(it)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true }
            .sumOf { it.amount }

    fun incomeInCycle(transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double =
        transactions.filter { it.txnKind == "income" && txDate(it)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true }
            .sumOf { it.amount }

    /** نفس اتفاقية `remaining`: سقف <= 0 = مش معروف = null، مش صفر. */
    fun remainingInCycle(monthlyLimit: Double, transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double? {
        if (monthlyLimit <= 0.0) return null
        return monthlyLimit - spentInCycle(transactions, cycleStart, cycleEnd) + incomeInCycle(transactions, cycleStart, cycleEnd)
    }

    /**
     * Task 27.1(a) — عدد معاملات الدورة الحالية (دخل/مصروف) اللي is_verified=false، أي
     * معاملة مش مؤكدة بشرياً (رسالة بنك اتحللت آليًا ولسه ما اتراجعتش، أو مصدر تاني غير
     * مباشر — انظر ZadViewModel.addTransaction overload لمين بيبقى isVerified=true). صفر
     * يعني الرقم قاطع، أي رقم تاني يعني الـ Figure اللي بيتبني على الدالة دي confident=false.
     */
    fun unverifiedCountInCycle(transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Int =
        transactions.count { tx ->
            (tx.txnKind == "expense" || tx.txnKind == "income") &&
                txDate(tx)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true &&
                !tx.isVerified
        }

    /** budget * (daysElapsed/cycleLength) هو المتوقع صرفه لحد دلوقتي — النسبة دي أعلى من ١ يعني بيصرف أسرع من المفروض */
    fun velocityInCycle(monthlyLimit: Double, transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate, asOf: LocalDate = LocalDate.now()): Double? {
        if (monthlyLimit <= 0.0) return null
        val cycleLength = CycleMath.cycleLengthDays(cycleStart, cycleEnd).coerceAtLeast(1)
        val daysElapsed = CycleMath.daysElapsed(asOf, cycleStart).coerceAtLeast(1)
        val expected = monthlyLimit * daysElapsed / cycleLength
        if (expected <= 0.0) return 0.0
        return spentInCycle(transactions, cycleStart, cycleEnd) / expected
    }

    /**
     * المسموح صرفه يومياً لازم يخرج من "المتاح" لا "المتبقي": الالتزامات المؤكدة
     * القادمة ليست فلوساً قابلة للصرف. القيمة الافتراضية تحفظ توافق الاستدعاءات القديمة
     * التي لم تكن تعرف الالتزامات، بينما الشاشة/السيرفر يمران الإجمالي الحقيقي.
     */
    fun dailyAllowanceInCycle(
        monthlyLimit: Double,
        transactions: List<ZadTransaction>,
        cycleStart: LocalDate,
        cycleEnd: LocalDate,
        asOf: LocalDate = LocalDate.now(),
        committed: Double = 0.0,
    ): Double? {
        val available = availableInCycle(
            remainingInCycle(monthlyLimit, transactions, cycleStart, cycleEnd),
            committed,
        ) ?: return null
        val daysLeft = CycleMath.daysLeft(asOf, cycleEnd)
        return if (daysLeft > 0) available / daysLeft else available
    }

    // ─── Task 26 — الالتزامات الثابتة ورقم "متاح" ───────────────────────────────
    // نفس القاعدة اللي zad-brain (index.ts) بتحسبها سيرفر-سايد، متكررة هنا للعرض
    // الفوري offline-first — لازم الاتنين يفضلوا متطابقين لو اتغيرت القاعدة في مكان.

    /**
     * الاستحقاق الجاي لالتزام — null لو 'once' فات معاده (افتراض إنه اتدفع)، أو due_day
     * مش موجود لالتزام دوري. quarterly/yearly بيتعاملوا بخطوة ٣/١٢ شهر من due_day نفسه —
     * تبسيط متعمد (نفس تعليق nextDueDate في zad-brain/index.ts)، الجدول مفيهوش due_month.
     */
    fun nextDueDate(obligation: ZadObligation, asOf: LocalDate = LocalDate.now()): LocalDate? {
        if (obligation.recurrence == "once") {
            val due = obligation.dueDate?.let { try { LocalDate.parse(it.take(10)) } catch (e: Exception) { null } } ?: return null
            return if (due.isBefore(asOf)) null else due
        }
        val day = obligation.dueDay ?: return null
        val stepMonths = when (obligation.recurrence) { "quarterly" -> 3L; "yearly" -> 12L; else -> 1L }
        var next = LocalDate.of(asOf.year, asOf.month, day.coerceAtMost(asOf.lengthOfMonth()))
        while (next.isBefore(asOf)) {
            next = next.plusMonths(stepMonths)
            next = next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
        }
        return next
    }

    /** إجمالي المحجوز: التزامات مؤكدة+نشطة مستحقة قبل نهاية الدورة (النهاية حصرية) + اشتراكات نشطة كذلك */
    fun committedInCycle(
        obligations: List<ZadObligation>,
        subscriptions: List<ZadSubscription>,
        cycleEnd: LocalDate,
        asOf: LocalDate = LocalDate.now()
    ): Double {
        val fromObligations = obligations
            .filter { it.active && it.confirmed }
            .sumOf { ob -> nextDueDate(ob, asOf)?.let { if (it.isBefore(cycleEnd)) ob.amount else 0.0 } ?: 0.0 }
        val fromSubscriptions = subscriptions
            .filter { it.isActive && !it.renewalDate.isNullOrBlank() }
            .sumOf { sub ->
                val renewal = try { LocalDate.parse(sub.renewalDate!!.take(10)) } catch (e: Exception) { null }
                if (renewal != null && renewal.isBefore(cycleEnd)) sub.amount else 0.0
            }
        return fromObligations + fromSubscriptions
    }

    /**
     * "متاح" — الرقم الأساسي اللي المفروض المستخدم يشوفه، مش "متبقي". ممكن يبقى سالب،
     * وده مقصود (PRODUCT_PLAN Task 26): إخفاؤه وراء صفر أخطر حاجة ممكن الميزة دي تعملها.
     */
    fun availableInCycle(remaining: Double?, committed: Double): Double? =
        remaining?.let { it - committed }

    /**
     * Task 19.4 — فلوس الكاش تحت اليد. مطابق تماماً لمنطق zad_cash_balance() SQL (migration
     * 20260726060000): سحب ATM (transfer→cash) بيزود، صرف من الكاش (expense مع wallet=cash)
     * بينقص. محسوب هنا من Room مباشرة (مش عن طريق نداء شبكة لنفس دالة الـ RPC) عشان يفضل
     * على نفس مبدأ سلطة واحدة مشتقة اللي 19.0 أسسه — كل رقم فلوس على الشاشة بييجي من هنا،
     * مش من مصدرين مختلفين ممكن يختلفوا لو الجهاز أوفلاين. الدالة على السيرفر (zad_cash_balance)
     * فضلت زي ما هي لمستهلكين تانيين (زاد-برين مثلاً)، مش استُبدلت.
     */
    fun cashOnHand(transactions: List<ZadTransaction>): Double {
        var balance = 0.0
        for (tx in transactions) {
            if (tx.txnKind == "transfer" && tx.transferTo == "cash") balance += tx.amount
            else if (tx.txnKind == "expense" && tx.wallet == "cash") balance -= tx.amount
        }
        return balance.asMoney()
    }
}
