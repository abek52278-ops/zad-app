package com.example.data

enum class HomeActivationStep {
    SET_BALANCE,
    ENABLE_BANK_READING,
    ADD_FIRST_INVENTORY_ITEM,
}

data class HomeActivationProgress(
    val completedSteps: Set<HomeActivationStep>,
) {
    val totalCount: Int = HomeActivationStep.entries.size
    val completedCount: Int = completedSteps.size
    val isComplete: Boolean = completedCount == totalCount

    fun isComplete(step: HomeActivationStep): Boolean = step in completedSteps
}

fun buildHomeActivationProgress(
    hasConfirmedBalance: Boolean,
    bankReadingEnabled: Boolean,
    hasInventory: Boolean,
): HomeActivationProgress {
    return HomeActivationProgress(
        completedSteps = buildSet {
            if (hasConfirmedBalance) add(HomeActivationStep.SET_BALANCE)
            if (bankReadingEnabled) add(HomeActivationStep.ENABLE_BANK_READING)
            if (hasInventory) add(HomeActivationStep.ADD_FIRST_INVENTORY_ITEM)
        },
    )
}
