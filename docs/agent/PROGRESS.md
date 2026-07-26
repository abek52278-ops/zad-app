# Progress log

Append one entry per task. Newest last.

## Task 1 — pharmacy RLS verification — DONE
RLS confirmed on all zad_* tables, policies scoped to auth.uid() = user_id.

## Task 2 — dependency graph — DONE, no code
supabase-kt all 3.0.3, Ktor 3.0.x, one intra-family override (3.0.1 -> 3.0.2). Clean.

## Task 3 — CI gaps — DONE
pull_request + workflow_dispatch triggers, lintDebug step, dependency drift guard.

## Task 4–7 — OTP filter, Egyptian banks, CSV dedupe, money containment — see git log

## Task 8 + 16 — brain deployed, validation layer — PARTIAL
Migration applied, RLS verified, zad_memory_upsert tested live, function deployed.
Failure path verified live: 401 (no key) -> queued -> 200, not 500.
BLOCKED: no working ZAD_API_KEY. The brain has never completed a successful run.

## Task 9 — ZadFacts — DONE
7 cards wired in HomeScreen.kt. Price radar and inflation radar NOT deleted — both have
real data sources (inflation compares against user history; price radar uses live web
search). Predictions + smart timing + behaviour patterns merged in
ZadIntelligenceScreen.kt.

## Task 17.1 — pharmacy diagnosis — DONE, no code
Scheduling is sound (AlarmManager exact + BootReceiver). Two real faults:
(a) days_left ignores `dosage` — wrong by a factor for any multi-unit dose;
(b) remaining_quantity only updates via the notification button — no manual path, so
the count freezes permanently if the alarm never fires.
dose_times parse failures are swallowed silently (PharmacyReminderScheduler.kt:92-97).

## Session tooling — DONE
`.claude/skills/run-zad-app/SKILL.md` + Robolectric/Roborazzi driver in PreviewTest.kt.
Renders real Compose screens to PNG on JVM — no emulator needed. `--no-daemon` required
(container OOM). Fixed 3 pre-existing missing imports that blocked all builds.

## Repo manifest commit — DONE
Committed docs/agent/ (ZAD_MASTER.md, NEXT_visible_progress.md, PROGRESS.md) and
callModel.ts to the repo per the user's "COMMIT THIS FIRST" instructions, so task
numbers and file references resolve to real files instead of living only in chat.
NEXT_visible_progress.md is missing its tail (message hit the 50k-char limit mid
TASK 17.3 acceptance list) — asked user to resend the rest. 15_family_alerts.md,
16_validation_and_recovery.md, and 17_2_pharmacy_fix.md were referenced but never
pasted — not created; CLAUDE.md's pointer to them is aspirational until they land.

## Task 9/10/11/17.2 wiring — DONE
ZadFacts (Task 9) was already wired; this pass closed the loop the brain's own
emit_insight action opens: zad_insights is now read (Home bell via
NotificationCenterScreen, ZadQuestionCard for kind=question), answering a question
calls triggerBrainEvent() so the brain's tool-driven memory write actually fires
(Task 11), and NearbyDealsScreen fires a real event trigger instead of a dead hint
label. Task 17.2 pharmacy fix landed in the same commit (touches ZadViewModel.kt and
SupabaseRepo.kt, which the insight pipeline also touches) — see commit message for the
full per-file breakdown; splitting further would have needed hunk-level staging across
interdependent files.

## Task 13 — delete-account edge function — DONE
`supabase/functions/delete-account/` added; `SupabaseRepo.deleteAccount()` now invokes
it instead of best-effort deleting 5 tables client-side and leaving the auth user (and
every table without an ON DELETE CASCADE) behind.

