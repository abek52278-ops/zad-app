# Dark mode — what is still broken, in priority order

Recorded 2026-09-05 from a full Roborazzi pass: all 40 `PreviewTest` captures in both
themes (80 PNGs), via the light/dark parameterisation in `b5dc259`.

Dark mode is **not done**. The token layer follows the theme end to end (`4aa4af3`,
`4c28d73`, `742bcab`, `f59c279`), but hardcoded `Color(0x…)` literals are invisible to
it and stay light on a dark canvas. This file ranks where that actually hurts, so the
literals get fixed worst-first instead of alphabetically.

## Method

Each dark capture was scored by the share of pixels above luminance 170 — i.e. pixels
that belong on a light surface and cannot be correct on a dark one. The top two were
also inspected by eye to confirm the metric matches real breakage. Both were worse than
the number suggested.

## The ranking

There is a cliff after the top three: 83% → 8.6%. These three are not "a bit off", they
are fully light screens with dark-mode text on them.

| # | Capture | Bright | Cause |
|---|---|---|---|
| 1 | `zad_shell_drawer` | **100%** | `ui/components/ZadShell.kt:143` — `.background(Color(0xFFF9FAFB).copy(alpha = 0.92f))` |
| 2 | `auth_header_and_cta` | **98.2%** | `ui/components/ZadDesignPrimitives.kt:331` — `ZadAuthBackground`: `Color(0xFFFBFAF8)` + pink radial `Color(0xFFFCD3C7)` |
| 3 | `onboarding_screen` | **83.1%** | same `ZadAuthBackground` |
| 4 | `kids_mode_content_no_crash` | 8.6% | **not a bug** — Kids Mode is deliberately excluded (CLAUDE.md) |
| 5 | `home_mockup_sequence` | 8.2% | mixed literals across Home's cards |
| 6 | `zad_shell_chrome` | 6.9% | `ZadShell.kt` again (nav//chrome) |
| 7 | `profile_menu_and_primitives` | 6.0% | `ZadDesignPrimitives.kt` |
| 8 | `companion_orb_states` | 5.9% | orb gradients |

Everything below 2% is effectively clean. One to watch that the metric under-rates:
`budget_setup_prompt_glass` is only 1.5% bright but has mean luminance 140 — a washed-out
mid-grey glass surface rather than bright blocks.

## The finding that should change the plan

**All three top offenders live in `ui/components/`, not `ui/screens/`.**

- `ui/components/` holds **431** raw `Color(0x…)` literals
- `ui/screens/` holds **231** — the ones baselined in `app/raw-color-baseline.txt`

So the lint ratchet added in `017de08`/`35e84ae` guards the **smaller and less damaging
half**, and the worst dark-mode breakage in the app is entirely outside its scope.

Two consequences:

1. **Fix shared components before screens.** `ZadShell.kt:143` is one line and it fixes
   the navigation drawer on every screen at once. `ZadAuthBackground` is one composable
   and it fixes the whole auth flow, which is the first thing a new user sees. That is
   three edits for the top three entries, versus 231 scattered literals for a fraction
   of the benefit.
2. **Extend `checkNoRawColorsInScreens` to `ui/components/`** and regenerate the
   baseline, or the larger half stays unguarded and this recurs.

## What this pass does not prove

Roborazzi renders Compose on the JVM, so it says nothing about the real system UI —
status-bar contrast in dark mode, navigation-bar scrim, or the API-31-gated
`Modifier.blur` glass effects, which no-op below that level. The canvas is also
3240×7200, far taller than any phone, so vertical rhythm and spacing in these images are
not representative. Colour and composition are what it verifies.
