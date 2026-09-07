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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
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
import com.example.data.Market
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.status.RefreshFailureCause

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.workers.PeriodicAnalysisWorker
import com.example.workers.MorningSummaryWorker

import android.content.Context
import android.content.Intent

class MainActivity : ComponentActivity() {
    companion object {
        var pendingInviteCode = mutableStateOf<String?>(null)
        var openChatFromNotification = mutableStateOf(false)

        /**
         * zad://rewards — بيفتح شاشة الشحن بالإعلانات.
         *
         * البوت بيبعت الزر ده في رسالة نفاد الرصيد: العميل واقف في تليجرام والرصيد
         * خلص، والحل جوه التطبيق. من غير الرابط ده الرسالة بتقوله "روح التطبيق"
         * وبس، وده احتكاك كفاية إنه ما يروحش.
         */
        var openRewardsFromDeepLink = mutableStateOf(false)

        /** "Hey Zad" — الخدمة طلبت فتح شاشة الصوت (wake word اتكشف). */
        var openVoiceRequest = mutableStateOf(false)

        /** بند 32.2 — دوس على إشعار FCM بتاع معاملة بنكية مستنية تأكيد يفتح الرئيسية
         *  فورًا (الكارت هناك بيتحدث لوحده realtime، مش محتاج route مخصوص). */
        var openTransactionProposalsRequest = mutableStateOf(false)
    }

    /**
     * لغة السوق المختارة بتتطبّق هنا، قبل ما أي resource يتحل.
     *
     * `MarketPrefs.applyStoredLocale()` في `onCreate` تحت بتفضل — هي اللي بتسجّل الاختيار
     * على مستوى التطبيق للشاشات الجاية — بس هي لوحدها ما كانتش بتغيّر الـ Activity دي:
     * `ComponentActivity` مالهاش `AppCompatDelegate` يلف `attachBaseContext`، والـ
     * Resources بتكون اتحلّت خلاص وقت ما `onCreate` بيجري. فاختيار التركي كان بيتخزن
     * وما بيبانش، رغم إن `values-tr` مترجمة بالكامل.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(MarketPrefs.wrapWithStoredLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            // سجل محلي قبل أي حاجة — لو الكراش قفل التطبيق، السجل يفضل موجود
            try { com.example.data.ZadCrashLog.record(this, throwable) } catch (_: Exception) {}
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

        // Initialize AdMob and Preload Rewarded Ad
        com.example.ads.RewardedBrainAdManager.initialize(this)
        com.example.ads.RewardedBrainAdManager.preload(this)

        // صلاحية ممنوحة مش معناها سيرفس شغال. أندرويد بيقتل NotificationListenerService
        // تحت ضغط الذاكرة أو بعد تحديث/إعادة تشغيل وساعات مابيرجعش يربطه، ومفيش حاجة في
        // الواجهة كانت بتفرّق — البانر بيقرا الصلاحية بس. النتيجة إن
        // zad_notification_ingest_events فضل فاضي تماماً رغم إن الصلاحية مفعّلة: مفيش
        // إشعار وصل السيرفس أصلاً. الطلب ده بيتبعت كل فتحة، وهو no-op لو مربوط بالفعل.
        com.example.data.BankReadingStatus.requestRebindIfPermitted(this)

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

        // مفيش وعي بحالة الشبكة كان موجود خالص — SyncOutbox كان بيتصرّف بس كل ٣ ساعات
        // (TransactionSyncWorker) بغض النظر عن رجوع النت الفعلي. register() idempotent.
        com.example.data.NetworkMonitor.register(applicationContext)

        // نفس السبب: صار object مشترك بدل instance منفصل لكل شاشة (ZadVoiceBottomSheet،
        // ZadIntelligenceScreen كانوا كل واحد بيعمل نسخته بنفسه). init() هنا يضمن إنه
        // جاهز قبل أي مكان يقرا voiceState بتاعه (زي مسكوت HomeScreen).
        try {
            com.example.voice.ZadVoiceManager.init(applicationContext)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "ZadVoiceManager.init safely caught: ${e.message}", e)
        }

        // Schedule periodic AI analysis (Feature 6)
        val workRequest = PeriodicWorkRequestBuilder<PeriodicAnalysisWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZadAnalysisWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // حلقة الوعي — كل ساعة: نداء العقل + مزامنة الرؤى لإشعارات/صوت.
        // (اللي فوق تحليل محلي كل 6 ساعات، وده "عين" العقل السحابي كل ساعة —
        // فواتير هتستحق، أدوية هتخلص، أصناف قبل الراتب — من غير ما تفتح التطبيق.)
        val awarenessRequest = PeriodicWorkRequestBuilder<com.example.workers.AwarenessWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.example.workers.AwarenessWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            awarenessRequest
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

        // "Hey Zad" wake word — استماع دائم (لو المستخدم مفعّله). لو RECORD_AUDIO مش
        // متاح لسه (أول تشغيل)، الخدمة هتفشل بهدوء والمستخدم هيدي الصلاحية من شاشة الصوت.
        if (com.example.data.WakePrefs.isEnabled(this)) {
            try {
                com.example.voice.HeyZadWakeService.start(this)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Wake service not started: ${e.message}")
            }
        }

        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
        if (uri != null) {
            Log.d("ZAD_DEEPLINK", "Received intent URI: $uri")
            val codeFromParam = uri.getQueryParameter("code")
            val code = if (!codeFromParam.isNullOrBlank()) {
                codeFromParam.trim()
            } else {
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank() && lastSegment != "invite" && lastSegment != "family") {
                    lastSegment.trim()
                } else null
            }
            if (!code.isNullOrBlank()) {
                Log.d("ZAD_DEEPLINK", "Extracted invite code from intent: $code")
                pendingInviteCode.value = code
            }
        }
        val explicitCode = intent?.getStringExtra("invite_code")
        if (!explicitCode.isNullOrBlank()) {
            pendingInviteCode.value = explicitCode.trim()
        }
        // الشكلين: zad://rewards (من جوه التطبيق) و https://zad.app/rewards (من
        // تليجرام — بيرفض المخططات المخصصة في أزرار الروابط).
        val wantsRewards = (uri?.scheme == "zad" && uri.host == "rewards") ||
            (uri?.host == "zad.app" && uri.path?.startsWith("/rewards") == true)
        if (wantsRewards) {
            Log.d("ZAD_DEEPLINK", "Opening rewards screen from deep link")
            openRewardsFromDeepLink.value = true
        }
        if (intent?.getBooleanExtra("open_family_chat", false) == true) {
            Log.d("ZAD_NOTIF", "Opening family chat from notification")
            openChatFromNotification.value = true
        }
        if (intent?.getBooleanExtra("open_voice", false) == true) {
            Log.d("ZAD_WAKE", "Wake word detected — opening voice screen")
            openVoiceRequest.value = true
        }
        if (intent?.getBooleanExtra("open_transaction_proposals", false) == true) {
            Log.d("ZAD_NOTIF", "Bank proposal notification tapped — opening home")
            openTransactionProposalsRequest.value = true
        }
    }
}

/** كام فشل تحديث توكن متتالي نقبله قبل ما نعتبر الجلسة ماتت فعلاً ونطلب تسجيل دخول. */
private const val MAX_REFRESH_FAILURES_BEFORE_LOGOUT = 3

@Composable
fun AppNavigation(pendingInviteCode: String? = null) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Default to LTR for Auth flow (since user requested English auth screens).
    // The main app is RTL, we handle that in MainScreen.

