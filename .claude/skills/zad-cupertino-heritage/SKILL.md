---
name: zad-cupertino-heritage
description: Apply or extend Zad's iOS-influenced "Cupertino Heritage" visual language on a Compose screen — corner radii, palette, hairline borders, glass/blur, elevation. Use whenever styling a new screen or component, choosing colors/shapes/shadows, or asked to make something "look more like iOS" or "more premium." Also use when confused by which of Zad's three coexisting theme systems (Cupertino Heritage, ZadV2/V3, ZadLuxe) applies to a given file.
---

# Zad Cupertino Heritage

Zad's visual language is `gemini-3.5-flash`-era iOS: Forest Emerald / Mustard Ochre / Terracotta Rust as the brand triad, soft rounded "squircle" cards, hairline borders instead of heavy dividers, and near-imperceptible ambient shadows instead of Material's default elevation shadows. This was formalized in commit `8b10086` ("implement iOS Cupertino Heritage design system"). **Read the three-systems section before touching any theme file** — this is the single most likely way to get styling on this app wrong.

## Three token systems coexist — know which one a file is actually using

1. **Cupertino Heritage** — `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/ZadTheme.kt` (`ZadShapes`, `ZadColorScheme`). **This is the canonical, live system** — it's what `AppTheme` (`ui/theme/Theme.kt`, wired in `MainActivity.kt`) actually consumes via `MaterialTheme.colorScheme`/`MaterialTheme.typography`/`MaterialTheme.shapes`. Reach for `MaterialTheme.colorScheme.primary` etc., or the named tokens directly, for any new screen that doesn't already have an established local style.
2. **`ZadV3`/`ZadV2`** (`ui/theme/ZadV2.kt` — `object ZadV3`, with `val ZadV2 = ZadV3` as an alias) — an older "Apple Wallet"-style green system (`green800 = 0xFF064E3B`, `rCard`/`rCardLg`/`rSheet`/`rHero`/`rPill` radii, `heroGradient`, `contractCardShadow`/`floatingShadow`), referenced **by name**, not through `MaterialTheme`. ~5 files use it directly (`ZadShell.kt`, `ZadAnimatedLogo.kt`, and others). **If you're editing one of those files, match its existing `ZadV3.*` references — don't silently migrate it to Heritage tokens mid-edit**, that's a much bigger, deliberate migration, not a drive-by styling fix.
3. **`ZadLuxe`** (`ui/theme/ZadHomeLuxe.kt`, `object ZadLuxe`) — explicitly scoped to `HomeScreen` only (its own doc comment says so directly: not a replacement for the shared ZadV3/V2, just Home-local). Re-exposes the same hex values as Heritage under shorter names (`emerald = 0xFF1B4332`, etc.) plus `squircle = RoundedCornerShape(20.dp)`. 22 files use it — all Home-screen-adjacent. Don't reach for `ZadLuxe` outside Home-screen code; use Heritage tokens instead.

**Rule of thumb**: new screen with no established local style → Cupertino Heritage via `MaterialTheme`. Editing an existing `ZadShell`/`ZadAnimatedLogo`-family file → match its `ZadV3` references. Editing Home-screen-family code → match its `ZadLuxe` references. Don't introduce a fourth system.

## The concrete vocabulary (Cupertino Heritage)

**Shapes** — `ZadShapes` in `ZadTheme.kt`:
| Token | Value | Use |
|---|---|---|
| `extraSmall` | 8.dp | small chips, tags |
| `small` | 16.dp | pills, chips |
| `medium` | 20.dp | "signature squircle" cards — the default card radius |
| `large` | 28.dp | buttons, the floating bottom bar |
| `extraLarge` | 32.dp | hero/sheet-level surfaces |

