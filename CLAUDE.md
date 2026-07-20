# CLAUDE.md — Zad Project Rules

Project-specific rules for any Claude session working in this repo. Read alongside [PROJECT_MAP.md](./PROJECT_MAP.md) (architecture, data flow, sprint history).

## Facts to not get wrong

- **AI provider is Groq only.** The `zad-ai-proxy` Supabase Edge Function runs every AI feature — chat, insights, predictions, and photo/receipt scanning via the vision model `meta-llama/llama-4-scout-17b-16e-instruct` — off a single `GROQ_API_KEY` set in Supabase project secrets. There is no server-side Gemini dependency.
- `ZadAiGeminiClient.kt` / `GEMINI_API_KEY` is a dormant optional client-side path (only activates if a user pastes a personal Gemini key into the camera screen). Never describe it as required, and never assume a feature is broken just because no Gemini key is configured — check whether it falls back to `callVisionEdge`/`zad-ai-proxy` first.
- Required secrets for a working build: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (`.env`, read via the Secrets Gradle Plugin into `BuildConfig`). `GEMINI_API_KEY` can stay a placeholder.

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

## Deliberately not adopted

Two skill sources the user shared were evaluated and intentionally not ported wholesale into this file — noting why so a future session doesn't wonder if they were missed:

- **Anthropic Cybersecurity Skills (817-skill red-team/blue-team library)**: mostly offensive tooling (C2 frameworks, exploit dev, phishing simulation) irrelevant to a Kotlin/Compose family finance app. The **Secure-coding checklist** above extracts the parts of it that actually apply to this codebase's real surface area instead.
- **claude-memory-skill**: this session already has a first-class memory system; installing a second, file-based one inside the project would just create two conflicting sources of persistent memory.
