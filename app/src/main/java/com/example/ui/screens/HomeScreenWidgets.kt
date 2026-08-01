package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import com.example.data.TasbihaTree
import com.example.data.ZadInventory
import com.example.data.ZadShoppingItem
import com.example.data.SupabaseRepo
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FeaturesCarousel(
    onNavigateToAssistant: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToSubscriptions: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            PremiumFeatureCard(
                title = "عقل زاد",
                icon = Icons.Filled.AutoAwesome,
                gradient = Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF3F3D56))),
                onClick = onNavigateToAssistant
            )
        }
        item {
            PremiumFeatureCard(
                title = "المخزون الذكي",
                icon = Icons.Filled.Kitchen,
                gradient = Brush.linearGradient(listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))),
                onClick = onNavigateToInventory
            )
        }
        item {
            PremiumFeatureCard(
                title = "وكيل التسوق",
                icon = Icons.Filled.ShoppingCart,
                gradient = Brush.linearGradient(listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF))),
                onClick = onNavigateToShopping
            )
        }
        item {
            PremiumFeatureCard(
                title = "عائلة زاد",
                icon = Icons.Filled.FamilyRestroom,
                gradient = Brush.linearGradient(listOf(Color(0xFFF6D365), Color(0xFFFDA085))),
                onClick = onNavigateToFamily
            )
        }
        item {
            PremiumFeatureCard(
                title = "الاشتراكات",
                icon = Icons.Filled.Subscriptions,
                gradient = Brush.linearGradient(listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4))),
                onClick = onNavigateToSubscriptions
            )
        }
    }
}

@Composable
fun PremiumFeatureCard(title: String, icon: ImageVector, gradient: Brush, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(100), label = "pfc_scale"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pfc_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .background(brush = gradient, alpha = glowAlpha)
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }.also { src ->
                        LaunchedEffect(src) {
                            src.interactions.collect { interaction ->
                                when (interaction) {
                                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed = true
                                    is androidx.compose.foundation.interaction.PressInteraction.Release -> pressed = false
                                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed = false
                                    else -> {}
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun MiniInventoryWidget(inventory: List<ZadInventory>, onNavigateToInventory: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToInventory() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المخزون السريع", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (inventory.isEmpty()) {
                Text("المخزون فارغ حالياً.", style = Typography.bodyMedium, color = Color.Gray)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(inventory.take(5)) { item ->
                        MiniItemChip(name = item.itemName, quantity = "${item.quantity} ${item.unit}")
                    }
                }
            }
        }
    }
}

@Composable
fun MiniShoppingWidget(shoppingList: List<ZadShoppingItem>, onNavigateToShopping: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToShopping() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("طلبات عاجلة للتسوق", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (shoppingList.isEmpty()) {
                Text("لا يوجد طلبات عاجلة.", style = Typography.bodyMedium, color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shoppingList.filter { !it.isPurchased }.take(3).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Circle, contentDescription = null, tint = dangerColor, modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(item.itemName, style = Typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${item.quantity}", style = Typography.bodyMedium, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home's Tasbiha garden card, rebuilt to the mockup's `tasbihaTitle` block:
 * translucent glass, the completion percentage on the trailing edge, the stage
 * emoji large and centered, a progress bar, and the count next to a purple tap
 * button.
 *
 * The mockup taps a hardcoded counter; here the button calls the real
 * `FamilyViewModel.tasbihaClick()` (Supabase-backed, streak-aware), so tasbih no
 * longer requires opening the full garden screen — tapping the card body still
 * does that.
 */
@Composable
fun TasbihaHomeWidget(
    tree: TasbihaTree?,
    onTasbih: () -> Unit,
    onNavigateToTasbiha: () -> Unit
) {
    val pct = ((tree?.progressToNext() ?: 0f) * 100).toInt().coerceIn(0, 100)
    val animatedPct by animateFloatAsState(
        targetValue = (tree?.progressToNext() ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "tasbiha_progress"
    )
    // The mockup rains petals the moment the bar fills; a level-up is this app's
    // equivalent milestone, and it is the only event worth interrupting for.
    var lastLevel by remember(tree?.id) { mutableStateOf(tree?.level ?: 1) }
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(tree?.level) {
        val level = tree?.level ?: 1
        if (level > lastLevel) {
            showConfetti = true
            kotlinx.coroutines.delay(2200)
            showConfetti = false
        }
        lastLevel = level
    }

    com.example.ui.components.GlassCard(
        modifier = Modifier.clickable { onNavigateToTasbiha() },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White.copy(alpha = 0.7f),
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("بستان التسبيح", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                Spacer(Modifier.weight(1f))
                Text("$pct%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = primaryLight)
            }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(tree?.stageEmoji() ?: "🌰", fontSize = 46.sp)
                if (showConfetti) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("🌸", "🍃", "🌸", "🍃", "🌸").forEach { Text(it, fontSize = 16.sp) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0F172A).copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(kidsPrimary, primaryLight)))
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${tree?.score ?: 0} / ${tree?.nextLevelAt()?.takeIf { it != Int.MAX_VALUE } ?: (tree?.score ?: 0)}",
                    fontSize = 12.sp,
                    color = onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(kidsPrimary)
                        .pressableScale()
                        .clickable { onTasbih() }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text("سبحان الله", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun MiniItemChip(name: String, quantity: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(quantity, style = Typography.labelSmall, color = primary)
    }
}