**Palette** — `Color.kt`. Brand triad, each with `Dark`/`Light`/`Container` variants: `ZadForestEmerald` (`0xFF1B4332`), `ZadMustardOchre` (`0xFFC68216`), `ZadTerracottaRust` (`0xFFD95726`). iOS-flavored neutrals: `ZadIosBackground` (`0xFFF8F9FA`), `ZadIosSurface` (`0xFFFFFFFF`), `ZadIosSurfaceVariant` (`0xFFEDEFE9`), `ZadIosOutline` (`0xFFE0E3DA` — the hairline color, see below). Below those, a large set of Material3-alias lowercase tokens (`primary`, `secondary`, `background`, `surface`, `surfaceContainerLow/High/Highest`, `textPrimary/Secondary/Tertiary`, `successColor/dangerColor/warningColor/infoColor`, per-category pastel pairs like `catBillsBg/Icon`) — these are the ones CLAUDE.md's mobile-UI rules mean by "reuse tokens from `ui/theme/Color.kt`." Kids Mode has its own intentionally-more-playful set (`kidsPrimary`, `kidsAccentPink`, etc.) — per CLAUDE.md, don't normalize Kids Mode toward the adult palette.

**Typography** — `Type.kt`, standard Material3 `Typography` scale (`displayLarge/Medium`, `headlineLarge/Medium`, `titleLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small`). Two font families: `CairoFamily` (Arabic UI face, variable font, weights Normal/SemiBold/Bold/ExtraBold/Black) and `InterFamily` (SF-Pro-style, for numbers/English — SemiBold/Bold/ExtraBold only). **All styles, including body/label, use `CairoFamily`** — this was deliberate, not an oversight (the file's own comments trace a weight-substitution bug that came from trying Inter on body/label). Don't hardcode a `fontSize`; use `MaterialTheme.typography.*`.

**Hairline borders** — `0.5.dp`, color `ZadIosOutline`. Used as `BorderStroke(0.5.dp, borderColor)` — this is the iOS-style thin divider, standing in for Material's heavier default dividers. Reach for this instead of a 1dp+ divider or a `Divider()` composable when the goal is a subtle card/list edge.

**Soft ambient elevation** — reference: `ZadGlanceCard.kt`. Uses `elevation = 1.dp` with `ambientColor = Color(0x0A000000)` and `spotColor = Color(0x05000000)` (both near-transparent black) rather than Material's default shadow colors. The point: shadows should be barely-there ambient softness, not a visible drop shadow. If a card looks like it has a "normal Android shadow," the ambient/spot colors are probably still at Material defaults — override them.

**Glass/blur** — `ui/components/PremiumSurfaces.kt`: `Modifier.zadGlassBlur(radius: Dp = 20.dp)` and `GlassCard(...)` (translucent `Color.White.copy(alpha = 0.85f)` + hairline border + blurred background). **`Modifier.blur` is a silent no-op below API 31** (minSdk is 24), and this is gated correctly already (`Build.VERSION.SDK_INT >= S`) — copy that guard, don't skip it. Also: blur must be applied to a **background-only `Box` positioned behind unblurred content**, never to the content column itself, or the content (including any text) blurs too — this was a real Roborazzi-caught bug. Use `GlassCard`/`zadGlassBlur` directly rather than re-deriving this.

**RTL** — the whole app runs `LayoutDirection.Rtl` via `CompositionLocalProvider` in the theme wrapper. Anything involving directional offsets (slide transitions, icon mirroring, padding start/end vs left/right) needs to be RTL-correct — use `start`/`end` padding, not `left`/`right`, and see [[zad-compose-motion]] for why `ZadTransitions`' offset signs look "backwards" if you're used to LTR.

**Icons** — plain `Icons.Rounded.*` (Material rounded set). There's no custom SF-Symbols-style icon system in this app — don't invent one or introduce a new icon library for a "more iOS" look; the iOS feel comes from shape/color/elevation/typography, not from icon glyphs.

## A caution on "reference" components

`ZadGlanceCard` and `ZadCategoryGridPicker` (added alongside the Heritage tokens) are each referenced only within their own definition file today — treat them as **patterns to imitate**, not proven, widely-adopted components. The standalone `ZadTheme()` composable is never actually called (`MainActivity.kt` wires `AppTheme` from `Theme.kt`, which already consumes the same underlying `ZadColorScheme`/`Typography`/`ZadShapes`) — don't assume wrapping something in `ZadTheme()` does anything different from the ambient theme already in effect.

## Related

- [[zad-compose-motion]] — the spring/transition system that should carry this visual language's cards/sheets on and off screen
- [[zad-micro-interactions]] — press feedback and haptics that pair with `pressableScale`d Heritage-styled cards
