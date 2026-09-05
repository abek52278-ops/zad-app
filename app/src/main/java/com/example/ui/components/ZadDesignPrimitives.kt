package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * The four surfaces the mockup repeats on every screen that isn't Home. They lived as
 * hand-rolled `Surface`/`Card`/`Box` copies inside each screen, each with its own radius,
 * elevation and pill padding, which is why the ported screens drifted apart visually even
 * where the layout was right. `PremiumSurfaces.kt` already holds the card and hero
 * primitives; this file adds the smaller repeated pieces that sit *inside* them.
 *
 * Values are the mockup's own (`web_preview/react/app.jsx`), not approximations.
 */

/** Page padding every non-Home screen uses: `padding: 18px 20px 130px`. */
val ZadScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 130.dp)

/** Vertical rhythm between sibling cards on a list screen (`gap: 14`). */
val ZadScreenGap = 14.dp

/**
 * The trailing status pill: `fontSize:11, fontWeight:700, borderRadius:9999,
 * padding:'4px 10px'`, on a faint green wash with the *text* carrying the status color.
 * The mockup deliberately keeps the background constant and varies only the text — a
 * fully-tinted pill per status turns a calm list into a traffic light.
 */
@Composable
fun ZadStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = primary.copy(alpha = 0.06f),
) {
    Text(
        text,
        style = Typography.labelSmall.copy(fontSize = 11.sp),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * The mockup's meter: a 6–8dp `#F1F4F3` track with a rounded colored fill. Used for
 * obligations, category budgets, the tasbiha garden and the spending-power bar.
 *
 * `progress` is coerced into 0..1 here rather than at each call site — a ratio built from
 * user data can legitimately exceed 1 (over budget), and a fill wider than its track
 * silently overflows the card instead of reading as "full".
 */
@Composable
fun ZadMeterBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = Color(0xFFF1F4F3),
    animate: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val width by animateFloatAsState(
        targetValue = if (animate) target else target,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "meter"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(width)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/**
 * The dark AI panel (`#052E16`, radius 20, padding 18) with its mint title
 * (`#6EE7B7`, 12.5/700). The mockup uses it for anything the assistant *says* as
 * opposed to anything the app *measures* — that contrast is the point, so this stays
 * a distinct surface rather than another white card.
 */
@Composable
fun ZadDarkPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(primaryDark)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            style = Typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.Bold,
            color = primary
        )
        content()
    }
}

/**
 * The single most repeated row in the design: a white 16dp card holding a 14/600 title
 * over an 11.5 grey subtitle, with something on the trailing edge (an amount, a status
 * pill, a distance). Subscriptions, pharmacy, maintenance, deals, family members, family
 * tasks and notifications are all this row with a different trailing slot.
 */
@Composable
fun ZadRowCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leadingAccent: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(Color.White)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The mockup's `borderRight: 3px solid <color>` accent (subscriptions). Drawn as a
        // full-height leading strip so it still lands on the start edge under RTL, which a
        // literal `borderRight` would not.
        if (leadingAccent != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(leadingAccent)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    style = Typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = Typography.labelMedium.copy(fontSize = 11.5.sp),
                        color = textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** The trailing amount on a `ZadRowCard` (14/700, near-black). */
@Composable
fun ZadRowAmount(text: String, color: Color = textPrimary) {
    Text(
        text,
        style = Typography.bodyLarge.copy(fontSize = 14.sp),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1
    )
}

/**
 * The mockup's settings list: **one** white 18dp card whose rows are separated by hairline
 * dividers — not one card per row. Row text is a plain 14/600 label; the design has no
 * icon tile and no per-row gradient.
 *
 * `ZadMenuRow` is a `RowScope`-free child so a group can mix plain rows with rows that
 * carry a trailing control (the kids-mode switch).
 */
@Composable
fun ZadMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // مرحلة ٥ب-٥ (docs/agent/PLAN_2026_08_06_rebuild.md) — 24dp بدل 18dp، نفس نصف قطر
    // عائلة الزجاج. الخلفية فضلت أبيض صافي عمداً (مش جراديانت/blur) — قايمة إعدادات
    // كثيفة نصوص، والـ blur هيقلل وضوح القراءة، نفس مبدأ iOS Settings.app (كروت
    // مجموعة معتمة، الزجاج محجوز للعناصر البارزة القليلة زي الهيدر فوقها).
    val shape = com.example.ui.theme.ZadLuxe.squircle
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, shape),
        content = content,
    )
}

/** One row inside a [ZadMenuGroup]. `showDivider` is false on the last row of a group. */
@Composable
fun ZadMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = textPrimary,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = Typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = Typography.labelMedium.copy(fontSize = 11.5.sp),
                        color = textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black.copy(alpha = 0.05f))
            )
        }
    }
}

/**
 * The splash/auth canvas: `#FBFAF8` under two soft radial washes — warm coral from the
 * top-start, mint from the bottom-end. The auth screens were on flat
 * `MaterialTheme.colorScheme.background` (plain white), so the app's first screen shared
 * nothing with the rest of the design.
 *
 * Compose has no CSS `radial-gradient(120% 100% at 15% 10%)`, so each wash is a
 * `Brush.radialGradient` with an explicit center and radius in pixels, sized off the
 * layout rather than hardcoded — the mockup's percentages are relative to the viewport.
 */
@Composable
fun ZadAuthBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color(0xFFFBFAF8))
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFCD3C7).copy(alpha = 0.55f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(wPx * 0.15f, hPx * 0.10f),
                        radius = maxOf(wPx, hPx) * 0.9f
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFBFE3D1).copy(alpha = 0.55f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(wPx * 0.85f, hPx * 0.90f),
                        radius = maxOf(wPx, hPx) * 0.9f
                    )
                )
        )
        content()
    }
}

/**
 * The mockup's primary CTA: a fully-rounded deep-green pill with a green-tinted drop
 * shadow (`0 10px 26px rgba(6,78,59,.25)`) and **white** label.
 *
 * The auth screens were passing `MaterialTheme.colorScheme.onSurface` as the label color
 * on a `primary` container — near-black text on deep green, which is what made the login
 * button read as disabled.
 */
@Composable
fun ZadPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(56.dp).pressableScale(),
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = primary,
            contentColor = Color.White,
            disabledContainerColor = primary.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
            defaultElevation = 10.dp,
            pressedElevation = 4.dp
        )
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text,
                style = Typography.titleMedium.copy(fontSize = 15.5.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
