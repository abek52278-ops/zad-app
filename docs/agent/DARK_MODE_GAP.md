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

## Status after the first wave (`49ae3b3`, `c1c622f`)

The top three are fixed and the ranking above is history — kept because it is the
evidence for why components were prioritised over screens. Re-measured after the fix:

| Capture | Before | After |
|---|---|---|
| `zad_shell_drawer` | 100.0% | **0.0%** |
| `auth_header_and_cta` | 98.2% | **0.6%** |
| `onboarding_screen` | 83.1% | **0.6%** |
| `zad_shell_chrome` | 6.9% | **1.6%** |

The ratchet now covers `ui/components/` as well, baseline regenerated at 658
occurrences.

## Second wave — is there more worth doing?

Short answer: **no urgent third wave.** After the fix the worst capture is 8.6%, and
that one is not a bug. Nothing left is a "light screen in dark mode"; what remains is
scattered elements.

| Capture | Bright | Assessment |
|---|---|---|
| `kids_mode_content_no_crash` | 8.6% | **Not a bug.** Kids Mode is deliberately excluded (CLAUDE.md). |
| `home_mockup_sequence` | 8.2% | Worth doing — see `ZadHomeGlanceCards.kt` below. |
| `profile_menu_and_primitives` | 6.0% | `ZadDesignPrimitives.kt`, scattered literals. |
| `companion_orb_states` | 5.9% | `CompanionOrb.kt`; decorative gradients, may be intentional. |
| `budget_setup_prompt_glass` | 1.5% | Low bright% but **mean luminance 140** — a washed-out mid-grey glass surface rather than bright blocks. The metric under-rates this one. |

Where the remaining literals are concentrated, worst first:

| File | `Color(0x…)` | bare `Color.White/Black` |
|---|---|---|
| `ZadHomeGlanceCards.kt` | **132** | **25** |
| `CategoryIllustrations.kt` | 67 | — |
| `ZadShell.kt` | 19 | 10 |
| `SubscriptionBrandIcons.kt` | 18 | — |
| `ZadVoiceBottomSheet.kt` | 15 | 13 |
| `ZadBezierSpendChart.kt` | 15 | — |
| `ZadWalletHeroCard.kt` | 13 | 18 |

`ZadHomeGlanceCards.kt` is the one file worth a dedicated pass: 157 literals between the
two kinds, on the app's most-visited screen. `CategoryIllustrations.kt` and
`SubscriptionBrandIcons.kt` are probably fine as-is — brand marks and illustrations are
legitimately fixed-colour, the same exemption CLAUDE.md grants Kids Mode.

## Two bug classes, one of them still open

### Class 1 — opaque white/black **surface**. Gated as of `4d3ca2f`.

`.background(Color.White)` on a card, pill or sheet. It stays white in dark mode.
`ZadDrawerContent` was this, and it was the worst breakage in the app.

The ratchet now catches it, deliberately narrowly. Of **324** bare `Color.White`/`Black`
across screens and components, 127 are text `color =`, 60 are `tint`/`contentColor`, and
40 more are `.copy(alpha = …)` scrims over coloured cards — all legitimately
theme-independent. Only an **opaque** `Color.White`/`Black` passed straight to
`.background(...)` is flagged, which leaves **12**. Seven were fixed in `4d3ca2f`; five
are baselined with their reasons in `app/raw-color-baseline.txt`.

### Class 2 — white/black **content on a surface that flips**. OPEN. Not caught by anything.

`Color.White` as `color =`, `tint =` or `contentColor =` on a surface painted with a
theme-aware token. In light the surface is dark so white content is correct; in dark the
surface becomes light and the content stays white, so it washes out. The colour that is
"wrong" is not itself hardcoded to a *surface* — it is hardcoded relative to one.

**Live example, now fixed (`b0250e1`):** `BudgetSetupPromptCard` in
`PremiumHomeComponents.kt`. Background was `listOf(primaryDark, primary, primaryDark)` —
theme-aware and correct. Icon, heading, body and CTA were `Color.White`. Light: white on
deep forest `#1B4332`, correct. Dark: white on mint `#74C69D`, with the body line barely
legible. Fix was `onPrimary`, which flips `#FFFFFF` → `#0B1710`.

Note this scored only **1.5% bright pixels** — the brightness metric used for the ranking
above does **not** find this class, because the failure is a mid-tone surface with
wrong-contrast content rather than bright blocks. Its mean luminance of 140 was the only
hint.

**Why no lint rule closes this.** A regex sees `tint = Color.White` and cannot tell
whether it sits on `primary` (breaks in dark), on a fixed brand colour (fine), or on a
photo (fine). Deciding requires knowing the surface the content is drawn on — data flow,
not text matching. Even a custom Lint Detector with type resolution would need to trace
the enclosing modifier chain, which is a real piece of work with a real false-positive
risk.

**Status: a future manual-review item, not a task in progress.** The practical approach
is a targeted read of files that pair a themed background with hardcoded white content —
`ZadWalletHeroCard`, `PremiumHomeComponents`, `ZadHomeGlanceCards` are the likely
candidates, since all three paint cards with `primary`/`primaryDark` gradients. Nobody
should assume this class is clean because the ratchet is green.

## What this pass does not prove

Roborazzi renders Compose on the JVM, so it says nothing about the real system UI —
status-bar contrast in dark mode, navigation-bar scrim, or the API-31-gated
`Modifier.blur` glass effects, which no-op below that level. The canvas is also
3240×7200, far taller than any phone, so vertical rhythm and spacing in these images are
not representative. Colour and composition are what it verifies.

**Do not mistake this pass for dark-mode verification.** A green Roborazzi run means the
theme reaches the composables and the colours are right. It does not mean dark mode
works on a phone. Status-bar and navigation-bar contrast, the blur/glass surfaces above
API 31, and anything involving real device chrome have **never been checked in any
theme** and require a real device — Robolectric cannot answer them. Dark mode stays
open until someone runs the app on hardware.
