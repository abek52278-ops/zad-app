# TASK 19.0 — One derived budget authority

> New step. Must land **before** 19.2/19.3. Place at `docs/agent/19_0_single_authority.md`.
>
> Found by the 19.1 audit: there are three independent budget authorities, two of which are
> stored mutable running balances. That is a worse defect than the ATM classification bug and it
> must be removed first, because 19.2's backfill cannot correct a stored balance.

---

## 0. The bug this exposes in the brain

`buildSnapshot` reads `zad_users.budget` as the **monthly limit** and then subtracts spending
from it:

```ts
const budget = money(usr.data?.budget ?? 0);
const spent  = money(expenses.reduce(...));
const remaining = money(budget - spent);      // spending subtracted twice
```

But `update_budget_on_transaction()` has already decremented `budget` on every expense row. So
every `remaining`, `velocity`, `daily_allowance_left`, and threat level the brain has ever
produced is wrong by roughly a full month of spending — always pessimistic, always warning too
early.

This is the most consequential single defect found so far. It affects `zad-brain/index.ts:95`,
`107`, `112`, and `0001_zad_brain.sql:192`, all already listed in the audit.

---

## 1. The decision: derive, never store

**One authority: a sum over transactions, computed on read.** Both stored balances are deleted.

Reasoning, so this is not relitigated later: a stored running balance drifts from truth through
failed sync, deleted transactions, edited amounts, reinstalls, and second devices. Every one of
those drifts is silent and unrecoverable. A derived sum is correct by construction and self-heals
after any data change — including after 19.2's backfill, which is exactly why this comes first.

Performance is not a reason to keep a running total. A `SUM` over a few thousand Room rows is
sub-millisecond, and Room exposes it as a `Flow` that recomputes automatically.

---

## 2. Separate the two meanings currently sharing one column

`zad_users.budget` is doing two jobs: the user's chosen monthly limit, and a live balance. Split
them.

```sql
-- The user's chosen monthly limit. Set by the user, never by a trigger.
alter table public.zad_users
  add column if not exists monthly_limit numeric,
  add column if not exists limit_confirmed_at timestamptz;
```

`monthly_limit` is **nullable**. Null means unknown, and the UI asks rather than guessing.

### Recovering the original limit

The original limit was destroyed by the trigger. It can be reconstructed:

```
monthly_limit ≈ current budget + sum(all amounts where is_expense = true)
```

This only holds if the user never manually edited `budget` and if the trigger never reset.
**Check both before trusting it:**

1. Is there any monthly reset job for `zad_users.budget`? If not, the column has been
   decrementing across every month since install, and for older users it is likely far negative
   and the reconstruction is meaningless.
2. Is there any code path where the user edits `budget` directly? Any such path breaks the
   reconstruction.

Report both findings. Then:

- Reconstruction plausible (positive, in a sane range) → write it to `monthly_limit` with
  `limit_confirmed_at = null`, and have the app confirm once: "ميزانيتك الشهرية ٥٠٠٠ صح؟"
- Reconstruction implausible → leave `monthly_limit` null and ask the user outright.

**Nothing in the app may display or reason on an unconfirmed reconstructed limit.** Consistent
with the standing rule: unconfirmed guesses do not drive numbers. Show "اظبط ميزانيتك" instead.

### Then stop the mutation

```sql
drop trigger if exists trg_update_budget_on_transaction on public.zad_transactions;
drop function if exists public.update_budget_on_transaction();
```

Keep the old `budget` column for one release as a dead field for rollback safety, then drop it.
Do not read it anywhere after this task.

---

## 3. Delete `BudgetTracker`'s running total

`BudgetTracker` in SharedPreferences, mutated by `BankTransactionApplier.kt:30-37`, is the number
users actually see on the home card — and the most drift-prone of the three.

Replace it with a Room query. No incremental mutation anywhere:

