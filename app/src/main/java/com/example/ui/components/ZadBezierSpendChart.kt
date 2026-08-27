package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyFormatter
import com.example.ui.theme.*

/**
 * Premium smooth Bezier weekly spend chart — real data only.
 * Empty list shows shimmer skeleton (no mock numbers).
 *
 * @param weeklySpend  7 (dayLabel, totalAmount) pairs, Sun first.
 */
@Composable
fun ZadBezierSpendChart(
    weeklySpend: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    var triggered by remember { mutableStateOf(false) }
    val drawProgress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "chartDraw"
    )
    LaunchedEffect(weeklySpend) {
        triggered = false
        triggered = true
    }

    val days = listOf("أحد", "إثن", "ثلا", "أرب", "خمس", "جمع", "سبت")
    val data: List<Pair<String, Double>> = when {
        weeklySpend.isEmpty() -> days.map { it to 0.0 }
        weeklySpend.size < 7  -> weeklySpend + List(7 - weeklySpend.size) { days[weeklySpend.size + it] to 0.0 }
        else                  -> weeklySpend.take(7)
    }

    val maxVal  = data.maxOf { it.second }.let { if (it == 0.0) 1.0 else it }
    val peakIdx = data.indexOfFirst { it.second == data.maxOf { p -> p.second } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, Color(0x0F000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                "المصروف الأسبوعي",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(Modifier.height(12.dp))

            if (weeklySpend.isEmpty()) {
                ShimmerChartPlaceholder()
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val topPad = 24.dp.toPx()
                    val botPad = 20.dp.toPx()
                    val chartH = h - topPad - botPad
                    val n = data.size
                    val stepX = w / (n - 1).toFloat()

                    fun xAt(i: Int) = i * stepX
                    fun yAt(i: Int) = topPad + chartH * (1f - (data[i].second / maxVal).toFloat())
                    val pts = (0 until n).map { Offset(xAt(it), yAt(it)) }

                    val curvePath = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until n - 1) {
                            val p0 = if (i == 0) pts[0] else pts[i - 1]
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val p3 = if (i + 2 < n) pts[i + 2] else pts[n - 1]
                            cubicTo(
                                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                                p2.x, p2.y
                            )
                        }
                    }

                    for (g in 1..3) {
                        val gy = topPad + chartH * (g / 4f)
                        drawLine(
                            color = Color(0x0F000000),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(w * drawProgress, h - botPad)
                        lineTo(0f, h - botPad)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primary.copy(alpha = 0.18f), Color.Transparent),
                            startY = topPad,
                            endY = h - botPad
                        )
                    )

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(Rect(0f, 0f, w * drawProgress, h))
                    drawPath(
                        path = curvePath,
                        color = primary,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawContext.canvas.restore()

                    if (drawProgress > 0.85f && peakIdx >= 0 && data[peakIdx].second > 0) {
                        val px = pts[peakIdx].x
                        val py = pts[peakIdx].y
                        drawCircle(
                            color = primary.copy(alpha = 0.15f),
                            radius = 14.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 8.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = primary,
                            radius = 6.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.forEachIndexed { i, (day, _) ->
                        Text(
                            text = day,
                            fontSize = 10.sp,
                            fontWeight = if (i == peakIdx) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == peakIdx) primary else textTertiary
                        )
                    }
                }

                if (peakIdx >= 0 && data[peakIdx].second > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "ذروة: " + CurrencyFormatter.format(ctx, data[peakIdx].second),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerChartPlaceholder() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.4f, 0.6f, 0.8f).forEach { frac ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(primary.copy(alpha = shimmer))
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(primary.copy(alpha = shimmer * 0.5f))
        )
    }
}
