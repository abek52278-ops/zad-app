package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ZadTheme v3 — Apple Wallet / iOS-inspired design system rebuilt from "new ui ux/" vision
 *
 * Color palette: Emerald Deep Green (#064E3B, #0A382C) as primary,
 * warm neutrals (#F2F2F7) as canvas, crisp white cards, glassmorphism.
 */

object ZadV3 {
    // ── Brand Emerald Deep Greens ──────────────────────────────────────────
    val green900 = Color(0xFF052E16)
    val green800 = Color(0xFF064E3B)       // Primary brand color
    val green700 = Color(0xFF0B6B4E)
    val green600 = Color(0xFF0F9B76)
    val green500 = Color(0xFF1BB383)
    val green400 = Color(0xFF34D399)
    val mint100 = Color(0xFFD9F2E6)
    val mint50  = Color(0xFFE6F4EC)
    val mintGlow = Color(0xFF6EE7B7)       // AI accent glow

    // ── Canvas & Neutrals ─────────────────────────────────────────────────
    // بند 36.1 — كان هنا #F2F2F7 مستقل، وColor.kt عنده canvasMid #ECEEF1 منفصل —
    // نفس القيمة المفهومياً ("خلفية التطبيق") بحرفين مختلفين في نظامين، وده بالظبط
    // اللي الخطة وصفته "بيتخانقوا". اتأكد حي إن ZadV3.canvas/canvasGradient
    // مالهومش أي استخدام في الكود (grep) — الكانفاس الفعلي المرسوم هو Color.kt's
    // canvasTop/Mid/Bottom عبر ZadCanvasBackground. فبدل ما نمسح القيمة أو نغيّر أي
    // بيكسل فعلي، ZadV3 بقت بترجع لنفس مصدر Color.kt — مصدر واحد حقيقي، صفر تغيير
    // بصري (مفيش حد بيقرا القيمة القديمة أصلاً).
    // (لسه صفر استخدام) — ZadV3 نظام قديم مالوش وعي بالثيم، فبيقرا نسخة الفاتح مباشرة.
    val canvas = ZadExtendedColorsLight.canvasMid
    val canvasWarm = Color(0xFFFBFAF8)      // Splash/auth canvas
    val surface = Color(0xFFFFFFFF)         // White card surface
    val ink = Color(0xFF0F172A)             // Near-black text
    val slate = Color(0xFF374151)
    val gray500 = Color(0xFF6B7280)
    val gray400 = Color(0xFF9CA3AF)
    val gray300 = Color(0xFFD1D5DB)
    val hairline = Color(0x0D000000)

    // ── Semantic ──────────────────────────────────────────────────────────
    val warn = Color(0xFFB45309)
    val danger = Color(0xFFDC5B4B)
    val info = Color(0xFF2563EB)
    val violet = Color(0xFF7C3AED)
    val amberDot = Color(0xFFF4A93B)
    val coralDot = Color(0xFFFF8066)

    // ── Category Tile Fills (pastel, matching the vision) ────────────────
    val tileInvBg = Color(0xFFE3F5EC);  val tileInvFg = Color(0xFF0B6B4E)
    val tileShopBg = Color(0xFFFCEEE3); val tileShopFg = Color(0xFFC2703D)
    val tileFamilyBg = Color(0xFFF1EAFB); val tileFamilyFg = Color(0xFF7C3AED)
    val tileSubsBg = Color(0xFFE8F1FC); val tileSubsFg = Color(0xFF2563EB)
    val tilePharmBg = Color(0xFFFCE8ED); val tilePharmFg = Color(0xFFDC5B4E)
    val tileMaintBg = Color(0xFFFDF3E1); val tileMaintFg = Color(0xFFB45309)
    val tileTasbihaBg = Color(0xFFF3E8FF); val tileTasbihaFg = Color(0xFF9333EA)

    // AI Plate — dark emerald for AI-generated content
    val aiPlate = Color(0xFF052E16)
    val aiCardBg = Color(0xFF0A382C)

    // ── Radii ─────────────────────────────────────────────────────────────
    val rCard = RoundedCornerShape(16.dp)
    val rCardLg = RoundedCornerShape(20.dp)
    val rSheet = RoundedCornerShape(24.dp)
    val rHero = RoundedCornerShape(28.dp)   // Apple Wallet card
    val rPill = RoundedCornerShape(999.dp)

    // ── Gradients ─────────────────────────────────────────────────────────
    val heroAmountBrush = Brush.verticalGradient(listOf(Color.White, mint100))
    val heroGradient = listOf(green800, green700)
    val heroGradientDeep = listOf(Color(0xFF0A382C), green800, green700)

    // ── Shadows ───────────────────────────────────────────────────────────
    val cardShadowAmbient = Color(0x0A0F172A)
    val cardShadowSpot = Color(0x120F172A)

    // ═══ Design-contract literal tokens ("new ui ux" spec — no rounding) ═══

    // Missing palette entries from the contract table
    val coralLight = Color(0xFFFF8066)      // #FF8066
    val neutralBg = Color(0xFFF1F4F3)       // #F1F4F3
    val greenDeepest = Color(0xFF052E16)    // #052E16 (== aiPlate, aliased for clarity)

    // Screen canvas — بند 36.1: نفس تدرّج ZadCanvasBackground الحقيقي (Color.kt's
    // canvasTop/Mid/Bottom) بدل تكرار نفس الألوان كحروف مستقلة هنا.
    val canvasGradient = Brush.linearGradient(
        colors = listOf(
            ZadExtendedColorsLight.canvasTop,
            ZadExtendedColorsLight.canvasMid,
            ZadExtendedColorsLight.canvasBottom,
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // Primary button/header gradient: linear-gradient(135deg,#064E3B,#0B6B4E)
    val primaryGradient = Brush.linearGradient(listOf(green800, green700))

    // Animated ring / hero mesh: linear-gradient(120deg,#0B6B4E,#0F9B76,#064E3B,#0B6B4E)
    val meshGradientStops = listOf(green700, green600, green800, green700)

    // White card → mint: linear-gradient(180deg,#fff,#D9F2E6)
    val whiteToMint = Brush.verticalGradient(listOf(Color.White, mint100))

    // Violet→green progress bar: linear-gradient(90deg,#7C3AED,#0F9B76)
    val violetGreenBar = Brush.horizontalGradient(listOf(violet, green600))

    // Family/Kids accent: linear-gradient(135deg,#7C3AED,#EC4899)
    val kidsGradient = Brush.linearGradient(listOf(violet, Color(0xFFEC4899)))

    /** Card shadow — contract literal:
     *  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 8px 20px rgba(15,23,42,.07) */
    fun Modifier.contractCardShadow(shape: Shape): Modifier = this.shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = Color(0x0A0F172A),
        spotColor = Color(0x120F172A),
    )

    /** Floating element shadow — contract literal:
     *  box-shadow: 0 8px 24px rgba(15,23,42,.14) */
    fun Modifier.floatingShadow(shape: Shape): Modifier = this.shadow(
        elevation = 24.dp,
        shape = shape,
        ambientColor = Color(0x240F172A),
        spotColor = Color(0x240F172A),
    )
}

/** Backward-compatible alias for old screens still referencing ZadV2 */
val ZadV2 = ZadV3

/** Glassmorphic blur for Android 12+ */
fun Modifier.glassBlur(radius: Float = 16f): Modifier = this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = android.graphics.RenderEffect.createBlurEffect(
            radius, radius, android.graphics.Shader.TileMode.DECAL
        ).let { it.asComposeRenderEffect() }
    }
}

/** Apple-style card shadow: elevated white surface with soft diffuse shadow */
fun Modifier.zadCardShadow(shape: Shape, elevation: androidx.compose.ui.unit.Dp = 8.dp): Modifier =
    this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color(0xFF0F172A).copy(alpha = 0.08f),
        spotColor = Color(0xFF0F172A).copy(alpha = 0.12f),
    )

