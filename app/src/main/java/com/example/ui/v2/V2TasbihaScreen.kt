package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZadV2

@Composable
fun V2TasbihaScreen(modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    val maxCount = 33
    val progress = (count.toFloat() / maxCount).coerceIn(0f, 1f)
    
    val currentEmoji = when {
        progress >= 1f -> "🌳"
        progress > 0.6f -> "🌿🌿"
        progress > 0.3f -> "🌿"
        else -> "🌱"
    }

    Box(modifier = modifier.fillMaxSize().background(ZadV2.canvasWarm)) {
        ConfettiOverlay(
            modifier = Modifier.fillMaxSize(),
            isTriggered = count >= maxCount
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(ZadV2.mint50),
                contentAlignment = Alignment.Center
            ) {
                Text(currentEmoji, fontSize = 60.sp, modifier = Modifier.floatingIdle(2000))
            }
            
            Text(
                "Tasbiha Garden",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ZadV2.ink,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
            )
            
            Text(
                "Grow your spiritual garden.\nTap to count.",
                fontSize = 15.sp,
                color = ZadV2.slate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(ZadV2.tileTasbihaFg, ZadV2.tileTasbihaFg.copy(alpha = 0.8f))
                        )
                    )
                    .pressableScale {
                        if (count < maxCount) count++ else count = 0
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (count >= maxCount) "Reset" else "$count",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

