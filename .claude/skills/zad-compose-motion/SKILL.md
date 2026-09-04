---
name: zad-compose-motion
description: Add or review motion (transitions, entrance animations, ticking counters, loading shimmer, press feedback) on any Zad Compose screen or component. Use whenever the task involves animating a value, a screen transition, a list reveal, a counter, a progress ring/bar, or "make this feel more alive." Enforces reuse of Zad's existing spring/tween library instead of hand-rolled curves.
---

# Zad Compose Motion

Zad already has a house motion library: `app/src/main/java/com/example/ui/components/ZadAnimations.kt`. CLAUDE.md's mobile-UI rules already say to reuse it instead of inventing new tween curves per screen — this skill is the detailed version of that rule. **Before writing any `animateFloatAsState`, `spring(...)`, or `tween(...)` by hand, check whether one of the presets below already does it.**

Everything here is spring-based, not linear/tween, unless the effect is a looping/periodic one (shimmer sweep, float, pulse) where a `tween` with a fixed period is correct. The target feel is "alive, like iOS" — springs settle with a little life to them; tweens for anything one-shot read as mechanical.

## Step 1: pick the right preset before writing a new curve

`object ZadSprings` (all `spring<Float>`):

| Preset | damping / stiffness | Use for |
|---|---|---|
| `ZadSprings.Press` | 0.55 / 600 | Button/card press-down scale — fast, snappy |
| `ZadSprings.Appear` | 0.7 / 300 | Entrances — soft, slight bounce |
| `ZadSprings.Screen` | 0.85 / 380 | Big transitions — full screens, cards sliding in |
| `ZadSprings.Celebrate` | 0.4 / 500 | Achievements/celebrations — strong bounce (e.g. Tasbiha tap: `tapScale.animateTo(1.22f, animationSpec = ZadSprings.Celebrate)`) |

`object ZadTransitions` — RTL-aware page transitions (`enter`/`exit`/`popEnter`/`popExit`), already wired for `NavHost`. **Do not write your own `slideInHorizontally` sign logic** — the app runs `LayoutDirection.Rtl` (`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` in the theme wrapper), so the offset signs that look "wrong" in `ZadTransitions` are correct for RTL and a naive LTR-style transition will slide the wrong direction. Also has `listItemEnter(index: Int)` for staggered list reveals — `delayMillis = (index * 40).coerceAtMost(400)`, so items after ~10 stop staggering further (avoids a slow reveal on long lists).

If you genuinely need a curve not covered above (rare — most motion needs are covered), match the existing vocabulary: `CubicBezierEasing(0.2f, 0f, 0f, 1f)` for entering, `CubicBezierEasing(0.4f, 0f, 1f, 1f)` for exiting, `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)` ("easePremium," used in `AppearOnEntry`) for premium/hero moments. Don't add a fifth ad hoc spring like `ZadCategoryGridPicker`'s `spring(dampingRatio = 0.7f, stiffness = 400f)` — that one is a known drift from the shared set (it should have been `ZadSprings.Appear`); don't repeat it.

## Step 2: reuse the modifier extensions before writing a new one

All in `ZadAnimations.kt`, all `Modifier.composed {}`:

- **`Modifier.pressableScale(pressedScale = 0.96f, withHaptic = true)`** — the canonical press effect. 117 call sites across 24 files — this is the default for *any* new tappable element (button, card, chip, list row), not a special case. Scales via `ZadSprings.Press`, fires `HapticFeedbackType.TextHandleMove` on press. See [[zad-micro-interactions]] for the haptics half of this.
- **`Modifier.shimmerLoading()`** — skeleton loading sweep (gray gradient, `tween(1100, LinearEasing)`). Use this for any loading placeholder instead of a spinner where a content-shaped skeleton is feasible — matches the existing empty/loading-state pattern CLAUDE.md's UI rules call for.
- **`Modifier.floatingIdle(amplitude = 6f)`** — continuous gentle vertical float, for idle decorative elements (icons, illustrations).
- **`Modifier.bellShake(enabled = true)`** — periodic shake for notification/alert affordances.
- **`Modifier.pulseGlow(minScale = 1f, maxScale = 1.06f)`** — pulsing scale for "look here" emphasis (e.g. a highlighted upsell card).

## Step 3: counters, progress, and screen entrance

- **`animatedCountAsState(targetValue: Int, durationMs = 800)`** — for any number that should tick up/down rather than jump (balances, totals, stat counters). Bank-app convention: numbers animate, labels don't.
- **`drawProgressOnEntry(key: Any? = Unit, durationMs = 1000)`** — use this, not a raw `animateFloatAsState(targetValue = 1f)`, for any progress ring/bar/donut that should fill on first appearance. `animateFloatAsState` targeting a value equal to the value it's already at (a constant `1f` on first composition with no prior state) never animates — this is a real bug class already hit and fixed once; the function exists specifically to avoid re-introducing it via `Animatable` + `snapTo(0f)` + animate. If you see a progress indicator that doesn't animate in on screen entry, this is almost certainly why.
- **`AppearOnEntry(delayMs, modifier, content)`** — wrap a composable to fade+slide it in on screen entry (`translateY(60px)` over 500ms, `easePremium` curve). Use for hero/header elements that should feel like they arrive, not just appear.
- **`rememberMarqueeFraction(periodMs = 22000)`** — looping 0..1 phase for horizontally-scrolling ticker rows (see `LiveMarketTicker.kt`, `PriceTickerRow.kt`). Use for any new ticker/marquee instead of a custom `LaunchedEffect` loop.

## When there's a genuine gap

If a screen needs motion that isn't one of the above (this should be uncommon), add it to `ZadAnimations.kt` as a new named preset/modifier rather than inlining a one-off `animateFloatAsState` in the screen file — that's how the existing library grew, and keeps the "check the library first" step above actually true for the next screen. Name it descriptively (`bellShake`, `pulseGlow` — action + quality, not `anim1`).

## Related

- [[zad-micro-interactions]] — haptics that pair with `pressableScale`, and the loading-skeleton vs shimmer-glow distinction
- [[zad-cupertino-heritage]] — the shape/color/elevation system these animations move within
