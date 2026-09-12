package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.voice.ZadCutePetSoundFx
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ZadVoicePet - Interactive pet-like companion character for the voice agent.
 * 
 * Visual states:
 * - Idle / Watching: Soft floating bounce with pupils slowly tracking cursor/movement
 * - Listening: Outer glowing emerald/blue pulse and expanding ring
 * - Thinking / Processing: Dynamic fluid gradient rotation (ElevenAgents/Siri glassmorphic aesthetic)
 * - Speaking: Mouth/eye bouncy reaction synced with audio output frequency
 * - Sleeping: Closed curved eyes (- -) with gentle breathing animation when inactive
 * 
 * All animations run on the draw scope (invalidateDraw) — no recomposition from audio.
 */
enum class VoicePetState(val skyColor: Color, val deepColor: Color) {
    Idle(ZadOrbIdleSky, ZadOrbIdleDeep),
    Listening(ZadOrbListeningSky, ZadOrbListeningDeep),
    Thinking(ZadOrbFocusedSky, ZadOrbFocusedDeep),
    Speaking(ZadOrbSpeakingSky, ZadOrbSpeakingDeep),
    Sleeping(ZadOrbIdleSky.copy(alpha = 0.5f), ZadOrbIdleDeep.copy(alpha = 0.5f))
}

@Composable
fun voicePetStateDescription(state: VoicePetState): String = stringResource(
    when (state) {
        VoicePetState.Idle -> R.string.voice_pet_state_idle
        VoicePetState.Listening -> R.string.voice_pet_state_listening
        VoicePetState.Thinking -> R.string.voice_pet_state_thinking
        VoicePetState.Speaking -> R.string.voice_pet_state_speaking
        VoicePetState.Sleeping -> R.string.voice_pet_state_sleeping
    }
)

/**
 * Unified audio level source for the pet orb.
 * Takes a StateFlow, smooths it, and returns a lambda that reads the smoothed value
 * inside the draw scope (no recomposition).
 */
@Composable
fun rememberPetAudioLevel(levelFlow: kotlinx.coroutines.flow.StateFlow<Float>?): () -> Float {
    val smoothed = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(levelFlow) {
        if (levelFlow == null) {
            smoothed.floatValue = 0f
            return@LaunchedEffect
        }
        levelFlow.collect { raw ->
            smoothed.floatValue = smoothOrbLevel(smoothed.floatValue, raw)
        }
    }
    return remember(smoothed) { { smoothed.floatValue } }
}

/**
 * Main ZadVoicePet composable — the interactive pet character.
 * 
 * @param state Current emotional state (Idle, Listening, Thinking, Speaking, Sleeping)
 * @param size Diameter of the pet orb
 * @param animated Whether to run continuous animations (breathing, blob morph, rotation)
 * @param blinkTrigger External blink trigger (tap = double blink)
 * @param glowTrigger External glow trigger (tap = pulse)
 * @param audioLevel Lambda returning current audio level 0..1 (for mouth/eye sync)
 * @param onClick Tap handler — triggers bounce + sound + double blink + glow
 */
