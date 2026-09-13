package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * المنطق اللي بيقرر إن العقل الاستباقي واقف — نسخة ٢ (`BrainHealth.kt`، مراجعة
 * 2026-09-13).
 *
 * الشاشة نفسها بتعرض اللي [evaluateBrainHealth] بيقوله، فلو العتبات اتكسرت الشاشة
 * هتقول "كل حاجة تمام" وهي مش تمام — نفس العطل الصامت اللي المراجعة دي اتعملت عشان
 * تمنعه. عشان كده كل حالة هنا مبنية على عطل حقيقي كان موجود في النسخة الأولى (شايفينه
 * في تعليق الملف بتاع `BrainHealth.kt`)، مش سيناريو مخترع.
 *
 * `now` ثابت في كل حالة عشان التست مايبقاش بيعتمد على ساعة الجهاز.
 */
class BrainHealthTest {

    private val now = 1_789_000_000_000L // لحظة ثابتة اختيارية
    private val hourMs = 60 * 60 * 1000L
    private val dayMs = 24 * hourMs
    private val minMs = 60 * 1000L

    private fun hoursAgo(h: Long) = now - h * hourMs
    private fun daysAgo(d: Long) = now - d * dayMs
    private fun minutesAgo(m: Long) = now - m * minMs

    /**
     * عقل شغال وسليم — إيقاع استباقي يومي (آخر ١١ يوم، آخر واحدة من ساعتين) و١٠
     * تشغيلات ناجحة آخر يوم. كل حالة تانية بتغيّر حاجة واحدة بس بـ`.copy`.
     */
    private fun baseInput(): BrainHealthInput {
        val cadenceTasks = (0..10L).map { d ->
            TaskSample(kind = "home_weekly_digest", createdAtMillis = hoursAgo(2) - d * dayMs)
        }
        val runs = (0..9L).map { i ->
            RunSample(startedAtMillis = hoursAgo(i + 1), status = "success")
        }
        return BrainHealthInput(
            nowMillis = now,
            recentRuns = runs,
            recentTasksForCadence = cadenceTasks,
            openTasks = emptyList(),
            failedTasksLast7Days = 0,
            queueRowsLast7Days = 0,
            insightsLast7Days = 3,
            pendingInsights = 0,
            driftLast7Days = 0,
            activeGoals = 1,
            requestsToday = 5,
            tokensToday = 10_000,
        )
    }

    // ---------------------------------------------------------------------
    // 1. الأساس السليم
    // ---------------------------------------------------------------------

    @Test
    fun `a healthy baseline has no reasons and status HEALTHY`() {
        val h = evaluateBrainHealth(baseInput())
        assertTrue("مفيش سبب المفروض يتقال", h.reasons.isEmpty())
        assertEquals(BrainHealthStatus.HEALTHY, h.status)
    }

    // ---------------------------------------------------------------------
    // 2. الرجريشن المركزي: الشات مايخبيش عقل استباقي ميت
    // ---------------------------------------------------------------------

    @Test
    fun `chat activity does not mask a dead proactive brain`() {
        // النسخة الأولى كانت بتتخدع بنشاط الشات — ٢٥٧ من ٢٦٣ صف في zad_brain_runs
        // كانت trigger='chat'. هنا الشات نشط جدًا (٢٠ تشغيلة ناجحة حديثة) لكن آخر
        // مهمة استباقية حقيقية من ٥ أيام — بعيد جدًا عن عتبة الإيقاع اليومي (٣٦ ساعة).
        val cadenceTasks = (0..10L).map { d ->
            TaskSample(kind = "home_weekly_digest", createdAtMillis = daysAgo(5) - d * dayMs)
        }
        val chatRuns = (0..19L).map { i -> RunSample(startedAtMillis = hoursAgo(i + 1), status = "success") }
        val h = evaluateBrainHealth(baseInput().copy(recentTasksForCadence = cadenceTasks, recentRuns = chatRuns))
        assertTrue(h.reasons.contains(BrainHealthReason.PROACTIVE_SILENT))
        assertEquals(BrainHealthStatus.STALLED, h.status)
    }