    // كان مفيش أي استجابة لموت الجلسة (refresh token اترفض/مسحوب من السيرفر — auth-kt
    // بيبعت RefreshFailure(InternalServerError) بعد كده) — التطبيق كان فاضل "مسجل دخول"
    // شكلياً وكل نداء Supabase بعد كده بيفشل 401 صامت (شات العيلة كان أوضح عرض لها لأنه
    // بيستطلع كل ٥ ثواني)، من غير أي إشارة للمستخدم إنه لازم يسجل دخول تاني. مقيّدة بـ
    // route == "main" (المستخدم فعلياً جوه التطبيق) عشان ميتعارضش مع تدفق onLogout العادي
    // (بيعمل NotAuthenticated(isSignOut=true) بنفسه، مستثناة هنا) ولا مع فحص الجلسة الأولي
    // في navigateAfterSplash.
    // عدّاد فشل التحديث المتتالي. RefreshFailure(InternalServerError) مش دليل قاطع إن
    // الجلسة ماتت — أي 5xx عابر من خدمة الـ auth بيوصل بنفس السبب بالظبط، وauth-kt بيفضل
    // بيحاول لوحده بعدها. تسجيل خروج من أول واحدة كان بيطلّع المستخدم من حسابه في نص
    // استخدامه على عطل لحظي (وده اللي المستخدمين بلّغوا عنه: "بيخرج لوحده من الأكونت").
    // بنستنى تلات فشل ورا بعض قبل ما نجبره يسجل دخول تاني؛ أي نجاح بيصفّر العداد.
    var consecutiveRefreshFailures by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        SupabaseRepo.client.auth.sessionStatus.collect { status ->
            if (status is SessionStatus.Authenticated) consecutiveRefreshFailures = 0
            val sessionDiedUnexpectedly = when (status) {
                is SessionStatus.RefreshFailure -> {
                    if (status.cause is RefreshFailureCause.InternalServerError) {
                        consecutiveRefreshFailures++
                        Log.w("AppNavigation", "Token refresh failed ($consecutiveRefreshFailures/$MAX_REFRESH_FAILURES_BEFORE_LOGOUT)")
                        consecutiveRefreshFailures >= MAX_REFRESH_FAILURES_BEFORE_LOGOUT
                    } else false // NetworkError — أوفلاين، مش جلسة ميتة
                }
                is SessionStatus.NotAuthenticated -> !status.isSignOut
                else -> false
            }
            if (sessionDiedUnexpectedly && navController.currentDestination?.route == "main") {
                Log.w("AppNavigation", "Session died unexpectedly ($status) — forcing re-login")
                SupabaseRepo.signOut(context)
                android.widget.Toast.makeText(context, context.getString(R.string.session_expired_message), android.widget.Toast.LENGTH_LONG).show()
                navController.navigate("login") { popUpTo(0) { inclusive = true } }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // بيتحط true طول ما getMarketProfile شغال. من غيره الشاشة بتفضل سودا لثواني
    // على شبكة بطيئة: navigateAfterSplash بيعمل launch وبيرجع من غير ما ينقل حاجة،
    // فالـNavHost يفضل على وجهة اتفضّى مكدسها ومفيش حاجة معروضة.
    // اتشاف في أول اختبار على جهاز حقيقي 2026-09-06.
    var resolvingMarket by remember { mutableStateOf(false) }

    /**
     * تفضية المكدس من **بداية الجراف** بدل popUpTo(0).
     *
     * الصفر بيشيل الجذر نفسه كمان، فبيفضل جزء من إطار مفيش فيه وجهة مركّبة —
     * ومحصلته وميض أسود. البداية بتدي نفس النتيجة (مفيش رجوع للتسجيل) من غير
     * ما تفضّي الجراف نفسه.
     */
    fun NavOptionsBuilder.clearBackStack() {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
    }

    fun goToMainOrOnboarding() {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        navController.navigate(if (session != null) "main" else "onboarding") { clearBackStack() }
    }
    val navigateAfterSplash: () -> Unit = navigate@{
        if (!MarketPrefs.hasSelectedMarket(context)) {
            // المحلي فاضي مش دليل إن المستخدم لسه ما اختارش سوق — ممكن يكون بيانات
            // الجهاز اتمسحت (تحديث/جهاز جديد) بينما السيرفر لسه فاكر اختياره. نسأل
            // السيرفر الأول قبل ما نجبره يختار تاني.
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id
            if (userId != null) {
                resolvingMarket = true
                coroutineScope.launch {
                    try {
                        val (_, serverCountry) = SupabaseRepo.getMarketProfile(userId)
                        val serverMarket = serverCountry?.let { code -> Market.entries.find { it.countryCode == code } }
                        if (serverMarket != null) {
                            MarketPrefs.setMarket(context, serverMarket)
                            goToMainOrOnboarding()
                        } else {
                            navController.navigate("market_selection") { clearBackStack() }
                        }
                    } finally {
                        // finally مش بعد الانتقال: لو النداء رمى، الشاشة لازم ترجع
                        // من التحميل بدل ما تعلّق فيه للأبد.
                        resolvingMarket = false
                    }
                }
                return@navigate
            }
            navController.navigate("market_selection") { clearBackStack() }
            return@navigate
        }
        goToMainOrOnboarding()
    }

    // الغطاء فوق الـNavHost: بيغطي اللحظة اللي مفيش فيها وجهة معروضة بخلفية
    // المصادقة نفسها + مؤشر، فالانتقال يبان "بيحمّل" مش "اتكسر".
    if (resolvingMarket) {
        com.example.ui.components.ZadAuthBackground {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
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
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("main") {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                com.example.MainScreen(
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    pendingInviteCode = pendingInviteCode,
                    openVoiceOnStart = MainActivity.openVoiceRequest.value
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
            com.example.ui.components.ZadAnimatedLogo(modifier = Modifier, size = 80.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "زاد",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryLight
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.slogan),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.splash_privacy),
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
