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

## Task 27 — Visible confidence (`≈`) + "why did this change" (2026-07-30, staged scope)

`PRODUCT_PLAN.md` **Phase C** (C2/C3 — mislabeled "Phase A" in this session's docs until
caught while writing Phase A closure notes; tasks 25-28 span two different phases, not
one — see the Task 28 entry below and `SESSION_2026_07_30_phaseA.md` for the correction).
Research pass first (Explore agent)
found the spec assumes infrastructure that doesn't exist yet: no cash-reconciliation
timestamp anywhere, client never read `zad_consumption`'s `rate_known`/`sample_count`,
bank-SMS raw text/timestamp computed by `SaBankParser` but discarded before reaching
`ZadTransaction`, and `ZadIngest` (cited as a "why" data source) is still an unbuilt
Task 12 plan. Flagged this to the user before building — **staged scope, confirmed by
user**: ship 27.1(a) `is_verified` + 27.1(b) pharmacy fix + 27.2 why-sheet using only
data that exists today; defer 27.1(c) stock-sample confidence and 27.1(d)
cash-reconciliation-age (both need new schema) to a follow-up. Also confirmed: keep
`TransactionsScreen`'s existing long-press-to-delete on transaction rows; the why-trail
long-press only applies to budget-card figures (no gesture collision there).

- **`Figure` type** (`app/src/main/java/com/example/data/Figure.kt`):
  `data class Figure(val value: Double, val confident: Boolean, val reason: String? = null)`,
  per spec. `ZadViewModel.availableFigure` replaces the old plain-`Double` `available`
  StateFlow entirely (single source of truth, not two overlapping ones).
