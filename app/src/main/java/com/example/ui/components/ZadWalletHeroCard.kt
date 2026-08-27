package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import kotlinx.coroutines.delay

/**
 * Re-imagined Apple Wallet-style dynamic balance card.
 * Matches Image 5 specification.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ZadWalletHeroCard(
    availableBalance: Double,
    spentThisCycle: Double,
    committedThisCycle: Double,
    isConfident: Boolean,
    modifier: Modifier = Modifier,
    onEditBudget: () -> Unit,
    onWhyChanged: () -> Unit,
    onQuickExpense: () -> Unit
) {
    val context = LocalContext.current

    // Spring press physics
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "hero_scale"
    )

    // Animated balance counter
    val animatedBalance by animateFloatAsState(
        targetValue = availableBalance.toFloat(),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "balance_anim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color(0xFF064E3B).copy(alpha = 0.45f),
                ambientColor = Color(0xFF052E16).copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A382C),
                        Color(0xFF0F9B76),
                        Color(0xFF064E3B),
                        Color(0xFF052E16)
                    )
                )
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    isPressed = true
                    onWhyChanged()
                },
                onLongClick = onQuickExpense
            )
            .padding(22.dp)
    ) {
        // Top Header inside card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💳", fontSize = 14.sp)
                }
                Text(
                    text = stringResource(R.string.available_label),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            IconButton(
                onClick = onEditBudget,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_monthly_budget),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Primary Balance Figure
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = (if (!isConfident) "≈ " else "") + CurrencyFormatter.format(context, animatedBalance.toDouble()),
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            if (isConfident) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sub-Breakdown Chips Row (Spent & Committed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Spent Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                )
                Text(
                    text = "${stringResource(R.string.spent_label)} ${CurrencyFormatter.format(context, spentThisCycle)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }

            // Committed Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF8066))
                )
                Text(
                    text = "${stringResource(R.string.committed_label)} ${CurrencyFormatter.format(context, committedThisCycle)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Wallet Footer: Emerald Hex Tag & Quick Edit Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#0A382C • ZAD WALLET",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
            Text(
                text = "تعديل الميزانية ✎",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6EE7B7),
                modifier = Modifier.clickable { onEditBudget() }
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
}

/**
 * Minimal Safe Daily Spend and Days Left Duo Cards (Matching Image 5).
 */
@Composable
fun ZadMinimalMetricsDuo(
    safeDailySpend: Double,
    daysLeft: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Safe Daily Spend Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.safe_daily_spend_label),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6B7280)
            )
            Text(
                text = CurrencyFormatter.format(context, safeDailySpend),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }

        // Days Left Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.days_left_label),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6B7280)
            )
            Text(
                text = "$daysLeft ${stringResource(R.string.v2_days_value, daysLeft)}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}
