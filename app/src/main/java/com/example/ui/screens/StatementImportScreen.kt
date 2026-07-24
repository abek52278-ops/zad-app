package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StatementCsvImporter
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * استيراد كشف حساب CSV — 3 خطوات: اختيار ملف، تحديد الأعمدة يدوياً (بدون تخمين
 * تلقائي — كل بنك بيصدّر بتنسيق مختلف)، ثم مراجعة كل صف قبل الاستيراد الفعلي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(onOpenDrawer: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var csvTable by remember { mutableStateOf<StatementCsvImporter.CsvTable?>(null) }
    var dateColIdx by remember { mutableStateOf<Int?>(null) }
    var titleColIdx by remember { mutableStateOf<Int?>(null) }
    var amountColIdx by remember { mutableStateOf<Int?>(null) }
    var categoryColIdx by remember { mutableStateOf<Int?>(null) }
    var invertSign by remember { mutableStateOf(false) }
    var previewRows by remember { mutableStateOf<List<StatementCsvImporter.PreviewRow>>(emptyList()) }
    var checkedRows by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isImporting by remember { mutableStateOf(false) }
    var readFailedMsg by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val table = StatementCsvImporter.readCsv(context, uri)
            if (table != null && table.headers.isNotEmpty()) {
                csvTable = table
                dateColIdx = null; titleColIdx = null; amountColIdx = null; categoryColIdx = null
                step = 1
            } else {
                readFailedMsg = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu), tint = onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.statement_import_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
            }

            when (step) {
                0 -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = primary, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.statement_import_intro), style = Typography.bodyMedium, color = onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))
                    if (readFailedMsg) {
                        Text(stringResource(R.string.statement_read_failed), color = dangerColor, style = Typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Button(
                        onClick = { readFailedMsg = false; filePicker.launch("*/*") },
                        modifier = Modifier.pressableScale(),
                        shape = RoundedCornerShape(50)
                    ) { Text(stringResource(R.string.pick_csv_file_action)) }
                }

                1 -> csvTable?.let { table ->
                    ColumnMappingStep(
                        headers = table.headers,
                        dateColIdx = dateColIdx, onDateColChange = { dateColIdx = it },
                        titleColIdx = titleColIdx, onTitleColChange = { titleColIdx = it },
                        amountColIdx = amountColIdx, onAmountColChange = { amountColIdx = it },
                        categoryColIdx = categoryColIdx, onCategoryColChange = { categoryColIdx = it },
                        invertSign = invertSign, onInvertSignChange = { invertSign = it },
                        onConfirm = {
                            val mapping = StatementCsvImporter.ColumnMapping(
                                dateColumnIndex = dateColIdx!!,
                                titleColumnIndex = titleColIdx!!,
                                amountColumnIndex = amountColIdx!!,
                                invertSign = invertSign,
                                categoryColumnIndex = categoryColIdx
                            )
                            val rows = StatementCsvImporter.buildPreview(context, table, mapping)
                            previewRows = rows
                            checkedRows = rows.filter { !it.hasError }.map { it.rowIndex }.toSet()
                            step = 2
                        }
                    )
                }

                2 -> PreviewStep(
                    rows = previewRows,
                    checkedRows = checkedRows,
                    onToggleRow = { idx, checked ->
                        checkedRows = if (checked) checkedRows + idx else checkedRows - idx
                    },
                    isImporting = isImporting,
                    onConfirmImport = {
                        isImporting = true
                        scope.launch {
                            val selected = previewRows.filter { it.rowIndex in checkedRows }
                            val (imported, skipped) = StatementCsvImporter.commitImport(context, selected)
                            isImporting = false
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.statement_import_done_toast, imported, skipped),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            step = 0
                            csvTable = null
                            previewRows = emptyList()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnMappingStep(
    headers: List<String>,
    dateColIdx: Int?, onDateColChange: (Int) -> Unit,
    titleColIdx: Int?, onTitleColChange: (Int) -> Unit,
    amountColIdx: Int?, onAmountColChange: (Int) -> Unit,
    categoryColIdx: Int?, onCategoryColChange: (Int) -> Unit,
    invertSign: Boolean, onInvertSignChange: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.map_columns_hint), style = Typography.bodyMedium, color = onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        ColumnPicker(stringResource(R.string.date_column_label), headers, dateColIdx, onDateColChange)
        Spacer(modifier = Modifier.height(12.dp))
        ColumnPicker(stringResource(R.string.title_column_label), headers, titleColIdx, onTitleColChange)
        Spacer(modifier = Modifier.height(12.dp))
        ColumnPicker(stringResource(R.string.amount_column_label), headers, amountColIdx, onAmountColChange)
        Spacer(modifier = Modifier.height(12.dp))
        ColumnPicker(stringResource(R.string.category_column_optional_label), headers, categoryColIdx, onCategoryColChange, optional = true)

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.invert_amount_sign_label), style = Typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = invertSign, onCheckedChange = onInvertSignChange)
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onConfirm,
            enabled = dateColIdx != null && titleColIdx != null && amountColIdx != null,
            modifier = Modifier.fillMaxWidth().pressableScale(),
            shape = RoundedCornerShape(50)
        ) { Text(stringResource(R.string.continue_to_preview_action)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnPicker(label: String, headers: List<String>, selected: Int?, onSelect: (Int) -> Unit, optional: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selected?.let { headers.getOrNull(it) } ?: if (optional) stringResource(R.string.none_option) else ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(text = { Text(header.ifBlank { "عمود ${index + 1}" }) }, onClick = { onSelect(index); expanded = false })
            }
        }
    }
}

@Composable
private fun PreviewStep(
    rows: List<StatementCsvImporter.PreviewRow>,
    checkedRows: Set<Int>,
    onToggleRow: (Int, Boolean) -> Unit,
    isImporting: Boolean,
    onConfirmImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.preview_rows_count_hint, rows.size, checkedRows.size),
            style = Typography.bodySmall, color = onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.rowIndex }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (row.hasError) dangerColor.copy(alpha = 0.06f) else surface)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = row.rowIndex in checkedRows,
                            onCheckedChange = { onToggleRow(row.rowIndex, it) },
                            enabled = !row.hasError
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (row.hasError) stringResource(R.string.row_parse_error_hint) else "${row.date} · ${row.category}",
                                style = Typography.labelSmall, color = if (row.hasError) dangerColor else onSurfaceVariant
                            )
                        }
                        if (row.amount != null) {
                            Text(
                                "${if (row.isExpense) "-" else "+"}${"%.2f".format(row.amount)}",
                                style = Typography.bodyMedium, fontWeight = FontWeight.Bold,
                                color = if (row.isExpense) dangerColor else successColor
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = onConfirmImport,
            enabled = checkedRows.isNotEmpty() && !isImporting,
            modifier = Modifier.fillMaxWidth().padding(16.dp).pressableScale(),
            shape = RoundedCornerShape(50)
        ) { Text(stringResource(R.string.import_selected_rows_action, checkedRows.size)) }
    }
}
