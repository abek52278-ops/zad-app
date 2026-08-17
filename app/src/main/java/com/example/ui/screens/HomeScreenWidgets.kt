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
    // white card with the design's two-layer shadow — these were flat, zero-elevation
    // half-transparent grey on the grey canvas, so they had no edge at all
    com.example.ui.components.ZadListCard(
        modifier = Modifier.clickable { onNavigateToInventory() },
        shape = RoundedCornerShape(24.dp),
        contentPadding = 0.dp
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
    // white card with the design's two-layer shadow — these were flat, zero-elevation
    // half-transparent grey on the grey canvas, so they had no edge at all
    com.example.ui.components.ZadListCard(
        modifier = Modifier.clickable { onNavigateToShopping() },
        shape = RoundedCornerShape(24.dp),
        contentPadding = 0.dp
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
                // Duplicate names (e.g. "مياه صفا" added three separate times) merge into one
                // row with a "(xN)" multiplier instead of repeating the same row three times.
                val aggregated = shoppingList.filter { !it.isPurchased }
                    .groupBy { it.itemName }
                    .map { (name, items) -> name to items.sumOf { it.quantity } }
                    .take(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    aggregated.forEach { (name, totalQuantity) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Circle, contentDescription = null, tint = dangerColor, modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (totalQuantity > 1) "$name (x$totalQuantity)" else name,
                                style = Typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/** أدوية قرّب رصيدها ينفد (أقل من ٣ أيام على معدل جرعتها اليومي) — نفس أسلوب
 *  [MiniInventoryWidget] بالظبط، عشان الرئيسية تبقى فيها نظرة سريعة على الصيدلية زي
 *  المخزون تماماً، مش لازم تفتح شاشة الصيدلية عشان تعرف إيه اللي محتاج تجديد. */
@Composable
fun MiniPharmacyWidget(pharmacyItems: List<com.example.data.ZadPharmacyItem>, onNavigateToPharmacy: () -> Unit) {
    val lowStock = pharmacyItems.filter { it.remainingQuantity <= it.dailyDoseCount * 3 }
    com.example.ui.components.ZadListCard(
        modifier = Modifier.clickable { onNavigateToPharmacy() },
        shape = RoundedCornerShape(24.dp),
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الصيدلية — قريب من النفاد", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (lowStock.isEmpty()) {
                Text("كل الأدوية رصيدها كفاية حالياً.", style = Typography.bodyMedium, color = Color.Gray)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lowStock.take(5)) { item ->
                        MiniItemChip(name = item.name, quantity = "${item.remainingQuantity} ${item.unit}")
                    }
                }
            }
        }
    }
}

/** اشتراكات هتتجدد خلال ٧ أيام — نفس أسلوب [MiniShoppingWidget] (صف بنقطة + اسم +
 *  مبلغ)، عشان تجديد قريب يبان قبل ما يتخصم لا بعده. */
@Composable
fun MiniSubscriptionsWidget(subscriptions: List<com.example.data.ZadSubscription>, onNavigateToSubscriptions: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = java.time.LocalDate.now()
    val upcoming = subscriptions
        .filter { it.isActive }
        .mapNotNull { sub ->
            val renewal = sub.renewalDate?.let { raw -> try { java.time.LocalDate.parse(raw.take(10)) } catch (e: Exception) { null } }
            renewal?.let { r -> if (!r.isBefore(today) && r.isBefore(today.plusDays(8))) sub to r else null }
        }
        .sortedBy { it.second }

    com.example.ui.components.ZadListCard(
        modifier = Modifier
            .pressableScale(pressedScale = 0.98f)
            .clickable { onNavigateToSubscriptions() },
        shape = RoundedCornerShape(24.dp),
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Subscriptions, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اشتراكات هتتجدد قريب", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (upcoming.isEmpty()) {
                Text("لا يوجد تجديد اشتراكات خلال الأسبوع الجاي.", style = Typography.bodyMedium, color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    upcoming.take(3).forEach { (sub, renewal) ->
                        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, renewal)
                        val brand = com.example.ui.components.subscriptionBrandFor(sub.title, sub.provider)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceContainerLow.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (brand != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(brand.color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(brand.icon, contentDescription = null, tint = brand.color, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = primary, modifier = Modifier.size(8.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(sub.title, style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(if (daysLeft == 0L) "تجديد اليوم ⚡" else "خلال $daysLeft يوم", fontSize = 11.sp, color = textTertiary)
                                }
                            }
                            Text(
                                com.example.data.CurrencyFormatter.format(context, sub.amount),
                                style = Typography.labelMedium,
                                color = onSurface,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home's Tasbiha garden card with floating tree physics & animated progress
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
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "tasbiha_progress"
    )
    val tapScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

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
        modifier = Modifier
            .pressableScale(pressedScale = 0.98f)
            .clickable { onNavigateToTasbiha() },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White.copy(alpha = 0.78f),
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Park, contentDescription = null, tint = secondaryDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بستان التسبيح", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryLight.copy(alpha = 0.15f)
                ) {
                    Text(
                        "$pct%",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tree?.stageEmoji() ?: "🌰",
                    fontSize = 50.sp,
                    modifier = Modifier.scale(tapScale.value)
                )
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
                    color = onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(kidsPrimary, Color(0xFF9333EA))
                            )
                        )
                        .pressableScale()
                        .clickable {
                            scope.launch {
                                tapScale.animateTo(1.22f, animationSpec = com.example.ui.components.ZadSprings.Celebrate)
                                tapScale.animateTo(1f, animationSpec = com.example.ui.components.ZadSprings.Press)
                            }
                            onTasbih()
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("سبحان الله", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
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
