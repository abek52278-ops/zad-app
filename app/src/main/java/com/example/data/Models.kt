package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Serializable
data class AiPriceEstimate(
    @SerialName("item_name") val itemName: String = "",
    @SerialName("low_price") val lowPrice: Double = 0.0,
    @SerialName("avg_price") val avgPrice: Double = 0.0,
    @SerialName("high_price") val highPrice: Double = 0.0,
    val store: String? = null,
    val currency: String = "SAR"
)

@Serializable
data class AiExpensePrediction(
    @SerialName("predicted_total") val predictedTotal: Double = 0.0,
    val confidence: Double = 0.0,
    val breakdown: List<AiPredictionBreakdown> = emptyList(),
    val warnings: List<String> = emptyList(),
    val tips: List<String> = emptyList()
)

@Serializable
data class AiPredictionBreakdown(
    val category: String = "",
    val predicted: Double = 0.0,
    @SerialName("avg_monthly") val avgMonthly: Double = 0.0
)

@Serializable
data class AiBillClassification(
    val type: String = "other",
    val provider: String? = null,
    val category: String = "عام",
    val confidence: Double = 0.0,
    @SerialName("is_recurring") val isRecurring: Boolean = false,
    @SerialName("suggested_frequency_days") val suggestedFrequencyDays: Int? = null
)

@Serializable
data class AiAgentSummary(
    val summary: String = "",
    val alerts: List<AiAgentAlert> = emptyList(),
    val suggestions: List<AiAgentSuggestion> = emptyList(),
    val stats: AiAgentStats = AiAgentStats()
)

@Serializable
data class AiAgentAlert(
    val type: String = "info",
    val title: String = "",
    val description: String = ""
)

@Serializable
data class AiAgentSuggestion(
    val action: String = "",
    val item: String = "",
    val reason: String = ""
)

@Serializable
data class AiAgentStats(
    @SerialName("inventory_count") val inventoryCount: Int = 0,
    @SerialName("expiring_soon") val expiringSoon: Int = 0,
    @SerialName("subscriptions_active") val subscriptionsActive: Int = 0,
    @SerialName("days_until_budget_end") val daysUntilBudgetEnd: Int = 30
)

@Entity(tableName = "zad_users")
@Serializable
data class ZadUser(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val budget: Double = 0.0,
    @SerialName("avatar_uri") val avatarUri: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("emergency_fund_balance") val emergencyFundBalance: Double = 0.0
)

@Entity(tableName = "zad_inventory")
@Serializable
data class ZadInventory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_name") val itemName: String,
    val category: String? = null,
    val quantity: Int = 1,
    val unit: String? = "pcs",
    @SerialName("low_stock_threshold") val lowStockThreshold: Int? = 2,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Entity(tableName = "zad_transactions")
@Serializable
data class ZadTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    val amount: Double,
    val title: String,
    val category: String? = null,
    @SerialName("is_expense") val isExpense: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("bank_name") val bankName: String? = null,
    @SerialName("merchant_name") val merchantName: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false
)

@Serializable
data class BehaviorCategoryTotal(
    val category: String = "",
    val total: Double = 0.0
)

@Serializable
data class UserBehaviorProfile(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("avg_weekly_spending") val avgWeeklySpending: Double = 0.0,
    @SerialName("top_spending_categories") val topSpendingCategories: List<BehaviorCategoryTotal> = emptyList(),
    @SerialName("spending_pattern_by_weekday") val spendingPatternByWeekday: Map<String, Double> = emptyMap(),
    @SerialName("subscription_load_monthly") val subscriptionLoadMonthly: Double = 0.0,
    @SerialName("inventory_consumption_rate") val inventoryConsumptionRate: Map<String, Double> = emptyMap(),
    @SerialName("last_updated_at") val lastUpdatedAt: String? = null
)

