package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.ui.viewmodels.ZadViewModel

/**
 * شاشة باقات واشتراكات زاد بريميوم الرسمية (Zad VIP)
 * متصلة بـ Google Play In-App Purchase و 3 باقات شهرية
 */
@Composable
fun SubscriptionPlansScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit = {}
) {
    ZadSubscriptionPaywallScreen(
        viewModel = viewModel,
        onBack = onBack
    )
}
