package com.example

import io.github.jan.supabase.auth.auth
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.ui.theme.AppTheme
import com.example.ui.theme.primary
import com.example.ui.theme.primaryLight
import com.example.ui.theme.textSecondary
import com.example.ui.theme.textTertiary
import com.example.ui.theme.background
import com.example.ui.components.zadGlassBlur
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.screens.auth.MarketSelectionScreen
import com.example.data.MarketPrefs
import com.example.ui.viewmodels.AuthViewModel
import com.example.ui.viewmodels.ZadViewModel
import com.example.MainScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.FamilyScreen
import com.example.ui.screens.CameraScreen
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.status.SessionStatus

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.workers.PeriodicAnalysisWorker
import com.example.workers.MorningSummaryWorker

import android.content.Intent

class MainActivity : ComponentActivity() {
    companion object {
        var pendingInviteCode = mutableStateOf<String?>(null)
        var openChatFromNotification = mutableStateOf(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("crash", throwable.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
        
        handleIntent(intent)

        MarketPrefs.applyStoredLocale(this)

        // Session persistence/refresh is entirely handled by auth-kt's own Auth plugin
        // (autoLoadFromStorage/autoSaveToStorage/alwaysAutoRefresh default to true) — no
        // app-side save/restore code needed. This collector only reacts to the resulting
        // Authenticated status to sync pending alerts.
        lifecycleScope.launch {
            SupabaseRepo.client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    // أول ما المستخدم يفتح التطبيق والجلسة تتعرف، اسحب رؤى العقل الـ pending
                    // اللي لسه نازلة (حرجة) وحوّلها إشعارات + صوت. كنا بنستنى الـ workers
                    // (كل 6 ساعات/يومياً) بس، فالرؤية كانت بتتأخر أو تختفي نهائياً.
                    try {
                        val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
                        if (userId != null) {
                            com.zad.agent.ZadAlertRouter.sync(applicationContext, userId)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "ZadAlertRouter.sync() on-open failed: ${e.message}")
                    }
                }
            }
        }

        // Schedule periodic AI analysis (Feature 6)
        val workRequest = PeriodicWorkRequestBuilder<PeriodicAnalysisWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadAnalysisWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // الملخص الصباحي الذكي — كل يوم الساعة 7 صباحاً
        val now = java.time.LocalDateTime.now()
        var next7am = now.withHour(7).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(next7am)) next7am = next7am.plusDays(1)
        val initialDelayMinutes = java.time.Duration.between(now, next7am).toMinutes()
        val morningWorkRequest = PeriodicWorkRequestBuilder<MorningSummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadMorningSummaryWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            morningWorkRequest
        )

        // تذكير التسبيح — كل يوم الساعة 5 عصراً، بس لو المستخدم لسه ما سبّحش النهاردة
        var next5pm = now.withHour(17).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(next5pm)) next5pm = next5pm.plusDays(1)
        val tasbihaInitialDelayMinutes = java.time.Duration.between(now, next5pm).toMinutes()
        val tasbihaReminderRequest = PeriodicWorkRequestBuilder<com.example.workers.TasbihaReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(tasbihaInitialDelayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadTasbihaReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            tasbihaReminderRequest
        )

        // تذكير المناسبات الموسمية — كل يوم الساعة 9 صباحاً (30 يوم قبل المناسبة)
        var next9am = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(next9am)) next9am = next9am.plusDays(1)
        val seasonalInitialDelayMinutes = java.time.Duration.between(now, next9am).toMinutes()
        val seasonalReminderRequest = PeriodicWorkRequestBuilder<com.example.workers.SeasonalEventReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(seasonalInitialDelayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadSeasonalEventReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            seasonalReminderRequest
        )

        // خصم الاشتراكات المتجددة تلقائياً — كل يوم الساعة 8 صباحاً
        var next8am = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(next8am)) next8am = next8am.plusDays(1)
        val autoDeductInitialDelayMinutes = java.time.Duration.between(now, next8am).toMinutes()
        val autoDeductRequest = PeriodicWorkRequestBuilder<com.example.workers.SubscriptionAutoDeductWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(autoDeductInitialDelayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadSubscriptionAutoDeductWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            autoDeductRequest
        )

        // مزامنة المعاملات من Supabase كل 3 ساعات — تناسق بين الأجهزة حتى لو التطبيق مفتوحش
        val txSyncRequest = PeriodicWorkRequestBuilder<com.example.workers.TransactionSyncWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadTransactionSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            txSyncRequest
        )

        // تنبيهات قرب السوبرماركت (opt-in) — الـ worker نفسه بيتشيك enabled/permission
        // ومايعملش حاجة لو مفعّلهاش المستخدم، فمأمون نجدولها دايماً زي باقي الـ workers
        val geofenceRefreshRequest = PeriodicWorkRequestBuilder<com.example.workers.GeofenceRefreshWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadGeofenceRefreshWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            geofenceRefreshRequest
        )

        // Start real-time chat notification service
        try {
            startService(Intent(this, com.example.services.ChatNotificationService::class.java))
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start ChatNotificationService: ${e.message}")
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AppNavigation(pendingInviteCode.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri?.scheme == "zad" && uri.host == "invite") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d("ZAD_DEEPLINK", "Received invite code from intent: $code")
                pendingInviteCode.value = code
            }
        }
        if (intent?.getBooleanExtra("open_family_chat", false) == true) {
            Log.d("ZAD_NOTIF", "Opening family chat from notification")
            openChatFromNotification.value = true
        }
    }
}

