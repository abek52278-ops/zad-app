package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PremiumTopBar(
    userName: String,
    avatarUrl: String? = null,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            userName.trim().take(1).ifEmpty { "؟" }.uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }
                } else {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(surface),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(background)
                    .padding(2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(successColor))
                }
            }
            Column {
                Text("مرحباً بعودتك", fontSize = 11.sp, color = textTertiary)
                Text(userName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "زاد",
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                color = secondaryLight // Gold
            )
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .clickable { onNotificationsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp).bellShake(enabled = hasUnreadNotifications)
                )
                if (hasUnreadNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dangerColor)
                    )
                }
            }
        }
    }
}

/**
 * Signature hero: a circular budget gauge instead of another rounded
 * rectangle — the ring depletes as the month's spending eats into the
 * budget, remaining balance sits big and centered inside it. Replaces
 * the old rectangular PremiumHeroCard.
 */
/**
 * Signature hero, v2: a physical-card silhouette (chip + wordmark + big
 * balance + usage bar like a card's magnetic stripe) instead of the
 * circular gauge — replaces ZadBudgetGauge.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ZadCardHero(
    budget: Double,
    spent: Double,
    remaining: Double,
    daysLeft: Int,
    onDepositClick: () -> Unit,
    // Task 26 — "متاح" (available) هو الرقم الأساسي دلوقتي، remaining بقى تفصيل ثانوي.
    // available == remaining لحد ما فيه التزامات مؤكدة (committed > 0)، فمفيش تغيير مرئي
    // لمستخدم لسه ما سجلش/أكدش أي التزام.
    available: com.example.data.Figure = com.example.data.Figure(remaining, confident = true),
    committed: Double = 0.0,
    nextObligationText: String? = null,
    // Task 27.2 — طول الضغط على الرقم بيفتح شيت "آخر التغييرات" (zad_brain_runs.mutations)
    onAvailableLongPress: () -> Unit = {}
) {
    val currencyContext = LocalContext.current
    val spentPct = if (budget > 0) (spent / budget * 100).toInt() else 0
    val progress = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progress else 0f,
        animationSpec = ZadSprings.Screen,
        label = "cardHeroProgress"
    )

    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .shadow(elevation = 22.dp, shape = cardShape, spotColor = primary.copy(alpha = 0.4f))
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(primaryDark, primary, primaryDark)))
            .padding(22.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // EMV-chip silhouette
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(secondaryLight, secondary)))
                )
                Text("زاد", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                stringResource(R.string.available_label),
                style = Typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            // Task 27.1(a) — "≈" لو فيه معاملة مش متأكدة، تاب يشرح السبب. طول الضغط (27.2)
            // يفتح شيت "آخر التغييرات" عن طريق onAvailableLongPress.
            var showAvailableReason by remember { mutableStateOf(false) }
            Text(
                (if (!available.confident) "≈ " else "") +
                    com.example.data.CurrencyFormatter.formatNumber(currencyContext, available.value),
                style = Typography.displayLarge.copy(fontSize = 36.sp, letterSpacing = 1.5.sp),
                color = if (available.value < 0) dangerColor else Color.White,
                modifier = Modifier.combinedClickable(
                    onClick = { if (!available.confident) showAvailableReason = true },
                    onLongClick = onAvailableLongPress
                )
            )
            if (showAvailableReason && available.reason != null) {
                AlertDialog(
                    onDismissRequest = { showAvailableReason = false },
                    confirmButton = { TextButton(onClick = { showAvailableReason = false }) { Text(stringResource(R.string.close_action)) } },
                    title = { Text(stringResource(R.string.available_label) + " ≈") },
                    text = { Text(available.reason!!) }
                )
            }
            // Task 26 — تفصيل "متبقي X · محجوز Y" تحت الرقم الأساسي. بيظهر بس لو فيه
            // التزامات فعلاً (committed > 0)، عشان مستخدم من غير التزامات مسجلة يشوف نفس
            // الشاشة القديمة بالظبط.
            if (committed > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (nextObligationText != null) {
                        stringResource(
                            R.string.available_breakdown_with_next,
                            com.example.data.CurrencyFormatter.format(currencyContext, remaining),
                            com.example.data.CurrencyFormatter.format(currencyContext, committed),
                            nextObligationText
                        )
                    } else {
                        stringResource(
                            R.string.available_breakdown,
                            com.example.data.CurrencyFormatter.format(currencyContext, remaining),
                            com.example.data.CurrencyFormatter.format(currencyContext, committed)
                        )
                    },
                    style = Typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (spentPct >= 100) dangerColor else Color.White)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.days_label), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("$daysLeft", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text(stringResource(R.string.spent_label), style = Typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(
                        com.example.data.CurrencyFormatter.format(currencyContext, spent),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(50),
                    onClick = onDepositClick,
                    modifier = Modifier.pressableScale()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deposit), tint = primaryDark, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.deposit), style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = primaryDark)
                    }
                }
            }
        }
    }
}

/**
 * Task 19.0 معيار قبول ٦ — بديل ZadCardHero لما monthly_limit لسه مش مؤكد. عمداً
 * بيعرض دعوة لضبط السقف بدل رقم مش موثوق فيه — نفس نمط الحالة الفاضية (أيقونة +
 * عنوان + سطر توضيح + زرار) المستخدم في باقي الشاشات، مش رقم صفر مضلل.
 */
