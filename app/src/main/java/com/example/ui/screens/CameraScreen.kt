package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.airbnb.lottie.compose.LottieConstants
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.AiParsedInventoryItem
import com.example.data.AiParsedReceipt
import com.example.data.AiParsedReceiptItem
import com.example.data.ZadAiRepository
import com.example.data.ZadInventory
import com.example.ui.components.ZadLottieAsset
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val RECEIPT_TYPE_OPTIONS = listOf(
    "pharmacy" to "صيدلية",
    "grocery" to "سوبرماركت",
    "general" to "مصاريف عامة"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: ZadViewModel,
    /** "INVENTORY" or "RECEIPT" — preselected by ZadCameraSheet's two buttons. */
    initialMode: String = "INVENTORY",
    onBack: () -> Unit = {}
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // stringResource() مش context.getString() هنا: `context` لسه ما اتعرّفش عند السطر ده
    // (تحت بشوية)، والقيمة دي بتتقرا مرة واحدة في تكوين الشاشة فالـ@Composable مناسبة.
    val initialCameraHint = stringResource(R.string.cam_hint_initial)
    var analysisStatus by remember { mutableStateOf(initialCameraHint) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var parsedItems by remember { mutableStateOf<List<AiParsedInventoryItem>>(emptyList()) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var parsedMedicine by remember { mutableStateOf<com.example.data.AiParsedMedicine?>(null) }
    var showMedicineConfirmationDialog by remember { mutableStateOf(false) }
    var parsedReceipt by remember { mutableStateOf<AiParsedReceipt?>(null) }
    var showReceiptConfirmationDialog by remember { mutableStateOf(false) }
    var showReceiptErrorDialog by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var scanMode by remember { mutableStateOf(initialMode) }
    var pulseScale by remember { mutableStateOf(1f) }
    var showCaptureFlash by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    val prefs = context.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val savedKey = prefs.getString("gemini_api_key", "")
        if (!savedKey.isNullOrEmpty()) {
            ZadAiRepository.geminiApiKey = savedKey
        }
    }

    // Pulse animation for camera button
    LaunchedEffect(isAnalyzing) {
        while (isAnalyzing) {
            pulseScale = 0.95f
            delay(400)
            pulseScale = 1.05f
            delay(400)
        }
        pulseScale = 1f
    }

    // Flash overlay: يبان لحظة رجوع الكاميرا بنجاح ثم يقفل نفسه لوحده
    LaunchedEffect(showCaptureFlash) {
        if (showCaptureFlash) {
            delay(700)
            showCaptureFlash = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        Log.d("CameraScreen", " Camera returned success: $success")
        if (success && imageUri != null) {
            showCaptureFlash = true
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri!!)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }
            imageBitmap = bitmap
            isAnalyzing = true
            analysisStatus = context.getString(R.string.cam_analyzing_ai)

            scope.launch {
                try {
                    if (scanMode == "PHARMACY") {
                        Log.d("CameraScreen", "Sending bitmap to analyzeMedicineImage")
                        val result = ZadAiRepository.analyzeMedicineImage(bitmap)
                        if (result != null && result.name.isNotBlank()) {
                            Log.d("CameraScreen", " AI found medicine: ${result.name}")
                            parsedMedicine = result
                            showMedicineConfirmationDialog = true
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                                if (Build.VERSION.SDK_INT >= 26) {
                                    vib?.vibrate(VibrationEffect.createOneShot(50, 200))
                                } else {
                                    @Suppress("DEPRECATION") vib?.vibrate(50)
                                }
                            } catch (_: Exception) {}
                            analysisStatus = context.getString(R.string.cam_medicine_extracted, result.name)
                        } else {
                            Log.e("CameraScreen", " AI found no medicine")
                            analysisStatus = context.getString(R.string.medicine_scan_error)
                        }
                    } else if (scanMode == "INVENTORY") {
                        Log.d("CameraScreen", "Sending bitmap to analyzeInventoryImage")
                        val result = ZadAiRepository.analyzeInventoryImage(bitmap)
                        if (result != null && result.items.isNotEmpty()) {
                            Log.d("CameraScreen", " AI found ${result.items.size} items")
                            parsedItems = result.items
                            showConfirmationDialog = true
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                                if (Build.VERSION.SDK_INT >= 26) {
                                    vib?.vibrate(VibrationEffect.createOneShot(50, 200))
                                } else {
                                    @Suppress("DEPRECATION") vib?.vibrate(50)
                                }
                            } catch (_: Exception) {}
                            analysisStatus = context.getString(R.string.cam_items_extracted, result.items.size)
                        } else {
                            Log.e("CameraScreen", " AI found no items")
                            analysisStatus = context.getString(R.string.cam_no_items_found)
                        }
                    } else {
                        Log.d("CameraScreen", "Sending bitmap to analyzeReceipt")
                        val result = ZadAiRepository.analyzeReceipt(bitmap)
                        // result.total defaults to 0.0 on a parse miss — a silent "0 EGP" save is
                        // worse than an error, so total<=0 with no items read as a failed scan.
                        if (result != null && (result.total > 0.0 || result.items.isNotEmpty())) {
                            parsedReceipt = result
                            showReceiptConfirmationDialog = true
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                                if (Build.VERSION.SDK_INT >= 26) {
                                    vib?.vibrate(VibrationEffect.createOneShot(50, 200))
                                } else {
                                    @Suppress("DEPRECATION") vib?.vibrate(50)
                                }
                            } catch (_: Exception) {}
                            analysisStatus = context.getString(R.string.cam_receipt_extracted, result.storeName)
                        } else {
                            showReceiptErrorDialog = true
                            analysisStatus = context.getString(R.string.cam_receipt_unreadable)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraScreen", "AI analysis crashed: ${e.message}", e)
                    analysisStatus = context.getString(R.string.cam_analysis_error)
                }
                isAnalyzing = false
            }
        }
    }

    // لما الصلاحية تتمنع نهائياً، `launch()` بترجع false فوراً من غير ما تعرض أي حوار
    // نظام. من غير الفلاج ده كان العميل يدوس الزرار ومايحصلش حاجة غير سطر حالة —
    // ومفيش أي طريق يرجع منه. نفس فئة الباج اللي اتصلحت قبل كده في صف "قراءة الرسايل"
    // (شوف CLAUDE.md §الخطة)، والحل الموجود أصلاً في LocationAlertsCard.
    var cameraPermanentlyDenied by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // رجع من الإعدادات — نصفّر الحالة عشان الزرار يشتغل عادي لو اداها الصلاحية.
        cameraPermanentlyDenied = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings() {
        settingsLauncher.launch(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("CameraScreen", " Camera permission: $isGranted")
        if (isGranted) {
            cameraPermanentlyDenied = false
            val dir = File(context.cacheDir, "images")
            if (!dir.exists()) dir.mkdirs()
            Log.d("CameraScreen", "Cache dir: ${dir.absolutePath}, exists: ${dir.exists()}")
            val imageFile = File(dir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            imageUri = uri
            try {
                launcher.launch(uri)
            } catch (e: Exception) {
                analysisStatus = context.getString(R.string.cam_cannot_open_camera)
            }
        } else {
            // `shouldShowRequestPermissionRationale` بترجع false في حالتين: الأولى قبل
            // أي طلب خالص، والتانية بعد المنع النهائي. إحنا هنا بعد رد فعلي بالرفض،
            // فـfalse معناها المنع النهائي — يعني الطلب تاني مش هيعرض أي حوار.
            val activity = context as? android.app.Activity
            val canAskAgain = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.CAMERA
                )
            } ?: true
            cameraPermanentlyDenied = !canAskAgain
            analysisStatus = if (canAskAgain) {
                context.getString(R.string.cam_permission_needed)
            } else {
                context.getString(R.string.cam_permission_blocked)
            }
        }
    }

    // Manual Entry Dialog
    if (showManualEntry) {
        ManualInventoryDialog(
            onDismiss = { showManualEntry = false },
            onSave = { items ->
                items.forEach { viewModel.addInventory(it) }
                analysisStatus = context.getString(R.string.cam_items_added_manual, items.size)
                showManualEntry = false
            }
        )
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(stringResource(R.string.cam_ai_setup_title)) },
            text = {
                Column {
                    // Gemini, not Groq: Groq refuses structured JSON on image requests, so
                    // the scanner runs on Gemini only now (ZadAiGeminiClient). Several keys
                    // can be pasted at once, separated by commas — they're tried in order
                    // when one hits its quota.
                    Text(stringResource(R.string.auto_camera_14259),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text(stringResource(R.string.cam_api_key_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("gemini_api_key", apiKeyInput.trim()).apply()
                    ZadAiRepository.geminiApiKey = apiKeyInput.trim()
                    showApiKeyDialog = false
                }) { Text(stringResource(R.string.cam_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text(stringResource(R.string.cam_cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("  ", fontSize = 20.sp)
                    Text(stringResource(R.string.cam_smart_scanner), fontWeight = FontWeight.Bold)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                IconButton(onClick = { 
                    apiKeyInput = prefs.getString("gemini_api_key", "") ?: ""
                    showApiKeyDialog = false 
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(com.example.ui.theme.ZadLuxe.squircle)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_captured_image),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.auto_camera_36966),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.auto_camera_21409),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Scanning overlay
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ZadLottieAsset(
                                resId = R.raw.lottie_scan_receipt,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.size(96.dp),
                                contentDescription = stringResource(R.string.cd_analyzing)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.cam_analyzing), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scanMode == "PHARMACY" || initialMode == "PHARMACY") {
                    Button(
                        onClick = {
                            scanMode = "PHARMACY"
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        enabled = !isAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("   " + stringResource(R.string.cam_scan_medicine), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    // Inventory scan button
                    Button(
                        onClick = {
                            scanMode = "INVENTORY"
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        enabled = !isAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("   " + stringResource(R.string.cam_scan_inventory), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Receipt scan button
                    OutlinedButton(
                        onClick = {
                            scanMode = "RECEIPT"
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        enabled = !isAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("   " + stringResource(R.string.cam_scan_receipt), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual entry button
            OutlinedButton(
                onClick = {
                    showManualEntry = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("   " + stringResource(R.string.cam_add_products_manually), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status message with animation
            AnimatedVisibility(
                visible = analysisStatus.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = analysisStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface,
                            textAlign = TextAlign.Center
                        )
                        // مخرج من الطريق المسدود. من غيره، عميل منع الصلاحية مرة يفضل
                        // يدوس على زرار المسح ومايحصلش حاجة للأبد: النظام مش بيعرض
                        // حوار تاني، والتطبيق مكانش بيوفر أي طريق للإعدادات.
                        if (cameraPermanentlyDenied) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { openAppSettings() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primary)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.cam_open_app_settings), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

        // Capture flash overlay: فلاش أبيض + علامة صح لحظة رجوع الكاميرا بالصورة
        androidx.compose.animation.AnimatedVisibility(
            visible = showCaptureFlash,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZadLottieAsset(
                    resId = R.raw.lottie_camera_capture,
                    iterations = 1,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null
                )
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        // الحقن الذكي: يزوّد الموجود بدل التكرار + يشطب من النواقص
                        viewModel.injectScannedItems(
                            parsedItems.map { item ->
                                ZadInventory(
                                    itemName = item.name,
                                    quantity = maxOf(1, item.quantity.toInt()),
                                    unit = item.unit,
                                    category = item.category
                                )
                            }
                        ) { summary -> analysisStatus = context.getString(R.string.cam_injected, summary) }
                        showConfirmationDialog = false
                        analysisStatus = context.getString(R.string.cam_injecting, parsedItems.size)
                        parsedItems = emptyList()
                        imageBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("   " + stringResource(R.string.cam_inject_into_stock))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                    analysisStatus = context.getString(R.string.cam_add_cancelled)
                }) {
                    Text(stringResource(R.string.cam_cancel))
                }
            },
            icon = { Text("  ", fontSize = 24.sp) },
            title = {
                Text(stringResource(R.string.cam_confirm_stock), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text(stringResource(R.string.auto_camera_11386),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var editableList by remember(parsedItems) { mutableStateOf(parsedItems) }

                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        editableList.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "${item.quantity} ${item.unit}  •  ${item.category}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        editableList = editableList.filter { it != item }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = dangerColor)
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(editableList) {
                        parsedItems = editableList
                    }
                }
            }
        )
    }

    if (showMedicineConfirmationDialog && parsedMedicine != null) {
        val med = parsedMedicine!!
        AlertDialog(
            onDismissRequest = { showMedicineConfirmationDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val estPrice = if (med.price > 0.0) med.price else (com.example.data.PharmacyPricingEstimator.estimatePrice(med.name) ?: 0.0)
                        viewModel.addPharmacyItem(
                            com.example.data.ZadPharmacyItem(
                                name = med.name,
                                activeIngredient = med.activeIngredient,
                                category = med.category,
                                dosage = med.dosage,
                                remainingQuantity = med.quantity,
                                unit = med.unit,
                                dailyDoseCount = med.dailyDoseCount,
                                doseTimes = med.doseTimes,
                                expiryDate = med.expiryDate,
                                price = estPrice
                            )
                        )
                        showMedicineConfirmationDialog = false
                        analysisStatus = context.getString(R.string.cam_medicine_extracted, med.name)
                        parsedMedicine = null
                        imageBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("   " + stringResource(R.string.cam_save_to_pharmacy))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMedicineConfirmationDialog = false
                    analysisStatus = context.getString(R.string.cam_add_cancelled)
                }) {
                    Text(stringResource(R.string.cam_cancel))
                }
            },
            icon = { Icon(Icons.Default.Medication, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp)) },
            title = {
                Text(
                    stringResource(R.string.cam_scan_medicine),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(med.name, style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!med.activeIngredient.isNullOrBlank()) {
                        Text(med.activeIngredient, style = Typography.bodySmall, color = onSurfaceVariant)
                    }
                    Text("${med.quantity} ${med.unit}  •  ${med.category}", style = MaterialTheme.typography.bodySmall)
                    if (!med.dosage.isNullOrBlank()) {
                        Text(med.dosage, style = Typography.bodySmall)
                    }
                    if (!med.expiryDate.isNullOrBlank()) {
                        Text("${stringResource(R.string.expiry_date_hint)}: ${med.expiryDate}", style = Typography.labelSmall, color = textTertiary)
                    }
                }
            }
        )
    }

    // Receipt Error Dialog — shown instead of a silent 0 ج.م save when the scan comes back
    // with no readable total AND no items.
    if (showReceiptErrorDialog) {
        AlertDialog(
            onDismissRequest = { showReceiptErrorDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showReceiptErrorDialog = false
                        imageBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) { Text(stringResource(R.string.cam_ok)) }
            },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = dangerColor) },
            title = { Text(stringResource(R.string.cam_receipt_read_failed_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.cam_receipt_read_failed_body)) }
        )
    }

    // Receipt Confirmation Dialog — same preview-before-write pattern as the inventory
    // dialog above, so a receipt scan can't silently commit a wrong store/total/items.
    if (showReceiptConfirmationDialog && parsedReceipt != null) {
        val receipt = parsedReceipt!!
        AlertDialog(
            onDismissRequest = { showReceiptConfirmationDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (receipt.receiptType == "pharmacy") {
                            viewModel.injectPharmacyReceipt(receipt)
                            analysisStatus = context.getString(R.string.cam_receipt_to_pharmacy, receipt.storeName, com.example.data.CurrencyFormatter.format(context, receipt.total))
                        } else if (receipt.items.all { it.category in setOf("تنظيف", "أدوات منزلية", "صيانة") } && receipt.items.isNotEmpty()) {
                            // فاتورة أدوات منزلية/تنظيف → الصيانة والمخزون العام معاً، ومعاملة واحدة
                            viewModel.addTransaction(
                                com.example.data.ZadTransaction(
                                    title = receipt.storeName,
                                    amount = receipt.total,
                                    isExpense = true,
                                    category = "أدوات منزلية"
                                )
                            )
                            viewModel.injectScannedItems(
                                receipt.items.map { item ->
                                    ZadInventory(
                                        itemName = item.name,
                                        quantity = maxOf(1, item.quantity.toInt()),
                                        unit = item.unit,
                                        category = item.category
                                    )
                                }
                            ) { summary ->
                                analysisStatus = context.getString(R.string.cam_receipt_summary, receipt.storeName, summary)
                            }
                            analysisStatus = context.getString(R.string.cam_receipt_and_stock, receipt.storeName, com.example.data.CurrencyFormatter.format(context, receipt.total))
                        } else {
                            viewModel.addTransaction(
                                com.example.data.ZadTransaction(
                                    title = receipt.storeName,
                                    amount = receipt.total,
                                    isExpense = true,
                                    category = receipt.category
                                )
                            )
                            // الحقن الذكي: يزوّد الموجود + يشطب من النواقص + يتعلم
                            viewModel.injectScannedItems(
                                receipt.items.map { item ->
                                    ZadInventory(
                                        itemName = item.name,
                                        quantity = maxOf(1, item.quantity.toInt()),
                                        unit = item.unit,
                                        category = item.category
                                    )
                                }
                            ) { summary ->
                                analysisStatus = context.getString(R.string.cam_receipt_full_summary, receipt.storeName, com.example.data.CurrencyFormatter.format(context, receipt.total), summary)
                            }
                            analysisStatus = context.getString(R.string.cam_receipt_and_items, receipt.storeName, com.example.data.CurrencyFormatter.format(context, receipt.total))
                        }
                        showReceiptConfirmationDialog = false
                        parsedReceipt = null
                        imageBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("   " + stringResource(R.string.cam_save_receipt))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReceiptConfirmationDialog = false
                    parsedReceipt = null
                    analysisStatus = context.getString(R.string.cam_receipt_cancelled)
                }) {
                    Text(stringResource(R.string.cam_cancel))
                }
            },
            icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
            title = {
                Text(stringResource(R.string.cam_confirm_receipt), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    Text(
                        "${receipt.storeName}  •  ${com.example.data.CurrencyFormatter.format(context, receipt.total)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.auto_camera_97981),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var editableReceiptType by remember(receipt) { mutableStateOf(receipt.receiptType) }
                    // بيكتب رجوع في parsedReceipt نفسه، فـ confirmButton (بره الـ text lambda دي)
                    // بيقرأ receipt.receiptType المحدّث من غير أي تغيير في منطق التسجيل هناك —
                    // نفس نمط editableReceiptItems تحت.
                    LaunchedEffect(editableReceiptType) {
                        parsedReceipt = receipt.copy(receiptType = editableReceiptType)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.cam_receipt_type_hint), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RECEIPT_TYPE_OPTIONS.forEach { (value, label) ->
                                FilterChip(
                                    selected = editableReceiptType == value,
                                    onClick = { editableReceiptType = value },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    var editableReceiptItems by remember(receipt) { mutableStateOf(receipt.items) }

                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        editableReceiptItems.forEachIndexed { idx, item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "${item.unit}  •  ${com.example.data.CurrencyFormatter.format(context, item.price)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    // Quick quantity edit: total ج.م injected stays the parsed
                                    // receipt total either way, only the inventory quantities move.
                                    IconButton(
                                        onClick = {
                                            editableReceiptItems = editableReceiptItems.toMutableList().apply {
                                                this[idx] = item.copy(quantity = (item.quantity - 1.0).coerceAtLeast(1.0))
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_decrease_qty), modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        "${item.quantity.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            editableReceiptItems = editableReceiptItems.toMutableList().apply {
                                                this[idx] = item.copy(quantity = item.quantity + 1.0)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase_qty), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = {
                                        editableReceiptItems = editableReceiptItems.filter { it != item }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = dangerColor)
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(editableReceiptItems) {
                        parsedReceipt = receipt.copy(items = editableReceiptItems)
                    }
                }
            }
        )
    }
}

@Composable
private fun ManualInventoryDialog(
    onDismiss: () -> Unit,
    onSave: (List<ZadInventory>) -> Unit
) {
    var items by remember { mutableStateOf(listOf(ZadInventory(itemName = "", quantity = 1, unit = "قطعة", category = "عام"))) }
    val categories = listOf("خضار", "فواكه", "ألبان", "لحوم", "بقالة", "مشروبات", "منظفات", "معلبات", "عام")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val valid = items.filter { it.itemName.isNotBlank() }
                    if (valid.isNotEmpty()) onSave(valid)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.camera_save_count, items.count { it.itemName.isNotBlank() }))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cam_cancel)) }
        },
        icon = { Text("  ", fontSize = 24.sp) },
        title = { Text(stringResource(R.string.cam_add_products_manually), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items.indices.toList().forEach { idx ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${idx + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                    OutlinedTextField(
                                        value = items[idx].itemName,
                                        onValueChange = { v ->
                                            items = items.toMutableList().apply { set(idx, items[idx].copy(itemName = v)) }
                                        },
                                        label = { Text(stringResource(R.string.cam_product_name)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (items.size > 1) {
                                        IconButton(onClick = {
                                            items = items.toMutableList().apply { removeAt(idx) }
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_delete), tint = dangerColor)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = items[idx].quantity.toString(),
                                        onValueChange = { v ->
                                            val q = v.toIntOrNull() ?: 0
                                            items = items.toMutableList().apply { set(idx, items[idx].copy(quantity = q)) }
                                        },
                                        label = { Text(stringResource(R.string.cam_quantity)) },
                                        singleLine = true,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    var selectedUnit by remember { mutableStateOf(items[idx].unit ?: "قطعة") }
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedUnit,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.cam_unit)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier.menuAnchor().width(100.dp)
                                        )
                                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            listOf("قطعة", "كجم", "لتر", "حبة", "علبة", "كرتون", "زجاجة", "كيس").forEach { u ->
                                                DropdownMenuItem(
                                                    text = { Text(u) },
                                                    onClick = {
                                                        selectedUnit = u
                                                        items = items.toMutableList().apply { set(idx, items[idx].copy(unit = u)) }
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    var selectedCat by remember { mutableStateOf(items[idx].category ?: "عام") }
                                    var catExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = catExpanded,
                                        onExpandedChange = { catExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedCat,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.cam_category)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                                            modifier = Modifier.menuAnchor().weight(1f)
                                        )
                                        ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                            categories.forEach { c ->
                                                DropdownMenuItem(
                                                    text = { Text(c) },
                                                    onClick = {
                                                        selectedCat = c
                                                        items = items.toMutableList().apply { set(idx, items[idx].copy(category = c)) }
                                                        catExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        items = items + ZadInventory(itemName = "", quantity = 1, unit = "قطعة", category = "عام")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("   " + stringResource(R.string.cam_add_another_product))
                }
            }
        }
    )
}
