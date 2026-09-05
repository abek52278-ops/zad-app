# CLAUDE.md — Zad Project Rules

Project-specific rules for any Claude session working in this repo. Read alongside [PROJECT_MAP.md](./PROJECT_MAP.md) (architecture, data flow, sprint history).

## Facts to not get wrong

- **AI provider is Gemini (5-key pool, native `generateContent`) primary, Groq secondary for text/JSON only — and vision never touches Groq.** As of 2026-08-01 `zad-core-intelligence` (not `zad-ai-proxy` — that one is dead code; the client never calls it outside a mocked androidTest) runs every text/JSON action through `callGeminiPool()` first: `ZAD_API_KEY_1..5` (falling back to the legacy singular `GEMINI_API_KEY` when none of the five are set), round-robin cursor, immediate next-key retry on 429 or any failure, exhausting the pool inside one request. Models are tiered by *thinking*, not by model name: `ZAD_MODEL_ROUTINE` (vision/OCR/SMS-extraction/classification, thinking **off** via `thinkingConfig.thinkingBudget: 0`) and `ZAD_MODEL_BRAIN` (analysis, thinking on). Both default to **`gemini-3.5-flash`** and both are set to it as project secrets — **the secret wins over the code default, so changing the default in code changes nothing on a deployed project.** Verified live 2026-08-01 against this project's own keys: `gemini-2.5-flash` **404s** ("no longer available to new users" — it still appears in ListModels, it just cannot be called), `gemini-2.5-pro` 429s on the first key (free pro quota is too thin to be a default), `gemini-3.5-flash` answers. Thinking-off matters: Gemini 2.5+ bills thinking tokens against `maxOutputTokens`, so a thinking model can burn the whole budget and return a candidate with no text part — a 200 that reads as an empty scan. `callGeminiNative` now logs `finishReason` + `usageMetadata` whenever that happens. Only when every Gemini key fails does text/JSON fall through to `callGroqPool()` (`GROQ_API_KEY_1`/`GROQ_API_KEY_2`, falling back to singular `GROQ_API_KEY`, model `ZAD_GROQ_TEXT_MODEL`, default **`openai/gpt-oss-120b`** as of 2026-08-31 — see the Groq-catalogue bullet below; the old `llama-3.3-70b-versatile` default 404s now). **`callVisionModel()` has no Groq fallback at all** — Groq rejects JSON mode on any request carrying an image, so a Groq image path can only 400, and the scan actions all need structured JSON. Direction has flipped in this file more than once (OpenRouter → Groq-primary → this); trust this bullet plus the file header, not older commits. `OPENROUTER_API_KEY` is inert here.
- **The repo and the deployed function can diverge, and did.** On 2026-08-01 deployed v89 already carried the Gemini pool but had *lost* the `nearby_pois` action, the hardened receipt/inventory vision prompts, and the "never suggest cancelling fixed obligations" guardrails that were in git. The current repo copy is the merge of both. Always diff `mcp__supabase__get_edge_function` output against `supabase/functions/…/index.ts` before assuming either side is authoritative.
- `ZAD_API_KEY_1..5` are deliberately the **same secret names `zad-brain` already reads** for its Gemini provider — one shared pool across both functions, not two separately-named sets. Legacy singular `GEMINI_API_KEY` is only a fallback now; if neither is set, vision has no provider at all and fails loudly in the logs rather than silently rerouting to Groq.
- **Quota is per key AND per model — both multiply (measured 2026-08-15).** `ZAD_API_KEY_1..5` sit in **five different Google Cloud projects**, proven by saturating key 1's per-minute bucket on `gemini-3.5-flash` until it returned 429 and then immediately calling keys 2–5 on the same model: all four answered **200**. So capacity is `5 keys × N models × per-model quota`, and both the key pool and the model chain are real multipliers. (An earlier note in this file claimed the pool bought nothing — that was wrong. The reason all five keys reported `limit: 20` in the 2026-08-15 failure log is that all five had genuinely spent their own 20 that day, i.e. ~100 requests, not that they shared one bucket.) Measured free-tier rates on this project: `gemini-3.5-flash` = **5 RPM / 20 RPD per key**; `gemini-3.5-flash-lite` and `gemini-3.1-flash-lite` = **15 RPM per key** (their RPD was not measured — no `...PerDay...` 429 appeared in ~30 calls). **No model on this project offers the 1,500 RPD figure**; that was `gemini-1.5-flash`'s old free tier and that model 404s here. The keys are genuinely five distinct secrets and the rotation code was always correct; neither was ever the bug.
- **`ListModels` lists models you cannot call — never pick a model from it alone.** All five keys return an identical 38-model catalogue, and `gemini-2.5-flash` / `gemini-2.5-flash-lite` are *in it* while `generateContent` on them answers 404 "no longer available to new users". Always probe `generateContent` (with `functionDeclarations` attached, if the caller needs tools) before adopting a name. `zad-brain/callModel.ts` now walks a **model chain** (each model = a fresh bucket), then falls out to **Groq** (`openai/gpt-oss-120b`, verified to emit tool_calls) as the last leg. 429-on-every-key and 503 both raise `ProviderUnavailableError`, which steps to the next model instead of burning backoff on a wall. Covered by `callModel_failover_test.ts` — don't change the chain without it.
- **Groq dropped every Llama chat model, and that silently killed the fallback (fixed 2026-08-31).** `llama-3.3-70b-versatile` — the default in `zad-brain/callModel.ts`, `zad-core-intelligence/index.ts`, and a hardcoded one in `amazon-creators-search/index.ts` — now answers **404 `model_not_found`** on this project's key. It is not a quota wall or a deprecation warning; the model is simply not in the catalogue any more. Because it sat at the *end* of every chain, nothing surfaced the breakage: a Gemini-wide 503 or daily-quota wipe went straight from "try Groq" to a hard turn failure. The full catalogue that day was `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `openai/gpt-oss-safeguard-20b`, `qwen/qwen3.6-27b`, `qwen/qwen3.8-27b`, `groq/compound`, `groq/compound-mini`, `whisper-large-v3(-turbo)`, `allam-2-7b`, `meta-llama/llama-prompt-guard-2-22m/86m` (a guard classifier, not a chat model) — **no Llama chat model at all**. New default everywhere: **`openai/gpt-oss-120b`**, probed live the same day for both things these call sites need — it emits real `tool_calls` (returned `add_expense{amount:50,category:"قهوة"}` for "سجل 50 جنيه قهوة", so it can carry the agent loop) and it honours `response_format: json_object` (what `callGroqPool`'s `jsonMode` needs). ⚠️ **`ZAD_GROQ_TEXT_MODEL` is a Supabase project secret and the secret outranks the code default** — same trap as `ZAD_MODEL_ROUTINE`. If that secret is currently set to the Llama name, this commit changes nothing in production; unset it or set it to `openai/gpt-oss-120b`. Also note `amazon-creators-search` is **not in `edge-functions.yml`'s check-or-deploy list**, so its copy of the fix does not ship from CI.
- **Re-probed 2026-08-31, Gemini side unchanged and still pinned to `gemini-3.5-flash`:** all six leading chain models — `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`, `gemini-flash-lite-latest`, `gemini-3-flash-preview`, `gemini-flash-latest`, `gemini-3.5-flash` — answered **200 with a `functionCall` part**. `ZAD_MODEL_ROUTINE`/`ZAD_MODEL_BRAIN` both already default to `gemini-3.5-flash`; no change was needed. The one exception is the chain's tail `gemini-3.6-flash`, which on 2026-08-31 returned **503 once and then hung past a 25s timeout** — harmless where it sits (last leg, after six working models) but do not promote it, and note that a hang costs more than a 404 does.
- **Model facts, probed live against this project 2026-08-15 (not from docs):** `gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-2.0-flash-lite`, `gemini-2.5-flash`, `gemini-2.5-flash-lite` **all 404** — retired or closed to new users. Do not "restore" them. Working with tool-calling: `gemini-3.5-flash-lite` (0 thought tokens, 0.57s), `gemini-3.1-flash-lite`, `gemini-flash-lite-latest`, `gemini-3-flash-preview`, `gemini-flash-latest`, `gemini-3.5-flash`; `gemini-3.7-flash` was 503. **`thinkingConfig` is not universally accepted**: `gemini-3.5-flash-lite` and `gemini-flash-lite-latest` answer **400 INVALID_ARGUMENT** if the field is present at all, while `gemini-3.1-flash-lite` accepts it — the "-lite" suffix predicts nothing. `sendGemini` therefore learns rejection at runtime and retries once without the field. Thinking is **off by default** because thought tokens bill against `maxOutputTokens`: `gemini-3.5-flash` spent 231 of them on "سجل 50 جنيه قهوة" and returned `finishReason: MAX_TOKENS`.
- **The agent loop's model is `ZAD_MODEL_AGENT`, not `ZAD_MODEL_ROUTINE`** (default `gemini-3.5-flash-lite`). They were split deliberately: `ZAD_MODEL_ROUTINE` is shared with `zad-core-intelligence`'s vision/OCR path, and whether a lite model reads a blurry receipt as well was **not** tested — so that secret's value is untouched.
- `GROQ_API_KEY` (singular, no suffix) is *also* still used directly by `transcribeAudio()` (Whisper) and the two `groq/compound-mini` web-search actions (`fetch_live_deals`, `fetch_price_shock_warnings` — Deal Matcher / Price Radar) — those three were deliberately left out of the multi-key pool refactor (different tuning, different failure modes, live-diagnosed and documented at each call site) and don't rotate keys.
- `ZadAiGeminiClient.kt` is a dormant optional client-side path (only activates if a user pastes their own key into the camera screen). As of 2026-08-01 the name is finally accurate: it calls Gemini's native `generateContent`, not Groq, and accepts several comma/space/newline-separated keys in the one stored string (`gemini_api_key` in SharedPreferences), rotating on 429. A stale **Groq** key left there by an older build simply fails and falls through to `zad-core-intelligence`. Never describe this path as required, and never assume a feature is broken just because no personal key is configured.
- Required secrets for a working build: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (`.env`, read via the Secrets Gradle Plugin into `BuildConfig`). `GEMINI_API_KEY` can stay a placeholder. Server-side, `ZAD_API_KEY_1..5` (or legacy singular `GEMINI_API_KEY`), `GROQ_API_KEY_1`/`GROQ_API_KEY_2` (or the original singular `GROQ_API_KEY`), and `GROQ_API_KEY` are Supabase project secrets, not app secrets. `OPENROUTER_API_KEY` is no longer required by `zad-core-intelligence` (still used by `zad-brain`'s `ZAD_PROVIDER=openai_compatible` + `ZAD_BASE_URL` config, a separate function/config path — see `docs/agent/`).

## Response style

Default to terse, high-signal replies once a plan is executing — state what changed and why, skip preamble and restating the request back. Full explanations are for genuinely new architectural decisions, not routine edits. Never fabricate build/test results. **Toolchain presence varies by container — check, don't assume.** This section has flipped more than once because each revision recorded one container as if it were permanent. On 2026-09-04 a fresh Codespace had **neither** Deno **nor** an Android SDK: no `~/.deno`, no `/home/codespace/android-sdk`, no `local.properties`, `ANDROID_HOME` unset. Earlier notes here asserting both were present ("`deno 2.9.5`, confirmed 2026-08-16"; "SDK is now present, confirmed 2026-09-03") were true of those containers only. Verify before relying on either, and if missing, install rather than skipping the check — network to `deno.land`, `dl.google.com` and `services.gradle.org` works, and both installs succeeded in ~10 minutes total on 2026-09-04:

- **Deno**: `curl -fsSL https://deno.land/install.sh | DENO_INSTALL=$HOME/.deno sh`. Then `deno check`/`deno test` on `supabase/functions/` run locally, and there is no excuse for pushing an edge-function change unverified.
- **Android SDK**: `commandlinetools-linux` → `sdkmanager --sdk_root=$HOME/android-sdk "platform-tools" "platforms;android-36" "build-tools;36.0.0"` (matching `compileSdk = 36`), then write `local.properties` with `sdk.dir=$HOME/android-sdk` (gitignored). **AGP 9.1.1 needs JDK 17+** — a JDK 21 already sits at `/usr/local/sdkman/candidates/java/21.0.10-ms` and Gradle picks it up on its own; `java -version` may still report the JDK 11 on `PATH`, which is not what the build uses.
- **Heap**: `gradle.properties` declares `org.gradle.jvmargs` **twice** (lines 2 and 15) and the later one wins at `-Xmx4096m`, which a container with ~2-3 GB free cannot honour — the daemon is OOM-killed and Gradle reports "Gradle build daemon disappeared unexpectedly" or "Connection to the Kotlin daemon has been unexpectedly lost". Neither message names memory; both mean it. Override per-invocation rather than editing the shared file. Use **two small JVMs, not one large one** — the split survives a tight container where a single big heap does not: `./gradlew compileDebugKotlin -Dorg.gradle.jvmargs="-Xmx1100m -XX:MaxMetaspaceSize=450m" -Dkotlin.daemon.jvmargs="-Xmx800m -XX:MaxMetaspaceSize=350m" --no-daemon`. That produced real `BUILD SUCCESSFUL` runs repeatedly on 2026-09-04/05 (`compileDebugKotlin` ~3 min, `assembleDebug` ~5 min). Two things that look like fixes and are not: `-Pkotlin.compiler.execution.strategy=in-process` needs one *larger* heap and failed more often than it worked, and a bigger `-Xmx` makes the OOM more likely, not less. If a build dies repeatedly, check `free -h` first — an orphaned Kotlin daemon from a killed build holds ~1 GB and will starve the next run; find it with `ps -eo pid,rss,args --sort=-rss | grep java` and kill it **by PID**. Do not `pkill -f GradleDaemon`: the pattern matches the shell running it and kills your own command (exit 144).

**`./gradlew compileDebugKotlin` and `./gradlew assembleDebug` are mandatory before reporting any Kotlin/Compose task complete or committing it** — run both (or `assembleDebug` alone, since it includes compilation) and quote the actual `BUILD SUCCESSFUL`/`BUILD FAILED` result; a task is not done on the strength of "the edit looks right." `compileDebugKotlin` is the fast check for iterating; `assembleDebug` is the fuller one (also runs resource merging/packaging) and is the one to run right before a commit. Never claim a compile succeeded without having actually run one this session, and never skip straight to "let CI be the check" now that local compilation works — CI remains the source of truth for `testDebugUnitTest`/`lintDebug` and for anything this sandbox can't reproduce (no emulator/KVM — see `run-zad-app` skill for the Robolectric/Roborazzi screenshot-test workaround), but it is a supplement to a local build now, not a replacement for one.

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
- **Dependency changes**: this project pulls Compose BOM, AGP, and Kotlin at fairly recent pinned versions (`gradle/libs.versions.toml`) — bump deliberately, not opportunistically, and check `compileSdk`/`minSdk` compatibility before raising `minSdk` cavalierly. **The old "`java.time` with `minSdk 24` and no desugaring" bug is fixed and this note was stale** — `app/build.gradle.kts` has `isCoreLibraryDesugaringEnabled = true` plus `desugar_jdk_libs:2.1.4`, and the debug APK was verified with `dexdump` on 2026-08-01: 225 `Lj$/time/*` classes are shipped, zero `java/time/*` classes are defined, and app bytecode references `Lj$/time/LocalDate;` 696 times with no un-rewritten `Ljava/time/` call site. Desugaring covers `java.*` only — **`android.*` APIs above 24 still need explicit `Build.VERSION.SDK_INT` guards**, and lint's `NewApi` is the check that catches them (it caught `VibrationEffect.createOneShot` in `TasbihaScreen`, which a `catch (Exception)` could not protect because a missing class throws `NoClassDefFoundError`, an `Error`). Don't baseline a `NewApi` or `MissingPermission` error — both are runtime crashes or silently dead features, not style nits.

## Deployment (changed 2026-08-15 — read this before hand-deploying anything)

- **Edge functions and migrations deploy from CI, not by hand.** `edge-functions.yml`
  has a `deploy` job gated on `needs: check`, so nothing that fails `deno check` or the
  tests reaches production. It runs on a push to `main` or a manual `workflow_dispatch`,
  and pushes migrations in the same job so schema and the code depending on it never
  arrive separately. This is the fix for the repo-vs-deployed drift documented above —
  that bullet's advice to diff before trusting either side is still worth doing, but the
  cause (deploying as a manual step disconnected from the checks) is closed.
- Needs two repository secrets: `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`. Missing
  either leaves `main` green and **skips the deploy** with a warning annotation and a run
  summary line — deliberately visible, since a silently skipped deploy is how the drift
  started. The project ref comes from `supabase/config.toml`, not a secret.
- **`supabase/config.toml` declares `verify_jwt` per function and that is load-bearing.**
  `supabase functions deploy` applies these on every deploy and defaults to `true` for
  anything undeclared. `zad-brain` and `zad-telegram-bot` both run with it **off** on
  purpose — Telegram's webhook sends no `Authorization` header at all — so an undeclared
  deploy would switch them on and kill the bot. Never remove those entries.
- The Supabase CLI version is **pinned** in the workflow. `version: latest` makes the
  setup action query the GitHub API unauthenticated and it failed with `rate limit
  exceeded` on the first real deploy. Bump it as a deliberate commit.
- **The migration history is reconciled as of 2026-08-15 and must stay that way.** Local
  filenames now match the versions recorded in `supabase_migrations.schema_migrations`
  exactly. Applying a migration through any path that stamps its own version (the MCP
  `apply_migration` tool, the dashboard) without adding a matching repo file breaks
  `supabase db push` for everyone with "Remote migration versions not found in local
  migrations directory". Add migrations as files and let CI apply them. If it does break,
  reconcile by renaming/restoring files — **not** with the `repair --status reverted` the
  CLI suggests, which records applied migrations as reverted and makes the history lie.

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
**Phase A is closed as of 2026-08-01**: A6 (drop `RECEIVE_SMS`, PRODUCT_PLAN.md §5's
Play Store compliance risk) landed in two steps — `757f41c` removed the permission and
replaced `UnifiedSmsReceiver` with `UnifiedBankListener` (bank text now comes from the
messaging app's *notification*), and a follow-up removed the code that still asked for
the now-undeclared permission (`BankReadingStatus.isSmsPermissionGranted`, and a
permanently-off "قراءة الرسايل" row whose Enable button the system rejected without
even showing a dialog). Verified in the **merged** manifest
(`:app:processDebugMainManifest`), not just the source one — 14 permissions, none of
them SMS. Remaining SMS mentions in `.kt` files are comments documenting the decision.
Two other Play-sensitive permissions survive and are undecided:
`ACCESS_BACKGROUND_LOCATION` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Update the
session file
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
- No LLM call on the UI thread or on screen open. **Qualified exception: `HomeScreen`
  is live on first open and cooldown-guarded on re-entry (changed 2026-09-04, `3b12078`
  — supersedes the 2026-07-30 decision below; both were deliberate, neither was an
  oversight).** The 2026-07-30 decision closing Epic 2 was that `LaunchedEffect(Unit)`
  firing `refreshAgentSummary`/`refreshAutoSuggestions`/`predictNextMonthExpenses`/
  `refreshLiveMarketPrices` **on every open** was intended UX — instant/live AI cards on
  the app's most-visited screen — despite `docs/agent/AUDIT.md` flagging it as this
  rule's worst violation by call frequency. What that decision did not have was the cost
  measurement. Measured 2026-09-04: **313 `zad-brain` invocations in 24h against zero
  recorded agent turns in the same window** — i.e. the entire spend was screen-entry
  refreshes, not customer intent — while one user reached **192,841 of the 200,000
  daily token cap (96%) in a single day**, at ~12,700-14,800 tokens per chat turn. Note
  `LaunchedEffect(Unit)` re-runs on every re-entry into composition, so "every open"
  meant every return to the tab, not the first open of a session. The five calls now go
  through `autoRefresh*` wrappers over `autoRefreshBlocked` (`ZadViewModel`, 5-minute
  window keyed per call, covered by `AutoRefreshCooldownTest`): **first open fires
  everything unchanged; a return inside 5 minutes shows the last-fetched values and
  fires nothing; after the window it refreshes again; a user-tapped refresh/retry always
  fires, guard bypassed.** So the live-cards intent is intact where the customer
  actually perceives it, and only silent re-entry churn was removed. Do not widen this
  back to unguarded calls, and do not narrow it to a cache-only screen, without new
  evidence and asking first. The other five screens AUDIT.md flagged for the same rule
  (`ZadIntelligenceScreen`, `ShoppingListScreen`, `WeeklyReportScreen`,
  `NearbyDealsScreen`, and `SubscriptionsScreen`'s/`ZadIntelligenceScreen`'s
  `detectSubscriptions()`) are unaffected by this exception — they were never exempt, so
  `3b12078` also routed `ZadIntelligenceScreen`'s two screen-entry calls
  (`refreshAgentSummary`/`predictNextMonthExpenses`) through the same guard, which
  enforces this rule there rather than excusing it. `detectSubscriptions()`
  specifically got its own confirm-before-write fix (2026-07-30) and still shouldn't
  auto-fire destructively even though it may still fire the read-only detection call.
- Money stays `Double` with `asMoney()` rounding — no minor-units migration.
- No DI framework. Follow the existing `object SupabaseRepo` pattern.
- If a premise in the docs contradicts the code, stop and ask.

## Deliberately not adopted

Two skill sources the user shared were evaluated and intentionally not ported wholesale into this file — noting why so a future session doesn't wonder if they were missed:

- **Anthropic Cybersecurity Skills (817-skill red-team/blue-team library)**: mostly offensive tooling (C2 frameworks, exploit dev, phishing simulation) irrelevant to a Kotlin/Compose family finance app. The **Secure-coding checklist** above extracts the parts of it that actually apply to this codebase's real surface area instead.
- **claude-memory-skill**: this session already has a first-class memory system; installing a second, file-based one inside the project would just create two conflicting sources of persistent memory.
