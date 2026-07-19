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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.example.ui.theme.AppTheme
import com.example.ui.theme.primary
import com.example.ui.theme.background
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.viewmodels.AuthViewModel
import com.example.ui.viewmodels.ZadViewModel
import com.example.MainScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.FamilyScreen
import com.example.ui.screens.CameraScreen
import com.example.data.SupabaseRepo

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.workers.PeriodicAnalysisWorker

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

        // Schedule periodic AI analysis (Feature 6)
        val workRequest = PeriodicWorkRequestBuilder<PeriodicAnalysisWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadAnalysisWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
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
    
    // Default to LTR for Auth flow (since user requested English auth screens).
    // The main app is RTL, we handle that in MainScreen.
    
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onTimeout = {
                val session = SupabaseRepo.client.auth.currentSessionOrNull()
                if (session != null) {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            })
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
        composable("inventory") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val invViewModel = androidx.lifecycle.viewmodel.compose.viewModel<ZadViewModel>()
                InventoryScreen(
                    viewModel = invViewModel,
                    onOpenDrawer = { navController.popBackStack() },
                    onNavigateToAssistant = { navController.navigate("assistant") },
                    onNavigateToCamera = { navController.navigate("camera") }
                )
            }
        }
        composable("family") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FamilyScreen(
                    pendingInviteCode = pendingInviteCode,
                    onOpenDrawer = { navController.popBackStack() }
                )
            }
        }
        composable("chat") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                androidx.compose.material3.Text("الشات متاح من صفحة العائلة")
            }
        }


        composable("camera") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text("الكاميرا متاحة من الشريط السفلي")
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
        com.example.data.SessionHelper.loadSession(context)
        
        // Load AI API key from SharedPreferences so it works in ALL screens (not just Camera)
        val savedApiKey = context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
        if (savedApiKey.isNotEmpty()) {
            com.example.data.ZadAiRepository.geminiApiKey = savedApiKey
            Log.d("ZAD_AI", "API key loaded from SharedPreferences on startup")
        }
        
        delay(2000)
        SupabaseRepo.client.auth.awaitInitialization()
        
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
        
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            com.example.ui.components.ZadLogo()
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
