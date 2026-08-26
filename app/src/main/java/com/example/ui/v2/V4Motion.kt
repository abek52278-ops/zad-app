package com.example.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * V4 Motion — the "iPhone-grade" layer ported from new ui ux/zad_premium_v5.html.
 *
 * Additive utilities only; no existing composable signatures change:
 *  - [deepPress]      press anticipation dip + spring overshoot release
 *  - [heroTilt]       drag-follow 3D tilt for the wallet hero card (±4°)
 *  - [rememberShimmerBrush] travelling specular gradient for big amounts
 *  - [rememberMarqueeFraction] seamless ticker loop phase 0..1
 *  - [AiRippleRings]  expanding fading rings behind the voice orb (ai_orb.json)
 *  - [ScanSweep]      camera-sheet laser line with glow band (scan_line.json)
 *
 * Signature easings extracted from the prototype CSS variables:
 *  --ease-ios:      cubic-bezier(.32,.72,0,1)   → sheets/choreography
 *  --ease-premium:  cubic-bezier(.22,1,.36,1)   → entrances
 *  --ease-spring:   cubic-bezier(.34,1.56,.64,1)→ tap overshoot
 */
val EaseIos = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
val EasePremium = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
val EaseSpringCurve = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// ── deepPress ────────────────────────────────────────────────────────────────

/**
 * Press: fast dip to 0.94 (~90ms, no overshoot on the way down).
 * Release: medium-bouncy spring back to 1f with follow-through.
 * Prefer over [pressableScale] for large targets (hero, FABs, primary buttons).
 */
fun Modifier.deepPress(onClick: () -> Unit = {}): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(0.94f, tween(durationMillis = 90, easing = EaseIos))
        } else {
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

// ── heroTilt ─────────────────────────────────────────────────────────────────

/**
 * Drag/touch-tracking 3D tilt for the hero wallet card, clamped to ±4°,
 * spring return to flat on release. Mirrors the HTML #phone hover behavior.
 */
fun Modifier.heroTilt(): Modifier = composed {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragAccumX by remember { mutableStateOf(0f) }
    var dragAccumY by remember { mutableStateOf(0f) }

    // Track target angles; smooth-chase them so the card feels weighty.
    LaunchedEffect(dragAccumX, dragAccumY) {
        if (dragging) {
            rotX.animateTo(dragAccumX, spring(stiffness = Spring.StiffnessMediumLow))
            rotY.animateTo(dragAccumY, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    LaunchedEffect(dragging) {
        if (!dragging) {
            dragAccumX = 0f
            dragAccumY = 0f
            // Spring back flat on release.
            rotX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            rotY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    this
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            cameraDistance = 18f * density // flatter iPhone-card perspective
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { _ -> dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, dragAmount ->
                change.consume()
                val targetY = (dragAccumY + dragAmount.x / size.width * 30f)
                    .coerceIn(-4f, 4f)
                val targetX = (dragAccumX - dragAmount.y / size.height * 30f)
                    .coerceIn(-4f, 4f)
                dragAccumY = targetY
                dragAccumX = targetX
            }
        }
}

// ── shimmerText ──────────────────────────────────────────────────────────

/** Travelling specular band for huge amounts ("≈ 3,240 ر.س"). Use as TextStyle brush. */
@Composable
fun rememberShimmerBrush(base: Color, highlight: Color): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_shift",
    )
    return remember(shift, base, highlight) {
        Brush.linearGradient(
            colors = listOf(base, highlight.copy(alpha = 0.85f), base),
            start = Offset(shift * 600f, 0f),
            end = Offset(shift * 600f + 300f, 120f),
        )
    }
}

// ── tickerMarquee ────────────────────────────────────────────────────────────

/** Seamless looping marquee phase in 0..1 for the LiveMarketTicker row to consume. */
@Composable
fun rememberMarqueeFraction(periodMs: Int = 22000): Float {
    val transition = rememberInfiniteTransition(label = "marquee")
    val f by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "marquee_fraction",
    )
    return f
}

// ── aiRippleRings ────────────────────────────────────────────────────────────

/**
 * Three staggered expanding rings behind the voice orb (port of assets/ai_orb.json):
 * each ring scales 20%→100% while fading 80%→0% over ~833ms at 60fps-equivalent
 * timing, 300ms lead between rings — same stagger as the Lottie source.
 */
@Composable
fun AiRippleRings(modifier: Modifier = Modifier, ringColor: Color = Color(0xFF6EE7B7)) {
    val transition = rememberInfiniteTransition(label = "ai_rings")
    val strokes = listOf(0, 1, 2).map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(833, delayMillis = i * 300, easing = EaseIos), RepeatMode.Restart),
            label = "ring$i",
        )
    }

    Canvas(modifier = modifier) {
        val maxR = size.minDimension / 2f
        val strokePx = 3.dp.toPx()
        strokes.forEach { s ->
            val v = s.value
            drawCircle(
                color = ringColor.copy(alpha = (0.8f * (1f - v)).coerceIn(0f, 1f)),
                radius = maxR * (0.2f + 0.8f * v),
                style = Stroke(width = strokePx),
            )
        }
    }
}

// ── scanSweep ────────────────────────────────────────────────────────────────

/**
 * Camera-sheet sweep line (port of assets/scan_line.json): eases top↔bottom
 * with an edge-faded mint line and a soft vertical glow band beneath it.
 */
@Composable
fun ScanSweep(modifier: Modifier = Modifier, lineColor: Color = Color(0xFF6EE7B7)) {
    val transition = rememberInfiniteTransition(label = "scan")
    val y by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseIos), RepeatMode.Reverse),
        label = "scan_y",
    )

    Canvas(modifier = modifier) {
        val yPx = size.height * y
        val bandHeight = size.height * 0.10f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, lineColor.copy(alpha = 0.16f), Color.Transparent),
                startY = yPx - bandHeight,
                endY = yPx + bandHeight,
            ),
            topLeft = Offset(0f, yPx - bandHeight),
            size = Size(size.width, bandHeight * 2),
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(lineColor.copy(alpha = 0f), lineColor, lineColor.copy(alpha = 0f)),
            ),
            start = Offset(size.width * 0.06f, yPx),
            end = Offset(size.width * 0.94f, yPx),
            strokeWidth = 2.5f,
        )
    }
}