@Entity(tableName = "zad_subscriptions")
@Serializable
data class ZadSubscription(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val amount: Double,
    @SerialName("renewal_date") val renewalDate: String? = null,
    val category: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    val type: String = "subscription",
    val provider: String? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("auto_deduct") val autoDeduct: Boolean = false
)

@Serializable
data class LiveDeal(
    val item: String = "",
    val store: String = "",
    val price: Double = 0.0,
    @SerialName("discount_percent") val discountPercent: Double = 0.0,
    val note: String? = null
)

@Serializable
data class PriceShockWarning(
    val category: String = "",
    @SerialName("expected_change_pct") val expectedChangePct: Double = 0.0,
    val direction: String = "up",
    val reasoning: String = "",
    @SerialName("source_note") val sourceNote: String? = null
)

@Serializable
data class ZadDebt(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("family_id") val familyId: String? = null,
    val name: String,
    @SerialName("principal_amount") val principalAmount: Double = 0.0,
    @SerialName("remaining_balance") val remainingBalance: Double = 0.0,
    @SerialName("interest_rate") val interestRate: Double = 0.0,
    @SerialName("minimum_payment") val minimumPayment: Double = 0.0,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Entity(tableName = "zad_behavior_patterns")
@Serializable
data class ZadBehaviorPattern(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    val category: String,
    @SerialName("avg_amount") val avgAmount: Double = 0.0,
    @SerialName("frequency_days") val frequencyDays: Int = 0,
    @SerialName("typical_time_of_day") val typicalTimeOfDay: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null
)

@Entity(tableName = "zad_shopping_list")
@Serializable
data class ZadShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_name") val itemName: String,
    val quantity: Int = 1,
    @SerialName("estimated_price") val estimatedPrice: Double = 0.0,
    @ColumnInfo(name = "is_purchased")
    @SerialName("is_purchased") val isPurchased: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val priority: String = "medium",
    @SerialName("predicted_days_left") val predictedDaysLeft: Int? = null,
    val store: String? = null
)

@Serializable
data class VoiceAgentResponse(
    val action: String = "chat",
    val message: String = "",
    val data: VoiceAgentData? = null
)

@Serializable
data class VoiceAgentData(
    val amount: Double = 0.0,
    val title: String = "",
    val category: String = "عام"
)

@Entity(tableName = "affiliate_products")
@Serializable
data class AffiliateProduct(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "product_name_ar")
    @SerialName("product_name_ar") val productNameAr: String,
    @ColumnInfo(name = "product_name_search_keywords")
    @SerialName("product_name_search_keywords") val productNameSearchKeywords: List<String> = emptyList(),
    val category: String? = null,
    val asin: String,
    @ColumnInfo(name = "image_url")
    @SerialName("image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "average_price_sar")
    @SerialName("average_price_sar") val averagePriceSar: Double = 0.0,
    @ColumnInfo(name = "is_active")
    @SerialName("is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    @SerialName("created_at") val createdAt: String? = null
)

@Entity(tableName = "affiliate_clicks")
@Serializable
data class AffiliateClick(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "product_id")
    @SerialName("product_id") val productId: String,
    @ColumnInfo(name = "user_id")
    @SerialName("user_id") val userId: String? = null,
    @ColumnInfo(name = "source_screen")
    @SerialName("source_screen") val sourceScreen: String = "shopping",
    @ColumnInfo(name = "clicked_at")
    @SerialName("clicked_at") val clickedAt: String? = null
)

@Entity(tableName = "affiliate_catalog_requests")
@Serializable
data class AffiliateCatalogRequest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "searched_term")
    @SerialName("searched_term") val searchedTerm: String,
    @ColumnInfo(name = "user_id")
    @SerialName("user_id") val userId: String? = null,
    @ColumnInfo(name = "created_at")
    @SerialName("created_at") val createdAt: String? = null
)

/** ذاكرة شات زاد الدائمة — محلية فقط (Room)، مش متزامنة مع Supabase */
@Entity(tableName = "zad_chat_messages")
data class ZadChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    @ColumnInfo(name = "is_user") val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
