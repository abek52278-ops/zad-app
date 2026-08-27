package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-fidelity category definitions with studio pastel backgrounds and theme tints.
 * Matches Image 4 design specification (Produce, Cooking Oil, Meat & Fish, Bakery, Dairy, Beverages).
 */
enum class ZadCategoryType(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val bgPastel: Color,
    val borderTint: Color,
    val accentFg: Color
) {
    PRODUCE(
        id = "produce",
        titleAr = "فواكه وخضار",
        titleEn = "Fresh Produce",
        bgPastel = Color(0xFFEBF7F0),
        borderTint = Color(0xFFC3EAD2),
        accentFg = Color(0xFF0F9B76)
    ),
    COOKING_OIL(
        id = "oils",
        titleAr = "زيوت وطهي",
        titleEn = "Cooking Oil & Ghee",
        bgPastel = Color(0xFFFFF5E9),
        borderTint = Color(0xFFFDE1BD),
        accentFg = Color(0xFFB45309)
    ),
    MEAT_FISH(
        id = "meat_fish",
        titleAr = "لحوم وأسماك",
        titleEn = "Meat & Fish",
        bgPastel = Color(0xFFFFEFEF),
        borderTint = Color(0xFFFCD0D0),
        accentFg = Color(0xFFDC5B4B)
    ),
    BAKERY(
        id = "bakery",
        titleAr = "مخبوزات وسناكس",
        titleEn = "Bakery & Snacks",
        bgPastel = Color(0xFFF3EDFC),
        borderTint = Color(0xFFDCC8F7),
        accentFg = Color(0xFF7C3AED)
    ),
    DAIRY_EGGS(
        id = "dairy_eggs",
        titleAr = "ألبان وبيض",
        titleEn = "Dairy & Eggs",
        bgPastel = Color(0xFFFFFDE8),
        borderTint = Color(0xFFFBF4B5),
        accentFg = Color(0xFFD97706)
    ),
    BEVERAGES(
        id = "beverages",
        titleAr = "مشروبات وعصائر",
        titleEn = "Beverages",
        bgPastel = Color(0xFFEBF5FC),
        borderTint = Color(0xFFC4E4F9),
        accentFg = Color(0xFF0284C7)
    ),
    PHARMACY(
        id = "pharmacy",
        titleAr = "صيدلية ورعاية",
        titleEn = "Pharmacy & Care",
        bgPastel = Color(0xFFFCE8ED),
        borderTint = Color(0xFFF9BDCC),
        accentFg = Color(0xFFE11D48)
    ),
    MAINTENANCE(
        id = "maintenance",
        titleAr = "صيانة ومنزل",
        titleEn = "Maintenance",
        bgPastel = Color(0xFFFDF3E1),
        borderTint = Color(0xFFFBE2B2),
        accentFg = Color(0xFFC2703D)
    ),
    SUBSCRIPTIONS(
        id = "subs",
        titleAr = "اشتراكات",
        titleEn = "Subscriptions",
        bgPastel = Color(0xFFE8F1FC),
        borderTint = Color(0xFFBDD9FA),
        accentFg = Color(0xFF2563EB)
    ),
    FAMILY(
        id = "family",
        titleAr = "العائلة",
        titleEn = "Family & Kids",
        bgPastel = Color(0xFFF1EAFB),
        borderTint = Color(0xFFD6C0F7),
        accentFg = Color(0xFF8B5CF6)
    ),
    TASBIHA(
        id = "tasbiha",
        titleAr = "بستان التسبيح",
        titleEn = "Tasbiha Garden",
        bgPastel = Color(0xFFE3F5EC),
        borderTint = Color(0xFFB5E8CE),
        accentFg = Color(0xFF059669)
    );

    companion object {
        fun fromId(id: String): ZadCategoryType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PRODUCE
        }

        fun fromCategoryName(name: String): ZadCategoryType {
            val lower = name.lowercase()
            return when {
                lower.contains("فاكه") || lower.contains("خضار") || lower.contains("fruit") || lower.contains("veg") || lower.contains("طماطم") || lower.contains("تفاح") || lower.contains("موز") -> PRODUCE
                lower.contains("زيت") || lower.contains("سمن") || lower.contains("oil") || lower.contains("ghee") -> COOKING_OIL
                lower.contains("لحم") || lower.contains("سمك") || lower.contains("دجاج") || lower.contains("meat") || lower.contains("fish") || lower.contains("chicken") -> MEAT_FISH
                lower.contains("خبز") || lower.contains("كيك") || lower.contains("شوكولاتة") || lower.contains("حلوي") || lower.contains("bakery") || lower.contains("snack") -> BAKERY
                lower.contains("حليب") || lower.contains("جبن") || lower.contains("بيض") || lower.contains("dairy") || lower.contains("egg") || lower.contains("milk") || lower.contains("cheese") -> DAIRY_EGGS
                lower.contains("عصير") || lower.contains("ماء") || lower.contains("مياه") || lower.contains("مشروب") || lower.contains("بيبسي") || lower.contains("drink") || lower.contains("beverage") -> BEVERAGES
                lower.contains("دواء") || lower.contains("صيدل") || lower.contains("فيتامين") || lower.contains("pharmacy") || lower.contains("med") -> PHARMACY
                lower.contains("صيان") || lower.contains("تصليح") || lower.contains("مكيف") || lower.contains("ثلاج") || lower.contains("maint") -> MAINTENANCE
                lower.contains("اشتراك") || lower.contains("نتفلكس") || lower.contains("فاتور") || lower.contains("sub") -> SUBSCRIPTIONS
                lower.contains("عائل") || lower.contains("أطفال") || lower.contains("طفل") || lower.contains("family") || lower.contains("kid") -> FAMILY
                lower.contains("تسبيح") || lower.contains("بستان") || lower.contains("tasbih") -> TASBIHA
                else -> PRODUCE
            }
        }
    }
}

