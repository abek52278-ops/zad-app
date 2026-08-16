package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 0 — proves the Kotlin mirror still agrees with the Postgres authority.
 *
 * `zad_budget_state()` (migration `20260809120000_single_budget_authority.sql`) is the one
 * definition of every money figure zad shows; `BudgetMath`/`CycleMath` exist only so the
 * app can render instantly and keep working offline. Two implementations of one definition
 * is the arrangement that produced the original bug, and the only thing that makes it safe
 * is a test that fails the moment they part company.
 *
 * **The expected values below are not hand-derived.** Each one was produced by calling the
 * real function on the live database and pasting the answer back. To extend or refresh
 * them, run the query in the comment above each block against the project and paste the
 * new output — do not "fix" a failure by editing the expectation to match Kotlin. If these
 * disagree, the SQL is right and `BudgetMath.kt`/`CycleMath.kt` is what needs changing.
 */
class BudgetAuthorityParityTest {

    private fun market(country: String): Market = Market.entries.first { it.countryCode == country }

    /**
     * Golden vectors from:
     *
     * ```sql
     * select b.cycle_start, b.cycle_end
     * from public.zad_cycle_bounds(:asof, :day, :anchor, :country) b;
     * ```
     *
     * Covers the cases where a naive implementation diverges: a day-31 salary in a short
     * month, the boundary day itself (start-of-cycle, not end), a last_working_day anchor
     * landing on a weekend, and the two different weekends in the region (Fri/Sat for Egypt
     * and the Gulf, Sat/Sun for Turkey and the Maghreb) — the Deno mirror that used to live
     * in `zad-brain` got the last two wrong by design and shifted the whole cycle.
     */
    @Test
    fun `cycle bounds match zad_cycle_bounds for every golden vector`() {
        data class Case(
            val asOf: String, val day: Int?, val anchor: String, val country: String,
            val start: String, val end: String,
        )

        val cases = listOf(
            Case("2026-08-09", null, "day_of_month", "EG", "2026-08-01", "2026-09-01"),
            Case("2026-08-09", 25, "day_of_month", "EG", "2026-07-25", "2026-08-25"),
            // The anchor day itself opens a new cycle rather than closing the old one.
            Case("2026-08-25", 25, "day_of_month", "EG", "2026-08-25", "2026-09-25"),
            // Day 31 clamped into February, then back out to a 31-day March.
            Case("2026-02-15", 31, "day_of_month", "SA", "2026-01-31", "2026-02-28"),
            Case("2026-03-01", 31, "day_of_month", "SA", "2026-02-28", "2026-03-31"),
            // 2026-08-28 is a Friday: a weekend in Egypt (walk back to Thursday the 27th),
            // an ordinary working day in Turkey (stays the 28th).
            Case("2026-08-09", 28, "last_working_day", "EG", "2026-07-28", "2026-08-27"),
            Case("2026-08-09", 28, "last_working_day", "TR", "2026-07-28", "2026-08-28"),
            Case("2026-05-31", 31, "last_working_day", "SA", "2026-05-31", "2026-06-30"),
            Case("2026-01-01", 1, "day_of_month", "EG", "2026-01-01", "2026-02-01"),
            // Year rollover.
            Case("2026-12-31", 15, "day_of_month", "MA", "2026-12-15", "2027-01-15"),
        )

        for (c in cases) {
            val asOf = LocalDate.parse(c.asOf)
            val m = market(c.country)
            assertEquals(
                "cycleStart for $c",
                LocalDate.parse(c.start),
                CycleMath.cycleStart(asOf, c.day, c.anchor, m),
            )
            assertEquals(
                "cycleEnd for $c",
                LocalDate.parse(c.end),
                CycleMath.cycleEnd(asOf, c.day, c.anchor, m),
            )
        }
    }

    /**
     * Golden vectors from:
     *
     * ```sql
     * select public.zad_obligation_next_due(:recurrence, :due_day, :due_date, :asof);
     * ```
     */
    @Test
    fun `obligation next due matches zad_obligation_next_due for every golden vector`() {
        data class Case(
            val recurrence: String, val dueDay: Int?, val dueDate: String?,
            val asOf: String, val expected: String?,
        )

        val cases = listOf(
            // This month's day has passed, so the charge lands next month.
            Case("monthly", 5, null, "2026-08-09", "2026-09-05"),
            Case("monthly", 5, null, "2026-08-03", "2026-08-05"),
            // Day 31 in February clamps to the 28th rather than rolling into March.
            Case("monthly", 31, null, "2026-02-01", "2026-02-28"),
            Case("quarterly", 10, null, "2026-08-11", "2026-11-10"),
            Case("yearly", 20, null, "2026-08-25", "2027-08-20"),
            Case("once", null, "2026-08-20", "2026-08-09", "2026-08-20"),
            // A one-off whose date has passed is assumed paid — not still committed.
            Case("once", null, "2026-08-01", "2026-08-09", null),
            // A recurring obligation with no due_day is refused, never guessed.
            Case("monthly", null, null, "2026-08-09", null),
        )

        for (c in cases) {
            val ob = ZadObligation(
                title = "test", amount = 100.0, kind = "rent",
                dueDay = c.dueDay, dueDate = c.dueDate, recurrence = c.recurrence,
                confirmed = true, active = true,
            )
            val actual = BudgetMath.nextDueDate(ob, LocalDate.parse(c.asOf))
            if (c.expected == null) assertNull("nextDueDate for $c", actual)
            else assertEquals("nextDueDate for $c", LocalDate.parse(c.expected), actual)
        }
    }

