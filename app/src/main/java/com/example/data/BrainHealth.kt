package com.example.data

/**
 * صحة العقل الاستباقي — **المنطق الصافي** (المرحلة 4، نسخة ٢).
 *
 * الملف ده متعمَّد يكون خالي من أي `android.*` أو Supabase: [BrainHealthInput] عيّنات
 * خام، و[evaluateBrainHealth] بتحوّلها لحكم. كل الفلترة والعدّ هنا مش في
 * `SupabaseRepo` — عشان `BrainHealthTest` يغطّيها على JVM عادي، لأن **ده بالظبط
 * المنطق اللي بيقرر إذا كان العقل واقف**، ولو اتكسر بصمت الشاشة تبقى بتكدب بواجهة
 * خضرا.
 *
 * ## ليه النسخة دي مختلفة عن الأولى (مراجعة 2026-09-13 قبل الدفع)
 *
 * النسخة الأولى كانت بتحكم من `zad_brain_runs` + "آخر إشارة من أي جدول"، وسقطت في
 * قياس حقيقي على الداتابيز قبل ما تتدفع:
 *
 * - **257 من 263** صف في `zad_brain_runs` هم `trigger='chat'` — المستخدم بيكلّم
 *   زاد، مش العقل الاستباقي بيشتغل. أي محادثة كانت بتصفّر عدّاد السكوت وتخبّي عقل
 *   ميت. آخر `daily` ناجح كان قبلها بأسبوعين.
 * - الشغل الاستباقي الحقيقي هو صفوف `agent_tasks` بنوع (`kind`) غير `reminder`،
 *   اللي بيكتبها `agent_proactive_scan()`. من 09-06 لـ09-12 **مفيش ولا صف لأي
 *   مستخدم** (عطل 42P01)، بينما `cron.job_run_details` قال 336/336 نجاح (لأن
 *   `net.http_post` async ومابيعكسش نتيجة الطلب الحقيقي). النسخة الأولى كانت هتقول
 *   "تمام" طول الأسبوع ده.
 * - تشغيلة فاشلة في مسار التحليل (مش الشات) بتتكتب `status:"queued"` مش `"failed"`
 *   (`zad-brain/index.ts`: `decideOnBrainFailure` → `update({status:"queued", error})`)
 *   وده نهائي، مفيش حاجة بتعيد المحاولة. النسخة الأولى كانت بتعدّ `"failed"` بس.
 * - `zad_brain_queue` write-only فعليًا: الإدراج الوحيد ليها بيحطّ `last_error` مع
 *   كل صف، ومفيش قاري بيمسح أو يعلّم صف "اتعالج". فحص "attempts>0 أو last_error
 *   موجود" كان بيتحقق **دايمًا**، يعني صف واحد قديم بيخلّي المستخدم STALLED للأبد.
 * - `agent_tasks.overdueTasks` كان بيتحسب وبيتجاهل — سر كرون منتهي الصلاحية (نفس
 *   عطل يوم 2026-09-13 اللي اتصلّح في نفس الجلسة) سيّب مهام مجدولة من غير تنفيذ
 *   لمدة 35 دقيقة، والشاشة القديمة كانت هتقول "تمام" طول الوقت ده.
 *
 * ## القرارات الجديدة
 *
 * 1. **إشارة الحياة = المهام الاستباقية بس** (`kind != "reminder"`)، مش أي جدول.
 *    الشات نشاط حقيقي لكن بيقيس حاجة تانية خالص (رغبة المستخدم يتكلم، مش إن العقل
 *    شغال في الخلفية).
 * 2. **عتبة السكوت بتتكيّف مع إيقاع المستخدم نفسه** بدل رقم ثابت: مستخدم بياخد
 *    ملخص يومي وواحد بياخد تنبيه كل أسبوع الاتنين عندهم "طبيعي" مختلف. العتبة =
 *    أكبر قيمة بين [BrainHealthLimits.MIN_SILENCE_HOURS] و1.5×**الوسيط** (مش
 *    المتوسط، عشان فجوة العطل نفسها ماتكبّرش العتبة) بين أيام النشاط في آخر
 *    [BrainHealthLimits.CADENCE_WINDOW_DAYS] يوم.
 * 3. **`queued` فشل، زي `failed` بالظبط.** ونفس الفحص اللي `zad_brain_health_check`
 *    بيعمله على السيرفر (٢٠٪ فشل بحد أدنى ٥ تشغيلات) اتنقل هنا، بالإضافة لفحص "آخر
 *    ٣ تشغيلات فشلوا ورا بعض" اللي بيمسك عطل حالي حتى لو العيّنة صغيرة.
 * 4. **الطابور بيتقاس بنافذة ٧ أيام** بدل "لسه ماتلمسش"، عشان يبان ويختفي مع الوقت
 *    بدل ما يفضل أحمر للأبد.
 * 5. **مهام متأخرة أو معلّقة في `running` = وقفة حقيقية**، اتفعّلت في الحكم مش
 *    مجرد رقم معروض.
 * 6. **مستخدم جديد مالوش أي تنبيه استباقي لسه = `HEALTHY`** (سبب معلوماتي)، مش
 *    `STALLED` — الفرق بين "لسه بيتعرف عليك" و"واقف" فرق حقيقي، والحل مختلف.
 *
 * النصوص مش هنا بقصد: الأسباب enum والواجهة بتترجمها، فقاعدة i18n في CLAUDE.md
 * مابتتكسرش.
 */