    @Test
    fun `a store visit or a delivery test does not mask a dead proactive brain`() {
        // store_arrival بيتولد من حركة العميل (دخول نطاق محل) مش من الماسح، وdelivery_test صف
        // اختبار. لو اتحسبوا إشارة حياة، زيارة محل النهاردة كانت هتخبّي ماسح ساكت من ٥ أيام —
        // نفس فخ الشات فوق. نفس القايمة في zad_proactive_silence_check (20260913230000).
        val cadenceTasks = (0..10L).map { d ->
            TaskSample(kind = "home_weekly_digest", createdAtMillis = daysAgo(5) - d * dayMs)
        }
        val fresh = listOf(
            TaskSample(kind = "store_arrival", createdAtMillis = hoursAgo(1)),
            TaskSample(kind = "delivery_test", createdAtMillis = hoursAgo(2)),
        )
        val h = evaluateBrainHealth(baseInput().copy(recentTasksForCadence = fresh + cadenceTasks))
        assertTrue(h.reasons.contains(BrainHealthReason.PROACTIVE_SILENT))
        assertEquals(BrainHealthStatus.STALLED, h.status)
    }

    // ---------------------------------------------------------------------
    // 3. queued = فشل، زي failed بالظبط
    // ---------------------------------------------------------------------

    @Test
    fun `a queued run counts as a failure just like failed`() {
        val queued = RunSample(startedAtMillis = hoursAgo(1), status = "queued")
        assertTrue(queued.isFailure(now))
    }

    @Test
    fun `queued rows push the failure ratio over the threshold on their own`() {
        // فشل واحد بس من ١٠ (١٠٪) ماكانش هيعدّي عتبة الـ٢٠٪ لو queued ماتحسبتش.
        // مع الاتنين queued يبقوا ٣ من ١٠ (٣٠٪) — العتبة بتتعدّى.
        val statuses = listOf(
            "success", "success", "success", "failed", "success",
            "success", "queued", "queued", "success", "success",
        )
        assertEquals(1, statuses.count { it == "failed" })
        val runs = statuses.mapIndexed { i, s ->
            RunSample(startedAtMillis = hoursAgo(i + 1L), status = s, error = if (s != "success") "boom" else null)
        }
        val h = evaluateBrainHealth(baseInput().copy(recentRuns = runs))
        assertTrue(h.reasons.contains(BrainHealthReason.RUNS_FAILING))
    }

    // ---------------------------------------------------------------------
    // 4. الفشل المتتالي بيمسك عطل حالي حتى بعيّنة صغيرة
    // ---------------------------------------------------------------------

    @Test
    fun `three consecutive failures trip RUNS_FAILING even under the ratio sample floor`() {
        val runs = listOf(
            RunSample(startedAtMillis = hoursAgo(1), status = "failed"),
            RunSample(startedAtMillis = hoursAgo(2), status = "failed"),
            RunSample(startedAtMillis = hoursAgo(3), status = "failed"),
        )
        val h = evaluateBrainHealth(baseInput().copy(recentRuns = runs))
        assertTrue(h.reasons.contains(BrainHealthReason.RUNS_FAILING))
    }

    @Test
    fun `two consecutive failures with a small sample do not trip RUNS_FAILING`() {
        val runs = listOf(
            RunSample(startedAtMillis = hoursAgo(1), status = "failed"),
            RunSample(startedAtMillis = hoursAgo(2), status = "failed"),
        )
        val h = evaluateBrainHealth(baseInput().copy(recentRuns = runs))
        assertFalse(h.reasons.contains(BrainHealthReason.RUNS_FAILING))
    }

    // ---------------------------------------------------------------------
    // 5. running بس بعد ما يتقل — مش من أول ما يبدأ
    // ---------------------------------------------------------------------

