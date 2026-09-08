package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * منتقي الأقسام السريع المحدّث — شريط رقائق أفقي أنيق (Minimalist Glass Chips Carousel)
 * بتصميم زجاجي خفيف وحركة نبض نابضية (Spring Tap Bounce) وأيقونات متجهة ثلاثية الأبعاد.
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
    Row(
        modifier = modifier
            .pressableScale(pressedScale = 0.94f)
            .clip(RoundedCornerShape(9999.dp))
            .background(category.bgPastel.copy(alpha = 0.70f))
            .border(
                width = 1.dp,
                color = category.borderTint.copy(alpha = 0.85f),
                shape = RoundedCornerShape(9999.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            ZadCategory3DIcon(
                category = category,
                modifier = Modifier.size(19.dp)
            )
        }

        Text(
            text = category.titleAr,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
            maxLines = 1
        )
    }
}

@Composable
fun ZadQuickCategoryGrid(
    onCategoryClick: (ZadCategoryType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(categoryDisplayOrder, key = { it.id }) { category ->
            ZadCategoryChip(
                category = category,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}
