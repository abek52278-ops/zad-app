package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.data.SupabaseRepo

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
    val contributionCount: Int,
    val score: Int
)

class PriceReportingViewModel(private val repo: SupabaseRepo) : ViewModel() {
    private val _state = MutableStateFlow(PriceReportState())
    val state: StateFlow<PriceReportState> = _state

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
                val userId = repo.getCurrentUserId() ?: throw Exception("User not authenticated")

                val result = repo.supabase
                    .from("price_index")
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
                // Query price_index for top contributors
                val result = repo.supabase
                    .from("price_index")
                    .select("user_id, count(*) as count")
                    .limit(10)
                    .decodeAs<List<Map<String, Any>>>()

                // Transform to LeaderboardEntryData
                val leaderboardEntries = result.mapIndexed { index, entry ->
                    LeaderboardEntryData(
                        userId = entry["user_id"] as? String ?: "",
                        userName = "المساهم ${index + 1}",
                        contributionCount = (entry["count"] as? Number)?.toInt() ?: 0,
                        score = (index + 1) * 10
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
