package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class PriceReportState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val contributionCount: Int = 0,
    val leaderboard: List<LeaderboardEntryData> = emptyList()
)

data class LeaderboardEntryData(
    val userId: String,
    val userName: String,
    val contributionCount: Int
)

class PriceReportingViewModel : ViewModel() {
    private val _state = MutableStateFlow(PriceReportState())
    val state: StateFlow<PriceReportState> = _state

    @Serializable
    private data class UserIdRow(val user_id: String? = null)

    fun submitPrice(
        itemName: String,
        category: String,
        price: Double,
        location: String,
        storeName: String
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                // Insert price into price_index table with current user_id
                val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                SupabaseRepo.client.postgrest["price_index"]
                    .insert(
                        mapOf(
                            "item_name" to itemName,
                            "item_category" to category,
                            "price" to price,
                            "currency" to "EGP",
                            "user_id" to userId,
                            "location" to location,
                            "source" to "crowdsource",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )

                _state.value = _state.value.copy(
                    isSubmitting = false,
                    successMessage = "تم تسجيل السعر بنجاح! شكراً على مساهمتك.",
                    contributionCount = _state.value.contributionCount + 1
                )

                // Clear success message after 3 seconds
                kotlinx.coroutines.delay(3000)
                _state.value = _state.value.copy(successMessage = null)

                // Refresh leaderboard
                loadLeaderboard()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "حدث خطأ أثناء التسجيل"
                )
            }
        }
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            try {
                // PostgREST القياسي مش بيعمل GROUP BY حر من غير view/RPC مخصص، فالعدّ
                // بيتحسب هنا محليًا بدل استعلام aggregate غير مضمون النتيجة. لازم
                // order صريح — من غيره الـ٥٠٠ صف اللي بترجع عشوائية بمجرد ما الجدول
                // يعدّي ٥٠٠ صف، فمساهم حقيقي ممكن يقع بره النافذة ويختفي من اللوحة.
                val rows = SupabaseRepo.client.postgrest["price_index"]
                    .select(Columns.list("user_id")) {
                        order(column = "timestamp", order = Order.DESCENDING)
                        limit(500)
                    }
                    .decodeList<UserIdRow>()

                val leaderboardEntries = rows
                    .mapNotNull { it.user_id }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .mapIndexed { index, (userId, count) ->
                        LeaderboardEntryData(
                            userId = userId,
                            userName = "المساهم ${index + 1}",
                            contributionCount = count
                        )
                    }

                _state.value = _state.value.copy(leaderboard = leaderboardEntries)
            } catch (e: Exception) {
                // Silently fail for leaderboard load
                _state.value = _state.value.copy(leaderboard = emptyList())
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}