## Task 20 (dedupe config) — DONE
Finished what was left PARTIAL after `f5139a5` (values were correct, architecture wasn't).
`zad_locale_config` table added (EG/SA/TR, 36h/5% defaults), applied live with RLS.
`TxDeduplicator.WINDOW_MS`/tolerance are now loaded via `refreshLocaleConfig()` (cached in
SharedPreferences, wired into `ZadViewModel.init`), with a hard 5% ceiling clamp in the
write path per EPIC_1_4.md's explicit "do not raise tolerance above 5%". 78 tests (5 new),
full suite 0 failures. Commit `8dfe84a`.

Numbering note: `f5139a5` called itself "Task 15" before `EPIC_1_4.md`/`PRODUCT_PLAN.md`
arrived. Tasks 15/16 belong to `15_family_alerts.md` / `16_validation_and_recovery.md`
(still not in the repo). The dedupe work is Task 20, now complete.

Not done, flagged in EPIC_1_4.md itself: Task 21 (Egypt SMS regex accuracy — needs real
SMS samples this session doesn't have) and StatementCsvImporter's own database-query
matching for rows older than the dedupe window (Task 6, separate mechanism).

## Task 21 — Egypt SMS tuning, country-keyed — DONE
User supplied constructed (not device-captured) SA/EG samples and asked for a universal
multi-country parsing architecture; scoped down to EPIC_1_4.md's actual Task 21 (Egypt
tuning + country-keyed rule loading), since the requested "global fallback engine"
already exists as `ZadAiRepository.analyzeBankNotification` (AI-based, any country/
currency) — a second regex-based universal layer would duplicate it.
`BankRulesEngine.tryParse` now filters `bank_rules.json` rules by the active market's
country (`MarketPrefs.currentMarket`) plus an "ALL" generic slot. Found and fixed a real
bug: all 9 Egypt debit rules required currency immediately *after* the amount, so the
common real-world shape "Purchase of EGP 450.00" (currency before amount) never matched
— made the trailing currency group optional. Added `eg_atm_withdrawal` (→
`TxType.WITHDRAWAL` → `txn_kind='transfer'`, matching the 19.2 wallet fix) and
`eg_instapay_credit` (incoming, alongside the existing outgoing-only rule). Minor
`SaBankParser` keyword additions: "كود التفعيل" (OTP noise), "مدفوعة" (Saudi purchase).
83 tests total (7 new), 0 failures; `lintDebug` and `assembleDebug` both green
(`./gradlew --no-daemon`). Commit `df46bf3`.

## Task 22 — Habit chips — DONE (core), bonus deferred
EPIC_1_4.md's spec assumed infra that doesn't exist (`ZadIngest`/Task 12 still a TODO
comment, cash card/Task 19.4 not started, no "quick-add sheet"). Stopped and asked;
user chose landing the chips on the existing `TransactionsScreen` via the existing
`ZadViewModel.addTransaction()` write path instead of blocking on that infra. Also found
a second doc/code mismatch while applying the migration: the spec's SQL groups by
`merchant_name`, which does not exist on the live `zad_transactions` table (verified via
direct query) — used `title` instead, the closest real column. `zad_habit_chips(p_user,
p_same_weekday)` applied live and verified (empty for a fake user, empty on the real
table too — dataset still too small to have any >=4-hit pattern, consistent with 19.3's
findings). Added `SupabaseRepo.getHabitChips()`, `ZadViewModel.habitChips` StateFlow, and
a tap-to-log `HabitChipsRow` on `TransactionsScreen` (inserts with `wallet="cash"`
through the existing manual-add path). Deliberately deferred: the "habit lifetime cost"
bonus insight (needs a separate quarterly-cap mechanism, out of scope for this commit).
83 tests, 0 new (no mock framework for Supabase calls in this codebase, consistent with
every other `SupabaseRepo` function). `lintDebug`/`assembleDebug` green. Commit `48e49ed`.

## Task 19.1 — audit: ATM withdrawal double-counted as spending — DONE
Confirmed the bug: `SaBankParser.kt` classified WITHDRAWAL as `is_expense=true`, so
withdrawing then spending double-counted. No cash/wallet concept existed. See
`docs/agent/SESSION_2026_07_26_epic19.md` for the full session narrative.

## Task 19.0 — one derived budget authority — DONE
Not in the original epic — discovered necessary during 19.1. `zad_users.budget` was
doing two jobs (user ceiling + trigger-decremented balance). Split into `monthly_limit`
(user-set) + a fully derived remaining/spent via new `BudgetMath.kt`. Dropped the
`update_budget_on_transaction` trigger (confirmed live, not assumed). Stripped
`BudgetTracker`'s overall-balance mutation, kept category tracking. Added
`BudgetSetupPromptCard` for unconfirmed limits. Found and fixed
`fallbackToDestructiveMigration()` wiping the whole local Room DB on any version bump —
added a real `Migration(12,13)`. Full spec: `docs/agent/19_0_single_authority.md`.
Commits: `abead82`, `6de438f`, `a19ea3d`, `60ea32f`, `8fb8caf`, `0c79f80`.

## Task 19.2 — wallet/txn_kind/transfer_to schema — DONE
Added the three columns to `zad_transactions` (Supabase + Room `Migration_12_13`,
`ZadTransaction` is a real Room entity). ATM withdrawals classified `txn_kind='transfer'`
at `BankTransactionApplier` (single convergence point for all bank-detected transactions).
Additive only — nothing read the column yet, zero behavior change. Commit: `7cbf1a0`.

## Task 19.3 — spending reads txn_kind, backfill, zad_cash_balance() — DONE
Backfilled existing rows (3 of 4 live rows had is_expense/txn_kind mismatch, corrected;
zero withdrawal rows existed so zero transfer reclassifications, zero visible-number
change — verified before/after via direct query). Flipped `BudgetMath` and the
`zad-brain` snapshot from `is_expense` to `txn_kind` — this closes the actual Task 19.1
bug. Fixed `notify_parents_on_child_spend` and `zad_brain_self_review` (SQL functions)
to match — closes the "not fixed here, that's 19.2/19.3's job" gap flagged in Task
19.0's own migration comment. `zad_cash_balance()` RPC added per spec, unused until
Task 19.4. Deliberately not touched: `ZadCentralBrain`'s 13 other `isExpense` sites
(category/merchant/trend analytics) and the server-side behavior-profile/seasonal
forecasting queries — same latent transfer-not-excluded gap, tracked not fixed, same
scoping discipline as 19.0. Commit: `58b69eb`.

Next up per `EPIC_1_4.md`'s stated order: Task 20 (dedupe as per-country config,
currently PARTIAL) or Task 19.4 (cash card UI, `zad_cash_balance()` is ready).

