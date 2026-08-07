package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * حالة الأيجنت العاطفية — كل حالة بتحدد لون الكورة وشكل العيون.
 * أزرق: عادي/هادئ. بنفسجي: مركّز/بيحلل. أخضر: سعيد/إنجاز. أحمر: تنبيه.
 */
enum class CompanionState(val skyColor: Color, val deepColor: Color) {
    Idle(Color(0xFF6CC3FF), Color(0xFF1C6FE0)),
    Focused(Color(0xFFB388FF), Color(0xFF4A148C)),
    Happy(Color(0xFF7CFFB2), Color(0xFF00B26A)),
    Alert(Color(0xFFFF8A80), Color(0xFFD32F2F))
}

/** الوصف المسموع لحالة الأيجنت — لقارئ الشاشة، الشكل واللون بصريين بس. */
fun companionStateDescription(state: CompanionState): String = when (state) {
    CompanionState.Idle -> "زاد: هادئ"
    CompanionState.Focused -> "زاد: بيفكر"
    CompanionState.Happy -> "زاد: مبسوط"
    CompanionState.Alert -> "زاد: تنبيه"
}

/**
 * الكورة الهلامية — أفتار الأيجنت.
 *
 * [animated] بيتحكم في كل الحركة المستمرة (نبض + تموّج السائل + الرمش العشوائي). خليه true
 * بس في الأماكن البارزة (رأس الشاشة/الشات) — نسخة كل فقاعة رسالة في لستة طويلة بتتقفل
 * (animated=false) عشان مانشغلش عشرات الـ infinite animation loops مع بعض في LazyColumn.
 */
@Composable
fun CompanionOrb(
    state: CompanionState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animated: Boolean = true
) {
    val skyColor by animateColorAsState(state.skyColor, tween(500), label = "orbSky")
    val deepColor by animateColorAsState(state.deepColor, tween(500), label = "orbDeep")

    val breathScale: Float
    val blobPhase: Float
    val eyeOpenAmount: Float

    if (animated) {
        val breathTransition = rememberInfiniteTransition(label = "orbBreath")
        breathScale = breathTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbBreathScale"
        ).value

        val blobTransition = rememberInfiniteTransition(label = "orbBlob")
        blobPhase = blobTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing)
            ),
            label = "orbBlobPhase"
        ).value

        var eyeOpen by remember { mutableFloatStateOf(1f) }
        val eyeOpenAnimated by animateFloatAsState(eyeOpen, tween(90), label = "orbBlink")
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2200, 5000))
                eyeOpen = 0.08f
                delay(110)
                eyeOpen = 1f
            }
        }
        eyeOpenAmount = eyeOpenAnimated
    } else {
        breathScale = 1f
        blobPhase = 0f
        eyeOpenAmount = 1f
    }

    val description = companionStateDescription(state)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val radius = (this.size.minDimension / 2f) * breathScale
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // glow behind the body — a few widening, fading rings instead of a real blur
        drawCircle(color = skyColor.copy(alpha = 0.18f), radius = radius * 1.35f, center = center)
        drawCircle(color = skyColor.copy(alpha = 0.28f), radius = radius * 1.15f, center = center)

        drawPath(
            path = blobPath(center, radius, blobPhase),
            brush = Brush.radialGradient(
                colors = listOf(skyColor, deepColor),
                center = center - Offset(radius * 0.3f, radius * 0.3f),
                radius = radius * 1.6f
            )
        )

        drawEyes(state, center, radius, eyeOpenAmount)
    }
}

private const val BLOB_POINTS = 8
private const val BLOB_AMPLITUDE = 0.045f

/**
 * شكل الكورة كسائل عضوي بدل دايرة ثابتة: نقط حوالين المحيط، كل واحدة نصف قطرها بيتموّج
 * بمعدل وطور مختلف عن التانية (موجات جيبية غير متزامنة)، متوصلة بمنحنيات ناعمة (quadratic
 * لكل نقطة نص المسافة للنقطة الجاية) بدل خطوط مستقيمة — نفس أسلوب "blob shape" الشائع.
 */
private fun blobPath(center: Offset, baseRadius: Float, phase: Float): Path {
    val points = (0 until BLOB_POINTS).map { i ->
        val angle = (i.toFloat() / BLOB_POINTS) * 2 * Math.PI.toFloat()
        val freq = 1.5f + (i % 3) * 0.7f
        val wobble = 1f + BLOB_AMPLITUDE * sin(phase * freq + i * 1.1f)
        val r = baseRadius * wobble
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    state: CompanionState,
    center: Offset,
    radius: Float,
    openAmount: Float
) {
    val eyeSpacing = radius * 0.42f
    val eyeWidth = radius * 0.26f
    val leftCenter = center - Offset(eyeSpacing, 0f)
    val rightCenter = center + Offset(eyeSpacing, 0f)

    when (state) {
        CompanionState.Happy -> {
            val heartHeight = radius * 0.5f * openAmount
            drawHeart(leftCenter, eyeWidth, heartHeight)
            drawHeart(rightCenter, eyeWidth, heartHeight)
        }
        else -> {
            val baseHeight = when (state) {
                CompanionState.Focused -> radius * 0.32f
                CompanionState.Alert -> radius * 0.44f
                else -> radius * 0.42f
            }
            val eyeHeight = baseHeight * openAmount
            drawRoundRect(
                color = Color.White,
                topLeft = leftCenter - Offset(eyeWidth / 2f, eyeHeight / 2f),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight.coerceAtLeast(3f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeWidth / 2f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = rightCenter - Offset(eyeWidth / 2f, eyeHeight / 2f),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight.coerceAtLeast(3f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeWidth / 2f)
            )
        }
    }
}

private val alertToneWords = listOf("تنبيه", "تحذير", "خطر", "حذر", "تجاوزت", "نفاد", "أوشك", "قارب على النفاد")
private val happyToneWords = listOf("ممتاز", "أحسنت", "تهانينا", "مبروك", "رائع", "وفرت", "نجحت", "تحقيق هدف")

/** استنتاج حالة الأيجنت من نص رسالة الشات — مافيش استدعاء AI جديد، تصنيف كلمات مفتاحية محلي بس. */
fun companionStateForMessage(text: String): CompanionState = when {
    alertToneWords.any { text.contains(it) } -> CompanionState.Alert
    happyToneWords.any { text.contains(it) } -> CompanionState.Happy
    else -> CompanionState.Idle
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    at: Offset,
    width: Float,
    height: Float
) {
    if (height < 3f) return
    val hw = width / 2f
    val path = Path().apply {
        moveTo(at.x, at.y - height * 0.35f)
        cubicTo(
            at.x - hw * 1.1f, at.y - height * 0.85f,
            at.x - hw * 1.3f, at.y + height * 0.05f,
            at.x, at.y + height * 0.55f
        )
        cubicTo(
            at.x + hw * 1.3f, at.y + height * 0.05f,
            at.x + hw * 1.1f, at.y - height * 0.85f,
            at.x, at.y - height * 0.35f
        )
        close()
    }
    drawPath(path, color = Color.White)
}
