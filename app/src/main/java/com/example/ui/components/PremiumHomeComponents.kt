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

/**
 * Signature hero: a circular budget gauge instead of another rounded
 * rectangle — the ring depletes as the month's spending eats into the
 * budget, remaining balance sits big and centered inside it. Replaces
 * the old rectangular PremiumHeroCard.
 */
/**
 * Signature hero, v4 — now the mockup's ("ZAD App.dc.html") hero card and nothing else:
 * a 28dp mesh-gradient card holding the "متاح" label, the figure, and the spent/committed
 * glow pills. That is the entire card in the design.
 *
 * v3 carried three things the mockup does not have, all removed here: an EMV-chip
 * silhouette + "زاد" wordmark row, a decorative translucent blob, and a nested glass panel
 * repeating days-left and spent with a deposit button. The days/spent repetition was the
 * real problem — `ZadDaysAndSafeSpendRow` renders both directly underneath, so the hero was
 * restating its own next sibling.
 *
 * The deposit shortcut moved out rather than being kept as an off-design extra: adding a
 * transaction still lives on Budget (its own FAB) and Transactions, and Home's "عرض الكل"
 * goes straight to Budget. One tap further, no capability lost.
 *
 * What is NOT dropped, because it is product logic rather than decoration: the "≈" prefix
 * and its explanation dialog when the figure isn't confident (Task 27.1a), the long-press
 * that opens "آخر التغييرات" (Task 27.2), and the "متبقي X · محجوز Y" breakdown line, which
 * only renders when committed > 0 (Task 26).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ZadCardHero(
    spent: Double,
    remaining: Double,
    available: com.example.data.Figure = com.example.data.Figure(remaining, confident = true),
    committed: Double = 0.0,
    nextObligationText: String? = null,
    onAvailableLongPress: () -> Unit = {}
) {
    val currencyContext = LocalContext.current
    val cardShape = RoundedCornerShape(28.dp)

    com.example.ui.components.HeroGradientCard(
        colors = com.example.ui.components.ZadHeroGradient,
        shape = cardShape,
        contentPadding = 0.dp
    ) {
        // mockup: padding 26px top / 24px sides / 22px bottom, 16px gaps.
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 22.dp)) {
            Text(
                stringResource(R.string.available_label),
                style = Typography.labelSmall.copy(fontSize = 11.5.sp, letterSpacing = 0.4.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.72f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            var showAvailableReason by remember { mutableStateOf(false) }
            // mockup paints the figure with a white → #D9F2E6 vertical gradient; a negative
            // figure drops the gradient for a flat danger color so it still reads as alarming.
            val figureStyle = Typography.displayLarge.copy(
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.2).sp,
                brush = if (available.value < 0) null else Brush.verticalGradient(
                    listOf(Color.White, Color(0xFFD9F2E6))
                )
            )
            Text(
                (if (!available.confident) "≈ " else "") +
                    com.example.data.CurrencyFormatter.formatNumber(currencyContext, available.value),
                style = figureStyle,
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroGlowPill(
                    dotColor = Color(0xFFF4A93B),
                    label = stringResource(R.string.spent_label),
                    value = com.example.data.CurrencyFormatter.format(currencyContext, spent)
                )
                if (committed > 0) {
                    HeroGlowPill(
                        dotColor = Color(0xFFFF8066),
                        label = stringResource(R.string.committed_label),
                        value = com.example.data.CurrencyFormatter.format(currencyContext, committed)
                    )
                }
            }
        }
    }
}

/**
 * شريحة زجاجية بنقطة متوهجة — عنصر متكرر في هيرو التصميم الجديد ("مصروف: ٢٤٠"،
 * "محجوز: ٨٠٠"). التوهج نقطة صغيرة ورا نفسها بشفافية أعلى، مش ظل حقيقي، عشان
 * يشتغل على كل إصدارات أندرويد من غير ما يعتمد على blur (minSdk 24).
 */
