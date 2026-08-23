package com.example.ui.components

import com.example.R
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
            text = { Text(stringResource(R.string.auto_comp_dismissreasonmenu_4986),) },
            onClick = { onReasonSelected("not_relevant"); onDismissRequest() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.auto_comp_dismissreasonmenu_23264), color = dangerColor) },
            onClick = { onReasonSelected("wrong_data"); onDismissRequest() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.auto_comp_dismissreasonmenu_40574), color = onSurfaceVariant) },
            onClick = { onReasonSelected("timing"); onDismissRequest() }
        )
    }
}
