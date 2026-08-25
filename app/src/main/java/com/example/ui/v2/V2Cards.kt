package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ZadGlassChip
import com.example.ui.theme.ZadStatCell
import com.example.ui.theme.ZadV2

@Composable
fun V2HeroCard(
    availableText: String,
    spentText: String,
    committedText: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZadV2.rHero)
            .pressableScale(onClick = onOpen)
            .border(1.dp, Color.White.copy(alpha = 0.16f), ZadV2.rHero),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            // New animated mesh background instead of static brush
            AnimatedMeshGradient(
                modifier = Modifier.matchParentSize(),
                colors = listOf(ZadV2.green700, ZadV2.green600, ZadV2.green800)
            )
            
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.v2_available),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    availableText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                    style = TextStyle(brush = ZadV2.heroAmountBrush),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZadGlassChip(
                        label = stringResource(R.string.v2_spent),
                        value = spentText,
                        dotColor = ZadV2.amberDot,
                    )
                    ZadGlassChip(
                        label = stringResource(R.string.v2_committed),
                        value = committedText,
                        dotColor = ZadV2.coralDot,
                    )
                }
            }
        }
    }
}

@Composable
fun V2StatRow(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.forEach { (caption, value) ->
            ZadStatCell(
                caption = caption,
                value = value,
                valueSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun V2ShortcutTile(
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
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 19.sp) }
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZadV2.gray500,
            maxLines = 1,
        )
    }
}

@Composable
fun V2AiSummaryCard(
    title: String,
    body: String,
    chips: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZadV2.aiPlate)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZadV2.mintGlow)
        Text(body, fontSize = 14.sp, color = Color.White, lineHeight = 21.sp)
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(3).forEach { chip ->
                    Text(
                        chip,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
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

@Composable
fun V2TxRow(title: String, date: String, amount: String, negative: Boolean) {
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
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.ink)
            Text(date, fontSize = 11.5.sp, color = ZadV2.gray400)
        }
        Text(
            amount,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (negative) ZadV2.danger else ZadV2.green800,
        )
    }
}
