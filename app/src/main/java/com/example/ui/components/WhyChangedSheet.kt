package com.example.ui.components

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SupabaseRepo
import com.example.ui.theme.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Task 27.2 — "ليه الرقم اتغيّر؟". طول الضغط على رقم الميزانية بيفتح الشيت ده، اللي بيعرض
 * آخر تعديلات زاد-برين الفعلية (zad_brain_runs.mutations) — أقرب حاجة موجودة فعلاً لمفهوم
 * الدوك "mutations + ZadIngest source + user edits" (ZadIngest نفسها لسه مبنيتش، Task 12).
 * بيعرض كل التعديلات الأخيرة، مش مفلترة على رقم بعينه — الـ schema الحالي مش بيربط كل
 * mutation برقم واجهة معين، فده تقريب مقصود ومعروف، موثّق في PROGRESS.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyChangedSheet(onDismiss: () -> Unit) {
    var mutations by remember { mutableStateOf<List<SupabaseRepo.BrainMutationEntry>?>(null) }

    LaunchedEffect(Unit) {
        mutations = SupabaseRepo.getRecentBrainMutations()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(stringResource(R.string.auto_comp_whychangedsheet_38518), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.auto_comp_whychangedsheet_19473),
                style = Typography.bodySmall,
                color = onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                mutations == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                mutations!!.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.auto_comp_whychangedsheet_94891), style = Typography.bodyMedium, color = onSurfaceVariant)
                }
                else -> mutations!!.forEach { m ->
                    MutationRow(m)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MutationRow(m: SupabaseRepo.BrainMutationEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(m.toolLabel, style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
            if (m.old != null || m.new != null) {
                Text(
                    "${m.old ?: "—"} ← ${m.new ?: "—"}",
                    style = Typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
        }
        Text(relativeTime(m.at), style = Typography.labelSmall, color = onSurfaceVariant)
    }
}

private fun relativeTime(iso: String): String {
    val then = try { Instant.parse(iso) } catch (e: Exception) { return "" }
    val minutes = ChronoUnit.MINUTES.between(then, Instant.now())
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes د"
        minutes < 1440 -> "منذ ${minutes / 60} س"
        else -> "منذ ${minutes / 1440} يوم"
    }
}