@Composable
fun ZadVoicePet(
    state: VoicePetState,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    animated: Boolean = true,
    blinkTrigger: Long = 0L,
    glowTrigger: Long = 0L,
    audioLevel: () -> Float = { 0f },
    onClick: (() -> Unit)? = null
) {
    var tapPulse by remember { mutableStateOf(0L) }
    val effectiveBlink = if (tapPulse != 0L) tapPulse else blinkTrigger
    val effectiveGlow = if (tapPulse != 0L) tapPulse else glowTrigger

    val skyColor by animateColorAsState(state.skyColor, tween(500), label = "petSky")
    val deepColor by animateColorAsState(state.deepColor, tween(500), label = "petDeep")

    val breathScale: Float
    val blobPhase: Float
    val rotationAngle: Float
    val sheenProgress: Float

    if (animated) {
        val breathTransition = rememberInfiniteTransition(label = "petBreath")
        breathScale = breathTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "petBreathScale"
        ).value

        val blobTransition = rememberInfiniteTransition(label = "petBlob")
        blobPhase = blobTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing)
            ),
            label = "petBlobPhase"
        ).value

        val rotateTransition = rememberInfiniteTransition(label = "petRotate")
        rotationAngle = rotateTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "petRotation"
        ).value

        val sheenTransition = rememberInfiniteTransition(label = "petSheen")
        sheenProgress = sheenTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "petSheen"
        ).value
    } else {
        breathScale = 1f
        blobPhase = 0f
        rotationAngle = 0f
        sheenProgress = 0.3f
    }

    var eyeOpen by remember { mutableFloatStateOf(1f) }
    val eyeOpenAnimated by animateFloatAsState(
        targetValue = eyeOpen,
        animationSpec = tween(85, easing = FastOutSlowInEasing),
        label = "petBlink"
    )

    LaunchedEffect(effectiveBlink) {
        if (effectiveBlink != 0L) {
            eyeOpen = 0.08f
            delay(90)
            eyeOpen = 1f
            delay(70)
            eyeOpen = 0.08f
            delay(90)
            eyeOpen = 1f
        }
    }

    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2400, 5200))
            eyeOpen = 0.08f
            delay(100)
            eyeOpen = 1f
        }
    }

    var glowTarget by remember { mutableFloatStateOf(0f) }
    val glow by animateFloatAsState(glowTarget, tween(450, easing = FastOutSlowInEasing), label = "petGlow")
    LaunchedEffect(effectiveGlow) {
        if (effectiveGlow != 0L) {
            glowTarget = 1f
            delay(120)
            glowTarget = 0f
        }
    }

    val tapScale by animateFloatAsState(
        targetValue = if (glowTarget > 0f) 0.92f else 1f,
        animationSpec = ZadSprings.Press,
        label = "petTapScale"
    )

    val petModifier = if (animated) {
        val description = voicePetStateDescription(state)
        modifier.size(size).semantics { contentDescription = description }
    } else {
        modifier.size(size)
    }
    val clickableModifier = if (onClick != null) {
        petModifier
            .scale(tapScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tapPulse = System.currentTimeMillis()
                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.5f)
                onClick()
            }
    } else {
        petModifier
    }

    Canvas(modifier = clickableModifier) {
        val level = audioLevel().coerceIn(0f, 1f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension / 2f) * 0.68f
        val radius = baseRadius * breathScale * (1f + 0.16f * level)

        // كثافة الزخرفة (حلقات + جزيئات) بتتبع الحالة مش ثابتة. الكورة الساكنة
        // المفروض تتقري ككرة مصمتة ليها عمق — الحلقات النيون الدايمة كانت بتحوّلها
        // لرسم تقني. بتولع لما تسمع أو تتكلم، وبتخفت لما تكون ساكنة.
        val decor = when (state) {
            VoicePetState.Listening, VoicePetState.Speaking -> 1f
            VoicePetState.Thinking -> 0.55f
            VoicePetState.Idle -> 0.20f
            VoicePetState.Sleeping -> 0.08f
        }

        // 1. Ambient Atmospheric Neon Aura (breathes with audio)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.28f + 0.35f * level),
                    deepColor.copy(alpha = 0.10f + 0.15f * level),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.80f + 0.40f * level)
            ),
            radius = radius * (1.80f + 0.40f * level),
            center = center
        )

        // 2. Waveform Resonance Ring (Inner Energetic Orbital Ring)
        val ring1Radius = radius * (1.24f + 0.16f * level)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ZadOrbNeonCyan.copy(alpha = (0.45f + 0.45f * level) * decor),
                    ZadOrbNeonMint.copy(alpha = 0.20f * decor),
                    ZadOrbNeonCyan.copy(alpha = (0.45f + 0.45f * level) * decor)
                ),
                center = center
            ),
            radius = ring1Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.5f + 2f * level).dp.toPx()
            )
        )

        // 3. Soundwave Ripple Ring (Outer Acoustic Ring) - prominent when Listening/Speaking
        val ring2Radius = radius * (1.46f + 0.26f * level)
        drawCircle(
            color = ZadOrbNeonMint.copy(alpha = ((0.16f + 0.32f * level) * decor).coerceIn(0f, 0.75f)),
            radius = ring2Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.0f + 1.2f * level).dp.toPx()
            )
        )

        // 4. Luminous Orbital Motes (6 rotating energy particles)
        if (decor > 0.25f) {
            for (i in 0 until 6) {
                val moteAngle = (rotationAngle * 0.6f + i * 60f) * (Math.PI / 180.0)
                val moteDist = radius * (1.35f + 0.10f * sin(blobPhase + i).toFloat() * (1f + level))
                val moteCenter = Offset(
                    center.x + (moteDist * cos(moteAngle)).toFloat(),
                    center.y + (moteDist * sin(moteAngle)).toFloat()
                )
                drawCircle(
                    color = ZadOrbNeonCyan.copy(alpha = ((0.35f + 0.45f * level) * decor).coerceIn(0f, 1f)),
                    radius = (1.8f + 1.2f * level).dp.toPx(),
                    center = moteCenter
                )
            }
        }

        // 5. Quick Tap Glow Burst
        if (glow > 0.01f) {
            drawCircle(
                color = ZadOrbNeonCyan.copy(alpha = 0.45f * glow),
                radius = radius * (1.4f + 0.5f * glow),
                center = center
            )
        }

        // 6. Living Liquid Glass Core Body
        val bodyPath = organicOrbPath(center, radius, blobPhase, level)

        // 6a. Volumetric Deep Spherical Gradient (3D Light Source at Top-Left)
        val lightOffset = center - Offset(radius * 0.35f, radius * 0.38f)
        drawPath(
            path = bodyPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    ZadOrbGlassSpecular.copy(alpha = 0.95f),
                    skyColor.copy(alpha = 0.88f),
                    ZadOrbNeonMint.copy(alpha = 0.72f),
                    deepColor.copy(alpha = 0.95f),
                    ZadOrbCoreDark
                ),
                center = lightOffset,
                radius = radius * 1.55f
            )
        )

        // 6b. Chromatic Glass Mesh Sweep Rotation
        withTransform({
            rotate(degrees = rotationAngle, pivot = center)
        }) {
            // شفافية أقل من الأول بقصد: الطبقة دي بتدي إحساس زجاج متحرك، لكن لما كانت
            // تقيلة كانت بتغطي التدرج الكروي تحتها فالكورة تتقري سطح مرسوم مش جسم له
            // عمق. الحجم اللي بيدي الإحساس ده جاي من 6a + الضوء المرتد في 6e.
            val meshSweepBrush = Brush.sweepGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.26f),
                    ZadOrbMeshMint.copy(alpha = 0.20f),
                    deepColor.copy(alpha = 0.38f),
                    ZadOrbNeonCyan.copy(alpha = 0.24f + 0.20f * level),
                    ZadOrbMeshTeal.copy(alpha = 0.20f),
                    deepColor.copy(alpha = 0.38f),
                    skyColor.copy(alpha = 0.26f)
                ),
                center = center
            )
            drawPath(path = bodyPath, brush = meshSweepBrush)
        }

        // 6c. Top-Left Glass Specular Sheen Crescent
        val highlightCenter = center - Offset(radius * 0.28f, radius * 0.30f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.75f * sheenProgress + 0.25f * level).coerceIn(0f, 0.95f)),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = highlightCenter,
                radius = radius * 0.50f
            ),
            radius = radius * 0.50f,
            center = highlightCenter
        )

        // 6e. الضوء المرتد من تحت (bounce light) — ده اللي بيخلي الكورة تتقري كجسم
        // كروي مش كدايرة ملونة: مصدر ضوء أساسي فوق-شمال (6a) + انعكاس خافت تحت-يمين
        // من الأرضية. من غيره النص السفلي بيتقري مسطّح.
        val bounceCenter = center + Offset(radius * 0.30f, radius * 0.44f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.34f),
                    skyColor.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = bounceCenter,
                radius = radius * 0.62f
            ),
            radius = radius * 0.62f,
            center = bounceCenter
        )

        // 6d. Fresnel Edge Glass Rim
        drawPath(
            path = bodyPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    ZadOrbNeonCyan.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.90f),
                    deepColor.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.85f)
                ),
                center = center
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.8.dp.toPx() * (1f + 0.45f * level))
            )
        )

        // 7. Dynamic Expressive Living Eyes (state-driven)
        drawPetEyes(
            state = state,
            center = center,
            radius = radius,
            openAmount = eyeOpenAnimated,
            audioLevel = level,
            sheenProgress = sheenProgress
        )
    }
}

