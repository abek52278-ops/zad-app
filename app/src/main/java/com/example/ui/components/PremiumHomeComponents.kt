package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
@Composable
fun ZadBudgetGauge(
    budget: Double,
    spent: Double,
    remaining: Double,
    daysLeft: Int,
    onDepositClick: () -> Unit
) {
    val progressRemaining = if (budget > 0) (remaining / budget).toFloat().coerceIn(0f, 1f) else 0f
    val spentPct = if (budget > 0) (spent / budget * 100).toInt() else 0
    val currencyContext = LocalContext.current

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progressRemaining else 0f,
        animationSpec = ZadSprings.Screen,
        label = "gaugeProgress"
    )

    val statusColor = when {
        spentPct >= 100 -> dangerColor
        spentPct >= 85 -> warningColor
        else -> successColor
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 16.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = outlineVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = statusColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.remaining),
                    style = Typography.labelMedium,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    com.example.data.CurrencyFormatter.formatNumber(currencyContext, remaining),
                    style = Typography.displayLarge.copy(fontSize = 32.sp),
                    color = onSurface
                )
                Text(
                    com.example.data.CurrencyFormatter.symbol(currencyContext),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeStatChip(
                icon = Icons.Default.TrendingDown,
                label = stringResource(R.string.spent_label),
                value = com.example.data.CurrencyFormatter.format(currencyContext, spent),
                tint = dangerColor
            )
            GaugeStatChip(
                icon = Icons.Default.CalendarMonth,
                label = stringResource(R.string.days_label),
                value = "$daysLeft",
                tint = primary
            )
            GaugeStatChip(
                icon = Icons.Default.AccountBalanceWallet,
                label = stringResource(R.string.budget_label),
                value = com.example.data.CurrencyFormatter.format(currencyContext, budget),
                tint = secondaryDark
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDepositClick,
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(50),
            modifier = Modifier.pressableScale()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.deposit), style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun GaugeStatChip(icon: ImageVector, label: String, value: String, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .zadGlassBlur()
            .background(surface.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).floatingIdle(amplitude = 3f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
        Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
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
