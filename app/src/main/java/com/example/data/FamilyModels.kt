package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FamilyGroup(
    val id: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FamilyMember(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("zad_id") val zadId: String? = null,
    val role: String = "member", // admin (parent) or child
    val alias: String = "",
    val balance: Double = 0.0,
    @SerialName("savings_goal") val savingsGoal: Double = 0.0,
    @SerialName("daily_limit") val dailyLimit: Double? = null,
    @SerialName("weekly_limit") val weeklyLimit: Double? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val isOnline: Boolean get() {
        if (lastSeenAt == null) return false
        return try {
            val lastSeen = java.time.Instant.parse(lastSeenAt!!)
            val now = java.time.Instant.now()
            java.time.Duration.between(lastSeen, now).toMinutes() < 5
        } catch (e: Exception) { false }
    }
}

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    val message: String = "",
    @SerialName("message_type") val messageType: String = "TEXT", // TEXT, PURCHASE_REQUEST, SOS, POLL
    val metadata: String? = null, // JSON string for extra data
    @SerialName("is_pinned") val isPinned: Boolean = false,
    val reactions: String? = null, // JSON: {"👍":["user1","user2"],"❤️":["user3"]}
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SharedGroceryItem(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("added_by") val addedBy: String = "",
    @SerialName("item_name") val itemName: String = "",
    val category: String? = null,
    @SerialName("is_purchased") val isPurchased: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FamilyGoal(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("month_year") val monthYear: String = "",
    @SerialName("reward_suggestion") val rewardSuggestion: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Chore(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("assigned_to") val assignedTo: String = "",
    val title: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reward_amount") val rewardAmount: Double = 0.0,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AppNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "",
    val message: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

// zad-brain's emit_insight action writes here (kind: insight/question/alert,
// surface: home_card/bell/voice, priority: normal/critical). Written since the
// brain shipped, never read by the client until now — the whole "brain learns
// and surfaces it to the user" pipeline was a write-only dead end.
@Serializable
data class ZadInsight(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val kind: String = "insight", // insight | question | alert
    val surface: String = "home_card", // home_card | bell | voice
    val priority: String = "normal", // normal | critical
    val title: String = "",
    val body: String = "",
    @SerialName("dedupe_key") val dedupeKey: String? = null,
    val status: String = "pending", // pending | seen | acted | dismissed
    @SerialName("action_type") val actionType: String? = null,
    @SerialName("about_item") val aboutItem: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TasbihaTree(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("tree_name") val treeName: String = "بذرة",
    @SerialName("tree_type") val treeType: String = "normal",
    val level: Int = 1,
    val score: Int = 0,
    @SerialName("total_clicks") val totalClicks: Int = 0,
    @SerialName("last_tasbih_at") val lastTasbihAt: String? = null,
    @SerialName("garden_name") val gardenName: String = "بستاني",
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("matured_at") val maturedAt: String? = null,
    @SerialName("tree_emoji") val treeEmoji: String = "🌰",
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("last_streak_date") val lastStreakDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun nextLevelAt(): Int = when (level) {
        1 -> 99
        2 -> 198
        3 -> 297
        4 -> 396
        else -> Int.MAX_VALUE
    }

    fun progressToNext(): Float = if (level >= 5) 1f else score.toFloat() / nextLevelAt().toFloat()

    fun stageEmoji(): String = when {
        level >= 5 && treeType == "golden" -> "🌟🌳🌸"
        level >= 5 && treeType == "special" -> "💎🌳🌸"
        level >= 5 -> "🌳🌸"
        level == 4 && treeType == "golden" -> "🌟🌳"
        level == 4 && treeType == "special" -> "💎🌳"
        level == 4 -> "🌳"
        level == 3 -> "🌿"
        level == 2 -> "🌱"
        else -> "🌰"
    }

    fun stageName(): String = when {
        level >= 5 -> "مثمرة 🍎"
        level == 4 -> "شجرة 🌳"
        level == 3 -> "شتلة 🌿"
        level == 2 -> "بذرة نبتت 🌱"
        else -> "بذرة 🌰"
    }

    fun typeEmoji(): String = when (treeType) {
        "golden" -> "⭐"
        "special" -> "💎"
        else -> ""
    }

    fun typeColor(): String = when (treeType) {
        "golden" -> "#FFD700"
        "special" -> "#9C27B0"
        else -> "#4CAF50"
    }

    companion object {
        val TREE_TYPES = listOf("normal", "special", "golden")
        val LEVEL_NAMES = listOf("بذرة", "بذرة نبتت", "شتلة", "شجرة", "مثمرة")
    }
}

@Serializable
data class TasbihaChallenge(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("challenge_type") val challengeType: String = "weekly",
    val title: String = "",
    val description: String? = null,
    @SerialName("target_clicks") val targetClicks: Int = 100,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TasbihaChallengeProgress(
    val id: String = "",
    @SerialName("challenge_id") val challengeId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("current_clicks") val currentClicks: Int = 0,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FinancialChallenge(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("challenge_type") val challengeType: String = "monthly",
    val title: String = "",
    val description: String? = null,
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("reward_amount") val rewardAmount: Double = 0.0,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FinancialChallengeProgress(
    val id: String = "",
    @SerialName("challenge_id") val challengeId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SeasonalEvent(
    val id: String = "",
    @SerialName("family_id") val familyId: String? = null,
    val slug: String? = null,
    val name: String = "",
    @SerialName("category_tags") val categoryTags: List<String> = emptyList(),
    @SerialName("is_recurring") val isRecurring: Boolean = false,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SeasonalEventWindow(
    val id: String = "",
    @SerialName("event_id") val eventId: String = "",
    val year: Int = 0,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = ""
)

@Serializable
data class SinkingFund(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("event_id") val eventId: String? = null,
    val name: String = "",
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FamilyMemberWithTasbiha(
    val member: FamilyMember,
    val trees: List<TasbihaTree>,
    val totalScore: Int = 0,
    val matureTrees: Int = 0
)

@Serializable
data class TypingStatus(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("is_typing") val isTyping: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

data class QuickReplyItem(
    val emoji: String,
    val text: String,
    val action: String = "text"
)

enum class MessageIntent { NORMAL, EXPENSE, SOS }

private val sosKeywords = listOf(
    "طوارئ", "طواري", "نجدة", "خطر شديد", "ساعدوني", "ساعديني", "إسعاف", "الإسعاف", "حادث", "حريق",
    "مخطوف", "خطف", "تعرضت لحادث", "بينزف", "بتنزف", "أنزف", "غريق", "تايه", "ضعت", "مش لاقي طريقي",
    "emergency", "help me", "accident", "injured"
)

private val expenseKeywords = listOf(
    "محتاج", "محتاجة", "عايز", "عاوز", "أحتاج", "اريد", "أريد", "لازمني", "هاخد", "هشتري", "عايزة اشتري"
)

/** تصنيف سريع محلي لنية الرسالة — بدل استدعاء AI لكل رسالة (تأخير + تكلفة)، فحص كلمات مفتاحية كافٍ لرصد نداء طوارئ حقيقي أو طلب مصروف واضح المبلغ. أي رسالة غامضة تفضل NORMAL. */
fun classifyMessageIntent(text: String): MessageIntent {
    val lower = text.trim().lowercase()
    if (lower.isEmpty()) return MessageIntent.NORMAL
    if (sosKeywords.any { lower.contains(it.lowercase()) }) return MessageIntent.SOS
    if (expenseKeywords.any { lower.contains(it.lowercase()) } && Regex("\\d").containsMatchIn(text)) return MessageIntent.EXPENSE
    return MessageIntent.NORMAL
}

fun extractExpenseAmount(text: String): Double? =
    Regex("\\d+(\\.\\d+)?").find(text)?.value?.toDoubleOrNull()

/** مجموع طلبات الشراء الموافق عليها لعضو معين منذ لحظة زمنية معينة — يُستخدم لحساب استهلاك اليوم/الأسبوع مقابل daily_limit/weekly_limit، بالاعتماد على تاريخ رسائل الشات بدل جدول منفصل. */
fun approvedSpendSince(messages: List<ChatMessage>, memberId: String, since: java.time.Instant): Double {
    return messages
        .filter { it.messageType == "PURCHASE_REQUEST" && it.senderId == memberId }
        .filter { it.metadata?.contains("\"status\":\"APPROVED\"") == true }
        .filter { msg ->
            val createdAt = msg.createdAt ?: return@filter false
            try { java.time.Instant.parse(createdAt) >= since } catch (e: Exception) { false }
        }
        .sumOf { msg ->
            val metaJson = msg.metadata ?: "{}"
            metaJson.substringAfter("\"amount\":").substringBefore(",").trim().toDoubleOrNull() ?: 0.0
        }
}
