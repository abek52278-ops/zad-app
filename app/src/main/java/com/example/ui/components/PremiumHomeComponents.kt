package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
                    .background(Color(0xFF22C55E))
                    .padding(2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(background))
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF22C55E)))
                }
            }
            Column {
                Text("مرحباً بعودتك 👋", fontSize = 11.sp, color = textTertiary)
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
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = textSecondary, modifier = Modifier.size(20.dp))
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

@Composable
fun PremiumHeroCard(
    budget: Double,
    spent: Double,
    remaining: Double,
    daysLeft: Int,
    onDepositClick: () -> Unit
) {
    val progress = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val progressPercent = (progress * 100).toInt()
    val currencyContext = LocalContext.current

    // Animations
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progress else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val (statusEmoji, statusLabel, statusColor) = when {
        progress >= 0.85f -> Triple("🔴", stringResource(R.string.budget_status_watch), dangerColor)
        progress >= 0.6f -> Triple("🟡", stringResource(R.string.budget_status_caution), warningColor)
        else -> Triple("🟢", stringResource(R.string.budget_status_excellent), successColor)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryDark, primaryContainer, primaryDark)
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("💰 " + stringResource(R.string.monthly_budget_hero_label), style = Typography.labelSmall, color = textSecondary)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            com.example.data.CurrencyFormatter.formatNumber(currencyContext, budget),
                            style = Typography.displayLarge.copy(fontSize = 34.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            com.example.data.CurrencyFormatter.symbol(currencyContext),
                            style = Typography.labelMedium,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                Surface(
                    color = surfaceVariant,
                    shape = RoundedCornerShape(50),
                    onClick = onDepositClick
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deposit), tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.deposit), style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStatItem(stringResource(R.string.spent_label), com.example.data.CurrencyFormatter.format(currencyContext, spent), Color.White, Modifier.weight(1f))
                HeroStatItem(stringResource(R.string.remaining), com.example.data.CurrencyFormatter.format(currencyContext, remaining), primaryLight, Modifier.weight(1f))
                HeroStatItem(stringResource(R.string.days_label), "$daysLeft", Color.White, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.budget_percent_used, progressPercent), style = Typography.labelSmall, color = textSecondary)
                Text("$statusEmoji $statusLabel", style = Typography.labelSmall, color = textSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = statusColor,
                trackColor = surfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStatItem(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x33000000))
            .padding(12.dp)
    ) {
        Text(label, style = Typography.labelSmall, color = textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = Typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = valueColor)
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
        QuickStatCard(stringResource(R.string.nav_inventory), "$inventoryCount", stringResource(R.string.quick_stat_inventory_unit), Icons.Default.Inventory2, primaryLight, primaryLight.copy(alpha=0.15f), onInventoryClick, Modifier.weight(1f))
        QuickStatCard(stringResource(R.string.quick_stat_subscriptions_title), "$activeSubsCount", stringResource(R.string.quick_stat_subscriptions_unit), Icons.Default.Subscriptions, warningColor, warningColor.copy(alpha=0.15f), onSubsClick, Modifier.weight(1f))
        QuickStatCard(stringResource(R.string.nav_family), "$familyCount", stringResource(R.string.quick_stat_family_unit), Icons.Default.FamilyRestroom, error, error.copy(alpha=0.15f), onFamilyClick, Modifier.weight(1f))
    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f)

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(15.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = Typography.labelSmall, color = textTertiary)
        Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Black, color = textPrimary)
        Text(subtitle, style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun PremiumInsightBanner(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Color(0x22F59E0B), Color(0x1A10B981))))
            .clickable { onClick() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0x44F59E0B), Color(0x3310B981)))),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", fontSize = 21.sp)
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
            Text("وجبات من ثلاجتك 🍳", fontSize = 15.sp, fontWeight = FontWeight.Black, color = textPrimary)
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
                            Text(if (meal.contains("كبسة")) "🍛" else if (meal.contains("سلطة")) "🥗" else "🍝", fontSize = 16.sp)
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
            Text("أحدث العمليات 💳", fontSize = 15.sp, fontWeight = FontWeight.Black, color = textPrimary)
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
                    Text("🎮", fontSize = 16.sp)
                    Text("وضع الأطفال", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("شجع أطفالك على الادخار مع مهام ومكافآت ممتعة", fontSize = 10.sp, color = Color.White.copy(alpha=0.7f), lineHeight = 14.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = kidsPrimaryLight)
        }
    }
}
