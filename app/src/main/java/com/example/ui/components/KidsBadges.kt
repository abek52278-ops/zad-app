package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.kidsPrimary
import com.example.ui.theme.kidsPrimaryLight
import com.example.ui.theme.onSurfaceVariant

data class KidBadge(val emoji: String, val label: String, val achieved: Boolean, val progress: Float = 1f)

private val kidBadgeColors = listOf(kidsPrimary, kidsPrimaryLight, Color(0xFFFDE68A), Color(0xFFBFDBFE))

/** 3 عتبات بسيطة من بيانات موجودة فعلاً — مفيش حقل DB جديد. */
fun buildKidBadges(
    completedChores: Int,
    tasbihaStreakDays: Int,
    savingsProgress: Float,
): List<KidBadge> = listOf(
    KidBadge("🏅", "3 مهام", achieved = completedChores >= 3, progress = (completedChores / 3f).coerceIn(0f, 1f)),
    KidBadge("🔥", "أسبوع كامل", achieved = tasbihaStreakDays >= 7, progress = (tasbihaStreakDays / 7f).coerceIn(0f, 1f)),
    KidBadge("💰", "نص الهدف", achieved = savingsProgress >= 0.5f, progress = (savingsProgress / 0.5f).coerceIn(0f, 1f)),
)

@Composable
fun KidBadgeRow(badges: List<KidBadge>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier) {
        items(badges.size) { index ->
            val badge = badges[index]
            val bg = kidBadgeColors[index % kidBadgeColors.size]
            AppearOnEntry(delayMs = index * 80) {
                Column(
                    modifier = Modifier.width(72.dp).padding(end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (badge.achieved) bg else bg.copy(alpha = 0.15f))
                            .let { if (badge.achieved) it.pulseGlow(maxScale = 1.08f) else it },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(badge.emoji, fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        badge.label,
                        fontSize = 10.sp,
                        fontWeight = if (badge.achieved) FontWeight.Bold else FontWeight.Normal,
                        color = if (badge.achieved) bg else onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