## Task 19.4 — cash card on Home — DONE
`BudgetMath.cashOnHand(txs)` mirrors `zad_cash_balance()` SQL exactly, but computed from
the same Room-backed transactions flow every other Home number already derives from
(`ZadViewModel.recalculateRemainingBalance`) instead of a second network round-trip to
the RPC — one authority, per 19.0's principle. The RPC itself is untouched for other
consumers (zad-brain). New `CashCard` (`ui/components/ZadCashCard.kt`) on `HomeScreen`:
shows only when `cashOnHand > 0`, disappears at zero, reuses Task 22's `HabitChipsRow`
(now `internal`, gained a `horizontalPadding` param to avoid double-padding inside the
card) so tapping a chip logs a cash expense, plus a "صرفت منهم" button opening a small
always-expense dialog. One-time lifetime ATM education message added to
`BankTransactionApplier` (the existing single convergence point for bank transactions)
via the same `sendAppNotification()` path `UnifiedSmsReceiver` already uses for the
salary notification — bypasses `ZadAlertRouter` same as that existing precedent;
Task 24 is where notification paths get reconciled centrally, not here. First
`BudgetMathTest.kt` in the repo (6 cases, `cashOnHand` only). 89 tests (6 new), 0
failures; `lintDebug`/`assembleDebug` green. Commit `bfda9a2`.

