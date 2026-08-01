package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Typography
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.outlineVariant
import com.example.ui.theme.primary
import com.example.ui.theme.surface
import com.example.ui.theme.surfaceContainerLow

/**
 * Cupertino-style shared controls — iOS look-alikes for the two Material
 * widgets (`Switch`, `TabRow`) used as-is (default Android styling) across
 * Tasbiha/ZadIntelligence/Profile/Inventory/Family/Subscriptions. Not wired
 * into any screen yet — each of those adopts these once the Home screen
 * direction is confirmed.
 */

/** Pill track + spring-animated thumb, replacing Material's default `Switch`. */
@Composable
fun ZadSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Kids mode's toggle is purple in the design, not brand green. */
    checkedColor: Color = primary
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val edgePadding = 2.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - edgePadding else edgePadding,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f), // same feel as ZadSprings.Press
        label = "zadSwitchThumb"
    )
    val trackColor = if (checked) checkedColor else outlineVariant

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** iOS-style segmented control: a single sliding pill highlights the selected
 * option, replacing `TabRow`/`ScrollableTabRow` usage. */
@Composable
fun ZadSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(surfaceContainerLow)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f), // same feel as ZadSprings.Screen
            label = "zadSegmentPill"
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(50), spotColor = primary.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(50))
                .background(surface)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = Typography.labelMedium,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selectedIndex) onSurface else onSurfaceVariant
                    )
                }
            }
        }
    }
}
