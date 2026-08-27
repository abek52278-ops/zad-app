package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * كرة صوت زاد الحية — مستوحاة من ChatGPT Voice & Gemini Live:
 * كرة هلامية ضوئية ثلاثية الأبعاد تنبض وتتفاعل مع الصوت بنعومة فائقة،
 * مع موجات صوتية متحدة المركز وهالات مدارية متوهجة.
 *
 * - Idle: أزرق سماوي نقي (#38BDF8 -> #0284C7)
 * - Listening: أخضر زمردي متوهج (#34D399 -> #064E3B)
 * - Thinking: بنفسجي ليزري ذكي (#C084FC -> #7C3AED)
 * - Speaking: كهرماني شمسي دافئ (#FBBF24 -> #D97706)
 * - Error: مرجاني حيوي (#F87171 -> #DC2626)
 */
enum class OrbState { Idle, Listening, Thinking, Speaking, Error }

@Composable
fun ZadVoiceOrb(
    state: OrbState,
    size: Dp,
    breathe: Float = 1f,
    haloRotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    val targetColors = when (state) {
        OrbState.Idle -> Triple(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFFBAE6FD))
        OrbState.Listening -> Triple(Color(0xFF34D399), Color(0xFF064E3B), Color(0xFF6EE7B7))
        OrbState.Thinking -> Triple(Color(0xFFC084FC), Color(0xFF7C3AED), Color(0xFFE9D5FF))
        OrbState.Speaking -> Triple(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFFFEF3C7))
        OrbState.Error -> Triple(Color(0xFFF87171), Color(0xFFDC2626), Color(0xFFFEE2E2))
    }

    val primaryColor by animateColorAsState(targetColors.first, tween(600, easing = FastOutSlowInEasing), label = "orbPrimary")
    val deepColor by animateColorAsState(targetColors.second, tween(600, easing = FastOutSlowInEasing), label = "orbDeep")
    val highlightColor by animateColorAsState(targetColors.third, tween(600, easing = FastOutSlowInEasing), label = "orbHighlight")

    val infiniteTransition = rememberInfiniteTransition(label = "orbHarmonics")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing)),
        label = "orbWavePhase"
    )
    val innerPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbInnerPulse"
    )
    val ringExpansion by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "orbRingExpand"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "orbRingAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // ── 1. موجات الصوت المتوسعة (Sound Ripple Waves) ──
        if (state == OrbState.Listening || state == OrbState.Speaking) {
            Canvas(modifier = Modifier.size(size * 1.5f)) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val maxRadius = this.size.minDimension / 2
                drawCircle(
                    color = primaryColor.copy(alpha = ringAlpha * 0.45f),
                    radius = maxRadius * ringExpansion,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = highlightColor.copy(alpha = (ringAlpha * 0.25f).coerceIn(0f, 1f)),
                    radius = maxRadius * (ringExpansion * 0.75f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // ── 2. الهالات المدارية الدوارة (Cosmic Orbital Rings) ──
        Canvas(
            modifier = Modifier
                .size(size * 1.15f)
                .rotate(haloRotationDegrees)
        ) {
            val stroke = 2.5.dp.toPx()
            val arcRadius = this.size.minDimension / 2 - stroke * 3
            val center = Offset(this.size.width / 2, this.size.height / 2)

            // مدار أول
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.4f to primaryColor.copy(alpha = 0.65f),
                    0.8f to highlightColor.copy(alpha = 0.9f),
                    1f to Color.Transparent
                ),
                startAngle = 0f,
                sweepAngle = 310f,
                useCenter = false,
                style = Stroke(width = stroke)
            )

            // مدار ثاني عكسي
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.5f to deepColor.copy(alpha = 0.45f),
                    1f to Color.Transparent
                ),
                startAngle = 180f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = stroke * 0.75f)
            )

            // ذرات ضوئية متلألئة تدور على المدار
            val dot1Angle = Math.toRadians(haloRotationDegrees * 1.5 % 360.0)
            val dot2Angle = Math.toRadians((haloRotationDegrees * 1.5 + 180) % 360.0)
            drawCircle(
                color = highlightColor,
                radius = stroke * 1.4f,
                center = Offset(
                    x = center.x + arcRadius * cos(dot1Angle).toFloat(),
                    y = center.y + arcRadius * sin(dot1Angle).toFloat()
                )
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.7f),
                radius = stroke * 1.0f,
                center = Offset(
                    x = center.x + arcRadius * 0.88f * cos(dot2Angle).toFloat(),
                    y = center.y + arcRadius * 0.88f * sin(dot2Angle).toFloat()
                )
            )
        }

        // ── 3. الكرة الهلامية الحية ثلاثية الأبعاد (Live 3D Fluid Blob) ──
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .scale(breathe * innerPulse)
        ) {
            Canvas(modifier = Modifier.size(size * 0.72f)) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val baseRadius = this.size.minDimension / 2 * 0.85f

                // توهج خلفي مشع
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.45f),
                            deepColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.6f
                    ),
                    radius = baseRadius * 1.5f,
                    center = center
                )

                // رسم الشكل الهلامي متعدد التوافقيات (Deformable Multi-Harmonic Blob)
                val path = Path()
                val points = 32
                for (i in 0..points) {
                    val angle = (i.toFloat() / points) * (2 * PI).toFloat()
                    val wave1 = sin(angle * 3f + wavePhase) * 0.055f
                    val wave2 = cos(angle * 2f - wavePhase * 1.3f) * 0.045f
                    val r = baseRadius * (1f + wave1 + wave2)
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                // التدرج اللوني العميق ثلاثي الأبعاد
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            highlightColor,
                            primaryColor,
                            deepColor
                        ),
                        center = center - Offset(baseRadius * 0.35f, baseRadius * 0.35f),
                        radius = baseRadius * 1.45f
                    )
                )

                // لمعة زجاجية كروية علوية (3D Specular Highlight)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = center - Offset(baseRadius * 0.32f, baseRadius * 0.38f),
                        radius = baseRadius * 0.55f
                    ),
                    radius = baseRadius * 0.50f,
                    center = center - Offset(baseRadius * 0.30f, baseRadius * 0.35f)
                )

                // توهج حافة ثانوية
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = center + Offset(baseRadius * 0.25f, baseRadius * 0.25f),
                        radius = baseRadius * 0.6f
                    ),
                    radius = baseRadius * 0.45f,
                    center = center + Offset(baseRadius * 0.22f, baseRadius * 0.22f)
                )
            }
        }
    }
}