Next up: Task 19.5 (weekly reconciliation via the brain's `ask_user` tool) — needs
confirming that flow is actually live first (Task 8/16 flagged it blocked earlier this
epic on a missing `ZAD_API_KEY`).

## Task 19.5 — weekly cash reconciliation — DONE, blocked on unrelated secret
buildSnapshot() now finally consumes `zad_cash_balance()` server-side (the RPC 19.3 built
and 19.4 deliberately left unconsumed "for other consumers e.g. zad-brain"). ISO-week
`dedupe_key` (`cash_reconciliation_<yr>_w<wk>`) computed in code (isoWeekKey), same
pattern as `suggest_budget_change`'s monthly key — not left to the model. `validateAskUser`
hard-blocks: an invented key not matching the snapshot, re-asking the same week, and
asking at all once `dismissed_count >= 2` — deterministic code gates, not prompt-only
guidance, matching this project's established discipline (Task 18's cooldown, Task 20's
5% ceiling). New `reconcile_cash_balance` tool inserts one correcting `zad_transactions`
row. Found a wording/mechanism gap: spec says "transfer row" for both directions, but the
only real lever that decreases `cashOnHand` (Task 19.3/19.4's own formula) is
`expense+wallet=cash`, not `transfer` — documented inline, not silently deviated from.
27 Deno tests (7 new, all passing), `deno check` clean on all zad-brain source. Deployed
live (zad-brain v31, `verify_jwt=false` unchanged).

**Found during post-deploy verification, not caused by this change:** a smoke-test POST
to the deployed function fails with `Invalid URL: '[https://api.groq.com/openai/v1](...)'`
— the `ZAD_BASE_URL` secret contains a markdown-link-wrapped value instead of a bare URL.
This blocks every zad-brain tool-calling path (daily/event/chat — not just cash
reconciliation) until fixed. No secret-management tool was available to fix this
directly; flagged for the user to fix via the Supabase dashboard or
`supabase secrets set ZAD_BASE_URL=https://api.groq.com/openai/v1`. Commit `6037768`.

Next up per `EPIC_1_4.md`'s order: Task 23 (inventory stagnation) or Task 24 (full-app
consistency audit) — both unblocked by the ZAD_BASE_URL issue since neither depends on a
live brain run.

