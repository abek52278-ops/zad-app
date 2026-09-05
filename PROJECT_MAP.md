# PROJECT_MAP — Zad Family Manager

## TECH_STACK
- **Frontend:** Android (Kotlin, Compose UI, Material3)
- **Backend:** Supabase (PostgreSQL, PostgREST, Realtime, Auth, Edge Functions)
- **AI:** Edge Function `zad-core-intelligence` for per-action AI, and `zad-brain` for the tool-calling agent (`agent_turn`/`agent_confirm`, the path both the in-app chat and the Telegram bot now go through). `zad-ai-proxy` no longer exists in this repo — the directory is gone; if you find it named anywhere else, that reference is stale. **The provider is Gemini, not OpenRouter** — `zad-core-intelligence` runs every text/JSON action through `callGeminiPool()` on `ZAD_API_KEY_1..5` first, and only falls out to Groq (`openai/gpt-oss-120b`) when every Gemini key fails; `callVisionModel()` has no Groq fallback at all, because Groq rejects JSON mode on any request carrying an image. `GROQ_API_KEY` (singular) is still used directly by Whisper transcription and the two `groq/compound-mini` web-search actions (Deal Matcher, Price Radar). **`OPENROUTER_API_KEY` is inert** — verified 2026-09-05 by grepping `Deno.env.get` across all 13 edge functions: zero hits. CLAUDE.md's first bullet is the authority on provider routing; this line has now been wrong twice (OpenRouter → Groq-primary → Gemini-primary), so prefer CLAUDE.md over this file if they ever disagree again.
- **AI Client SDK:** none — all AI calls go through `ZadAiRepository`/`SupabaseRepo.callEdgeFunction` (raw `HttpURLConnection`) to the Edge Function, never a client-side AI SDK
- **Local DB:** Room (SQLite, cache layer)
- **Build:** Gradle (Kotlin DSL, KSP, Secrets Gradle Plugin)
- **Min SDK:** 24, Target: 35, Compile: 36
- **Serialization:** kotlinx.serialization (JSON), Moshi (legacy)

## SYSTEM_FLOW

### Authentication Flow
```
SplashScreen → check Supabase session
  ├─ Has session → MainScreen (Home)
  └─ No session → OnboardingScreen → Login/SignUp
```

### Data Flow (Personal)
```
User Input (UI) → ViewModel → SupabaseRepo (PostgREST)
                                    ↓
                              Room DB (local cache)
                                    ↓
                              ZadCentralBrain → Groq AI (via Edge Function)
                                    ↓
                              UI StateFlow updates → Screen recomposition
```

### Data Flow (Family)
```
FamilyScreen → FamilyViewModel → SupabaseRepo (PostgREST)
                                    ↓
                              Realtime subscriptions (chat_messages)
                                    ↓
                              UI StateFlow updates → Screen recomposition
```

### AI Proxy Flow
```
App (ZadAiRepository.callAction) → Edge Function `zad-core-intelligence` → Gemini pool (ZAD_API_KEY_1..5, text/JSON + vision) → Groq fallback for text/JSON only (openai/gpt-oss-120b); GROQ_API_KEY also serves Whisper + compound-mini search
     ↓                                                                            ↓
  {action, user_id, dialect, payload}                                    {text|insights|prices|..., ok}
```

### Navigation Structure
```
MainActivity
  └─ "splash" → SplashScreen
  ├─ "onboarding" → OnboardingScreen (Login/SignUp)
  └─ "main" → MainScreen
                ├─ "home" → HomeScreen
                ├─ "inventory" → InventoryScreen
                ├─ "transactions" → TransactionsScreen
                ├─ "subscriptions" → SubscriptionsScreen
                ├─ "shopping" → ShoppingScreen
                ├─ "family" → FamilyScreen (5 tabs)
                ├─ "tasbiha" → TasbihaScreen (splash → garden)
                ├─ "assistant" → AssistantScreen
                ├─ "budget" → BudgetScreen
                ├─ "camera" → CameraScreen
                ├─ "profile" → ProfileScreen
                ├─ "intelligence" → ZadIntelligenceScreen
                └─ "notifications" → NotificationsScreen
```