/**
 * dotPulse من البروتوتايب (`@keyframes dotPulse`): scale 1→1.3 وopacity 1→0.7
 * على لوب 2.4s. للنقط الملونة في صفوف insights وأجراس الإشعارات.
 */
@Composable
fun Modifier.zadDotPulse(periodMs: Int = 2400): Modifier {
    val transition = rememberInfiniteTransition(label = "dotPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotPulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotPulseAlpha"
    )
    return this.graphicsLayer {
        scaleX = pulse
        scaleY = pulse
        this.alpha = alpha
    }
}

/** V2-compatible card style (white, shadow, clip) */
fun Modifier.zadV2Card(shape: Shape = ZadV3.rCard): Modifier = this
    .shadow(elevation = 12.dp, shape = shape, ambientColor = ZadV3.cardShadowAmbient, spotColor = ZadV3.cardShadowSpot)
    .clip(shape)
    .background(Color.White)

// ── Reusable composables ──────────────────────────────────────────────────────




/** Page padding for non-home screens */
val V3ScreenPadding = androidx.compose.foundation.layout.PaddingValues(
    start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp
)
val V3ScreenGap = 14.dp

/**
 * UI_ARCHITECTURE_SPEC.md §3.2 — bottom clearance for lists/grids sitting under the
 * floating mic button, standardized to 110.dp (was scattered as a repeated 100.dp
 * literal across BudgetScreen/MaintenanceScreen/PharmacyScreen/SubscriptionsScreen/
 * InventoryScreen). Adopt this constant instead of a new literal when touching those
 * screens' hub rebuilds.
 */
val ZadHubListBottomPadding = 110.dp

/** Meter bar (progress) */
@Composable
fun ZadMeterBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp,
    trackColor: Color = Color(0xFFF1F4F3),
) {
    val target = progress.coerceIn(0f, 1f)
    val width: Float by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "meter",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(trackColor)
    ) {
        Box(Modifier.fillMaxWidth(width).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** Status pill (faint bg, colored text) */
@Composable
fun ZadStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = ZadV3.green800.copy(alpha = 0.06f),
) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(containerColor).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Dark AI panel */
@Composable
fun ZadDarkPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(ZadV3.rCardLg).background(ZadV3.aiPlate).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.mintGlow)
        content()
    }
}

/** Universal row card for list screens */
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
            .then(if (onClick != null) Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ) else Modifier)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingAccent != null) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(leadingAccent))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 11.5.sp, color = ZadV3.gray400, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** Row amount display */
@Composable
fun ZadRowAmount(text: String, color: Color = ZadV3.ink) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
}

/** Settings-style menu group */
@Composable
fun ZadMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier.fillMaxWidth().zadCardShadow(shape).clip(shape).background(Color.White),
        content = content,
    )
}

/** Settings-style menu row */
@Composable
fun ZadMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = ZadV3.ink,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 11.5.sp, color = ZadV3.gray400, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.05f)))
        }
    }
}

/** Primary CTA button */
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
        modifier = modifier.height(56.dp).let { it }, // pressableScale is applied inline
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = ZadV3.green800,
            contentColor = Color.White,
            disabledContainerColor = ZadV3.green800.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
            defaultElevation = 10.dp, pressedElevation = 4.dp,
        ),
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}