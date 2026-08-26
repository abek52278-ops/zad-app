package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ZadV3
import com.example.ui.theme.zadV2Card

/**
 * V3 Cards — Shared composables for the new design system
 */

/**
 * Apple Wallet-style hero card with animated mesh gradient
 */
@Composable
fun V3HeroCard(
    availableText: String,
    spentText: String,
    committedText: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZadV3.rHero)
            .pressableScale(onClick = onOpen)
            .let {
                // Border is applied via the outer Box below
                it
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            AnimatedMeshGradient(
                modifier = Modifier.matchParentSize(),
                colors = listOf(Color(0xFF0A382C), ZadV3.green800, ZadV3.green700),
            )
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.v2_available),
                    fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    availableText,
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp,
                    style = androidx.compose.ui.text.TextStyle(brush = ZadV3.heroAmountBrush),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.example.ui.theme.ZadGlassChip(
                        label = stringResource(R.string.v2_spent),
                        value = spentText,
                        dotColor = ZadV3.amberDot,
                    )
                    com.example.ui.theme.ZadGlassChip(
                        label = stringResource(R.string.v2_committed),
                        value = committedText,
                        dotColor = ZadV3.coralDot,
                    )
                }
            }
        }
    }
}

/** Widget cell (daily safe, days left) */
@Composable
fun V3WidgetCell(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .zadV2Card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(caption, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
    }
}

/** Shortcut tile (category grid) */
@Composable
fun V3ShortcutTile(
    emoji: String,
    label: String,
    bg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .pressableScale(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500, maxLines = 1)
    }
}

/** AI summary card (dark green) */
@Composable
fun V3AiSummaryCard(
    title: String,
    body: String,
    chips: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZadV3.aiCardBg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.mintGlow)
        Text(body, fontSize = 14.sp, color = Color.White, lineHeight = 21.sp)
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(3).forEach { chip ->
                    Text(
                        chip, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { /* action */ },
                    )
                }
            }
        }
    }
}

/** Transaction row */
@Composable
fun V3TxRow(title: String, date: String, amount: String, negative: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
            Text(date, fontSize = 11.5.sp, color = ZadV3.gray400)
        }
        Text(amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (negative) ZadV3.danger else ZadV3.green800)
    }
}

/** Category card for the Home grid (Apple Wallet Pass style) */
@Composable
fun V3CategoryCard(
    emoji: String,
    name: String,
    itemCount: String,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .pressableScale(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(emoji, fontSize = 28.sp)
            Column {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                Text(itemCount, fontSize = 11.sp, color = ZadV3.gray500)
            }
        }
    }
}

/** Offer card (Exclusive deals) */
@Composable
fun V3OfferCard(
    image: String,
    title: String,
    subtitle: String,
    price: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .pressableScale(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)).background(ZadV3.tileShopBg),
            contentAlignment = Alignment.Center,
        ) { Text(image, fontSize = 32.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
            Text(subtitle, fontSize = 12.sp, color = ZadV3.gray500, maxLines = 2)
            Text(price, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.danger)
        }
    }
}