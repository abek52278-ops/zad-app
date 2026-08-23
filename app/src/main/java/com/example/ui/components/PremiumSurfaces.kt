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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.canvasBottom
import com.example.ui.theme.canvasMid
import com.example.ui.theme.canvasTop
import com.example.ui.theme.primary

/** Real RenderEffect blur only on API 31+ (Modifier.blur is a silent no-op below it,
 * which matters here since minSdk is 24) — every glass surface goes through this
 * single gate instead of each call site checking Build.VERSION itself. */
internal fun Modifier.zadGlassBlur(radius: Dp = 20.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/**
 * App canvas — the mockup's single neutral diagonal gradient
 * (`linear-gradient(165deg,#F4F5F7 0%,#ECEEF1 45%,#E9ECEF 100%)`), nothing else.
 *
 * This used to paint four large blurred pastel blobs (Emerald/Gold/Coral/Lilac)
 * over a white base. The mockup has no such blobs: every screen sits on that one
 * cool-neutral gradient, and all the color comes from the cards on top of it
 * (green hero, amber prices, category badges). The blobs were the "random yellow
 * circles / random light backgrounds" the design review flagged — they tinted
 * whole regions of Home amber and lilac, which reads as an unfinished gradient
 * bug rather than as depth, and they fought the cards for attention.
 *
 * Kept as a composable (not a plain `Modifier.background`) because call sites
 * layer it behind scrolling content with `fillMaxSize()`, and because the mockup's
 * 165° angle needs an explicit start/end offset rather than `verticalGradient`.
 */
@Composable
fun ZadCanvasBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // The mockup's 165° is within 15° of straight-down, so a vertical
                // gradient reproduces it at phone width without the diagonal's
                // corner-to-corner banding.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to canvasTop,
                        0.45f to canvasMid,
                        1.0f to canvasBottom,
                    )
                )
            )
    )
}

/**
 * Frosted-glass card: translucent surface + subtle border, with a real blur
 * layered in automatically on API 31+. Replaces the ad hoc
 * `Card(shape = RoundedCornerShape(16.dp), elevation = 2.dp)` boilerplate
 * repeated across the AI/finance cards.
 *
 * Bug fix (found via an actual Roborazzi screenshot, not by inspection —
 * `ZadCardHero`'s glassmorphism pass rendered its stats panel nearly blank):
 * `zadGlassBlur()` used to sit on the same `Column` that laid out `content`,
 * so `Modifier.blur()`'s RenderEffect blurred the card's own children (text,
 * icons) along with the background — on API 31+ that's a 20dp blur radius
 * smearing the very content the card exists to show. Blur now lives on a
 * separate background-only `Box` behind an unblurred content `Column`, which
 * is also the technically correct way to do this (blur belongs on what's
 * behind the glass, never on the glass's own foreground content).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = Color.White.copy(alpha = 0.85f),
    borderColor: Color = Color.Black.copy(alpha = 0.05f),
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().clip(shape)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .zadGlassBlur(14.dp)
                .background(containerColor)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, shape)
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * The mockup's two-layer card shadow (`0 1px 2px rgba(15,23,42,0.04),
 * 0 8px 20px rgba(15,23,42,0.07)`) collapsed to the single ambient/spot pair
 * Compose actually supports. Applied *before* `.clip()`/`.background()` at the
 * call site, as `Modifier.shadow` requires.
 */
fun Modifier.zadCardShadow(shape: Shape, elevation: Dp = 8.dp): Modifier =
    this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color(0xFF0F172A).copy(alpha = 0.10f),
        spotColor = Color(0xFF0F172A).copy(alpha = 0.14f),
    )

/**
 * Gradient hero surface — the pattern already hand-rolled for the budget
 * header and the Kids candy balance card, unified into one shape so both
 * refactor onto the same primitive instead of two near-duplicates.
 */
@Composable
fun HeroGradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = ZadHeroGradient,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: Dp = 24.dp,
    /** حركة التدرج المستمرة (zadMeshShift من البروتوتايب) — الكارت الأخضر "بيتنفس". */
    animateMesh: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // نفس أنيميشن background-position في الويب: 9 ثواني ease، بيتحرك بس لما مطلوب
    val meshShift = if (animateMesh) {
        val transition = rememberInfiniteTransition(label = "hero_mesh")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "mesh_offset"
        ).value
    } else 0.35f // موضع ثابت وسط التدرج

    val brush = Brush.linearGradient(
        colors = colors + colors.first(), // نغلق الحلقة عشان الانتقال يبقى سلس
        start = Offset(x = meshShift * 900f - 300f, y = -250f),
        end = Offset(x = sizeMaxX() - meshShift * 500f, y = 850f),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.35f),
                spotColor = primary.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** عرض مرجعي ثابت لمحور التدرج — قيمة كافية لتغطية أعرض شاشة */
private fun sizeMaxX(): Float = 1400f

/**
 * The mockup's plain list card: opaque white, 16dp radius, two-layer shadow. This is
 * the single most repeated surface in the design (inventory items, shopping rows,
 * subscriptions, pharmacy, maintenance, deals, profile rows), and the screens had each
 * hand-rolled it as a Surface/Box with slightly different radii and elevations.
 *
 * Distinct from GlassCard on purpose: GlassCard is translucent and belongs over the
 * gradient canvas or a hero; this is the flat card used inside ordinary lists, where
 * translucency would just muddy the text.
 */
@Composable
fun ZadListCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = Color.White,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(containerColor)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Compact banner used at the top of a list screen for one headline number or warning
 * (shopping total, low-stock count, monthly obligations). Mesh gradient + white text,
 * matching the mockup's smaller sibling of the Home hero.
 */
@Composable
fun ZadScreenBanner(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    HeroGradientCard(
        modifier = modifier,
        colors = ZadHeroGradient,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}

/** The mockup's hero mesh gradient (`120deg, #0B6B4E, #0F9B76, #064E3B, #0B6B4E`). */
val ZadHeroGradient = listOf(
    Color(0xFF0B6B4E),
    Color(0xFF0F9B76),
    Color(0xFF064E3B),
    Color(0xFF0B6B4E),
)
