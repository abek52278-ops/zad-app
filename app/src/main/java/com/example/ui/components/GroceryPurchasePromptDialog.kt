package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadInventory
import com.example.data.ZadTransaction
import com.example.ui.theme.*

/**
 * مرحلة ٣ (docs/agent/PLAN_2026_08_06_rebuild.md) — معاملة بقالة جديدة (بنكية أو يدوية)
 * كانت بتتصنّف وتقف عند كده، من غير أي وصل بالمخزون خالص. الدايلوج ده بيسأل فوراً "ضيف
 * إيه؟" — شيبس من أصناف المخزون الحالية (تاب = +1) زائد حقل نص لصنف جديد. تاب على شيب
 * ميقفلش الدايلوج، عشان يقدر يضيف كذا صنف من نفس عملية الشراء؛ "تم" بس اللي بيقفل.
 *
 * كل إضافة بتعيد استخدام InventoryFlowEngine.injectScannedItems (عبر
 * ZadViewModel.addGroceryPurchaseItem) — نفس منطق حقن فاتورة مصوّرة بالظبط، مفيش مسار
 * مواز بمنطق مختلف.
 */
@Composable
fun GroceryPurchasePromptDialog(
    transaction: ZadTransaction,
    inventory: List<ZadInventory>,
    onAddItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newItemName by remember { mutableStateOf("") }
    var addedNames by remember { mutableStateOf(setOf<String>()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.grocery_purchase_prompt_title,
                    transaction.merchantName ?: transaction.title,
                    CurrencyFormatter.format(context, transaction)
                ),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (inventory.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(inventory.take(20), key = { it.id }) { item ->
                            val added = item.itemName in addedNames
                            AssistChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LightClick)
                                    onAddItem(item.itemName)
                                    addedNames = addedNames + item.itemName
                                },
                                label = { Text(item.itemName) },
                                leadingIcon = {
                                    Icon(
                                        if (added) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.height(16.dp)
                                    )
                                },
                                colors = if (added) AssistChipDefaults.assistChipColors(
                                    containerColor = primary.copy(alpha = 0.12f),
                                    labelColor = primary
                                ) else AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        placeholder = { Text(stringResource(R.string.grocery_purchase_new_item_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newItemName.isNotBlank()) {
                            onAddItem(newItemName.trim())
                            addedNames = addedNames + newItemName.trim()
                            newItemName = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.grocery_purchase_add_action), tint = primary)
                    }
                }
                if (addedNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.grocery_purchase_added_count, addedNames.size),
                        style = Typography.labelSmall,
                        color = successColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.grocery_purchase_done_action)) }
        }
    )
}
