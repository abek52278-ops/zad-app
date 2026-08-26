package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZadV3

@Composable
fun V3FamilyScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf("members") }

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color(0xFFE9ECEF)).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("members" to "Members", "tasks" to "Tasks", "chat" to "Chat").forEach { (key, label) ->
                val isSelected = selectedTab == key
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .pressableScale { selectedTab = key }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, color = if (isSelected) ZadV3.ink else ZadV3.gray500)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(ZadV3.rCardLg).background(Color.White).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE8F1FC)), contentAlignment = Alignment.Center) { Text("👨‍👩‍👧", fontSize = 20.sp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Zad Family", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                        Text("3 Members Active", fontSize = 13.sp, color = ZadV3.gray500)
                    }
                }
            }
        }
    }
}

@Composable
fun V3DealsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛍️", fontSize = 48.sp)
                    Text("No local deals near you right now", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                    Text("Check back later for discounts", fontSize = 13.sp, color = ZadV3.gray500)
                }
            }
        }
    }
}

@Composable
fun V3NotificationsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔔", fontSize = 48.sp)
                    Text("You're all caught up!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                    Text("No new notifications", fontSize = 13.sp, color = ZadV3.gray500)
                }
            }
        }
    }
}

@Composable
fun V3TasbihaScreen(modifier: Modifier = Modifier) {
    var count by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val maxCount = 33
    val progress = (count.toFloat() / maxCount).coerceIn(0f, 1f)

    val currentEmoji = when {
        progress >= 1f -> "🌳"
        progress > 0.6f -> "🌿🌿"
        progress > 0.3f -> "🌿"
        else -> "🌱"
    }

    Box(modifier = modifier.fillMaxSize().background(ZadV3.canvasWarm)) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize(), isTriggered = count >= maxCount)

        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(ZadV3.mint50), contentAlignment = Alignment.Center) {
                Text(currentEmoji, fontSize = 60.sp, modifier = Modifier.floatingIdle(2000))
            }

            Text("Tasbiha Garden", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink, modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
            Text("Grow your spiritual garden.\nTap to count.", fontSize = 15.sp, color = ZadV3.slate, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(bottom = 64.dp))

            Box(
                modifier = Modifier.size(140.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ZadV3.tileTasbihaFg, ZadV3.tileTasbihaFg.copy(alpha = 0.8f))))
                    .pressableScale { if (count < maxCount) count++ else count = 0 },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (count >= maxCount) "Reset" else "$count", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}