- **27.1(a) `is_verified`**: found via research that this field was already dead for
  confidence purposes — `SubscriptionAutoDeductWorker` is the *only* place that ever set
  it `true`, so a literal "any unverified transaction in the sum → ≈" would show ≈ nearly
  always for nearly everyone, which is a real design conflict with the spec's own
  intent (an ambient indicator that's always on carries no information). Fix: gave the
  field real meaning at its three genuinely-manual `AddTransactionDialog` call sites
  (`HomeScreen.kt`, `TransactionsScreen.kt`, `BudgetScreen.kt` — a human typed the amount
  and hit save) by passing `isVerified = true` there specifically; left it `false`
  (unchanged) for `CameraScreen`'s OCR receipt-total insert, `ZadVoiceFab`'s
  voice-parsed entries, and both habit-chip tap paths (`CashCard`/`HabitChipsRow`) —
  none of those are a human confirming a specific number. New pure function
  `BudgetMath.unverifiedCountInCycle(transactions, cycleStart, cycleEnd): Int`, used by
  `ZadViewModel.recalculateRemainingBalance` to set `availableFigure.confident` and a
  one-line Arabic `reason`. 4 new unit tests.
- **27.1(b) pharmacy**: the null-`unitsPerDose` → "not a number, needs confirmation"
  behavior from Task 17.2.1 was already correctly built in `PharmacyItemCard` (list
  view) — but **`PharmacyItemGridCard` (grid view) was missing it entirely**, silently
  falling through to `remaining_quantity_label` always. Added the same tappable
  "الكمية محتاجة تأكيد" chip + `ConfirmQuantityDialog` wiring to the grid card
  (`PharmacyScreen.kt`), now consistent with the list view.
- **27.2 why-sheet**: first-ever client read of `zad_brain_runs` (previously
  write-only from Kotlin's perspective). `SupabaseRepo.getRecentBrainMutations()`
  fetches the last 20 runs' `mutations` jsonb arrays and flattens them into
  `BrainMutationEntry` (tool label in Arabic, old→new, relative time). New
  `WhyChangedSheet` composable (`ui/components/WhyChangedSheet.kt`) — `ModalBottomSheet`
  matching `FamilyScreen`'s `MemberDetailSheet` convention (the only prior bottom-sheet
  usage in the app). Opens on long-press of the "available" figure in `ZadCardHero`
  (Home) and `BudgetScreen`'s header; a plain tap on a non-confident figure instead
  opens a small explanation dialog with `Figure.reason` (matches spec: "tapping explains
  ... tapping" is the uncertainty explanation, long-press is the change history — two
  different gestures, `combinedClickable`).
- Verification: `compileDebugUnitTestKotlin`/`testDebugUnitTest` clean, 122/122 (4 new).
  No Android SDK emulator in this container — new UI not visually screenshotted, verified
  by compile + unit test only, same caveat as Task 26.

**Deliberately deferred, disclosed not silently skipped (staged-scope decision above):**
- 27.1(c) stock days-left with <3 samples — needs a new client read path for
  `zad_consumption.rate_known`/`sample_count` (currently server-only).
- 27.1(d) cash balance not reconciled in >14 days — needs a new `reconciled_at` column;
  no such timestamp exists anywhere today, client or server.
- The why-sheet shows **all** recent mutations, not filtered to the specific figure that
  was long-pressed — the `mutations` jsonb doesn't attribute a change to a specific UI
  number, so this is a real (small) precision gap, not an oversight.
- The spec's literal "رسالة من CIB الساعة ٣:١٢" example isn't reconstructable — would
  need `bankName`/raw SMS text persisted onto `ZadTransaction` (computed by
  `SaBankParser`, currently discarded before the insert) — a privacy-relevant decision
  (storing raw bank SMS text) intentionally left to a separate discussion, not decided
  unilaterally here.
- Habit-chip tap paths (`CashCard.onChipTap`/`onSpentFromCash`, `HabitChipsRow`) were
  left `isVerified=false` — a judgment call (system-suggested + one tap ≠ typed
  confirmation), not obviously wrong either way; flagging in case a future session
  wants to reconsider.

## Task 28 — Informative dismissal (2026-07-30)

`PRODUCT_PLAN.md` **Phase C** (C1 — 28 is C1, ahead of C2/C3 in the doc's own phase
table §3, done last here per the user's explicit 25→26→27→28 numeric request; see
Task 26's and Task 27's entries above — **correction**: tasks 25-28 were mislabeled
"Phase A" throughout this session's docs until caught while writing Phase A's actual
closure notes. Real mapping: Task 25 = A2, Task 26 = A3 (Phase A), Task 27 = C2+C3,
Task 28 = C1 (Phase C). Phase A itself is **not** closed by these four tasks — A1/A4/A5
were already done in prior sessions, A2/A3 landed today, but **A6 (drop `RECEIVE_SMS`)
is still open** and unaffected by anything in tasks 25-28. See
`SESSION_2026_07_30_phaseA.md` for the full phase-status writeup.) Replaces the single
silent dismiss action with three reasoned ones.

- Migration `20260730140000_informative_dismissal.sql`: `zad_insights.dismiss_reason`
  (`not_relevant` | `wrong_data` | `timing`), nullable, CHECK-constrained.
- Server (`zad-brain` `buildSnapshot`): `dismissedRes` now also selects
  `dismiss_reason`. `dismissed_keys` **excludes** rows with `reason='timing'** — this is
  the mechanism behind "suppress this instance only, may recur later": the row stays
  `status='dismissed'` in the DB (hidden from the client's pending list) but isn't a
  permanent block, so a later `emit_insight` upsert with the same `dedupe_key` flips it
  back to `pending` and the client sees it fresh again. `not_relevant`/`wrong_data` stay
  in `dismissed_keys` forever, same as today's behavior. New `dismissal_reasons` array
  (`{dedupe_key, reason}[]`) added to the snapshot alongside `dismissed_keys`, plus a
  short system-prompt note telling the model `wrong_data` means don't assume the same
  figure is right next time without new evidence, `not_relevant` doesn't mean the
  category itself is untrustworthy.
- Client: `ZadInsight.dismissReason` field. New pure function
  `DismissalMemory.noteFor(reason, insight): Note?` (scope/note/confidence per reason —
  `wrong_data` gets its own `data_quality` scope and the highest confidence of the
  three, deliberately distinct from `not_relevant`/`timing` so the signal survives
  rather than blending into generic dismissal noise per the spec's explicit "this
  signal is being thrown away" complaint). `SupabaseRepo.dismissInsightWithReason()`
  writes `status='dismissed'` + `dismiss_reason`, then calls `zad_memory_upsert`
  directly (same RPC zad-brain's own `remember()` tool uses) — a deterministic
  client-driven write, not an LLM decision, so it bypasses the brain entirely by
  design. `ZadViewModel.dismissInsightWithReason()` added alongside (not replacing)
  the existing `dismissInsight(id)`, which still backs `ZadQuestionCard`'s dismiss —
  deliberately out of scope, see below.
- UI: new `DismissReasonMenu` (`ui/components/DismissReasonMenu.kt`) — a 3-item
  `DropdownMenu` (matching this app's existing dropdown convention, e.g.
  `InventoryScreen.kt`'s unit/category pickers), "الرقم غلط" rendered in `dangerColor`
  so the most valuable option doesn't visually blend in. Wired at both existing
  insight-dismiss call sites: `HomeScreen`'s home-card insight row (was a bare `X`
  `IconButton`) and `NotificationCenterScreen`'s brain-alert `NotificationCard` (was
  `onClick` = instant dismiss with zero feedback).
- Verification: `deno check`/`deno test` clean (42/42, unchanged — no validator logic
  touched). `compileDebugUnitTestKotlin`/`testDebugUnitTest` clean, 127/127 (5 new in
  `DismissalMemoryTest`).

**Deliberately out of scope, disclosed not silently skipped:**
- `ZadQuestionCard`'s dismiss (declining to answer a brain-asked question) was left on
  the old plain `dismissInsight(id)` path — "مش مهم/الرقم غلط/عرفت خلاص" fit an
  *informational* insight/alert being wrong or unwanted; declining to answer a direct
  question is a different action with different semantics, and forcing it into the same
  3-option frame would be a stretch, not a natural fit.
- "flag the underlying data for review" (the spec's `wrong_data` effect) is implemented
  as the `data_quality`-scoped memory note itself — the brain reads `memory` every run
  already, so this note *is* the flag. No separate review queue/table was built; the
  spec's own wording ("each writes a zad_memory note with the reason") supports this
  being the whole mechanism, not an addition to it.
- No UI surfaces `dismissal_reasons` or `data_quality`-scope memory notes back to the
  user anywhere (e.g. no "you told us your budget was wrong 3 times" screen) — the spec
  only requires the brain to see it, not the user.

## Phase B4 — Telegram bot infrastructure (2026-07-30)

`PRODUCT_PLAN.md` Phase B (B4 — "after Phase C", now unblocked since Phase C closed
today). Only real spec fragment was in `EPIC_1_4.md`: function name `zad-telegram-bot`,
one-time binding code, inline buttons, and the explicit warning **"a chat_id is never an
identity."** No functional spec existed for what the bot lets a user *do* — confirmed via
repo-wide grep before starting, PRODUCT_PLAN.md's B4 row is one line
("text and buttons only, no voice"). Scope agreed with the user before writing code:
**read-only v1** (balance, recent transactions, pending insights + Task 28 dismissal),
infra built now, bot token supplied by the user afterward. Mid-build, the user corrected
two choices — using **grammY** (not a hand-rolled fetch wrapper) and naming the table
**`telegram_bindings`** (not `zad_`-prefixed) — both applied; the table-naming deviation
from this schema's otherwise-universal `zad_` prefix is flagged in the migration's own
comment, not silently followed.

- Migration `20260730150000_telegram_bindings.sql`: `telegram_bindings` (`user_id`,
  `chat_id` nullable, `binding_code`, `code_expires_at`, `bound_at` nullable). Partial
  unique indexes enforce **one bound chat per user and one bound user per chat** — a
  user can generate multiple codes (old ones just go stale) but can't bind two Telegram
  accounts, and a chat can't be bound to two zad accounts. RLS restricts a user to their
  own rows; the edge function (service role) bypasses it by design, same as `zad-brain`
  — it has to look up a row by `binding_code`/`chat_id` before it knows a `user_id` at
  all.
- Edge function `zad-telegram-bot`, built on **grammY** (`npm:grammy@1` — resolves fine
  in this sandboxed container, confirmed live before committing to the rewrite).
  `telegram.ts` holds every pure function (keyboard layouts, message formatting,
  binding-code/callback-data parsing, the dismissal→`zad_memory` note mapping) with zero
  grammY/Supabase imports, so it's fully unit-testable without a live bot or database —
  `index.ts` is a thin `Bot` + `webhookCallback(bot, "std/http", { secretToken })` shell
  that wires those functions to Supabase queries. Flow: `/start <code>` binds
  `chat_id`↔`user_id` (rejects expired/wrong codes, rejects if the chat or user is
  already bound elsewhere); the 3-button main menu reads balance/transactions/insights
  scoped to that `user_id`; each pending insight gets its own message with the Task 28
  three-reason dismiss keyboard, wired to the exact same `zad_insights` update +
  `zad_memory_upsert` RPC call the Kotlin client uses (`memoryNoteForDismissal` in
  `telegram.ts` is a deliberate small duplicate of `DismissalMemory.kt`, documented at
  both — third instance of this app's established "duplicate math across runtimes,
  document it" pattern alongside `BudgetMath.kt`/`buildSnapshot`).
- Client: `SupabaseRepo.generateTelegramBindingCode()` (8-char code, charset excludes
  `0/O/1/I` to avoid manual-entry ambiguity, 10-minute expiry) / `isTelegramLinked()` /
  `unlinkTelegram()`. New `TelegramLinkDialog` in `ProfileScreen.kt` — generates and
  displays a tap-to-copy code with instructions, or shows "already linked" + an unlink
  action if a bound row already exists. New `ProfileMenuItem` entry ("ربط تليجرام")
  above the existing help-support row.
- Verification: `deno check`/`deno test` clean (17/17, all pure-function — no live
  Telegram/Supabase calls were possible or attempted, no token available in this
  environment beyond what's now a Supabase secret). `compileDebugUnitTestKotlin`/
  `testDebugUnitTest` clean, 127/127 (unchanged — the new client functions are
  network-bound Supabase calls, consistent with this codebase's existing convention of
  not unit-testing `SupabaseRepo` functions directly).

**What's genuinely still needed before this works end-to-end (none of it possible from
this session — no Supabase CLI/MCP auth, no way to reach the live bot):**
1. ~~Apply `20260730150000_telegram_bindings.sql`~~ — **DONE 2026-07-31** via MCP
   `apply_migration`; `telegram_bindings` now exists live with RLS enabled, and the
   security advisor reports no new findings for it.
2. ~~Deploy the `zad-telegram-bot` function~~ — **DONE 2026-07-31** via MCP
   `deploy_edge_function`; live as version 1, status ACTIVE, `verify_jwt: false`
   (required — Telegram's webhook POST carries no Supabase JWT; the real authorization
   is the webhook secret below plus the chat_id→user_id binding).
3. **ROOT CAUSE, confirmed live 2026-07-31: `TELEGRAM_BOT_TOKEN` is NOT set on this
   project.** The claim below (and in the 2026-07-30 notes) that it had been set via
   `supabase secrets set` is **false** — it was never verified, and the deployed
   function's own config probe now reports `bot_token: false`. This is why the bot never
   worked, and it made every downstream step moot: the function was crashing at module
   load on `new Bot("")` and returning an opaque WORKER_ERROR/500 to everything,
   including its own health endpoint.

   **The fix is one action the user must take** (MCP has no secret-setting tool):
   either the Supabase dashboard → Project Settings → Edge Functions → Secrets, or
   ```
   supabase secrets set TELEGRAM_BOT_TOKEN=<token> --project-ref auuftqncrjsnyylolhbu
   ```
   Once set, no further deploy is needed for the webhook: the function self-registers on
   its next cold start, and `GET /functions/v1/zad-telegram-bot` returns the live config
   and webhook state. `TELEGRAM_WEBHOOK_SECRET` is optional — if unset, the function
   derives one from the bot token so signature verification is enforced either way.

3b. Historical note — the manual setWebhook path, no longer required:
   ```
   curl "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook" \
     -d "url=https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot" \
     -d "secret_token=<a-random-string-you-also-set-as-TELEGRAM_WEBHOOK_SECRET>"
   ```
   Superseded as of 2026-07-31: the function calls `setWebhook` itself on cold start
   (`ensureWebhook`, idempotent via `getWebhookInfo`), so this curl is no longer needed.
   The earlier "accepts any webhook call with no secret verification" exposure is also
   closed — `WEBHOOK_SECRET` no longer falls through to `undefined`; when
   `TELEGRAM_WEBHOOK_SECRET` is unset it is derived from the bot token, so grammY's
   `secretToken` check is always enforced. Setting `TELEGRAM_WEBHOOK_SECRET` explicitly
   still takes precedence if you prefer to manage it yourself.
4. ~~Confirm the bot's actual Telegram username~~ — **DONE 2026-07-31, and it was
   wrong.** `getMe` reports the username is **`@ZadhApp_bot`**; `ZadSmartBot` is only
   the bot's *display name*. `ProfileScreen.kt` was telling users to open
   `@ZadSmartBot`, which does not resolve in Telegram search — so even a fully working
   bot would have looked broken to anyone following the app's own instructions. Fixed.

### 2026-07-31 (later) — bot is live end to end

`TELEGRAM_BOT_TOKEN` was set by the user; the function self-registered its webhook on
the next cold start, exactly as designed. Verified against the live deployment:
- config probe: `bot_token: true`, `webhook_secret: true` (source `derived`).
- `setWebhook` returned `Webhook was set`, with `previous: ""` — confirming it had
  genuinely never been registered before, which was the standing diagnosis.
- `getWebhookInfo`: correct URL, `pending_update_count: 0`, no `last_error_message`.
- Signature enforcement actually tested, not assumed: a forged POST with no
  `X-Telegram-Bot-Api-Secret-Token`, and one with a wrong token, both return **401**.
  The pre-existing "accepts any webhook call" exposure is closed.

Still unproven: reply quality. No real customer message has round-tripped through
`askZad` yet, and the expense-logging confirm flow has not been exercised end to end.

### 2026-07-31 — Telegram bot v2: conversational agent + deployed

- `context.ts` (new, pure/testable like `telegram.ts`): `buildAgentContext()` assembles
  the customer's full picture server-side using the same `=== SECTION ===` contract as
  the Kotlin client's `ZadViewModel.buildFullChatContext()` — profile/budget, month
  totals, per-category spend, last 30 transactions, obligations, inventory,
  subscriptions, pharmacy, shopping list, pending insights, tasbiha garden, `zad_memory`
  notes. `agentSystemPrompt()` holds the rules and never opens a `=== ===` section
  itself, so injected text (a transaction title, an item name) can't reach rule-level
  authority.
- `index.ts`: free text now goes to Zad instead of bouncing back the button menu. New
  `/tahlil` (full situation analysis), `/menu` (old buttons). The model call routes
  through `zad-core-intelligence`'s `ai_text` action, so the bot inherits the app's
  Groq-pool-primary/Gemini-fallback policy rather than forking a second provider path.
  Replies clamped to Telegram's 4096-char cap.
- Bug caught pre-deploy: `fetchAgentContext` had `.or("created_at.gte.<monthStart>")`
  which would have silently excluded all older transactions while the comment claimed
  the opposite. Replaced with a straight 200-row newest-first window, which is what
  `monthTotals`/`categoryBreakdown` actually need to be correct.
- Verification: `deno test` 29/29 (13 new context tests), `deno check index.ts` clean.
  One new test caught a wrong assertion of mine about the system prompt and was
  corrected rather than deleted. **Not verified against live Telegram** — the webhook is
  still unset (step 3 above), so no real message has round-tripped yet.
- **Still read-only.** The agent is explicitly instructed (rule 5) not to claim it
  logged or changed anything, because it genuinely cannot from here. User has approved
  adding expense logging (with a confirm step) as the next phase — not yet built.

**Deliberately deferred, disclosed not silently skipped:**
- **Read-only v1 only** — no expense logging, no budget edits, nothing beyond dismissing
  an insight. Per the user's explicit scope decision.
- The "الرصيد المتبقي" (balance) button computes a plain calendar-month
  `budget - spent + income`, **not** the app's actual "متاح" (`BudgetMath.availableInCycle`
  / `buildSnapshot`'s `available` — salary cycle + committed obligations). Reusing that
  exact logic here would need extracting it into a module shared across the Kotlin app
  and two independent Deno functions, which is real refactor work, not a quick win —
  documented in `telegram.ts` itself, not silently approximated.
- No rate limiting / abuse protection on the webhook beyond (once set up) the secret
  token check — fine for a single-user-per-account bot with no write actions, would need
  revisiting before any write capability is added.

## Budget UI glassmorphism pass (2026-07-30)

User request: bring the budget Hero Card + `BudgetScreen` header in line with a "new iOS
design" (glassmorphism, 24dp corners, "متاح" shown prominently) — no design file/Figma
was available, so scope was the concrete attributes given plus reusing whatever glass
design-system pieces already existed in the codebase.

- **Found existing, partially-adopted infrastructure before writing anything new**:
  `PremiumSurfaces.kt` already had `GlassCard` (translucent + blurred panel), a
  `zadGlassBlur()` gate (real `Modifier.blur()` on API 31+, safe no-op below — matters
  since `minSdk 24`), `ZadCanvasBackground` (blurred pastel-blob screen background,
  already wired into `HomeScreen`), and `HeroGradientCard` — its own doc comment said it
  was meant to unify "the budget header and the Kids candy balance card" onto one
  primitive, but neither `ZadCardHero` (the live Home hero) nor `BudgetScreen`'s header
  had actually been migrated onto it. There was also a **dead, never-called
  `BudgetCardSection`** composable in `HomeScreen.kt` that had already prototyped the
  `HeroGradientCard` + nested `GlassCard` pattern, but predated Task 26/27 (no
  available/committed/confidence) — deleted rather than left as a second, stale "budget
  hero" alongside the real one, per "delete rather than fake."
- **`ZadCardHero`** (`PremiumHomeComponents.kt`): now built on `HeroGradientCard` (outer,
  24dp, unchanged gradient colors) with the secondary stats (days left / spent / deposit
  button + progress bar) moved into a nested `GlassCard` panel instead of sitting
  directly on the gradient. Dropped the fixed `aspectRatio(1.62f)` credit-card
  silhouette for content-sized height — the nested glass panel needs room the rigid
  ratio didn't leave. All Task 26/27 behavior preserved exactly: `available: Figure`
  with `≈`/tap-to-explain/long-press-why, the `متبقي X · محجوز Y` breakdown line.
- **`BudgetScreen` header**: same treatment — was a full-bleed, non-rounded, edge-to-edge
  gradient banner (no card shape at all); now a floating `HeroGradientCard` with 16dp
  horizontal margin and 24dp corners, and the manual translucent income/spent/budget row
  (`Color.White.copy(alpha=0.12f)` + hand-rolled border) replaced with the same
  `GlassCard` component.
- **Real bug found and fixed via actual screenshot verification, not by inspection**:
  first Roborazzi capture of the new `ZadCardHero` (added `captureZadCardHero_glassmorphism`
  to `PreviewTest.kt` — it's a stateless composable, no ViewModel needed) showed the
  nested `GlassCard`'s stats panel rendering **nearly blank** — progress bar and all
  text/icons gone. Root cause: `GlassCard`'s `zadGlassBlur()` sat on the *same* `Column`
  that laid out its `content` children, so `Modifier.blur()`'s RenderEffect blurred the
  card's own foreground (text, icons) along with its background — on API 31+ (this
  test's Robolectric config is `sdk=33`) that's a 20dp blur smearing the very content
  the card exists to show. This is a **pre-existing bug in `GlassCard` itself**, used
  as-is in `ZadIntelligenceScreen`/`HomeScreen`/`ShoppingListScreen`/`PharmacyScreen`
  already — never caught because none of those usages had been screenshot-verified
  before (all are behind a `ZadViewModel`, out of this session's Roborazzi driver's
  reach). Fixed at the source in `PremiumSurfaces.kt`: blur now lives on a separate
  background-only `Box` behind an unblurred content `Column` — the technically correct
  technique (blur belongs on what's behind glass, never on the glass's own foreground)
  and it benefits all five call sites, not just the two touched here. Re-screenshotted
  after the fix — confirmed the stats panel (progress bar, "الأيام: 12",
  "المصروف: 1,200 ر.س", "إيداع" button) now renders correctly through the glass.
- Verification: `compileDebugUnitTestKotlin`/`testDebugUnitTest` clean, 128/128 (1 new —
  the Roborazzi capture test; a pixel-diff regression baseline, not a pass/fail
  assertion on visual correctness beyond "it rendered without crashing," which is why
  actually viewing the PNG mattered here, not just a green test run).

**Deliberately not done, disclosed not silently skipped:**
- `BudgetScreen`'s header was **not** visually screenshotted — it needs a real/fake
  `ZadViewModel`, out of this session's Roborazzi driver's stated scope. Confidence is
  reasonably high since it reuses the exact same now-fixed `HeroGradientCard`/`GlassCard`
  primitives just proven correct on `ZadCardHero`, but this is inference, not direct
  visual proof — flagging the difference honestly.
- No literal iOS reference (screenshot/Figma) was available or requested from the user
  beyond the three named attributes (glassmorphism, 24dp, "available" shown) — this pass
  interprets those attributes using this app's own existing (now-fixed) glass design
  system, not a pixel-matched port of an actual iOS screen.
- Other budget-adjacent cards (category cards, transaction rows, the stats sub-row
  corner radii ranging 14–20dp across `BudgetScreen.kt`) were **not** touched — scope
  was explicitly "Hero Card" + the header, not a full corner-radius consistency sweep
  across every card in the budget screens.
- The other four existing `GlassCard` call sites (`ZadIntelligenceScreen`, `HomeScreen`,
  `ShoppingListScreen`, `PharmacyScreen`) automatically get the blur fix for free since
  it's fixed at the shared component, but none of them were individually re-verified
  visually this session — only `ZadCardHero` was actually screenshotted before and after.

## A6 — `RECEIVE_SMS` مشالت بالكامل (2026-08-01)

Phase A بقت مقفولة. الصلاحية نفسها كانت اتشالت من `AndroidManifest.xml` في
`757f41c` مع تحويل `UnifiedSmsReceiver` لـ `UnifiedBankListener`، بس فضل كود حي
بينده صلاحية مش موجودة:

- `BankReadingStatus.isSmsPermissionGranted()` — `checkSelfPermission` على صلاحية
  غير معلَنة، يعني `DENIED` دايماً.
- صف "قراءة الرسايل" في `BankReadingStatusScreen` — كان بيفضل OFF على طول وزراره
  بيطلب `RECEIVE_SMS`؛ طلب صلاحية غير معلَنة النظام بيرفضه فوراً من غير dialog،
  فالزرار كان مسدود وبيوحي إن قراءة البنك مكسورة.

اتشال الاتنين، ومعاهم `sms_reading_status_label` من الأربع لغات. عنوان الشاشة بقى
"قراءة إشعارات البنك" — الصفوف الفاضلة إشعارات + بطارية بس.

**التحقق**: `:app:processDebugMainManifest` — الـ merged manifest (بعد دمج
مانيفستات كل المكتبات، وده المستوى اللي Play بيفحصه) فيه 14 صلاحية، ولا واحدة
منهم SMS. `compileDebugKotlin` نضيف و129 unit test بيعدوا.

**ملاحظة مش من نطاق A6**: الـ merged manifest فيه كمان
`ACCESS_BACKGROUND_LOCATION` و`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — الاتنين
حساسين عند Play (الأولى محتاجة declaration form، والتانية مقيّدة لفئات محددة).
اتسابوا زي ما هما، بس محتاجين قرار قبل النشر.

## minSdk 24 / API-level safety (2026-08-01)

**الباج المسجّل في CLAUDE.md كان متصلّح خلاص، والملاحظة هي اللي كانت قديمة.**
`app/build.gradle.kts` فيه `isCoreLibraryDesugaringEnabled = true` +
`desugar_jdk_libs:2.1.4`. اتأكدت من الـ APK نفسه بـ `dexdump` مش من ملف البناء:
`classes12.dex` بيعرّف **225 كلاس `Lj$/time/*`**، وصفر كلاس `java/time/*` معرّف في
أي dex (ده مهم — ART بترفض كلاسات في باكدج `java.`)، وكود التطبيق في `classes6.dex`
بيشاور على `Lj$/time/LocalDate;` **696 مرة** من غير ولا نداء `Ljava/time/` متساب من
غير إعادة كتابة. يعني `java.time` آمنة فعلياً على API 24-25.

**بس الـ desugaring بيغطي `java.*` بس — مش `android.*`.** ولقينا اتنين:

1. **`TasbihaScreen:730` — كراش حقيقي على API 24-25.**
   `VibrationEffect.createOneShot` (API 26) من غير حارس `SDK_INT`. كان ملفوف في
   `try/catch (Exception)` وده مكانش بيحمي: كلاس ناقص بيرمي `NoClassDefFoundError`
   وده `Error` مش `Exception`. يعني كل ضغطة تسبيحة على جهاز 24-25 = كراش.
   اتحط نفس الحارس اللي `CameraScreen` مستعمله + fallback لـ `vibrate(long)`.
2. **`android.permission.VIBRATE` مكانتش معلَنة خالص.** كل نداء `vibrate()` في
   التطبيق كان بيرمي `SecurityException` ويتبلع في الـ try/catch — الاهتزاز مكانش
   شغال في أي مكان (تسبيح + كاميرا). اتعلنت في المانيفست؛ صلاحية عادية بتتمنح وقت
   التثبيت، مفيش نافذة طلب ولا خطر Play.

الاتنين كانوا **متخبّيين في `lint-baseline.xml`** (2 × `NewApi` + 5 ×
`MissingPermission`). السبع مدخلات اتشالوا من الـ baseline بدل ما يتساب الغطا عليهم،
و`lintDebug` بقى نضيف (0 errors غير مفلترة). قاعدة للجلسات الجاية: `NewApi` و
`MissingPermission` ما يتحطوش في baseline — دول كراشات أو ميزات ميتة بصمت.

**التحقق**: `lintDebug` نضيف، `compileDebugKotlin` نضيف، 129 unit test بيعدوا،
والـ merged manifest فيه 15 صلاحية (VIBRATE موجودة، SMS صفر).

## دفعة تصليحات UX/infrastructure — 7 تاسكات (2026-08-06) — DONE

مش جزء من ترقيم Task 1-28 الموجود — دفعة بق تقارير مستخدم مباشرة (زر تحويل العملة،
الأسعار الحية، صورة البروفايل، تنبيهات الدوا، زر المايك، الاشتراكات، شات العائلة).
خطة كاملة اتكتبت في plan mode واتوافق عليها الأول (`/home/codespace/.claude/plans/
splendid-cooking-marble.md` — ملف خارج الريبو، مرجع بس). كل تاسك commit مستقل، وكل
واحد فيهم اتبنى واتفحص فعلياً (`compileDebugUnitTestKotlin` + `testDebugUnitTest`،
163 test) بعد التعديل مباشرة — مفيش تقرير نجاح من غير تشغيل.

1. **`b45981e` — زر تحويل العملة/الدولة كان no-op.** `MarketPrefs.currentMarket` كان
   `@Volatile var` عادي مش Compose state، فـ `CurrencyFormatter` مكانش بيتحدث غير
   بالصدفة مع أي recomposition تانية. بقى `by mutableStateOf(...)` — كل الـ ١٧٩ نداء
   لـ `CurrencyFormatter.format/formatNumber/symbol/currencyCode` بقوا reactive
   تلقائياً من غير ما يتغيروا هما. البانر نفسه كمان كان بيسيب `dismissedTravelCountry`
   زي ما هو فيفضل ظاهر حتى بعد نجاح التحويل — بقى بيمسح نفسه فوراً. وضيف
   `Context.findActivity().recreate()` بعد التبديل عشان اللغة (locale) تتحدث كمان على
   أجهزة أقدم من API 33 (`MainActivity` مش `AppCompatActivity`، فمفيش auto-recreate).

2. **`ac3ca99` — الأسعار الحية بتتقفل بشكل غلط (نجاح فاضي بدل خطأ).**
   `ZadAiRepository.callAction()` كان بيبلع أي exception ويرجّع `emptyMap()`، فـ
   `fetchLiveMarketPrices()` بيفحص `response["ok"] == false` بس ده `null` مش `false`
   على فشل شبكة، فالفحص بيعدي بصمت والـ ViewModel بيسجل "نجح، صفر نتايج" بدل Error.
   `callAction` بقى عنده `swallowErrors` (افتراضي `true`، باقي الـ ٢٠+ نداء زي ما هما)
   ونداء الأسعار الحية بس بيبعت `false`. مهلة القراءة اتزودت لـ ١١٠ ثانية (سيرفر أسوأ
   حالة ~١٠٠ث) مع سقف صلب `withTimeout(120s)`. كاش محلي (SharedPreferences لكل سوق)
   بيعرض آخر أسعار نجحت فوراً بدل سبينر فاضي.

3. **`c9a9e88` — أبلود الصورة كان بيمسح الاسم صامتاً + الهيدر الرئيسي مالوش مكان أفاتار.**
   `uploadAvatar()` كان بيعمل upsert باسم `_userName.value ?: ""` — لو الاسم لسه ما
   اتحملش، بيتمسح. `SupabaseRepo.updateUserProfile`'s `name` بقى nullable وبيسيب
   الاسم الحالي لو `null`. `ZadTopHeader` (هيدر الهوم الفعلي) مكانش عنده `avatarUri`
   parameter خالص — بس دايرة الـ drawer كانت شغالة. ضفت دايرة أفاتار في الهيدر متوصلة
   بنفس `StateFlow` المشترك، فالمكانين يتحدثوا سوا من غير refresh يدوي.

4. **`2685ed6` — تنبيهات الدوا: الجدولة سليمة، المشكلة كانت اكتشاف إذن مفقود.**
   `AlarmManager.setExactAndAllowWhileIdle` + قناة `IMPORTANCE_HIGH` + إعادة جدولة بعد
   الريستارت كانوا موجودين وصح. على Android 14+، `SCHEDULE_EXACT_ALARM` مرفوض
   افتراضياً لحد ما المستخدم يوافق يدوي، والتنبيه الوحيد كان بانر مدفون جوه
   `PharmacyScreen`. ضفت `ExactAlarmPermissionCard` على الرئيسية (نفس نمط
   `LocationAlertsCard`). Badge: أندرويد مفيهوش API عام موحد لكل اللانشرات —
   `NotificationChannel.setShowBadge(true)` (كان `true` افتراضي، بقى صريح) هو المدعوم
   رسمياً؛ مكتبة OEM خارجية (ShortcutBadger) اتأجلت عمداً لحد ما تتلاحظ مشكلة فعلية.

5. **`e1ef9af` — شيل زرار تسجيل الصوت الداخلي (`ZadVoiceFab`) خالص.**
   حذف الملف + `AudioRecorderHelper.kt` (المستخدم الوحيد ليه) + `RECORD_AUDIO` من
   المانيفست (اتأكد من الـ merged manifest الفعلي، مش المصدر بس) + كل الكود الميت
   المرتبط (`processVoiceCommand` client wrappers، `VoiceAgentResponse`/`VoiceAgentData`،
   ٤ اختبارات Roborazzi كانت بتصوره). `TelegramBotCard`/`TelegramBotSheet` الموجودين
   أصلاً بقوا نقطة الدخول الوحيدة لبوت تليجرام (already had the exact `ACTION_VIEW`
   deep-link pattern المطلوب — مفيش كود جديد احتاج).

6. **`af0417b` — فصل تاب "الاشتراكات والفرص" من عقل زاد لشاشة `SubscriptionsScreen`
   مستقلة.** الـ Navigation/Drawer/NavHost كانوا أصلاً متوصلين على `SubscriptionsScreen`
   (بس نسخة أنحف) — نقلت المحتوى الغني (مخطط سداد الديون، عروض حية، تحديات عائلة،
   صناديق تجميع) ليها، وسيبت `Overview`/`Behavior&Predictions`/`Tools&Chat` جوه عقل
   زاد زي ما هي (القرار: الاقتصار على نقل الاشتراكات بس، مش "شات فقط" بالكامل —
   الطلب الأوسع ده اتأجل عمداً). `SubscriptionsScreen` بقت محتاجة `familyViewModel`
   param جديد. `detectSubscriptions()` كان بيتنادى مرتين (من الشاشتين) — بقى مرة واحدة.

7. **`9d24e3d` — بق `senderId`/`userId` في شات العائلة + ترتيب فواصل التاريخ + كارت SOS
   في عقل زاد.** `HomeScreen`'s mini widget كان بيقارن `msg.senderId` (بيخزن
   `family_members.id`) مع `member.userId` (auth user id) — مقارنة غلط دايماً فاشلة.
   فواصل التاريخ في `FamilyScreen.ChatTab` كانت بتترتب تحت مجموعتها بدل فوقها بسبب
   `reverseLayout=true` + ترتيب emission غلط — اتصلح بـ `itemsIndexed` ونظرة قدّام
   للرسالة الجاية. `SosBubble` مفيهوش overlay ثابت للنقل أصلاً (رسالة شات عادية) —
   ضفت `ActiveSosBanner` جديد فوق تاب Tools&Chat جوه عقل زاد، بيظهر لو آخر نداء طوارئ
   عمره أقل من ساعتين (مفيش resolved flag في الـ schema)، بيودي لشات العائلة بالضغط.

