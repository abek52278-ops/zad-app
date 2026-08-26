package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
 * الكارت الأخضر. رقم واحد: **الرصيد اللي معاك دلوقتي**.
 *
 * ده إعادة هيكلة كاملة بتعليمة مباشرة من العميل (2026-08-16)، مش تعديل على اللي قبله.
 * النص بالحرف: "عاوز كرت الميزانية فقط الرقم اللي العميل يدخله يبتدي يحسب على أساسه كل
 * شيء، مليش دعوة بمصاريفه القديمة".
 *
 * اللي اتشال، وليه:
 *
 * - **شريط "٪ من الميزانية"**. كان بيحسب `المصروف ÷ monthly_limit`. العمود ده بقى
 *   **الرصيد الابتدائي** مش سقف صرف (migration 20260816010000)، فالنسبة كانت بتقيس رقم
 *   على حاجة مش سقفه: عميل رصيده الابتدائي ٥٠٠٠ ودخله ١٠٠٠٠ كان بيشوف الشريط أحمر
 *   ومتملي وهو معاه فلوس. النسبة ماكانتش غلط في حسابها، كانت بتجاوب على سؤال مالوش وجود.
 * - **سطر "الميزانية الشهرية: X"** فوق الرقم — نفس العلة، بيسمّي الرصيد الابتدائي "ميزانية".
 * - **شريط "المحجوز"** وشرايح "مصروف/محجوز**. الرقم الكبير كان `الرصيد − المحجوز`
 *   والشرايح بتفكّكه، فالكارت بقى تلات أرقام بيشرحوا بعض. الالتزامات والاشتراكات
 *   لسه موجودة في شاشاتها وفي "المسموح يومياً" تحت الكارت — اللي اتشال هو خصمها من
 *   الرقم اللي المفروض يجاوب سؤال واحد: "معايا كام دلوقتي؟"
 *
 * اللي فضل: الرقم، وعلامة `≈` لما يكون فيه معاملة لسه ما اتأكدتش (Task 27.1a)، والضغطة
 * الطويلة اللي بتفتح "آخر التغييرات" (Task 27.2) — دول بيفسّروا الرقم نفسه، مش بيضيفوا
 * رقم تاني جنبه.
 *
 * الرقم بيتحسب من نقطة تثبيت الرصيد (`balance_anchored_at`، migration 20260816120000)،
 * فمصاريف قبل ما العميل يقول "معايا كذا" مش بتنقّصه — دي النص التاني من نفس التعليمة.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ZadCardHero(
    balance: com.example.data.Figure,
    spentThisMonth: Double? = null,
    committedAmount: Double? = null,
    onBalanceLongPress: () -> Unit = {},
    onOpenDetail: () -> Unit = {}
) {
    val currencyContext = LocalContext.current
    val animatedBalance = remember { Animatable(balance.value.toFloat()) }
    LaunchedEffect(balance.value) {
        animatedBalance.animateTo(
            balance.value.toFloat(),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        )
    }

    com.example.ui.components.HeroGradientCard(
        colors = com.example.ui.components.ZadHeroGradient,
        shape = RoundedCornerShape(28.dp),
        contentPadding = 0.dp,
        animateMesh = true // zadMeshShift — الكارت الأخضر بيتنفس زي البروتوتايب
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onOpenDetail)
                .pressableScale(pressedScale = 0.985f)
        ) {
            // بقعة ضوء زجاجية أعلى يسار الكارت
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .zadGlassBlur(36.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
            )
            // wallet-stripes من البروتوتايب: repeating-linear-gradient 10px/20px ارتفاع 5px
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .align(Alignment.BottomCenter)
                    .drawBehind {
                        val stripe = 20.dp.toPx()
                        val half = stripe / 2f
                        var x = 0f
                        while (x < size.width) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.25f),
                                topLeft = Offset(x, 0f),
                                size = Size(half, size.height)
                            )
                            x += stripe
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.current_balance_label),
                        style = Typography.labelSmall.copy(fontSize = 11.5.sp, letterSpacing = 0.4.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.76f)
                    )
                    // زر التعديل من مرجع "new ui ux" (الرندر): أيقونة قلم زجاجية أعلى
                    // الكارت — بتفتح نفس تفاصيل الرصيد. الأيقونة كانت محفظة ديكورية؛
                    // القلم بيقول "الرقم ده بتاعك — عدّله من هنا".
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_balance_action),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenDetail() }
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var showBalanceReason by remember { mutableStateOf(false) }
                val figureStyle = Typography.displayLarge.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                    brush = if (balance.value < 0) null else Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFD9F2E6))
                    )
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.combinedClickable(
                        // البروتوتايب: UI.openWhy() على ضغطة الكارت نفسه. هنا: التفسير
                        // الوظيفي للرقم متاح بضغطة عادية (≈) أو لونج-بريس دايماً.
                        onClick = { if (!balance.confident || balance.reason != null) showBalanceReason = true else onOpenDetail() },
                        onLongClick = onBalanceLongPress
                    )
                ) {
                    // العداد المالي الحي — AnimatedContent بدل Text ثابت: كل تغيير في الرصيد
                    // بيعمل انتقال انزلاقي/تلاشي بين القيمة القديمة والجديدة فوق أنيميشن العدّاد.
                    AnimatedContent(
                        targetState = (if (!balance.confident) "≈ " else "") +
                            com.example.data.CurrencyFormatter.formatNumber(currencyContext, animatedBalance.value.toDouble()),
                        transitionSpec = {
                            (slideInVertically { it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
                        },
                        label = "heroBalanceText"
                    ) { balanceText ->
                        Text(
                            balanceText,
                            style = figureStyle,
                            maxLines = 1,
                            color = if (balance.value < 0) coralLight else Color.White,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        com.example.data.CurrencyFormatter.symbol(currencyContext),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = (if (balance.value < 0) coralLight else Color.White).copy(alpha = 0.75f),
                        modifier = Modifier.alignByBaseline()
                    )
                }
                if (showBalanceReason && balance.reason != null) {
                    AlertDialog(
                        onDismissRequest = { showBalanceReason = false },
                        confirmButton = {
                            TextButton(onClick = { showBalanceReason = false }) {
                                Text(stringResource(R.string.close_action))
                            }
                        },
                        title = { Text(stringResource(R.string.current_balance_label) + " ≈") },
                        text = { Text(balance.reason!!) }
                    )
                }

                // البروتوتايب: حبتان زجاجيتان داخل الكارت الأخضر — «مصروف» بنقطة كهرمانية
                // و«محجوز للالتزامات» بنقطة مرجانية. أرقام حية من المعاملات والالتزامات،
                // والحبة تختفي لو الرقم مش موجود (مفيش بيانات = مفيش ديكور).
                if (spentThisMonth != null || committedAmount != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        spentThisMonth?.let { spent ->
                            HeroGlassPill(
                                dotColor = Color(0xFFF4A93B),
                                label = stringResource(R.string.spent_label),
                                amount = com.example.data.CurrencyFormatter.format(currencyContext, spent)
                            )
                        }
                        committedAmount?.let { committed ->
                            HeroGlassPill(
                                dotColor = Color(0xFFFF8066),
                                label = stringResource(R.string.committed_label),
                                amount = com.example.data.CurrencyFormatter.format(currencyContext, committed)
                            )
                        }
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
    ) {
        // نفس بقعة الضوء الزجاجية بتاعة ZadCardHero — الحالة الفاضية دي بديل الكارت
        // ده، لازم يحس إنه نفس العائلة البصرية مش شاشة تانية مختلفة الطابع.
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .offset(x = (-24).dp, y = (-24).dp)
                .zadGlassBlur(32.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
        )
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
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
}

/**
 * حبة زجاجية داخل الكارت الأخضر — نفس شكل البروتوتايب: خلفية بيضاء 14% + blur،
 * حدود بيضاء 14%، نقطة ملونة متوهجة، ونص 12.5sp أبيض.
 */
@Composable
private fun HeroGlassPill(dotColor: Color, label: String, amount: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .zadGlassBlur(10.dp)
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = dotColor)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            "$label: $amount",
            style = Typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
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
 * Mockup fidelity (zad_premium_v5 `.tiles`): شبكة 3 أعمدة × صفين، gap 14px، بادج
 * 52×52 r17 بخلفية صلبة (مش alpha)، إيموجي-بديل أيقونة Material 22dp، تسمية
 * 10.5px gray500. Items order dictates row layout; callers pass 6 items.
 */
@Composable
fun ZadPageShortcutsGrid(items: List<ZadShortcutItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * 3 + colIndex
                    AppearOnEntry(
                        delayMs = (index * 50).coerceAtMost(400),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val badgeShape = RoundedCornerShape(17.dp)
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .zadCardShadow(badgeShape, elevation = 6.dp)
                                    .clip(badgeShape)
                                    .background(item.color.copy(alpha = 0.14f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = item.onClick
                                    )
                                    .pressableScale(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.label, tint = item.color, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                item.label,
                                style = Typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 12.5.sp),
                                color = textSecondary,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                // تعبئة الخانة الفاضية لو الصف الأخير ناقص — عشان الـweight يفضل مظبوط
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
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
        // قيمة حية — AnimatedContent بيسطّر تغيّر الرقم بحركة انزلاق بدل القفزة الجافة.
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
            },
            label = "statTileValue"
        ) { animatedValue ->
            Text(animatedValue, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1)
        }
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
    val shape = RoundedCornerShape(20.dp)
    val safeSpend = if (daysLeft > 0 && available > 0) available / daysLeft else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .zadCardShadow(shape, elevation = 3.dp)
                .clip(shape)
                .background(surface)
                .pressableScale(pressedScale = 0.98f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.days_left_label), style = Typography.labelSmall, color = textSecondary)
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            // العدّاد الحي — AnimatedContent بدل رقم جامد (نفس معالجة الهيرو والـtiles)
            AnimatedContent(
                targetState = daysLeft,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
                },
                label = "daysLeftCounter"
            ) { animatedDays ->
                Text(
                    stringResource(R.string.days_left_value, animatedDays),
                    style = Typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .zadCardShadow(shape, elevation = 3.dp)
                .clip(shape)
                .background(surface)
                .pressableScale(pressedScale = 0.98f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.safe_daily_spend_label), style = Typography.labelSmall, color = textSecondary, maxLines = 1)
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedSafeSpendValue(
                value = safeSpend?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: "—",
                color = if (safeSpend != null) primary else textSecondary
            )
        }
    }
}

/**
 * العدّاد الحي لقيمة الصرف الآمن — نفس transitionSpec الموحد في الشاشة.
 */
@Composable
private fun AnimatedSafeSpendValue(value: String, color: Color) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { it / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
        },
        label = "safeSpendCounter"
    ) { animatedValue ->
        Text(
            animatedValue,
            style = Typography.headlineSmall.copy(fontSize = 17.sp),
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1
        )
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
fun ZadChefCard(
    suggestion: String?,
    onClick: () -> Unit,
    /** نص الحالة الفاضية — مخزون لسه فاضي مش زي مخزون كله اتصفّر. */
    @androidx.annotation.StringRes emptyHintRes: Int = R.string.chef_card_empty_hint,
) {
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
                suggestion?.takeIf { it.isNotBlank() } ?: stringResource(emptyHintRes),
                style = Typography.bodyMedium,
                color = textSecondary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
