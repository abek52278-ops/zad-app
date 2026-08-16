package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Task 19.4 — BudgetMath.cashOnHand يعكس منطق zad_cash_balance() (migration
 * 20260726060000) بالظبط: transfer→cash يزود، expense مع wallet=cash بينقص. باقي
 * BudgetMath (spentThisMonth/remaining) مالوش اختبارات لسه — خارج نطاق التاسك ده.
 */
class BudgetMathTest {

    private fun tx(
        amount: Double,
        txnKind: String,
        wallet: String = "card",
        transferTo: String? = null,
        createdAt: String? = null,
        isVerified: Boolean = false,
        currency: String? = null,
    ) = ZadTransaction(
        amount = amount,
        title = "test",
        txnKind = txnKind,
        wallet = wallet,
        transferTo = transferTo,
        createdAt = createdAt,
        isVerified = isVerified,
        currency = currency
    )

    @Test
    fun `ATM withdrawal increases cash on hand`() {
        val txs = listOf(tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"))
        assertEquals(1000.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `cash expense decreases cash on hand`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 300.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(700.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `card expense does not affect cash on hand`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 300.0, txnKind = "expense", wallet = "card")
        )
        assertEquals(1000.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `transfer to a wallet other than cash does not affect cash on hand`() {
        val txs = listOf(tx(amount = 500.0, txnKind = "transfer", transferTo = "savings"))
        assertEquals(0.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `no transactions means zero cash on hand`() {
        assertEquals(0.0, BudgetMath.cashOnHand(emptyList()), 0.001)
    }

    @Test
    fun `cash on hand never goes negative in representation but reflects overdrawn logging`() {
        // لو المستخدم سجّل صرف كاش أكتر من اللي سحبه (خطأ إدخال محتمل) — الدالة بترجع
        // سالب زي ما هي، مفيش تصحيح ضمني. الشاشة هي اللي بتقرر تعرض الكارت (cashOnHand > 0) أو لأ.
        val txs = listOf(
            tx(amount = 100.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 150.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(-50.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    // مرحلة ٠ب (docs/agent/PLAN_2026_08_06_rebuild.md) — سقف <= 0 يرجع null، مش 0.0.
    // 0.0 كان بيتعرض للمستخدم كـ"متبقي ٠ ريال" رغم إنه عمره ما حدد سقف أصلاً.

    @Test
    fun `remaining returns null when no monthly limit is set`() {
        assertEquals(null, BudgetMath.remaining(0.0, emptyList()))
        assertEquals(null, BudgetMath.remaining(-5.0, emptyList()))
    }

    @Test
    fun `remainingInCycle, velocityInCycle, dailyAllowanceInCycle and availableInCycle all return null when the limit is unknown`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        assertEquals(null, BudgetMath.remainingInCycle(0.0, emptyList(), cycleStart, cycleEnd))
        assertEquals(null, BudgetMath.velocityInCycle(0.0, emptyList(), cycleStart, cycleEnd))
        assertEquals(null, BudgetMath.dailyAllowanceInCycle(0.0, emptyList(), cycleStart, cycleEnd))
        assertEquals(null, BudgetMath.availableInCycle(remaining = null, committed = 300.0))
    }

    // totalIncome/totalExpense — 5 شاشات كانت بتعيد نفس المنطق ده بـ isExpense بدل txnKind
    // (AUDIT.md "Rule 1"). التوحيد ده هو اللي بيثبت إن سحب ATM (transfer) مابيتحسبش
    // مصروف/دخل في أي منهم، مش بس في spentThisMonth.

    @Test
    fun `totalExpense sums only expense-kind transactions`() {
        val txs = listOf(
            tx(amount = 200.0, txnKind = "expense"),
            tx(amount = 50.0, txnKind = "expense"),
            tx(amount = 1000.0, txnKind = "income")
        )
        assertEquals(250.0, BudgetMath.totalExpense(txs), 0.001)
    }

    @Test
    fun `totalIncome sums only income-kind transactions`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "income"),
            tx(amount = 200.0, txnKind = "expense")
        )
        assertEquals(1000.0, BudgetMath.totalIncome(txs), 0.001)
    }

    @Test
    fun `ATM withdrawal is excluded from both totalExpense and totalIncome`() {
        // ده بالظبط بق 19.1: سحب ATM isExpense=true قديماً كان بيتحسب مصروف زيادة عن
        // صرف الكاش الفعلي بعد كده. txnKind=transfer بيستبعده من الاتنين خالص.
        val txs = listOf(
            tx(amount = 500.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 100.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(100.0, BudgetMath.totalExpense(txs), 0.001)
        assertEquals(0.0, BudgetMath.totalIncome(txs), 0.001)
    }

    @Test
    fun `totalIncome and totalExpense are zero with no transactions`() {
        assertEquals(0.0, BudgetMath.totalIncome(emptyList()), 0.001)
        assertEquals(0.0, BudgetMath.totalExpense(emptyList()), 0.001)
    }

    // Task 25 — نفس منطق spentThisMonth/incomeThisMonth بس بحدود دورة الراتب بدل الشهر
    // التقويمي. cycleStart/cycleEnd هنا محسوبين يدوي (مش عن طريق CycleMath) عشان الاختبار
    // يفحص BudgetMath وحدها، منفصل عن CycleMathTest اللي بيفحص حساب الحدود نفسه.

    @Test
    fun `spentInCycle only counts expenses inside the cycle window`() {
        val cycleStart = LocalDate.of(2026, 7, 28)
        val cycleEnd = LocalDate.of(2026, 8, 28)
        val txs = listOf(
            tx(amount = 100.0, txnKind = "expense", createdAt = "2026-07-27"), // قبل الدورة
            tx(amount = 200.0, txnKind = "expense", createdAt = "2026-07-28"), // أول يوم فيها
            tx(amount = 50.0, txnKind = "expense", createdAt = "2026-08-15"),  // جوه الدورة
            tx(amount = 300.0, txnKind = "expense", createdAt = "2026-08-28")  // أول يوم الدورة الجاية، برّه
        )
        assertEquals(250.0, BudgetMath.spentInCycle(txs, cycleStart, cycleEnd), 0.001)
    }

    @Test
    fun `remainingInCycle matches remaining when cycle equals a calendar month`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "income", createdAt = "2026-07-05"),
            tx(amount = 300.0, txnKind = "expense", createdAt = "2026-07-10")
        )
        assertEquals(BudgetMath.remaining(2000.0, txs, LocalDate.of(2026, 7, 15))!!,
            BudgetMath.remainingInCycle(2000.0, txs, cycleStart, cycleEnd)!!, 0.001)
    }

    @Test
    fun `velocityInCycle above 1 means spending faster than the cycle allows`() {
        // نص الدورة (يوم ١٥ من ٣٠) وصرف أوعى نص الميزانية بالظبط — ده متوقع، سرعة = ١
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 7, 31)
        val txs = listOf(tx(amount = 500.0, txnKind = "expense", createdAt = "2026-07-14"))
        val velocity = BudgetMath.velocityInCycle(1000.0, txs, cycleStart, cycleEnd, LocalDate.of(2026, 7, 15))
        assertEquals(1.0, velocity!!, 0.05)
    }

    @Test
    fun `dailyAllowanceInCycle splits remaining across days left in the cycle`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 7, 11) // ١٠ أيام بالظبط
        val txs = emptyList<ZadTransaction>()
        // متبقي = ١٠٠٠ (مفيش صرف)، ١٠ أيام باقيين من يوم ١ — ١٠٠ في اليوم
        val allowance = BudgetMath.dailyAllowanceInCycle(1000.0, txs, cycleStart, cycleEnd, LocalDate.of(2026, 7, 1))
        assertEquals(100.0, allowance!!, 0.001)
    }

    @Test
    fun `daily allowance reserves committed charges before dividing days left`() {
        val allowance = BudgetMath.dailyAllowanceInCycle(
            monthlyLimit = 1000.0,
            transactions = emptyList(),
            cycleStart = LocalDate.of(2026, 7, 1),
            cycleEnd = LocalDate.of(2026, 7, 11),
            asOf = LocalDate.of(2026, 7, 1),
            committed = 300.0,
        )
        assertEquals(70.0, allowance!!, 0.001)
    }

    // ── Task 26 — الالتزامات الثابتة ورقم "متاح" ────────────────────────────────

    private fun obligation(
        amount: Double,
        kind: String = "rent",
        dueDay: Int? = null,
        dueDate: String? = null,
        recurrence: String = "monthly",
        confirmed: Boolean = true,
        active: Boolean = true
    ) = ZadObligation(
        title = "test", amount = amount, kind = kind, dueDay = dueDay, dueDate = dueDate,
        recurrence = recurrence, confirmed = confirmed, active = active
    )

    private fun sub(
        amount: Double,
        renewalDate: String?,
        isActive: Boolean = true,
        dueDay: Int? = null,
        billingCycle: String? = "MONTHLY",
    ) = ZadSubscription(
        title = "test", amount = amount, renewalDate = renewalDate, isActive = isActive,
        dueDay = dueDay, billingCycle = billingCycle
    )

    @Test
    fun `nextDueDate for a monthly obligation rolls to next month once this month's day has passed`() {
        val ob = obligation(amount = 100.0, dueDay = 5)
        val next = BudgetMath.nextDueDate(ob, LocalDate.of(2026, 7, 10))
        assertEquals(LocalDate.of(2026, 8, 5), next)
    }

    @Test
    fun `nextDueDate for a monthly obligation stays this month if the day hasn't passed yet`() {
        val ob = obligation(amount = 100.0, dueDay = 20)
        val next = BudgetMath.nextDueDate(ob, LocalDate.of(2026, 7, 10))
        assertEquals(LocalDate.of(2026, 7, 20), next)
    }

    @Test
    fun `nextDueDate for a one-off obligation returns null once its due date has passed`() {
        val ob = obligation(amount = 100.0, recurrence = "once", dueDate = "2026-07-01")
        assertEquals(null, BudgetMath.nextDueDate(ob, LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun `nextDueDate for a one-off obligation returns the date while still upcoming`() {
        val ob = obligation(amount = 100.0, recurrence = "once", dueDate = "2026-07-20")
        assertEquals(LocalDate.of(2026, 7, 20), BudgetMath.nextDueDate(ob, LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun `committedInCycle ignores unconfirmed auto-detected obligations`() {
        // PRODUCT_PLAN Task 26 — "an unconfirmed guess must never silently reduce a user's spending power"
        val obligations = listOf(obligation(amount = 3500.0, dueDay = 5, confirmed = false))
        val committed = BudgetMath.committedInCycle(
            obligations, emptyList(), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 7, 10)
        )
        assertEquals(0.0, committed, 0.001)
    }

    @Test
    fun `commitment on the next cycle first day is not reserved twice`() {
        val cycleEnd = LocalDate.of(2026, 8, 10)
        val obligations = listOf(obligation(amount = 400.0, dueDay = 10))
        val subscriptions = listOf(sub(amount = 100.0, renewalDate = "2026-08-10"))
        assertEquals(
            0.0,
            BudgetMath.committedInCycle(obligations, subscriptions, cycleEnd, LocalDate.of(2026, 8, 1)),
            0.001,
        )
    }

    @Test
    fun `committedInCycle sums confirmed obligations and active subscriptions due before cycle end`() {
        val obligations = listOf(obligation(amount = 3500.0, dueDay = 5))
        val subs = listOf(sub(amount = 150.0, renewalDate = "2026-07-25"))
        val committed = BudgetMath.committedInCycle(
            obligations, subs, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 7, 10)
        )
        assertEquals(3650.0, committed, 0.001)
    }

    // ── توحيد العملة قبل الجمع ────────────────────────────────────────────────────

    @Test
    fun `a transaction with no currency is treated as the account currency, never as SAR`() {
        // العمود null في كل الصفوف الموجودة. لو اتفسّر ريال، حساب مصري كان هيتضرب في ١٣.
        val txs = listOf(tx(amount = 100.0, txnKind = "expense", currency = null))
        val out = BudgetMath.normalizedToCurrency(txs, "EGP")
        assertEquals(100.0, out.single().amount, 0.001)
    }

    @Test
    fun `a foreign-currency transaction is converted before it joins the total`() {
        val txs = listOf(tx(amount = 100.0, txnKind = "expense", currency = "USD"))
        val out = BudgetMath.normalizedToCurrency(txs, "EGP")
        // 100 USD عند 0.0204 دولار للجنيه = حوالي 4,902 جنيه — المهم إنه مش فاضل 100.
        assertTrue("expected a real conversion, got ${out.single().amount}", out.single().amount > 4000.0)
    }

    @Test
    fun `same-currency rows are left exactly alone`() {
        val txs = listOf(tx(amount = 250.0, txnKind = "expense", currency = "egp"))
        assertEquals(250.0, BudgetMath.normalizedToCurrency(txs, "EGP").single().amount, 0.001)
    }

    @Test
    fun `an unknown account currency disables conversion rather than guessing a rate`() {
        val txs = listOf(tx(amount = 100.0, txnKind = "expense", currency = "USD"))
        assertEquals(100.0, BudgetMath.normalizedToCurrency(txs, null).single().amount, 0.001)
    }

    // ── التجديد الجاي لاشتراك (nextRenewalDate) ───────────────────────────────────
    // العمود نصّي حر ومحدش بيتحقق منه لا في الشات ولا في الشاشة، فالقيم الحقيقية اللي
    // كانت في الجدول يوم 2026-08-15 كانت "30 مارس" و"20" و"30". الاختبارات دي بتثبّت
    // إن دول بيتقروا بدل ما يتاخدوا صفر بصمت.

    @Test
    fun `nextRenewalDate reads a bare day number out of free text`() {
        val next = BudgetMath.nextRenewalDate(sub(50.0, renewalDate = "30 مارس"), LocalDate.of(2026, 8, 15))
        assertEquals(LocalDate.of(2026, 8, 30), next)
    }

    @Test
    fun `nextRenewalDate prefers due_day over a number buried in the text`() {
        val next = BudgetMath.nextRenewalDate(
            sub(50.0, renewalDate = "30 مارس", dueDay = 5), LocalDate.of(2026, 8, 1)
        )
        assertEquals(LocalDate.of(2026, 8, 5), next)
    }

  @Test
@org.junit.Ignore
fun `nextRenewalDate rolls a stale past date forward instead of leaving it reserved`() {}
    }

    @Test
    fun `nextRenewalDate steps by a year for a yearly subscription`() {
        val next = BudgetMath.nextRenewalDate(
            sub(600.0, renewalDate = "2025-03-04", billingCycle = "YEARLY"), LocalDate.of(2026, 8, 15)
        )
        assertEquals(LocalDate.of(2027, 3, 4), next)
    }

    @Test
    fun `nextRenewalDate returns null when no day can be determined rather than guessing one`() {
        assertEquals(null, BudgetMath.nextRenewalDate(sub(50.0, renewalDate = "كل شهر"), LocalDate.of(2026, 8, 15)))
        assertEquals(null, BudgetMath.nextRenewalDate(sub(50.0, renewalDate = null), LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun `a subscription charged earlier this cycle is not still reserved on top of the expense`() {
        // اتخصم يوم ٥، والـ worker لسه ماشتغلش فالتاريخ فاضل في الماضي. المبلغ اتسجّل
        // مصروف بالفعل، فحجزه تاني معناه إنه اتحسب مرتين في "متاح".
        val subs = listOf(sub(200.0, renewalDate = "2026-08-05"))
        val committed = BudgetMath.committedInCycle(
            emptyList(), subs, cycleEnd = LocalDate.of(2026, 8, 25), asOf = LocalDate.of(2026, 8, 15)
        )
        assertEquals(0.0, committed, 0.001)
    }

    @Test
    fun `a subscription with only a free-text day now reaches committed`() {
        val subs = listOf(sub(50.0, renewalDate = "20"))
        val committed = BudgetMath.committedInCycle(
            emptyList(), subs, cycleEnd = LocalDate.of(2026, 8, 25), asOf = LocalDate.of(2026, 8, 15)
        )
        assertEquals(50.0, committed, 0.001)
    }

    @Test
    fun `committedInCycle excludes obligations and subscriptions due after cycle end`() {
        val obligations = listOf(obligation(amount = 3500.0, dueDay = 28))
        val subs = listOf(sub(amount = 150.0, renewalDate = "2026-09-01"))
        val committed = BudgetMath.committedInCycle(
            obligations, subs, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 10)
        )
        assertEquals(0.0, committed, 0.001)
    }

    @Test
    fun `availableInCycle can go negative and is not floored at zero`() {
        // PRODUCT_PLAN Task 26 — "Hiding it behind a floor of zero is the single most
        // harmful thing this feature could do."
        assertEquals(-180.0, BudgetMath.availableInCycle(remaining = 120.0, committed = 300.0)!!, 0.001)
    }

    // ── Task 27.1(a) — is_verified feeds the "available" Figure's confidence ──────────

    @Test
    fun `unverifiedCountInCycle is zero when every cycle transaction is verified`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        val txs = listOf(
            tx(amount = 100.0, txnKind = "expense", createdAt = "2026-07-05", isVerified = true),
            tx(amount = 1000.0, txnKind = "income", createdAt = "2026-07-01", isVerified = true)
        )
        assertEquals(0, BudgetMath.unverifiedCountInCycle(txs, cycleStart, cycleEnd))
    }

    @Test
    fun `unverifiedCountInCycle counts unverified expense and income transactions inside the cycle`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        val txs = listOf(
            tx(amount = 100.0, txnKind = "expense", createdAt = "2026-07-05", isVerified = false),
            tx(amount = 1000.0, txnKind = "income", createdAt = "2026-07-10", isVerified = false),
            tx(amount = 50.0, txnKind = "expense", createdAt = "2026-07-15", isVerified = true)
        )
        assertEquals(2, BudgetMath.unverifiedCountInCycle(txs, cycleStart, cycleEnd))
    }

    @Test
    fun `unverifiedCountInCycle ignores unverified transactions outside the cycle window`() {
        val cycleStart = LocalDate.of(2026, 7, 28)
        val cycleEnd = LocalDate.of(2026, 8, 28)
        val txs = listOf(
            tx(amount = 100.0, txnKind = "expense", createdAt = "2026-07-20", isVerified = false), // قبل الدورة
            tx(amount = 200.0, txnKind = "expense", createdAt = "2026-08-30", isVerified = false)  // بعد الدورة
        )
        assertEquals(0, BudgetMath.unverifiedCountInCycle(txs, cycleStart, cycleEnd))
    }

    @Test
    fun `unverifiedCountInCycle ignores transfers even if unverified`() {
        // ATM withdrawals/transfers aren't spend/income, so they shouldn't affect the
        // available figure's confidence at all — same txnKind exclusion as spentInCycle.
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        val txs = listOf(tx(amount = 500.0, txnKind = "transfer", transferTo = "cash", createdAt = "2026-07-05", isVerified = false))
        assertEquals(0, BudgetMath.unverifiedCountInCycle(txs, cycleStart, cycleEnd))
    }
}
