# CLAUDE.md — Zad Project Rules

Project-specific rules for any Claude session working in this repo. Read alongside [PROJECT_MAP.md](./PROJECT_MAP.md) (architecture, data flow, sprint history).

## Facts to not get wrong

- **AI provider is Groq (multi-key pool) primary, Gemini fallback — not OpenRouter.** As of 2026-07-25 `zad-core-intelligence` (not `zad-ai-proxy` — that one is dead code; the client never calls it outside a mocked androidTest) runs every text/JSON/vision action through `callGroqPool()`: `GROQ_API_KEY_1`/`GROQ_API_KEY_2` (falls back to the original singular `GROQ_API_KEY` if `_1` is unset), round-robin, immediate next-key retry on 429 or failure, model `ZAD_GROQ_TEXT_MODEL` (default `llama-3.3-70b-versatile`) for text/JSON and `ZAD_GROQ_VISION_MODEL` (default `llama-3.2-11b-vision-instruct`, **not live-verified against Groq's current catalog** — Groq has pulled vision models before over licensing) for vision. Only when the whole Groq pool fails does it fall through to `GEMINI_API_KEY` via Gemini's OpenAI-compatible endpoint (`ZAD_GEMINI_FALLBACK_MODEL`, default `gemini-2.0-flash`) — one shared `callOpenAICompatibleChat()` helper for both Groq and Gemini, not three separate implementations. `OPENROUTER_API_KEY` is no longer read by any of these three functions; if it's still set as a secret, it's now inert here. This corrects an earlier version of this file that described OpenRouter-primary/Gemini-fallback (text) and Gemini-primary/OpenRouter-fallback (vision) — see git history (`d478a97`, `8a9e950`, and the 2026-07-25 Groq-pool refactor commit) for the full migration.
- `GEMINI_API_KEY` is the **same Supabase project secret** used by both the Gemini fallback above and the dormant client-side path below — one key, two call sites, not two separate keys. Don't assume it's provisioned: it isn't in the "required secrets" list below, so verify before treating the Gemini fallback as reachable; if unset, `callGeminiFallback()` logs and returns null instead of erroring, and a fully-exhausted Groq pool with no Gemini key means the action genuinely fails.
- `GROQ_API_KEY` (singular, no suffix) is *also* still used directly by `transcribeAudio()` (Whisper) and the two `groq/compound-mini` web-search actions (`fetch_live_deals`, `fetch_price_shock_warnings` — Deal Matcher / Price Radar) — those three were deliberately left out of the multi-key pool refactor (different tuning, different failure modes, live-diagnosed and documented at each call site) and don't rotate keys.
- `ZadAiGeminiClient.kt` / `GEMINI_API_KEY` is a dormant optional client-side path (only activates if a user pastes a personal Gemini key into the camera screen) — despite the file's name, it actually calls Groq's vision endpoint directly, not Gemini. Never describe it as required, and never assume a feature is broken just because no Gemini key is configured — check whether it falls back to `zad-core-intelligence` first.
- Required secrets for a working build: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (`.env`, read via the Secrets Gradle Plugin into `BuildConfig`). `GEMINI_API_KEY` can stay a placeholder. Server-side, `GROQ_API_KEY_1`/`GROQ_API_KEY_2` (or the original singular `GROQ_API_KEY`), `GROQ_API_KEY`, and `GEMINI_API_KEY` are Supabase project secrets, not app secrets. `OPENROUTER_API_KEY` is no longer required by `zad-core-intelligence` (still used by `zad-brain`'s `ZAD_PROVIDER=openai_compatible` + `ZAD_BASE_URL` config, a separate function/config path — see `docs/agent/`).

## Response style

Default to terse, high-signal replies once a plan is executing — state what changed and why, skip preamble and restating the request back. Full explanations are for genuinely new architectural decisions, not routine edits. Never fabricate build/test results — this environment has no Android SDK, so say so explicitly instead of claiming a compile succeeded.

## Mobile UI/UX rules (Compose)

Applies to every screen/composable added or touched, adapted from the `mobile-app-ui-design` skill for Jetpack Compose (not Tailwind/React):

- **Spacing**: values divisible by 4 or 8 (`4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp...`). Related elements closer together than unrelated ones — don't space everything uniformly.
- **Typography**: use the existing `Typography` scale (`ui/theme/Type.kt`) — don't hardcode ad-hoc `fontSize`s. Build hierarchy with weight/size/opacity, not by bolding everything.
- **Color**: reuse tokens from `ui/theme/Color.kt` (already follows a 60/30/10-ish split: neutral surfaces, `primary`/`secondary` as accents). New one-off hex colors are fine for illustrative/gamified UI (Kids Mode, category icons) but should stay inside that screen, not leak into shared components.
- **Thumb zone**: primary actions (FABs, main CTAs) in the bottom third of the screen; this app already does this consistently — keep it.
- **Empty/error/loading states**: every list-backed screen needs a real empty state (icon + short guidance), not a blank `LazyColumn`. Match the existing pattern (`Icon` + title + subtitle, see `BudgetScreen`'s empty transactions state).
- **Motion as feedback**: use `animateFloatAsState`/`AnimatedVisibility` for state changes (already the house style via `ZadAnimations.kt`) — reuse `ZadSprings`/`ZadTransitions` instead of inventing new tween curves per screen.
- **Tap targets** ≥ 44dp (Material default `IconButton` size already satisfies this — don't shrink below it for density).
- Kids Mode specifically: playful gradients/emoji are intentional there (see `KidAvatar`, candy-gradient balance card) — don't "normalize" it to the adult palette.

## Secure-coding checklist

Scoped to this app's actual attack surface (Android client + Supabase backend + AI chat) — not a general pentesting checklist:

- **RLS is the security boundary, not client-side filtering.** Every new Supabase table needs RLS enabled before it ships (the `affiliate_products`/`affiliate_clicks`/`affiliate_catalog_requests` tables were missing this for a while — see `supabase/migrations/20260720000000_create_affiliate_tables.sql`). Use `get_my_family_ids()` for family-scoped tables, never trust `family_id` passed from the client alone.
- **Never commit secrets.** `.env`, `local.properties`, keystores are gitignored — keep it that way. Supabase URL appearing in docs is low-risk (RLS protects data); anon keys and service-role keys are not, and service-role keys must never leave the Edge Function environment.
- **AI prompt injection**: user-controlled text (chat messages, OCR'd receipt/inventory text, SMS content) flows into system prompts (`ZadViewModel.buildFullChatContext`, `SaBankParser`). Keep injected data inside clearly delimited `=== SECTION ===` blocks and keep the system prompt's instructions authoritative over anything inside those blocks — never let user/OCR text redefine the assistant's rules. Don't relax this for convenience.
- **Signing/release secrets** (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) are env-var-only, sourced from CI secrets or a local, gitignored keystore — never hardcode.
- **Dependency changes**: this project pulls Compose BOM, AGP, and Kotlin at fairly recent pinned versions (`gradle/libs.versions.toml`) — bump deliberately, not opportunistically, and check `compileSdk`/`minSdk` compatibility before raising `minSdk` cavalierly (there's already a real gap: `java.time.*` is used pervasively with `minSdk 24` and no core library desugaring — treat this as a known open bug, not something to shrug off if touched).

## Project plan and task numbering

The full plan lives in `docs/agent/`. Read `ZAD_MASTER.md` first — it is the single
source of truth for architecture, settled decisions, and task numbering. Task numbers
used in conversation (Task 9, Task 17.2, etc.) refer to that file.

**Read `docs/agent/SESSION_2026_07_30_phaseA.md` before picking up any Task 25+ work** —
it's the running status file for the current phase (PRODUCT_PLAN.md Phase A, tasks
25-28; what's done, what's left, in what order, and a critical note on what's committed
to git vs. actually deployed to Supabase). Update it (don't just append to PROGRESS.md)
whenever a Task 25+ item lands, so a fresh session — this file is auto-loaded every
time, no manual paste needed — starts already knowing where things stand instead of
re-deriving it from commit history. `SESSION_2026_07_26_epic19.md` is archived (Epic
1+4, tasks 19-24, closed); Epic 2 (`EPIC_2_ai_screen.md`) closed 2026-07-30.

- `docs/agent/ZAD_MASTER.md` — architecture, repo facts, settled decisions, tasks 1–14
- `docs/agent/NEXT_visible_progress.md` — current task order (supersedes the order in
  ZAD_MASTER; task contents unchanged). **Incomplete as of 2026-07-25** — truncated
  mid TASK 17.3, tail was never received.
- `docs/agent/PRODUCT_PLAN.md` — **the why and the order.** Phases A–D, tasks 25–28
  (salary cycle, committed obligations/"available", visible confidence, informative
  dismissal). Read this before picking up any task — it sets priority across all the
  files below.
- `docs/agent/EPIC_1_4.md` — tasks 19–24 (cash ledger + ATM transfer bug, dedupe config,
  Egypt SMS, habit chips, inventory stagnation, consistency audit). Includes an explicit
  **rejected-proposals** section (knowledge graph, multi-agent endpoint split) — do not
  reimplement those under another name.
- `docs/agent/15_family_alerts.md` — Task 15 (**not yet in the repo** — referenced but
  never pasted; ask the user for it before assuming Task 15 content)
- `docs/agent/16_validation_and_recovery.md` — Task 16 (**not yet in the repo**, same as above)
- `docs/agent/17_2_pharmacy_fix.md` — Task 17.2 (**not yet in the repo**, same as above —
  Task 17.2's actual pharmacy-dosage fix already landed in code per
  `docs/agent/PROGRESS.md`; this file would only be the written spec for it)
- `docs/agent/PROGRESS.md` — append-only log, one entry per completed task

Standing rules:
- One task, one commit, one report, then stop.
- **Never report a task complete without build output from a run that happened AFTER
  the changes.** This has already failed once: Task 9 and 17.2 were reported complete
  while the build was broken by three missing imports.
- No LLM call on the UI thread or on screen open. **Explicit exception: `HomeScreen`**
  (`LaunchedEffect(Unit)` firing `refreshAgentSummary`/`refreshAutoSuggestions`/
  `predictNextMonthExpenses`/`refreshLiveMarketPrices` on every open) — flagged in
  `docs/agent/AUDIT.md` as this rule's worst violation by call frequency, but the user
  explicitly decided (2026-07-30, closing Epic 2) to keep it: instant/live AI cards on
  the app's most-visited screen are the intended UX, not an oversight. Don't "fix" this
  without asking first. The other five screens AUDIT.md flagged for the same rule
  (`ZadIntelligenceScreen`, `ShoppingListScreen`, `WeeklyReportScreen`,
  `NearbyDealsScreen`, and `SubscriptionsScreen`'s/`ZadIntelligenceScreen`'s
  `detectSubscriptions()`) are unaffected by this exception — `detectSubscriptions()`
  specifically got its own confirm-before-write fix (2026-07-30) and still shouldn't
  auto-fire destructively even though it may still fire the read-only detection call.
- Money stays `Double` with `asMoney()` rounding — no minor-units migration.
- No DI framework. Follow the existing `object SupabaseRepo` pattern.
- If a premise in the docs contradicts the code, stop and ask.

## Deliberately not adopted

Two skill sources the user shared were evaluated and intentionally not ported wholesale into this file — noting why so a future session doesn't wonder if they were missed:

- **Anthropic Cybersecurity Skills (817-skill red-team/blue-team library)**: mostly offensive tooling (C2 frameworks, exploit dev, phishing simulation) irrelevant to a Kotlin/Compose family finance app. The **Secure-coding checklist** above extracts the parts of it that actually apply to this codebase's real surface area instead.
- **claude-memory-skill**: this session already has a first-class memory system; installing a second, file-based one inside the project would just create two conflicting sources of persistent memory.