object BrainHealthLimits {
    /**
     * مرآة لافتراضيات `zad-brain/index.ts` (`DAILY_REQUEST_CAP`/`DAILY_TOKEN_CAP`).
     * ⚠️ السقف ده **على المحادثة بس** ولمستخدمي الباقة المجانية بس
     * (`handleAgentTurn`) — الفحص الاستباقي ومنفّذ المهام مابيقروش `agent_usage`
     * خالص. فالقيم دي تحذير على الشات، مش حكم على العقل الاستباقي.
     */
    const val DAILY_REQUESTS = 60
    const val DAILY_TOKENS = 200_000

    /** فوقها = رصيد المحادثة المجاني قرب يخلص. */
    const val CHAT_QUOTA_WARN_RATIO = 0.80f

    /** أقل سكوت يتحسب وقفة، حتى لمستخدم بياخد ملخص يومي (يوم فايت + نص يوم سماح). */
    const val MIN_SILENCE_HOURS = 36L

    /** عامل السماح فوق الإيقاع المعتاد قبل ما السكوت يتحسب مشكلة. */
    const val CADENCE_SLACK = 1.5

    /** نافذة قراءة الإيقاع — أطول بكتير من أي عطل عايزين نمسكه. */
    const val CADENCE_WINDOW_DAYS = 60

    /** نفس عتبة `zad_brain_health_check` على السيرفر: 20% من 5 تشغيلات فأكتر. */
    const val RUN_FAILURE_RATIO = 0.20f
    const val RUN_FAILURE_MIN_SAMPLE = 5

    /** آخر كام تشغيلة فاشلة ورا بعض تكفي لوحدها، من غير ما تستنى عيّنة كبيرة. */
    const val CONSECUTIVE_FAILURES = 3

    /** مهمة `pending` فات ميعادها بأكتر من كده = المنفّذ واقف. الكرون كل ٥ دقايق. */
    const val OVERDUE_GRACE_MINUTES = 15L

    /** صف `running` أقدم من كده = اتقتل في النص ومحدش هيكمّله. */
    const val STUCK_RUNNING_MINUTES = 30L
}

/** حالة العقل في سطر واحد. `STALLED` = محتاج تدخّل، مش مجرد يوم هادي. */
enum class BrainHealthStatus { HEALTHY, QUIET, STALLED }

/** سبب واحد محدَّد. `severity` مصدر الحقيقة الوحيد لخطورته — [BrainHealth.status] بيتحسب منه. */
enum class BrainHealthReason(val severity: BrainHealthStatus) {
    /** زاد كان بيبعت تنبيهات بإيقاع معروف وبطّل. العطل الأساسي اللي الشاشة دي اتعملت عشانه. */
    PROACTIVE_SILENT(BrainHealthStatus.STALLED),

    /** مهام فات ميعادها أو اتعلّقت في `running` — المنفّذ واقف. */
    TASKS_BACKED_UP(BrainHealthStatus.STALLED),

    /** نسبة معتبرة من التشغيلات بتفشل، أو آخر كام تشغيلة فشلوا ورا بعض. */
    RUNS_FAILING(BrainHealthStatus.STALLED),

    /** مهام اتنفذت ورجعت بفشل في آخر أسبوع. */
    TASKS_FAILED(BrainHealthStatus.STALLED),