/**
 * 3D-styled category card with pastel background, rich vector canvas illustration, and subtle spring press animation.
 */
@Composable
fun ZadCategoryCard(
    category: ZadCategoryType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "cat_scale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(category.bgPastel)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = category.accentFg.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 3D Canvas-drawn vector illustration
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            ZadCategory3DIcon(
                category = category,
                modifier = Modifier.size(42.dp)
            )
        }

        Text(
            text = category.titleAr,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(120)
            isPressed = false
        }
    }
}

/**
 * Draws rich vector shapes simulating 3D studio product photography (No emojis).
 */
@Composable
fun ZadCategory3DIcon(
    category: ZadCategoryType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (category) {
            ZadCategoryType.PRODUCE -> drawProduceIcon(w, h)
            ZadCategoryType.COOKING_OIL -> drawCookingOilIcon(w, h)
            ZadCategoryType.MEAT_FISH -> drawMeatFishIcon(w, h)
            ZadCategoryType.BAKERY -> drawBakeryIcon(w, h)
            ZadCategoryType.DAIRY_EGGS -> drawDairyEggsIcon(w, h)
            ZadCategoryType.BEVERAGES -> drawBeveragesIcon(w, h)
            ZadCategoryType.PHARMACY -> drawPharmacyIcon(w, h)
            ZadCategoryType.MAINTENANCE -> drawMaintenanceIcon(w, h)
            ZadCategoryType.SUBSCRIPTIONS -> drawSubscriptionsIcon(w, h)
            ZadCategoryType.FAMILY -> drawFamilyIcon(w, h)
            ZadCategoryType.TASBIHA -> drawTasbihaIcon(w, h)
        }
    }
}

// ── Vector Draw Functions for 3D Categories ──────────────────────────────────

private fun DrawScope.drawProduceIcon(w: Float, h: Float) {
    val basketPath = Path().apply {
        moveTo(w * 0.18f, h * 0.45f)
        lineTo(w * 0.82f, h * 0.45f)
        lineTo(w * 0.72f, h * 0.88f)
        lineTo(w * 0.28f, h * 0.88f)
        close()
    }
    drawPath(
        path = basketPath,
        brush = Brush.verticalGradient(listOf(Color(0xFFD97706), Color(0xFF92400E)))
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF6B6B), Color(0xFFDC2626)),
            center = Offset(w * 0.38f, h * 0.35f),
            radius = w * 0.22f
        ),
        radius = w * 0.18f,
        center = Offset(w * 0.38f, h * 0.38f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFCD34D), Color(0xFFEA580C)),
            center = Offset(w * 0.62f, h * 0.34f),
            radius = w * 0.2f
        ),
        radius = w * 0.17f,
        center = Offset(w * 0.62f, h * 0.36f)
    )
    drawOval(
        color = Color(0xFF16A34A),
        topLeft = Offset(w * 0.45f, h * 0.14f),
        size = Size(w * 0.18f, h * 0.12f)
    )
}

