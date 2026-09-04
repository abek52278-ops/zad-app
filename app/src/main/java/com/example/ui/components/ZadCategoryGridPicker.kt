package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class ZadCategoryItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color
)

/**
 * ZadCategoryGridPicker — 4-Column Visual Category Grid Picker.
 * Features:
 * - 4-column adaptive layout (nested-safe via chunked Rows)
 * - Soft circular icon containers (56.dp)
 * - Clear category typography underneath (12.sp, Medium)
 * - iOS-style active selection indicator with smooth spring animation
 */
@Composable
fun ZadCategoryGridPicker(
    modifier: Modifier = Modifier,
    categories: List<ZadCategoryItem> = defaultZadCategories(),
    selectedCategoryId: String? = null,
    onCategorySelected: (ZadCategoryItem) -> Unit
) {
    val rows = categories.chunked(4)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                rowItems.forEach { item ->
                    val isSelected = item.id == selectedCategoryId
                    CategoryGridItemView(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onCategorySelected(item) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fill empty slots in the row if less than 4 items
                if (rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridItemView(
    item: ZadCategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "item_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ZadForestEmerald else Color.Transparent,
        label = "border_color"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(62.dp)
        ) {
            // Circular container with active ring indicator
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) item.backgroundColor.copy(alpha = 0.28f)
                        else item.backgroundColor
                    )
                    .border(
                        border = BorderStroke(
                            width = if (isSelected) 2.5.dp else 0.5.dp,
                            color = if (isSelected) ZadForestEmerald else ZadIosOutline
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = if (isSelected) ZadForestEmerald else item.iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Small active checkmark badge on top right
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(ZadForestEmerald)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ZadForestEmerald else ZadNeutralDark
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Default 8 core categories for household budgeting and inventory */
fun defaultZadCategories(): List<ZadCategoryItem> = listOf(
    ZadCategoryItem(
        id = "food",
        title = "طعام ومؤن",
        icon = Icons.Rounded.Restaurant,
        iconColor = ZadMustardOchre,
        backgroundColor = ZadMustardContainer
    ),
    ZadCategoryItem(
        id = "bills",
        title = "فواتير",
        icon = Icons.Rounded.ReceiptLong,
        iconColor = ZadTerracottaRust,
        backgroundColor = ZadTerracottaContainer
    ),
    ZadCategoryItem(
        id = "health",
        title = "صيدلية",
        icon = Icons.Rounded.LocalPharmacy,
        iconColor = catHealthIcon,
        backgroundColor = catHealthBg
    ),
    ZadCategoryItem(
        id = "shopping",
        title = "تسوق",
        icon = Icons.Rounded.ShoppingCart,
        iconColor = ZadForestEmerald,
        backgroundColor = ZadEmeraldContainer
    ),
    ZadCategoryItem(
        id = "transport",
        title = "مواصلات",
        icon = Icons.Rounded.DirectionsCar,
        iconColor = catTransportIcon,
        backgroundColor = catTransportBg
    ),
    ZadCategoryItem(
        id = "savings",
        title = "ادخار",
        icon = Icons.Rounded.Savings,
        iconColor = ZadForestEmeraldLight,
        backgroundColor = ZadEmeraldContainer
    ),
    ZadCategoryItem(
        id = "family",
        title = "العائلة",
        icon = Icons.Rounded.FamilyRestroom,
        iconColor = Color(0xFF6B46C1),
        backgroundColor = Color(0xFFF3E8FF)
    ),
    ZadCategoryItem(
        id = "more",
        title = "أخرى",
        icon = Icons.Rounded.Category,
        iconColor = ZadNeutralMuted,
        backgroundColor = ZadIosSurfaceVariant
    )
)
