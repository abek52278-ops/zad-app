package com.example.ui.v2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * V3 Animations — iOS 18 premium feel
 * - pressableScale: Spring press (0.94x)
 * - fadeUpOnAppear: Staggered fade + translate Y
 * - floatingIdle: Gentle 6px float
 * - ringSpin: Orbital rotation
 * - AnimatedMeshGradient: Canvas-based 9s mesh gradient
 * - ConfettiOverlay: Rising particles
 */

/** iOS-style spring press effect */
fun Modifier.pressableScale(onClick: () -> Unit = {}): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressable_scale",
    )

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/** Staggered fade + translate Y entrance */
fun Modifier.fadeUpOnAppear(delayMs: Long = 0): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (delayMs > 0) delay(delayMs); visible = true }

    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "fadeUp_alpha")
    val offsetY by animateFloatAsState(targetValue = if (visible) 0f else 30f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "fadeUp_offset")

    this.graphicsLayer { this.alpha = alpha; this.translationY = offsetY }
}

/** Gentle idle floating (6px) */
fun Modifier.floatingIdle(duration: Int = 2000): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "floating_offset",
    )
    this.graphicsLayer { translationY = offsetY }
}

/** Infinite spinning ring */
fun Modifier.ringSpin(duration: Int = 8000): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(duration, easing = androidx.compose.animation.core.LinearEasing), repeatMode = RepeatMode.Restart),
        label = "spin_rotation",
    )
    this.graphicsLayer { rotationZ = rotation }
}

/** Animated mesh gradient background */
@Composable
fun AnimatedMeshGradient(modifier: Modifier = Modifier, colors: List<Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "mesh_shift",
    )

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val x1 = w * (0.2f + 0.6f * shift); val y1 = h * (0.2f + 0.3f * (1f - shift))
        val x2 = w * (0.8f - 0.4f * shift); val y2 = h * (0.8f - 0.2f * shift)

        drawRect(brush = Brush.linearGradient(colors = colors, start = Offset(0f, 0f), end = Offset(w, h)))
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(colors.getOrElse(1) { colors.first() }.copy(alpha = 0.6f), Color.Transparent), center = Offset(x1, y1), radius = w * 0.8f),
            center = Offset(x1, y1), radius = w * 0.8f,
        )
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(colors.getOrElse(2) { colors.last() }.copy(alpha = 0.5f), Color.Transparent), center = Offset(x2, y2), radius = w * 0.7f),
            center = Offset(x2, y2), radius = w * 0.7f,
        )
    }
}

/** Confetti particles */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier, isTriggered: Boolean) {
    if (!isTriggered) return
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "confetti_progress",
    )
    val particles = remember {
        List(20) {
            object {
                val startX = Random.nextFloat()
                val targetYOffset = Random.nextFloat() * -400f - 100f
                val color = listOf(Color(0xFF6EE7B7), Color(0xFFF4A93B), Color(0xFF7C3AED)).random()
                val radius = Random.nextFloat() * 8f + 4f
                val delay = Random.nextFloat() * 0.3f
            }
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        particles.forEach { p ->
            val pProgress = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (pProgress > 0f) {
                val currentY = h + (p.targetYOffset * pProgress)
                drawCircle(color = p.color.copy(alpha = (1f - pProgress).coerceIn(0f, 1f)), radius = p.radius, center = Offset(w * p.startX, currentY))
            }
        }
    }
}