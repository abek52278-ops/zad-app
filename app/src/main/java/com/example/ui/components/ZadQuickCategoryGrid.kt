package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

/**
 * الأقسام اللي بتفتح شاشة خاصة بيها — محفوظة من أجل اختبارات CategoryDisplayOrderTest.
 */
private val PRIMARY_DESTINATIONS = listOf(
    ZadCategoryType.PHARMACY,
    ZadCategoryType.FAMILY,
    ZadCategoryType.TASBIHA,
    ZadCategoryType.SUBSCRIPTIONS,
    ZadCategoryType.MAINTENANCE,
)

internal val categoryDisplayOrder: List<ZadCategoryType> =
    PRIMARY_DESTINATIONS + (ZadCategoryType.entries - PRIMARY_DESTINATIONS.toSet())

@Composable
fun ZadCategoryChip(
    category: ZadCategoryType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .pressableScale(pressedScale = 0.93f)
            .zadCardShadow(cardShape)
            .clip(cardShape)
            .background(category.bgPastel.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = category.borderTint.copy(alpha = 0.70f),
                shape = cardShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .border(0.5.dp, category.borderTint.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            ZadCategory3DIcon(
                category = category,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = category.titleAr,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            maxLines = 1
        )
    }
}

/**
 * شريط مربعات الاختصارات الأربعة (المخزون، التسوق، العائلة، الاشتراكات)
 * بتصميم ثلاثي الأبعاد فاخر ولمعة زجاجية وحركة تعويم وانضغاط تفاعلية.
 */
@Composable
fun ZadQuickCategoryGrid(
    onNavigateToInventory: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quickShortcutsFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quickShortcutsBobbing"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        QuickShortcutItem(
            title = stringResource(R.string.shortcut_inventory),
            subtitle = stringResource(R.string.shortcut_inventory_sub),
            icon = Icons.Default.Inventory2,
            gradientColors = ZadShortcutInventoryGradient,
            glowColor = ZadShortcutInventoryGlow,
            floatOffset = floatOffset,
            modifier = Modifier.weight(1f),
            onClick = onNavigateToInventory
        )
        QuickShortcutItem(
            title = stringResource(R.string.shortcut_shopping),
            subtitle = stringResource(R.string.shortcut_shopping_sub),
            icon = Icons.Default.ShoppingCart,
            gradientColors = ZadShortcutShoppingGradient,
            glowColor = ZadShortcutShoppingGlow,
            floatOffset = -floatOffset,
            modifier = Modifier.weight(1f),
            onClick = onNavigateToShopping
        )
        QuickShortcutItem(
            title = stringResource(R.string.shortcut_family),
            subtitle = stringResource(R.string.shortcut_family_sub),
            icon = Icons.Default.FamilyRestroom,
            gradientColors = ZadShortcutFamilyGradient,
            glowColor = ZadShortcutFamilyGlow,
            floatOffset = floatOffset,
            modifier = Modifier.weight(1f),
            onClick = onNavigateToFamily
        )
        QuickShortcutItem(
            title = stringResource(R.string.shortcut_subscriptions),
            subtitle = stringResource(R.string.shortcut_subscriptions_sub),
            icon = Icons.Default.CreditCard,
            gradientColors = ZadShortcutSubscriptionsGradient,
            glowColor = ZadShortcutSubscriptionsGlow,
            floatOffset = -floatOffset,
            modifier = Modifier.weight(1f),
            onClick = onNavigateToSubscriptions
        )
    }
}

/**
 * التوافق التراجعي لمنتقي الأقسام
 */
@Composable
fun ZadQuickCategoryGrid(
    onCategoryClick: (ZadCategoryType) -> Unit,
    modifier: Modifier = Modifier
) {
    ZadQuickCategoryGrid(
        onNavigateToInventory = { onCategoryClick(ZadCategoryType.PRODUCE) },
        onNavigateToShopping = { onCategoryClick(ZadCategoryType.PRODUCE) },
        onNavigateToFamily = { onCategoryClick(ZadCategoryType.FAMILY) },
        onNavigateToSubscriptions = { onCategoryClick(ZadCategoryType.SUBSCRIPTIONS) },
        modifier = modifier
    )
}

@Composable
private fun QuickShortcutItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    glowColor: Color,
    floatOffset: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .pressableScale()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        // 3D Metallic Glass Capsule with ambient lighting & specular sheen
        Box(
            modifier = Modifier
                .offset(y = (floatOffset * 0.7f).dp)
                .size(58.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = glowColor.copy(alpha = 0.55f),
                    ambientColor = glowColor.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(brush = Brush.verticalGradient(colors = gradientColors))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.12f),
                            glowColor.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Specular light reflection on top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 3D Vector Icon with drop shadow
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .shadow(elevation = 3.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
            )
        }

        // Title Label
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        // Subtitle Badge
        Text(
            text = subtitle,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

