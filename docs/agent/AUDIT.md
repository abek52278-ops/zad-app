# AUDIT.md — Full-app consistency audit (Task 24)

> ⚠️ **ARCHIVED 2026-09-05 — describes an app structure that no longer exists.**
> Do not use the screen table below to decide anything about the current code.
>
> This audit was run 2026-07-26. Since then the per-screen navigation it documents
> was merged into tab hubs, and **four of the screens named in its table are gone
> from the repo entirely**: `TransactionsScreen`, `WeeklyReportScreen`,
> `AssistantScreen`, `ZadScreens`. Their rows describe files that cannot be opened.
> What replaced them: `FinancesScreen`, `PantryShoppingScreen`, `BrainFamilyScreen`.
>
> Two of its findings were closed and should not be re-investigated:
>   * the `anon`-callable `zad_behavior_patterns(p_user uuid)` hole — revoked;
>     verified 2026-09-05 that no SECURITY DEFINER function is executable by `anon`.
>   * `HomeScreen`'s screen-entry LLM calls — now cooldown-guarded (`3b12078`),
>     see the CLAUDE.md standing rule for the current, measured decision.
>
> It is kept for the methodology (the six rules) and for history, not as a
> statement about today's code. The file said "re-run each epic, don't just append
> to it"; that did not happen for six weeks, and a stale living document is worse
> than an absent one because it reads as current. A fresh full-stack audit was run
> 2026-09-04/05 — that one, not this, reflects the code.

> Living document — re-run each epic, don't just append to it. This run: 2026-07-26,
> after Epic 1+4 (Tasks 19–24). Built by reading code directly (three parallel
> code-reading passes covering all ~24 live screens + independent verification of the
> highest-severity claims), not by assuming.

## Methodology

Every screen under `app/src/main/java/com/example/ui/screens/` was read in full and
checked against the six rules below. Claims that materially change the audit's verdict
(dead-code routes, the price-radar call origin, the auto-write subscription behavior,
whether `ZadAlertRouter` is actually called anywhere) were independently re-verified by
direct `grep`/`Read` against the source after the initial pass, not taken at face value.

## The six rules

1. Numbers must come from `ZadFacts` or `zad_insights` — never a screen-local query, never a literal.
2. Currency must come from `CurrencyFormatter` — zero literals in any Composable.
3. Zero LLM calls on screen open.
4. Every notification goes through `ZadAlertRouter`. Any other path is a duplicate system.
5. Every write goes through `ZadIngest` (once Task 12 lands) — list the exceptions now.
6. Every `zad_*` table has RLS scoped to `auth.uid() = user_id`.

---

## Screen table

