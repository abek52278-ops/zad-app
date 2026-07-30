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

## Task 29.1 — Epic 2: Zad AI screen restructure (4-tab regroup) — DONE

Scope agreed with user, documented in `docs/agent/EPIC_2_ai_screen.md`. Split the old
3-tab `ZadIntelligenceScreen` (Analytics/Subscriptions/Chat, with Analytics alone
stacking ~20 ungrouped cards) into 4 tabs: نظرة عامة (Overview), السلوك والتوقعات
(Behavior & Predictions), الاشتراكات والفرص (Subscriptions & Deals — gained
`LiveDealsCard`/`FinancialChallengesCard`/`SinkingFundsCard`, moved in from the old
Analytics tab), أدوات ومحادثة (Tools & Chat — new `ToolsChatTab` with a segmented
`FilterChip` toggle between What-If/Insights and the existing Chat UI, avoiding nested
LazyColumn/LazyColumn scroll conflict). Pure layout regroup — no card internals, no data
source, no LLM-call logic changed. New string resources added across all 4 locale files.

**Build not verified this session**: `compileDebugKotlin` was OOM-killed by the container
on 4 consecutive attempts (with and without reduced `-Xmx`); `free -h` showed under 1GB
free throughout, multiple concurrent Claude Code sessions running. Same class of
environment constraint as the outstanding `lintDebug` issue above. Diff was manually
reviewed for signature/call-site consistency instead. **Do not mark this task done until
a clean `compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest` run happens next
session** — first thing to do.