@Composable
private fun HeroGlowPill(dotColor: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            "$label: $value",
            style = Typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
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

/**
 * Shortcut grid — the mockup's `grid-template-columns:repeat(6,1fr); gap:8px`: six
 * fixed columns, one screen each, all visible at once.
 *
 * Was a nine-item `LazyRow`: on a 402dp-wide phone that left three-and-a-bit items
 * on screen and the rest behind a scroll nobody discovers, which is what made the
 * row read as an arbitrary bar of icons rather than as a launcher. A fixed
 * six-column grid is also why the badge shrank 52dp → 46dp and the label to
 * `labelSmall` at 10sp: that's what fits six columns inside 20dp page padding.
 */
@Composable
fun ZadPageShortcutsGrid(items: List<ZadShortcutItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            AppearOnEntry(
                delayMs = (index * 50).coerceAtMost(400),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val badgeShape = RoundedCornerShape(15.dp)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .zadCardShadow(badgeShape, elevation = 6.dp)
                            .clip(badgeShape)
                            .background(item.color.copy(alpha = 0.12f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = item.onClick
                            )
                            .pressableScale(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = item.color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        item.label,
                        style = Typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = textSecondary,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * The mockup's plain stat tile: white, 16dp radius, 14dp padding, a small grey
 * label over a bold dark value. No icon and no colored fill — the mockup keeps
 * color for the hero, the category badges and the AI card, and deliberately
 * leaves these neutral so a 2×2 grid of them doesn't fight the hero above it.
 */
@Composable
fun ZadStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = Typography.labelSmall, color = textTertiary, maxLines = 1)
        Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1)
    }
}

/**
 * The mockup's pair of 18dp cards directly under the hero: days left in the salary
 * cycle, and the daily safe-spend rate derived from it.
 *
 * Safe spend is `available / daysLeft` — the same arithmetic the mockup labels
 * "معدل الصرف اليومي الآمن". Guarded at `daysLeft <= 0` (cycle boundary day) and at
 * a negative available balance, both of which would otherwise print a nonsense
 * number on the busiest card on the screen.
 */
@Composable
fun ZadDaysAndSafeSpendRow(daysLeft: Int, available: Double) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    val safeSpend = if (daysLeft > 0 && available > 0) available / daysLeft else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .zadCardShadow(shape)
                .clip(shape)
                .background(surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.days_left_label), style = Typography.labelSmall, color = textSecondary)
            Text(
                stringResource(R.string.days_left_value, daysLeft),
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .zadCardShadow(shape)
                .clip(shape)
                .background(surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.safe_daily_spend_label), style = Typography.labelSmall, color = textSecondary, maxLines = 2)
            Text(
                safeSpend?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: "—",
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ShortagesSummaryCard(shortageCount: Int, onViewShortagesClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

/**
 * Relative transaction date, the mockup's `dateAr` ("اليوم" / "قبل يومين").
 *
 * The rows used to print `tx.createdAt` raw, which is an ISO-8601 instant — every
 * transaction on Home read "2026-08-01T09:12:33Z" where the design has one word.
 */
@Composable
private fun relativeTxDate(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return stringResource(R.string.today_label)
    val days = remember(createdAt) {
        runCatching {
            val date = java.time.Instant.parse(createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now()).toInt()
        }.getOrNull()
    } ?: return stringResource(R.string.today_label)
    return when {
        days <= 0 -> stringResource(R.string.today_label)
        days == 1 -> stringResource(R.string.yesterday_label)
        else -> stringResource(R.string.days_ago_label, days)
    }
}

/**
 * Recent transactions — the mockup's `HOME_TX` block: a plain title row with a
 * "عرض الكل" link, then three 14dp white rows of name + relative date on the
 * leading edge and a signed amount on the trailing edge.
 *
 * The 36dp category icon tile each row used to carry is gone: the mockup has no
 * icon here, and the tile was decorative anyway — it only ever encoded
 * expense-vs-income, which the signed amount already says in the same row.
 */
@Composable
fun PremiumTransactionsRow(transactions: List<com.example.data.ZadTransaction>, onSeeAllClick: () -> Unit) {
    val txCurrencyContext = LocalContext.current
    // No inner horizontal padding: Home's content Column already applies the mockup's
    // 20dp page padding.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_transactions),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                stringResource(R.string.view_all),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                modifier = Modifier.clickable { onSeeAllClick() }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (transactions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = textTertiary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.no_transactions_yet), fontSize = 12.sp, color = textTertiary)
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val recent = transactions.sortedByDescending { it.createdAt ?: "" }.take(3)
            for (tx in recent) {
                val rowShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zadCardShadow(rowShape)
                        .clip(rowShape)
                        .background(surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            tx.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(relativeTxDate(tx.createdAt), fontSize = 11.5.sp, color = textTertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        (if (tx.isExpense) "-" else "+") + com.example.data.CurrencyFormatter.format(txCurrencyContext, tx.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isExpense) dangerColor else primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Chef Zad — the mockup's single 18dp white row card: a 56dp amber tile with the
 * chef-hat glyph, the section title, and one line of today's suggestion.
 *
 * Replaces a horizontal carousel of 190dp meal cards whose images were three
 * hardcoded Unsplash food photos with no relationship to the user's inventory, and
 * which fell back to three hardcoded English placeholder meals whenever the AI
 * hadn't answered. The mockup has one card here, not a rail.
 */
@Composable
fun ZadChefCard(suggestion: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .clickable { onClick() }
            .pressableScale(pressedScale = 0.98f, withHaptic = false)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFDF3E1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                tint = secondaryDark,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.zad_chef_suggestions),
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                suggestion?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chef_card_empty_hint),
                style = Typography.bodyMedium,
                color = textSecondary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