@Composable
fun AppNavigation(pendingInviteCode: String? = null) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Default to LTR for Auth flow (since user requested English auth screens).
    // The main app is RTL, we handle that in MainScreen.

    val navigateAfterSplash: () -> Unit = navigate@{
        if (!MarketPrefs.hasSelectedMarket(context)) {
            navController.navigate("market_selection") {
                popUpTo(0) { inclusive = true }
            }
            return@navigate
        }
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        navController.navigate(if (session != null) "main" else "onboarding") {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onTimeout = navigateAfterSplash)
        }
        composable("market_selection") {
            MarketSelectionScreen(onContinue = navigateAfterSplash)
        }
        composable("onboarding") {
            OnboardingScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToMain = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }
        composable("signup") {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateToMain = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("main") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MainScreen(
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    pendingInviteCode = pendingInviteCode
                )
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("ZAD_PERM", "${it.key} = ${it.value}")
        }
    }
    LaunchedEffect(key1 = true) {
        startAnimation = true
        // Session restore is handled by auth-kt's own Auth plugin (autoLoadFromStorage) —
        // awaitInitialization() below just waits for that to finish.

        // Load AI API key from SharedPreferences so it works in ALL screens (not just Camera)
        val savedApiKey = context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
        if (savedApiKey.isNotEmpty()) {
            com.example.data.ZadAiRepository.geminiApiKey = savedApiKey
            Log.d("ZAD_AI", "API key loaded from SharedPreferences on startup")
        }
        
        delay(2000)
        SupabaseRepo.client.auth.awaitInitialization()
        com.example.data.CurrentUser.cache(context, SupabaseRepo.client.auth.currentUserOrNull()?.id)

        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Phase A6 (PRODUCT_PLAN.md §5): RECEIVE_SMS/READ_SMS are Play-restricted and
        // are no longer requested. Bank messages now arrive through
        // UnifiedBankListener, which reads them from the messaging app's own
        // notification — same data, a permission the user grants explicitly.
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        onTimeout()
    }

    // Warm off-white canvas with two soft ambient blobs (peach top-start, mint
    // bottom-end), matching the design mockup's radial-gradient splash.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBFAF8)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-60).dp)
                .size(320.dp)
                .clip(CircleShape)
                .zadGlassBlur(80.dp)
                .background(Color(0xFFFCD3C7).copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 60.dp)
                .size(320.dp)
                .clip(CircleShape)
                .zadGlassBlur(80.dp)
                .background(Color(0xFFBFE3D1).copy(alpha = 0.55f))
        )

        Column(
            modifier = Modifier.alpha(alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.example.ui.components.ZadLogo()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "زاد",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryLight
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "تدبير ذكي لبيت هادئ",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "خصوصية بياناتك أولوية، دائماً",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = textTertiary
            )
            Spacer(Modifier.height(60.dp))
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.12f))
            )
        }
    }
}

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = intent.getStringExtra("crash") ?: "Unknown crash"
        setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Text(text = "App Crashed!", color = Color.Red, fontSize = 24.sp)
                            Text(text = crash, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