    @Test
    fun `a running run only counts as failed once it is stuck`() {
        val justStarted = RunSample(startedAtMillis = minutesAgo(5), status = "running")
        val stuck = RunSample(startedAtMillis = minutesAgo(45), status = "running")
        assertFalse(justStarted.isFailure(now))
        assertTrue(stuck.isFailure(now))
    }

    // ---------------------------------------------------------------------
    // 6. مهام pending فات ميعادها
    // ---------------------------------------------------------------------

    @Test
    fun `an overdue pending task trips TASKS_BACKED_UP, one inside the grace window does not`() {
        val overdueTask = TaskSample(status = "pending", scheduledForMillis = minutesAgo(20))
        val overdue = evaluateBrainHealth(baseInput().copy(openTasks = listOf(overdueTask)))
        assertTrue(overdue.reasons.contains(BrainHealthReason.TASKS_BACKED_UP))
        assertEquals(BrainHealthStatus.STALLED, overdue.status)

        val withinGrace = TaskSample(status = "pending", scheduledForMillis = minutesAgo(10))
        val notOverdue = evaluateBrainHealth(baseInput().copy(openTasks = listOf(withinGrace)))
        assertFalse(notOverdue.reasons.contains(BrainHealthReason.TASKS_BACKED_UP))
    }

    // ---------------------------------------------------------------------
    // 7. مهمة running اتعلّقت — مستقلة عن الـpending المتأخرة
    // ---------------------------------------------------------------------

    @Test
    fun `a stuck running task trips TASKS_BACKED_UP independently of overdue pending tasks`() {
        val stuckTask = TaskSample(status = "running", updatedAtMillis = minutesAgo(40))
        val stuck = evaluateBrainHealth(baseInput().copy(openTasks = listOf(stuckTask)))
        assertTrue(stuck.reasons.contains(BrainHealthReason.TASKS_BACKED_UP))

        val freshTask = TaskSample(status = "running", updatedAtMillis = minutesAgo(5))
        val fresh = evaluateBrainHealth(baseInput().copy(openTasks = listOf(freshTask)))
        assertFalse(fresh.reasons.contains(BrainHealthReason.TASKS_BACKED_UP))
    }

    // ---------------------------------------------------------------------
    // 8. مستخدم جديد: NO_PROACTIVE_YET لكن HEALTHY، مش STALLED
    // ---------------------------------------------------------------------

    @Test
    fun `a brand-new user with no proactive tasks ever is NO_PROACTIVE_YET and stays HEALTHY`() {
        // فرق متعمَّد عن النسخة الأولى (كانت بتحسب ده STALLED): "لسه بيتعرف عليك"
        // مختلف عن "واقف"، والحل مختلف.
        val h = evaluateBrainHealth(baseInput().copy(recentTasksForCadence = emptyList()))
        assertEquals(listOf(BrainHealthReason.NO_PROACTIVE_YET), h.reasons)
        assertEquals(BrainHealthStatus.HEALTHY, h.status)
        assertNull(h.lastProactiveAtMillis)
    }

    // ---------------------------------------------------------------------
    // 9. الإيقاع المتكيّف: مستخدم أسبوعي مالوش تنبيه كاذب، ويتمسك لو سكت أكتر من إيقاعه
    // ---------------------------------------------------------------------

    @Test
    fun `adaptive cadence tolerates a weekly user's normal gap and flags a longer one`() {
        val weeklyDays = listOf(35L, 28L, 21L, 14L, 5L).map { daysAgo(it) }
        val withinCadence = evaluateBrainHealth(
            baseInput().copy(
                recentTasksForCadence = weeklyDays.map { TaskSample(kind = "weekly_digest", createdAtMillis = it) },
            ),
        )
        assertFalse(withinCadence.reasons.contains(BrainHealthReason.PROACTIVE_SILENT))

        // نفس المستخدم، من غير آخر تنبيه — سكوت من ١٤ يوم، أطول بكتير من ١٫٥×الفجوة
        // المعتادة (٧ أيام => عتبة ~١٠٫٥ يوم).
        val goneQuiet = listOf(35L, 28L, 21L, 14L).map { daysAgo(it) }
        val silent = evaluateBrainHealth(
            baseInput().copy(
                recentTasksForCadence = goneQuiet.map { TaskSample(kind = "weekly_digest", createdAtMillis = it) },
            ),
        )
        assertTrue(silent.reasons.contains(BrainHealthReason.PROACTIVE_SILENT))
    }