private fun DrawScope.drawCookingOilIcon(w: Float, h: Float) {
    val bottlePath = Path().apply {
        moveTo(w * 0.38f, h * 0.18f)
        lineTo(w * 0.62f, h * 0.18f)
        lineTo(w * 0.62f, h * 0.32f)
        lineTo(w * 0.75f, h * 0.45f)
        lineTo(w * 0.75f, h * 0.88f)
        lineTo(w * 0.25f, h * 0.88f)
        lineTo(w * 0.25f, h * 0.45f)
        lineTo(w * 0.38f, h * 0.32f)
        close()
    }
    drawPath(
        path = bottlePath,
        brush = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706)))
    )
    drawRoundRect(
        color = Color(0xFF92400E),
        topLeft = Offset(w * 0.35f, h * 0.08f),
        size = Size(w * 0.3f, h * 0.12f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = Offset(w * 0.32f, h * 0.48f),
        end = Offset(w * 0.32f, h * 0.82f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawMeatFishIcon(w: Float, h: Float) {
    val steakPath = Path().apply {
        moveTo(w * 0.2f, h * 0.4f)
        cubicTo(w * 0.2f, h * 0.15f, w * 0.75f, h * 0.18f, w * 0.8f, h * 0.45f)
        cubicTo(w * 0.85f, h * 0.75f, w * 0.45f, h * 0.9f, w * 0.22f, h * 0.72f)
        close()
    }
    drawPath(
        path = steakPath,
        brush = Brush.verticalGradient(listOf(Color(0xFFF87171), Color(0xFF991B1B)))
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = w * 0.09f,
        center = Offset(w * 0.45f, h * 0.45f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.8f),
        start = Offset(w * 0.45f, h * 0.45f),
        end = Offset(w * 0.68f, h * 0.38f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawBakeryIcon(w: Float, h: Float) {
    val breadPath = Path().apply {
        moveTo(w * 0.15f, h * 0.65f)
        cubicTo(w * 0.15f, h * 0.25f, w * 0.85f, h * 0.25f, w * 0.85f, h * 0.65f)
        cubicTo(w * 0.85f, h * 0.85f, w * 0.15f, h * 0.85f, w * 0.15f, h * 0.65f)
        close()
    }
    drawPath(
        path = breadPath,
        brush = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309)))
    )
    drawLine(color = Color(0xFF78350F), start = Offset(w * 0.32f, h * 0.4f), end = Offset(w * 0.42f, h * 0.62f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = Color(0xFF78350F), start = Offset(w * 0.5f, h * 0.35f), end = Offset(w * 0.58f, h * 0.6f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = Color(0xFF78350F), start = Offset(w * 0.68f, h * 0.4f), end = Offset(w * 0.74f, h * 0.62f), strokeWidth = 3f, cap = StrokeCap.Round)
}

private fun DrawScope.drawDairyEggsIcon(w: Float, h: Float) {
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFEFF6FF), Color(0xFF93C5FD))),
        topLeft = Offset(w * 0.18f, h * 0.28f),
        size = Size(w * 0.32f, h * 0.6f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRect(
        color = Color(0xFF2563EB),
        topLeft = Offset(w * 0.18f, h * 0.52f),
        size = Size(w * 0.32f, h * 0.16f)
    )
    val eggPath = Path().apply {
        moveTo(w * 0.72f, h * 0.42f)
        cubicTo(w * 0.58f, h * 0.42f, w * 0.54f, h * 0.85f, w * 0.72f, h * 0.88f)
        cubicTo(w * 0.9f, h * 0.85f, w * 0.86f, h * 0.42f, w * 0.72f, h * 0.42f)
        close()
    }
    drawPath(
        path = eggPath,
        brush = Brush.radialGradient(
            listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B)),
            center = Offset(w * 0.72f, h * 0.6f),
            radius = w * 0.2f
        )
    )
}

private fun DrawScope.drawBeveragesIcon(w: Float, h: Float) {
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0369A1))),
        topLeft = Offset(w * 0.3f, h * 0.28f),
        size = Size(w * 0.4f, h * 0.6f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFFBAE6FD),
        topLeft = Offset(w * 0.32f, h * 0.22f),
        size = Size(w * 0.36f, h * 0.08f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawLine(
        color = Color(0xFFEF4444),
        start = Offset(w * 0.5f, h * 0.24f),
        end = Offset(w * 0.72f, h * 0.08f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawPharmacyIcon(w: Float, h: Float) {
    drawRoundRect(
        brush = Brush.horizontalGradient(
            0.0f to Color(0xFFF43F5E),
            0.5f to Color(0xFFF43F5E),
            0.501f to Color(0xFFEFF6FF),
            1.0f to Color(0xFFEFF6FF)
        ),
        topLeft = Offset(w * 0.15f, h * 0.35f),
        size = Size(w * 0.7f, h * 0.32f),
        cornerRadius = CornerRadius(16f, 16f)
    )
    val crossW = w * 0.08f
    val crossLen = w * 0.22f
    drawRect(color = Color(0xFFE11D48), topLeft = Offset(w * 0.5f - crossW / 2, h * 0.1f), size = Size(crossW, crossLen))
    drawRect(color = Color(0xFFE11D48), topLeft = Offset(w * 0.5f - crossLen / 2, h * 0.1f + crossLen / 2 - crossW / 2), size = Size(crossLen, crossW))
}

private fun DrawScope.drawMaintenanceIcon(w: Float, h: Float) {
    val wrenchPath = Path().apply {
        moveTo(w * 0.25f, h * 0.75f)
        lineTo(w * 0.65f, h * 0.35f)
        lineTo(w * 0.78f, h * 0.42f)
        lineTo(w * 0.85f, h * 0.25f)
        lineTo(w * 0.68f, h * 0.18f)
        lineTo(w * 0.55f, h * 0.25f)
        lineTo(w * 0.18f, h * 0.68f)
        close()
    }
    drawPath(
        path = wrenchPath,
        brush = Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFF64748B)))
    )
    drawCircle(color = Color(0xFF0F172A), radius = w * 0.06f, center = Offset(w * 0.25f, h * 0.75f))
}