```kotlin
@Query("""
  SELECT COALESCE(SUM(amount), 0) FROM transactions
   WHERE txnKind = 'expense'
     AND createdAt >= :cycleStart AND createdAt < :cycleEnd
""")
fun spentInCycle(cycleStart: Long, cycleEnd: Long): Flow<Double>
```

- Delete `deductExpense()` and every call site. The REFUND special case disappears too — a refund
  is simply a row that does not match `txn_kind = 'expense'`.
- If SharedPreferences is still wanted for instant cold-start display, it may cache the **derived**
  value, written after each recompute and never incremented. A cache that can only be overwritten
  by a recomputation cannot drift.
- Once `cycleStart`/`cycleEnd` come from Task 25, this query is already cycle-aware. Until then
  pass calendar month boundaries.

---

## 4. Everything reads `ZadFacts`

The audit found `ZadFacts` exists but only carries `report: BrainReport` and `stressTest`. Extend
it to be the single source every screen reads:

```kotlin
data class ZadFacts(
    val currency: String,
    val monthlyLimit: Figure,      // Figure(value, confident, reason) — see Task 27
    val spent: Double,
    val remaining: Double,
    val committed: Double,         // Task 26
    val available: Double,         // Task 26 — the primary displayed number
    val cashOnHand: Figure,        // Task 19.3
    val cycleStart: LocalDate, val cycleEnd: LocalDate, val daysLeft: Int,
    val velocity: Float, val threat: Threat,
    val byCategory: Map<String, Double>,
    val report: BrainReport, val stressTest: StressTest,
)
```

Then rewire, in this task, all sites the audit listed:

- `HomeScreen.kt:115-117` — stop computing locally; read `ZadFacts`
- `TransactionsScreen.kt:72`, `ZadIntelligenceScreen.kt:202`, `TransactionWidget.kt:73`
- `ZadViewModel.kt:808` (the 85% alert) and `ZadViewModel.kt:2327`
- `ZadCentralBrain.kt` — all 13 sites (191, 206, 294, 360, 394, 704, 709, 726, 731, 835, 882,
  884, 885)

Server side, replace `zad_users.budget` reads with `monthly_limit` and a derived sum:

- `zad-brain/index.ts:95,107,112`
- `update-behavior-profile:37,43,54`
- `0001_zad_brain.sql:192`
- `seasonal:137,161`
- `parent_alerts:34,57`

`parent_alerts` deserves specific attention: the audit notes a child's ATM withdrawal currently
fires a spending alert to a parent. That is not only a wrong number, it is a wrong message sent to
another person — the failure mode `15_family_alerts.md` exists to prevent. Fix it here, do not
wait for Task 15.

---

## 5. Order within this task

1. Answer the two reconstruction questions (monthly reset? manual edit path?). **Report before
   changing anything.**
2. Add `monthly_limit`, backfill where plausible, mark unconfirmed.
3. Drop the trigger.
4. Extend `ZadFacts`; add the derived Room query.
5. Rewire all client sites.
6. Rewire all server sites.
7. Delete `BudgetTracker`'s mutation methods.
8. Only then proceed to 19.2 (wallets and `txn_kind`) and 19.3.

Step 1 gates everything. If the reconstruction is implausible for most users, the app needs an
onboarding prompt for the limit and that changes the shape of step 2.

---

## 6. Acceptance

1. Exactly one code path computes spending. `grep` for `filter { it.isExpense }.sumOf` returns
   only `ZadFacts`.
2. No trigger or function mutates any stored balance. `grep` for `SET budget` returns nothing.
3. `BudgetTracker` has no method that increments or decrements.
4. Deleting a transaction changes the displayed remaining immediately and correctly — the test
   that a stored balance always failed.
5. The brain's `remaining` matches the app's, to the cent, for three real users.
6. A user with a null `monthly_limit` sees a prompt, never a number.
7. `./gradlew assembleDebug testDebugUnitTest lintDebug` clean, run **after** the changes.

Add a test that inserts, edits, and deletes a transaction and asserts the derived remaining is
correct after each. That is the invariant this whole task buys.
