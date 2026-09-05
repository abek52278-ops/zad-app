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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.data.TasbihaTree
import com.example.data.ZadInventory
import com.example.data.ZadShoppingItem
import com.example.data.ZadTransaction
import com.example.data.SupabaseRepo
import com.example.ui.components.pressableScale
import com.example.ui.components.ZadListCard
import com.example.ui.components.ZadLottieAsset
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class DailySpendPoint(
    val dayName: String,
    val shortDate: String,
    val amount: Double
)

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
                Text(stringResource(R.string.auto_homescreenwidgets_66761), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (inventory.isEmpty()) {
                Text(stringResource(R.string.auto_homescreenwidgets_70957), style = Typography.bodyMedium, color = Color.Gray)
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
                Text(stringResource(R.string.auto_homescreenwidgets_59477), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (shoppingList.isEmpty()) {
                Text(stringResource(R.string.auto_homescreenwidgets_18310), style = Typography.bodyMedium, color = Color.Gray)
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
                Text(stringResource(R.string.auto_homescreenwidgets_38951), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (lowStock.isEmpty()) {
                Text(stringResource(R.string.auto_homescreenwidgets_76814), style = Typography.bodyMedium, color = Color.Gray)
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
                    Text(stringResource(R.string.auto_homescreenwidgets_17406), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Filled.ArrowForward, contentDescription = "View All", tint = primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (upcoming.isEmpty()) {
                Text(stringResource(R.string.auto_homescreenwidgets_19179), style = Typography.bodyMedium, color = Color.Gray)
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
    onNavigateToTasbiha: () -> Unit,
    activeChallenge: com.example.data.TasbihaChallenge? = null,
    challengeCurrentClicks: Int = 0
) {
    val pct = ((tree?.progressToNext() ?: 0f) * 100).toInt().coerceIn(0, 100)
    val animatedPct by animateFloatAsState(
        targetValue = (tree?.progressToNext() ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "tasbiha_progress"
    )
    val tapScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var lastLevel by remember(tree?.id) { mutableStateOf(tree?.level ?: 1) }
    var showConfetti by remember { mutableStateOf(false) }
    // floatY من البروتوتايب: 8px صعود وهبوط على لوب 3.4s ease-in-out
    val treeFloat = rememberInfiniteTransition(label = "treeFloat").animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "treeFloatY"
    )
    // بتلات التسبيح — كل ضغطة على "سبّح" بتطلق دفعة بتلات (rise 1.4s من البروتوتايب)
    var petalBurstKey by remember { mutableStateOf(0) }
    var petalsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(petalBurstKey) {
        if (petalBurstKey > 0) {
            petalsVisible = true
            kotlinx.coroutines.delay(1400)
            petalsVisible = false
        }
    }
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
                    Text(stringResource(R.string.auto_homescreenwidgets_20673), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = textSecondary)
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
                // البستان الحي — أنيميشن Lottie حقيقي من res/raw بدل الإيموجي الثابت.
                // garden burst (بتلات متطايرة) بتشتغل loop دائم خلف الشجرة، وconfetti
                // بيطلع مرة واحدة لحظة ترقية المستوى فوقها.
                ZadLottieAsset(
                    resId = R.raw.zad_v4_garden_burst,
                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                    modifier = Modifier.size(96.dp),
                    contentDescription = null
                )
                Text(
                    tree?.stageEmoji() ?: "🌰",
                    fontSize = 50.sp,
                    // floatY من البروتوتايب: صعود وهبوط لطيف خلف اللوتي
                    modifier = Modifier
                        .graphicsLayer { translationY = -treeFloat.value }
                        .scale(tapScale.value)
                )
                if (petalsVisible) {
                    // rise 1.4s: البتلات بتطلع من الشجرة لفوق 140px وتتلاشى (أنيميشن حقيقي)
                    val petalRise = remember(petalBurstKey) {
                        androidx.compose.animation.core.Animatable(0f)
                    }
                    LaunchedEffect(petalBurstKey) {
                        petalRise.snapTo(0f)
                        petalRise.animateTo(
                            1f,
                            animationSpec = tween(1400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.graphicsLayer {
                            translationY = -140f * petalRise.value
                            alpha = 1f - petalRise.value
                        }
                    ) {
                        listOf("🍃", "🌸", "🍃", "🌸").forEach { Text(it, fontSize = 16.sp) }
                    }
                }
                if (showConfetti) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("🌸", "🍃", "🌸", "🍃", "🌸").forEach { Text(it, fontSize = 16.sp) }
                    }
                    ZadLottieAsset(
                        resId = R.raw.lottie_confetti_burst,
                        iterations = 1,
                        modifier = Modifier.size(140.dp),
                        contentDescription = null
                    )
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
                            // UI.tapTasbih من البروتوتايب: هزة خفيفة (vibrate 12) + بتلات
                            // متطايرة 🍃🌸 فوق الشجرة مع كل ضغطة
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    vib?.vibrate(android.os.VibrationEffect.createOneShot(12, 140))
                                } else {
                                    @Suppress("DEPRECATION") vib?.vibrate(12)
                                }
                            } catch (_: Exception) {}
                            petalBurstKey++
                            scope.launch {
                                tapScale.animateTo(1.22f, animationSpec = com.example.ui.components.ZadSprings.Celebrate)
                                tapScale.animateTo(1f, animationSpec = com.example.ui.components.ZadSprings.Press)
                            }
                            onTasbih()
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auto_homescreenwidgets_18996), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // تحدي التسبيحة العائلي — مؤشر تقدم دائري حقيقي مرتبط بـ
            // family_tasbiha_challenges/tasbiha_challenge_progress (§7.4). بيتخفي
            // تمامًا لو مفيش تحدي نشط، مفيش حالة فاضية مزيفة.
            if (activeChallenge != null) {
                val challengeProgress = (challengeCurrentClicks.toFloat() / activeChallenge.targetClicks.coerceAtLeast(1))
                    .coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(kidsPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { challengeProgress },
                            modifier = Modifier.size(40.dp),
                            color = kidsPrimary,
                            trackColor = kidsPrimary.copy(alpha = 0.15f),
                            strokeWidth = 4.dp
                        )
                        Text("${(challengeProgress * 100).toInt()}%", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = kidsPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(activeChallenge.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1)
                        Text(
                            "$challengeCurrentClicks من ${activeChallenge.targetClicks} تسبيحة",
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
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

/**
 * 💡 ويدجت نبضات وأفكار عقل زاد اليومية (الذكاء الاستباقي المستقل)
 * نصائح توفير مخصصة، اقتراح وجبات من المخزون، ورادار للمناسبات.
 */
@Composable
fun ZadAutonomousIdeasWidget(
    inventory: List<ZadInventory>,
    onAskAi: (String) -> Unit = {},
    onAddToShopping: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ideaIndex by remember { mutableStateOf(0) }

    val ideas = remember(inventory) {
        val list = mutableListOf<Triple<String, String, String>>()
        // 1. فكرة توفير — نص نظيف بلا إيموجي (الأيقونة ملصوقة بالرأس بتشوّه التايبوغرافي)
        list.add(
            Triple(
                "حيلة توفير اليوم",
                "شراء الأساسيات كـ (أرز، زيت، منظفات) في العروض الأسبوعية بالحجم العائلي يوفّر ~18% من فاتورة مشتريات الشهر.",
                "ضيف للمشتريات"
            )
        )
        // 2. فكرة وجبة من المخزون
        val availableNames = inventory.filter { it.quantity > 0 }.map { it.itemName }.take(3)
        if (availableNames.isNotEmpty()) {
            list.add(
                Triple(
                    "وجبة ذكية من مخزونك",
                    "عندك في المخزون (${availableNames.joinToString("، ")}). تقدر تعمل وجبة غداء سريعة واقتصادية من غير ما تطلب دليفري!",
                    "وريني الوصفة"
                )
            )
        }
        // 3. رادار المناسبات
        list.add(
            Triple(
                "رادار مواسم زاد",
                "الاستعداد المبكر لمناسبات الشهر بيحميك من الطوارئ. عقل زاد حجز لك جزءاً من الميزانية تلقائياً لتفادي أي عجز.",
                "استعرض الميزانية"
            )
        )
        list
    }

    val currentIdea = ideas.getOrElse(ideaIndex % ideas.size) { ideas.first() }

    ZadListCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF9333EA)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(currentIdea.first, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                }
                IconButton(
                    onClick = { ideaIndex++ },
                    modifier = Modifier.size(30.dp).pressableScale()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "فكرة أخرى", tint = primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                currentIdea.second,
                style = Typography.bodyMedium,
                color = onSurfaceVariant,
                lineHeight = 20.sp,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAskAi(currentIdea.second) },
                    modifier = Modifier.weight(1f).height(36.dp).pressableScale(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(currentIdea.third, style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                OutlinedButton(
                    onClick = { ideaIndex++ },
                    modifier = Modifier.height(36.dp).pressableScale(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.auto_homescreenwidgets_25768), style = Typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 📈 ويدجت مؤشر الإنفاق اليومي المباشر (Live Daily Spending Trend Curve)
 * جراف خطي انسيابي تفاعلي يوضح وتيرة الصرف لآخر 7 أيام مع نقاط التفاعل والمتوسط اليومي.
 */
@Composable
fun LiveSpendingLineGraphWidget(
    transactions: List<ZadTransaction>,
    onNavigateToBudget: () -> Unit = {}
) {
    val context = LocalContext.current
    val currency = remember { com.example.data.MarketPrefs.getMarket(context).currencySymbol }

    // حساب آخر 7 أيام
    val dailyData: List<DailySpendPoint> = remember(transactions) {
        val today = java.time.LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale("ar"))
        val shortFormatter = java.time.format.DateTimeFormatter.ofPattern("d/M")

        days.map { date ->
            val dateStr = date.toString()
            val daySpend = transactions.filter {
                (it.isExpense) && (it.createdAt?.take(10) == dateStr)
            }.sumOf { it.amount }

            val dayName = date.format(formatter)
            val shortDate = date.format(shortFormatter)
            DailySpendPoint(dayName, shortDate, daySpend)
        }
    }

    val total7Days = dailyData.sumOf { it.amount }
    val avgDaily = total7Days / 7.0
    val maxSpend = maxOf(dailyData.maxOf { it.amount }, 10.0)

    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    ZadListCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ShowChart, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.auto_homescreenwidgets_31620), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        Text("متوسط الصرف: ${com.example.data.CurrencyFormatter.format(context, avgDaily)} $currency / يوم", style = Typography.labelSmall, color = onSurfaceVariant, fontSize = 10.sp)
                    }
                }
                TextButton(
                    onClick = onNavigateToBudget,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.auto_homescreenwidgets_87424), style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tooltip or selected day highlight
            val activeIndex = selectedDayIndex ?: (dailyData.size - 1)
            val selectedInfo = dailyData.getOrNull(activeIndex)
            if (selectedInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedInfo.dayName} (${selectedInfo.shortDate})", style = Typography.labelSmall, color = onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text(
                        "${com.example.data.CurrencyFormatter.format(context, selectedInfo.amount)} $currency",
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedInfo.amount > avgDaily * 1.3) dangerColor else primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Interactive Bezier Curve Chart
            val pointsCount = dailyData.size
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // DrawScope is not a composable context, so the theme-aware token is read
                // here and passed in. Reading `primary` inside the lambda does not compile.
                val lineColor = primary
                val selectedDotColor = successColor
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height - 24f
                    val pointSpacing = if (pointsCount > 1) canvasWidth / (pointsCount - 1) else canvasWidth

                    val points: List<androidx.compose.ui.geometry.Offset> = dailyData.mapIndexed { index, item ->
                        val x = index * pointSpacing
                        val y = canvasHeight - ((item.amount / maxSpend).toFloat() * (canvasHeight - 20f))
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    // Average horizontal dashed line
                    val avgY = canvasHeight - ((avgDaily / maxSpend).toFloat() * (canvasHeight - 20f))
                    drawLine(
                        color = Color(0xFF64748B).copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(0f, avgY),
                        end = androidx.compose.ui.geometry.Offset(canvasWidth, avgY),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )

                    // Draw Smooth Cubic Curve
                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 0 until points.size - 1) {
                                val current = points[i]
                                val next = points[i + 1]
                                val controlPoint1 = androidx.compose.ui.geometry.Offset((current.x + next.x) / 2f, current.y)
                                val controlPoint2 = androidx.compose.ui.geometry.Offset((current.x + next.x) / 2f, next.y)
                                cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, next.x, next.y)
                            }
                        }
                    }

                    // Fill gradient below curve
                    val fillPath = androidx.compose.ui.graphics.Path().apply {
                        addPath(path)
                        lineTo(canvasWidth, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f))
                        )
                    )

                    // Stroke line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )

                    // Draw Point Dots
                    points.forEachIndexed { i, p ->
                        val isSel = i == activeIndex
                        drawCircle(
                            color = Color.White,
                            radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                            center = p
                        )
                        drawCircle(
                            color = if (isSel) selectedDotColor else lineColor,
                            radius = if (isSel) 4.5.dp.toPx() else 2.5.dp.toPx(),
                            center = p
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Day Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for ((index, item) in dailyData.withIndex()) {
                    val isSel = index == activeIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { selectedDayIndex = index }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.dayName.take(3),
                            style = Typography.labelSmall,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) primary else onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 👑 ويدجت زاد بريميوم الملون والمغري (Zad Pro Dynamic Highlight Widget)
 * بطاقة مضيئة جذابة بتصميم أبل السائل تعرض حالة طاقة الذكاء الاصطناعي وباقات الاشتراك.
 */
@Composable
fun ZadProHighlightWidget(
    onNavigateToPlans: () -> Unit = {}
) {
    val context = LocalContext.current
    var adWatchCount by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.getAdWatchCount(context)) }
    var isSessionUnlocked by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.isSessionUnlocked(context)) }

    LaunchedEffect(Unit) {
        com.example.ads.RewardedBrainAdManager.syncServerState(context)?.let { state ->
            adWatchCount = state.adWatchCount
            isSessionUnlocked = state.brainSessionActive
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF064E3B),
                        Color(0xFF0F766E),
                        Color(0xFF1E3A8A)
                    )
                )
            )
            .clickable { onNavigateToPlans() }
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primaryFixed, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.auto_homescreenwidgets_96665), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stringResource(R.string.auto_homescreenwidgets_55260), style = Typography.labelSmall, color = primaryFixed, fontSize = 10.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSessionUnlocked) successColor else Color(0xFFF59E0B))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isSessionUnlocked) "جلسة نشطة ⚡" else "ترقية ⭐",
                        style = Typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.auto_homescreenwidgets_39973),
                style = Typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 18.sp,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = primaryFixed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.auto_homescreenwidgets_20825), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.auto_homescreenwidgets_7454), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryFixed)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = primaryFixed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun StitchQuickActionGrid(
    onVoiceShopping: () -> Unit,
    onScanReceipt: () -> Unit,
    onAddToInventory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Voice Shopping Action
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(primary, Color(0xFF0D5C46))
                    )
                )
                .pressableScale()
                .clickable { onVoiceShopping() }
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "قولي مشترياتك",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.auto_homescreenwidgets_55813),
                    style = Typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
                Text(stringResource(R.string.auto_homescreenwidgets_10567),
                    style = Typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }

        // 2. Receipt Scanner Action
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(secondary, Color(0xFFD97706))
                    )
                )
                .pressableScale()
                .clickable { onScanReceipt() }
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = "مسح فاتورة",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.auto_homescreenwidgets_13791),
                    style = Typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
                Text(stringResource(R.string.auto_homescreenwidgets_49739),
                    style = Typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }

        // 3. Quick Inventory Restock Action
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(tertiary, Color(0xFF92400E))
                    )
                )
                .pressableScale()
                .clickable { onAddToInventory() }
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddShoppingCart,
                        contentDescription = "إضافة للمخزون",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.auto_homescreenwidgets_12649),
                    style = Typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
                Text(stringResource(R.string.auto_homescreenwidgets_95862),
                    style = Typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ZadAdEnergyWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var adWatchCount by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.getAdWatchCount(context)) }
    var isSessionUnlocked by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.isSessionUnlocked(context)) }

    LaunchedEffect(Unit) {
        com.example.ads.RewardedBrainAdManager.syncServerState(context)?.let { state ->
            adWatchCount = state.adWatchCount
            isSessionUnlocked = state.brainSessionActive
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.03f))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auto_homescreenwidgets_29197),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "شاهد 3 إعلانات لشحن بطارية زاد بـ 5 محادثات وجلسة مجانية! (يتبقى ${if (adWatchCount >= 3) 3 else 3 - adWatchCount})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            
            if (isSessionUnlocked) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.auto_homescreenwidgets_69631),
                    style = MaterialTheme.typography.labelSmall,
                    color = successColor,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    com.example.ads.RewardedBrainAdManager.showRewardedEnergyAd(
                        context = context,
                        onAdWatched = { newCount, fullyUnlocked ->
                            adWatchCount = newCount
                            isSessionUnlocked = fullyUnlocked
                            android.widget.Toast.makeText(context, "تمت إضافة الرصيد بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onFailed = {
                            android.widget.Toast.makeText(context, "لم نتمكن من تحميل الإعلان حالياً", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .pressableScale(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primary)
            ) {
                Text(
                    "شاهد إعلان لجمع الرصيد 🎥 (${adWatchCount}/3)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