    /** طلبات اتسجّلت في طابور إعادة المحاولة — ومفيش حاجة على السيرفر بتعيدها فعليًا. */
    REQUESTS_NOT_RETRIED(BrainHealthStatus.QUIET),

    /** رصيد المحادثة المجاني قرب يخلص — بيأثر على الشات مش على التنبيهات الاستباقية. */
    CHAT_QUOTA_NEAR_LIMIT(BrainHealthStatus.QUIET),

    /** لسه ولا تنبيه استباقي خالص — مستخدم جديد أو مفيش بيانات تستاهل. معلومة مش عطل. */
    NO_PROACTIVE_YET(BrainHealthStatus.HEALTHY),
}

/**
 * نوع آخر عطل، بدل نص الخطأ الخام. الخام بيكون JSON من مزوّد الموديل فيه أرقام
 * مفاتيح وأسماء كوتة داخلية — مش مفهوم لعيلة، وبيسرّب تفاصيل مالهاش لازمة للعرض.
 */
enum class BrainFailureKind { PROVIDER_BUSY, CONFIGURATION, OTHER }

fun classifyBrainFailure(error: String?): BrainFailureKind? {
    if (error.isNullOrBlank()) return null
    val e = error.lowercase()
    // الترتيب مهم: رسالة انشغال ممكن يبقى فيها رقم زي 400 جوه تفاصيلها الفرعية.
    val busy = listOf("429", "503", "resource_exhausted", "unavailable", "quota", "providerunavailable", "overloaded")
    if (busy.any { it in e }) return BrainFailureKind.PROVIDER_BUSY
    val config = listOf("400", "invalid_argument", "function_declarations", "thought_signature")
    if (config.any { it in e }) return BrainFailureKind.CONFIGURATION
    return BrainFailureKind.OTHER
}

/** تشغيلة من `zad_brain_runs`، أي `trigger` — آخر ٧ أيام، أي ترتيب. */
data class RunSample(val startedAtMillis: Long, val status: String, val error: String? = null)

/**
 * التشغيلة فاشلة لو:
 * - `failed` — مسار المحادثة (`finishRun`).
 * - `queued` — **فشل مش انتظار**: مسار التحليل بيكتبها كده لما نداء الموديل يقع،
 *   ومفيش حاجة بتعيدها تلقائيًا. `zad_brain_health_check` على السيرفر بيعدّها فشل برضه.
 * - `running` أقدم من [BrainHealthLimits.STUCK_RUNNING_MINUTES] — اتقتلت في النص.
 */
fun RunSample.isFailure(nowMillis: Long): Boolean = when (status) {
    "failed", "queued" -> true
    "running" -> nowMillis - startedAtMillis > BrainHealthLimits.STUCK_RUNNING_MINUTES * MILLIS_PER_MINUTE
    else -> false
}

/**
 * صف من `agent_tasks`. مستخدَم في دورين مختلفين حسب مين بيملاه:
 * - `recentTasksForCadence`: بس `kind` و`createdAtMillis` مهمين (فلترة `reminder` بتتعمل هنا).
 * - `openTasks`: بس `status`/`scheduledForMillis`/`updatedAtMillis` مهمين.
 */
data class TaskSample(
    val kind: String = "",
    val status: String = "",
    val createdAtMillis: Long = 0L,
    val scheduledForMillis: Long? = null,
    val updatedAtMillis: Long = 0L,
)

/**
 * العيّنات الخام زي ما الـrepo قراها. أي فلترة زمنية غير مذكورة (٧ أيام، آخر ٥٠٠
 * صف...) هي فلتر `SupabaseRepo` قبل الإرسال هنا — القرارات المنطقية (مين فشل، مين
 * "استباقي") هنا في [evaluateBrainHealth] عشان تتغطى بتست.
 */
data class BrainHealthInput(
    val nowMillis: Long,
    /** آخر ٧ أيام، أي trigger. */
    val recentRuns: List<RunSample> = emptyList(),
    /** آخر ~٥٠٠ مهمة بأي `kind`، الأحدث الأول — `reminder` بتتفلتر جوه [evaluateBrainHealth]. */
    val recentTasksForCadence: List<TaskSample> = emptyList(),
    /** مهام `pending`/`running` حاليًا، أي عمر. */
    val openTasks: List<TaskSample> = emptyList(),
    /** مهام `failed` اتحدّثت آخر ٧ أيام. */
    val failedTasksLast7Days: Int = 0,
    /** صفوف `zad_brain_queue` اتسجّلت آخر ٧ أيام. */
    val queueRowsLast7Days: Int = 0,
    val insightsLast7Days: Int = 0,
    val pendingInsights: Int = 0,
    val driftLast7Days: Int = 0,
    val activeGoals: Int = 0,
    val requestsToday: Int = 0,
    val tokensToday: Int = 0,
)