@Composable
fun BudgetSetupPromptCard(onSetBudget: () -> Unit) {
    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .shadow(elevation = 22.dp, shape = cardShape, spotColor = primary.copy(alpha = 0.4f))
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(primaryDark, primary, primaryDark)))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.budget_setup_prompt_title),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.budget_setup_prompt_subtitle),
                style = Typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSetBudget,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = primary),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(stringResource(R.string.budget_setup_prompt_action), style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

data class ZadShortcutItem(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

/** Circular quick-access row — one tappable circle per major screen, so the
 * customer reaches any page from Home in one tap instead of the drawer. */
@Composable
fun ZadPageShortcutsRow(items: List<ZadShortcutItem>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        itemsIndexed(items) { index, item ->
            AppearOnEntry(delayMs = (index * 50).coerceAtMost(400)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(68.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape, spotColor = item.color.copy(alpha = 0.35f))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(item.color, item.color.copy(alpha = 0.8f))))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = item.onClick
                            )
                            .pressableScale(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        item.label,
                        style = Typography.labelSmall,
                        color = onSurfaceVariant,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumQuickStatsRow(
    inventoryCount: Int,
    activeSubsCount: Int,
    familyCount: Int,
    onInventoryClick: () -> Unit,
    onSubsClick: () -> Unit,
    onFamilyClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickStatCard(stringResource(R.string.nav_inventory), "$inventoryCount", stringResource(R.string.quick_stat_inventory_unit), Icons.Default.Inventory2, primary, onInventoryClick, Modifier.weight(1f))
        QuickStatCard(stringResource(R.string.quick_stat_subscriptions_title), "$activeSubsCount", stringResource(R.string.quick_stat_subscriptions_unit), Icons.Default.Subscriptions, secondaryDark, onSubsClick, Modifier.weight(1f))
        QuickStatCard(stringResource(R.string.nav_family), "$familyCount", stringResource(R.string.quick_stat_family_unit), Icons.Default.FamilyRestroom, catDailyIcon, onFamilyClick, Modifier.weight(1f))
    }
}

/** Bold, distinctly-colored tile per stat — each stat is its own saturated
 * "gadget" instead of 3 uniform white cards, closer to the colorful
 * multi-shape dashboard reference than a faint icon-chip tint. */
@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = cardShape, spotColor = color.copy(alpha = 0.35f))
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .pressableScale()
            .padding(15.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = Typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
        Text(subtitle, style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun PremiumInsightBanner(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(secondary.copy(alpha = 0.13f), primary.copy(alpha = 0.10f))))
            .clickable { onClick() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(secondary.copy(alpha = 0.27f), primary.copy(alpha = 0.20f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = primaryDark, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = Typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = textPrimary)
            Text(subtitle, style = Typography.labelSmall, color = textSecondary, lineHeight = 16.sp)
        }
    }
}

@Composable
fun ShortagesSummaryCard(shortageCount: Int, onViewShortagesClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(dangerColor.copy(alpha = 0.10f))
            .clickable { onViewShortagesClick() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(dangerColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null, tint = dangerColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.shortages_summary_title, shortageCount),
                style = Typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                stringResource(R.string.shortages_summary_subtitle),
                style = Typography.labelSmall,
                color = textSecondary
            )
        }
        Surface(shape = RoundedCornerShape(50), color = dangerColor) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.view_shortages_action), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun PremiumMealsRow(meals: List<String>, onMealClick: (String) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
                Text("وجبات من ثلاجتك", fontSize = 15.sp, fontWeight = FontWeight.Black, color = textPrimary)
            }
            Text("المزيد", fontSize = 11.sp, color = primaryLight)
        }
        Spacer(modifier = Modifier.height(11.dp))
        if (meals.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = textTertiary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("لا توجد اقتراحات وجبات حالياً — ضيف أصناف لمخزونك عشان زاد يقترح لك", fontSize = 11.sp, color = textTertiary)
            }
            return@Column
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(meals) { meal ->
                Box(
                    modifier = Modifier
                        .width(135.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(surface)
                        .clickable { onMealClick(meal) }
                        .padding(13.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier.size(31.dp).clip(CircleShape).background(primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val mealIcon = when {
                                meal.contains("كبسة") -> Icons.Default.RiceBowl
                                meal.contains("سلطة") -> Icons.Default.Grass
                                else -> Icons.Default.RamenDining
                            }
                            Icon(mealIcon, contentDescription = null, tint = catFoodIcon, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(meal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("اقتراح — افتح الوصفة للتفاصيل", fontSize = 9.sp, color = textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumTransactionsRow(transactions: List<com.example.data.ZadTransaction>, onSeeAllClick: () -> Unit) {
    val txCurrencyContext = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
                Text("أحدث العمليات", fontSize = 15.sp, fontWeight = FontWeight.Black, color = textPrimary)
            }
            Text("سجل كامل", fontSize = 11.sp, color = primaryLight, modifier = Modifier.clickable { onSeeAllClick() })
        }
        Spacer(modifier = Modifier.height(11.dp))
        if (transactions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = textTertiary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("لا توجد عمليات مسجلة بعد", fontSize = 12.sp, color = textTertiary)
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val recent = transactions.take(3)

            for (tx in recent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surface)
                        .padding(13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (tx.isExpense) dangerColor.copy(alpha=0.15f) else successColor.copy(alpha=0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (tx.isExpense) Icons.Default.ShoppingCart else Icons.Default.AccountBalanceWallet, contentDescription = null, tint = if (tx.isExpense) dangerColor else successColor, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(tx.title, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 13.sp)
                            Text(tx.createdAt ?: "اليوم", fontSize = 10.sp, color = textTertiary)
                        }
                    }
                    Text("${if (tx.isExpense) "-" else "+"} ${com.example.data.CurrencyFormatter.format(txCurrencyContext, tx.amount)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (tx.isExpense) textPrimary else successColor)
                }
            }
        }
    }
}

@Composable
fun PremiumKidsSnippet(onKidsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(kidsPrimaryDark, kidsBackground)))
            .clickable { onKidsClick() }
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = kidsPrimaryLight, modifier = Modifier.size(16.dp))
                    Text("وضع الأطفال", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("شجع أطفالك على الادخار مع مهام ومكافآت ممتعة", fontSize = 10.sp, color = Color.White.copy(alpha=0.7f), lineHeight = 14.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = kidsPrimaryLight)
        }
    }
}
