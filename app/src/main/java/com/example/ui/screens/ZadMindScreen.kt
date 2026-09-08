package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.ui.viewmodels.ZadViewModel

/**
 * شاشة "عقل زاد" (Zad Mind Graph Visualizer) — واجهة المراقبة العصبية والاستخباراتية التفاعلية
 * بنمط Cyberpunk / Sci-Fi Intelligence Dashboard عالي الكثافة البيانية.
 */
@Composable
fun ZadMindScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {}
) {
    ZadKnowledgeMapScreen(
        viewModel = viewModel,
        onBack = onBack,
        onNavigateToRoute = onNavigateToRoute
    )
}