private fun DrawScope.drawSubscriptionsIcon(w: Float, h: Float) {
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
        topLeft = Offset(w * 0.15f, h * 0.25f),
        size = Size(w * 0.7f, h * 0.5f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFFFBBF24),
        topLeft = Offset(w * 0.25f, h * 0.36f),
        size = Size(w * 0.18f, h * 0.14f),
        cornerRadius = CornerRadius(3f, 3f)
    )
}

private fun DrawScope.drawFamilyIcon(w: Float, h: Float) {
    drawCircle(color = Color(0xFF8B5CF6), radius = w * 0.15f, center = Offset(w * 0.36f, h * 0.35f))
    drawCircle(color = Color(0xFFA78BFA), radius = w * 0.13f, center = Offset(w * 0.64f, h * 0.4f))
    drawRoundRect(
        color = Color(0xFF7C3AED),
        topLeft = Offset(w * 0.2f, h * 0.52f),
        size = Size(w * 0.32f, h * 0.35f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFF8B5CF6),
        topLeft = Offset(w * 0.5f, h * 0.56f),
        size = Size(w * 0.3f, h * 0.31f),
        cornerRadius = CornerRadius(10f, 10f)
    )
}

private fun DrawScope.drawTasbihaIcon(w: Float, h: Float) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xFF34D399), Color(0xFF047857)),
            center = Offset(w * 0.5f, h * 0.35f),
            radius = w * 0.3f
        ),
        topLeft = Offset(w * 0.25f, h * 0.15f),
        size = Size(w * 0.5f, h * 0.45f)
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(w * 0.44f, h * 0.55f),
        size = Size(w * 0.12f, h * 0.35f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}