### Database Tables (Supabase)
| Table | Type | Status |
|-------|------|--------|
| zad_users | Personal | ✅ OK |
| zad_inventory | Personal | ✅ OK |
| zad_transactions | Personal | ✅ OK |
| zad_subscriptions | Personal | ✅ OK |
| zad_shopping_list | Personal | ✅ OK |
| family_groups | Family | ✅ OK |
| family_members | Family | ✅ OK |
| chat_messages | Family | ✅ OK (Realtime) |
| shared_grocery_list | Family | ✅ OK |
| family_goals | Family | ✅ OK |
| family_chores | Family | ✅ OK |
| app_notifications | Notification | ✅ OK |
| family_tasbiha | Tasbiha | ✅ OK |
| family_tasbiha_challenges | Tasbiha | ✅ OK |
| tasbiha_challenge_progress | Tasbiha | ✅ OK |
| family_typing_status | Family | ✅ OK |
| affiliate_products / affiliate_clicks / affiliate_catalog_requests | Personal | ✅ OK (RLS: catalog read-only for authenticated, writes service_role) |
| zad_pharmacy_items | Personal | ✅ OK (RLS: owner-only) — Smart Pharmacy dose scheduling/tracking |
| zad_maintenance_items | Personal | ✅ OK (RLS: owner-only) — Home Maintenance tracking |
| zad_dose_log | Personal | ✅ OK (RLS: owner-only) — pharmacy dose-taken history |
| market_price_cache | Shared/global | ✅ OK (RLS: authenticated read-only, writes via service_role only) — 12h cache for Zad Live Market Ticker, added 2026-07-24 |

### Edge Functions
| Function | Endpoints | Status |
|----------|-----------|--------|
| zad-core-intelligence | meal_suggestions, grocery_suggestions, spending_insights, agent_summary, analyze_bank_notification, analyze_inventory_image, analyze_receipt, family_assistant, estimate_price, detect_subscriptions, recipe_details, behavior_analysis, expense_prediction, bill_classification, family_analysis, auto_suggest, family_goals_suggest, fetch_live_deals, fetch_price_shock_warnings, fetch_live_market_prices, ai_text, brain_evaluate, voice_agent, seasonal_forecast | ✅ 24/24, v40 as of 2026-07-24 |
| update-behavior-profile | (pg_cron scheduled) | ✅ OK |
| amazon-creators-search | (Amazon affiliate catalog search) | ✅ OK |
| ~~zad-ai-proxy~~ | — | deleted — no source directory in `supabase/functions/`. Do not resurrect; `zad-core-intelligence` and `zad-brain` are the two live functions |

### Key Architecture Decisions
1. **Room as single source of truth** for personal data (sync from Supabase)
2. **No Room for family data** — direct Supabase queries + Realtime
3. **Optimistic UI** for mutations (update local state first, then remote)
4. **SECURITY DEFINER function** `get_my_family_ids()` to break RLS recursion
5. **ZadCentralBrain** unified AI brain with behavior learning + predictions