    @Test
    fun `expectedProactiveSilenceHours needs two distinct days and uses the median gap`() {
        assertNull(expectedProactiveSilenceHours(emptyList()))
        assertNull(expectedProactiveSilenceHours(listOf(hoursAgo(1))))
        assertNull(expectedProactiveSilenceHours(listOf(now, now, now))) // كل الوقت في نفس اليوم

        // فجوات الأيام هنا: ٧،٧،٧،٩ — عدد زوجي من الفجوات فبيتوسط النص. الوسيط = ٧
        // يوم => العتبة = ٧×٢٤×١٫٥ = ٢٥٢ ساعة.
        val fourGaps = listOf(35L, 28L, 21L, 14L, 5L).map { daysAgo(it) }
        assertEquals(252L, expectedProactiveSilenceHours(fourGaps))
    }

    // ---------------------------------------------------------------------
    // 10. MIN_SILENCE_HOURS بيمنع تنبيه مبكر لما الإيقاع لسه مش معروف
    // ---------------------------------------------------------------------

    @Test
    fun `MIN_SILENCE_HOURS is the floor when the cadence itself is not yet established`() {
        // تنبيه استباقي واحد بس => الإيقاع مش معروف (<٢ يوم مميّز)، فالسكوت المسموح
        // بيرجع للحد الأدنى الآمن مباشرة، مش لصفر أو لرقم أصغر.
        fun withSingleProactive(hoursSilent: Long): BrainHealth {
            val task = TaskSample(kind = "home_weekly_digest", createdAtMillis = now - hoursSilent * hourMs)
            return evaluateBrainHealth(baseInput().copy(recentTasksForCadence = listOf(task)))
        }
        assertFalse(withSingleProactive(35).reasons.contains(BrainHealthReason.PROACTIVE_SILENT))
        assertTrue(withSingleProactive(37).reasons.contains(BrainHealthReason.PROACTIVE_SILENT))
    }

    // ---------------------------------------------------------------------
    // 11. الطابور والكوتة لوحدهم مايتخطوش QUIET
    // ---------------------------------------------------------------------

    @Test
    fun `queue rows alone only reach QUIET`() {
        val h = evaluateBrainHealth(baseInput().copy(queueRowsLast7Days = 3))
        assertTrue(h.reasons.contains(BrainHealthReason.REQUESTS_NOT_RETRIED))
        assertEquals(BrainHealthStatus.QUIET, h.status)
    }

    @Test
    fun `chat quota near the ceiling alone only reaches QUIET, from either requests or tokens`() {
        val tokensHigh = evaluateBrainHealth(baseInput().copy(tokensToday = 190_000))
        assertTrue(tokensHigh.reasons.contains(BrainHealthReason.CHAT_QUOTA_NEAR_LIMIT))
        assertEquals(BrainHealthStatus.QUIET, tokensHigh.status)

        val requestsHigh = evaluateBrainHealth(baseInput().copy(requestsToday = 54))
        assertTrue(requestsHigh.reasons.contains(BrainHealthReason.CHAT_QUOTA_NEAR_LIMIT))
        assertEquals(BrainHealthStatus.QUIET, requestsHigh.status)
    }

    // ---------------------------------------------------------------------
    // 12. تصنيف نوع الفشل
    // ---------------------------------------------------------------------

