package com.example.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurfaceVariant

/**
 * Task 28 (PRODUCT_PLAN.md) — "informative dismissal". Replaces a plain dismiss button
 * with a 3-option choice; the reason is more valuable than the fact. "الرقم غلط" is
 * rendered in the danger color deliberately — it's a free bug report and the option
 * least likely to get tapped by accident should stand out, not blend in.
 */
@Composable
fun DismissReasonMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onReasonSelected: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text("مش مهم") },
            onClick = { onReasonSelected("not_relevant"); onDismissRequest() }
        )
        DropdownMenuItem(
            text = { Text("الرقم غلط", color = dangerColor) },
            onClick = { onReasonSelected("wrong_data"); onDismissRequest() }
        )
        DropdownMenuItem(
            text = { Text("عرفت خلاص", color = onSurfaceVariant) },
            onClick = { onReasonSelected("timing"); onDismissRequest() }
        )
    }
}
