# ZAD — MASTER DIRECTIVE
### Single source of truth. Supersedes `ZAD_AGENT_TASK.md`, `ZAD_FIX_PASS.md`, and `DECISIONS.md`.

---

## 0. EXECUTION PROTOCOL — READ THIS FIRST

This file is comprehensive in **scope**, not in **execution**. Do not attempt to implement
it in one pass.

**The rule: one numbered task, one commit, one report, then stop and wait.**

A directive this size executed in a single pass produces conflicting edits across dozens of
files and a build no one can debug. The tasks are ordered by severity and dependency —
follow the order. Do not skip ahead because a later task looks easier.

After each task, report:
1. Files changed, with a one-line reason each.
2. Anything you found that contradicts this document.
3. Anything you deliberately did not do, and why.
4. Result of `./gradlew assembleDebug testDebugUnitTest lintDebug`.

**Verify in Codespaces. Do not push to trigger CI as a way of testing.** No emulator is
available, so runtime behaviour (Photo Picker on old Android, PDF pagination, TTS
availability) is a manual device check, not a CI check.

**Stop and ask whenever a premise here does not match the code.** That instruction has
already proven correct once and produced a better plan than the one originally given.

---

## 1. WHAT THE APP IS BECOMING

ZAD is an Android app (Kotlin, Compose, Room, Supabase) for managing household affairs. It
must stop being a set of screens and become one autonomous agent: all data flows into one
brain, the brain analyses and corrects the numbers, and it proactively alerts and suggests.

The architecture is settled and not open for redesign:

```
7 data sources → ZadIngest → Room + Supabase
                                  ↓
                       ZadFacts (pure Kotlin, instant, no network)
                                  ↓
                       zad-brain Edge Function (LLM, background only)
                                  ↓
                       zad_insights table (stored decisions)
                                  ↓
        Home cards / unified bell / voice alerts / number corrections
```

**The single most important invariant: no screen ever calls an LLM.** Screens read
`ZadFacts` and `zad_insights`. The brain runs on a schedule and on debounced events. This
is what makes the app instant instead of slow, and it must survive every task below.

---

## 2. REPO FACTS (established by Phase 0 — do not re-explore)

**Supabase tables and the columns that matter:**

| Table | Key columns |
|---|---|
| `zad_transactions` | `id, user_id, amount (double), title, category, is_expense, created_at, bank_name, merchant_name, source_type, is_verified` |
| `zad_users` | `id, name, budget (double), avatar_uri, emergency_fund_balance` — **the monthly budget lives here, there is no budgets table** |
| `zad_inventory` | `id, user_id, item_name, category, quantity (int4), unit, expiry_date, low_stock_threshold` |
| `zad_shopping_list` | `id, user_id, item_name, quantity, estimated_price, is_purchased` |
| `zad_subscriptions` | `id, user_id, title, amount, renewal_date, category, billing_cycle, due_day, auto_deduct` |
| `zad_pharmacy_items` | `name, dosage, remaining_quantity, unit, daily_dose_count, dose_times, price` — **`user_id` unconfirmed, see Task 1** |
| `zad_users` / `family_members` | `family_members: id, family_id, user_id, role, alias, balance, savings_goal` |

**Seven independent transaction write paths** (each calls `dao.insertTransaction()` +
`SupabaseRepo.addTransaction()` on its own):

`ZadViewModel.kt:858,888,1020` · `services/UnifiedBankListener.kt:147,210` ·
`receivers/UnifiedSmsReceiver.kt:100,153,169` · `data/SmsBackfillScanner.kt:90` ·
`data/StatementCsvImporter.kt:143` · `workers/SubscriptionAutoDeductWorker.kt:49` ·
`data/InventoryFlowEngine.kt:111,115,153,188`

**Existing infrastructure to build on, not replace:**

- `SaBankParser.kt` — regex parser, 13 Saudi + 9 Turkish banks, hardcoded in Kotlin. **No
  Egyptian banks.** No OTP/declined filtering.
- `object TxDeduplicator` (`SaBankParser.kt:326`) — fingerprint of amount + isExpense +
  merchant, 10-minute window, SharedPreferences. Has a real test
  (`TxDeduplicatorTest.kt`). Used by the bank listener and SMS receiver only.
