package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * مرحلة ٤ (docs/agent/PLAN_2026_08_06_rebuild.md) — تفعيل تنبيهات الموقع (geofencing)
 * كان مدفون في NearbyDealsScreen بس، مفيش مكان تاني يعرّف المستخدم إن الميزة دي موجودة
 * أصلاً غير لو دخل شاشة "زاد القريب" بنفسه. الكارت ده بيظهر أعلى HomeScreen (نفس نمط
 * NotificationPermissionCard الموجود) لحد ما المستخدم يفعّل أو يتجاهل — تجاهل بيتفتكر
 * دائماً (مش زي التذكير الدوري)، عشان مايبقاش إلحاح على ميزة اختيارية.
 *
 * التحكم في إيقافها بعد التفعيل لسه في NearbyDealsScreen بس — الكارت ده مسؤوليته الدعوة
 * الأولى بس، مش لوحة تحكم كاملة.
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

    if (enabled || dismissed) return

    ZadListCard(containerColor = primary.copy(alpha = 0.08f), contentPadding = 0.dp) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.location_alerts_toggle_label), fontWeight = FontWeight.Bold, color = onSurface)
                Text(stringResource(R.string.location_alerts_toggle_hint), fontSize = 12.sp, color = onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onEnableClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(stringResource(R.string.enable), fontSize = 12.sp) }
                Spacer(modifier = Modifier.width(4.dp))
                androidx.compose.material3.TextButton(onClick = onDismiss, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.dismiss_action), fontSize = 11.sp, color = onSurfaceVariant)
                }
            }
        }
    }
}
