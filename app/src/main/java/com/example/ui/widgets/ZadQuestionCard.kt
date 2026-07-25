package com.example.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ZadInsight
import com.example.ui.theme.*

/**
 * zad-brain can ask (kind="question", answer_type: number|yes_no|camera — see
 * zad-brain/validators.ts validateAskUser) but until this component existed there was no
 * way to actually answer: Home/bell only rendered a title+body+dismiss button. This is the
 * missing other half — reusable wherever a ZadInsight of kind "question" needs to render.
 */
@Composable
fun ZadQuestionCard(
    insight: ZadInsight,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenCamera: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(secondary.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = secondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(insight.title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Text(insight.body, style = Typography.bodySmall, color = onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (insight.actionType) {
            "yes_no" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAnswer("أيوة") }, modifier = Modifier.height(32.dp)) {
                        Text("أيوة", style = Typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onAnswer("لأ") }, modifier = Modifier.height(32.dp)) {
                        Text("لأ", style = Typography.labelSmall)
                    }
                }
            }
            "number" -> {
                var value by remember(insight.id) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { if (it.all { c -> c.isDigit() }) value = it },
                        modifier = Modifier.weight(1f).height(56.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Button(
                        onClick = { value.toIntOrNull()?.let { onAnswer(it.toString()) } },
                        enabled = value.toIntOrNull() != null,
                        modifier = Modifier.height(40.dp)
                    ) { Text("إرسال", style = Typography.labelSmall) }
                }
            }
            "camera" -> {
                Button(onClick = { onOpenCamera?.invoke() }, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افتح الكاميرا", style = Typography.labelSmall)
                }
            }
            else -> {
                // Unknown/missing answer_type — still let the client answer as free text
                // rather than dead-end on a question with no way to respond at all.
                var value by remember(insight.id) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true)
                    Button(onClick = { if (value.isNotBlank()) onAnswer(value) }, modifier = Modifier.height(40.dp)) {
                        Text("إرسال", style = Typography.labelSmall)
                    }
                }
            }
        }
    }
}
