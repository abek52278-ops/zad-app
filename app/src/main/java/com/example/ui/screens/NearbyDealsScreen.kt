package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NearbyStore
import com.example.ui.components.ZadListCard
import com.example.ui.components.pressableScale
import com.example.ui.theme.*

// شاشة "زاد القريب" (NearbyDealsScreen) نفسها اتشالت من الناف (درج + قائمة المزيد) —
// كانت غالباً بتفضل فاضية (مفيش أسعار/عروض حقيقية، بس بحث جغرافي عبر Overpass)، وتحكم
// إيقاف تنبيهات الموقع بعد التفعيل نقل لـ LocationAlertsCard على الهوم. الـ geofencing
// نفسه (GroceryGeofenceManager) فاضل شغال زي ما هو تماماً — مالوش أي علاقة بالشاشة دي.
//
// NearbyStoreCard فاضل هنا لوحده لأنه مكوّن مستقل ومغطى بـ Roborazzi screenshot tests
// (PreviewTest.kt).
@Composable
internal fun NearbyStoreCard(store: NearbyStore, lowStockNames: List<String>, isPharmacy: Boolean = false) {
    // مرحلة ٥ب-٢ (docs/agent/PLAN_2026_08_06_rebuild.md) — 24dp بدل 16dp، نفس نصف قطر
    // كارت الميزانية وكارت تنبيهات الموقع، عائلة بصرية واحدة عبر التطبيق
    val cardShape = RoundedCornerShape(24.dp)
    ZadListCard(
        modifier = Modifier.pressableScale(),
        shape = cardShape,
        contentPadding = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // دبوس مكان عائم — ظل حقيقي تحت الشارة بدل دائرة لون مسطّحة، إحساس "معلّق"
            // فوق الخريطة مش مجرد أيقونة تصنيف
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = 8.dp, shape = CircleShape,
                        spotColor = (if (isPharmacy) catHealthIcon else catFoodIcon).copy(alpha = 0.45f)
                    )
                    .clip(CircleShape)
                    .background(if (isPharmacy) catHealthBg else catFoodBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPharmacy) Icons.Default.LocalPharmacy else Icons.Default.Storefront,
                    contentDescription = null, tint = if (isPharmacy) catHealthIcon else catFoodIcon, modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(store.name, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NearMe, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        if (store.distanceMeters < 1000) "${store.distanceMeters} م" else "${"%.1f".format(store.distanceMeters / 1000.0)} كم",
                        style = Typography.labelSmall, color = onSurfaceVariant
                    )
                }
                if (lowStockNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            if (isPharmacy) R.string.refill_needed_reminder_hint else R.string.low_stock_reminder_hint,
                            lowStockNames.take(3).joinToString("، ")
                        ),
                        style = Typography.labelSmall, color = if (isPharmacy) dangerColor else primary, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
