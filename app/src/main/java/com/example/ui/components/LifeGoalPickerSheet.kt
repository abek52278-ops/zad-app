package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LifeGoalPreset
import com.example.data.LifeGoalSeeds
import com.example.data.SupabaseRepo
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * خطوة «حدد أول هدف لبيتك» — مرحلة ٢ بند ٢.
 *
 * بتسجّل الهدف عن طريق `zad_seed_life_goal` مباشرة (حتمي)، مش عن طريق طلب للموديل زي حوار
 * «هدف جديد» في البروفايل: `set_life_goal` عمره مااتنادى فعليًا (صفر صف في agent_actions).
 * الدالة بتربط بالهدف متابعة أسبوعية، فالمتابعة بتوصل تليجرام وحلقة التقدم بتبدأ فورًا.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeGoalPickerSheet(onDismiss: () -> Unit, onGoalSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        LifeGoalPickerContent(
            saving = saving,
            errorCode = errorCode,
            onSubmit = { seed ->
                saving = true
                errorCode = null
                scope.launch {
                    val result = SupabaseRepo.seedLifeGoal(seed)
                    saving = false
                    if (result.ok) onGoalSaved() else errorCode = result.error ?: "unknown"
                }
            },
        )
    }
}

/**
 * محتوى الشيت من غير الشيت نفسه — عشان يتصوّر في Roborazzi (ModalBottomSheet مابيترسمش
 * مستقر في Robolectric) وعشان منطق الاختيار يبان منفصل عن النداء.
 */
@Composable
fun LifeGoalPickerContent(
    saving: Boolean,
    errorCode: String?,
    onSubmit: (com.example.data.LifeGoalSeed) -> Unit,
    modifier: Modifier = Modifier,
    initialPreset: LifeGoalPreset = LifeGoalPreset.SAVE_MONTHLY,
    initialAmount: String = "",
) {
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var amountText by rememberSaveable { mutableStateOf(initialAmount) }
    var customText by rememberSaveable { mutableStateOf("") }

    val seed = remember(preset, amountText, customText) {
        LifeGoalSeeds.build(
            preset = preset,
            amount = amountText.trim().replace(',', '.').toDoubleOrNull(),
            customTitle = customText,
            today = LocalDate.now(),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.life_goal_sheet_title),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
            Text(
                stringResource(R.string.life_goal_sheet_subtitle),
                style = Typography.bodySmall,
                color = onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetRow(LifeGoalPreset.SAVE_MONTHLY, preset, Icons.Default.Savings,
                R.string.life_goal_preset_save_title, R.string.life_goal_preset_save_desc) { preset = it }
            if (preset == LifeGoalPreset.SAVE_MONTHLY) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                    label = { Text(stringResource(R.string.life_goal_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PresetRow(LifeGoalPreset.STICK_TO_BUDGET, preset, Icons.Default.AccountBalanceWallet,
                R.string.life_goal_preset_budget_title, R.string.life_goal_preset_budget_desc) { preset = it }
            PresetRow(LifeGoalPreset.PAY_OFF_DEBTS, preset, Icons.Default.CreditCard,
                R.string.life_goal_preset_debts_title, R.string.life_goal_preset_debts_desc) { preset = it }
            PresetRow(LifeGoalPreset.REDUCE_WASTE, preset, Icons.Default.Kitchen,
                R.string.life_goal_preset_waste_title, R.string.life_goal_preset_waste_desc) { preset = it }
            PresetRow(LifeGoalPreset.CUSTOM, preset, Icons.Default.Edit,
                R.string.life_goal_preset_custom_title, R.string.life_goal_preset_custom_desc) { preset = it }
            if (preset == LifeGoalPreset.CUSTOM) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.take(200) },
                    label = { Text(stringResource(R.string.life_goal_custom_label)) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (errorCode != null) {
            Text(
                stringResource(
                    if (errorCode == "too_many_active_goals") R.string.life_goal_error_too_many
                    else R.string.life_goal_error_generic
                ),
                style = Typography.bodySmall,
                color = dangerColor,
            )
        }

        Button(
            onClick = { seed?.let(onSubmit) },
            enabled = seed != null && !saving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onPrimary)
            } else {
                Text(stringResource(R.string.life_goal_save), style = Typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PresetRow(
    value: LifeGoalPreset,
    selected: LifeGoalPreset,
    icon: ImageVector,
    titleRes: Int,
    descRes: Int,
    onSelect: (LifeGoalPreset) -> Unit,
) {
    val isSelected = value == selected
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) primaryContainer else surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) primary else outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) primary else onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(titleRes), style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) onPrimaryContainer else onSurface)
                Text(stringResource(descRes), style = Typography.bodySmall,
                    color = if (isSelected) onPrimaryContainer else onSurfaceVariant)
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}