    @Test
    fun `classifyBrainFailure buckets provider-busy, config, and unknown errors`() {
        assertEquals(BrainFailureKind.PROVIDER_BUSY, classifyBrainFailure("429 Too Many Requests"))
        assertEquals(BrainFailureKind.PROVIDER_BUSY, classifyBrainFailure("RESOURCE_EXHAUSTED: quota"))
        assertEquals(BrainFailureKind.CONFIGURATION, classifyBrainFailure("400 INVALID_ARGUMENT: bad function_declarations"))
        assertEquals(BrainFailureKind.OTHER, classifyBrainFailure("connection reset by peer"))
        assertNull(classifyBrainFailure(null))
        assertNull(classifyBrainFailure(""))
        assertNull(classifyBrainFailure("   "))
    }

    @Test
    fun `lastFailureKind reflects the most recent failing run, not an older one`() {
        val runs = listOf(
            RunSample(startedAtMillis = hoursAgo(1), status = "success"),
            RunSample(startedAtMillis = hoursAgo(2), status = "failed", error = "429 quota exceeded"),
            RunSample(startedAtMillis = hoursAgo(3), status = "failed", error = "400 invalid_argument"),
        )
        val h = evaluateBrainHealth(baseInput().copy(recentRuns = runs))
        assertEquals(BrainFailureKind.PROVIDER_BUSY, h.lastFailureKind)
    }

    // ---------------------------------------------------------------------
    // 13. الأسباب مرتّبة الأخطر أول
    // ---------------------------------------------------------------------

    @Test
    fun `reasons come back sorted most-severe-first`() {
        val overdueTask = TaskSample(status = "pending", scheduledForMillis = minutesAgo(30))
        val h = evaluateBrainHealth(
            baseInput().copy(
                openTasks = listOf(overdueTask),
                queueRowsLast7Days = 2,
                tokensToday = 170_000,
            ),
        )
        assertTrue(h.reasons.contains(BrainHealthReason.TASKS_BACKED_UP))
        assertTrue(h.reasons.contains(BrainHealthReason.REQUESTS_NOT_RETRIED))
        assertTrue(h.reasons.contains(BrainHealthReason.CHAT_QUOTA_NEAR_LIMIT))
        val severities = h.reasons.map { it.severity.ordinal }
        assertEquals("الأخطر الأول", severities.sortedDescending(), severities)
        assertEquals(BrainHealthStatus.STALLED, h.status)
    }

    // ---------------------------------------------------------------------
    // 14. مفيش قسمة على صفر مع تشغيلات فاضية
    // ---------------------------------------------------------------------

    @Test
    fun `no runs at all never trips RUNS_FAILING and never divides by zero`() {
        val h = evaluateBrainHealth(baseInput().copy(recentRuns = emptyList()))
        assertEquals(0, h.runsLast7Days)
        assertEquals(0, h.failedRunsLast7Days)
        assertFalse(h.reasons.contains(BrainHealthReason.RUNS_FAILING))
    }

    // ---------------------------------------------------------------------
    // 15. ساعة الجهاز المتقدمة
    // ---------------------------------------------------------------------

    @Test
    fun `hoursSinceLastProactive never goes negative even with a future timestamp`() {
        val futureTask = TaskSample(kind = "home_weekly_digest", createdAtMillis = now + 5 * dayMs)
        val h = evaluateBrainHealth(baseInput().copy(recentTasksForCadence = listOf(futureTask)))
        assertEquals(0L, h.hoursSinceLastProactive)
    }

    // ---------------------------------------------------------------------
    // 16. عدّاد المهام المعلّقة
    // ---------------------------------------------------------------------

    @Test
    fun `pendingTasksCount reflects every pending task, overdue or not`() {
        val tasks = listOf(
            TaskSample(status = "pending", scheduledForMillis = minutesAgo(5)), // مش متأخر
            TaskSample(status = "pending", scheduledForMillis = minutesAgo(30)), // متأخر
            TaskSample(status = "running", updatedAtMillis = minutesAgo(5)),
            TaskSample(status = "done"),
        )
        val h = evaluateBrainHealth(baseInput().copy(openTasks = tasks))
        assertEquals(2, h.pendingTasksCount)
    }
}