| Screen | Numbers come from | Currency source | LLM call on open? | Notification path | Write path |
|---|---|---|---|---|---|
| **HomeScreen** | Mostly VM StateFlows (`zadFacts`, `budget`, `cashOnHand`) — but `totalIncome`/`totalSpent` recomputed screen-locally via `.filter{}.sumOf{}` (L118-119); `consumptionTrend` local `sumOf` (L445) | Clean except `showAllTransactionsDialog` prints raw `tx.amount` with no formatter (L678) | **Yes — 4 real AI/edge calls on every open**: `LaunchedEffect(Unit)` → `refreshAgentSummary()`, `refreshAutoSuggestions()`, `predictNextMonthExpenses()`, `refreshLiveMarketPrices()`. `loadZadInsights()` in the same block is a plain read (fine) | `NotificationManagerCompat` used only to *check* listener-access permission, never to send | `ZadIngest` doesn't exist; `addTransaction`/`updateBudget` → dao/SupabaseRepo directly |
| **TransactionsScreen** | `totalIncome`/`totalSpent`/`balance` computed screen-locally (L72-74); local filter for `filteredTx` | Clean, all via `CurrencyFormatter` | None — no `LaunchedEffect` in the file at all | none | `addTransaction`/`deleteTransaction`/`updateTransactionCategory` → dao/SupabaseRepo directly |
| **BudgetScreen** | `totalIncome`/`totalSpent` local sumOf; category cards from `BudgetTracker` SharedPrefs (not Room); `monthSpent`/`monthIncome`/`dayTotal` local | Clean, all via `CurrencyFormatter` | None on open; `CategoryInsightDialog`'s AI call only fires on explicit tap of the insight icon | none | `addTransaction`/`deleteTransaction`/`updateBudget`/`applySuggestedBudget` → dao/SupabaseRepo; `BudgetTracker.setCategoryBudget` → local SharedPreferences |
| **ZadIntelligenceScreen** (the live "Assistant" nav destination — see note below) | `brainReport` is VM-owned (fine), but `AnalyticsTab` re-derives `totalExpense`/`totalIncome`, subscription totals, category buckets screen-locally | Clean, 38 `CurrencyFormatter` call sites | **Yes — worst offender, and one call auto-writes data.** `LaunchedEffect(Unit)` → `refreshAgentSummary()`, `predictNextMonthExpenses()`, `detectSubscriptions()`, `generateBrainReport()` (which itself may call `suggestMealsForUrgentItems`). Confirmed independently: `detectSubscriptions()` calls `addSubscription()` for any detection with `confidence > 0.8` — **new subscription rows get written with zero user confirmation, purely from opening this screen** (`ZadViewModel.kt:1634-1659`) | none | Same as above — the auto-write is the write path |
| **WeeklyReportScreen** | `categoryTotals`/`weeklyTx` computed screen-locally; `familyHealthScore` from AI (fine) | Clean, via `CurrencyFormatter` | **Yes.** `LaunchedEffect(familyState)` auto-calls `ZadAiRepository.analyzeFamily` as soon as family data resolves — no explicit "generate" tap required | none | Read-only screen, no write path |
| **StatementImportScreen** | `row.amount` shown raw via `"%.2f".format()`, not from a VM/ZadFacts source | **No `CurrencyFormatter` anywhere in the file** — bare unformatted numbers | None — CSV parsing is fully local | `Toast`, not a system notification and not `ZadAlertRouter` | `StatementCsvImporter.commitImport` → dao + SupabaseRepo directly |
| **AssistantScreen.kt** (`fun AssistantScreen`) | N/A | N/A | N/A | N/A | **Dead code** — see note below |
| **ZadScreens.kt** (`ChatScreen`, `MainContent`) | Raw Postgrest `chat_messages` select (plain, no AI) | none | None | none | `SupabaseRepo.sendMessage` | **Dead code** — see note below |
| **FamilyScreen** (Family/NoFamily/ActiveFamily) | `state.members/chores/goals` from Supabase; `totalBalance`, `maxBalance`/`fraction`, `goalProgress`, spend ratios all computed screen-locally in the Composable body | Clean, all via `CurrencyFormatter` | No — `loadChildrenSpending()` on open is a plain Supabase read; `suggestFamilyGoal()` is AI but button-triggered | **`FamilyViewModel` calls `SupabaseRepo.sendAppNotification` directly in 7+ places** — bypasses `ZadAlertRouter` entirely | `createFamily`/`joinFamily`/`sendMessage`/`toggleGroceryItem`/`toggleChore`/`addChore`/`updateSpendLimits` → SupabaseRepo directly |
| **TasbihaScreen** | VM StateFlows ← plain Supabase reads | none displayed | No — plain reads only | none | `incrementTasbihaClicks`/`renameTasbiha`/`createNewTree` → SupabaseRepo directly |
| **ProfileScreen** | `inventory.size`/`subscriptions.size`/`transactionsCount` from VM StateFlows; `familyMembersCount` filtered locally | none displayed | No — `loadUserProfile()` is a plain read | none | `updateUserProfile`/`uploadAvatar`/`deleteAccount` → SupabaseRepo directly |
| **EditProfileScreen** | none displayed | none | No | none | `updateUserProfile`; `updateFamilyMemberAlias` called directly from Composable scope |
| **FamilyManagementScreen** | `activeState.members` from VM state | none | No | Toast only | `changeMemberRole`/`kickMember` → SupabaseRepo directly |
| **PaymentAndBudgetScreen** | `budget` VM StateFlow; hardcoded `3500.0` fallback default until loaded (`ZadViewModel.kt:96`) | Clean — `CurrencyFormatter.format`; `market.currencySymbol` from the `Market` enum (not a literal) | No | none | `updateBudget` → SupabaseRepo; `MarketPrefs.setMarket()` writes SharedPreferences directly |
| **AssistantAlertsScreen** | Toggles only, no numbers | none | No | Sets `AlertPrefs` SharedPreferences toggles that gate notifications sent elsewhere — no direct send | `AlertPrefs.setEnabled` → SharedPreferences |
| **NotificationCenterScreen** | `unreadCount` computed screen-locally over VM StateFlows | none | No — `loadNotifications()`/`loadZadInsights()` on open are plain reads; `answerBrainQuestion()`'s `triggerBrainEvent` only fires on tap | none direct — only reads/marks state | `markNotificationRead`/`dismissInsight` → SupabaseRepo directly |
| **InventoryScreen** | VM StateFlow, but the shortage banner's cost estimate uses a **hardcoded local price table** `getEstimatedPrice()` (L408-424) | Clean, via `CurrencyFormatter` | None | none | `addInventory`/`deleteInventory`/`consumeInventoryItem`/`injectScannedItems`/`addShoppingItem` → dao/SupabaseRepo directly |
| **ShoppingListScreen** | VM StateFlows, totals derived acceptably | Clean, via `CurrencyFormatter` | **Yes.** `LaunchedEffect(Unit)` → `fetchGrocerySuggestions()` (AI); reactive `LaunchedEffect(shoppingList)` → `ZadAiRepository.estimatePrice()` per item, no user tap | none | `addShoppingItem`/`toggleShoppingItemPurchased`/`deleteShoppingItem`/`matchProduct` → SupabaseRepo directly |
| **SubscriptionsScreen** | VM StateFlow, totals derived acceptably | Clean, via `CurrencyFormatter` | **Yes.** `LaunchedEffect(Unit)` → `detectSubscriptions()` (AI) fires every open (same auto-write behavior as ZadIntelligenceScreen, since it's the same VM function) | none | `addSubscription`/`deleteSubscription`/`updateSubscriptionActive` → dao/SupabaseRepo directly |
| **PharmacyScreen** | VM StateFlows; day-supply/expiry math via item helper methods (acceptable) | Clean, via `CurrencyFormatter` | None | none | `addPharmacyItem`/`deletePharmacyItem`/`consumePharmacyDose`/`refillPharmacyItem` → dao/SupabaseRepo directly |
| **MaintenanceScreen** | VM StateFlow; due/overdue filters computed in body (acceptable) | Clean | None | none | `addMaintenanceItem`/`deleteMaintenanceItem`/`markMaintenanceServicedToday` → dao/SupabaseRepo directly |
| **CameraScreen** | N/A | Clean, via `CurrencyFormatter` | **Fine, user-triggered** — `analyzeInventoryImage`/`analyzeReceipt` only fire inside the photo-capture callback, reached only after an explicit "مسح المخزون"/"مسح الفاتورة" tap. `LaunchedEffect(Unit)` only restores a saved API key from prefs | none | `addTransaction`/`injectScannedItems`/`addInventory` → dao/SupabaseRepo directly |
| **RecipeDetailScreen** / **RecipeDetailDialog** | Props passed in; dialog auto-fetches via `LaunchedEffect` | none | Dialog's `getRecipeDetails` fetch-on-open is a legitimate loading-screen pattern (shows loading/error/retry) | none | none |
| **NearbyDealsScreen** | VM StateFlows for inventory/pharmacy context | none (no monetary values shown) | **Yes, and architecturally inconsistent.** Does *not* call the price-radar actions at all (those live in ZadIntelligenceScreen, button-gated — see below). Instead: `LaunchedEffect(hasLocationPermission)` auto-calls `searchNearby()` → `OverpassRepo` makes a **direct client-side OkHttp POST to `https://overpass-api.de/api/interpreter`**, entirely bypassing `SupabaseRepo`/any Edge Function. On success it also auto-calls `triggerBrainEvent()` → `zad-brain` — an on-open LLM call with no user tap | none | none |
| **HelpSupportScreen** | N/A | none | Fine — AI call only fires inside the send-button `onClick` | none | none |
| **TermsOfServiceScreen** | Static text | none | none | none | none |
| **Auth flow** (Login/SignUp/MarketSelection/Onboarding) | N/A | `MarketSelectionScreen.kt:31` has a literal `"ريال سعودي"` — a market-name label, not a formatted amount, so not a rule-2 violation | none | none | Writes go through `AuthViewModel`, not screen-local |

**`ZadIngest` (rule 5) does not exist anywhere in this codebase** — confirmed by `grep`;
the only two hits are forward-looking comments (`SaBankParser.kt:483`,
`SupabaseRepo.kt:229`) describing it as a Task 12 future consolidation. Every write
listed above is, by definition, an "exception" to rule 5 today. This isn't new
information but is worth stating plainly rather than repeating "ZadIngest doesn't exist"
in every row.

### Dead-code note: `AssistantScreen.kt` and `ZadScreens.kt`

Verified independently (not just taken from the code-reading pass): `MainScreen.kt:483`
maps the live `Screen.Assistant.route` nav destination to `ZadIntelligenceScreen`, not
`AssistantScreen`. Separately, `MainActivity.kt:283`'s `composable("chat")` route renders
a literal placeholder — `Text("الشات متاح من صفحة العائلة")` — not `ChatScreen`. Both
`AssistantScreen.kt` (`fun AssistantScreen`) and `ZadScreens.kt` (`fun ChatScreen`,
`fun MainContent`) are unreachable from any live navigation path; they only reference
each other. The real chat surfaces in the shipped app are the `ChatTab` inside
`FamilyScreen.kt` (family chat) and `ZadIntelligenceScreen` (AI assistant). Recommend
deleting `AssistantScreen.kt` and the `ChatScreen`/`MainContent` functions in
`ZadScreens.kt` in a future cleanup pass — not done in this audit since deleting code is
out of scope for a read-only audit and deserves its own reviewed commit.

---

## Violations by rule, most severe first

### Rule 4 — notifications must go through `ZadAlertRouter`

**This is not a partial violation — `ZadAlertRouter` is entirely unused.** Verified with
`grep -rn "ZadAlertRouter\."` excluding its own file: zero call sites anywhere in the
app. It is fully implemented (`com/zad/agent/ZadAlertRouter.kt`) but wired to nothing.
Every actual notification in the shipped app goes through one of 14 other files that
independently call `NotificationCompat.Builder` and/or `SupabaseRepo.sendAppNotification`
directly: `SeasonalEventReminderWorker`, `TasbihaReminderWorker`, `MorningSummaryWorker`,
`UnifiedBankListener`, `ChatNotificationService`, `BankTransactionApplier`,
`ZadCentralBrain`, `BudgetTracker`, `ZadNotifier`, `SupabaseRepo` (the helper itself),
`SyncOutbox`, `UnifiedSmsReceiver`, `PharmacyReminderReceiver`, `ZadViewModel`,
`FamilyViewModel`. This is 14 independent, unreconciled notification systems, not one
router with occasional bypasses. Fixing this properly is a real migration project (audit
each of the 14 call sites' surface/priority semantics against what `ZadAlertRouter`
already models), not a one-line fix — flagged here, not attempted in this audit.

### Rule 3 — zero LLM calls on screen open

Six confirmed on-open AI calls, worst first:

1. **`ZadIntelligenceScreen`** — 3 AI calls on every open (`refreshAgentSummary`,
   `predictNextMonthExpenses`, `detectSubscriptions`), and `detectSubscriptions()`
   **auto-writes new subscription rows with zero user confirmation** whenever detection
   confidence exceeds 0.8 (`ZadViewModel.kt:1634-1659`). This is the highest-severity
   finding in the whole audit: a write happening as a side effect of navigation, not a
   deliberate action.
2. **`HomeScreen`** — 4 AI/edge calls on every open (`refreshAgentSummary`,
   `refreshAutoSuggestions`, `predictNextMonthExpenses`, `refreshLiveMarketPrices`). Same
   underlying functions as above in some cases — this is the app's most-visited screen,
   so it's the highest-*frequency* violation even though `ZadIntelligenceScreen`'s
   single auto-write is worse in kind.
3. **`SubscriptionsScreen`** — same `detectSubscriptions()` auto-write behavior,
   independently triggered from this screen's own `LaunchedEffect(Unit)`.
4. **`ShoppingListScreen`** — `fetchGrocerySuggestions()` on open, plus a reactive
   `LaunchedEffect(shoppingList)` that calls `estimatePrice()` per item with no user tap.
5. **`WeeklyReportScreen`** — `analyzeFamily()` auto-fires as soon as family data
   resolves, not gated behind an explicit "generate" action.
6. **`NearbyDealsScreen`** — `triggerBrainEvent()` fires automatically after a
   successful location search, no user tap.

Confirmed clean: `CameraScreen` (AI only after explicit capture), `HelpSupportScreen`
(AI only on Send tap), `BudgetScreen`'s category-insight AI (button-gated),
`TransactionsScreen`/`ProfileScreen`/`TasbihaScreen`/`FamilyScreen`/
`NotificationCenterScreen` (on-open effects are plain reads only).

### Rule 1 — numbers must come from `ZadFacts`/`zad_insights`

Five independent screen-local re-derivations of the same "income/expense" math instead
of one shared source: `HomeScreen` (L118-119), `TransactionsScreen` (L72-74),
`BudgetScreen` (L86-87), `WeeklyReportScreen` (L154-164), `ZadIntelligenceScreen`'s
`AnalyticsTab` (L202-205). None of these are wrong today, but five separate
implementations of the same filter+sum is exactly the drift risk Task 19.0 was created to
eliminate for the *budget* numbers specifically (`BudgetMath`) — these five are the same
class of risk for the *income/expense breakdown* numbers, just not yet consolidated.
Also: `InventoryScreen`'s shortage-cost banner uses a **hardcoded local price table**
(`getEstimatedPrice()`), and `StatementImportScreen` displays transaction amounts that
never touch any shared source at all (raw CSV row data).

### Rule 2 — currency must come from `CurrencyFormatter`

Two real literals found:
- `StatementImportScreen.kt:249` — every amount in the whole screen is a bare
  `"%.2f".format()` number with no currency symbol at all.
- `HomeScreen.kt:678` — the "show all transactions" dialog prints `tx.amount` raw.

(`MarketSelectionScreen.kt:31`'s `"ريال سعودي"` is a market-name label in an onboarding
picker, not a currency-formatted amount — not a rule-2 violation.)

### Rule 6 — RLS on every `zad_*`/user-scoped table

**Found and fixed during this audit, verified live:** `sent_budget_alerts` (used by the
`notify_parents_on_child_spend` trigger) had RLS **disabled entirely** — same class of
gap as the `affiliate_*` tables fixed earlier this epic. The trigger writes via
`SECURITY DEFINER` so it was unaffected, but with RLS off, any authenticated client could
have read or written arbitrary rows in this table directly through Postgrest. Fixed:
`enable row level security` + a policy scoped to `auth.uid() = user_id`
(migration `20260726110000_sent_budget_alerts_rls.sql`), applied live and confirmed via
direct query (`polqual` shows the `auth.uid() = user_id` comparison). All other
`zad_*` tables plus the other user-scoped non-`zad_`-prefixed tables (`app_notifications`,
`user_behavior_profile`, `chat_messages`, etc.) have RLS enabled with at least one
policy — family-shared tables (`family_*`, `shared_grocery_*`) correctly use family-scoped
policies rather than a bare `auth.uid() = user_id` check, which is the correct pattern for
shared data, not a violation of the rule's intent.

---

## The two specifically-called-out checks

### Where does the price radar's `groq` call originate?

**Confirmed safe.** `refreshLiveDeals()`/`refreshPriceShockWarnings()` (the actual
Deal Matcher / Price Radar functions, calling the server's `fetch_live_deals`/
`fetch_price_shock_warnings` actions) are called **only** from `onClick` handlers in
`ZadIntelligenceScreen.kt` (lines 2256, 2337) — never from a `LaunchedEffect`. Both route
through `ZadAiRepository.callAction()` → `SupabaseRepo.callEdgeFunction()`, i.e. the
`zad-core-intelligence` Edge Function server-side. No Groq key exists anywhere in the
Android client (`grep -rn "groq\|GROQ_API_KEY" app/src/main/java` finds nothing except
`ZadAiGeminiClient.kt`, a separate, unrelated, intentionally-dormant BYOK path — see
below). So: not extractable, not an on-open call. This item, flagged in the epic as
"reported broken and never confirmed fixed," is now confirmed fixed.

Separately, and not what this check asked about but adjacent: `ZadAiGeminiClient.kt`
*does* call `api.groq.com` directly from the client — but only when a user has manually
pasted their own personal key into the camera screen (gated behind
`geminiApiKey?.takeIf { it.isNotEmpty() }` in `ZadAiRepository.kt`). This is a
user-supplied BYOK path, not an embedded app secret, and is already documented as
intentionally dormant/optional in `CLAUDE.md` — not a new finding, noted here only for
completeness.

Also found, not asked about but worth flagging under the same "does the app make direct
client-side network calls it shouldn't" umbrella: `NearbyDealsScreen`'s `OverpassRepo`
makes a direct client-side POST to the public, keyless `overpass-api.de` API. No secret
is at risk (Overpass is anonymous/public), but it's still a client-side network call and,
per the finding above, fires automatically on screen open rather than being user-gated —
inconsistent with how the app handles every other external-API call (all routed through
Edge Functions).

### The three previously-missing imports — does the build run clean now?

Confirmed this session, **after** all Task 19–24 changes: `./gradlew --no-daemon
:app:compileDebugKotlin` and `:app:assembleDebug` both completed with `BUILD SUCCESSFUL`
and zero compilation errors (only pre-existing deprecation warnings, e.g.
`Icons.Filled.TrendingUp` → `AutoMirrored`, none related to missing imports).
`:app:testDebugUnitTest` passed clean at 94/94. **`:app:lintDebug` could not be completed
this session** — the container ran with multiple concurrent Claude Code sessions active
in parallel (up to 8 observed via `ps aux`), and the Gradle daemon was repeatedly
SIGKILLed by memory pressure before finishing lint analysis, across roughly ten attempts
over the course of this session. This is an environment constraint, not a build defect —
`compileDebugKotlin`/`assembleDebug` succeeding is strong evidence the three-missing-import
class of bug is not present, but `lintDebug` (which would also catch resource-level
issues like the `MissingTranslation` error found and fixed during Task 23) is the one
gate that still needs a clean run confirmed, ideally in a less resource-contended
session.

---

## Summary for next session

- Fix quickly, cheap: the two `CurrencyFormatter` literals (rule 2), delete the two
  confirmed-dead screen files (`AssistantScreen.kt`, the dead parts of `ZadScreens.kt`).
- Fix carefully, real design work: the `detectSubscriptions()` auto-write-on-open bug —
  probably wants an `emit_insight`/confirmation-card pattern instead of a silent
  direct write, matching how `suggest_budget_change` already asks before acting.
- Biggest structural item, not a quick fix: reconciling 14 independent notification call
  sites into (or explicitly retiring) `ZadAlertRouter`.
- Still owed: a clean `lintDebug` run in a less resource-contended session.