    /**
     * The arithmetic contract itself, stated once in each language:
     * `remaining = opening balance + income - spent`, and an opening balance of zero or
     * less is **unknown**.
     *
     * The `+ income` half has now changed twice, and the second change reverted the first.
     * It originally added every deposit; 20260815133417 narrowed it to income the customer
     * had explicitly allocated, on the grounds that a 20,000 transfer arriving is not
     * 20,000 more of household grocery money. That reasoning held only while the figure
     * was a *ceiling*. The ledger migration (20260816010000) made it a balance, where a
     * deposit landing is exactly a balance going up, so every deposit counts again — and a
     * salary no longer sits in the account moving the headline number by zero while
     * waiting for a question.
     *
     * The null half is unchanged and was a live divergence in its own right: `zad-brain`
     * computed `0 - spent` with no ceiling, producing a negative "remaining" and a
     * permanent threat=OVER for anyone who had never set a budget.
     */
    @Test
    fun `remaining is the ledger balance and a missing opening balance is unknown rather than zero`() {
        val cycleStart = LocalDate.parse("2026-08-01")
        val cycleEnd = LocalDate.parse("2026-09-01")
        val txs = listOf(
            ZadTransaction(amount = 300.0, title = "سوبرماركت", txnKind = "expense", createdAt = "2026-08-05T10:00:00Z"),
            ZadTransaction(
                amount = 200.0, title = "بيع حاجة", txnKind = "income",
                countsTowardBudget = true, createdAt = "2026-08-06T10:00:00Z",
            ),
            // Nobody has asked the customer about this one. Under the ceiling rule it was
            // excluded and the balance did not move; under the ledger it counts like any
            // other deposit, which is the whole point of the change.
            ZadTransaction(amount = 700.0, title = "تحويل", txnKind = "income", createdAt = "2026-08-06T12:00:00Z"),
            // An ATM withdrawal is a transfer, not spending — counting it would double-count
            // the money once it is actually spent in cash (the Task 19.1 bug).
            ZadTransaction(amount = 500.0, title = "سحب", txnKind = "transfer", transferTo = "cash", createdAt = "2026-08-07T10:00:00Z"),
        )

        assertEquals(300.0, BudgetMath.spentInCycle(txs, cycleStart, cycleEnd), 0.001)
        assertEquals(900.0, BudgetMath.incomeInCycle(txs, cycleStart, cycleEnd), 0.001)
        // Still reported honestly, and still meaningful to the agent — it just no longer
        // decides the balance.
        assertEquals(200.0, BudgetMath.allocatedIncomeInCycle(txs, cycleStart, cycleEnd), 0.001)

        // 1000 opening + 900 income - 300 spent. The ATM withdrawal is a transfer and is
        // absent from both sides.
        assertEquals(1600.0, BudgetMath.remainingInCycle(1000.0, txs, cycleStart, cycleEnd)!!, 0.001)
        assertEquals(1600.0, BudgetMath.balanceInCycle(1000.0, txs, cycleStart, cycleEnd), 0.001)

        assertNull(BudgetMath.remainingInCycle(0.0, txs, cycleStart, cycleEnd))
        // ...but the ledger itself always answers, treating an unset opening as zero.
        assertEquals(600.0, BudgetMath.balanceInCycle(0.0, txs, cycleStart, cycleEnd), 0.001)
        assertNull(BudgetMath.availableInCycle(null, 0.0))
    }

    /**
     * The anchored window, shared with `zad_budget_state` since migration 20260816120000:
     *
     * ```sql
     * where user_id = p_user and created_at >= v_anchor
     * ```
     *
     * Unlike the vectors above, this expectation is **derived from the SQL text, not from a
     * live call** — the migration ships in the same change and CI deploys it on merge, so
     * there was no deployed function to query. Flagging that rather than letting it pass as
     * a golden vector: if the two ever disagree in production, re-derive this one first.
     *
     * The two properties that matter are that the anchor replaces the cycle's *lower* bound
     * and removes the upper one. Removing the upper bound is deliberate — a ledger balance
     * is "what I have now" and does not reset when a salary cycle rolls over; the salary
     * landing is an income row that raises it.
     */
    @Test
    fun `the anchored window replaces the cycle lower bound and drops the upper one`() {
        val cycleStart = LocalDate.parse("2026-08-01")
        val cycleEnd = LocalDate.parse("2026-09-01")
        val anchor = java.time.Instant.parse("2026-08-16T12:00:00Z")
        val txs = listOf(
            ZadTransaction(amount = 300.0, title = "قبل النقطة", txnKind = "expense", createdAt = "2026-08-05T10:00:00Z"),
            ZadTransaction(amount = 120.0, title = "بعد النقطة", txnKind = "expense", createdAt = "2026-08-20T10:00:00Z"),
            // بعد نهاية الدورة: الفلتر بالدورة بيستبعدها، الفلتر بالنقطة بيعدّها.
            ZadTransaction(amount = 80.0, title = "الدورة الجاية", txnKind = "expense", createdAt = "2026-09-03T10:00:00Z"),
        )

        assertEquals(420.0, BudgetMath.spentInCycle(txs, cycleStart, cycleEnd, null), 0.001)
        assertEquals(200.0, BudgetMath.spentInCycle(txs, cycleStart, cycleEnd, anchor), 0.001)
        assertEquals(800.0, BudgetMath.balanceInCycle(1000.0, txs, cycleStart, cycleEnd, anchor), 0.001)
    }

    /**
     * `available = remaining - committed`, and it is allowed to be negative. Clamping it at
     * zero would hide exactly the situation the number exists to surface.
     */
    @Test
    fun `available may be negative and is never clamped`() {
        assertEquals(-200.0, BudgetMath.availableInCycle(300.0, 500.0)!!, 0.001)
    }
}
