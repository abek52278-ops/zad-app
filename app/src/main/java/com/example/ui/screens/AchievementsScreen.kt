package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SupabaseRepo
import com.example.ui.components.ZadErrorState
import com.example.ui.components.ZadLoadingState
import com.example.ui.theme.primary

data class AchievementData(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val points: Int,
    val isUnlocked: Boolean,
    val unlockedAt: String? = null
)

data class UserStatsData(
    val level: Int,
    val totalPoints: Int,
    val totalContributions: Int,
    val currentStreak: Int,
    val achievementsUnlocked: Int,
    val nextAchievementName: String?,
    val nextAchievementProgress: String?
)

enum class AchievementCondition { CONTRIBUTION_COUNT, STREAK, TOTAL_SCORE }

data class AchievementCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val points: Int,
    val condition: AchievementCondition,
    val threshold: Int
)

// نفس الكتالوج بالظبط اللي في supabase/functions/zad-market-intelligence/gamification.ts
// (ACHIEVEMENTS) — ده كتالوج التعريفات الثابت بس (اسم/أيقونة/شرط الفتح)؛ حالة "مفتوح"
// الفعلية بتيجي من صفوف user_achievements الحقيقية (SupabaseRepo.getUserAchievements)،
// مش محسوبة هنا. ملحوظة: gamification.ts نفسه مش متستدعى من index.ts حاليًا، يعني
// مفيش صفوف بتتكتب في user_achievements فعليًا لحد دلوقتي — الشاشة صادقة وهتفضل
// فاضية لحد ما ده يتوصل من ناحية السيرفر (خارج نطاق الإصلاح ده).
val ACHIEVEMENT_CATALOG = listOf(
    AchievementCatalogEntry("first_step", "الخطوة الأولى", "أضف سعرك الأول", "🌟", 10, AchievementCondition.CONTRIBUTION_COUNT, 1),
    AchievementCatalogEntry("rising_star", "نجم صاعد", "أضف 10 أسعار", "⭐", 50, AchievementCondition.CONTRIBUTION_COUNT, 10),
    AchievementCatalogEntry("market_analyst", "محلل أسواق", "أضف 50 سعر", "📊", 200, AchievementCondition.CONTRIBUTION_COUNT, 50),
    AchievementCatalogEntry("expert_reporter", "خبير التقارير", "أضف 100 سعر", "🏆", 500, AchievementCondition.CONTRIBUTION_COUNT, 100),
    AchievementCatalogEntry("on_fire", "أسبوع متتالي", "ساهم 7 أيام متتالية", "🔥", 100, AchievementCondition.STREAK, 7),
    AchievementCatalogEntry("consistency", "الصبر والمثابرة", "مجموع 500 نقطة", "💪", 150, AchievementCondition.TOTAL_SCORE, 500)
)

// نفس منطق calculateStreak في gamification.ts حرفيًا (streak = 1 لو آخر مساهمة خلال
// يوم، غير كده صفر) — مش منطق تراكمي حقيقي، ده قصور موجود في الكود الأصلي مش من هنا.
fun calculateContributionStreak(lastContributionAtIso: String?): Int {
    if (lastContributionAtIso.isNullOrBlank()) return 0
    return try {
        val lastMillis = java.time.Instant.parse(lastContributionAtIso).toEpochMilli()
        val diffMillis = System.currentTimeMillis() - lastMillis
        val diffDays = kotlin.math.ceil(diffMillis / 86_400_000.0).toInt()
        if (diffDays > 1) 0 else 1
    } catch (e: Exception) {
        0
    }
}

// بيحول صفوف user_achievements الحقيقية + أرقام price_index الحقيقية لنفس شكل
// UserStatsData/AchievementData اللي الشاشة محتاجاه — بدل الـ parameters الفاضية.
fun buildAchievementsUiState(
    unlockedRows: List<com.example.data.ZadUserAchievement>,
    contributionCount: Int,
    currentStreak: Int
): Pair<UserStatsData, List<AchievementData>> {
    val unlockedIds = unlockedRows.map { it.achievementId }.toSet()
    val totalPoints = unlockedRows.sumOf { it.pointsEarned }

    val achievements = ACHIEVEMENT_CATALOG.map { def ->
        val row = unlockedRows.find { it.achievementId == def.id }
        AchievementData(
            id = def.id,
            name = def.name,
            description = def.description,
            icon = def.icon,
            points = def.points,
            isUnlocked = row != null,
            unlockedAt = row?.unlockedAt
        )
    }

    fun conditionMet(def: AchievementCatalogEntry): Boolean = when (def.condition) {
        AchievementCondition.CONTRIBUTION_COUNT -> contributionCount >= def.threshold
        AchievementCondition.STREAK -> currentStreak >= def.threshold
        AchievementCondition.TOTAL_SCORE -> totalPoints >= def.threshold
    }

    val next = ACHIEVEMENT_CATALOG
        .filter { it.id !in unlockedIds && !conditionMet(it) }
        .minByOrNull { it.threshold }

    val progressText = next?.let { def ->
        when (def.condition) {
            AchievementCondition.CONTRIBUTION_COUNT -> "$contributionCount/${def.threshold} مساهمة"
            AchievementCondition.STREAK -> "$currentStreak/${def.threshold} أيام متتالية"
            AchievementCondition.TOTAL_SCORE -> "$totalPoints/${def.threshold} نقطة"
        }
    }

    val stats = UserStatsData(
        level = (totalPoints / 100) + 1,
        totalPoints = totalPoints,
        totalContributions = contributionCount,
        currentStreak = currentStreak,
        achievementsUnlocked = unlockedRows.size,
        nextAchievementName = next?.name,
        nextAchievementProgress = progressText
    )
    return stats to achievements
}

