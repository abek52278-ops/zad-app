package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.surfaceContainerLow

/** Real RenderEffect blur only on API 31+ (Modifier.blur is a silent no-op below it,
 * which matters here since minSdk is 24) — every glass surface goes through this
 * single gate instead of each call site checking Build.VERSION itself. */
private fun Modifier.zadGlassBlur(radius: Dp = 20.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/**
 * Frosted-glass card: translucent surface + subtle border, with a real blur
 * layered in automatically on API 31+. Replaces the ad hoc
 * `Card(shape = RoundedCornerShape(16.dp), elevation = 2.dp)` boilerplate
 * repeated across the AI/finance cards.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = surfaceContainerLow.copy(alpha = 0.6f),
    borderColor: Color = Color.White.copy(alpha = 0.35f),
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .zadGlassBlur()
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Gradient hero surface — the pattern already hand-rolled for the budget
 * header and the Kids candy balance card, unified into one shape so both
 * refactor onto the same primitive instead of two near-duplicates.
 */
@Composable
fun HeroGradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .padding(contentPadding),
        content = content,
    )
}
