# EPIC_2_ai_screen.md — Zad AI screen restructure

Scope for Epic 2, agreed with the user on 2026-07-26 (`docs/agent/SESSION_2026_07_26_epic19.md`
flagged that no spec existed yet and scope had to be defined with the user first — this file
is that definition, not a pre-existing plan).

## Why

`ZadIntelligenceScreen.kt` had 3 tabs (Analytics / Subscriptions / Chat). The "Analytics" tab
alone stacked ~20 cards in a single `LazyColumn` scroll (health gauge, health score, stress
test, month comparison, depletion forecast, export, behavior profile, behavioral nudge, 4
mini-stats, expense donut, inflation radar, price shock radar, monthly bar chart, consumption
ticker, prediction, smart buying timing, patterns list, live deals, financial challenges,
sinking funds, what-if simulator, insights list). No grouping, no hierarchy — a wall of cards.

## Agreed 4-section structure

| # | Tab | Contents |
|---|---|---|
| 1 | **نظرة عامة** (Overview) | `SpendingPowerGaugeCard`, `HealthScoreCard`, `FinancialStressTestCard`, `MonthComparisonCard`, `DepletionForecastCard`, `ExportReportButton`, mini-stats (expense/income, inventory/subscriptions), `ExpenseDonutCard`, `MonthlyBarChartCard` |
| 2 | **السلوك والتوقعات** (Behavior & Predictions) | `BehaviorAnalysisCard`, `ServerBehaviorProfileCard`, `BehavioralNudgeCard`, `InflationRadarCard`, `PriceShockRadarCard`, `ConsumptionTickerCard`, `PredictionCard`, `SmartBuyingTimingCard`, patterns list |
| 3 | **الاشتراكات والفرص** (Subscriptions & Deals) | existing subscriptions list + `DebtPayoffPlannerCard` (unchanged) + `LiveDealsCard`, `FinancialChallengesCard`, `SinkingFundsCard` (moved in from the old Analytics tab) |
| 4 | **أدوات ومحادثة** (Tools & Chat) | `WhatIfSimulatorCard` + insights list, and the existing Chat UI — switched via an in-tab segmented `FilterChip` toggle ("أدوات" / "محادثة"), not nested scrollables, since `ChatTab` is a full-height `Column` with its own `LazyColumn` + pinned input |

Navigation shape: 4 top-level `Tab`s in the same `TabRow` (replacing the old 3), per user's
choice — not an accordion.

## Task 29.1 — pure layout regroup (this task)

- Renamed `AnalyticsTab` → split into `OverviewTab` + `BehaviorPredictionsTab`.
- `SubscriptionsTab` gained `inventory`/`familyViewModel` params to host the 3 moved cards.
- New `ToolsChatTab` composable (segmented toggle wrapping `WhatIfSimulatorCard`+insights vs.
  the existing `ChatTab`).
- New string resources across all 4 locale files (`values`, `values-ar-rEG`, `values-ar-rSA`,
  `values-tr`): `tab_overview`, `tab_behavior_predictions`, `tab_subscriptions_deals`,
  `tab_tools_chat`, `tools_chat_tools_label`, `tools_chat_chat_label`.
- No card internals touched, no logic changed, no new LLM calls, no data source changes —
  this task is placement only.

**Deliberately deferred to a separate task** (per user's explicit decision when scoping this
epic): the `AUDIT.md`-flagged `detectSubscriptions()` auto-write-without-confirmation bug, and
the rule-1 screen-local income/expense recomputation. Both still apply post-restructure exactly
as documented in `AUDIT.md` — this task did not touch `ZadViewModel` or any data-fetching logic.

## Build status

**Not verified this session** — `./gradlew --no-daemon :app:compileDebugKotlin` was
OOM-killed by the container on every attempt (4 attempts, with/without reduced `-Xmx`); `free -h`
showed under 1GB free RAM throughout, several concurrent Claude Code sessions running. Same
class of environment constraint as the `lintDebug` issue documented in `AUDIT.md`. The diff was
manually reviewed line-by-line for signature/call-site consistency (all call sites match new
function signatures, brace balance verified, no dangling references to the old `AnalyticsTab`
name confirmed via `grep`), but this is not a substitute for an actual compiler run. **First
thing next session: run `compileDebugKotlin` + `assembleDebug` + `testDebugUnitTest` in a less
contended session and confirm clean before treating this task as done.**

## Session close — 2026-07-26

User explicitly signed off on closing the session with Task 29.1 in this state: code
committed (`70ba336`), build **not** re-verified — `compileDebugKotlin` still OOM-killed
by the container after 4 attempts, no clean run happened. This is a recorded user
decision to accept that risk and stop here, not a claim that the build passed.

## Build confirmed — 2026-07-30

This session's container had no Android SDK at all (not an OOM issue this time —
`ANDROID_HOME` unset, no `local.properties`). Installed `platforms;android-36` +
`build-tools;36.0.0` + `platform-tools` via `sdkmanager`, added `local.properties`
(gitignored). All three gates ran clean: `compileDebugKotlin` BUILD SUCCESSFUL,
`assembleDebug` BUILD SUCCESSFUL, `testDebugUnitTest` 94/94 passing (3 skipped). Task
29.1 is done — see `PROGRESS.md`.

## `detectSubscriptions()` auto-write fix — DONE (2026-07-30)

`ZadViewModel.detectSubscriptions()` used to call `addSubscription()` directly for any
AI detection with `confidence > 0.8` — a silent write on screen open, zero user
confirmation (`AUDIT.md`'s highest-severity finding). Fixed with a confirm/dismiss
pattern instead of piggybacking on `zad-brain`'s server-side `ask_user`/`emit_insight`
tool machinery (that's a different system — tool-calling loop against `zad_insights`,
not reachable from this purely client → `zad-core-intelligence` Edge Function call).

- New `ZadViewModel.pendingSubscriptions: StateFlow<List<DetectedSubscription>>`.
  `detectSubscriptions()` now populates it instead of writing; `confirmDetectedSubscription()`
  writes (existing `addSubscription()` path) and clears the pending entry;
  `dismissDetectedSubscription()` clears it with no write and remembers the name
  in-memory so the same detection doesn't reappear every time the screen reopens
  (not persisted across app restarts — deliberately minimal, matches this fix's scope).
- New shared composable `ui/components/PendingSubscriptionsCard.kt`
  (`DetectedSubscriptionsSection`) — confirm/dismiss card, used in both places
  `detectSubscriptions()` is triggered: `SubscriptionsScreen.kt` and
  `ZadIntelligenceScreen.kt`'s `SubscriptionsTab`.
- New strings (`detected_subscription_title/confirm/dismiss`) in all 4 locale files.
- `compileDebugKotlin` / `assembleDebug` / `testDebugUnitTest` (94/94) / `lintDebug` all
  ran clean in one pass — first clean `lintDebug` in this epic (previously blocked by
  container memory pressure across ~10 attempts in prior sessions).

## What's next in Epic 2 (not started)

- Separate task: consolidate the 5 screen-local income/expense recomputations.
- Not yet scoped: whether Epic 2 also touches `HomeScreen`'s AI-cards-on-open problem or stays
  scoped to `ZadIntelligenceScreen` only — ask the user before assuming either way.
