package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.ZadCutePetSoundFx
import kotlinx.coroutines.delay

/**
 * Emotional state machine for the Zad SmartBot agent.
 */
enum class ZadBotEmotion {
    IDLE,
    HAPPY,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * Zad SmartBot Agent — 3D Glassmorphic Emerald Orb with Living Expressive Eyes.
 * Matches Image 5 specification.
 */
@Composable
fun ZadSmartBotAgent(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 84.dp,
    emotion: ZadBotEmotion = ZadBotEmotion.IDLE,
    onClick: (() -> Unit)? = null
) {
    // ── Eye Blinking Animation ────────────────────────────────────────────────
    val blinkAnim = remember { Animatable(1f) }
    var eyeLookX by remember { mutableStateOf(0f) }
    var eyeLookY by remember { mutableStateOf(0f) }

    LaunchedEffect(emotion) {
        while (true) {
            delay((2800..4500).random().toLong())
            if (emotion != ZadBotEmotion.SPEAKING) {
                // Quick double or single blink
                blinkAnim.animateTo(0.12f, tween(90, easing = LinearEasing))
                blinkAnim.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                if ((0..2).random() == 1) {
                    delay(120)
                    blinkAnim.animateTo(0.12f, tween(70, easing = LinearEasing))
                    blinkAnim.animateTo(1f, tween(90, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    // Thinking glance motion
    LaunchedEffect(emotion) {
        if (emotion == ZadBotEmotion.THINKING) {
            eyeLookX = -4f
            eyeLookY = -6f
        } else {
            eyeLookX = 0f
            eyeLookY = 0f
        }
    }

    // Continuous orbital rotation & glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "smartbot_ambient")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit_angle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_pulse"
    )
    val harmonicWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "harmonic_wave"
    )

    // Spring tap feedback
    var isPressed by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tap_scale"
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .scale(tapScale)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                spotColor = Color(0xFF10B981).copy(alpha = 0.45f),
                ambientColor = Color(0xFF047857).copy(alpha = 0.25f)
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = w * 0.45f

            // 1. Outer Glassmorphic Halo Glow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF34D399).copy(alpha = 0.35f * glowPulse),
                        Color(0xFF0F9B76).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = center
            )

            // 2. Primary 3D Emerald Orb Body
            val orbGradient = when (emotion) {
                ZadBotEmotion.THINKING -> listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95), Color(0xFF0A382C))
                ZadBotEmotion.LISTENING -> listOf(Color(0xFF34D399), Color(0xFF0F9B76), Color(0xFF052E16))
                ZadBotEmotion.HAPPY -> listOf(Color(0xFFFBBF24), Color(0xFF0F9B76), Color(0xFF052E16))
                else -> listOf(Color(0xFF10B981), Color(0xFF064E3B), Color(0xFF052E16))
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = orbGradient,
                    center = Offset(center.x - radius * 0.25f, center.y - radius * 0.3f),
                    radius = radius * 1.3f
                ),
                radius = radius,
                center = center
            )

            // 3. Specular Glass Arc & Highlights
            drawArc(
                color = Color.White.copy(alpha = 0.45f),
                startAngle = 200f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
                size = Size(radius * 1.76f, radius * 1.76f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )

            // 4. Orbital Ring with Starlight Particle
            val orbitRadius = radius * 1.05f
            drawCircle(
                color = Color(0xFF6EE7B7).copy(alpha = 0.25f),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )
            val rad = Math.toRadians(orbitAngle.toDouble())
            val particlePos = Offset(
                center.x + (orbitRadius * Math.cos(rad)).toFloat(),
                center.y + (orbitRadius * Math.sin(rad)).toFloat()
            )
            drawCircle(
                color = Color(0xFFD9F2E6),
                radius = 3.5f,
                center = particlePos
            )

            // 5. Living Glowing Eyes (Image 5 specification: Dual Glowing White Capsules)
            val eyeWidth = w * 0.11f
            val eyeHeight = h * 0.26f * blinkAnim.value
            val eyeSpacing = w * 0.16f
            val eyeY = center.y - eyeHeight * 0.5f + eyeLookY

            val leftEyeX = center.x - eyeSpacing - eyeWidth * 0.5f + eyeLookX
            val rightEyeX = center.x + eyeSpacing - eyeWidth * 0.5f + eyeLookX

            // Eye glow halos
            drawRoundRect(
                color = Color(0xFF6EE7B7).copy(alpha = 0.6f),
                topLeft = Offset(leftEyeX - 2f, eyeY - 2f),
                size = Size(eyeWidth + 4f, eyeHeight + 4f),
                cornerRadius = CornerRadius(eyeWidth, eyeWidth)
            )
            drawRoundRect(
                color = Color(0xFF6EE7B7).copy(alpha = 0.6f),
                topLeft = Offset(rightEyeX - 2f, eyeY - 2f),
                size = Size(eyeWidth + 4f, eyeHeight + 4f),
                cornerRadius = CornerRadius(eyeWidth, eyeWidth)
            )

            // Eye primary shapes
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(leftEyeX, eyeY),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth, eyeWidth)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(rightEyeX, eyeY),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth, eyeWidth)
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
}
