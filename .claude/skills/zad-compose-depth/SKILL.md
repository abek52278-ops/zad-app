---
name: zad-compose-depth
description: Add depth, layering, or 3D-feeling effects to a Zad Compose screen — parallax, card flips, tilt, isometric or "living" visualizations, elevation hierarchy. Use when a screen feels flat and the ask is to give it dimensionality, or when building a new data visualization (network graphs, maps, relationship diagrams) that should feel spatial.
---

# Zad Compose Depth & 3D

**Read this first: Zad has never used real 3D transforms.** There are zero uses of `rotationX`, `rotationY`, or `cameraDistance` anywhere in the app. Every "depth" effect that exists today is *simulated* depth on a flat `Canvas`/`graphicsLayer` — scale, alpha, and timing standing in for actual perspective. Don't assume a `rotationX`/`cameraDistance` card-flip pattern already exists in this codebase and go looking for it; it doesn't. This skill covers both: how the existing fake-depth pattern works (use this by default), and how to add real 3D transforms correctly when a screen genuinely needs one (card flips, tilt-on-drag) — which is new territory for the app, not a house pattern being extended.

## Default: simulated depth (scale + alpha + phase, no new graphics cost)

The reference implementation is `FamilyNeuralNetworkCanvas` in `ZadIntelligenceScreen.kt` (`ui/screens/ZadIntelligenceScreen.kt`, ~line 3768), a `Canvas`-drawn family network graph. Its own doc comment states the philosophy directly (translated): *fake depth (parallax) instead of a real 3D engine — each node sits at a different phase of a breathing wave, so its scale and alpha change over time instead of staying static, giving a "living" feel with no new graphics library and no measurable perf cost.*

The technique, concretely:

1. Drive one or more phases with `rememberInfiniteTransition` — e.g. a slow `rotationDeg` (26s linear) for orbital motion, a `breathePhase` (7s sine) per element, a faster `pulsePhase` (2.2s) for a traveling highlight.
2. Give each element its own phase **offset**, not a shared phase — `breathePhase + index * (PI / count)` — so elements don't move in lockstep. Lockstep motion reads as mechanical; offset motion reads as alive.
3. Compute `depth = (sin(depthPhase) + 1) / 2` (normalizes a sine wave to 0..1) and derive **both** scale and alpha from the same `depth` value — e.g. `depthScale` in range 0.76–1.12, `depthAlpha` in range 0.55–1.0, moving together. This coupling (bigger = more opaque = "closer," smaller = fainter = "farther") is the actual illusion. Driving scale and alpha from unrelated phases breaks the depth read.
4. Optionally flatten one axis (e.g. `* 0.55f` on a y-offset) for a slight isometric/top-down feel without any real projection math.

Use this pattern for: new data visualizations (network/relationship graphs, category maps), any "living" decorative element, anywhere a full 3D engine would be overkill for the payoff. It costs one `Canvas` and a couple of `infiniteTransition` animations — no new dependency, no measurable perf hit, which is why it's the default over reaching for real 3D.

A lighter version of the same idea (no breathing/phase, just direct manipulation) is pinch-zoom/pan on `ZadKnowledgeMapScreen.kt`: `graphicsLayer { scaleX/scaleY = zoomScale; translationX/Y = panOffset }` over a `Canvas`-drawn map. Use this shape for anything zoomable/pannable rather than the network-canvas breathing pattern.

## When simulated depth isn't enough: real 3D transforms

If the ask is specifically a card flip, a tilt-on-drag effect, or something else that needs actual perspective rather than a "living" feel, use `graphicsLayer`'s real 3D fields — this is new ground for the app, so be deliberate rather than copying a nonexistent precedent:

- `rotationX` / `rotationY` on `Modifier.graphicsLayer` for the flip/tilt itself.
- `cameraDistance` — Compose's default camera distance makes rotations beyond ~15-20° look warped (excessive foreshortening) because the default is tuned for subtle effects. Increase it (e.g. `8 * density` as a starting point, tune by eye) for anything beyond a slight tilt, especially a 90°+ card flip.
- For a two-sided flip (front/back content), gate content visibility on the rotation angle crossing 90° (`if (rotation <= 90f) FrontContent() else BackContent()`), and mirror the back content (`scaleX = -1f` or `rotationY -= 180f` on the back layer) so text doesn't render mirror-reversed once past the midpoint.
- Pair the rotation with [[zad-compose-motion]]'s `ZadSprings.Screen` (0.85/380) for the settle, not a linear tween — a 3D flip that eases linearly reads as a slideshow transition, not a physical flip.

Keep new 3D effects sparingly used and purposeful (a card-flip reveal, a single hero tilt) rather than applied broadly — this is a finance app with RTL Arabic text-heavy screens, and heavy real-3D use (busy tilting lists, rotating everything) will both hurt readability of Arabic script at odd angles and stand out as inconsistent against the rest of the app's flat, calm surfaces (see [[zad-cupertino-heritage]]).

## Related

- [[zad-compose-motion]] — the spring/easing presets to pair any 3D transform's settle-in with
- [[zad-cupertino-heritage]] — the flat elevation/shadow language (soft ambient shadows, not heavy Material elevation) that real 3D effects should stay visually consistent with