/**
 * Draws expressive eyes based on pet state and audio level.
 * Each state has unique eye morphology and pupil behavior.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPetEyes(
    state: VoicePetState,
    center: Offset,
    radius: Float,
    openAmount: Float,
    audioLevel: Float,
    sheenProgress: Float
) {
    val eyeSpacing = radius * 0.38f
    val eyeWidth = radius * 0.22f
    val baseEyeY = center.y - radius * 0.08f
    val leftCenter = Offset(center.x - eyeSpacing, baseEyeY)
    val rightCenter = Offset(center.x + eyeSpacing, baseEyeY)

    when (state) {
        VoicePetState.Listening -> {
            // Wide attentive eyes with pupils tracking (subtle upward gaze = listening)
            val eyeHeight = (radius * 0.42f + radius * 0.14f * audioLevel) * openAmount
            val pupilOffset = Offset(0f, -radius * 0.03f)
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
        }
        VoicePetState.Speaking -> {
            // Eyes bounce with speech rhythm (synced to audioLevel)
            val eyeHeight = (radius * 0.38f + radius * 0.12f * audioLevel) * openAmount
            val pupilOffset = Offset(0f, radius * 0.02f * sin(sheenProgress * Math.PI.toFloat()))
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
        }
        VoicePetState.Thinking -> {
            // Narrow focused eyes, no pupils visible = deep concentration
            val eyeHeight = radius * 0.28f * openAmount
            drawFocusedEye(leftCenter, eyeWidth * 1.15f, eyeHeight)
            drawFocusedEye(rightCenter, eyeWidth * 1.15f, eyeHeight)
        }
        VoicePetState.Sleeping -> {
            // Closed curved eyes (- -) with gentle breathing
            val eyeHeight = radius * 0.12f * openAmount
            drawSleepingEye(leftCenter, eyeWidth * 1.2f, eyeHeight)
            drawSleepingEye(rightCenter, eyeWidth * 1.2f, eyeHeight)
        }
        VoicePetState.Idle -> {
            // Normal expressive eyes, pupils centered
            val eyeHeight = radius * 0.38f * openAmount
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, Offset.Zero, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, Offset.Zero, openAmount)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpressiveEye(
    center: Offset,
    width: Float,
    height: Float,
    pupilOffset: Offset,
    openAmount: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, minOf(width / 2f, height / 2f))
    // عين كبسولة بيضا صافية من غير بؤبؤ داكن — ده شكل المرجع اللي المستخدم بعته،
    // وهو كمان بيقرا أنضف على حجم 56dp في الرئيسية (البؤبؤ + اللمعة كانوا بيتحوّلوا
    // لبقعتين رماديتين). الاتجاه بيتنقل بإزاحة الكبسولة نفسها مش بحركة بؤبؤ جواها.
    val eyeCenter = center + pupilOffset * openAmount
    val eyeSize = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f))
    val eyeTopLeft = Offset(eyeCenter.x - width / 2f, eyeCenter.y - eyeSize.height / 2f)
    drawRoundRect(
        color = Color.White,
        topLeft = eyeTopLeft,
        size = eyeSize,
        cornerRadius = cornerRadius
    )
    // ظل داخلي خفيف تحت — بيمنع الكبسولة إنها تتقري ستيكر مسطّح ملزوق على الكورة.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, ZadDarkSlate.copy(alpha = 0.12f)),
            startY = eyeTopLeft.y + eyeSize.height * 0.45f,
            endY = eyeTopLeft.y + eyeSize.height
        ),
        topLeft = eyeTopLeft,
        size = eyeSize,
        cornerRadius = cornerRadius
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusedEye(
    center: Offset,
    width: Float,
    height: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, height / 2f)
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f)),
        cornerRadius = cornerRadius
    )
    // Small centered dot = focused gaze
    drawCircle(
        color = ZadOrbFocusedDeep,
        radius = minOf(width, height) * 0.32f,
        center = center
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSleepingEye(
    center: Offset,
    width: Float,
    height: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, height / 2f)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f)),
        cornerRadius = cornerRadius
    )
    // Closed eye curve (- -)
    val path = Path().apply {
        moveTo(center.x - width * 0.4f, center.y)
        quadraticTo(center.x, center.y - height * 0.5f, center.x + width * 0.4f, center.y)
    }
    drawPath(
        path = path,
        color = ZadDarkSlate,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.5f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    )
}

/**
 * Organic blob path — multi-tone sine wave producing a living jelly-like shape.
 */
private fun organicOrbPath(center: Offset, baseRadius: Float, phase: Float, level: Float = 0f): Path {
    val pointsCount = 12
    val amplitude = 0.042f * (1f + 1.2f * level)
    val points = (0 until pointsCount).map { i ->
        val angle = (i.toFloat() / pointsCount) * 2 * Math.PI.toFloat()
        val freq1 = 2.0f
        val freq2 = 3.0f
        val wave = sin(phase * freq1 + i * 0.8f) * 0.6f + cos(phase * freq2 + i * 1.2f) * 0.4f
        val r = baseRadius * (1f + amplitude * wave.toFloat())
        Offset(center.x + r * cos(angle), center.y + r * sin(angle))
    }

    val path = Path()
    val start = Offset((points.last().x + points.first().x) / 2f, (points.last().y + points.first().y) / 2f)
    path.moveTo(start.x, start.y)
    for (i in points.indices) {
        val current = points[i]
        val next = points[(i + 1) % points.size]
        val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
        path.quadraticTo(current.x, current.y, mid.x, mid.y)
    }
    path.close()
    return path
}