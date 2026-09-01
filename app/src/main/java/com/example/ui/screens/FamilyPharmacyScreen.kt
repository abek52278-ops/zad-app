package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.FamilyMember
import com.example.data.SupabaseRepo
import com.example.data.ZadPharmacyItem
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.ZadLoadingState
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*

/**
 * "أدوية العيلة" — رؤية بس (family_admin_read_pharmacy migration، 2026-09-01)، متاحة
 * للوالدين (role=admin) بس. مفيش تعديل/حذف هنا عن قصد: الدوا شخصي وحساس، على عكس
 * المخزون العام (Task 30) اللي اندمج فعليًا — القرار هنا كان رؤية، مش دمج.
 *
 * حالة محلية بسيطة (مش ViewModel) — نفس نمط ZadMemoryScreen/AgentActionLogScreen.
 */
@Composable
fun FamilyPharmacyScreen(members: List<FamilyMember>, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<ZadPharmacyItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        items = SupabaseRepo.getFamilyPharmacyItems()
        loading = false
    }

    val byMember = remember(items, members) {
        members.associateWith { member -> items.filter { it.userId == member.userId } }
            .filterValues { it.isNotEmpty() }
    }

    Scaffold(
        containerColor = background,
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
                    stringResource(R.string.family_pharmacy_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
        }
    ) { padding ->
        when {
            loading -> ZadLoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            byMember.isEmpty() -> ZadEmptyState(
                icon = Icons.Default.LocalPharmacy,
                title = stringResource(R.string.family_pharmacy_empty_title),
                subtitle = stringResource(R.string.family_pharmacy_empty_subtitle),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                byMember.forEach { (member, memberItems) ->
                    item(key = "header_${member.id}") { FamilyPharmacyMemberHeader(member) }
                    items(memberItems, key = { it.id }) { med -> FamilyPharmacyItemRow(med) }
                }
            }
        }
    }
}

@Composable
private fun FamilyPharmacyMemberHeader(member: FamilyMember) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                member.alias.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟",
                style = Typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(member.alias, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
    }
}

@Composable
private fun FamilyPharmacyItemRow(item: ZadPharmacyItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = onSurface)
            if (!item.dosage.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.dosage, style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
        val lowStock = item.isLowStock()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background((if (lowStock) dangerColor else successColor).copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "${item.remainingQuantity} ${item.unit}",
                style = Typography.labelSmall,
                color = if (lowStock) dangerColor else successColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
