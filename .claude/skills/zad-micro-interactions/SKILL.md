---
name: zad-micro-interactions
description: Add haptic feedback, press states, loading skeletons, or any small tap/response feedback to a Zad Compose screen. Use whenever adding a new tappable element, a vibration/haptic buzz, a loading placeholder, or reviewing whether a list-backed screen's empty/loading state meets the house bar. Also use when touching any raw Vibrator/VibrationEffect code — there is a specific three-part SDK guard this app requires.
---

# Zad Micro-interactions & Haptics

Small feedback — a press scale, a buzz, a shimmer while data loads — is what makes a screen feel responsive rather than inert. Zad has two haptics mechanisms and a specific, previously-crash-causing guard pattern for one of them. Get the guard wrong and it's not a style nit, it's a crash on API 24-25 devices (minSdk is 24).

## Press feedback: use `pressableScale`, don't hand-roll it

`Modifier.pressableScale(pressedScale = 0.96f, withHaptic = true)` in `ui/components/ZadAnimations.kt` is the house pattern — 117 call sites across 24 files, the dominant reused pattern in the app. It scales the element down on press via `ZadSprings.Press` (see [[zad-compose-motion]]) and fires a light haptic. **Any new tappable card, button, chip, or list row should use this** rather than a custom `pointerInput` + `animateFloatAsState` combination. If you're about to write `detectTapGestures` with a manual scale animation, stop and use this instead unless the interaction is genuinely non-standard (e.g. drag-to-reorder, swipe-to-dismiss).

## Which haptics API: Compose `LocalHapticFeedback` vs raw `Vibrator`

**Default to Compose's `LocalHapticFeedback`** (`androidx.compose.ui.hapticfeedback.HapticFeedbackType`) — it's what `pressableScale` already uses, needs no manual SDK guard, and covers the vast majority of cases. The observed convention in this codebase:
- `HapticFeedbackType.TextHandleMove` — light, for routine/frequent taps: presses, dismissals, rejecting something minor.
- `HapticFeedbackType.LongPress` — heavier, reserve for weightier confirm actions: sending an SOS, approving/rejecting a family purchase request, a destructive or high-stakes confirm.

Only reach for raw `Vibrator`/`VibrationEffect` when you need a **specific custom duration/amplitude** the Compose API doesn't expose (e.g. a distinctive short double-buzz on a successful scan, a longer buzz for a milestone). This is genuinely a second, lower-level mechanism used deliberately in a few places (`TasbihaScreen.kt`, `CameraScreen.kt`, `HomeScreenWidgets.kt`) — not a mistake to consolidate away.

### The three-part guard, exact pattern (copy this, don't simplify it)

```kotlin
try {
    val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        vib?.vibrate(android.os.VibrationEffect.createOneShot(30, 200)) // duration ms, amplitude 0-255
    } else {
        @Suppress("DEPRECATION") vib?.vibrate(30)
    }
} catch (_: Exception) {}
```

Why all three parts matter, per CLAUDE.md's secure-coding checklist (this was a real bug, already fixed once — don't reintroduce a simplified version that drops a part):
1. **The `SDK_INT >= 26` branch is the actual guard**, not the `catch`. `VibrationEffect` was added in API 26; referencing the class on API 24-25 throws `NoClassDefFoundError`, which is an `Error`, not an `Exception` — a bare `catch (Exception)` does **not** catch it. Skipping the SDK check and relying on the catch alone crashes the app on API 24-25.
2. The `else` branch (`@Suppress("DEPRECATION") vib?.vibrate(30)`) keeps haptics working — not silently disabled — on the two SDK levels below the guard, since minSdk is 24.
3. The outer `try/catch` is defense-in-depth for other failure modes (no vibrator hardware, permission edge cases) — real, just not the thing that stops the crash above.

Tune duration/amplitude per feel (`30ms`/`200` for a light tap, `50ms`/`200` ×2 for a success confirmation, `12ms`/`140` for a subtle widget tap are the values already in use) rather than reusing one buzz everywhere.

## Loading states: shimmer, not a bare spinner

`Modifier.shimmerLoading()` (`ZadAnimations.kt`) — a gray gradient sweep — is the canonical loading placeholder. Use a content-shaped skeleton with this modifier instead of a centered `CircularProgressIndicator` wherever the eventual content's layout is known ahead of time (list rows, cards) — it reads as "content is arriving" rather than "app is thinking." Reserve a bare spinner for cases where no layout shape is known yet (first-ever load of an unknown-shape response).

Note there's a second, adjacent shimmer pattern: `ZadHomeGlanceCards.kt` hand-rolls its own local `shimmerProgress`/`shimmerAlpha` `infiniteTransition` for a border-glow effect on premium-upsell cards — that's a different visual goal (a glowing edge, not a loading-content sweep) and not a bug to consolidate, but don't copy its hand-rolled animation for a plain loading skeleton; use `shimmerLoading()` for that.

## Empty/error states

Per CLAUDE.md's mobile-UI rules: every list-backed screen needs a real empty state (icon + short title + subtitle guidance), not a blank `LazyColumn` — match `BudgetScreen`'s empty-transactions pattern. This is a completion bar, not optional polish: a screen with loading/press/haptic feedback but a blank empty state is only half-finished from a micro-interactions standpoint.

## Related

- [[zad-compose-motion]] — the spring preset (`ZadSprings.Press`) `pressableScale` runs on
- [[zad-cupertino-heritage]] — the shapes/colors these press-and-loading states render inside of
