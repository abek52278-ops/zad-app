package com.example.ui.screens

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
import com.example.ui.theme.primary

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

@Composable
fun RecommendationsScreen(
    recommendations: List<RecommendationData>,
    stats: RecommendationsStatsData,
    onAction: (recommendationId: String) -> Unit,
    onDismiss: (recommendationId: String) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
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
                    Icon(Icons.Default.ArrowBack, "Back", tint = primary)
                }
                Text(
                    "توصيات الشراء الذكية",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.AutoAwesome, "AI", tint = primary, modifier = Modifier.size(24.dp))
            }
        }

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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Empty",
                            tint = primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "شغلت كل التوصيات! 🎉",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "بتتحدث التوصيات كل ساعة",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
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
                    Text("تم", fontSize = 11.sp)
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
