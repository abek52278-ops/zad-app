package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.GroceryGeofenceManager
import com.example.data.NearbyStore
import com.example.data.OverpassRepo
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadListCard
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

/**
 * زاد القريب: يفحص السوبرماركتس القريبة (OpenStreetMap/Overpass، مجاني)
 * ويقارنها بالمخزون الناقص عندك — فحص لحظي وقت فتح الشاشة (foreground only)،
 * مفيش مراقبة موقع في الخلفية ومفيش إذن ACCESS_BACKGROUND_LOCATION لهذا الجزء تحديداً.
 *
 * ملاحظة صدق: مفيش عروض/أسعار حقيقية هنا — لا يوجد API أسعار متاجر متاح
 * في المشروع، فالميزة بتقارن القرب الجغرافي بس بنواقص مخزونك.
 *
 * الـ toggle تحت ("تنبيهات ذكية") ميزة منفصلة تماماً: geofencing حقيقي وopt-in،
 * محتاج ACCESS_BACKGROUND_LOCATION — انظر GroceryGeofenceManager.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyDealsScreen(
    viewModel: ZadViewModel
) {
    val context = LocalContext.current
    val inventory by viewModel.inventory.collectAsState()
    val pharmacyItems by viewModel.pharmacyItems.collectAsState()
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var stores by remember { mutableStateOf<List<NearbyStore>>(emptyList()) }
    var pharmacies by remember { mutableStateOf<List<NearbyStore>>(emptyList()) }
    var searchError by remember { mutableStateOf(false) }
    // This screen only ever showed a static hint text while browsing — no alert
    // ever reached the brain or the user outside this screen. One event trigger
    // per successful search reports the real match so zad-brain can reason about
    // it and (if worth surfacing) emit a real alert to Home/bell/voice.
    var lastReportedSearch by remember { mutableStateOf(false) }

    val lowStockNames = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }.map { it.itemName }
    val refillNeededMeds = pharmacyItems.filter { val d = it.daysOfSupplyLeft(); d != null && d <= 5 }.map { it.name }

    fun searchNearby() {
        isLoading = true
        searchError = false
        scope.launch {
            // مرحلة ٤ — FusedLocationProviderClient بدل LocationManager.getLastKnownLocation()
            // (كاش ممكن يبقى فاضي أو قديم بالساعات)؛ Tasks.await() جوّاها بلوكينج فلازم IO
            val location = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.LocationHelper.getCurrentLocation(context)
            }
            if (location == null) {
                isLoading = false
                searchError = true
                return@launch
            }
            stores = OverpassRepo.findNearbySupermarkets(location.latitude, location.longitude)
            // نجيب الصيدليات بس لو فعلاً فيه دواء قرب يخلص — مفيش داعي نستهلك API لغرض مفيدش
            pharmacies = if (refillNeededMeds.isNotEmpty()) {
                OverpassRepo.findNearbyPharmacies(location.latitude, location.longitude)
            } else emptyList()
            isLoading = false

            if (!lastReportedSearch && stores.isNotEmpty() && (lowStockNames.isNotEmpty() || refillNeededMeds.isNotEmpty())) {
                lastReportedSearch = true
                val parts = buildList {
                    if (lowStockNames.isNotEmpty()) add("محتاج يشتري: ${lowStockNames.take(5).joinToString("، ")}")
                    if (refillNeededMeds.isNotEmpty()) add("دواء قرب يخلص: ${refillNeededMeds.take(5).joinToString("، ")}")
                }.joinToString(" — ")
                viewModel.triggerBrainEvent(
                    "العميل فاتح صفحة السوبرماركت القريب ولقى ${stores.size} محل قريب منه. $parts. لو دي فرصة توفير حقيقية نبهه."
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) searchNearby()
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) searchNearby()
    }

    // تنبيهات قرب السوبرماركت (opt-in، geofencing حقيقي) — منفصلة عن البحث اليدوي فوق
    var locationAlertsEnabled by remember { mutableStateOf(GroceryGeofenceManager.isEnabled(context)) }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            GroceryGeofenceManager.setEnabled(context, true)
            locationAlertsEnabled = true
            androidx.work.WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<com.example.workers.GeofenceRefreshWorker>().build()
            )
        } else {
            locationAlertsEnabled = false
        }
    }
    val foregroundForAlertsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                GroceryGeofenceManager.setEnabled(context, true)
                locationAlertsEnabled = true
                androidx.work.WorkManager.getInstance(context).enqueue(
                    androidx.work.OneTimeWorkRequestBuilder<com.example.workers.GeofenceRefreshWorker>().build()
                )
            }
        } else {
            locationAlertsEnabled = false
        }
    }
    fun onLocationAlertsToggle(wantEnabled: Boolean) {
        if (!wantEnabled) {
            GroceryGeofenceManager.setEnabled(context, false)
            locationAlertsEnabled = false
            return
        }
        if (!hasLocationPermission) {
            foregroundForAlertsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !GroceryGeofenceManager.hasBackgroundLocationPermission(context)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            GroceryGeofenceManager.setEnabled(context, true)
            locationAlertsEnabled = true
            androidx.work.WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<com.example.workers.GeofenceRefreshWorker>().build()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp)).background(surfaceContainerLow).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.location_alerts_toggle_label), style = Typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                    Text(stringResource(R.string.location_alerts_toggle_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                com.example.ui.components.ZadSwitch(checked = locationAlertsEnabled, onCheckedChange = { onLocationAlertsToggle(it) })
            }

            if (!hasLocationPermission) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.location_permission_rationale), style = Typography.bodyMedium, textAlign = TextAlign.Center, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.pressableScale(),
                        shape = RoundedCornerShape(50)
                    ) { Text(stringResource(R.string.allow_location_action)) }
                }
            } else {
                AppearOnEntry {
                    // The mockup tints this note `rgba(6,78,59,.06)` with green text.
                    // It was painted with `primaryContainer` (#052E16) — a near-black
                    // slab at the top of the screen for what is a footnote.
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(14.dp)).background(primary.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.nearby_deals_disclaimer),
                            fontSize = 12.sp, lineHeight = 18.sp, color = primary
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primary)
                    }
                } else if (searchError) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.location_unavailable_hint), style = Typography.bodyMedium, color = onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { searchNearby() }, modifier = Modifier.pressableScale()) {
                            Text(stringResource(R.string.retry_action))
                        }
                    }
                } else if (stores.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    ) {
                        ZadLottieAsset(resId = R.raw.lottie_empty_box, modifier = Modifier.size(140.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.no_nearby_stores_hint), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pharmacies.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.nearby_pharmacies_section_title),
                                    style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface
                                )
                            }
                            items(pharmacies, key = { "pharmacy_" + it.name + it.lat }) { pharmacy ->
                                NearbyStoreCard(store = pharmacy, lowStockNames = refillNeededMeds, isPharmacy = true)
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.nearby_supermarkets_section_title),
                                    style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface
                                )
                            }
                        }
                        items(stores, key = { "store_" + it.name + it.lat }) { store ->
                            NearbyStoreCard(store = store, lowStockNames = lowStockNames)
                        }
                    }
                }
            }
        }
    }
}

// internal مش private — Roborazzi capture tests (PreviewTest.kt) محتاجة توصله مباشرة
// عشان NearbyDealsScreen نفسها stateful (بتاخد ZadViewModel)، مش قابلة للـ capture كاملة.
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
