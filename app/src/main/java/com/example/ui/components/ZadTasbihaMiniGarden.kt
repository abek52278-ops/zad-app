package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.ZadCutePetSoundFx
import kotlinx.coroutines.delay
import com.example.ui.theme.surface

/**
 * Tasbiha Garden component translating garden and tree mechanics into native Jetpack Compose.
 */
@Composable
fun ZadTasbihaMiniGarden(
    tasbihaCount: Int,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val progress = (tasbihaCount % 100) / 100f
    val treeEmoji = when {
        tasbihaCount >= 100 -> "🌳"
        tasbihaCount >= 75 -> "🌿"
        tasbihaCount >= 50 -> "🦋"
        tasbihaCount >= 25 -> "🌿"
        else -> "🌱"
    }

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "tasbih_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.04f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "بستان التسبيح 📿",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F9B76)
            )
        }

        // Tree zone with interactive spring tap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF0FDF4))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                    onTap()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = treeEmoji,
                fontSize = 48.sp,
                modifier = Modifier.scale(scale)
            )
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(9999.dp)),
            color = Color(0xFF0F9B76),
            trackColor = Color(0xFFE2E8F0)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$tasbihaCount / 100 تسبيحة",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF0F9B76))
                        )
                    )
                    .clickable {
                        isPressed = true
                        ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                        onTap()
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "سبّح",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
}