## Family Hub gap-analysis + haptics/glassmorphism pass — DONE (2026-08-07, commit `34ff482`)
User pasted a "re-architect Family screen into iOS-style live chat hub" prompt (realtime
chat, AI co-pilot approval cards, shared goals, shared tasbiha leaderboard, glassmorphism,
FCM). Gap analysis first (user's call, not a greenfield build): almost everything already
existed — `chat_messages` realtime via `RealtimeChatRepo`, in-chat `PURCHASE_REQUEST`
approval bubble writing real balance updates through `SyncOutbox.enqueueFamilyBalanceUpdate`,
shared `FamilyGoal` progress bar, shared grocery list, and a family-scoped tasbiha
leaderboard already in `TasbihaScreen`'s `FamilyGardenTab`. Real gaps found: zero haptic
feedback anywhere in `FamilyScreen.kt`, and no true blur/glassmorphism (solid gradients
only). User explicitly chose to skip FCM (keep the existing foreground-service +
Supabase-realtime + TTS push mechanism) and skip LLM-based intent parsing (keep the
existing local `classifyMessageIntent()` regex — cheaper, matches this repo's
no-LLM-on-UI-thread bias).
Fix used only existing primitives (`GlassCard`/`zadGlassBlur`/`pressableScale` from
`PremiumSurfaces.kt`/`ZadAnimations.kt`, already used elsewhere e.g. `PharmacyScreen`) —
no new components. `PurchaseBubble` (money approval card) now real frosted glass (API 31+
blur, no-op below per `zadGlassBlur`'s own gate); Approve fires `HapticFeedbackType.LongPress`
(money commit, same weight as Tasbiha's milestone haptic), Reject fires lighter
`TextHandleMove`. Chat input bar background is now frosted glass. Send/SOS/quick-reply/
purchase/poll icon buttons all get `pressableScale()`; SOS additionally fires `LongPress`
given it's an emergency action.
Also verified (no code changes needed, already correct from earlier same-day commits
`ea9f3f6`/`d8635a5`): `CompanionState` StateFlow is centralized on `ZadViewModel`, observed
via `collectAsState()` in both `HomeScreen` (agent-summary-driven) and `ZadIntelligenceScreen`
chat (typing/reply-tone-driven); `uploadAvatar()` sets the `_avatarUri` StateFlow immediately
on Storage-upload success rather than gating on the second `zad_users` DB write, and both
header + drawer `AsyncImage` have `placeholder`/`error` set to `R.drawable.avatar` (Coil
fallback confirmed present, not a blank space on load failure).
Build after the edits: `compileDebugUnitTestKotlin` + `testDebugUnitTest` both green,
171 tests total across 19 classes (3 pre-existing skips, unrelated to this change), 0
failures, 0 errors — includes the `captureCompanionOrbStates` Roborazzi screenshot test.
Ran with `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m`: the container had a
second concurrent Claude Code session running its own Gradle build this session, which
OOM-killed the daemon twice at default settings before the capped-memory retry succeeded.
Pushed to `origin/main` (fast-forward, `5e9bf11..34ff482`).

## Phase 2 — agent_turn unifies the three agents — steps 1-4 DONE, step 5 partial (2026-08-09)
Based on `docs/agent/AGENT_GAP_ANALYSIS.md` (`269737f`): the app had three agents with
three different execution mechanisms, and the only one with real tool calling
(`zad-brain`) was the one the user never talked to. In-app chat and the Telegram bot now
both go through the same `agent_turn`/`agent_confirm` endpoint instead of the old
`[[ACTION]]` text protocol / separate intent-classifier prompts.

- `ff76f01` — `agent_turn`/`agent_confirm` added to `zad-brain`, plus the seven missing
  chat tools (`log_transaction`, `update_transaction`, `set_monthly_limit`,
  `add_inventory_item` — distinct from `update_inventory_qty`, one refuses an item that
  exists and the other refuses one that doesn't — `add_pharmacy_item`, `set_market`,
  `query_family`). Money-writing tools (`CONFIRM_REQUIRED_TOOLS`) are guarded in the loop
  itself, never by prompt instruction: they validate and return a proposal, they never
  execute inside `agent_turn`. 70/70 Deno tests, `deno check` clean. Not deployed.
- `19f10f1` — `ZadViewModel.sendAiChatMessage` calls `agent_turn` first; `[[ACTION]]`
  stays only as a fallback for when that call fails. The visible reply is now built from
  what tools actually returned (executed → ✅ lines, money → an explicit unconfirmed
  proposal) instead of trusting the model's own prose. 186/186 Android tests. Not deployed.
- `80ffea3` — the Telegram bot's three separate per-message model calls (spend classifier,
  medication classifier, reply) collapsed into one `agent_turn` call with the full tool
  set. The `telegram_pending_writes` confirm button is unchanged — same guard, new source.
  128/128 Deno tests. Not deployed.
- `df2faf1` — added `log_pharmacy_dose`, the last tool needed for `[[ACTION]]`'s four
  legacy types to have a 1:1 replacement (found because `tryAgentTurn` returning `true`
  on any successful reply means a missing tool fails *silently*, not as a visible gap).
  A test now asserts every legacy `[[ACTION]]` type has an equivalent tool. 131/131 Deno,
  186/186 Android.
- `497ddec` — found while answering "what happens when `agent_turn` fails on a live
  call": if turn 1 already executed writes and turn 2's `callModel` then threw, the
  handler returned `ok:false` regardless, which makes the client fall back to
  `[[ACTION]]` and can duplicate the write that already committed. Fixed: writes already
  executed → `ok:true, partial:true` + the executed list (client renders it, does not
  fall back); nothing executed → `ok:false` as before (safe, nothing to duplicate). Also
  added durable `zad_brain_runs` logging per turn so a first-deploy failure is visible in
  the table, not just in ephemeral function console logs. Same commit cleaned the
  remaining `zad-ai-proxy` references in `DEPLOY.md`, `PROJECT_MAP.md`, `README.md`
  (the directory itself was already gone before this phase started).

**Deliberately not done (Phase 2-هـ, gated on live validation):** the `[[ACTION]]` text
protocol in `ZadViewModel` and the Telegram bot's old `askZad` read-only path both stay
as fallbacks. Every commit above says "Not deployed" — none of this has run against a
live model yet, so removing the only path that has ever actually worked would be
premature. Delete both only after `agent_turn` has proven itself on real traffic.

Not deployed to Supabase as of this entry — committed to `origin/main` only.

## Phase 0 — single authority for every money figure (2026-08-09)

Requested as the first step of a broader "ZAD → agentic assistant" transformation
prompt the user pasted; only Phase 0 ("fix the ground truth before any agent work") was
in scope for this session. Root cause, independent of that prompt: the app, `zad-brain`,
and the Telegram bot each computed budget/spent/remaining/available on their own and
disagreed — the brain dropped income from `remaining` and read the salary cycle in UTC
(off by a day for any non-UTC account), an unset budget ceiling computed as `0 - spent`
and therefore permanently reported `threat=OVER` to every user who never set a budget,
and the bot's `/balance` button additionally zeroed the ceiling whenever
`limit_confirmed_at` was null — which `AGENT_GAP_ANALYSIS.md` §6 already documents as
null on accounts holding a real `monthly_limit`. Three surfaces, three formulas, same
customer reading three different "المتبقي" numbers depending which screen was open.

- `91409db` — `zad_budget_state(p_user, p_tz)` (migration
  `20260809120000_single_budget_authority.sql`, **applied to the live project**) is now
  the one definition of spent/income/remaining/committed/available/velocity/threat, the
  salary-cycle window, and the per-category split. New helper functions
  (`zad_market_timezone`, `zad_weekend_dows`, `zad_anchored_day`, `zad_cycle_bounds`,
  `zad_obligation_next_due`) mirror `CycleMath.kt`/`BudgetMath.nextDueDate` exactly,
  including `last_working_day` and the Fri/Sat vs Sat/Sun weekend split — the two things
  the brain's old inline TS mirror got wrong. `zad-brain`'s `buildSnapshot` and
  `zad-telegram-bot`'s `context.ts`/`/balance` button now call this RPC instead of
  recomputing; their local TS copies of the cycle/obligation math are deleted, not just
  unused. Kotlin's `BudgetMath.kt`/`CycleMath.kt` stay as the offline-first mirror
  (`ZadViewModel.refreshBudgetState()` overwrites their output with the server's once it
  answers, and logs a warning if the two ever disagree by more than a rounding cent —
  that log line is the only thing that will catch this drifting apart again).
  `BudgetAuthorityParityTest.kt` pins the Kotlin math against golden vectors pulled live
  from the deployed SQL (day-31 salaries, `last_working_day` on a weekend in both
  weekend conventions, year rollover, `once` vs recurring obligations) — not
  hand-derived expectations, actual RPC output pasted in.
  131/131 Deno tests, `deno check` clean on both edge functions, 197/197 Android unit
  tests (0 failures, 3 pre-existing skips, includes the new parity suite). SDK wasn't
  present in this container; installed `cmdline-tools` + platform 36 + build-tools 35.0.0
  to get a real `testDebugUnitTest` run rather than reporting green from a typecheck.
  **Edge functions not deployed** — same "committed, not deployed until validated"
  posture as the rest of this file; only the Postgres migration is live. Deploy
  `zad-brain` and `zad-telegram-bot` once the user confirms.

- **Follow-up fix, same day** — `committed_items` listed obligations only, while
  `committed` summed obligations **and** subscriptions, so the itemisation could not add
  up to the total printed next to it: exactly the defect the list was added to prevent.
  Caught by calling the deployed function on a real account rather than by a test — the
  first live user checked had `committed = 500` sourced entirely from one active
  subscription (ايجار, due 2026-08-24) and got `committed_items: []` with
  `next_obligation_due: null`, so `zad-brain` was handed "500 محجوز" and nothing to name
  it with, on a cycle that did have a charge coming. Both halves are now listed, under
  the same predicates that produce their sums, subscriptions tagged `kind:"subscription"`.
  Verified live: items sum == `committed`, and repo-vs-deployed function bodies hash
  identically once comments are stripped. Note there is no SQL test harness in this repo,
  so this invariant is held by that manual check and the migration's comment, not by a
  test — a `sum(committed_items) = committed` assertion is the obvious thing to add
  whenever a Postgres test path appears.
- **Known carried-over behaviour, not introduced here:** the subscription filter has no
  lower date bound (`renewal_date <= cycle_end`), so an active subscription whose
  `renewal_date` was never rolled forward counts toward `committed` indefinitely. This is
  the same predicate the old `zad-brain` used; preserving it keeps Phase 0 a pure
  consistency change rather than one that also moves everyone's numbers. Worth revisiting
  separately.

Phases 1–5 of the transformation prompt (a `zad-agent` Anthropic-tool-use core,
`NotificationListenerService` auto-capture, geofencing, a persistent `ZadAgentOverlay`
chat surface, Undo) were not attempted this session — out of scope for Phase 0, and
Phase 1 in particular substantially overlaps work already in flight under
`AGENT_GAP_ANALYSIS.md`'s `agent_turn` unification (see the Phase 2 entry above), which
should be reconciled with rather than duplicated under a new name.

## Agentic transformation, reconciled — W1 through W8 (2026-08-09)

Follow-up session to the entry above: the user pasted the full 9-phase transformation
prompt again and asked for it to be executed. Audited the repo first rather than
building blind — most of Phases 0–3 and pieces of 5 already existed
(`zad_budget_state()`, `agent_turn`/`agent_confirm` with 24 real tools, confirm-tier
gating, `UnifiedBankListener` as the notification listener, geofencing, `zad_memory`,
per-tool mutation caps). Reconciled the spec against that reality instead of
re-implementing it, and closed the genuine gaps as W1–W8, one commit each:

- **W1** `1004616` — `agent_actions` table (`previous_state`/`new_state` snapshots,
  monotonic `seq` column — `created_at` alone ties within one Postgres transaction,
  caught by the undo-ordering test) + `zad_agent_undo()` RPC (table allowlist,
  ownership check, "newer action on the same row" guard). Live-tested all 5 cases
  (insert/update/delete undo, stale-undo block, non-allowlisted table) via a
  throwaway SQL harness, cleaned up after.
- **W2** (same commit) — `audit.ts`: `writeRows()` (read-after-write — 0 rows changed
  is now a hard failure back to the model, not a false "done") + `recordAction()`,
  wired into every mutating tool.
- **W3** `10b6096` — both `zad-brain` tool loops raised from a hard 2-turn cap to 8,
  with a 20k-token budget backstop; removed the forced early-break that was cutting
  off any chained multi-tool request after one round.
- **W4** `719b1f8` — `agent_usage` daily cap (60 requests / 200k tokens per user per
  day, env-configurable), checked before `buildSnapshot`/`callModel` so a blocked
  request costs nothing. Live-verified: pre-seeded-at-cap request got the Arabic
  rate-limit message with zero model calls; under-cap request proceeded normally.
- **W5** `f22605b` — "سجل تعديلات زاد" screen (`AgentActionLogScreen.kt`), reachable
  from Profile → Settings, listing `agent_actions` with an Undo button wired to
  `zad_agent_undo()`.
- **W6** `da05808` — `ZadAgentOverlay`: rather than add a second competing floating
  bubble next to the existing `FloatingMascotCompanion`, gave it an optional
  `onQuickChat` callback (long-press opens an inline chat sheet instead of full
  navigation; default `null` preserves old behavior everywhere else).
- **W7** `22b6c47` — first `domain/usecases` extraction (`DeletePharmacyItemUseCase`)
  + matching `delete_pharmacy_item` chat tool, establishing the client/agent
  same-contract pattern (they're different runtimes — Kotlin vs Deno — so it's the
  contract that's shared, not literal code). Also delivered the honest button↔tool
  audit the spec's Phase 4.5 asks for when full coverage isn't realistic in one pass:
  grepped all 128 mutating `SupabaseRepo` functions against the 24 tools and listed
  every orphaned one (subscriptions cancel/pause — the spec's own named example —
  debts, maintenance, family balances/chores, shopping-quantity/delete, insight
  dismissal). Not claimed as "100% parity" because it isn't yet.
- **W8** `eedb70e` — `agent_tasks` deferred-task queue + `pg_cron` processor (every 5
  min, secret-header-gated like the existing telegram cron jobs) + `schedule_task`
  chat tool. Live-verified end to end: a seeded due task was picked up, actually run
  through a real model call (Gemini's quota had recovered by this point in the
  session), and delivered as a real `app_notifications` row; `schedule_task` called
  live through `agent_turn` created both the task and its `agent_actions` audit row.

**Known rough edge, not fixed:** `schedule_task`'s confirmation string displays the
stored UTC instant with a hardcoded UTC label instead of the user's local time — found
live-testing W8 ("بكرة الساعة ٩" echoed back as "٠٦:٠٠"). Timezone-display question,
not an `agent_tasks` bug; worth a follow-up once it's clear how the rest of the app
resolves user-local time.

**Deliberately not attempted:** Phase 6 (`github_code_agent`) — the transformation
prompt itself says to confirm before starting it even when included; Phase 4.6
(household/family coordination) — `family_groups`/`family_members` already exist, so
this is an extension once W7's audit list is worked through, not new ground.

---

## 2026-08-15 — الميزانية والتكرار واللغة والملكية (`b1641a7` → HEAD)

جلسة واحدة، ١٦ commit، كلها اتحرت لسبب جذري في **بيانات حية** مش من العرض. أول رن CI
أخضر على `main` هو أول تحقق حقيقي — الكونتينر مفيهوش Android SDK ولا Deno، فكل اللي
قبل كده كان قراءة كود ومحاكاة منطق واستعلامات قراءة على قاعدة البيانات.

### الميزانية
- **الاشتراكات ماكانتش بتدخل "المحجوز" ولا مرة.** `renewal_date` عمود نصّي حر ومحدش
  بيتحقق منه، والفلتر كان regex على تاريخ ISO. القيم الحقيقية كانت `'30 مارس'` و`'20'`
  و`'30'` — ولا واحدة بتعدّي. `zad_subscription_next_renewal()` بتحل اليوم من (تاريخ
  ISO ← `due_day` ← رقم مجرد في النص) وبتلفّ للأمام بخطوة دورة الفوترة، زي الالتزامات
  بالظبط. **بعد النشر: `committed_subscriptions` بقى 50.00 بدل صفر.**
- **`setMonthlyLimit` كانت بترجع `true` من غير ما تتأكد.** `upsert` مابيرجعش صفوف،
  وكل مسارات الإصلاح (SyncOutbox، resync) متفرّعة على البوليان ده. بقت upsert +
  read-back + إعادة محاولة، زي `syncMarketProfile`.
- **العملة بتتقرا وماحدش بيستخدمها.** `normalizedToCurrency` بتحوّل قبل الجمع. عملة
  فاضية = عملة الحساب **مش SAR** — تفسيرها ريال كان هيضرب حساب مصري في ١٣.
  ملحوظة: `zad_budget_state` لسه بيجمع خام؛ مالوش أثر والعمود null، و`BUDGET DRIFT`
  هيقول أول ما يبان فرق.

### التكرار
- **`log_transaction` كان الأداة الوحيدة من غير حارس** — والوحيدة اللي بتكتب فلوس.
  تلات صفوف دخل ١٠,٠٠٠ في دقيقتين (راتب واحد، إعادة إرسال بعد فشل الدور). الحارس على
  (المبلغ + النوع) خلال ١٠ دقايق، **بيرفض ويسأل** مش بيرمي ولا بيكتب بصمت؛
  `allow_duplicate` بيمرّر تأكيد العميل.
- **`syncData()` كانت إضافة بس** فالصفوف اللي اتشالت من السيرفر فضلت على الجهاز — وRoom
  هو اللي الشاشة بتقرا منه. التنضيف بيشتغل بعد ما الطابور يتفضّى وعلى رد مش مقصوص؛
  المعاملات مستثناة عن قصد.

### الملكية والبيانات
- **٣ من ٤ كتّاب كانوا يقدروا ييتّموا صف.** `addInventory` وحدها فيها الحارس.
  `user_id` بقى NOT NULL على الخمس جداول، والصفوف اليتيمة **اتأرشفت** في
  `zad_orphaned_rows` قبل الحذف — مفيش حاجة اتدمّرت.
- **الوحدة كانت بتتكتب في خانة الفئة** (`كجم`, `لتر`, `حبة`). درس الفاتورة اتطبّق على
  المخزون: التقييد في الكود مش في البرومبت بس.
- **نص الإشعار كان بيترسّب خام.** تطبيقات المراسلة مستثناة عمداً من التجاهل (ده أساس
  الاستغناء عن إذن الـ SMS)، فرسالة شخصية فيها فلوس ورقم كانت بتتخزّن كاملة في
  `zad_notification_ingest_events` وفي `zad_insights.body`. `redact.ts` بتحجب
  الآيبان/البطاقات/التليفونات/البريد/الأرقام الطويلة **وبتسيب المبلغ** — هو سبب الرسالة.
  التصنيف لسه على الخام في الذاكرة.

### اللغة
- `values-en` مكانش موجود أصلاً (١٠١٣ نص). والتركي كان مترجم بالكامل و**مالوش طريق**:
  الاختيار كان `isArabic` بولياني. و`locales_config.xml` مكانش فيه `en` فأندرويد ١٣+
  كان بيرفض الطلب.

### البنية التحتية — وده أهم اللي فيهم على المدى الطويل
- **النشر بقى من الريبو بعد ما الفحص يعدي**، مش خطوة يدوية. ده السبب الجذري لانحراف
  v89 عن git اللي CLAUDE.md بتحذّر منه — النصيحة بتعالج العرض، ده بيقفل السبب.
- **`config.toml` بقى فيه `verify_jwt` لكل فانكشن.** الـ CLI بيطبّق الافتراضي `true`
  على أي فانكشن مش معلنة — و`zad-telegram-bot` شغّال بـ `false` لأن webhook تليجرام
  مابيبعتش `Authorization`. **أول نشر آلي كان هيقتل البوت.**
- **سجل الـ migrations كان متفرّع عن الريبو**: ٢٠ إصدار مطبّق مالهمش ملفات بنفس الأرقام
  (اتطبقوا بأداة بتحط ختم وقتها). ١٩ ملف اتغيّر اسمه للأرقام المسجّلة فعلاً (اتنين
  اتطابقوا **بالمحتوى** مش بالاسم)، وواحد اترجّع من الـ statement المطبّق.
  **ماتعملش `repair --status reverted`** اللي الـ CLI بيقترحه — دي migrations مطبّقة،
  وتسجيلها متراجَع عنها معناه سجل بيكدب على القاعدة.

### لسه مفتوح
- العملة على السيرفر (`zad_budget_state` بيجمع خام).
- زر "عروض قريبة" والكورة الزرقاء — الكود سليم بالقراءة، محتاجين جهاز حقيقي للتأكيد.
- `AiPriceEstimate.currency` افتراضيها `"SAR"` — نفس نوع الكذبة في مسار تقدير الأسعار.
