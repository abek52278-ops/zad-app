package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.background
import com.example.ui.theme.coral
import com.example.ui.theme.lilac
import com.example.ui.theme.primary
import com.example.ui.theme.secondary
import com.example.ui.theme.surfaceContainerLow

/** Real RenderEffect blur only on API 31+ (Modifier.blur is a silent no-op below it,
 * which matters here since minSdk is 24) — every glass surface goes through this
 * single gate instead of each call site checking Build.VERSION itself. */
internal fun Modifier.zadGlassBlur(radius: Dp = 20.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/**
 * Soft gradient-mesh canvas — pure white base with four large blurred
 * pastel-accent blobs (Emerald/Gold/Coral/Lilac) behind scrollable content,
 * so GlassCard's translucency has something colorful to actually blur
 * instead of sitting on a flat background (which just reads as flat, not
 * "glass"). Below API 31 (see zadGlassBlur) the blobs render as soft flat
 * tints instead of blurred ones — same known, acceptable degradation
 * GlassCard itself already has.
 */
@Composable
fun ZadCanvasBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(background)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = (-50).dp)
                .size(220.dp)
                .clip(CircleShape)
                .zadGlassBlur(70.dp)
                .background(primary.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = 40.dp)
                .size(180.dp)
                .clip(CircleShape)
                .zadGlassBlur(65.dp)
                .background(secondary.copy(alpha = 0.13f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp, y = 200.dp)
                .size(200.dp)
                .clip(CircleShape)
                .zadGlassBlur(70.dp)
                .background(coral.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 60.dp)
                .size(240.dp)
                .clip(CircleShape)
                .zadGlassBlur(75.dp)
                .background(lilac.copy(alpha = 0.14f))
        )
    }
}

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
