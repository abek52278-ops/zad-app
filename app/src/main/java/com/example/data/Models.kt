package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
data class AiSeasonalForecast(
    @SerialName("event_id") val eventId: String = "",
    val slug: String? = null,
    @SerialName("days_until") val daysUntil: Int = 0,
    @SerialName("predicted_total") val predictedTotal: Double = 0.0,
    val confidence: Double = 0.0,
    val breakdown: List<AiSeasonalForecastBreakdown> = emptyList(),
    val tip: String = ""
)

@Serializable
data class AiSeasonalForecastBreakdown(
    val category: String = "",
    val predicted: Double = 0.0,
    @SerialName("baseline_monthly_avg") val baselineMonthlyAvg: Double = 0.0,
    @SerialName("multiplier_used") val multiplierUsed: Double = 0.0,
    val source: String = "fallback"
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
    @SerialName("auto_deduct") val autoDeduct: Boolean = false,
    @SerialName("billing_cycle") val billingCycle: String? = "MONTHLY"
)

@Entity(tableName = "zad_pharmacy_items")
@Serializable
data class ZadPharmacyItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("active_ingredient") val activeIngredient: String? = null,
    val category: String = "عام", // مسكن / مضاد حيوي / فيتامين / مزمن / عام
    val dosage: String? = null, // "500mg"، "قرص كل 8 ساعات"...
    @SerialName("remaining_quantity") val remainingQuantity: Int = 1,
    val unit: String = "قرص",
    @SerialName("daily_dose_count") val dailyDoseCount: Int = 1,
    @SerialName("dose_times") val doseTimes: String? = null, // "08:00,20:00" — مواعيد الجرعة بالساعة، اختياري
    @SerialName("expiry_date") val expiryDate: String? = null,
    val price: Double = 0.0,
    @SerialName("is_recurring") val isRecurring: Boolean = false, // دواء مزمن/روشتة متجددة — يدخل في حساب التكلفة الشهرية
    @SerialName("family_member_id") val familyMemberId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Task 17.2.1 — nullable, NO default. remaining_quantity / daily_dose_count silently
    // assumed one dose = one unit; defaulting this to 1 would make the same wrong guess
    // just one layer deeper. null means "we genuinely don't know" — see daysOfSupplyLeft().
    @SerialName("units_per_dose") val unitsPerDose: Double? = null,
    @SerialName("qty_confirmed_at") val qtyConfirmedAt: String? = null,
    @SerialName("has_invalid_dose_time") val hasInvalidDoseTime: Boolean = false
) {
    /** كام يوم يكفي المخزون الحالي — null لو unitsPerDose مش معروف، مش رقم مخمّن أبداً */
    fun daysOfSupplyLeft(): Int? {
        val perDose = unitsPerDose
        if (perDose == null || perDose <= 0.0 || dailyDoseCount <= 0) return null
        return kotlin.math.floor(remainingQuantity / (perDose * dailyDoseCount)).toInt()
    }

    fun doseTimesList(): List<String> = doseTimes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}

// Task 17.2.2 — per-dose history record (zad_pharmacy_doses). The unique index on
// (user_id, item_id, scheduled_at) is what makes logging the same scheduled dose twice
// (notification tap + screen tap) a safe no-op instead of a double stock deduction.
@Serializable
data class ZadPharmacyDose(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_id") val itemId: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("taken_at") val takenAt: String? = null,
    val status: String, // taken | skipped | missed
    val units: Double = 1.0,
    @SerialName("created_at") val createdAt: String? = null
)

@Entity(tableName = "zad_dose_log")
@Serializable
data class ZadDoseLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("pharmacy_item_id") val pharmacyItemId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("scheduled_at") val scheduledAt: String, // الميعاد المفروض تُؤخذ فيه الجرعة
    @SerialName("taken_at") val takenAt: String? = null, // null = لسه معلّقة/فاتت من غير ما تتاخد
    @SerialName("created_at") val createdAt: String? = null
)

@Entity(tableName = "zad_maintenance_items")
@Serializable
data class ZadMaintenanceItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    val name: String, // "تكييف الصالة"، "سيارة"...
    val category: String = "عام", // تكييف / سخان / غسالة / سيارة / فلتر مياه / عام
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("warranty_expiry_date") val warrantyExpiryDate: String? = null,
    @SerialName("last_service_date") val lastServiceDate: String? = null,
    @SerialName("service_interval_days") val serviceIntervalDays: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double = 0.0,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    /** أول موعد صيانة معروف (شراء أو تركيب) — أساس حساب الموعد الجاي لو لسه ماخدتش صيانة */
    private fun baselineDate(): String? = lastServiceDate ?: purchaseDate

    fun nextServiceDate(): LocalDate? {
        val base = baselineDate() ?: return null
        val interval = serviceIntervalDays ?: return null
        return try { LocalDate.parse(base.take(10)).plusDays(interval.toLong()) } catch (e: Exception) { null }
    }

    fun daysUntilService(): Int? = nextServiceDate()?.let {
        ChronoUnit.DAYS.between(LocalDate.now(), it).toInt()
    }

    fun daysUntilWarrantyExpiry(): Int? = warrantyExpiryDate?.let {
        try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.take(10))).toInt() } catch (e: Exception) { null }
    }
}

/** نتيجة سوبرماركت قريب من OpenStreetMap/Overpass — مش من Google Places (مجاني وبدون API key) */
data class NearbyStore(
    val name: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Int
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
data class MarketPriceItem(
    val symbol: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    @SerialName("change_percent") val changePercent: Double = 0.0,
    /** "up" | "down" | "flat" */
    val trend: String = "flat"
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

/**
 * Outbox for a Supabase write that failed while offline — local-first paths (SaBankParser's
 * offline transaction parse, shopping-list inserts, ...) already write to Room immediately and
 * only best-effort push to Supabase; before this entity, a failed push was just logged and
 * dropped (see SyncOutbox.kt). Room-only, never itself synced to Supabase — it IS the retry queue.
 */
@Entity(tableName = "zad_pending_sync_ops")
data class PendingSyncOp(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val opType: String, // "add_transaction" — only type today, extend as new offline-write paths need it
    val payloadJson: String,
    val createdAt: String,
    val attempts: Int = 0
)

/**
 * سجل رسايل بنكية محتاجة مراجعة — غرضين مختلفين بنفس الشكل:
 * 1. OTP/DECLINED/EXPIRED/PROMO: اتّرفضت كضجيج قبل أي تحليل. الغرض: التأكد إن الفلتر مش
 *    بيبلع عمليات حقيقية غلط — لو مستخدم اشتكى "معاملة ضاعت"، الجدول ده أول مكان تتفقده.
 * 2. UNPARSED: عدّت فحص "شكلها بنكية" لكن كل مسارات التحليل فشلت تفهمها — مادة خام لإضافة
 *    rule جديدة في bank_rules.json بدقة بدل تخمين.
 * Room-only، مش بيتزامن مع Supabase (بيانات تشخيصية، مش بيانات مستخدم مالية).
 * مقفول على آخر 200 صف إجمالاً بين النوعين (SaBankParser.logRejection()).
 */
@Entity(tableName = "zad_rejected_bank_messages")
data class RejectedBankMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reason: String, // "OTP" | "DECLINED" | "EXPIRED" | "PROMO" | "UNPARSED"
    val source: String,
    val rawText: String,
    val createdAt: String
)
