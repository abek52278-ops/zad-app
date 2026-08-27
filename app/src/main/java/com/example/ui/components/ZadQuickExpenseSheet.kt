package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

/**
 * Quick Expense Bottom Sheet — شيفو's design.
 *
 * Three fields: Name + Query + Total → Send button.
 * Calls onSend(name, query, amount) with real data; no mock values.
 * Caller is responsible for wiring this to ZadViewModel.logExpense().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadQuickExpenseSheet(
    onDismiss: () -> Unit,
    onSend: (name: String, query: String, amount: Double) -> Unit,
) {
    var name   by remember { mutableStateOf("") }
    var query  by remember { mutableStateOf("") }
    var total  by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val nameFocus   = remember { FocusRequester() }
    val queryFocus  = remember { FocusRequester() }
    val totalFocus  = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isValid = name.isNotBlank() && total.toDoubleOrNull() != null && total.toDouble() > 0

    fun submit() {
        if (!isValid || sending) return
        sending = true
        onSend(name.trim(), query.trim(), total.toDouble())
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFE5E7EB))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Send, null, tint = primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.quick_expense_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        stringResource(R.string.quick_expense_subtitle),
                        fontSize = 12.sp,
                        color = textTertiary
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF3F4F6))

            // Name field
            QuickExpenseField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.quick_expense_name),
                placeholder = stringResource(R.string.quick_expense_name_hint),
                focusRequester = nameFocus,
                imeAction = ImeAction.Next,
                onNext = { queryFocus.requestFocus() }
            )

            // Query / category field
            QuickExpenseField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.quick_expense_query),
                placeholder = stringResource(R.string.quick_expense_query_hint),
                focusRequester = queryFocus,
                imeAction = ImeAction.Next,
                onNext = { totalFocus.requestFocus() }
            )

            // Total field
            QuickExpenseField(
                value = total,
                onValueChange = { total = it },
                label = stringResource(R.string.quick_expense_total),
                placeholder = "0.00",
                focusRequester = totalFocus,
                imeAction = ImeAction.Send,
                keyboardType = KeyboardType.Decimal,
                onNext = { focusManager.clearFocus(); submit() }
            )

            Spacer(Modifier.height(4.dp))

            // Send button
            Button(
                onClick = { focusManager.clearFocus(); submit() },
                enabled = isValid && !sending,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    disabledContainerColor = primary.copy(alpha = 0.3f)
                )
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.quick_expense_send),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}

@Composable
private fun QuickExpenseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    focusRequester: FocusRequester,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Text,
    onNext: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = textTertiary, fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primary,
                unfocusedBorderColor = Color(0xFFE5E7EB),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFF9FAFB),
                cursorColor = primary
            ),
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
                onSend = { onNext() },
                onDone = { onNext() }
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    }
}
