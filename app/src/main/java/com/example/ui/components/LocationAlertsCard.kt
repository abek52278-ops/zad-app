package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.GroceryGeofenceManager
import com.example.ui.theme.*

/**
 * مرحلة ٥ب-٢ (docs/agent/PLAN_2026_08_06_rebuild.md) — نفس عائلة الزجاج البصرية اللي
 * ابتدت في كارت الميزانية (`ZadCardHero`): زوايا 24dp، بقعة ضوء `zadGlassBlur`، ودبوس
 * مكان "عائم" (شارة دائرية بظل حقيقي) بدل أيقونة مسطّحة.
 *
 * مرحلة ٤ (docs/agent/PLAN_2026_08_06_rebuild.md) — تفعيل تنبيهات الموقع (geofencing)
 * كان مدفون في شاشة "زاد القريب" (NearbyDealsScreen) بس. الكارت ده بيظهر أعلى HomeScreen
 * (نفس نمط NotificationPermissionCard الموجود) لحد ما المستخدم يفعّل أو يتجاهل — تجاهل
 * بيتفتكر دائماً (مش زي التذكير الدوري)، عشان مايبقاش إلحاح على ميزة اختيارية.
 *
 * تنظيف الناف — شاشة "زاد القريب" اتشالت من الدرج/قائمة المزيد (كانت غالباً بتفضل فاضية:
 * مفيش أسعار/عروض حقيقية، بس بحث جغرافي). كان التحكم في إيقاف التنبيهات بعد التفعيل
 * موجود جوّاها بس — دلوقتي الكارت ده نفسه بيتحول لصف تحكم مصغّر لما تكون التنبيهات
 * مفعّلة (بدل ما يختفي تماماً)، عشان المستخدم يقدر يوقفها من غير الشاشة اللي اتشالت.
 */
@Composable
fun LocationAlertsCard(dismissed: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var enabled by remember { mutableStateOf(GroceryGeofenceManager.isEnabled(context)) }

    fun activateGeofencing() {
        GroceryGeofenceManager.setEnabled(context, true)
        enabled = true
        androidx.work.WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<com.example.workers.GeofenceRefreshWorker>().build()
        )
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) activateGeofencing()
    }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (!granted) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            activateGeofencing()
        }
    }

    fun onEnableClick() {
        if (!hasLocationPermission) {
            foregroundLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !GroceryGeofenceManager.hasBackgroundLocationPermission(context)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            activateGeofencing()
        }
    }

    if (enabled) {
        // صف تحكم مصغّر — المكان الوحيد دلوقتي لإيقاف التنبيهات بعد ما شاشة "زاد القريب"
        // اتشالت من الناف.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceContainerLow)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.location_alerts_toggle_label), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = onSurface)
                Text(stringResource(R.string.location_alerts_enabled_hint), fontSize = 11.sp, color = onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            ZadSwitch(
                checked = true,
                onCheckedChange = {
                    if (!it) {
                        GroceryGeofenceManager.setEnabled(context, false)
                        enabled = false
                    }
                }
            )
        }
        return
    }

    if (dismissed) return

    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(primary.copy(alpha = 0.10f), tertiary.copy(alpha = 0.07f))
                )
            )
    ) {
        // نفس بقعة الضوء الزجاجية بتاعة ZadCardHero — عائلة بصرية واحدة عبر الكروت
        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopStart)
                .offset(x = (-26).dp, y = (-26).dp)
                .zadGlassBlur(30.dp)
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // دبوس مكان عائم — شارة دائرية بظل حقيقي بدل أيقونة مسطّحة على الخلفية
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = primary.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.location_alerts_toggle_label), fontWeight = FontWeight.Bold, color = onSurface)
                    Text(stringResource(R.string.location_alerts_toggle_hint), fontSize = 12.sp, color = onSurfaceVariant, lineHeight = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onEnableClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.enable), fontSize = 12.sp) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(4.dp)) {
                    Text(stringResource(R.string.dismiss_action), fontSize = 11.sp, color = onSurfaceVariant)
                }
            }
        }
    }
}
