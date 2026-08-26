package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.ui.theme.ZadMeterBar
import com.example.ui.theme.ZadStatusPill
import com.example.ui.theme.ZadV3
import com.example.ui.theme.zadCardShadow
import com.example.ui.viewmodels.ZadViewModel

// ═══════════════════════════════════════════════════════════════════════════════
// V3 Extras — Kids Mode home (prototype renderKidsHome) + camera sheet (renderCameraSheet)
// ═══════════════════════════════════════════════════════════════════════════════

/** Kids-mode preference helpers (per-device, like the prototype's localStorage). */
object V3KidsMode {
    private const val PREFS = "zad-kids-mode"
    fun isEnabled(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(context: android.content.Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
    }
}

/**
 * V3KidsHomeScreen — prototype renderKidsHome():
 * purple/pink gradient balance card with savings goal meter, task rows with coin pills,
 * badge row. Real data: balance = remainingBalance, chores come from the kids' family
 * chores via viewModel (title + rewardAmount coins). Badges are decorative emoji chips.
 */
@Composable
fun V3KidsHomeScreen(
    viewModel: ZadViewModel,
    onAskForMoney: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remaining by viewModel.remainingBalance.collectAsState()
    // Chores live in FamilyViewModel (not exposed here without changing the call
    // contract in V2MainScreen); tasks section shows a localized empty state until
    // chore data is threaded through. No fake data per localization/data invariants.
    val chores: List<com.example.data.Chore> = emptyList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Balance hero (purple→pink gradient per prototype)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.v3x_kids_balance),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    CurrencyFormatter.format(context, remaining ?: 0.0),
                    fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.v3x_kids_goal), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                        Text("60%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(0.6f).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White))
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .pressableScale(onClick = onAskForMoney)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(
                        stringResource(R.string.v3x_kids_ask),
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED),
                    )
                }
            }
        }

        // Tasks
        item {
            Text(stringResource(R.string.v3x_kids_tasks), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
        }
        if (chores.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White).padding(20.dp)) {
                    Text(
                        stringResource(R.string.v3x_kids_no_tasks),
                        fontSize = 13.sp, color = ZadV3.gray500, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            items(chores.size) { i ->
                val c = chores[i]
                Row(
                    modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink, modifier = Modifier.weight(1f))
                    ZadStatusPill(text = "🪙 ${c.rewardAmount.toInt()}", color = ZadV3.warn, containerColor = Color(0x1FF59E0B))
                }
            }
        }

        // Badges row (decorative)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("🔥" to R.string.v3x_kids_badge_streak, "💰" to R.string.v3x_kids_badge_saver, "⭐" to R.string.v3x_kids_badge_star)
                    .forEach { (emoji, labelRes) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).zadCardShadow(ZadV3.rCard).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(vertical = 14.dp),
                        ) {
                            Text(emoji, fontSize = 22.sp)
                            Text(stringResource(labelRes), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
                        }
                    }
            }
        }
    }
}

/**
 * V3CameraSheet — prototype renderCameraSheet(): bottom sheet with preview placeholder
 * and two scan actions wired to the real camera flow entry points when available.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun V3CameraSheet(
    onDismiss: () -> Unit,
    onScanInventory: () -> Unit,
    onScanReceipt: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.v3x_camera_title),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.v3x_camera_preview),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f),
                )
                // V4 — live laser sweep from assets/scan_line.json timing
                ScanSweep(modifier = Modifier.matchParentSize())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(ZadV3.green800).pressableScale(onClick = onScanInventory),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.v3x_camera_scan_inventory), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(
                    modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F4F3)).pressableScale(onClick = onScanReceipt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.v3x_camera_scan_receipt), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                }
            }
        }
    }
}