- `ZadViewModel` — genuinely a single source of truth, created once in `MainScreen.kt`,
  passed to every screen.
- **No DI framework.** `SupabaseRepo` is a manual Kotlin `object` singleton.
- **WorkManager with 6 workers:** `TransactionSyncWorker`, `SubscriptionAutoDeductWorker`,
  `PeriodicAnalysisWorker`, `MorningSummaryWorker`, `SeasonalEventReminderWorker`,
  `TasbihaReminderWorker`.
- CI: `.github/workflows/build-debug-apk.yml` — writes `.env` from secrets, runs
  `testDebugUnitTest`, then `assembleDebug`. Triggers on push to `main` and
  `feat/ui-redesign` only. No lint, no instrumented tests.
- Tests present: `HomeScreenTest`, `PreviewTest`, `NotificationAndLearningTest`,
  `TxDeduplicatorTest`.

**Contract files to add to the repo (v2 versions only — v1 targets tables that don't exist):**

| File | Path |
|---|---|
| `01_schema_v2.sql` | `supabase/migrations/0001_zad_brain.sql` |
| `02_zad_brain_v2.ts` | `supabase/functions/zad-brain/index.ts` |
| `03_ZadAlertRouter.kt` | `app/src/main/java/com/zad/agent/ZadAlertRouter.kt` |

---

## 3. SETTLED DECISIONS — DO NOT REOPEN

**Money stays `Double`.** No migration to Long minor units. A 64-bit Double carries ~15
significant decimal digits; summing ten thousand transactions leaves error far below one
hundredth of a currency unit. The real Double problems are *display*
(`1234.5600000000001`) and *equality comparison*, and both are fixed without touching the
schema. See Task 7. Revisit only for exact bill-splitting, interest, or an audited export —
and then only in that one calculation.

**No DI framework.** `ZadIngest` follows the existing `object SupabaseRepo` pattern.
Introducing Hilt into a working app is a separate large refactor with no payoff here.

**Parser migration to JSON is additive.** Do not rewrite 22 working bank formats. Try
`assets/bank_rules.json` first, fall back to existing Kotlin rules. New banks go in JSON.

**`TxDeduplicator` is extended, not replaced.** Keep it and its test.

**No instrumented tests in CI.** Emulators in GitHub Actions are slow and flaky, and a
pipeline that fails randomly gets ignored.

**Out of scope entirely** — do not build, do not stub, do not fake: financial challenges,
savings pots, real supermarket pricing (no price API exists). A card that lies is worse than
a card that is absent. If a feature cannot be backed by real data, delete it and say so.

**Already correctly assessed as leave-alone:** `FamilyScreen.kt:546` local state (low risk),
`MarketSelectionScreen.kt:31` (a currency name inside a currency picker is not a bug).

---

## 4. TASKS

### TASK 1 — SECURITY: verify `zad_pharmacy_items` row isolation. **NO CODE.**

The Phase 0 report listed this table's columns with no `user_id`. If that is accurate, every
user's medications are readable by every other user. This outranks everything else here.

```sql
select column_name, data_type from information_schema.columns
 where table_name = 'zad_pharmacy_items';

select tablename, policyname, cmd, qual from pg_policies
 where tablename like 'zad_%';
```

Report: does `user_id` exist? Is RLS enabled on every `zad_*` table? Any table with RLS off
or a policy of `true` is exposed. If anything is exposed, **stop and report — do not
continue to Task 2.**

---

### TASK 2 — Verify the dependency graph. **NO CODE unless broken.**

`storage-kt:3.0.3` has never actually run. supabase-kt 3.x moved to Ktor 3.x. A version
mismatch here **compiles cleanly and passes all unit tests**, then throws
`NoSuchMethodError` at runtime on first use. CI cannot catch this.

```bash
./gradlew app:dependencies --configuration debugRuntimeClasspath \
  | grep -iE "ktor|supabase|kotlinx-serialization"
```

Report: every supabase module with its resolved version (all must match exactly); the
resolved Ktor version (2.x alongside supabase 3.x is a defect); and **every line containing
`->`**, which marks a Gradle version override — those are the silent failures.

If mismatched, fix with the BOM so a mismatch becomes structurally impossible, and remove
every hardcoded supabase version:

```kotlin
implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
```

---

### TASK 3 — Close the CI gaps (do this early so all later tasks are verified)

```yaml
on:
  push:
    branches: [main, feat/ui-redesign]
  pull_request:
  workflow_dispatch:
```

Without `workflow_dispatch`, work on a feature branch is never verified — the build may
never have run for the current work at all.

Add after unit tests:

```yaml
      - name: Lint
        run: ./gradlew lintDebug
      - name: Upload lint report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
```

`if: always()` matters — the report is most useful when the step fails.

Add a drift guard so Task 2 cannot silently regress:

```yaml
      - name: Check dependency alignment
        run: |
          ./gradlew app:dependencies --configuration debugRuntimeClasspath > deps.txt
          if grep -qE "io\.ktor.*:2\." deps.txt; then
            echo "::error::Ktor 2.x resolved alongside supabase 3.x"; exit 1
          fi
```

---

### TASK 4 — OTP and declined-transaction filter. **Live bug corrupting the budget.**

An OTP message containing digits, or a declined transaction, can currently deduct from the
budget. This is the smallest high-severity fix in the project. Do it before anything else in
the parser.

Add a rejection pass in `SaBankParser` that runs **before any amount extraction**:

```
REJECT outright (return null, do not parse):
  OTP · رمز التحقق · كلمة المرور · verification code · لا تشارك · do not share
  declined · مرفوضة · لم تتم · فشلت · insufficient · رصيد غير كاف
  expired · انتهت صلاحية

CLASSIFY as non-expense (record, but do not deduct):
  salary · راتب · deposit · إيداع · تحويل وارد · credit · refund · استرداد · مرتجع
```

Requirements: order matters — reject before classify before extract. Log every rejection
with the raw text to a capped local table so you can confirm the filter is not swallowing
real transactions. Unit-test each keyword class with a real-world sample message.

---

### TASK 5 — Egyptian banks + multipart SMS

The parser covers Saudi and Turkish banks only. The primary user is in Egypt. **Bank reading
does not work for this user at all** — the original complaint that "enabling it does
nothing" was never a permissions problem.

1. Create `assets/bank_rules.json` and load it **before** the Kotlin rules, falling back to
   them. Working Saudi/Turkish rules stay untouched.
2. Add: CIB, QNB Alahli, NBE, Banque Misr, Alex Bank, Bank of Alexandria, HSBC Egypt, and
   the wallets — Vodafone Cash, InstaPay, Fawry. Amount formats `EGP`, `ج.م`, `جنيه`.
3. Parse Arabic-Indic digits (`٠١٢٣٤٥٦٧٨٩`) and Arabic decimal separators.
4. **Reassemble multipart SMS before parsing.** Long bank messages arrive split; the current
   receiver parses fragments. Use `SmsMessage.createFromPdu` across the full PDU array and
   concatenate by originating address within a short window.
5. Rule structure — data, not code:

```json
{
  "id": "eg_cib_debit",
  "country": "EG",
  "senders": ["CIB", "QNB", "NBE", "BanqueMisr"],
  "match": "(?:شراء|خصم|purchase|debited)\\D{0,30}(?<amount>[\\d,]+\\.?\\d*)\\s*(?<currency>EGP|ج\\.?م|جنيه)",
  "merchant": "(?:at|في|لدى)\\s+(?<merchant>[^\\n,.]{2,40})",
  "ref": "(?:ref|مرجع)\\.?\\s*(?<ref>[A-Za-z0-9]{4,})",
  "type": "debit",
  "confidence": 0.9
}
```

6. Store every unparsed bank-looking message (raw text, capped at 200 rows). That is the
   dataset for adding rules accurately later.
7. Honest permission status in settings: a live row reflecting the **real** permission state
   via `NotificationManagerCompat.getEnabledListenerPackages()`, never a toggle that looks
   on while access is off. Plus a **"اختبار"** button that runs the parser on a sample
   message and shows extracted amount, merchant, and type — so the user can verify without
   waiting for a real transaction.

---

### TASK 6 — CSV import and subscription-worker duplication. **Highest-severity data bug.**

`TxDeduplicator`'s 10-minute fingerprint window is correct for live SMS and notifications
arriving seconds apart, and **useless** for CSV import — statement rows are days or weeks
old, so every row falls outside the window. Importing a statement covering a period already
captured by SMS duplicates every overlapping transaction. The budget silently doubles and
the user cannot tell why.

CSV import needs a database query, not a cache. For each imported row, look for an existing
transaction with: amount within 0.005, same `is_expense`, `created_at` in the same calendar
day, and (merchant matches OR either merchant is blank). If found, skip and count as a
merge. Report to the user: "٤٢ عملية اتضافت، ١٧ كانت مسجلة قبل كده".

`SubscriptionAutoDeductWorker` has the same shape of bug: if the bank SMS for a subscription
charge also arrives, the user is charged twice in the app. Match on (subscription title,
amount, same month) before inserting.

Also extend `TxDeduplicator`: add `externalRef` (the bank reference number) as a fingerprint
component when the parser finds one — far more reliable than amount plus merchant — and add
a confidence field. Moving its store from SharedPreferences to Room is cleanup, not a
blocker.

---

### TASK 7 — Money containment (closes the Double decision)

1. One helper, applied at every write to Room and Supabase and at every display:
   ```kotlin
   fun Double.asMoney(): Double = Math.round(this * 100) / 100.0
   ```
2. **Audit every `==` comparison on money.** Search `amount ==`, `total ==`, `balance ==`,
   `budget ==`, `price ==`; replace with `abs(a - b) < 0.005`. Report every occurrence found
   — this is where Double actually produces visible bugs.
3. Round after every currency conversion. Multiplying by an exchange rate is where error
   compounds fastest, and multi-currency is in scope.
4. Add the guard test:
   ```kotlin
   @Test fun sumOf10kTransactionsStaysExact() {
       val amounts = List(10_000) { Random.nextDouble(1.0, 5000.0).asMoney() }
       val sum = amounts.sum()
       val exact = amounts.fold(BigDecimal.ZERO) { a, v -> a + BigDecimal.valueOf(v) }
       assertTrue(abs(sum - exact.toDouble()) < 0.01)
   }
   ```
   If it ever fails, the decision is reopened. Until then it is settled.

---

### TASK 8 — Deploy the brain

1. Add the three v2 contract files at the paths in section 2.
2. Run the migration. Verify with the check query at the bottom of the SQL file.
3. `supabase secrets set ANTHROPIC_API_KEY=...` then `supabase functions deploy zad-brain`.
4. Invoke it manually once with a real `user_id` and `trigger: "daily"`. Report the returned
   JSON, the rows written to `zad_insights`, and the token counts from `zad_brain_runs`.
5. If the snapshot comes back with zeros or empty arrays, the column names in the function
   don't match reality — fix the function, not the schema, and report which names were wrong.

Cost check: with Haiku on the daily run, expect roughly 3000 input + 500 output tokens per
call. Watch `zad_brain_runs` for the first week.

---

### TASK 9 — `ZadFacts`: numbers without AI

Pure Kotlin, no network, no LLM, microseconds. Every home card reads from this. Most of the
cards the user called dead only ever needed this.

```kotlin
data class ZadFacts(
    val currency: String,
    val budget: Double, val spent: Double, val remaining: Double,
    val daysLeftInMonth: Int,
    val dailyAllowanceLeft: Double,
    val velocity: Float,          // spent / (budget * dayOfMonth/daysInMonth)
    val threat: Threat,           // SAFE, WATCH, DANGER, OVER
    val byCategory: Map<String, Double>,
    val stock: List<StockFact>,   // name, qty, daysLeft, rateKnown
    val anomalies: List<Anomaly>,
    val upcoming: List<Upcoming>, // subscription renewals, medication doses
)
```

- Budget comes from `zad_users.budget`. Spend is `zad_transactions` where `is_expense`.
- Per-item `daysLeft = quantity / avgDailyQty`. Compute `avgDailyQty` from real consumption
  history and write it to `zad_consumption` so the brain can see it. Requires ≥3 samples to
  be trusted; below that `rateKnown = false` — and unknown items are exactly what the brain
  should ask about.
- Anomalies: per category, mean and standard deviation over 90 days, minimum 5 samples, flag
  above mean + 2σ.
- Expose as `StateFlow<ZadFacts>` from `ZadViewModel`.

Then point Financial Health, Spending Velocity, Financial Resilience, Monthly Spending,
Consumption Index, and the Budget card at this flow. **Delete any card that cannot be
computed from real data** — specifically anything requiring live market prices.

---

### TASK 10 — Insights reach the user

1. Mirror `zad_insights` into Room so the app works offline.
2. Render by `surface`: `home_card` → Home cards ordered by priority; `bell` → the unified
   notification centre; `voice` → `ZadAlertRouter`.
3. **One bell, one badge, one list**, grouped by day. Remove the scattered bottom-sheet
   alerts on Home and the alert tabs buried inside the intelligence screen.
4. Status transitions: tap → `seen`, act → `acted`, dismiss → `dismissed`. **`dismissed` is
   permanent** — the brain reads it in the snapshot and must never raise it again. This is
   how the agent learns what the user does not care about.
5. Wire `ZadAlertRouter` and implement `AlertStateStore` with DataStore: daily counter,
   last-reset date, dismissed key set.
6. **Fix Arabic TTS before enabling voice.** `TextToSpeech` does not throw when a language
   is unavailable — it silently does nothing, so critical alerts are never spoken and no
   error appears anywhere. Check `isLanguageAvailable(Locale("ar"))` once at init and cache
   it; always post the visual notification **first** and treat speech as an addition, never
   a replacement; register an `UtteranceProgressListener` and log `onError`; surface status
   in settings with `ACTION_INSTALL_TTS_DATA` when data is missing; add a voice test button.

---

### TASK 11 — The question loop (this is where learning happens)

For insights with `kind = 'question'`:
1. Render an inline answer control on Home — number pad, yes/no, or "صوّر" opening the camera.
2. Apply the answer (update inventory, confirm a transaction).
3. Mark the insight `acted`.
4. **Call the brain with `trigger: "chat"` and the answer as `user_message`** so it writes
   what it learned to `zad_memory`.

Without step 4 the app collects answers and forgets them. This loop is the whole difference
between an app and an agent.

Brain triggers, using the existing WorkManager infrastructure:

| Trigger | When |
|---|---|
| `daily` | hang off `MorningSummaryWorker` — do not add a new worker |
| `event` | new worker, 60s debounce, coalescing: expense > 15% of budget, item crossing `daysLeft <= 3`, receipt OCR'd, anomaly detected, missed dose |
| `chat` | user message, mic, or a question answered |

Debounce and coalesce so ten quick transactions produce one brain call. Retry with backoff;
queue when offline.

**Check `PeriodicAnalysisWorker`** — it may already produce insights the brain will replace.
Report what it does and whether it should be retired, so two systems don't compete.

---

### TASK 12 — `ZadIngest`: consolidate the seven write paths

Deliberately late. The brain reads Supabase, so it does not care how many files write —
consolidation buys consistent dedupe and clean event triggering, not agent functionality.
Do not start until Tasks 1–11 are green.

```kotlin
object ZadIngest {
    suspend fun submit(signal: ZadSignal)
}

sealed interface ZadSignal {
    data class Expense(
        val amount: Double, val currency: String, val merchant: String?,
        val category: String?, val occurredAt: Instant,
        val source: Source, val confidence: Float,
        val rawText: String?, val externalRef: String?,
    ) : ZadSignal
    data class InventoryChange(val itemName: String, val deltaQty: Double,
                               val unit: String?, val source: Source) : ZadSignal
    data class Receipt(val imageUri: String, val ocrLines: List<String>,
                       val total: Double?) : ZadSignal
    data class UserUtterance(val text: String) : ZadSignal

    enum class Source { MANUAL, SMS, BANK_NOTIFICATION, CAMERA_OCR, VOICE, CSV_IMPORT,
                        SUBSCRIPTION_WORKER, INVENTORY_ENGINE }
}
```

`submit()` in order: normalize currency from active country (never hardcode) → dedupe
(window-based for live sources, database-query-based for CSV per Task 6) → route by
confidence (≥0.85 write directly; below that write with `is_verified = false` and surface a
confirmation card — never silently guess, never silently drop) → persist to Room then sync
to Supabase → recompute `ZadFacts` → decide whether to wake the brain.

Refactor all seven paths to call it. `object` singleton, matching `SupabaseRepo`. No UI
layout changes in this task.

---

### TASK 13 — Complete account deletion

Currently 5 tables are cleared and `signOut()` is called — the Auth user survives, so the
user can sign back in to an empty profile. The v2 migration's
`on delete cascade` handles the `zad_*` brain tables automatically once the Auth user is
gone, so the remaining work is the Auth deletion itself plus the older tables.

Deleting an Auth user needs `service_role`, which must never ship in the app. Create
`supabase/functions/zad-delete-account/index.ts`. **Identify the user from their own JWT via
`admin.auth.getUser(jwt)` — never from a request body.** Accepting a `user_id` parameter
would let anyone delete anyone.

Then: enumerate every table with `user_id` or `family_id` and confirm coverage; decide and
document the family rule (last member out → delete family rows; otherwise remove membership
only, or you delete a relative's data); prefer `on delete cascade` foreign keys where
possible; remove the avatar from Storage; call `admin.auth.admin.deleteUser`. In the app,
require typing a confirmation word rather than a single tap, then sign out and clear Room.

Verify: seed a test account, delete, query every table for that id — zero rows, and sign-in
must fail.

---

### TASK 14 — Cleanup

- **Delete the empty "وجبات من ثلاجتك" row.** It renders nothing and lies to the user. Chef
  Zad already does this against real Room inventory — put Chef Zad where the row was.
- **Floating buttons.** Replace hardcoded padding with a screen-level `Scaffold` owning
  `floatingActionButton`, and collapse the two floating buttons (family + mic) into **one**
  expandable FAB. Two independently positioned floating buttons on a phone will always fight
  for space; merging them removes the problem instead of managing it. Add `imePadding()`, and
  give every scrollable list `contentPadding` from the Scaffold plus
  `WindowInsets.navigationBars`. Remove every `padding(bottom = Ndp)` that existed to dodge
  them. Verify the last list item is reachable on the smallest supported screen with the
  keyboard open.
- **Top bar and drawer:** logo, avatar, and a working drawer icon in the top bar; move
  Subscriptions and Profile Settings into the drawer.
- **Avatar upload** to Supabase Storage with a real signed-URL flow (this is the first real
  use of `storage-kt`; retest after Task 2).
- **Monthly report** as a real branded PDF from live `ZadFacts` plus the brain's behavioural
  analysis, not a plain-text share intent.
- **Country switching** must propagate currency, price sources, store search radius, and the
  language of brain output together. A user in Turkey must never see Saudi addresses.

---

## 5. ACCEPTANCE CRITERIA

Each must be demonstrated, not asserted:

1. Every `zad_*` table has RLS enabled and a policy scoped to `auth.uid()`.
2. An OTP message and a declined transaction leave the budget unchanged.
3. A test Egyptian bank SMS parses correctly and updates the budget within 2 seconds; the
   same transaction arriving as a notification does not double-count.
4. Importing a CSV that overlaps SMS-captured transactions inserts no duplicates and reports
   the merge count.
5. `sumOf10kTransactionsStaysExact` passes; no `==` comparison on money remains.
6. Launching the app with the network disabled renders every Home card — proving zero LLM
   calls on screen open.
7. Cold start to interactive Home under 1.5 seconds.
8. Dismissing an insight prevents that `dedupe_key` from ever reappearing.
9. Answering a brain question updates the database **and** produces a new `zad_memory` row.
10. Deleting an account leaves zero rows anywhere and sign-in fails.
11. `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.

---

## 6. STANDING CONSTRAINTS

- One task, one commit, one report, then stop.
- **Do not rewrite working code.** Phase 0 established that most of this app functions.
  Extend and redirect; do not regenerate screens that work.
- Never delete a user-facing feature without saying so and why in the report.
- No LLM call on the UI thread or on screen open. Ever.
- The brain never changes a budget without user approval — it suggests, the user confirms.
- Every brain modification to user data writes old and new values to
  `zad_brain_runs.mutations`. An agent that changes numbers without an audit trail is not
  trustworthy.
- If a feature needs an API that does not exist, say so and delete the dependent UI. Do not
  fabricate data to fill a card.
- When this document conflicts with the code, stop and ask.