/**
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط ZadMemoryScreen/AgentActionLogScreen:
 * قراءة Supabase مباشرة من غير أي نداء LLM هنا، فمفيش داعي لدورة حياة ViewModel كاملة.
 * كانت الشاشة قبل كده بتاخد stats/achievements كـ parameters فاضية بلا أي caller حقيقي.
 */
@Composable
fun AchievementsRoute(onBack: () -> Unit) {
    var stats by remember { mutableStateOf<UserStatsData?>(null) }
    var achievements by remember { mutableStateOf<List<AchievementData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        loadFailed = false
        try {
            val unlocked = SupabaseRepo.getUserAchievements()
            val contributionTimestamps = SupabaseRepo.getCrowdsourceContributionTimestamps()
            val currentStreak = calculateContributionStreak(contributionTimestamps.firstOrNull())
            val (loadedStats, loadedAchievements) = buildAchievementsUiState(
                unlockedRows = unlocked,
                contributionCount = contributionTimestamps.size,
                currentStreak = currentStreak
            )
            stats = loadedStats
            achievements = loadedAchievements
        } catch (e: Exception) {
            loadFailed = true
        }
        loading = false
    }

    when {
        loading -> ZadLoadingState(modifier = Modifier.fillMaxSize())
        loadFailed || stats == null -> ZadErrorState(
            message = stringResource(R.string.changes_save_failed),
            modifier = Modifier.fillMaxSize(),
            retryLabel = stringResource(R.string.retry_action),
            onRetry = { reloadKey++ }
        )
        else -> AchievementsScreen(stats = stats!!, achievements = achievements, onBack = onBack)
    }
}

@Composable
fun AchievementsScreen(
    stats: UserStatsData,
    achievements: List<AchievementData>,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.ZadLuxe.canvasBackground)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = com.example.ui.theme.ZadHubListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = com.example.ui.theme.ZadLuxe.emerald)
                }
                Text(
                    stringResource(R.string.achievements_screen_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }

        // Level Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.SolidColor(com.example.ui.theme.ZadLuxe.emerald)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = com.example.ui.theme.ZadLuxe.squircle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Text(
                            stringResource(R.string.achievements_level),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stats.level.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.achievements_on_track),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = ((stats.totalPoints % 100) / 100f).coerceIn(0f, 1f))
                                    .background(Color.White, shape = RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${stats.totalPoints} من ${stats.level * 100} نقطة",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    label = "المساهمات",
                    value = stats.totalContributions.toString(),
                    icon = "📊",
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    label = "التسلسل",
                    value = "${stats.currentStreak}🔥",
                    icon = "⚡",
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    label = "الإنجازات",
                    value = stats.achievementsUnlocked.toString(),
                    icon = "🏆",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Next Achievement Preview
        if (stats.nextAchievementName != null && stats.nextAchievementProgress != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .border(0.5.dp, com.example.ui.theme.ZadLuxe.ochre.copy(alpha = 0.3f), com.example.ui.theme.ZadLuxe.squircle),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ZadLuxe.ochre.copy(alpha = 0.1f)),
                    shape = com.example.ui.theme.ZadLuxe.squircle
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            "Next",
                            tint = com.example.ui.theme.ZadLuxe.ochre,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.achievements_next),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = com.example.ui.theme.ZadLuxe.ochre
                            )
                            Text(
                                stats.nextAchievementName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                stats.nextAchievementProgress,
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }

        // Achievements Grid
        item {
            Text(
                stringResource(R.string.achievements_list_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(achievements) { achievement ->
                    AchievementCard(achievement = achievement)
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, com.example.ui.theme.ZadLuxe.squircle),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ZadLuxe.cardWhite),
        shape = com.example.ui.theme.ZadLuxe.squircle
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.ZadLuxe.emerald
            )
            Text(
                label,
                fontSize = 10.sp,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementData) {
    Card(
        modifier = Modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) com.example.ui.theme.ZadLuxe.cardWhite else Color(0xFFF1F5F9)
        ),
        shape = com.example.ui.theme.ZadLuxe.squircle,
        border = if (achievement.isUnlocked)
            androidx.compose.foundation.BorderStroke(2.dp, com.example.ui.theme.ZadLuxe.ochre)
        else
            androidx.compose.foundation.BorderStroke(0.5.dp, com.example.ui.theme.ZadLuxe.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                achievement.icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                achievement.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            if (achievement.isUnlocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "+${achievement.points}",
                    fontSize = 10.sp,
                    color = com.example.ui.theme.ZadLuxe.ochre,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.achievements_locked),
                    fontSize = 9.sp,
                    color = Color(0xFFA1A5AB)
                )
            }
        }
    }
}
