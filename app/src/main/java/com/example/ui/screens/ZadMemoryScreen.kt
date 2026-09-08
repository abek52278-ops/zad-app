package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SupabaseRepo
import com.example.data.ZadMemoryNote
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadLoadingState
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * "زاد عارف عني إيه" — شفافية الذاكرة طويلة الأمد (zad_memory). عرض + نسيان (حذف) بس،
 * زي فيتشر الذاكرة في ChatGPT — مفيش تعديل نص الملاحظة نفسها هنا (خطر حقن نص حر يترجع
 * يتقرا كسياق موثوق بعدين)، لو العميل مش موافق على ملاحظة يقدر ينساها بس مش يعدّلها.
 *
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط AgentActionLogScreen: قراءة/كتابة Supabase
 * مباشرة من غير أي نداء LLM هنا، فمفيش داعي لدورة حياة ViewModel كاملة.
 */
@Composable
fun ZadMemoryScreen(onBack: () -> Unit) {
    var notes by remember { mutableStateOf<List<ZadMemoryNote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ZadMemoryNote?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // لازم تتحل هنا (سياق composable) — showSnackbar بيشتغل جوه coroutine، وstringResource
    // مش composable function ينفع يتنده هناك.
    val deletedMessage = stringResource(R.string.zad_memory_deleted_snackbar)
    val deleteFailedMessage = stringResource(R.string.zad_memory_delete_failed_snackbar)

    suspend fun refresh() {
        loading = true
        try {
            notes = SupabaseRepo.getMemoryNotes(limit = 200)
        } catch (e: Exception) {
            android.util.Log.e("ZadMemoryScreen", "Failed to get memory notes: ${e.message}")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun onConfirmDelete(note: ZadMemoryNote) {
        val id = note.id ?: return
        pendingDelete = null
        deletingId = id
        scope.launch {
            val ok = SupabaseRepo.deleteMemoryNote(id)
            deletingId = null
            if (ok) {
                notes = notes.filterNot { it.id == id }
                snackbarHostState.showSnackbar(deletedMessage)
            } else {
                snackbarHostState.showSnackbar(deleteFailedMessage)
            }
        }
    }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.zad_memory_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
        }
    ) { padding ->
        when {
            loading -> ZadLoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            notes.isEmpty() -> ZadEmptyState(
                icon = Icons.Default.Psychology,
                title = stringResource(R.string.zad_memory_empty_title),
                subtitle = stringResource(R.string.zad_memory_empty_subtitle),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, com.example.ui.theme.ZadHubListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes, key = { it.id ?: it.note }) { note ->
                    ZadMemoryRow(
                        note = note,
                        isDeleting = deletingId == note.id,
                        onDeleteClick = { pendingDelete = note }
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.zad_memory_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zad_memory_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(note) }) {
                    Text(stringResource(R.string.zad_memory_delete_action), color = dangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ZadMemoryRow(
    note: ZadMemoryNote,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.example.ui.theme.ZadLuxe.squircle)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, com.example.ui.theme.ZadLuxe.squircle)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                note.note,
                style = Typography.bodyMedium.copy(lineHeight = 20.sp),
                color = onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryLight.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(note.scope, style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Medium)
                }
                if (note.evidenceCount > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.zad_memory_evidence_count, note.evidenceCount),
                        style = Typography.labelSmall,
                        color = onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDeleteClick, enabled = !isDeleting) {
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Close, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
