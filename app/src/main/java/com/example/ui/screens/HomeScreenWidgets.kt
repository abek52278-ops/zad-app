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

// ── تنظيف 2026-09-05 (بند APP-05) ──────────────────────────────────────────────
// الملف ده كان ١٢٩٠ سطر و١٣ مكوّن، **اتنين بس** منهم بيوصلوا للتطبيق. الـ١١ التانيين
// مكانش ليهم أي نداء لا من بره الملف ولا من جوّاه:
//   FeaturesCarousel، PremiumFeatureCard، MiniInventoryWidget، MiniPharmacyWidget،
//   MiniSubscriptionsWidget، MiniItemChip، ZadAutonomousIdeasWidget،
//   LiveSpendingLineGraphWidget، ZadProHighlightWidget، StitchQuickActionGrid،
//   ZadAdEnergyWidget
//
// اتأكدت قبل الحذف إن الاتنين الباقيين مالهمش أي نداء لأي مكوّن شقيق جوّه جسمهم.
//
// ليه دلوقتي: فيه تمريرة ثيم غامق شغالة على الملفات دي، وكل مكوّن ميت كان لازم
// يتصان في كل تمريرة من غير أي عائد.

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
                            stringResource(R.string.tasbiha_challenge_progress, challengeCurrentClicks, activeChallenge.targetClicks),
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
