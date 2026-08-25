package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadInsight
import com.example.ui.theme.ZadV2
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

/**
 * V2 Zad Mind — the "Zad SmartBot Agent" panel from the Gemini render:
 * premium glassmorphic gradient ring around the emerald orb, then the real
 * insights feed (kindDot info/warn/danger from the prototype INSIGHTS.js).
 */

@Composable
fun V2AssistantScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zadInsights by viewModel.zadInsights.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Glassmorphic orb hero
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF0F9B76), Color(0xFF064E3B))
                            )
                        )
                        .border(6.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // the orb's two eyes (Gemini render)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) {
                            Box(
                                Modifier
                                    .size(width = 14.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.v2_mind_title),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink,
                )
                Text(
                    stringResource(R.string.v2_mind_subtitle),
                    fontSize = 13.sp, color = ZadV2.gray500,
                )
            }
        }
        // Tap-to-talk pill
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ZadV2.green800)
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.v2_talk_to_zad),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        // Real insights feed
        item {
            Text(stringResource(R.string.v2_insights), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
        }
        if (zadInsights.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.v2_no_insights), fontSize = 13.sp, color = ZadV2.gray400)
                }
            }
        } else {
            zadInsights.take(8).forEach { insight ->
                item { V2InsightRow(insight) }
            }
        }
    }
}

/** INSIGHTS.js row: kind dot + title + subtitle + kind tag pill. */
@Composable
private fun V2InsightRow(insight: ZadInsight) {
    val (dot, tagBg, tagColor) = when (insight.surface) {
        "bell" -> Triple(ZadV2.warn, Color(0x1AB45309), ZadV2.warn)
        else -> Triple(ZadV2.green800, Color(0x1A064E3B), ZadV2.green800)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCard)
            .background(Color.White.copy(alpha = 0.85f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
                .padding(top = 5.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV2.ink)
            if (insight.body.isNotBlank()) {
                Text(insight.body, fontSize = 13.sp, color = ZadV2.gray500, lineHeight = 18.sp)
            }
        }
    }
}
