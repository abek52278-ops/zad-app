package com.example.ui.components

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * كرة صوت زاد — بأسلوب ChatGPT Voice / Gemini Live: كرة واحدة متوهجة، بدون وجه
 * كرتوني. بتتنفس مع مستوى الصوت، واللون بيتغير حسب الحالة:
 * - Idle: أزرق هادئ
 * - Listening: أخضر فيروزي (بيسمعك)
 * - Thinking: بنفسجي (بيفكر)
 * - Speaking: برتقالي دافئ (بيرد عليك)
 * - Error: أحمر خافت
 *
 * الهالة الخارجية بتلف ببطء — إحساس "حية" حتى لما مفيش كلام.
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
    val (coreColor, haloColor) = when (state) {
        OrbState.Idle -> Color(0xFF4A7DFF) to Color(0xFF6C9BFF)
        OrbState.Listening -> Color(0xFF00BFA6) to Color(0xFF4DD8C4)
        OrbState.Thinking -> Color(0xFF8B5CF6) to Color(0xFFB48CFF)
        OrbState.Speaking -> Color(0xFFFF8A3D) to Color(0xFFFFB27D)
        OrbState.Error -> Color(0xFFE05252) to Color(0xFFFF8A8A)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // V4 — ripple rings behind the orb while listening (ai_orb.json port)
        if (state == OrbState.Listening) {
            com.example.ui.v2.AiRippleRings(
                modifier = Modifier.size(size * 1.9f),
                ringColor = haloColor,
            )
        }
        // الهالة الدوارة (قوسان بزاوية) — بتلف ببطء
        Canvas(
            modifier = Modifier
                .size(size)
                .rotate(haloRotationDegrees)
        ) {
            val stroke = 3.dp.toPx()
            val arcRadius = this.size.minDimension / 2 - stroke * 2
            // قوسان متقابلان — يعطي إحساس مدار
            drawArc(
                brush = Brush.sweepGradient(
                    0f to haloColor.copy(alpha = 0.0f),
                    0.5f to haloColor.copy(alpha = 0.55f),
                    1f to haloColor.copy(alpha = 0.0f)
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0f to coreColor.copy(alpha = 0.0f),
                    0.5f to coreColor.copy(alpha = 0.35f),
                    1f to coreColor.copy(alpha = 0.0f)
                ),
                startAngle = 180f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = stroke * 0.7f)
            )
            // نقطة صغيرة على المدار — توحي بالحركة
            drawCircle(
                color = haloColor.copy(alpha = 0.8f),
                radius = stroke * 0.9f,
                center = Offset(
                    x = this.center.x + arcRadius * kotlin.math.cos(Math.toRadians(45.0)).toFloat(),
                    y = this.center.y + arcRadius * kotlin.math.sin(Math.toRadians(45.0)).toFloat()
                )
            )
        }

        // الكرة الأساسية — تتنفس
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .scale(breathe)
        ) {
            Canvas(modifier = Modifier.size(size * 0.62f)) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val radius = this.size.minDimension / 2

                // توهج خارجي
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                // الجسم الأساسي — تدرج داخلي
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            haloColor,
                            coreColor
                        ),
                        center = center.copy(
                            x = center.x - radius * 0.25f,
                            y = center.y - radius * 0.25f
                        ),
                        radius = radius * 1.3f
                    ),
                    radius = radius * 0.78f,
                    center = center
                )
                // لمعة علوية — إحساس كروي ثلاثي الأبعاد
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = center.copy(
                            x = center.x - radius * 0.3f,
                            y = center.y - radius * 0.35f
                        ),
                        radius = radius * 0.5f
                    ),
                    radius = radius * 0.45f,
                    center = center.copy(
                        x = center.x - radius * 0.28f,
                        y = center.y - radius * 0.32f
                    )
                )
            }
        }
    }
}