## COMPLETED FEATURES
- **Sprint 9 — Honest AI-failure messaging + Profile/Home dead-code cleanup + product animations + consumption ticker + Zad Live Market Ticker (2026-07-24):**
  - **Root-caused the "most AI features say فشل الاتصال (connection failed)" complaint:** the failure was never a real network problem — `zad-core-intelligence`'s `family_assistant`/`ai_text`/`brain_evaluate` actions baked "تعذر الاتصال" directly into their JSON response whenever OpenRouter's free-tier model was slow/rate-limited (confirmed via edge-function logs: HTTP 200 on every call, some taking 20–25s, right at the model's own timeout). Fixed to the same honest-failure contract already used by `meal_suggestions`/`recipe_details` (`text: null, ok: false` on genuine failure, no baked-in blame text) — client-side fallback strings (`ZadViewModel.kt`, `ZadAiRepository.kt`) updated to say the AI is busy, not that the connection failed. Deployed live (v36+), no app rebuild needed for this half of the fix.
  - **`MainActivity.kt`:** the outer (legacy, unreachable) `NavHost`'s `"inventory"` route had `onNavigateToAssistant` pointing at a non-existent `"assistant"` route in that graph — genuinely unreachable in practice (verified nothing ever navigates the outer NavHost there) but fixed to point at `"main"` as cheap insurance.
  - **`ProfileScreen.kt` / `ProfileSubScreens.kt`:** removed an unconditional "Verified" badge with no backing verification state, a stray duplicate first-letter text under the username, and the entirely unused/duplicate `HelpAndSupportScreen` (dead mailto-based FAQ screen — the real Help button opens the AI-chat-based `HelpSupportScreen` instead). `AnimatedStatCard`/`AchievementBadge` had shadow/card styling that looked tappable with no `onClick` — shadow removed, flat tinted/borderless treatment substituted so they read as informational, not buttons.
  - **`HomeScreen.kt`:** deleted 10 confirmed-dead composables + 2 dead helpers (`ExpenseAnalysisSection`, `QuickStatsSection`, `RecentTransactionsSection`, `DashboardSummariesSection`, `MiniTransactionsWidget`, `QuickGlanceWidgets`/`GlanceWidgetCard`, `FamilyMiniHub`, `FeaturesRowSection`/`FeatureItem`, `GreetingBanner`, `DashboardGrid`) — zero call sites anywhere, confirmed via grep before removal; pure cleanup, no UI change since none were ever rendered.
  - **`InventoryScreen.kt`:** replaced the generic Material-icon-per-item system (`getIconForItem`, now deleted) with `getEmojiForItem` — product-specific animated emoji (🥛 milk, 🍞 bread, 🍅 tomato, 🍗 chicken, etc.) using `Modifier.floatingIdle()` for a subtle "alive" bob, on both the main grid card and the shortage-list card.
  - **`ZadIntelligenceScreen.kt`:** new `ConsumptionTickerCard` — stock-exchange-style line chart (Canvas-drawn, gradient area fill, glowing live end-point) of daily spend over the last 30 real calendar days (`computeDailySpendData`), with a red/green % badge vs. the prior 30-day period. Distinct from the existing monthly bar chart (`MonthlyBarChartCard`) — genuinely date-granular, not month-bucketed.
  - **Zad Live Market Ticker** — new feature, home screen, adult/admin mode only: `fetch_live_market_prices` action in `zad-core-intelligence` (12h server cache in new `market_price_cache` table, `groq/compound-mini` web search, narrowed to fuel + gold prices — the only commodities with one genuinely searchable canonical daily price in Saudi Arabia, unlike per-store retail produce which `fetch_live_deals` already covers). New `LiveMarketTicker.kt` component (glassmorphism, emerald/gold) with a manual refresh button and a distinct retry row when the live search comes back empty. **Known limitation, not a bug:** `groq/compound-mini` is non-deterministic — confirmed live that its `web_search` tool sometimes finds real data but drops it when formatting the final JSON; one internal server-side retry-on-empty + the strengthened "don't discard found data" prompt instruction improve the odds but can't eliminate the miss rate. The manual retry button is the primary mitigation, by design, not a stopgap.
- **PR #2 merge — `feat/ui-redesign` → `main` (2026-07-24):** glassmorphism/iOS visual overhaul (new `lottie-compose` dependency) across nearly every screen; two new screens — **Smart Pharmacy** (dose scheduling/reminders that survive reboot via `BootReceiver`, `zad_pharmacy_items`/`zad_dose_log`) and **Home Maintenance** (`zad_maintenance_items`); **Nearby Deals** (`OverpassRepo.kt`, OpenStreetMap/Overpass — zero-cost alternative to Google Places, foreground geofencing, new `ACCESS_FINE/COARSE_LOCATION` permissions); subscription auto-deduct (`due_day`/`auto_deduct` columns + scheduled worker); Gemini vision-only fallback for receipt/inventory scanning when OpenRouter's free vision model hits its daily cap; `groq/compound` → `compound-mini` fix for a hard 6000 TPM rate-limit wall on the two live-search actions. Backend (5 migrations + edge function changes) was already deployed to production before the PR merged — this was Android client code only. Dead `ZadAiProxyClient.kt` (510 lines) deleted in the same branch.
- **Phase 5 — Chat action-tag protocol (2026-07-21):** `ZadViewModel.sendAiChatMessage()`: chat is no longer read-only for inventory. AI can append a trailing `[[ACTION:{"type":"consume"|"add",...}]]` tag (rule 7 in the system prompt, explicit-intent-only) which `applyChatAction()` parses (org.json), fuzzy-matches against real inventory via `InventoryFlowEngine.namesMatch`, executes through the existing `consumeInventoryItem`/`injectScannedItems` mutation paths (so low-stock auto-replenish, shopping-list loop-closing, and Supabase sync all still apply), strips the raw tag before the message is shown/persisted, and appends a confirmation line. Unmatched items degrade to a safe "not found" message instead of silently failing.
- **Sprint 7 — Zad Intelligence Premium + Full-Context Chat + Kids UI (2026-07-20):**
  - `ZadCentralBrain.kt`: `BrainReport` extended with `SpendingPower` (safe daily spend gauge, 0-100 power%), `BehaviorProfile` (top spending day, weekend share%, avg transaction, impulse-purchase count, evening share%), `MonthComparison` (this month vs. same-period last month, per-category deltas) — all computed locally from real transactions, no extra AI calls. `buildExportText()` renders the full report as shareable plain text.
  - `ZadIntelligenceScreen.kt`: `SpendingPowerGaugeCard` (animated semicircle gauge), `MonthComparisonCard`, `BehaviorAnalysisCard` ("زاد يعرفك"), `ExportReportButton` (Android share sheet) — all wired into `AnalyticsTab`.
  - `ZadViewModel.buildFullChatContext()`: chat now injects full context into every message — inventory (+ depletion forecasts), last 30 transactions, per-category budgets, active subscriptions, shopping list, learned behavior patterns, the brain report, expense prediction, and family state (`updateFamilyContext`, fed from `FamilyViewModel.state` in `MainScreen`). Keeps last 8 messages as conversation memory.
  - `ZadAiRepository.suggestMealsForUrgentItems()` + `ZadViewModel.generateUrgentRecipes()`: brain-linked recipes — inventory items expiring within 2 days or predicted to deplete within 2 days trigger an AI recipe suggestion automatically, shown via `UrgentRecipeCard` on `HomeScreen`.
  - `BudgetScreen.kt`: per-category budget UI — `CategoryBudgetCard` list (progress bar, over-budget highlighting) + `CategoryBudgetEditDialog` to set/edit a category's monthly cap; spent amounts computed live from transactions (not just bank-auto-detected ones).
  - `MorningSummaryWorker.kt`: new daily WorkManager job (`PeriodicWorkRequestBuilder<MorningSummaryWorker>(24h)`, initial delay computed to next 7:00 AM) — single notification with spending power for the day + one urgent item (expiring/depleting/low health score).
  - `HomeScreen.kt` Kids Mode: cute redesign — `KidAvatar` (deterministic emoji+color per member, no photo needed) in greeting header, chat bubbles, and `FamilyScreen`'s parent-facing kids list; candy-gradient balance card, emoji-based chore cards with coin-chip rewards, playful empty states.
  - `supabase/migrations/20260720000000_create_affiliate_tables.sql`: **new** — `affiliate_products`/`affiliate_clicks`/`affiliate_catalog_requests` were referenced in app code (`SupabaseRepo`, `Models.kt`) but had no migration; added with RLS (catalog read-only for authenticated users, writes reserved for service_role).
- **Sprint 5 — iOS-grade animation system + Tasbiha fixes (2026-07-20):**
  - `ZadAnimations.kt`: unified animation toolkit — `ZadSprings` (iOS spring physics presets), `ZadTransitions` (RTL push/pop screen transitions), `Modifier.pressableScale()` (Apple-style press shrink + haptic), `shimmerLoading()`, `floatingIdle()`, `pulseGlow()`, `animatedCountAsState()`, `AppearOnEntry`
  - NavHost now uses ZadTransitions for all screen navigation (push/pop like iOS)
  - Tasbiha fixed: silent-return bug when no tree selected (now falls back to any tree or auto-creates "بستاني الأول"), streak logic corrected (consecutive-day +1, reset to 1 after gap, no infinite increment)
- **Sprint 4 — Profile/Subscriptions/Chat fixes (2026-07-20):**
  - ProfileSubScreens: fixed literal `\$budget` display bug (showed "$budget ر.س" instead of the number); alert switches now persist via `AlertPrefs` (SharedPreferences) and actually gate notifications (BudgetTracker + low-stock alerts respect them); dead "contact support" button now opens mailto intent
  - Subscription intelligence in BrainReport: annual cost projection, cancel suggestion when subs >20% of budget, duplicate streaming services detection
  - RealtimeChatRepo: malformed messages no longer kill the chat flow (mapNotNull + catch)
- **Sprint 2 — Closed-Loop Inventory (2026-07-20):**
  - `InventoryFlowEngine.kt`: smart injection (scanned items merge quantities into existing rows via fuzzy Arabic name matching), auto-marks matching shopping-list items purchased (closes the loop), `autoReplenish` (low-stock → auto-added to shopping with priority), `consumeItem` (− button feeds learning + triggers replenish)
  - `ConsumptionLearner`: records purchase/consumption dates per item, predicts days-to-depletion from average purchase interval
  - Periodic check-in: "هل خلص X؟" notifications for items predicted depleted (in `generateSmartNotifications`)
  - `ZadViewModel`: `injectScannedItems` / `consumeInventoryItem` / `runAutoReplenish` (+ Supabase sync via new `upsertInventory`)
  - CameraScreen: receipt & inventory-scan flows now use smart injection; InventoryScreen: +/− quantity stepper on cards; dead "تسوق" TODO button now wired to `runAutoReplenish`
- **Sprint 1 — Bank accuracy engine (2026-07-20):**
  - `SaBankParser` rewritten: noise filters (OTP/declined/promo ignored), explicit `TxType` enum (11 types incl. REFUND), labeled-amount extraction (`بمبلغ X` prioritized, `الرصيد X` excluded), Arabic-Indic digit normalization, no more default-expense guessing (ambiguous → AI fallback)
  - `TxDeduplicator`: blocks double-counting when the same tx arrives via SMS + bank-app notification (10-min fingerprint window)
  - `BudgetTracker` upgraded: per-category budget cards (`setCategoryBudget`/`getAllCategoryCards`), precise expense injection into the right card, refund handling, monthly reset resets category spend, two-level alerts (category overrun + total 75/90/100%)
  - Both `UnifiedBankListener` & `UnifiedSmsReceiver` wired to the new engine; unsafe raw-regex SMS fallback replaced with strict amount+type requirement
- **Sprint 1 — Professional Terms of Service:** `TermsContent.kt` (full English ToS v2.0: AI disclaimer, limitation of liability, third-party AI providers, indemnification); SignUpScreen dialog + consent row updated to English
- **Database RLS fix** — SECURITY DEFINER function `get_my_family_ids()` to break infinite recursion on `family_members` (FIX_DATABASE.sql)
- **Tasbiha independence** — `TasbihaHomeWidget` now navigates to Tasbiha route, not Family
- **Tasbiha splash screen** — Breathing tree animation, floating particles, glow effect, "ادخل البستان" button
- **Tasbiha game** — Tap-to-count with: emoji particles burst (✨🌟💫🕌🤲📿🍃💚), spring bounce animation, level-up celebration
- **Living tree** — Idle sway animation, tap rotation kick, pulsing glow ring, level-based background gradient
- **Sound effects** — ToneGenerator click on each tap (TONE_PROP_BEEP2), level-up sound (TONE_PROP_ACK), haptic feedback (VibrationEffect)

## ORPHANS & PENDING
- (2026-07-23: prior entries — affiliate-tables migration and missing core library desugaring — verified fixed and removed as of that date.)
- **2026-07-24, unverified by compile — read before trusting:** this session's Kotlin changes (Sprint 9 above: `MainActivity.kt`, `Models.kt`, `ZadAiRepository.kt`, `HomeScreen.kt`, `InventoryScreen.kt`, `ProfileScreen.kt`, `ProfileSubScreens.kt`, `ZadIntelligenceScreen.kt`, `ZadViewModel.kt`, new `LiveMarketTicker.kt`) were written and manually balance-checked (braces/parens) but **never compiled** — this sandbox has no Android SDK. Build in Android Studio before trusting further.
- **Zad Live Market Ticker reliability** — by design, not a bug (see Sprint 9 entry above): the ticker may frequently show its "couldn't load, try again" retry row rather than real prices, because `groq/compound-mini`'s web-search extraction is non-deterministic per call. If this remains unacceptably unreliable after real-device testing, the next lever to pull is either (a) narrowing further to gold-only (single most reliably searchable commodity), or (b) swapping the source entirely for a real financial-data API instead of agentic web search — not attempted this session, scope decision left to the user.
- `Screen.RecipeDetail` (`MainScreen.kt`) — declared route, no `composable()` registration, never navigated to anywhere. Dead, harmless, not cleaned up (out of scope of what was asked this session).
- `TermsOfService` screen (`MainScreen.kt`) — built and routed, but no button anywhere navigates to it. Orphaned entry point, not the dead-route problem (screen works fine if reached).
- Kids Mode chat-role gap (from the `feat/ui-redesign` merge, still open): a parent's manual "preview as kid" toggle doesn't propagate into `FamilyViewModel.sendMessage`'s role param, so a parent previewing Kids Mode still gets adult-tier AI replies in family chat. Real child accounts unaffected.

<!-- Update this file after every significant change -->
