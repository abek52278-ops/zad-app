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

@Composable
fun TasbihaHomeWidget(tree: TasbihaTree?, onNavigateToTasbiha: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTasbiha() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (tree?.treeType) {
                "golden" -> Color(0xFFFFF8E1)
                "special" -> Color(0xFFF3E5F5)
                else -> primaryContainer.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (tree != null) Icons.Default.Park else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Park, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (tree != null) "بستان تسبيحة" else "ابدأ تسبيحك!", fontWeight = FontWeight.Bold, color = onSurface)
                    if (tree?.treeType == "golden") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    } else if (tree?.treeType == "special") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    if (tree != null) "${tree.treeName} • ${tree.stageName()} • ${tree.score} تسبيحة"
                    else "اضغط لبدء بستان العائلة",
                    style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant
                )
                if (tree != null && tree.streakDays > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(14.dp))
                        Text(
                            "${tree.streakDays} أيام متتالية",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (tree != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { tree.progressToNext() },
                        modifier = Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = when (tree.treeType) {
                            "golden" -> Color(0xFFFFD700)
                            "special" -> Color(0xFF9C27B0)
                            else -> primary
                        },
                        trackColor = onSurface.copy(alpha = 0.1f)
                    )
                    Text(
                        "${tree.level}/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ArrowForward, null, tint = primary)
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
