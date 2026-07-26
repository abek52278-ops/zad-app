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
