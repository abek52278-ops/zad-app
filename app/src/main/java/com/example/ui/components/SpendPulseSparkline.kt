package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * نبض الإنفاق الحي — sparkline بسبعة أيام، نسخة Compose من الـ SVG polyline
 * في البروتوتايب: خط أخضر سميك + تعبئة متدرجة تحته تختفي للأسفل.
 * القيم نسبية (0..1) أو مبالغ — الرسم بيتطبّع تلقائياً على أكبر قيمة.
 */
@Composable
fun SpendPulseSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF0F9B76),
    fillColor: Color = Color(0x660F9B76),
) {
    if (values.size < 2) return
    val maxV = values.max()
    val minV = values.min()
    val range = (maxV - minV).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1)

        fun yFor(v: Float): Float =
            h - 8f - ((v - minV) / range) * (h - 16f)

        // نقاط الخط
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, yFor(v))
        }

        // التعبئة المتدرجة تحت الخط
        val fillPath = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(w, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                endY = h
            )
        )

        // الخط نفسه — سميك بحواف دائرية
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 5f)
        )

        // نقطة على آخر قيمة (اليوم) — دائرة صغيرة بارزة
        drawCircle(
            color = lineColor,
            radius = 10f,
            center = points.last()
        )
        drawCircle(
            color = Color.White,
            radius = 4.5f,
            center = points.last()
        )
    }
}
