package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Phase 0 — the `zad_budget_state(p_user, p_tz)` row, the single authority for every money
 * figure zad shows (migration `20260809120000_single_budget_authority.sql`).
 *
 * Why this exists when [BudgetMath] already computes all of it. It computed *the app's*
 * version of all of it. `zad-brain` and the Telegram bot each had their own, and the three
 * disagreed: the brain dropped income from `remaining` and read the cycle window in UTC,
 * the bot used calendar months and blanked the ceiling whenever `limit_confirmed_at` was
 * null. The same customer could read three different "المتبقي" figures for the same day
 * depending on which surface they opened.
 *
 * The division of labour from here on:
 *
 * - **[BudgetMath] renders instantly and works offline.** It runs on Room data the moment
 *   a transaction changes, with no network round-trip, and it is the only thing that can
 *   answer at all on a plane. It stays.
 * - **This row wins whenever it arrives.** It sees every transaction (not just the synced
 *   subset), it honours the account's own timezone, and it is the same row the bot and the
 *   brain read. When the RPC answers, its values replace the locally derived ones.
 *
 * So [BudgetMath] is the mirror and this is the original. If the two ever disagree, the
 * mirror is wrong — fix `BudgetMath.kt` to match the SQL, never the other way round.
 *
 * Nullable [monthlyLimit]/[remaining]/[available] mean "no ceiling set", which is not zero.
 * Same contract as `BudgetMath.remaining`.
 */
@Serializable
data class BudgetState(
    @SerialName("monthly_limit") val monthlyLimit: Double? = null,
    @SerialName("limit_confirmed") val limitConfirmed: Boolean = false,
    val spent: Double = 0.0,
    val income: Double = 0.0,
    val remaining: Double? = null,
    val committed: Double = 0.0,
    val available: Double? = null,
    @SerialName("cash_on_hand") val cashOnHand: Double = 0.0,
    @SerialName("cycle_start") val cycleStart: String? = null,
    @SerialName("cycle_end") val cycleEnd: String? = null,
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("days_elapsed") val daysElapsed: Int = 0,
    @SerialName("daily_allowance_left") val dailyAllowanceLeft: Double? = null,
    val velocity: Double? = null,
    val threat: String = "UNKNOWN",
    /** نفس تجميع RPC للدورة وtxn_kind؛ لا تعيد الشاشات جمعه بـ isExpense. */
    @SerialName("by_category") val byCategory: Map<String, Double> = emptyMap(),
    @SerialName("unverified_count") val unverifiedCount: Int = 0,
    val currency: String? = null,
    val timezone: String? = null,
    /**
     * When the server computed this. Rendered nowhere, but carried so two surfaces showing
     * different numbers is a diagnosable staleness question rather than an unanswerable
     * "which formula ran where" — the exact ambiguity that made the original bug so hard
     * to pin down.
     */
    @SerialName("computed_at") val computedAt: String? = null,
) {
    fun cycleStartDate(): LocalDate? = cycleStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun cycleEndDate(): LocalDate? = cycleEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
