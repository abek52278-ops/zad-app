package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyFormatter
import androidx.compose.ui.platform.LocalContext

/**
 * High-fidelity Catmull-Rom Bezier Smooth Curve Chart.
 * Translates sparkline() and high-fidelity bezier chart from Image 5 & HTML v5 into native Kotlin Jetpack Compose.
 */
@Composable
fun ZadBezierSpendChart(
    weeklySpend: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(weeklySpend) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }

    val maxSpend = remember(weeklySpend) {
        weeklySpend.maxOfOrNull { it.second }?.coerceAtLeast(100.0) ?: 100.0
    }
    val peakIndex = remember(weeklySpend) {
        weeklySpend.indexOfFirst { it.second == maxSpend }
    }
    val totalWeekly = remember(weeklySpend) {
        weeklySpend.sumOf { it.second }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.04f))
            .padding(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "نبض الإنفاق الحي",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "إجمالي الأسبوع: ${CurrencyFormatter.format(context, totalWeekly)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B7280)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFE6F4EC))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "آخر 7 أيام",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bezier Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (weeklySpend.isEmpty()) return@Canvas

                val w = size.width
                val h = size.height
                val paddingBottom = 20f
                val chartHeight = h - paddingBottom
                val stepX = w / (weeklySpend.size - 1).coerceAtLeast(1)

                // Calculate point coordinates
                val points = weeklySpend.mapIndexed { index, pair ->
                    val normalizedY = (pair.second / maxSpend).toFloat().coerceIn(0.05f, 0.95f)
                    val x = index * stepX
                    val y = chartHeight - (normalizedY * chartHeight * progress.value)
                    Offset(x, y)
                }

                // Construct smooth Catmull-Rom Bezier Path
                val curvePath = Path()
                val fillPath = Path()

                curvePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, chartHeight)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = if (i > 0) points[i - 1] else points[i]
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = if (i < points.size - 2) points[i + 2] else p2

                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                    val cp1y = p1.y + (p2.y - p0.y) / 6f

                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                    val cp2y = p2.y - (p3.y - p1.y) / 6f

                    curvePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    fillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }

                fillPath.lineTo(points.last().x, chartHeight)
                fillPath.close()

                // Draw Gradient Fill under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F9B76).copy(alpha = 0.35f),
                            Color(0xFF0F9B76).copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Draw Main Curve Stroke
                drawPath(
                    path = curvePath,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF064E3B), Color(0xFF0F9B76), Color(0xFF34D399))
                    ),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Highlight Peak Dot
                if (peakIndex in points.indices && maxSpend > 0) {
                    val peakPoint = points[peakIndex]
                    drawCircle(
                        color = Color(0xFF064E3B),
                        radius = 6.dp.toPx(),
                        center = peakPoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = peakPoint
                    )
                }
            }
        }

        // Day Labels Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weeklySpend.forEach { (day, _) ->
                Text(
                    text = day,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
