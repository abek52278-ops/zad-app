# CLAUDE.md — Zad Project Rules

Project-specific rules for any Claude session working in this repo. Read alongside [PROJECT_MAP.md](./PROJECT_MAP.md) (architecture, data flow, sprint history).

## Facts to not get wrong

- **AI provider is Gemini (5-key pool, native `generateContent`) primary, Groq secondary for text/JSON only — and vision never touches Groq.** As of 2026-08-01 `zad-core-intelligence` (not `zad-ai-proxy` — that one is dead code; the client never calls it outside a mocked androidTest) runs every text/JSON action through `callGeminiPool()` first: `ZAD_API_KEY_1..5` (falling back to the legacy singular `GEMINI_API_KEY` when none of the five are set), round-robin cursor, immediate next-key retry on 429 or any failure, exhausting the pool inside one request. Models are tiered, not one-size-fits-all: `ZAD_MODEL_ROUTINE` (default `gemini-2.5-flash`) for vision/OCR/SMS-extraction/classification, `ZAD_MODEL_BRAIN` (default `gemini-2.5-pro`) for analysis. Only when every Gemini key fails does text/JSON fall through to `callGroqPool()` (`GROQ_API_KEY_1`/`GROQ_API_KEY_2`, falling back to singular `GROQ_API_KEY`, model `ZAD_GROQ_TEXT_MODEL`, default `llama-3.3-70b-versatile`). **`callVisionModel()` has no Groq fallback at all** — Groq rejects JSON mode on any request carrying an image, so a Groq image path can only 400, and the scan actions all need structured JSON. Direction has flipped in this file more than once (OpenRouter → Groq-primary → this); trust this bullet plus the file header, not older commits. `OPENROUTER_API_KEY` is inert here.
- **The repo and the deployed function can diverge, and did.** On 2026-08-01 deployed v89 already carried the Gemini pool but had *lost* the `nearby_pois` action, the hardened receipt/inventory vision prompts, and the "never suggest cancelling fixed obligations" guardrails that were in git. The current repo copy is the merge of both. Always diff `mcp__supabase__get_edge_function` output against `supabase/functions/…/index.ts` before assuming either side is authoritative.
- `ZAD_API_KEY_1..5` are deliberately the **same secret names `zad-brain` already reads** for its Gemini provider — one shared pool across both functions, not two separately-named sets. Legacy singular `GEMINI_API_KEY` is only a fallback now; if neither is set, vision has no provider at all and fails loudly in the logs rather than silently rerouting to Groq.
- `GROQ_API_KEY` (singular, no suffix) is *also* still used directly by `transcribeAudio()` (Whisper) and the two `groq/compound-mini` web-search actions (`fetch_live_deals`, `fetch_price_shock_warnings` — Deal Matcher / Price Radar) — those three were deliberately left out of the multi-key pool refactor (different tuning, different failure modes, live-diagnosed and documented at each call site) and don't rotate keys.
- `ZadAiGeminiClient.kt` is a dormant optional client-side path (only activates if a user pastes their own key into the camera screen). As of 2026-08-01 the name is finally accurate: it calls Gemini's native `generateContent`, not Groq, and accepts several comma/space/newline-separated keys in the one stored string (`gemini_api_key` in SharedPreferences), rotating on 429. A stale **Groq** key left there by an older build simply fails and falls through to `zad-core-intelligence`. Never describe this path as required, and never assume a feature is broken just because no personal key is configured.
- Required secrets for a working build: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (`.env`, read via the Secrets Gradle Plugin into `BuildConfig`). `GEMINI_API_KEY` can stay a placeholder. Server-side, `ZAD_API_KEY_1..5` (or legacy singular `GEMINI_API_KEY`), `GROQ_API_KEY_1`/`GROQ_API_KEY_2` (or the original singular `GROQ_API_KEY`), and `GROQ_API_KEY` are Supabase project secrets, not app secrets. `OPENROUTER_API_KEY` is no longer required by `zad-core-intelligence` (still used by `zad-brain`'s `ZAD_PROVIDER=openai_compatible` + `ZAD_BASE_URL` config, a separate function/config path — see `docs/agent/`).

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

**Read `docs/agent/SESSION_2026_07_30_phaseA.md` before picking up any Task 25+ work,
or any Phase A/C item** — it's the running status file for tasks 25-28 and for
PRODUCT_PLAN.md's Phase A/C generally (what's done, what's left, in what order, and a
critical note on what's committed to git vs. actually deployed to Supabase). **Tasks
25-28 span two phases, not one** — despite the filename, Task 25/26 = Phase A (A2/A3),
Task 27/28 = Phase C (C2+C3/C1); the file itself documents and corrects this mislabel.
**Phase A is not closed**: A1/A2/A3/A4/A5 are done, but **A6 (drop `RECEIVE_SMS`,
PRODUCT_PLAN.md §5's Play Store compliance risk) is still open** — verified live in
`AndroidManifest.xml`/`UnifiedSmsReceiver.kt` as of 2026-07-30, not carried forward from
an old note. Don't describe Phase A as done until A6 lands. Update the session file
(don't just append to PROGRESS.md) whenever a Task 25+ item or Phase A/C item lands, so
a fresh session — this file is auto-loaded every time, no manual paste needed — starts
already knowing where things stand instead of re-deriving it from commit history.
`SESSION_2026_07_26_epic19.md` is archived (Epic 1+4, tasks 19-24, closed); Epic 2
(`EPIC_2_ai_screen.md`) closed 2026-07-30.

- `docs/agent/ZAD_MASTER.md` — architecture, repo facts, settled decisions, tasks 1–14
- `docs/agent/NEXT_visible_progress.md` — current task order (supersedes the order in
  ZAD_MASTER; task contents unchanged). **Incomplete as of 2026-07-25** — truncated
  mid TASK 17.3, tail was never received.
- `docs/agent/PRODUCT_PLAN.md` — **the why and the order.** Phases A–D; §3's own table is
  the authority on which task belongs to which phase — don't infer it from task numbers
  or from other docs' labels (this file's own Phase A/B/C labeling was wrong for tasks
  27-28 until 2026-07-30, see the correction above). Tasks 25/26 = Phase A (A2/A3,
  salary cycle / committed obligations-"available"); tasks 27/28 = Phase C (C2+C3/C1,
  visible confidence / informative dismissal). Read this before picking up any task — it
  sets priority across all the files below.
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
