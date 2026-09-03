package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseRepo
import com.example.data.ZadShoppingItem
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadLoadingState
import com.example.ui.theme.primary
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

data class RecommendationData(
    val id: String,
    val itemName: String,
    val recommendationType: String, // buy_now, wait, bulk_buy, avoid, substitute
    val reasoning: String,
    val estimatedSavings: Double,
    val urgency: String, // low, medium, high
    val bestStore: String? = null,
    val bestPrice: Double? = null,
    val actedOn: Boolean = false
)

data class RecommendationsStatsData(
    val totalRecommendations: Int,
    val totalSavingsPotential: Double,
    val actionedCount: Int
)

private fun com.example.data.ZadShoppingRecommendation.toUiData() = RecommendationData(
    id = id.toString(),
    itemName = itemName,
    recommendationType = recommendationType,
    reasoning = reasoning ?: "",
    estimatedSavings = estimatedSavings ?: 0.0,
    urgency = urgency,
    bestStore = bestStore,
    bestPrice = bestPrice,
    actedOn = false
)

/**
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط AchievementsRoute/ZadMemoryScreen:
 * قراءة/كتابة Supabase مباشرة من غير أي نداء LLM هنا. كانت الشاشة قبل كده بتاخد
 * recommendations/stats كـ parameters فاضية بلا أي caller حقيقي.
 *
 * دلوقتي تاب داخل PantryShoppingScreen (UI_ARCHITECTURE_SPEC.md §2.3) بدل route
 * منفصل (`ZadNav.RECOMMENDATIONS` كانت orphaned — وصولها الوحيد كان من البروفايل) —
 * مفيش onBack لأنها مش شاشة مستقلة تحتاج ترجع منها.
 *
 * UI_ARCHITECTURE_SPEC.md §7.3 — `onAction` كانت بس بتنده markRecommendationActedOn
 * (تغيير status)، من غير أي إضافة فعلية لـ zad_shopping_list — الزرار كان بيوهم إنه
 * "حوّل التوصية لسلة" بينما فعليًا بيخفي الكارت بس. دلوقتي بيضيف الصنف فعليًا كمان.
 */
@Composable
fun RecommendationsRoute(viewModel: ZadViewModel) {
    var recommendations by remember { mutableStateOf<List<com.example.data.ZadShoppingRecommendation>>(emptyList()) }
    var actionedCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        recommendations = SupabaseRepo.getShoppingRecommendations()
        actionedCount = SupabaseRepo.getActedOnRecommendationsCount()
        loading = false
    }

    if (loading) {
        ZadLoadingState(modifier = Modifier.fillMaxSize())
    } else {
        val stats = RecommendationsStatsData(
            totalRecommendations = recommendations.size,
            totalSavingsPotential = recommendations.sumOf { it.estimatedSavings ?: 0.0 },
            actionedCount = actionedCount
        )
        RecommendationsScreen(
            recommendations = recommendations.map { it.toUiData() },
            stats = stats,
            onAction = { idStr ->
                idStr.toLongOrNull()?.let { id ->
                    val rec = recommendations.find { it.id == id }
                    recommendations = recommendations.filterNot { it.id == id }
                    actionedCount += 1
                    if (rec != null) {
                        viewModel.addShoppingItem(
                            ZadShoppingItem(
                                itemName = rec.itemName,
                                quantity = 1,
                                estimatedPrice = rec.bestPrice ?: 0.0,
                                store = rec.bestStore ?: ""
                            )
                        )
                    }
                    scope.launch {
                        if (!SupabaseRepo.markRecommendationActedOn(id)) {
                            Log.e("RecommendationsRoute", "markRecommendationActedOn($id) FAILED")
                        }
                    }
                }
            },
            onDismiss = { idStr ->
                idStr.toLongOrNull()?.let { id ->
                    recommendations = recommendations.filterNot { it.id == id }
                    scope.launch {
                        if (!SupabaseRepo.dismissRecommendation(id)) {
                            Log.e("RecommendationsRoute", "dismissRecommendation($id) FAILED")
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun RecommendationsScreen(
    recommendations: List<RecommendationData>,
    stats: RecommendationsStatsData,
    onAction: (recommendationId: String) -> Unit,
    onDismiss: (recommendationId: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = com.example.ui.theme.ZadHubListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "إجمالي التوفير",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                com.example.data.CurrencyFormatter.format(context, stats.totalSavingsPotential),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "التوصيات النشطة",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${stats.totalRecommendations}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "منفذة",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${stats.actionedCount}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Filter Info
        item {
            Text(
                "التوصيات المقترحة",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Recommendation Items
        items(recommendations) { rec ->
            RecommendationCard(
                recommendation = rec,
                onAction = { onAction(rec.id) },
                onDismiss = { onDismiss(rec.id) }
            )
        }

        // Empty State
        if (recommendations.isEmpty()) {
            item {
                ZadEmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "شغلت كل التوصيات! 🎉",
                    subtitle = "بتتحدث التوصيات كل ساعة",
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: RecommendationData,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Urgency Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when (recommendation.urgency) {
                                "high" -> Color(0xFFEF4444)
                                "medium" -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (recommendation.urgency) {
                            "high" -> "🔴"
                            "medium" -> "🟡"
                            else -> "🟢"
                        },
                        fontSize = 16.sp
                    )
                }

                // Item Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recommendation.itemName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        getRecommendationLabel(recommendation.recommendationType),
                        fontSize = 12.sp,
                        color = primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Savings
                if (recommendation.estimatedSavings > 0) {
                    Text(
                        "−" + com.example.data.CurrencyFormatter.format(context, recommendation.estimatedSavings),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            // Divider
            Divider(color = Color(0xFFF1F5F9))

            // Reasoning
            Text(
                recommendation.reasoning,
                fontSize = 12.sp,
                color = Color(0xFF475569),
                modifier = Modifier.padding(12.dp)
            )

            // Store Info (if available)
            if (recommendation.bestStore != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0FDF4))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        "Store",
                        tint = primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            recommendation.bestStore,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        if (recommendation.bestPrice != null) {
                            Text(
                                com.example.data.CurrencyFormatter.format(context, recommendation.bestPrice),
                                fontSize = 10.sp,
                                color = primary
                            )
                        }
                    }
                }
            }

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF475569)
                    )
                ) {
                    Text("لاحقاً", fontSize = 11.sp)
                }

                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        "Action",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("أضف للسلة", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun getRecommendationLabel(type: String): String = when (type) {
    "buy_now" -> "🛒 اشتري دلوقتي"
    "wait" -> "⏳ انتظر شوية"
    "bulk_buy" -> "📦 اشتري كمية"
    "avoid" -> "❌ تجنّب"
    "substitute" -> "🔄 استخدم بديل"
    else -> "تم"
}
