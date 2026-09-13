package com.example.data

enum class HomeActivationStep {
    SET_BALANCE,
    ENABLE_BANK_READING,
    ADD_FIRST_INVENTORY_ITEM,
    /**
     * أول هدف حياة (agent_goals). شاشة الـOnboarding قبل تسجيل الدخول، فمفيش user_id يتسجل
     * عليه هدف هناك — عشان كده الزرع هنا بعد التسجيل. من غير هدف، المتابعة الأسبوعية وحلقة
     * التقدم (trg_agent_goal_progress) مالهمش حاجة يشتغلوا عليها: agent_goals كان صفر صفوف
     * و`set_life_goal` عمره مااتنادى (قياس 2026-09-13).
     */
    SET_FIRST_GOAL,
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
    hasActiveGoal: Boolean,
): HomeActivationProgress {
    return HomeActivationProgress(
        completedSteps = buildSet {
            if (hasConfirmedBalance) add(HomeActivationStep.SET_BALANCE)
            if (bankReadingEnabled) add(HomeActivationStep.ENABLE_BANK_READING)
            if (hasInventory) add(HomeActivationStep.ADD_FIRST_INVENTORY_ITEM)
            if (hasActiveGoal) add(HomeActivationStep.SET_FIRST_GOAL)
        },
    )
}
