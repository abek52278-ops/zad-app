---
name: run-zad-app
description: Build, test, and screenshot the Zad Android app (Kotlin/Compose) via Robolectric + Roborazzi. Use when asked to build the app, run its unit tests, or take a screenshot of a Compose screen/widget — this container has no Android emulator or KVM, so screenshots come from JVM-rendered Robolectric tests, not a running device.
---

Zad is a Kotlin/Jetpack Compose Android app (single Gradle module,
`:app`). This container has **no emulator and no `/dev/kvm` access**
(checked: `platform-tools` exists, `emulator` package does not, `/dev/kvm`
exists but isn't accessible to this user) — so there is no way to launch
the app on a device/AVD here. The driver instead is
**Robolectric + Roborazzi**: JVM-only Android framework shim +
Compose screenshot testing, already wired into the project
(`app/build.gradle.kts`, plugin `io.github.takahirom.roborazzi`). It
actually composes real screens/widgets and renders real PNGs — this is
not a mock, it's the same rendering path Compose Preview uses.

The driver test file **must live under `app/src/test/`** — Gradle only
discovers Robolectric tests inside a module's test source set, so
unlike other project types there's no separate driver file inside this
skill directory. The driver is:
**`app/src/test/java/com/example/ui/screens/PreviewTest.kt`**

All paths below are relative to the repo root.

## Prerequisites

Nothing to install — JDK, Android SDK platform/build-tools, and all
Gradle deps are already present in this container. No `.env` is
required either: the Secrets Gradle Plugin falls back to
`.env.example`'s placeholder values (`secrets { defaultPropertiesFileName
= ".env.example" }` in `app/build.gradle.kts`) when `.env` is absent,
which is enough for `compileDebugKotlin` and Robolectric rendering
(nothing in these tests makes a real Supabase/network call).

## Build

```bash
./gradlew --no-daemon :app:compileDebugUnitTestKotlin
```

## Run (agent path)

1. Add a `@Test` method to `PreviewTest.kt` that composes your target
   screen/widget inside `AppTheme { ... }` and calls
   `composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/<name>.png")`.
   Copy the pattern from the existing `captureZadQuestionCard_numberType`
   test — for a **stateless** composable (plain data-class params +
   lambdas) this is a 10-line test. A **ViewModel-backed** screen (e.g.
   `HomeScreen(viewModel: ZadViewModel, ...)`) needs a real or fake
   `ZadViewModel` constructed first — out of scope for this driver as
   written; none of the current capture tests do this.

2. Run it:

```bash
./gradlew --no-daemon :app:recordRoborazziDebug --tests "com.example.ui.screens.PreviewTest"
```

3. Read the PNG from `app/build/outputs/roborazzi/<name>.png` (path is
   set per-test in the `filePath` argument, relative to `app/`).

**Always pass `--no-daemon`** — see Gotchas.

### Verified working examples (this session)

| test | captures | path |
|---|---|---|
| `captureOnboardingScreen` | `OnboardingScreen` (no ViewModel) | `app/build/outputs/roborazzi/onboarding_screen.png` |
| `captureZadQuestionCard_numberType` | `ZadQuestionCard`, `actionType="number"` | `app/build/outputs/roborazzi/zad_question_card_number.png` |
| `captureZadQuestionCard_yesNoType` | `ZadQuestionCard`, `actionType="yes_no"` | `app/build/outputs/roborazzi/zad_question_card_yes_no.png` |

## Run (human path)

Not meaningfully different — there's no device to install an APK onto
in this container. `./gradlew :app:assembleDebug` builds the APK if
you just need to confirm it packages.

## Test

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

68 tests, 0 failures (this session, full `app/src/test/` suite —
15 test files, includes the 3 Roborazzi capture tests above).
`app/src/androidTest/` (2 files) needs a real device/emulator and
**cannot run in this container** — don't attempt it here.

Junit XML: `app/build/test-results/testDebugUnitTest/*.xml`.
HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

## Gotchas

- **Always run with `--no-daemon`.** With the daemon on, a full test
  run crashed mid-build with `Gradle build daemon disappeared
  unexpectedly (it may have been killed or may have crashed)` (this
  container has 7.8Gi RAM / 2 CPUs — the daemon plus the forked test
  JVM plus the Kotlin compile daemon likely exceeded it). `--no-daemon`
  forks a single-use JVM per invocation instead and was reliable across
  every run this session (build succeeded 3/3 times with it, 0/1 without).
- **Screenshots default to dark background** in these tests — that's
  the `@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")`
  Robolectric config on `PreviewTest`, not a bug in the composable. If
  you need a light-mode capture, add `-night` (or whatever qualifier
  the project's theme reads) to a new `@Config` on your test class —
  not verified in this session, since both examples above used the
  class default.
- **`Icons.AutoMirrored.Filled.*` needs its own import** —
  `import androidx.compose.material.icons.automirrored.filled.<Name>`
  — the wildcard `icons.filled.*` import doesn't cover it. Hit this
  compiling `HomeScreen.kt` (missing `TrendingUp` import) this session.

## Troubleshooting

- **`Gradle build daemon disappeared unexpectedly`**: OOM under the
  daemon. Re-run the same command with `--no-daemon`.
- **`Unresolved reference` on a Compose Material icon that clearly
  exists**: check whether it's under `Icons.AutoMirrored.*` — needs the
  explicit import above, not covered by `icons.filled.*`.
- **`Unresolved reference 'auth'` on a Supabase client**: missing
  `import io.github.jan.supabase.auth.auth` (the `.auth` accessor is an
  extension function, not a member — every other file that uses
  `SupabaseRepo.client.auth` imports it explicitly).
