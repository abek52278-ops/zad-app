package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyFormatter

/**
 * High-fidelity Catmull-Rom Bezier Smooth Curve Chart.
 * Translates the high-fidelity bezier chart from the user's mockup image into native Kotlin Jetpack Compose.
 */
@Composable
fun ZadBezierSpendChart(
    weeklySpend: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(weeklySpend) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
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
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.04f))
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
                    text = "منحنى نبض الإنفاق التحليلي",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "إجمالي الأسبوع: ${CurrencyFormatter.format(context, totalWeekly)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFE6F4EC))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "آخر 7 أيام 📈",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bezier Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(weeklySpend) {
                        detectTapGestures { tapOffset ->
                            if (weeklySpend.isNotEmpty()) {
                                val stepX = size.width / (weeklySpend.size - 1).coerceAtLeast(1)
                                val tappedIdx = (tapOffset.x / stepX).toInt().coerceIn(0, weeklySpend.size - 1)
                                selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                            }
                        }
                    }
            ) {
                if (weeklySpend.isEmpty()) return@Canvas

                val w = size.width
                val h = size.height
                val topPadding = 30f
                val paddingBottom = 16f
                val chartHeight = h - paddingBottom - topPadding
                val stepX = w / (weeklySpend.size - 1).coerceAtLeast(1)

                // Draw Horizontal Subtle Gridlines
                val gridLines = 4
                for (i in 0..gridLines) {
                    val gridY = topPadding + chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color(0xFFF1F5F9),
                        start = Offset(0f, gridY),
                        end = Offset(w, gridY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                // Calculate point coordinates
                val points = weeklySpend.mapIndexed { index, pair ->
                    val normalizedY = (pair.second / maxSpend).toFloat().coerceIn(0.04f, 0.96f)
                    val x = index * stepX
                    val y = topPadding + (chartHeight - (normalizedY * chartHeight * progress.value))
                    Offset(x, y)
                }

                // Construct smooth Catmull-Rom Bezier Path
                val curvePath = Path()
                val fillPath = Path()

                curvePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, topPadding + chartHeight)
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

                fillPath.lineTo(points.last().x, topPadding + chartHeight)
                fillPath.close()

                // Draw Gradient Fill under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F9B76).copy(alpha = 0.38f),
                            Color(0xFF0F9B76).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = topPadding,
                        endY = topPadding + chartHeight
                    )
                )

                // Draw Main Curve Stroke
                drawPath(
                    path = curvePath,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF052E16), Color(0xFF0F9B76), Color(0xFF34D399))
                    ),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw Point Circles & Peak Tooltip Badges
                val activeIdx = selectedIndex ?: peakIndex
                if (activeIdx in points.indices && maxSpend > 0) {
                    val activePoint = points[activeIdx]
                    val amountVal = weeklySpend[activeIdx].second

                    // Vertical dashed guideline to the bottom
                    drawLine(
                        color = Color(0xFF0F9B76).copy(alpha = 0.6f),
                        start = Offset(activePoint.x, activePoint.y),
                        end = Offset(activePoint.x, topPadding + chartHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Draw Peak Dot
                    drawCircle(
                        color = Color(0xFF064E3B),
                        radius = 7.dp.toPx(),
                        center = activePoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = activePoint
                    )

                    // Floating Peak Tooltip Bubble Badge
                    if (amountVal > 0) {
                        val labelText = "${amountVal.toInt()} ر.س"
                        val textLayout = textMeasurer.measure(
                            text = labelText,
                            style = TextStyle(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF064E3B)
                            )
                        )
                        val badgeW = textLayout.size.width + 16f
                        val badgeH = textLayout.size.height + 8f
                        val badgeX = (activePoint.x - badgeW / 2).coerceIn(4f, w - badgeW - 4f)
                        val badgeY = (activePoint.y - badgeH - 10f).coerceAtLeast(2f)

                        drawRoundRect(
                            color = Color(0xFFE6F4EC),
                            topLeft = Offset(badgeX, badgeY),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = Color(0xFF34D399),
                            topLeft = Offset(badgeX, badgeY),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(badgeX + 8f, badgeY + 4f)
                        )
                    }
                }
            }
        }

        // Day Labels Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weeklySpend.forEachIndexed { index, (day, _) ->
                val isSelected = selectedIndex == index || (selectedIndex == null && index == peakIndex)
                Text(
                    text = day,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF0F9B76) else Color(0xFF94A3B8)
                )
            }
        }
    }
}
