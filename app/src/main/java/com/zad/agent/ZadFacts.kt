package com.zad.agent

import android.content.Context
import com.example.data.ZadCentralBrain
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import com.example.ui.screens.StressTestResult
import com.example.ui.screens.calculateStressTest

/**
 * Task 9 — every field here is pure local computation: no network, no LLM, no
 * Supabase call. It exists because the same math (ZadCentralBrain.generateReport,
 * calculateStressTest) already ran correctly, but only inside ZadIntelligenceScreen's
 * Composable scope — Home never saw it, so these 7 numbers looked like dead shells
 * there even though the underlying engines were already right.
 *
 * ZadFacts is deliberately a thin wrapper, not a re-derivation: it composes the two
 * existing report types instead of recomputing health score / spending power /
 * category breakdown a second time from scratch.
 */
data class ZadFacts(
    val report: ZadCentralBrain.BrainReport,
    val stressTest: StressTestResult
)

suspend fun computeZadFacts(
    context: Context,
    inventory: List<ZadInventory>,
    transactions: List<ZadTransaction>,
    subscriptions: List<ZadSubscription>,
    budget: Double,
    emergencyFund: Double
): ZadFacts {
    val report = ZadCentralBrain.generateReport(
        context = context,
        inventory = inventory,
        transactions = transactions,
        subscriptions = subscriptions,
        budget = budget
    )
    val stressTest = calculateStressTest(transactions, emergencyFund)
    return ZadFacts(report, stressTest)
}
