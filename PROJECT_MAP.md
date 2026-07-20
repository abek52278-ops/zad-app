# PROJECT_MAP — Zad Family Manager

## TECH_STACK
- **Frontend:** Android (Kotlin, Compose UI, Material3)
- **Backend:** Supabase (PostgreSQL, PostgREST, Realtime, Auth, Edge Functions)
- **AI:** Groq (Llama 3.3 70B via Edge Function `zad-ai-proxy`)
- **AI Client SDK:** Groq SDK (npm) — NOT directly called; routed through Supabase Edge Functions
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
App (ZadAiProxyClient) → Edge Function `zad-ai-proxy` → Groq API
     ↓                                                         ↓
  {request_type, payload}                               {text, insights, ...}
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

### Edge Functions
| Function | Endpoints | Status |
|----------|-----------|--------|
| zad-ai-proxy | chat, agent_summary, meal_suggestions, recipe_details, grocery_suggestions, spending_insights, receipt_analysis, inventory_scan, bank_sms_parsing, subscription_detection, ai_text, brain_evaluate, voice_agent, expense_prediction, auto_suggest, family_analysis, family_goals_suggest, behavior_learning, smart_search, cash_flow_prediction, anomaly_detection, price_estimate | ✅ 22/22 |

### Key Architecture Decisions
1. **Room as single source of truth** for personal data (sync from Supabase)
2. **No Room for family data** — direct Supabase queries + Realtime
3. **Optimistic UI** for mutations (update local state first, then remote)
4. **SECURITY DEFINER function** `get_my_family_ids()` to break RLS recursion
5. **ZadCentralBrain** unified AI brain with behavior learning + predictions

## COMPLETED FEATURES
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
- none currently

<!-- Update this file after every significant change -->