/** اللقطة المحسوبة — كل اللي الشاشة بتعرضه، محسوبة مرة واحدة في [evaluateBrainHealth]. */
data class BrainHealth(
    val nowMillis: Long,
    val lastProactiveAtMillis: Long?,
    val proactiveTasksLast7Days: Int,
    /** السكوت المسموح بإيقاع المستخدم بالساعات. `null` = الإيقاع لسه مش معروف (استُخدم الحد الأدنى). */
    val expectedSilenceHours: Long?,
    val runsLast7Days: Int,
    val failedRunsLast7Days: Int,
    val lastFailureKind: BrainFailureKind?,
    val overdueTasks: Int,
    val stuckRunningTasks: Int,
    val pendingTasksCount: Int,
    val failedTasksLast7Days: Int,
    val queueRowsLast7Days: Int,
    val insightsLast7Days: Int,
    val pendingInsights: Int,
    val driftLast7Days: Int,
    val activeGoals: Int,
    val requestsToday: Int,
    val tokensToday: Int,
    val reasons: List<BrainHealthReason>,
) {
    /** الحالة العامة = أخطر سبب منطبق. لستة فاضية = كل حاجة تمام. */
    val status: BrainHealthStatus
        get() = reasons.maxOfOrNull { it.severity } ?: BrainHealthStatus.HEALTHY

    /** أعلى نسبة استهلاك بين الطلبات والتوكنز — أي واحدة فيهم بتقفل باب المحادثة. */
    val chatQuotaRatio: Float
        get() = maxOf(
            requestsToday.toFloat() / BrainHealthLimits.DAILY_REQUESTS,
            tokensToday.toFloat() / BrainHealthLimits.DAILY_TOKENS,
        )

    /** ساعات كاملة من آخر تنبيه استباقي. `null` = ولا تنبيه. سالب (ساعة الجهاز متقدمة) = صفر. */
    val hoursSinceLastProactive: Long?
        get() = lastProactiveAtMillis?.let { ((nowMillis - it) / MILLIS_PER_HOUR).coerceAtLeast(0L) }

    /** مهام واقفة أو متأخرة مجمّعة — ده الرقم اللي [BrainHealthReason.TASKS_BACKED_UP] بيتكلم عنه. */
    val tasksBackedUp: Int get() = overdueTasks + stuckRunningTasks
}

internal const val MILLIS_PER_MINUTE = 60_000L
internal const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
internal const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

/**
 * السكوت المسموح بالساعات، أو `null` لو الإيقاع لسه مش معروف (أقل من يومين UTC
 * مميّزين جوه النافذة). بيستخدم **الوسيط** بين أيام النشاط المميّزة، مش المتوسط —
 * عشان فجوة العطل نفسها ماتكبّرش العتبة وتخبّي نفسها.
 */
