package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Market
import com.example.ui.theme.*

/**
 * مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — اقتراح تحويل السوق لما
 * `TravelDetector` يكتشف إن بلد الشبكة الحالي مختلف عن السوق المختار في التطبيق.
 * اقتراح بس — زرار "تحويل" صريح وزرار تجاهل، مفيش تبديل صامت لعملة المستخدم.
 */
@Composable
fun TravelBanner(suggestedMarket: Market, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(primary.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            stringResource(R.string.travel_banner_message, suggestedMarket.displayNameAr, suggestedMarket.currencySymbol),
            style = Typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSwitch) {
            Text(stringResource(R.string.travel_banner_action), color = primary, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss_action), tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
