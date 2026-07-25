# CLAUDE.md — Zad Project Rules

Project-specific rules for any Claude session working in this repo. Read alongside [PROJECT_MAP.md](./PROJECT_MAP.md) (architecture, data flow, sprint history).

## Facts to not get wrong

- **AI provider is OpenRouter + Groq split, not Groq-only.** The app's real AI function is `zad-core-intelligence` (not `zad-ai-proxy` — that one is dead code; the client never calls it outside a mocked androidTest). `zad-core-intelligence` runs nearly every AI feature — chat, insights, predictions, recipes — off `OPENROUTER_API_KEY` (model `openai/gpt-oss-20b:free` for text/JSON). `GROQ_API_KEY` is only used for Whisper audio transcription and the two `groq/compound` web-search actions (`fetch_live_deals`, `fetch_price_shock_warnings` — Deal Matcher / Price Radar). This corrects an earlier version of this file that said "Groq only"; see git history (`d478a97`, `8a9e950`) for the migration.
- **Vision/OCR (`analyze_receipt`, `analyze_inventory_image`) has a THIRD, separate server-side key**: `zad-core-intelligence` tries a server-side `GEMINI_API_KEY` (Gemini's own vision API, `generativelanguage.googleapis.com`, function `callGeminiVision`) as primary, falling back to OpenRouter's `nvidia/nemotron-nano-12b-v2-vl:free` (`callVisionModel`) only if that fails or the key is unset. This `GEMINI_API_KEY` is a **Supabase project secret**, unrelated to the client-side one described below — do not conflate them, and don't assume it's provisioned: it isn't in the "required secrets" list below, so verify before treating Gemini-primary as the live behavior; if unset, vision already runs on OpenRouter as this file used to say unconditionally.
- `ZadAiGeminiClient.kt` / `GEMINI_API_KEY` is a dormant optional client-side path (only activates if a user pastes a personal Gemini key into the camera screen) — despite the file's name, it actually calls Groq's vision endpoint directly, not Gemini. Never describe it as required, and never assume a feature is broken just because no Gemini key is configured — check whether it falls back to `zad-core-intelligence` first.
- Required secrets for a working build: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (`.env`, read via the Secrets Gradle Plugin into `BuildConfig`). `GEMINI_API_KEY` can stay a placeholder. Server-side, `OPENROUTER_API_KEY` and `GROQ_API_KEY` are Supabase project secrets, not app secrets.

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

- `docs/agent/ZAD_MASTER.md` — architecture, repo facts, settled decisions, tasks 1–14
- `docs/agent/NEXT_visible_progress.md` — current task order (supersedes the order in
  ZAD_MASTER; task contents unchanged). **Incomplete as of 2026-07-25** — truncated
  mid TASK 17.3, tail was never received.
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
- No LLM call on the UI thread or on screen open.
- Money stays `Double` with `asMoney()` rounding — no minor-units migration.
- No DI framework. Follow the existing `object SupabaseRepo` pattern.
- If a premise in the docs contradicts the code, stop and ask.

## Deliberately not adopted

Two skill sources the user shared were evaluated and intentionally not ported wholesale into this file — noting why so a future session doesn't wonder if they were missed:

- **Anthropic Cybersecurity Skills (817-skill red-team/blue-team library)**: mostly offensive tooling (C2 frameworks, exploit dev, phishing simulation) irrelevant to a Kotlin/Compose family finance app. The **Secure-coding checklist** above extracts the parts of it that actually apply to this codebase's real surface area instead.
- **claude-memory-skill**: this session already has a first-class memory system; installing a second, file-based one inside the project would just create two conflicting sources of persistent memory.