fun expectedProactiveSilenceHours(taskTimesMillis: List<Long>): Long? {
    val days = taskTimesMillis.map { Math.floorDiv(it, MILLIS_PER_DAY) }.distinct().sorted()
    if (days.size < 2) return null
    val gaps = days.zipWithNext { a, b -> b - a }.sorted()
    val median = if (gaps.size % 2 == 1) {
        gaps[gaps.size / 2].toDouble()
    } else {
        (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2.0
    }
    val cadenceHours = (median * 24 * BrainHealthLimits.CADENCE_SLACK).toLong()
    return maxOf(BrainHealthLimits.MIN_SILENCE_HOURS, cadenceHours)
}

fun evaluateBrainHealth(input: BrainHealthInput): BrainHealth {
    val now = input.nowMillis
    val weekAgo = now - 7 * MILLIS_PER_DAY
    val windowStart = now - BrainHealthLimits.CADENCE_WINDOW_DAYS * MILLIS_PER_DAY

    val runs = input.recentRuns.filter { it.startedAtMillis >= weekAgo }
    val failedRuns = runs.filter { it.isFailure(now) }
    val newestFirst = runs.sortedByDescending { it.startedAtMillis }
    val lastFailureRun = newestFirst.firstOrNull { it.isFailure(now) }

    // "استباقي" = أي نوع مهمة غير reminder. القرار ده هنا (مش في استعلام الـrepo)
    // عشان يتغطى بتست — ده بالظبط الفرق بين النسخة دي والنسخة اللي كانت بتنخدع
    // بنشاط الشات.
    val proactive = input.recentTasksForCadence.filter { it.kind.isNotBlank() && it.kind != "reminder" }
    val lastProactive = proactive.maxOfOrNull { it.createdAtMillis }
    val proactiveInWindow = proactive.filter { it.createdAtMillis >= windowStart }.map { it.createdAtMillis }
    val expectedSilence = expectedProactiveSilenceHours(proactiveInWindow)

    val overdue = input.openTasks.count {
        it.status == "pending" && it.scheduledForMillis != null &&
            now - it.scheduledForMillis > BrainHealthLimits.OVERDUE_GRACE_MINUTES * MILLIS_PER_MINUTE
    }
    val stuckRunning = input.openTasks.count {
        it.status == "running" &&
            now - it.updatedAtMillis > BrainHealthLimits.STUCK_RUNNING_MINUTES * MILLIS_PER_MINUTE
    }
    val pendingCount = input.openTasks.count { it.status == "pending" }

    val reasons = mutableListOf<BrainHealthReason>()

    if (lastProactive == null) {
        reasons += BrainHealthReason.NO_PROACTIVE_YET
    } else {
        val silentHours = (now - lastProactive) / MILLIS_PER_HOUR
        // الإيقاع مش معروف؟ منرجعش لـ"متحكمش" — بنستخدم الحد الأدنى الآمن بدل ما
        // نسيب سكوت طويل من غير ما حد يحكم عليه.
        val allowed = expectedSilence ?: BrainHealthLimits.MIN_SILENCE_HOURS
        if (silentHours > allowed) reasons += BrainHealthReason.PROACTIVE_SILENT
    }

    if (overdue + stuckRunning > 0) reasons += BrainHealthReason.TASKS_BACKED_UP

    val ratioTrips = runs.size >= BrainHealthLimits.RUN_FAILURE_MIN_SAMPLE &&
        failedRuns.size.toFloat() / runs.size >= BrainHealthLimits.RUN_FAILURE_RATIO
    val streakTrips = newestFirst.size >= BrainHealthLimits.CONSECUTIVE_FAILURES &&
        newestFirst.take(BrainHealthLimits.CONSECUTIVE_FAILURES).all { it.isFailure(now) }
    if (ratioTrips || streakTrips) reasons += BrainHealthReason.RUNS_FAILING

    if (input.failedTasksLast7Days > 0) reasons += BrainHealthReason.TASKS_FAILED
    if (input.queueRowsLast7Days > 0) reasons += BrainHealthReason.REQUESTS_NOT_RETRIED

    val chatQuotaRatio = maxOf(
        input.requestsToday.toFloat() / BrainHealthLimits.DAILY_REQUESTS,
        input.tokensToday.toFloat() / BrainHealthLimits.DAILY_TOKENS,
    )
    if (chatQuotaRatio >= BrainHealthLimits.CHAT_QUOTA_WARN_RATIO) {
        reasons += BrainHealthReason.CHAT_QUOTA_NEAR_LIMIT
    }

    return BrainHealth(
        nowMillis = now,
        lastProactiveAtMillis = lastProactive,
        proactiveTasksLast7Days = proactive.count { it.createdAtMillis >= weekAgo },
        expectedSilenceHours = expectedSilence,
        runsLast7Days = runs.size,
        failedRunsLast7Days = failedRuns.size,
        lastFailureKind = lastFailureRun?.let { classifyBrainFailure(it.error) ?: BrainFailureKind.OTHER },
        overdueTasks = overdue,
        stuckRunningTasks = stuckRunning,
        pendingTasksCount = pendingCount,
        failedTasksLast7Days = input.failedTasksLast7Days,
        queueRowsLast7Days = input.queueRowsLast7Days,
        insightsLast7Days = input.insightsLast7Days,
        pendingInsights = input.pendingInsights,
        driftLast7Days = input.driftLast7Days,
        activeGoals = input.activeGoals,
        requestsToday = input.requestsToday,
        tokensToday = input.tokensToday,
        // الأخطر الأول؛ `sortedByDescending` ثابت فالمتساويين بيفضلوا بترتيب الاكتشاف فوق.
        reasons = reasons.sortedByDescending { it.severity.ordinal },
    )
}