Deliberately deferred (user's explicit call): `detectSubscriptions()` auto-write bug and
the rule-1 screen-local income/expense duplication, both still open per `AUDIT.md`.

**Build confirmed next session (2026-07-30).** This container had no Android SDK at all
(`ANDROID_HOME` unset, no `local.properties`, no `platform-tools`) — a fresh environment,
not the same OOM-under-load constraint as prior sessions. Installed cmdline-tools +
`platforms;android-36` + `build-tools;36.0.0` + `platform-tools` via `sdkmanager`, wrote
`local.properties` (gitignored). With that, all three gates ran clean in one pass, no
memory pressure this time: `compileDebugKotlin` BUILD SUCCESSFUL, `assembleDebug` BUILD
SUCCESSFUL, `testDebugUnitTest` 94 tests / 0 failures / 3 skipped. **Task 29.1 is now
actually DONE**, not just code-complete.

## `detectSubscriptions()` auto-write fix — DONE (2026-07-30)

`AUDIT.md`'s highest-severity finding: opening `ZadIntelligenceScreen` or
`SubscriptionsScreen` silently wrote new subscription rows for any AI detection above
`confidence > 0.8`, no user confirmation. `ZadViewModel.detectSubscriptions()` now
populates a new `pendingSubscriptions` StateFlow instead of calling `addSubscription()`
directly; a new shared `DetectedSubscriptionsSection` composable
(`ui/components/PendingSubscriptionsCard.kt`) renders each pending detection with
confirm/dismiss buttons in both screens that trigger detection. Confirm calls the
existing `addSubscription()` write path; dismiss clears it with no write and blocks that
name from re-prompting for the rest of the app session (in-memory only, not persisted —
kept deliberately minimal for this fix's scope). New strings in all 4 locale files.
`compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest` (94/94)/`lintDebug` all green —
first clean `lintDebug` run this epic, prior sessions couldn't complete it under memory
pressure. See `docs/agent/EPIC_2_ai_screen.md` for the full writeup.

## User follow-up batch (2026-07-30): rent/subscriptions, Amazon link, nav drawer, location alerts

User sent four items in one message. Three were quick, scoped fixes; the fourth
(background location alert service) needed investigation + a design decision, so it
was not built blind. Commits `0678d3b` (rent/subscriptions) and `50fc73b` (nav drawer).

**1. Never suggest cancelling rent/installments — DONE.** Two real bugs found:
`ZadCentralBrain`'s local "review this if unused" insight picked the single most
expensive *active* `zad_subscriptions` row with zero regard for what it actually was —
if a user's rent/installment happened to be the biggest recurring line item (likely),
it would get suggested for cancellation. Added `isFixedObligation()` (checks `type` plus
a category/title keyword match for إيجار/قسط/rent/installment/mortgage/loan, same
keyword-matching style already used for the existing streaming-duplicate detector in
this file) and excluded those rows from the pool that insight draws from. Also hardened
the three `zad-core-intelligence` prompts that touch this (`spending_insights`,
`agent_summary`, `detect_subscriptions`): explicit instruction never to suggest
cancelling/reducing fixed obligations, and not to classify rent/loan repayments as
"subscriptions" at all. **Not done, deliberately out of scope**: `AddSubscriptionDialog`
already gets a `type` back from `classifyBill()` but discards it, always saving
`type="subscription"` — the keyword check on category/title is a safety net that covers
this without needing that separate fix. Deno edge function prompt-string changes were
not deployed or `deno check`-ed this session (no `deno` binary in this container) —
deploy and smoke-test before relying on this in production.

**2. Amazon affiliate link — investigated, already correct, no change made.**
`AffiliateHelper.productUrl()` already builds `amazon.sa/dp/$ASIN$/?tag=...` directly
when a valid 10-char ASIN is present, falling back to a tagged search link only when it
isn't. All 3 real product-purchase call sites (`HomeScreen.kt` x2, `ShoppingListScreen.kt`)
pass the real `product.asin` through. The one `asin = null` call site
(`AmazonAffiliateWidget.kt`'s `AffiliateEmptyState`) is intentional — it only fires when
no catalog product matched at all, so there's no ASIN to use; a code comment there
already documents why. If users are still landing on search results instead of product
pages, the likely cause is the live `affiliate_products.asin` column having invalid/
placeholder values, not app code — this needs a direct DB check, which this session
couldn't do (Supabase MCP requires interactive auth, unavailable here).

**3. Nav drawer clarity — DONE.** Subscriptions screen (bills/installments tabs included)
was already in the side drawer, just labeled plain "اشتراكات". Added a drawer-specific
label override (`nav_subscriptions_installments` = "الاشتراكات والأقساط") in all 4
locale files, applied only in `MainScreen.kt`'s drawer item — left the shared
`R.string.subscriptions` (bottom bar pill, `ProfileScreen`, `HomeScreen` stat chip)
untouched since those are tighter-space/different contexts.

**4. Location alert service (proactive supermarket/mall push notifications) — NOT
STARTED, needs a design decision first.** Investigated `NearbyDealsScreen.kt`: today
it's a one-shot, user-triggered, foreground-only query (`ACCESS_FINE_LOCATION`,
`getLastKnownLocation()` on tap) — no background service, no geofencing, no push
notifications, no `WorkManager`. What the user described (always-on background
monitoring, detect proximity to a specific supermarket/mall, push a smart notification
with missing-items/price-comparison content) is a genuinely new feature, not a fix, and
touches: `ACCESS_BACKGROUND_LOCATION` (a Play Store sensitive-permission category with
its own disclosure requirements), a foreground service or geofencing API integration
(battery tradeoffs), and a notification-cooldown policy (AUDIT.md already flagged 14
uncoordinated notification call sites — a 15th needs to not repeat that). Asked the user
for design decisions before building (see chat) rather than picking silently.

**4b. Location alert service — simplified MVP DONE (2026-07-30, commit `35cff38`).**
User chose the simplified option: real geofencing for known stores (reusing
`OverpassRepo`, the same lookup `NearbyDealsScreen` already used for its manual search)
+ periodic `WorkManager` refresh, missing-items notification only — no store price/
"expensive" classification (no data source for that exists in this project). New:
`GroceryGeofenceManager` (opt-in flag off by default, registers up to 20 nearest-store
geofences via Play Services `GeofencingClient`, 200m radius, ENTER transition — the
`play-services-location` dependency was already sitting commented-out in
`libs.versions.toml`/`build.gradle.kts`, just uncommented it), `GeofenceBroadcastReceiver`
(notifies with the user's real unpurchased shopping-list items, max once per 24h per
store, **skips the notification entirely if there's nothing to buy** — deliberate, so
this doesn't become AUDIT.md's 15th uncoordinated notification source), `GeofenceRefreshWorker`
(12h periodic re-registration, self-gates on opt-in+permission so it's safe to
unconditionally schedule like every other worker). `NearbyDealsScreen` got a new toggle
with a chained permission flow (foreground `ACCESS_FINE_LOCATION` first, then
`ACCESS_BACKGROUND_LOCATION` on API 29+) — explicit opt-in only. New
`ACCESS_BACKGROUND_LOCATION` manifest permission — **Play Store requires its own
disclosure/justification for this permission category at submission time, not handled
by this commit**. `compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest`(94/94)/
`lintDebug` all clean; this is framework glue (BroadcastReceiver/GeofencingClient/
WorkManager) that Robolectric can't meaningfully exercise and this container has no
device/emulator to test on — verified by compilation + manual review only, same
untested-by-design status as this codebase's existing `BootReceiver`/
`ChatNotificationService`. **Not yet done**: real-device verification, and the
deliberately-deferred store price/expensive classification (would need either a new
price-data source or an AI-estimate call — separate task if wanted later).

## Location alerts follow-up: LocationIQ + pharmacy geofencing (2026-07-30, commit `3ef5849`)

User shared a LocationIQ API key in chat mid-session and asked for it wired into the
geofencing feature, plus pharmacy coverage and a real-time-query guarantee.

- **Key handling**: did not embed the key client-side. `AMAZON_ASSOCIATE_TAG` (the
  existing precedent for a `BuildConfig` secret) is a *public* affiliate tag meant to
  appear in URLs — a LocationIQ key is a real rate-limited credential that would get
  extracted from the APK and have its free-tier quota burned. New `nearby_pois` action
  on `zad-core-intelligence` (plain server-side proxy, no LLM, 6h cache on a ~110m
  lat/lon grid) reads `LOCATIONIQ_API_KEY` as a Supabase secret instead. **Not deployed
  this session** (no Supabase CLI/MCP auth available) — user still needs to run
  `supabase secrets set LOCATIONIQ_API_KEY=<key>` and redeploy
  `zad-core-intelligence`. Falls back to `OverpassRepo` silently until then, so nothing
  is broken in the meantime.
- New `LocationIqRepo.kt` (client), mirrors `OverpassRepo`'s exact function signatures.
  `GroceryGeofenceManager` tries LocationIQ first per category, Overpass fallback.
- **Pharmacy geofencing added**: `GeofenceCategory` enum (`SUPERMARKET`/`PHARMACY`),
  id-prefixed so `GeofenceBroadcastReceiver` knows which table to query on entry — up to
  15 geofences per category (30 total, well under Android's 100-per-app cap).
- **Real-time query confirmed/extended**: the receiver already read Room fresh at
  ENTER-event time (not a cached list from registration time) for the supermarket case;
  now the pharmacy case does the same — `daysOfSupplyLeft() <= 5` computed from a live
  DAO read at the moment of entry, same threshold `NearbyDealsScreen` already uses.
- `compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest`(98/98)/`lintDebug` all clean.
  TypeScript changes not `deno check`-ed or deployed (no `deno`/deploy access here).
- **User confirmed (2026-07-30)**: `LOCATIONIQ_API_KEY` set as a Supabase secret and
  `zad-core-intelligence` redeployed. Not independently verified from this session (no
  Supabase MCP auth here) — taken on the user's word, not a claim this session tested it.

## Epic 2 — CLOSED (2026-07-30)

Last open question: whether `HomeScreen`'s on-open AI calls (`AUDIT.md`'s highest-
frequency Rule 3 finding) should be fixed as part of Epic 2. User's explicit decision:
leave it — instant/live AI cards on open is the intended UX for the app's most-visited
screen, not a bug. No code change (this was already the screen's existing behavior);
recorded as a deliberate exception in `CLAUDE.md`'s standing rules so it doesn't get
"fixed" by a future session without checking first. See `docs/agent/EPIC_2_ai_screen.md`
for Epic 2's full closing summary.

## Task 25 — Salary cycle replaces calendar month (2026-07-30, commit `cdd62d7`)

`PRODUCT_PLAN.md` Phase A, first of tasks 25-28. Every budget calculation used calendar-
month boundaries; households here live on salary cycles (25th/28th/last working day), so
every warning fired at the wrong time for most users.

- Migration `20260730120000_salary_cycle.sql`: `zad_users.cycle_start_day` (nullable —
  null means calendar-month fallback, never defaulted to 1) + `cycle_anchor`
  (`day_of_month`/`last_working_day`). **Not applied to the live DB this session** (no
  Supabase CLI/MCP auth) — needs `supabase db push` or a dashboard apply.
- Client: new `CycleMath.kt` (pure date-boundary math, market-aware weekend handling for
  `last_working_day`) and `BudgetMath.spentInCycle/incomeInCycle/remainingInCycle/
  velocityInCycle/dailyAllowanceInCycle` alongside the existing calendar-month functions
  (kept, not replaced). `SupabaseRepo.getCycleSettings()` mirrors `getMonthlyLimit()`'s
  pattern.
- Server (`zad-brain`): `buildSnapshot` computes spent/remaining/velocity/threat/
  byCategory over the cycle window instead of the calendar month. Detection: income
  transactions clustered within ±3 days over the last 4 months → `cycle_detection` in
  the snapshot. New `confirm_cycle_start` tool + `ask_user` integration, same
  hard-validated dedupe_key pattern as Task 19.5's cash reconciliation.
- **Installed a `deno` binary this session specifically to verify these changes** —
  `deno check` and `deno test` both clean on the full `zad-brain` source (34/34 tests,
  7 new), not just reviewed by eye. 12 new client tests (`CycleMathTest` + `BudgetMath`
  cycle cases), 110 total, 0 failures.
  `compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest`/`lintDebug` all clean.

**Deliberately not touched, disclosed not silently skipped:**
- `zad_brain_self_review()`'s velocity-warning accuracy check still uses
  `date_trunc('month', ...)` — historical warnings predate cycle tracking, no coherent
  way to retroactively re-evaluate them against a cycle that didn't exist yet at the
  time. Needs its own follow-up once `cycle_start_day` has been live long enough.
- Server-side `last_working_day` is NOT market-aware (unlike the client) — treated
  identically to `day_of_month` for now, since the server doesn't know the user's market.
- No client UI wired to the cycle numbers yet (`HomeScreen` still shows calendar-month
  figures) — that's Task 26's "available" card display.
- The spec's "report old vs new daysLeft/velocity for three real users" impact check
  needs live DB access this session doesn't have.

**Blocking Task 26 and beyond until applied**: the migration above needs to be pushed to
the live DB and `zad-brain` redeployed before any of this actually takes effect — right
now it's all correct code sitting inert, the system still computes on calendar months.

## Task 26 — Committed obligations and the "available" number (2026-07-30)

`PRODUCT_PLAN.md` Phase A, second of tasks 25-28. Also does the client-side cycle-number
wiring Task 25 explicitly deferred ("No client UI wired to the cycle numbers yet — that's
Task 26's job").

- Migration `20260730130000_zad_obligations.sql`: `zad_obligations` table exactly per
  spec (title/amount/kind/due_day/due_date/recurrence/auto_detected/confirmed/active),
  RLS `auth.uid() = user_id` (same pattern as `zad_subscriptions`). **Not applied to the
  live DB this session** — user said they'll run the migration and redeploy `zad-brain`
  themselves this time.
- Server (`zad-brain`): `buildSnapshot` now fetches `zad_obligations`, computes
  `committed` (confirmed+active obligations due before `cycleEnd` + active subscriptions
  due before it) and `available = remaining - committed` — **not floored at zero**, per
  the spec's explicit warning that hiding a negative available is the most harmful thing
  this feature could do. Auto-detection (`detectObligationCandidate`): expense
  transactions clustered by (merchant, amount) across ≥3 distinct months in the last 4
  months → one candidate per run (same "one ask per run" budget as everything else),
  excluding anything already in `zad_obligations` or `zad_subscriptions` (no duplicate
  subscription detection, per spec). New `confirm_obligation` tool + validator: model
  only supplies `kind` (its one judgment call); title/amount/due_day come from the
  snapshot's `obligation_detection`, not retyped by the model. Row is written
  `confirmed=true` directly on confirmation — no separate silent pending-insert step
  (a deliberate simplification from the spec's literal two-phase sketch, documented at
  the `detectObligationCandidate`/`confirm_obligation` call sites: the dedupe-key +
  dismissed-keys mechanism already prevents re-asking, so no DB row is needed before
  confirmation to get that guarantee). `validateEmitInsight`'s critical-priority check
  now reads `available` instead of `remaining` (falls back to `remaining` only if
  `available` is absent, e.g. an old test snapshot) — matches the spec's "warnings cite
  available, not remaining" instruction, also updated in the system prompt text.
  `executeTool` picked up a `snap` parameter it didn't need before (only
  `confirm_obligation` reads the snapshot at execution time).
- Client: `ZadObligation` model added **without** a Room entity — mirrors the existing
  `ZadDebt` precedent (`SupabaseRepo.getObligations()`, no local cache), not the
  Room+DAO+migration path `ZadSubscription`/`ZadTransaction` use. Chose this deliberately
  after checking Room's migration-validation risk: a hand-written `CREATE TABLE` for a
  version bump can't be verified against Room's actual expected schema in this
  environment (no instrumented-test/device access), and `zad_debts` already establishes
  that not every Supabase table needs local caching. `BudgetMath` gained
  `nextDueDate`/`committedInCycle`/`availableInCycle`, mirroring zad-brain's calculation
  exactly (same quarterly/yearly simplification, documented at both call sites).
  `ZadViewModel.recalculateRemainingBalance` now computes `spent`/`remaining` inside the
  salary cycle (`CycleMath`, via new `loadCycleSettings()`) instead of the calendar month
  — this is the Task 25 UI-wiring debt closing — plus `committed`/`available`/
  `nextObligationDue`/`daysLeftInCycle`, recalculated whenever transactions, budget,
  subscriptions, or obligations change.
- UI: `ZadCardHero` (the Home screen's Visa-style budget card) now shows **available**
  as the primary figure (colored `dangerColor` when negative, never hidden) with a
  `متبقي X · محجوز Y (إيجار بعد ٤ أيام)`-style subtitle that only appears once
  `committed > 0` — a user with no obligations registered sees the exact same card as
  before. `BudgetScreen`'s header hero got the identical treatment. `daysLeft` on
  `HomeScreen`'s card switched from a raw `Calendar` calendar-month calculation to
  `viewModel.daysLeftInCycle`. 4 new string resources
  (`available_label`/`available_breakdown`/`available_breakdown_with_next`/
  `obligation_due_in_days`) added to all four locale files (`values`, `values-ar-rSA`,
  `values-ar-rEG`, `values-tr`).
- Verification: `deno check` + `deno test` clean on the full `zad-brain` source (42/42,
  8 new). `compileDebugUnitTestKotlin`/`testDebugUnitTest` clean (118/118, 8 new in
  `BudgetMathTest`). No Android SDK emulator in this container, so the new UI was not
  visually screenshotted — verified by type-check + unit test only, not a real render.

**Deliberately not built, disclosed not silently skipped:**
- No manual "add/edit/delete obligation" screen — out of scope for this task (spec is
  auto-detection + calculation + display); `SupabaseRepo.getObligations()` is read-only
  from the client today, writes only happen server-side via `confirm_obligation`.
- `next_obligation`'s "بعد ٤ أيام" phrasing only covers the single nearest confirmed
  obligation — no UI list of all committed obligations yet (would be natural for a future
  obligations-management screen, not requested here).
- Quarterly/yearly obligations use the same due-day monthly-stepping approximation as
  zad-brain (no `due_month` column in the schema) — auto-detection only ever produces
  `monthly` rows in practice, so this path is exercised by unit tests but not by any real
  detected data yet.
- **Blocking this task's server-side effect until applied**: the migration needs
  `supabase db push` (or dashboard apply) and `zad-brain` needs redeploying — the user
  said they'd handle both this session. Until then `available` == `remaining` for every
  real user (no `zad_obligations` rows exist), so the client change is inert but safe.
