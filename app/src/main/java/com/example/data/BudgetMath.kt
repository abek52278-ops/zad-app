package com.example.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * زاد بقى **دفتر حسابات**: `الرصيد = الرصيد الابتدائي + كل الدخل - كل المصروف`
 * (migration 20260816010000). مفيش سقف ميزانية يتطرح منه، ومفيش دخل مستني موافقة عشان
 * يتحسب. `zad_users.monthly_limit` لسه اسمه كده في قاعدة البيانات بس معناه بقى "الرصيد
 * الابتدائي للدورة" — الاسم اتساب عشان ~٤٠ نقطة استدعاء متتغيرش من غير مكسب سلوكي.
 *
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

    /**
     * توحيد العملة قبل أي جمع. كل دالة تحت بتعمل `sumOf { it.amount }` من غير ما تبص على
     * `currency` خالص، فمعاملة بعملة تانية كانت بتتجمع كأنها بعملة الحساب: خصم ١٠٠ دولار
     * بيزوّد المصروف ١٠٠ جنيه، وبيتعرض بعلامة الجنيه.
     *
     * ودي مش حالة نظرية — `SaBankParser.extractCurrency` اتعمل مخصوص عشان يمسك "رسالة من
     * بنك مصري وأنت مسافر" ويسجّل عملتها الصح. فالإصلاح ده خلّى الصف أمين، وبعدين مفيش
     * حاجة بتقرا الحقل. البيانات بقت صادقة والحسبة فضلت بتكدب.
     *
     * `currency` فاضية أو null معناها **عملة الحساب**، مش SAR. ده مهم: العمود null لكل
     * الصفوف الموجودة، ولو اتفسّر على إنه ريال كان التحويل هيضرب أرقام حساب مصري في ١٣.
     * التحويل بيحصل بس لما الصف حامل كود صريح ومختلف عن عملة الحساب.
     *
     * المعدلات في [CurrencyExchange] تقريبية ومحدّثة يدوياً، فالرقم الناتج تقريبي — بس
     * تقريبي أقرب للحقيقة بكتير من جمع عملتين مختلفتين كأنهم واحدة.
     *
     * ملحوظة: `zad_budget_state` على السيرفر لسه بيجمع `amount` خام. ده مالوش أثر
     * دلوقتي لأن العمود null في كل الصفوف، بس أول ما تتسجّل معاملة بعملة أجنبية هيبقى
     * فيه فرق بين الرقم المحلي ورقم السيرفر — والـ drift log في ZadViewModel هيقوله.
     */
    fun normalizedToCurrency(transactions: List<ZadTransaction>, homeCurrency: String?): List<ZadTransaction> {
        if (homeCurrency.isNullOrBlank()) return transactions
        return transactions.map { tx ->
            val code = tx.currency?.trim()?.uppercase()
            if (code.isNullOrBlank() || code == homeCurrency.uppercase()) tx
            else tx.copy(amount = CurrencyExchange.convert(tx.amount, code, homeCurrency.uppercase()).asMoney())
        }
    }

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
     * `openingBalance` من zad_users.monthly_limit — العمود محتفظ باسمه القديم بس معناه
     * اتغيّر: بقى **الرصيد اللي الدورة بدأت بيه**، مش سقف صرف (migration 20260816010000).
     * قيمة <= 0 معناها "لسه متحددش"، والدالة بترجع **null** مش 0.0.
     *
     * كان بيرجع 0.0، وده كان بيتعرض للمستخدم كرقم حقيقي: "متبقي ٠ ر.س" لمستخدم عمره ما
     * حدد رصيد. صفر رقم له معنى (خلصت فلوسك) مختلف تماماً عن غياب الرقم، والاتنين كانوا
     * بيتخلطوا في كل شاشة ماليّة. null بيجبر كل مستهلك يقرر يعرض إيه بدل ما يرث كدبة.
     */
    fun remaining(openingBalance: Double, transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double? {
        if (openingBalance <= 0.0) return null
        // نفس قاعدة remainingInCycle بالظبط — كل الدخل، مش المخصص منه بس. لو النسختين
        // اختلفوا، الشاشات اللي لسه بتنادي دي (FamilyState/BudgetTracker/ZadCentralBrain)
        // هتعرض رقم مختلف عن الشاشة الرئيسية لنفس الشهر — وهو بالظبط اختلاف المرجع اللي
        // Task 19.0 اتعمل عشان يقفله.
        return openingBalance - spentThisMonth(transactions, asOf) + incomeThisMonth(transactions, asOf)
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

    /**
     * الدخل اللي العميل أكّد إنه مخصص لمصروف الشهر. مرآة `income_allocated` في
     * zad_budget_state_legacy.
     *
     * **مابيدخلش في حساب الرصيد خلاص** بعد تحوّل زاد لدفتر حسابات — [balanceInCycle]
     * بتجمع كل الدخل. سايبينها لأن العقل لسه بيميّز بين إيداع العميل قال عنه "ده مش
     * للبيت" وإيداع عادي، وده تمييز له معنى في النصيحة حتى لما مابقاش له أثر في الرقم.
     */
    fun allocatedIncomeInCycle(transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double =
        transactions.filter {
            it.txnKind == "income" && it.countsTowardBudget == true &&
                txDate(it)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true
        }.sumOf { it.amount }

    /**
     * إيداعات الدورة اللي لسه محدش سأل العميل عنها. بعد الدفتر دي المفروض تفضل فاضية
     * دايماً — الـ backfill في 20260816010000 صفّى الصفوف القديمة، والعمود بقى default
     * true. أي صف بيظهر هنا دلوقتي معناه كتابة بتفرض null صراحة، وده يستاهل الفحص.
     */
    fun incomeAwaitingDecisionInCycle(transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): List<ZadTransaction> =
        transactions.filter {
            it.txnKind == "income" && it.countsTowardBudget == null &&
                txDate(it)?.let { d -> !d.isBefore(cycleStart) && d.isBefore(cycleEnd) } == true
        }.sortedByDescending { it.createdAt ?: "" }

    /**
     * الرصيد الحالي بحدود الدورة — `الرصيد الابتدائي + كل الدخل - كل المصروف`.
     *
     * ده كان `limit - spent + allocatedIncome`: سقف صرف العميل اختاره، والدخل مابيدخلش
     * غير لما يقول صراحة إنه مخصص للشهر. الاتنين اتشالوا مع تحوّل زاد لدفتر حسابات
     * (migration 20260816010000): مفيش سقف يتطرح منه، وكل جنيه دخل بيزوّد الرصيد ساعة ما
     * يوصل من غير سؤال — السؤال ده هو اللي كان بيخلي راتب ١٠,٠٠٠ وصل فعلاً ما يحركش
     * الرقم ولا مليم.
     *
     * بيرجع null لو الرصيد الابتدائي لسه متحددش (نفس اتفاقية [remaining] بالظبط) — مش
     * صفر. [balanceInCycle] هي النسخة اللي بترجع رقم دايماً.
     *
     * لازم تفضل مطابقة لـ `zad_budget_state` — هي المرجع، ودي المرآة الأوفلاين.
     */
    fun remainingInCycle(openingBalance: Double, transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double? {
        if (openingBalance <= 0.0) return null
        return balanceInCycle(openingBalance, transactions, cycleStart, cycleEnd)
    }

    /**
     * نفس معادلة [remainingInCycle] بس من غير اتفاقية الـ null — رصيد ابتدائي مش متحدد
     * بيتحسب صفر. الفرق ده مقصود: الشاشات محتاجة تفرّق بين "لسه ما حددتش رصيدك" و"رصيدك
     * صفر"، لكن أي حاسب (خصم سريع، تعديل يدوي، البوت) محتاج رقم يشتغل عليه دايماً.
     */
    fun balanceInCycle(openingBalance: Double, transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate): Double =
        (openingBalance + incomeInCycle(transactions, cycleStart, cycleEnd) -
            spentInCycle(transactions, cycleStart, cycleEnd)).asMoney()

    /**
     * الفرق اللي لازم يتسجّل عشان الرصيد يبقى [targetBalance] — ده اللي "تعديل يدوي
     * للرصيد" بيعمله. موجب = يتسجّل دخل، سالب = يتسجّل مصروف.
     *
     * ليه فرق يتسجّل مش رصيد ابتدائي يتعدّل. تعديل الرصيد الابتدائي كان هو الحل الواضح
     * (اطرح الدخل، زوّد المصروف، اكتب الناتج)، وهو غلط لسببين: الرقم الناتج بيطلع سالب
     * بسهولة — تصحيح لتحت على حساب دخله وصل بيدّي رصيد ابتدائي بالسالب — وكل قارئ في
     * النظام بيعتبر `monthly_limit <= 0` معناها "لسه متحددش"، فالتصحيح كان هيمسح الكارت
     * بدل ما يصلّح رقمه. وكمان كان بيخبّي الفرق: العميل يقول ٤٢٠٠ وما يفضلش في النظام أي
     * أثر ليه غير رقم بداية اتغيّر من غير سبب مكتوب.
     *
     * معاملة تصحيح بتحل الاتنين. الدفتر بيفضل `دخل - مصروف` بالحرف، والفرق بيفضل سطر
     * ظاهر في السجل يتشاف ويتراجع ويتمسح — نفس اللي [BalanceAnchor] بيعمله للتصحيح
     * الآلي من رصيد البنك، بس دي كلمة العميل مش استنتاج.
     */
    fun correctionToReachBalance(
        targetBalance: Double,
        openingBalance: Double,
        transactions: List<ZadTransaction>,
        cycleStart: LocalDate,
        cycleEnd: LocalDate,
    ): Double = (targetBalance - balanceInCycle(openingBalance, transactions, cycleStart, cycleEnd)).asMoney()

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

    /**
     * سرعة الصرف: المصروف مقسوم على المتوقع خطّياً لحد دلوقتي. أعلى من ١ يعني بيصرف أسرع
     * من إن الفلوس تكفي الدورة.
     *
     * المقارنة بقت مع `الرصيد الابتدائي + دخل الدورة` مش مع سقف — السقف اتشال، والسؤال
     * اللي الرقم ده بيجاوبه ("بصرف أسرع من إن ده يكفّيني؟") فضل هو هو بس بقى متسأل على
     * رقم حقيقي. لازم يفضل مطابق لحساب `velocity` في `zad_budget_state`.
     */
    fun velocityInCycle(openingBalance: Double, transactions: List<ZadTransaction>, cycleStart: LocalDate, cycleEnd: LocalDate, asOf: LocalDate = LocalDate.now()): Double? {
        val funded = openingBalance + incomeInCycle(transactions, cycleStart, cycleEnd)
        if (funded <= 0.0) return null
        val cycleLength = CycleMath.cycleLengthDays(cycleStart, cycleEnd).coerceAtLeast(1)
        val daysElapsed = CycleMath.daysElapsed(asOf, cycleStart).coerceAtLeast(1)
        val expected = funded * daysElapsed / cycleLength
        if (expected <= 0.0) return 0.0
        return spentInCycle(transactions, cycleStart, cycleEnd) / expected
    }

    /**
     * المسموح صرفه يومياً لازم يخرج من "المتاح" لا "المتبقي": الالتزامات المؤكدة
     * القادمة ليست فلوساً قابلة للصرف. القيمة الافتراضية تحفظ توافق الاستدعاءات القديمة
     * التي لم تكن تعرف الالتزامات، بينما الشاشة/السيرفر يمران الإجمالي الحقيقي.
     */
    fun dailyAllowanceInCycle(
        openingBalance: Double,
        transactions: List<ZadTransaction>,
        cycleStart: LocalDate,
        cycleEnd: LocalDate,
        asOf: LocalDate = LocalDate.now(),
        committed: Double = 0.0,
    ): Double? {
        val available = availableInCycle(
            remainingInCycle(openingBalance, transactions, cycleStart, cycleEnd),
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

    /**
     * التجديد الجاي لاشتراك — نفس عقد [nextDueDate] بالظبط، ولنفس السبب.
     *
     * الاشتراكات كانت بتتقرا بـ `LocalDate.parse(renewalDate)` وخلاص: مفيش حد أدنى ومفيش
     * لفّ للأمام. وده كان بيكسر في اتجاهين، الاتنين اتشافوا في بيانات حقيقية 2026-08-15:
     *
     * ١. `renewal_date` عمود **نصّي حر**، والكتابة عليه مش متحققة لا في الشات ولا في شاشة
     *    الاشتراكات. القيم الفعلية في الجدول كانت "30 مارس" و"20" و"30" — ولا واحدة منهم
     *    تاريخ ISO. الكوتلن كان بيرمي الاستثناء ويرجع صفر، والـ SQL شرطه regex على
     *    `^\d{4}-\d{2}-\d{2}$` فكان بيستبعدها. النتيجة إن الاشتراكات ماكانتش بتدخل
     *    "المحجوز" **أبداً** — الرقم مكانش غلط، الميزة كانت ميتة بصمت.
     * ٢. حتى لو التاريخ سليم، تاريخ فات مابيتلفّش. الالتزامات بيلفّها
     *    [nextDueDate]/`zad_obligation_next_due`، والاشتراكات كان بيلفّها
     *    `SubscriptionAutoDeductWorker` بس — وهو PeriodicWork كل ٢٤ ساعة على الأندرويد،
     *    مفيش مقابل ليه على السيرفر. فاشتراك اتخصم الصبح بيفضل محجوز لحد ما الـ worker
     *    يشتغل، وهو نفس المبلغ اللي اتسجّل مصروف فعلاً — محسوب مرتين في "متاح".
     *
     * الترتيب هنا بيقرا نية العميل من أوضح مصدر للأقل: تاريخ ISO كامل، وإلا عمود
     * `due_day` (موجود في الجدول ومكانش بيتقرا أصلاً)، وإلا رقم يوم مجرد جوه النص الحر.
     * لو مفيش أي واحد فيهم بنرجّع null — "مش عارفين" مش "أول الشهر"، عشان تخمين يوم
     * غلط بيحجز فلوس في دورة مش بتاعتها.
     */
    fun nextRenewalDate(sub: ZadSubscription, asOf: LocalDate = LocalDate.now()): LocalDate? {
        val raw = sub.renewalDate?.trim().orEmpty()
        val iso = raw.take(10).let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
        // اليوم بييجي من التاريخ نفسه أول ما يبقى موجود. الريجيكس ده للنص الحر بس، ولو
        // اتساب يشتغل على تاريخ ISO بيمسك أول رقمين في السنة: "2026-05-12" بيدّي ٢٠ من
        // "2026"، فاشتراك يوم ١٢ بيتحوّل ليوم ٢٠ ويقع في الدورة الغلط. نسخة الـ SQL
        // (zad_subscription_next_renewal) كانت مظبوطة من الأول — بتاخد
        // extract(day from v_anchor) لما فيه تاريخ وماتلمسش الريجيكس؛ الكوتلن هو اللي
        // خرج عن النسخة دي، وده اللي كسر الاختبار.
        val dayOfMonth = iso?.dayOfMonth
            ?: sub.dueDay
            ?: Regex("\\d{1,2}").find(raw)?.value?.toIntOrNull()?.takeIf { it in 1..31 }

        var next = iso ?: dayOfMonth?.let {
            LocalDate.of(asOf.year, asOf.month, it.coerceAtMost(asOf.lengthOfMonth()))
        } ?: return null

        // نفس خطوة SubscriptionAutoDeductWorker.nextRenewalDate — لازم يفضلوا متفقين،
        // ده بيتنبأ باللي الـ worker هيعمله والتاني بينفّذه.
        while (next.isBefore(asOf)) {
            next = when (sub.billingCycle?.uppercase()) {
                "YEARLY", "ANNUAL" -> next.plusYears(1)
                "WEEKLY" -> next.plusWeeks(1)
                else -> next.plusMonths(1).let { m ->
                    // القصّ لآخر يوم في الشهر مايبقاش دائم: اشتراك يوم ٣١ يرجع ٣١ بعد فبراير
                    dayOfMonth?.let { d -> m.withDayOfMonth(d.coerceAtMost(m.lengthOfMonth())) } ?: m
                }
            }
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
            .filter { it.isActive }
            .sumOf { sub -> nextRenewalDate(sub, asOf)?.let { if (it.isBefore(cycleEnd)) sub.amount else 0.0 } ?: 0.0 }
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