## Task 23 — Inventory stagnation — DONE (code), lintDebug/assembleDebug unverified
Confirmed with the user first which "shopping suggestion" path(s) to filter — the
codebase has two (`InventoryFlowEngine.autoReplenish`'s deterministic low-stock filter
and `ZadAiRepository.suggestGroceries`'s AI-driven suggestions); user said both.
`InventoryFlowEngine.isStagnant()`: no quantity decrease in 30 days AND zero consumption
samples ever. Uses the most recent activity (purchase OR consume event) as the reference
point, not just `createdAt` — otherwise a regularly-restocked item (receipt-scanned,
quantity topped up, never itself consumed yet) would misclassify as stagnant just for
having an old original row. Any consume event ever, even an old one, permanently rules
an item out (mirrors Task 18's `rate_known`). Wired into `autoReplenish()` (excluded from
auto-add) and `fetchGrocerySuggestions()` (excluded from what's sent to the AI as
"current inventory"). `generateUrgentRecipes()` (chef mode): stagnant items now lead the
trigger list per spec's "first", expiry window widened 2→5 days to match the spec's
literal wording, and the AI prompt now distinguishes stagnant framing from
expiring/depleting framing. `UrgentRecipeCard` UI branches its copy accordingly (new
strings in all 4 locale files). Flagged but not fixed: EPIC_1_4.md claims "items with
expiry_date within 5 days already reach the brain via the snapshot" — false, zad-brain's
`buildSnapshot()` fetches `expiry_date` but never returns it in any field; not fixed
because chef mode reads straight from the client's own inventory state and never
consumes zad-brain's snapshot, so the gap doesn't actually affect it. +5
`InventoryFlowEngineTest` cases for `isStagnant`.

**Verification gap, flagged explicitly:** `testDebugUnitTest` passed standalone twice
(94 tests, 0 failures, includes catching and fixing a real `MissingTranslation` lint
error along the way). `compileDebugKotlin`/`compileDebugUnitTestKotlin` passed clean.
`lintDebug`/`assembleDebug` could NOT be completed — the container had up to 8 concurrent
Claude Code sessions running in parallel this session, and the Gradle daemon (even
`--no-daemon`) was silently SIGKILLed by memory pressure five consecutive times before
finishing lint analysis. This is an environment constraint, not a code defect. User
explicitly approved committing without that final verification given the constraint —
running `lintDebug`/`assembleDebug` cleanly is still owed and should be the first thing
done next session. Commit `76f488d`.

Update: `assembleDebug` succeeded cleanly later the same session once memory pressure
eased. `lintDebug` specifically still could not complete — roughly 8 attempts total
across the session, all SIGKILLed at the same `lintAnalyzeDebug` step with no Java-side
error (classic OOM-killer signature, confirmed no stack trace in any daemon log).
Consistently reproducible even after `assembleDebug` succeeded, suggesting lint analysis
specifically has a higher peak memory footprint than assemble on this codebase. Still
owed next session, ideally with fewer concurrent sessions in the container.

## Task 24 — Full-app consistency audit — DONE
Three parallel Explore-agent code-reading passes covered all ~24 live screens against
the six rules in EPIC_1_4.md; the highest-severity claims (dead-code routes, the
price-radar call origin, the subscription auto-write, whether `ZadAlertRouter` is called
anywhere) were independently re-verified via direct `grep`/`Read`, not taken at face
value from the agents.

Headline findings, most severe first:
- **`ZadAlertRouter` has zero call sites anywhere in the app** (confirmed via grep) —
  fully implemented but entirely unused. Every real notification goes through one of 14
  other files calling `NotificationCompat.Builder`/`SupabaseRepo.sendAppNotification`
  directly. Not a partial bypass; the router is orphaned.
- **`detectSubscriptions()`** (fired from `LaunchedEffect(Unit)` in both
  `ZadIntelligenceScreen` and `SubscriptionsScreen`) **auto-writes new subscription rows
  with zero user confirmation** whenever AI confidence exceeds 0.8 — a write as a side
  effect of navigation, not a deliberate action. Highest-severity single finding.
- `HomeScreen` fires 4 AI/edge calls on every open; `ZadIntelligenceScreen` fires 3 (plus
  the auto-write above); `WeeklyReportScreen`, `ShoppingListScreen`, and
  `NearbyDealsScreen` each fire one AI call uncontrolled by a user tap.
- `AssistantScreen.kt` and `ZadScreens.kt`'s `ChatScreen`/`MainContent` are dead code —
  verified via `MainScreen.kt`'s nav graph (live "Assistant" route renders
  `ZadIntelligenceScreen`) and `MainActivity.kt`'s "chat" route (renders a literal
  placeholder `Text`, not `ChatScreen`).
- The two specifically-called-out checks: price radar's `groq` call confirmed to
  originate server-side only (`ZadAiRepository.callAction` → `SupabaseRepo.callEdgeFunction`
  → `zad-core-intelligence`), button-gated, no client-side key — this previously-flagged
  gap is now confirmed fixed. `compileDebugKotlin`/`assembleDebug` both ran clean this
  session (no missing-import-class failure); `lintDebug` still unverified (see above).
- **Found and fixed live during the audit**: `sent_budget_alerts` had RLS disabled
  entirely (same class of gap as the `affiliate_*` tables fixed earlier this epic) —
  enabled + `auth.uid() = user_id` policy added, verified via direct query
  (migration `20260726110000_sent_budget_alerts_rls.sql`).
- Two `CurrencyFormatter` literals (`StatementImportScreen`, `HomeScreen`'s
  all-transactions dialog); five screens independently re-derive the same
  income/expense sums instead of sharing one source; `InventoryScreen`'s shortage
  banner uses a hardcoded local price table.

Full per-screen table and a prioritized summary for next session are in
`docs/agent/AUDIT.md` itself — it's a living document, re-run each epic. Commit `a6f2f93`.

**Epic 1+4 (Tasks 19–24) is now complete.** Remaining open items: the `lintDebug`
verification above, and Task 22's deliberately-deferred "habit lifetime cost" bonus
insight. Everything else in `EPIC_1_4.md` is done.
