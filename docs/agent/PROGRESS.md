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

## Task 20 (dedupe config) — PARTIAL, not done
Commit `f5139a5` landed the two corrected *values* directly in `TxDeduplicator`
(`SaBankParser.kt`): window `10min → 36h`, amount match `exact → 5% symmetric relative
tolerance` (`abs(a-b)/max(a,b) <= 0.05`). 12 tests pass, full suite 73/0 failures.

**This is half of Task 20 and the wrong half architecturally.** `EPIC_1_4.md` Task 20
requires both parameters to come from a `zad_locale_config` row keyed by the user's active
country, not from Kotlin constants — "hardcoding the corrected values repeats the mistake."
Remaining work: create `zad_locale_config`, load per-country config at runtime, drop the
constants. The values themselves are correct and match the spec, so this is not a
regression — just an unfinished task that must not be marked DONE.

Numbering note: that commit called itself "Task 15" before `EPIC_1_4.md`/`PRODUCT_PLAN.md`
arrived. Tasks 15/16 belong to `15_family_alerts.md` / `16_validation_and_recovery.md`
(still not in the repo). The dedupe work is Task 20.
