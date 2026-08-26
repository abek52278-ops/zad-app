package com.example.ui.v2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.example.ui.theme.ZadV3
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

/**
 * V3 Assistant Screen — Glassmorphic Agent Orb + tap-to-talk + insights feed
 */
@Composable
fun V3AssistantScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onOpenVoice: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zadInsights by viewModel.zadInsights.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Glassmorphic Agent Orb hero
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // Emerald glass orb with eyes and rotating ring
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .floatingIdle(2500)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(ZadV3.green600, ZadV3.green800)))
                        .border(6.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Rotating outer ring
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .ringSpin(12000)
                            .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    )
                    // Eyes
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) {
                            Box(
                                Modifier.size(width = 14.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }

                Text(stringResource(R.string.v2_mind_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                Text(stringResource(R.string.v2_mind_subtitle), fontSize = 13.sp, color = ZadV3.gray500)
            }
        }

        // Tap-to-talk pill
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(999.dp))
                    .background(ZadV3.green800).pressableScale(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.v2_talk_to_zad), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Knowledge map preview
        item {
            Box(
                modifier = Modifier.fillMaxWidth().clip(ZadV3.rCard).background(ZadV3.mint50)
                    .clickable(onClick = onNavigateToKnowledge).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("🗺️", fontSize = 20.sp) }
                    Column {
                        Text(stringResource(R.string.nav_assistant) + " Map", fontWeight = FontWeight.Bold, color = ZadV3.green800)
                        Text("View semantic relations and facts", fontSize = 12.sp, color = ZadV3.green700)
                    }
                }
            }
        }

        // Insights feed
        item { Text(stringResource(R.string.v2_insights), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink) }
        if (zadInsights.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.v2_no_insights), fontSize = 13.sp, color = ZadV3.gray400) } }
        } else {
            zadInsights.take(8).forEach { insight -> item { V3InsightRow(insight) } }
        }
    }
}

@Composable
private fun V3InsightRow(insight: ZadInsight) {
    val (dot, tagBg, tagColor) = when (insight.surface) {
        "bell" -> Triple(ZadV3.warn, Color(0x1AB45309), ZadV3.warn)
        else -> Triple(ZadV3.green800, Color(0x1A064E3B), ZadV3.green800)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(ZadV3.rCard).background(Color.White.copy(alpha = 0.85f)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot).padding(top = 5.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
            if (insight.body.isNotBlank()) Text(insight.body, fontSize = 13.sp, color = ZadV3.gray500, lineHeight = 18.sp)
        }
    }
}