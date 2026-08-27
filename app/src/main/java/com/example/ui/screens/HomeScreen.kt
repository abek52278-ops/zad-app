package com.example.ui.screens

import com.example.ui.components.ZadCategoryCard
import com.example.ui.components.ZadCategoryType
import com.example.ui.components.ZadSmartBotAgent
import com.example.ui.components.ZadBotEmotion
import com.example.ui.components.ZadWalletHeroCard
import com.example.ui.components.ZadMinimalMetricsDuo
import com.example.ui.components.ZadBezierSpendChart
import com.example.ui.components.ZadQuickExpenseSheet
import com.example.ui.components.LiveMarketTicker
import com.example.ui.components.ZadHorizontalShortcutsRail
import com.example.ui.components.ZadFoodShortagesGlanceCard
import com.example.ui.components.ZadSubscriptionsGlanceCard
import com.example.ui.components.ZadPharmacyGlanceCard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.data.TasbihaTree
import com.example.data.ZadInventory
import com.example.data.ZadTransaction
import com.example.data.SupabaseRepo
import com.example.data.buildHomeActivationProgress
import com.example.data.findActivity
import io.github.jan.supabase.auth.auth
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.Image

import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.FamilyState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.widgets.AffiliateProductCard

import com.example.ui.components.*

private const val TAG_HOME = "HomeScreen"

@Composable
fun HomeScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel = viewModel(),
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToShopping: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToTasbiha: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPharmacy: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    /** فتح شاشة "البلد والعملة" مباشرة — لما رؤية زاد تكون عن عملة/بلد غير معروفين
     * (زاد-برين بيكتب "غير معروف" ويطلب من العميل يحدده في الإعدادات)، بدل ما نسيب
     * المستخدم يدوّر بنفسه على الشاشة الصح. */
    onNavigateToCurrencySettings: () -> Unit = {},
    onNavigateToMaintenance: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    /** تفعيل يدوي من الأب/الأم (Switch to Kids Mode) — بيفرض واجهة الأطفال حتى لو role الحساب "admin" */
    kidsModeOverride: Boolean = false
) {
    val inventory by viewModel.inventory.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    // عرض بس: بعض البنوك بتسجل إشعارات إعلانية (عروض 4G وغيرها) كمعاملة بقيمة 0 جنيه —
    // سطر زيرو مايغيرش أي مجموع أصلاً، فالمجاميع/التحليل لسه بيشتغلوا على transactions
    // الكاملة؛ الفلترة هنا للقايمة المعروضة بس عشان مايبانش كإدخال مالي حقيقي.
    val visibleTransactions = transactions.filter { it.amount != 0.0 }
    val subscriptions by viewModel.subscriptions.collectAsState()
    val mealSuggestions by viewModel.mealSuggestions.collectAsState()
    val chefRecipes by viewModel.chefRecipes.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val zadInsights by viewModel.zadInsights.collectAsState()
    val zadFacts by viewModel.zadFacts.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val budgetConfirmed by viewModel.budgetConfirmed.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val availableFigure by viewModel.availableFigure.collectAsState()
    val balanceFigure by viewModel.balanceFigure.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val daysLeftInCycle by viewModel.daysLeftInCycle.collectAsState()
    val cycleStart by viewModel.cycleStart.collectAsState()
    val cycleEnd by viewModel.cycleEnd.collectAsState()
    val showBudgetDialog by viewModel.showBudgetDialog.collectAsState()
    val shoppingList by viewModel.shoppingList.collectAsState()
    val pharmacyItems by viewModel.pharmacyItems.collectAsState()
    val affiliateProducts by viewModel.affiliateProducts.collectAsState()
    val affiliatePicks by viewModel.affiliatePicks.collectAsState()
    val affiliateSearchNeeds by viewModel.affiliateSearchNeeds.collectAsState()
    val urgentRecipes by viewModel.urgentRecipes.collectAsState()
    val upcomingSeasonalEvents by familyViewModel.upcomingSeasonalEvents.collectAsState()
    val seasonalForecasts by viewModel.seasonalForecasts.collectAsState()
    val expensePrediction by viewModel.expensePrediction.collectAsState()
    val outingSuggestion by viewModel.outingSuggestion.collectAsState()
    val weeklyAdherence by viewModel.weeklyAdherencePercent.collectAsState()
    val nextObligationDue by viewModel.nextObligationDue.collectAsState()
    val agentSummary by viewModel.agentSummary.collectAsState()
    val isAgentLoading by viewModel.isAgentLoading.collectAsState()
    val autoSuggestions by viewModel.autoSuggestions.collectAsState()
    val inventoryCheckIns by viewModel.inventoryCheckIns.collectAsState()
    val pendingGroceryPurchase by viewModel.pendingGroceryPurchase.collectAsState()
    val transactionProposals by viewModel.transactionProposals.collectAsState()
    val resolvingTransactionProposals by viewModel.resolvingTransactionProposals.collectAsState()
    val failedTransactionProposals by viewModel.failedTransactionProposals.collectAsState()
    val liveMarketPrices by viewModel.livePrices.collectAsState()
    val marketFetchState by viewModel.marketPricesFetchState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLiveMarketPrices()
    }

    // "مصروف" في كارت الميزانية لازم يكون مصروف نفس الدورة اللي "متاح" اتحسب عليها.
    // كان BudgetMath.totalExpense — إجمالي كل المعاملات من أول يوم في التطبيق — جنب
    // "متاح" المحسوب على الدورة، فالكارت كان بيعرض رقمين مالهمش علاقة ببعض وبيكبر
    // للأبد. spentThisCycle هو نفس الرقم اللي ZadViewModel بيطرحه من الميزانية.

    val shortageCount = remember(inventory) {
        val lowStock = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }
        val expiring = inventory.filter { item ->
            val d = daysUntilExpiry(item.expiryDate)
            d != null && d <= 3
        }
        (lowStock + expiring).distinctBy { it.id }.size
    }

    val displayChefRecipes = remember(chefRecipes) {
        if (chefRecipes.isNotEmpty()) chefRecipes
        else listOf(
            com.example.data.ZadRecipe(
                id = "def_1",
                name = "كبسة دجاج سريعة",
                description = "وجبة غداء شهية ومكتملة من الأرز والدجاج والبهارات العربية",
                prepTimeMinutes = 35,
                difficulty = "سهل",
                missingIngredients = listOf("أرز بسمتي", "دجاج طازج"),
                imageUrl = "https://images.pexels.com/photos/1624487/pexels-photo-1624487.jpeg"
            ),
            com.example.data.ZadRecipe(
                id = "def_2",
                name = "مكرونة بالصلصة والجبن",
                description = "طبق عشاء سريع ولذيذ في 15 دقيقة بمكونات متوفرة",
                prepTimeMinutes = 15,
                difficulty = "سريع",
                missingIngredients = listOf("مكرونة", "جبنة موزاريلا"),
                imageUrl = "https://images.pexels.com/photos/1279330/pexels-photo-1279330.jpeg"
            ),
            com.example.data.ZadRecipe(
                id = "def_3",
                name = "شوربة خضار مشكل",
                description = "شوربة صحية ودافئة غنية بالفيتامينات والمعادن",
                prepTimeMinutes = 20,
                difficulty = "صحي",
                missingIngredients = emptyList(),
                imageUrl = "https://images.pexels.com/photos/539451/pexels-photo-539451.jpeg"
            )
        )
    }

    val displayAffiliatePicks = remember(affiliatePicks, affiliateProducts) {
        if (affiliatePicks.isNotEmpty()) {
            affiliatePicks
        } else {
            val active = affiliateProducts.filter { it.isActive }
            if (active.isNotEmpty()) {
                active.map { com.example.ui.viewmodels.AffiliatePick(product = it, reason = "منتج موصى به للعائلة") }
            } else {
                listOf(
                    com.example.ui.viewmodels.AffiliatePick(
                        product = com.example.data.AffiliateProduct(
                            id = "az_oil",
                            name = "زيت زيتون بكر ممتاز 1 لتر",
                            category = "بقالة",
                            price = 38.5,
                            currency = "SAR",
                            imageUrl = "https://m.media-amazon.com/images/I/71wE6bHqLqL._AC_SL1500_.jpg",
                            affiliateUrl = "https://www.amazon.sa/dp/B08XYZ1234",
                            isActive = true
                        ),
                        reason = "أفضل قيمة لمطبخك"
                    ),
                    com.example.ui.viewmodels.AffiliatePick(
                        product = com.example.data.AffiliateProduct(
                            id = "az_rice",
                            name = "أرز بسمتي هندي فاخر 5 كجم",
                            category = "بقالة",
                            price = 45.0,
                            currency = "SAR",
                            imageUrl = "https://m.media-amazon.com/images/I/61abc123XYZ._AC_SL1500_.jpg",
                            affiliateUrl = "https://www.amazon.sa/dp/B08XYZ5678",
                            isActive = true
                        ),
                        reason = "عرض شهري خاص"
                    ),
                    com.example.ui.viewmodels.AffiliatePick(
                        product = com.example.data.AffiliateProduct(
                            id = "az_soap",
                            name = "مسحوق غسيل أوتوماتيك مركز 3 كجم",
                            category = "منظفات",
                            price = 52.0,
                            currency = "SAR",
                            imageUrl = "https://m.media-amazon.com/images/I/81XYZclean._AC_SL1500_.jpg",
                            affiliateUrl = "https://www.amazon.sa/dp/B08XYZ9999",
                            isActive = true
                        ),
                        reason = "توفير لاحتياج المنزل"
                    )
                )
            }
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val marketSyncFailedText = stringResource(R.string.changes_save_failed)
    var isNotificationAccessGranted by remember {
        mutableStateOf(
            androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context).contains(context.packageName)
        )
    }
    var bankReaderConnectedAt by remember {
        mutableStateOf(com.example.data.BankReadingStatus.lastConnectedAt(context))
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = androidx.core.app.NotificationManagerCompat
                    .getEnabledListenerPackages(context).contains(context.packageName)
                bankReaderConnectedAt = com.example.data.BankReadingStatus.lastConnectedAt(context)
                viewModel.loadTransactionProposals()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val familyState by familyViewModel.state.collectAsState()
    val userNameState by viewModel.userName.collectAsState()
    
    val isChild = remember(familyState, kidsModeOverride) {
        kidsModeOverride || if (familyState is FamilyState.Active) {
            val activeState = familyState as FamilyState.Active
            activeState.myMemberInfo.role == "child"
        } else false
    }

    LaunchedEffect(Unit) {
        if (userNameState.isNullOrBlank()) viewModel.loadUserProfile()
        viewModel.refreshAgentSummary()
        viewModel.refreshAutoSuggestions()
        viewModel.predictNextMonthExpenses()
        viewModel.refreshOutingSuggestion()
        viewModel.loadZadInsights()
        viewModel.loadTransactionProposals()
        familyViewModel.loadUpcomingSeasonalEvents()
        Log.d(TAG_HOME, "HomeScreen loaded — userName=$userNameState, budget=$budget, transactions=${transactions.size}, isChild=$isChild")
    }

    LaunchedEffect(upcomingSeasonalEvents) {
        if (upcomingSeasonalEvents.isNotEmpty()) viewModel.loadSeasonalForecast(upcomingSeasonalEvents)
    }

    val userName = userNameState ?: "..."

    var showAllTransactionsDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showQuickDeductDialog by remember { mutableStateOf(false) }
    var showWhySheet by remember { mutableStateOf(false) }
    var showQuickExpenseSheet by remember { mutableStateOf(false) } // Task 27.2 — طول الضغط على "متاح"
    var showTelegramSheet by remember { mutableStateOf(false) } // بوت تليجرام — اتنقل من البروفايل للرئيسية
    var selectedRecipeTitle by remember { mutableStateOf<String?>(null) }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showTasbihaReminder by remember { mutableStateOf(false) }
    // أدوات الرئيسية (شيف زاد + أمازون + التوقعات) ظاهرة افتراضيًا — كانت مدفونة
    // وراء قسم مطوي فالمستخدمين مكانوش بيشوفوها خالص.
    var showHomeTools by rememberSaveable { mutableStateOf(true) }

    // Use FamilyViewModel's tasbiha data instead of direct SupabaseRepo call
    val myTasbiha = familyViewModel.myTasbiha
    LaunchedEffect(Unit) {
        familyViewModel.loadTasbiha()
    }

    // No local canvas here any more — MainScreen paints ZadCanvasBackground once behind
    // the whole Scaffold so every screen shares the mockup's one gradient.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (isChild) {
            // KIDS MODE UI
            val needAmountPattern = stringResource(R.string.need_amount_purchase)
            KidsModeContent(
                familyState = familyState as? FamilyState.Active,
                onAddRequest = { title, amount ->
                    val meta = """{"amount":$amount,"status":"PENDING"}"""
                    familyViewModel.sendMessage(String.format(needAmountPattern, amount, title), "PURCHASE_REQUEST", meta)
                },
                onNavigateToFamily = onNavigateToFamily,
                onNavigateToTasbiha = onNavigateToTasbiha,
                myTasbiha = myTasbiha,
                affiliateProducts = affiliateProducts,
                viewModel = viewModel,
                familyViewModel = familyViewModel
            )
        } else {
            // ADULT/ADMIN MODE UI
            Spacer(modifier = Modifier.height(16.dp))

            // Home is ordered around trust and completion: setup, balance, actions,
            // decisions, then history. Secondary modules stay available behind one
            // explicit disclosure instead of competing for attention on every launch.
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — اقتراح تحويل السوق لو
                // بلد الشبكة الحالي مختلف عن السوق المختار. اقتراح بس، مفيش تبديل صامت —
                // التجاهل بيتذكر لنفس البلد المكتشف بس (SharedPreferences)، فمابيرجعش
                // يزعج في كل فتح للتطبيق لحد ما البلد يتغير تاني فعلاً.
                var dismissedTravelCountry by remember {
                    mutableStateOf(context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
                        .getString("dismissed_travel_country", null))
                }
                val detectedCountry = remember { com.example.data.TravelDetector.detectCurrentCountryCode(context) }
                val suggestedMarket = remember(detectedCountry, dismissedTravelCountry) {
                    if (detectedCountry != null && detectedCountry == dismissedTravelCountry) null
                    else com.example.data.TravelDetector.suggestedMarketFor(context)
                }
                if (suggestedMarket != null) {
                    com.example.ui.components.TravelBanner(
                        suggestedMarket = suggestedMarket,
                        onSwitch = {
                            val previousMarket = com.example.data.MarketPrefs.currentMarket
                            com.example.data.MarketPrefs.setMarket(context, suggestedMarket)
                            viewModel.convertLimitsForMarketChange(context, previousMarket, suggestedMarket)
                            scope.launch {
                                val synced = com.example.data.SupabaseRepo.syncMarketProfile(suggestedMarket)
                                if (!synced) {
                                    android.util.Log.i("HomeScreen", "Travel market profile sync offline — enqueued to SyncOutbox")
                                    com.example.data.SyncOutbox.enqueueMarketProfile(context, suggestedMarket.currencyCode, suggestedMarket.countryCode)
                                }
                            }
                            dismissedTravelCountry = detectedCountry
                            context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("dismissed_travel_country", detectedCountry).apply()
                            context.findActivity()?.recreate()
                        },
                        onDismiss = {
                            dismissedTravelCountry = detectedCountry
                            context.getSharedPreferences("zad_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("dismissed_travel_country", detectedCountry).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val isOnline by com.example.data.NetworkMonitor.isOnline.collectAsState()
                if (!isOnline) {
                    OfflineBanner()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── 0b. مساعد زاد الذكي الحية — ZadSmartBotAgent بمشاعر وعيون مضيئة 3D ──
                com.example.ui.components.AppearOnEntry {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF064E3B).copy(alpha = 0.08f))
                            .border(1.dp, Color(0xFF0F9B76).copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable {
                                com.example.voice.ZadCutePetSoundFx.play(com.example.voice.ZadCutePetSoundFx.PetSound.HappyChirp)
                                onOpenVoice()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZadSmartBotAgent(
                            sizeDp = 60.dp,
                            emotion = ZadBotEmotion.IDLE,
                            onClick = onOpenVoice
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.greeting_hi_name, userName),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "مساعدك الذكي جاهز • اضغط للتحدث 🎙️",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F9B76)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                val activationProgress = buildHomeActivationProgress(
                    hasConfirmedBalance = budgetConfirmed && balanceFigure != null,
                    bankReadingEnabled = isNotificationAccessGranted && bankReaderConnectedAt != null,
                    hasInventory = inventory.isNotEmpty(),
                )
                if (!activationProgress.isComplete) {
                    com.example.ui.components.HomeActivationCard(
                        progress = activationProgress,
                        onSetBalance = { viewModel.showBudgetDialog() },
                        onEnableBankReading = {
                            if (isNotificationAccessGranted) {
                                com.example.data.BankReadingStatus.requestRebindIfPermitted(context)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                        onAddInventoryItem = onNavigateToInventory,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // مرحلة ٣ — سؤال متابعة مخزون تفاعلي (أعلى مرشّح بس، عشان مايبقاش إلحاح)
                inventoryCheckIns.firstOrNull()?.let { candidate ->
                    com.example.ui.components.InventoryCheckInCard(
                        candidate = candidate,
                        onDecrement = { viewModel.answerCheckInDecrement(it) },
                        onFinished = { viewModel.answerCheckInFinished(it) },
                        onStillHave = { viewModel.answerCheckInStillHave(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── 0. شريط الأسعار الحي التلقائي (Live Market Ticker) ──
                com.example.ui.components.AppearOnEntry {
                    LiveMarketTicker(
                        prices = liveMarketPrices,
                        fetchState = marketFetchState,
                        onRetry = { viewModel.refreshLiveMarketPrices() }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ── 1. كارت المحفظة الفاخر بنمط Apple Wallet (ZadWalletHeroCard) ──
                val availableBal = availableFigure?.value ?: balanceFigure?.value ?: 0.0
                val spentMonth = com.example.data.BudgetMath.spentThisMonth(visibleTransactions)
                val safeDailySpendVal = remember(availableBal, daysLeftInCycle) {
                    if (daysLeftInCycle > 0) availableBal / daysLeftInCycle else availableBal
                }

                com.example.ui.components.AppearOnEntry {
                    ZadWalletHeroCard(
                        availableBalance = availableBal,
                        spentThisCycle = spentMonth,
                        committedThisCycle = committed,
                        isConfident = availableFigure?.confident ?: true,
                        onEditBudget = { viewModel.showBudgetDialog() },
                        onWhyChanged = { showWhySheet = true },
                        onQuickExpense = { showQuickExpenseSheet = true }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ── 2. مؤشرات الأيام المتبقية والصرف اليومي الآمن (ZadMinimalMetricsDuo) ──
                com.example.ui.components.AppearOnEntry(delayMs = 40) {
                    ZadMinimalMetricsDuo(
                        safeDailySpend = safeDailySpendVal,
                        daysLeft = daysLeftInCycle
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // ── 3. شريط الاختصارات السريعة الأفقي (Horizontal Shortcuts Rail) ──
                com.example.ui.components.AppearOnEntry(delayMs = 50) {
                    ZadHorizontalShortcutsRail(
                        onNavigateToInventory = onNavigateToInventory,
                        onNavigateToShopping = onNavigateToShopping,
                        onNavigateToFamily = onNavigateToFamily,
                        onNavigateToSubscriptions = onNavigateToSubscriptions,
                        onNavigateToPharmacy = onNavigateToPharmacy,
                        onNavigateToMaintenance = onNavigateToMaintenance,
                        onNavigateToTasbiha = onNavigateToTasbiha
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 4. إيدج زاد بريميوم الجذاب بدون إعلانات (Zad Premium Promo Hero Card) ──
                com.example.ui.components.AppearOnEntry(delayMs = 58) {
                    com.example.ui.components.ZadPremiumPromoCard(
                        onUpgradeClick = onNavigateToPlans
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 5. إيدج صحة المخزون والنواقص الحية (Food Health & Shortages) ──
                com.example.ui.components.AppearOnEntry(delayMs = 65) {
                    ZadFoodShortagesGlanceCard(
                        inventory = inventory,
                        onViewAllClick = onNavigateToInventory,
                        onConfirmItem = { viewModel.answerCheckInDecrement(it) }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 5. إيدج الاشتراكات الشهرية والتجديدات (Subscriptions Glance) ──
                com.example.ui.components.AppearOnEntry(delayMs = 75) {
                    ZadSubscriptionsGlanceCard(
                        subscriptions = subscriptions,
                        onViewAllClick = onNavigateToSubscriptions
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 6. إيدج الصيدلية والجرعات المنتظمة (Pharmacy & Doses Glance) ──
                com.example.ui.components.AppearOnEntry(delayMs = 85) {
                    ZadPharmacyGlanceCard(
                        pharmacyItems = pharmacyItems,
                        onViewAllClick = onNavigateToPharmacy
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 7. بستان التسبيح التفاعلي (Tasbiha Garden) ──
                com.example.ui.components.AppearOnEntry(delayMs = 95) {
                    TasbihaHomeWidget(
                        tree = myTasbiha,
                        onTasbih = { familyViewModel.tasbihaClick() },
                        onNavigateToTasbiha = onNavigateToTasbiha,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // ── 8. شيف زاد الذكي للوجبات السريعة (Smart Chef Section) ──
                com.example.ui.components.AppearOnEntry(delayMs = 105) {
                    SmartChefSection(
                        suggestions = mealSuggestions,
                        onViewAll = onNavigateToAssistant,
                        onOpenRecipe = { title ->
                            selectedRecipeTitle = title
                            showRecipeDialog = true
                        },
                        recipes = displayChefRecipes,
                        onAddMissingToShopping = { missing ->
                            missing.forEach { name ->
                                viewModel.addShoppingItem(
                                    com.example.data.ZadShoppingItem(itemName = name, quantity = 1),
                                )
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                if (transactionProposals.isNotEmpty()) {
                    Text(
                        stringResource(R.string.bank_proposals_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        transactionProposals.forEach { proposal ->
                            com.example.ui.widgets.TransactionProposalCard(
                                proposal = proposal,
                                resolving = proposal.id in resolvingTransactionProposals,
                                failed = proposal.id in failedTransactionProposals,
                                onDecision = { decision -> viewModel.resolveTransactionProposal(proposal.id, decision) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── 4. Insights (mockup: translucent glass rows, dot + text + tag) ──
                // أهم تنبيهات عقل زاد — zad_insights كان مكتوب من زاد-برين وميتقراش
                // خالص، فالتحليل والتنبيهات ما كانتش توصل هنا. دي أول محطة ليها.
                val homeInsights = zadInsights
                    .filter { it.surface == "home_card" }
                    .sortedByDescending { it.priority == "critical" }
                    .take(3)
                if (homeInsights.isNotEmpty()) {
                    Text(
                        stringResource(R.string.zad_smart_insight_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        homeInsights.forEach { insight ->
                            if (insight.kind == "question") {
                                com.example.ui.widgets.ZadQuestionCard(
                                    insight = insight,
                                    onAnswer = { answer -> viewModel.answerBrainQuestion(insight, answer) },
                                    onDismiss = { viewModel.dismissInsight(insight.id) },
                                    onOpenCamera = onNavigateToCamera
                                )
                                return@forEach
                            }
                            // mockup: translucent white glass row, 16dp radius, a colored
                            // priority dot (not an icon), title + body, and a pill tag on
                            // the trailing edge.
                            val isCritical = insight.priority == "critical"
                            val accent = if (isCritical) dangerColor else primary
                            com.example.ui.components.GlassCard(
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color.White.copy(alpha = 0.85f),
                                contentPadding = 0.dp,
                                modifier = Modifier.clickable { onNavigateToNotifications() }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                            .zadDotPulse()
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(insight.title, style = Typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                                        Text(insight.body, style = Typography.bodyMedium, color = onSurfaceVariant, maxLines = 2)
                                        // زاد-برين بيكتب الرؤية دي بنص حر ("العملة/البلد غير
                                        // معروفين") مش عبر actionType مخصص، فمفيش حقل هيكلي
                                        // نربط عليه — مطابقة كلمات مفتاحية عشان زر "الإعدادات"
                                        // يودّي المستخدم للشاشة الصح بدل ما يدوّر بنفسه.
                                        val isCurrencyInsight = remember(insight.id) {
                                            val haystack = insight.title + " " + insight.body
                                            haystack.contains("عملة") || haystack.contains("البلد") ||
                                                haystack.contains("currency", ignoreCase = true) ||
                                                haystack.contains("country", ignoreCase = true)
                                        }
                                        if (isCurrencyInsight) {
                                            TextButton(
                                                onClick = onNavigateToCurrencySettings,
                                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(stringResource(R.string.country_and_currency), style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // mockup: a kind tag pill on the trailing edge, tinted to
                                    // match the dot. The mockup has three (معلومة/مطلوب/تحذير)
                                    // because its data is hardcoded; ZadInsight.priority only
                                    // has normal|critical, so inventing a third would mean
                                    // inventing a state the brain never emits.
                                    Text(
                                        stringResource(
                                            if (isCritical) R.string.insight_tag_alert else R.string.insight_tag_info
                                        ),
                                        style = Typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Task 28 — "رفض بمعنى": بدل رفض صامت، ٣ خيارات بسبب فعلي
                                    var showDismissMenu by remember(insight.id) { mutableStateOf(false) }
                                    IconButton(onClick = { showDismissMenu = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
                                    }
                                    com.example.ui.components.DismissReasonMenu(
                                        expanded = showDismissMenu,
                                        onDismissRequest = { showDismissMenu = false },
                                        onReasonSelected = { reason -> viewModel.dismissInsightWithReason(insight, reason) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // The visible home ends its core loop with proof of what Zad recorded.
                // Users can verify the latest entries before opening analytics or extras.
                PremiumTransactionsRow(
                    transactions = visibleTransactions,
                    onSeeAllClick = { showAllTransactionsDialog = true },
                )
                Spacer(modifier = Modifier.height(18.dp))



                // ── 6. Dark AI summary card (mockup: #052E16, mint title, chips) ──
                agentSummary?.let { summary ->
                    // كانت فيه كورة تانية هنا فوق كارت الملخص. `FloatingMascotCompanion`
                    // (MainScreen) عايمة فوق كل الشاشات، فلما المحتوى بيتمرر النسخة دي كانت
                    // بتوصل جنبها وتبان كورتين فوق بعض — والجوّانية ساكنة ومش بترد على اللمس،
                    // فاللي بيدوس عليها بيحس إن الأيجنت باظ. كورة واحدة، هي اللي بتتفاعل.
                    AgentSummaryCard(
                        agentSummary = summary,
                        isLoading = isAgentLoading,
                        onRefresh = { viewModel.refreshAgentSummary() },
                        onNavigateToAssistant = onNavigateToAssistant,
                        onNavigateToShopping = onNavigateToShopping,
                        onNavigateToInventory = onNavigateToInventory
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // (المخزون/النواقص/التسبيحة/شيف زاد/الصيدلية/الاشتراكات نقلوا لكتلة ٣c فوق)

                // ── 9. Amazon picks (mockup: 140dp fixed-width cards in a horizontal
                // rail). AffiliateProductCard is `fillMaxWidth()` + its own 16dp margins,
                // so putting it inside a LazyRow gave every card the full viewport width —
                // that's the "overlapping cards" in the design review. ZadAmazonDealCard
                // is the mockup's actual rail card and has a fixed width.
                // كان `affiliateProducts.filter { it.isActive }.take(8)` — أول ٨ صفوف في
                // الكتالوج، نفسهم لكل مستخدم، بترتيب الجدول، مالهمش أي علاقة بمخزونه.
                // دلوقتي كل كارت لازم يكون مربوط بنقص حقيقي (صنف خلص/قارب يخلص/في قايمة
                // التسوق) والسبب مكتوب على الكارت. مفيش نقص = القسم كله مايظهرش.
                // ...وده كان بيخفي القسم بالكامل. الكتالوج **خمس منتجات** (زيت، حليب،
                // أرز، شاي، سكر)، والعيلة دي ناقصها مياه وبيض ولحمة وفراخ — صفر تطابق،
                // فقسم أمازون مابانش ولا مرة. الشرط "لازم نقص حقيقي" صح ويفضل؛ اللي اتصلح
                // إن النقص اللي مالوش صف في الكتالوج بقى يتعرض كبحث بالتاج بدل ما يتبلع.
                // ── 9. تسوق أمازون والعروض الموصى بها (Amazon Smart Deals Rail) ──
                if (displayAffiliatePicks.isNotEmpty() || affiliateSearchNeeds.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🛍️", fontSize = 16.sp)
                            Text(stringResource(R.string.shop_from_amazon), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (displayAffiliatePicks.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayAffiliatePicks) { pick ->
                                com.example.ui.widgets.ZadAmazonDealCard(
                                    product = pick.product,
                                    reason = pick.reason,
                                    onClick = {
                                        viewModel.recordAffiliateClick(pick.product.id, "home")
                                        com.example.data.AffiliateHelper.openProduct(context, pick.product)
                                    }
                                )
                            }
                        }
                    }
                    if (affiliateSearchNeeds.isNotEmpty()) {
                        if (displayAffiliatePicks.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(affiliateSearchNeeds) { need ->
                                com.example.ui.widgets.ZadAmazonSearchChip(
                                    itemName = need.itemName,
                                    reason = need.reason,
                                    onClick = {
                                        com.example.data.AffiliateHelper.open(
                                            context,
                                            com.example.data.AffiliateHelper.productUrl(
                                                asin = null,
                                                fallbackSearchTerm = need.itemName,
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
                // ── 10. Recent transactions — اتشالت النسخة المكررة. PremiumTransactionsRow
                // فوق (بعد insights) هو العرض الرسمي: تواريخ نسبية بالعربي (اليوم/قبل يومين)
                // مش ISO خام، وهو اللي بيطابق البروتوتايب `.tx` حرفياً.

                // ── Beyond the mockup ──────────────────────────────────────────────
                // Cards Zad has and the mockup doesn't. They stay (each one is backed by
                // real data the app computes), but they now sit below the mockup sequence
                // as one block instead of being scattered between its sections.

                // كارت الكاش (Task 19.4) اتشال بناءً على طلب العميل — "الإيدج بتاع الـ5000
                // الكاش مش عاوزه". السحب النقدي لسه بيتصنّف transfer→cash وبيفضل برّه
                // `spent` (BudgetMath.cashOnHand لسه بيحسبه، والعقل لسه بيشوفه) — اللي
                // اتشال هو الكارت اللي كان بيعرضه على الشاشة الرئيسية وشرايح العادات اللي
                // جواه، مش دفتر الكاش نفسه.

                // (النواقص نقلت لكتلة ٣c فوق)

                // "Warning" = budget-at-risk (analyzeBudgetOverruns/analyzeSubscriptionUsage),
                // "Alert" = anomaly spending — both belong on "الميزانية في خطر"'s banner, not
                // just Alert. Was filtering Warning out entirely, so budget-at-risk content was
                // computed correctly but never shown here (only visible in ZadIntelligenceScreen).
                val alerts = insights.filter { it.type == "Alert" || it.type == "Warning" }
                    .sortedBy { if (it.type == "Alert") 0 else 1 }
                if (alerts.isNotEmpty()) {
                    AiAlertBanner(title = alerts.first().title, description = alerts.first().description)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (showHomeTools && autoSuggestions.isNotEmpty()) {
                    AutoSuggestionsCard(suggestions = autoSuggestions)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // رادار المناسبات (family-only: needs cross-member transaction history)
                if (showHomeTools && familyState is FamilyState.Active && seasonalForecasts.isNotEmpty()) {
                    EventsRadarCard(forecasts = seasonalForecasts)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // PredictionCard بيقارن توقّع الشهر الجاي بالسقف نفسه (budget)، مش بمتبقي
                // الدورة — سقف <= 0 يبقى "غير معروف" أصلاً فمفيش كارت يتعرض من غير معنى.
                if (showHomeTools && expensePrediction != null && budget > 0) {
                    PredictionCard(expensePrediction!!, budget)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // زاد مش رقيب مالي بس — لو المتاح لسه صحي، بيرشح خروجة قريبة (OutingSuggestionCard
                // بتتخفي تلقائياً لو مفيش اقتراح، مافيش حالة "فاضي" تتعرض هنا).
                if (showHomeTools) {
                    outingSuggestion?.let { spot ->
                        OutingSuggestionCard(spot = spot)
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                // (المخزون السريع نقل لكتلة ٣c فوق)

                if (showHomeTools) {
                    MiniShoppingWidget(shoppingList = shoppingList, onNavigateToShopping = onNavigateToShopping)
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // العقل → الوصفات: "عندك دجاج هينتهي بكرة → 3 وصفات بيه"
                urgentRecipes?.let { urgent ->
                    UrgentRecipeCard(
                        triggerItems = urgent.triggerItems,
                        text = urgent.text,
                        // Task 23 — لو كل الأصناف المحفّزة راكدة (مفيش صنف هيخلص/ينتهي فعلاً)،
                        // العنوان يتغيّر — "هيخلص قريب" غلط لصنف حد ناسيه من شهر مش هيتلف بكرة.
                        isStagnantOnly = urgent.stagnantItems.isNotEmpty() && urgent.stagnantItems.size == urgent.triggerItems.size,
                        onOpenChat = onNavigateToAssistant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                Spacer(modifier = Modifier.height(84.dp))
            } // closes inner Column
        } // closes else block (line 125)
    } // closes outer Column (line 103)

        // الصفحة الرئيسية كانت من غير FAB خالص رغم إنها أكتر شاشة بيتفتح — مقفولة في وضع
        // الأطفال لأن طلب الشراء بتاعهم بيحصل من KidsModeContent (onAddRequest) مش من هنا.
        if (!isChild) {
            // "خصم سريع" هو الزرار الأساسي دلوقتي، مش إضافة معاملة كاملة. السبب إن أغلب
            // المصاريف اللي بتضيع مش اللي العميل قاعد يصنّفها — هي اللي بيدفعها وهو واقف
            // والنظام ما شافهاش. الفورم الكامل (عنوان/فئة/دخل ولا مصروف) لسه موجود في
            // الزرار الصغير فوقه، بس مابقاش هو الافتراضي.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 88.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showAddTransactionDialog = true },
                    containerColor = surface,
                    contentColor = primary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.add_action))
                }
                ExtendedFloatingActionButton(
                    onClick = { showQuickDeductDialog = true },
                    containerColor = primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(
                            stringResource(R.string.quick_deduct),
                            style = Typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
} // closes Box
    if (showQuickDeductDialog) {
        QuickDeductDialog(
            currentBalance = remainingBalance,
            onDismiss = { showQuickDeductDialog = false },
            onDeduct = { amount, note ->
                Log.d(TAG_HOME, "QuickDeductDialog → amount=$amount")
                viewModel.quickDeduct(amount, note)
                showQuickDeductDialog = false
            }
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            onDismiss = { showAddTransactionDialog = false },
            onSave = { amount, title, isExpense, category ->
                viewModel.addTransaction(
                    com.example.data.ZadTransaction(
                        amount = amount, title = title,
                        isExpense = isExpense, category = category, isVerified = true
                    )
                )
                showAddTransactionDialog = false
            }
        )
    }

    if (showTelegramSheet) {
        com.example.ui.components.TelegramBotSheet(onDismiss = { showTelegramSheet = false })
    }

    
    if (showQuickExpenseSheet) {
        com.example.ui.components.ZadQuickExpenseSheet(
            onDismiss = { showQuickExpenseSheet = false },
            onSend = { name, query, amount ->
                viewModel.addTransaction(
                    amount = amount,
                    title = name,
                    isExpense = true,
                    category = if (query.isNotBlank()) query else "Other"
                )
                showQuickExpenseSheet = false
            }
        )
    }

    if (showWhySheet) {
        com.example.ui.components.WhyChangedSheet(onDismiss = { showWhySheet = false })
    }

    if (showBudgetDialog) {
        val cycleExpenseTxs = transactions
            .filter { tx ->
                tx.txnKind == "expense" && tx.createdAt?.let { raw ->
                    try {
                        val d = java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        !d.isBefore(cycleStart) && d.isBefore(cycleEnd)
                    } catch (e: Exception) { false }
                } == true
            }
            .sortedByDescending { it.createdAt ?: "" }
        BudgetEditSheet(
            currentBalance = remainingBalance,
            committed = committed,
            expenseTransactions = cycleExpenseTxs,
            onDismiss = { viewModel.hideBudgetDialog() },
            onSave = { newBalance ->
                Log.d(TAG_HOME, "BudgetEditSheet SAVE → newBalance=$newBalance → calling viewModel.setBalanceTo()")
                viewModel.setBalanceTo(newBalance)
                viewModel.hideBudgetDialog()
            },
            onEditCategory = { id, newCategory -> viewModel.updateTransactionCategory(id, newCategory) },
            onDeleteTransaction = { id -> viewModel.deleteTransaction(id) }
        )
    }

    if (showAllTransactionsDialog) {
        AlertDialog(
            onDismissRequest = { showAllTransactionsDialog = false },
            title = { Text(stringResource(R.string.recent_transactions_full_log)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(visibleTransactions.reversed()) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(tx.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(tx.category ?: "Other", style = Typography.labelSmall, color = Color.Gray)
                            }
                            Text(
                                text = "${if (tx.isExpense) "-" else "+"}${tx.amount}",
                                style = Typography.bodyMedium,
                                color = if (tx.isExpense) dangerColor else successColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllTransactionsDialog = false }) { Text(stringResource(R.string.close_action)) }
            }
        )
    }
    if (showRecipeDialog && selectedRecipeTitle != null) {
        RecipeDetailDialog(
            recipeTitle = selectedRecipeTitle!!,
            inventory = inventory,
            onDismiss = { showRecipeDialog = false }
        )
    }

    // مرحلة ٣ — معاملة بقالة جديدة تفتح سؤال "ضيف إيه للمخزون؟"
    pendingGroceryPurchase?.let { tx ->
        com.example.ui.components.GroceryPurchasePromptDialog(
            transaction = tx,
            inventory = inventory,
            onAddItem = { itemName -> viewModel.addGroceryPurchaseItem(itemName) },
            onDismiss = { viewModel.dismissPendingGroceryPurchase() }
        )
    }
}

/**
 * "خصم سريع" — مبلغ واحد بيتشال من الرصيد على طول.
 *
 * الغرض منه مش إنه اختصار لـ [AddTransactionDialog]، الغرض إنه **مخرج**. النظام بيقرا
 * رسايل البنك ويسمع من البوت ويستنتج، وكل واحدة من الطرق دي بتفوّت حاجة أحياناً — ومن غير
 * طريقة يدوية سريعة، العميل بيفضل شايف رقم يعرف إنه غلط ومش قادر يعمل فيه حاجة. الحقل
 * الوحيد المطلوب هو المبلغ؛ الملاحظة اختيارية عشان السطر يبقى مفهوم في السجل بعدين، مش
 * عشان التصنيف (كله بيروح "أخرى" — انظر QUICK_DEDUCT_CATEGORY).
 *
 * `currentBalance` للمعاينة بس. الطرح الحقيقي بيحصل في ZadViewModel.quickDeduct عن طريق
 * معاملة مصروف عادية، فالرقم اللي بيتعرض هنا لازم يطابق اللي هيظهر بعد الحفظ من غير ما
 * الشاشة تحسب حاجة لوحدها.
 */
@Composable
fun QuickDeductDialog(
    currentBalance: Double?,
    onDismiss: () -> Unit,
    onDeduct: (amount: Double, note: String?) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountStr.toDoubleOrNull()
    val valid = amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = primary) },
        title = { Text(stringResource(R.string.quick_deduct), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.quick_deduct_hint),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.quick_deduct_amount)) },
                    suffix = { Text(com.example.data.CurrencyFormatter.symbol(context)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.quick_deduct_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentBalance != null && amount != null && valid) {
                    val after = currentBalance - amount
                    Text(
                        stringResource(
                            R.string.balance_after_deduct,
                            com.example.data.CurrencyFormatter.format(context, after)
                        ),
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (after >= 0) primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDeduct(amount ?: 0.0, note.trim().ifBlank { null }) },
                enabled = valid
            ) { Text(stringResource(R.string.quick_deduct_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Plain M3 AlertDialog balance editor — still used by BudgetScreen.kt. HomeScreen's own
 * Budget Card uses [BudgetEditSheet] below instead.
 *
 * `currentBudget` is the current *balance* since the ledger migration; the parameter kept
 * its name because the only caller passes it positionally-by-name and renaming it buys
 * nothing. Both callers now save through `ZadViewModel.setBalanceTo`. */
@Composable
fun BudgetEditDialog(currentBudget: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    // currentBudget == 0.0 means "unknown" (UNKNOWN_BUDGET in ZadViewModel), not a real
    // zero balance — showing "0" here would read as a real (wrong) value already saved.
    var budgetStr by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toInt().toString() else "") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_monthly_budget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.budget_save_hint), style = Typography.labelSmall, color = onSurfaceVariant)
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text(stringResource(R.string.budget_with_currency, com.example.data.CurrencyFormatter.symbol(context))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = budgetStr.toDoubleOrNull() ?: currentBudget
                Log.d(TAG_HOME, "BudgetEditDialog confirm → parsed=$parsed")
                onSave(parsed)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * تعديل الرصيد يدوياً — الشيت اللي بيدي العميل الكلمة الأخيرة على الرقم.
 *
 * ده كان محرّر "سقف الميزانية": بتكتب السقف والشيت بيعرض المتبقي المتوقع منه. بعد ما زاد
 * بقى دفتر حسابات مفيش سقف يتكتب — العميل بيكتب **الرصيد اللي المفروض يشوفه**، والرصيد
 * الابتدائي بيتحسب رجوعياً في `ZadViewModel.setBalanceTo` عشان المعادلة تطلع الرقم ده.
 *
 * يعني الرقم اللي في الخانة هو نفسه الرقم اللي هيظهر على الكارت بعد الحفظ، مش مُدخل
 * بيتحسب منه رقم تاني — وده الفرق اللي خلّى المحرّر القديم محتاج معاينة أصلاً.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditSheet(
    currentBalance: Double?,
    committed: Double,
    expenseTransactions: List<com.example.data.ZadTransaction> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onEditCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteTransaction: (String) -> Unit = {}
) {
    // null = الرصيد لسه متحددش. خانة فاضية بتسأل، بدل ما "0" يتقري كرقم محفوظ فعلاً.
    var budgetStr by remember { mutableStateOf(currentBalance?.takeIf { it != 0.0 }?.toInt()?.toString() ?: "") }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingCategoryFor by remember { mutableStateOf<com.example.data.ZadTransaction?>(null) }

    val parsed = budgetStr.toDoubleOrNull()
    // الرقم المكتوب **هو** الرصيد، فمفيش "متبقي متوقع" يتحسب. المحجوز لسه بيتطرح منه
    // عشان "متاح" يفضل يجاوب على سؤال مختلف: إيه اللي ينفع أصرفه من غير ما ألغي التزام.
    val previewRemaining = parsed
    val previewAvailable = previewRemaining?.let { it - committed }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(stringResource(R.string.edit_monthly_budget), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.budget_save_hint), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.manual_balance_hint), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = budgetStr,
                onValueChange = { budgetStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.balance_label) + " (" + com.example.data.CurrencyFormatter.symbol(context) + ")") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (previewRemaining != null && previewAvailable != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(
                        R.string.available_breakdown,
                        com.example.data.CurrencyFormatter.format(context, previewRemaining),
                        com.example.data.CurrencyFormatter.format(context, committed)
                    ),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
                Text(
                    "${stringResource(R.string.available_label)}: ${com.example.data.CurrencyFormatter.format(context, previewAvailable)}",
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (previewAvailable >= 0) primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        // parsed is non-null whenever the button is enabled; currentBalance
                        // is the belt-and-braces fallback rather than a labelled return,
                        // which reads ambiguously inside a named-argument lambda.
                        val value = parsed ?: currentBalance
                        if (value != null) {
                            Log.d(TAG_HOME, "BudgetEditSheet confirm → balance=$value")
                            onSave(value)
                        }
                    },
                    enabled = parsed != null && parsed > 0,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.save)) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = outlineVariant)

            Text(
                "${stringResource(R.string.spent_label)} · ${com.example.data.CurrencyFormatter.format(context, expenseTransactions.sumOf { it.amount })}",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (expenseTransactions.isEmpty()) {
                Text(
                    stringResource(R.string.no_transactions),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                expenseTransactions.forEach { tx ->
                    BudgetSheetTxRow(
                        tx = tx,
                        onEditCategory = { editingCategoryFor = tx },
                        onDelete = { onDeleteTransaction(tx.id) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }

    editingCategoryFor?.let { tx ->
        AlertDialog(
            onDismissRequest = { editingCategoryFor = null },
            title = { Text(stringResource(R.string.edit_category_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    com.example.data.BudgetTracker.STANDARD_CATEGORIES.forEach { cat ->
                        Text(
                            cat,
                            style = Typography.bodyMedium,
                            color = if (cat == tx.category) primary else onSurface,
                            fontWeight = if (cat == tx.category) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEditCategory(tx.id, cat)
                                    editingCategoryFor = null
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingCategoryFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BudgetSheetTxRow(
    tx: com.example.data.ZadTransaction,
    onEditCategory: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onEditCategory)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.title, style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
            Text(tx.category ?: stringResource(R.string.category_label), style = Typography.labelSmall, color = onSurfaceVariant)
        }
        Text(
            com.example.data.CurrencyFormatter.format(context, tx.amount),
            style = Typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = stringResource(R.string.delete_action),
                tint = onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ChartBar(label: String, heightRatio: Float, isSelected: Boolean) {
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        animationPlayed = true
    }

    val animatedHeightRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animationPlayed) heightRatio else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 1000,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedHeightRatio)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (isSelected) Brush.verticalGradient(listOf(primary.copy(alpha = 0.8f), primary)) 
                    else Brush.verticalGradient(listOf(primaryFixed.copy(alpha = 0.2f), primaryFixed.copy(alpha = 0.4f)))
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = Typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primary else onSurfaceVariant
        )
    }
}

@Composable
fun CategoryChip(color: Color, text: String, bgColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = text, style = Typography.labelSmall, color = textColor)
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = Typography.labelMedium,
            color = onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = Typography.titleMedium,
            color = onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

/** كرت "العقل → الوصفات": يظهر لما فيه أصناف هتخلص/تنتهي مع اقتراحات وصفات فعلية بيها */
@Composable
fun UrgentRecipeCard(
    triggerItems: List<String>,
    text: String,
    isStagnantOnly: Boolean = false,
    onOpenChat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF7ED),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenChat
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFED7AA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (isStagnantOnly) R.string.items_stagnant_hint else R.string.items_expiring_soon_hint,
                            triggerItems.take(2).joinToString("، ")
                        ),
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412)
                    )
                    Text(
                        stringResource(if (isStagnantOnly) R.string.suggested_recipes_use_it_up else R.string.suggested_recipes_before_expiry),
                        style = Typography.labelSmall,
                        color = Color(0xFFC2410C)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text.take(220),
                style = Typography.bodySmall,
                color = Color(0xFF7C2D12),
                lineHeight = 18.sp,
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.tap_for_more_in_chat), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
        }
    }
}

/**
 * Chef Zad — one card, per the mockup. The old version rendered a rail of three
 * meal cards; when the AI hadn't answered it fell back to three hardcoded meals with
 * hardcoded Unsplash photos, which is exactly the kind of fake content the design
 * review flagged. Now: real suggestion or an honest empty line, never a placeholder
 * meal presented as a recommendation.
 */
@Composable
fun SmartChefSection(
    suggestions: String,
    onViewAll: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    recipes: List<com.example.data.ZadRecipe> = emptyList(),
    onAddMissingToShopping: (List<String>) -> Unit = {}
) {
    // Was `isNotBlank() && startsWith("1.") || startsWith("-") || startsWith("•")` —
    // && binds tighter than ||, so isNotBlank() only guarded the "1." branch, and the
    // real model output is plain prose ("يمكنك تحضير وجبة دجاج..."), never numbered/
    // bulleted. Net effect: isRealAi was false for every real AI response.
    val allDepleted = suggestions == com.example.data.ZadAiRepository.MEAL_SUGGESTIONS_ALL_DEPLETED
    val isRealAi = suggestions.isNotBlank() && !allDepleted &&
        suggestions != com.example.data.ZadAiRepository.MEAL_SUGGESTIONS_FALLBACK &&
        suggestions != com.example.data.ZadAiRepository.MEAL_SUGGESTIONS_LOADING

    val dish = if (isRealAi) {
        suggestions.split("\n").firstOrNull { it.isNotBlank() }?.trim()
            ?.replace(Regex("^[\\d\\-•·.]+\\s*"), "")
    } else null

    com.example.ui.components.ZadChefCard(
        suggestion = dish,
        emptyHintRes = if (allDepleted) R.string.chef_card_all_depleted_hint else R.string.chef_card_empty_hint,
        // الكارت بيوعد بوصفة، فيفتح الوصفة. كان بيروح لعقل زاد، و RecipeDetailDialog
        // (بكل الـ parsing وقائمة المقادير وخطوات التحضير) ما كانش ليه أي مدخل —
        // showRecipeDialog اتعرّف واتقرا وعمره ما اتعمل true.
        onClick = { if (dish != null) onOpenRecipe(dish) else onViewAll() }
    )

    // الكروت تحت الكارت النصي مش بدله: النص هو الجملة الودودة اللي شيف زاد بتفتح بيها،
    // والكروت هي اللي ينفع يتطبخ منها. الاتنين جايين من نفس الرد.
    if (recipes.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        com.example.ui.components.ChefRecipeRow(
            recipes = recipes,
            onAddMissingToShopping = onAddMissingToShopping,
        )
    }
}

@Composable
fun MealCard(title: String, desc: String, status: String, isAvailable: Boolean, imgUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
            AsyncImage(
                model = imgUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isAvailable) catFoodBg else Color(0xFFFFF3E0))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAvailable) Icons.Default.Eco else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAvailable) catFoodIcon else warningColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = status,
                    style = Typography.labelSmall,
                    color = if (isAvailable) catFoodIcon else warningColor
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontSize = 14.sp),
                color = onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = Typography.labelSmall,
                color = onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun TransactionItem(
    icon: ImageVector = Icons.Default.ShoppingCart,
    iconBg: Color = primaryContainer,
    iconColor: Color = primary,
    title: String,
    subtitle: String,
    amount: String,
    isExpense: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontSize = 14.sp),
                color = onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = Typography.labelSmall,
                color = onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = Typography.titleMedium.copy(fontSize = 14.sp),
            color = if (isExpense) dangerColor else successColor
        )
    }
}

@Composable
fun AgentSummaryCard(
    agentSummary: com.example.data.AiAgentSummary?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToInventory: () -> Unit
) {
    // The mockup's `aiSummaryTitle` card: #052E16 at 20dp radius, a mint title in
    // 12.5sp, the summary at 14sp/1.55, and the actions as translucent chips. The
    // 36dp robot-avatar circle and the 16sp white heading that used to sit on top
    // are gone — the mockup gives this card one small label, then the sentence.
    // Mockup fidelity (zad_premium_v5 .ai-panel): gradient 135deg #052E16→#0A382C,
    // mint sparkle before the title, and the 140px radial "breathe" glow orb.
    // breathe 4s ease infinite — القيمة 0.93→1.07 (±7% زي scale(1.05) في CSS تقريباً)
    val breatheGlow = rememberInfiniteTransition(label = "aiBreathe").animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aiBreatheScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF052E16), Color(0xFF0A382C))))
            // ::before بتاع البروتوتايب: دائرة ضوء mint نصف قطرها 140px أعلى اليمين
            // بتنفس breathe 4s (scale 1→1.05)
            .drawBehind {
                val breatheVal = breatheGlow.value
                val radius = size.width * 0.35f * breatheVal
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6EE7B7).copy(alpha = 0.22f), Color.Transparent)
                    ),
                    radius = radius,
                    center = Offset(size.width + 40f, -40f)
                )
            }
            .padding(18.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Sparkle SVG من البروتوتايب (نجمة رباعية mint 14px)
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_zad_sparkle),
                        contentDescription = null,
                        tint = Color(0xFF6EE7B7),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        stringResource(R.string.zad_agent),
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_cd), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isLoading) {
                com.example.ui.components.ZadLoadingState(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    size = 24.dp,
                    strokeWidth = 2.dp
                )
            } else if (agentSummary != null) {
                Text(agentSummary.summary, color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)

                if (agentSummary.alerts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    agentSummary.alerts.take(2).forEach { alert ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                when (alert.type) { "warning" -> Icons.Default.Warning; "success" -> Icons.Default.CheckCircle; else -> Icons.Default.Info },
                                contentDescription = null, tint = when (alert.type) {
                                    "warning" -> Color(0xFFFF9800); "success" -> Color(0xFF4CAF50); else -> Color.White.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(alert.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (agentSummary.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        agentSummary.suggestions.take(3).forEach { suggestion ->
                            val suggestionColor = when (suggestion.action) {
                                "add_to_shopping" -> Color(0xFF4CAF50)
                                "check_budget" -> Color(0xFFFF9800)
                                "cook_meal" -> Color(0xFF2196F3)
                                else -> Color.White.copy(alpha = 0.3f)
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(suggestionColor.copy(alpha = 0.18f)).clickable {
                                    when (suggestion.action) {
                                        "add_to_shopping" -> onNavigateToShopping()
                                        "cook_meal" -> onNavigateToAssistant()
                                        else -> onNavigateToAssistant()
                                    }
                                }.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text(suggestion.reason, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        }
                    }
                }

                if (agentSummary.stats.inventoryCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(stringResource(R.string.nav_inventory), "${agentSummary.stats.inventoryCount}", Color.White)
                        StatItem(stringResource(R.string.expiring_soon_stat_label), "${agentSummary.stats.expiringSoon}",
                            if (agentSummary.stats.expiringSoon > 0) Color(0xFFFF9800) else Color.White.copy(alpha = 0.6f))
                        StatItem(stringResource(R.string.subscriptions), "${agentSummary.stats.subscriptionsActive}", Color.White)
                    }
                }
            } else {
                Text(stringResource(R.string.zad_analyzing_now), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
    }
}

@Composable
fun AutoSuggestionsCard(suggestions: List<com.example.data.ZadAiRepository.AutoSuggestion>) {
    val priorityOrder = mapOf("high" to 0, "medium" to 1, "low" to 2)
    val sorted = suggestions.sortedBy { priorityOrder[it.priority] ?: 1 }.take(4)
    GlassCard(
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.auto_home_27381), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sorted.forEach { suggestion ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(suggestion.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(suggestion.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
                        if (suggestion.description.isNotBlank()) {
                            Text(suggestion.description, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
fun PredictionCard(prediction: com.example.data.AiExpensePrediction, budget: Double) {
    val context = LocalContext.current
    val color = when {
        prediction.predictedTotal > budget -> dangerColor
        prediction.predictedTotal > budget * 0.8 -> Color(0xFFF9A825)
        else -> successColor
    }
    GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.zad_prediction_next_month), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        // بيوضّح إن الرقم ده إجمالي الشهر كله، مش توقع مناسبة بعينها — EventsRadarCard جنبه
        // بيعرض رقم تاني تماماً (توقع مناسبة زي رمضان بس)، وكان بيتقرا كتضارب من غير التمييز ده.
        Text(stringResource(R.string.zad_prediction_total_spend_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.example.data.CurrencyFormatter.format(context, prediction.predictedTotal), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.prediction_confidence, (prediction.confidence * 100).toInt()), color = onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (prediction.warnings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            prediction.warnings.take(2).forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(w, color = onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        if (prediction.tips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            prediction.tips.take(2).forEach { tip ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tip, color = onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun OutingSuggestionCard(spot: com.example.data.NearbyStore) {
    val distanceText = if (spot.distanceMeters < 1000) {
        stringResource(R.string.outing_suggestion_distance_m, spot.distanceMeters)
    } else {
        stringResource(R.string.outing_suggestion_distance_km, spot.distanceMeters / 1000.0)
    }
    GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Celebration, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.outing_suggestion_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.outing_suggestion_subtitle), style = Typography.bodySmall, color = onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(spot.name, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Text(distanceText, style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
    }
}

private fun seasonalEventDisplayName(slug: String?): Int? = when (slug) {
    "ramadan" -> R.string.event_ramadan
    "eid_al_fitr" -> R.string.event_eid_al_fitr
    "eid_al_adha" -> R.string.event_eid_al_adha
    "back_to_school" -> R.string.event_back_to_school
    else -> null
}

@Composable
fun EventsRadarCard(forecasts: List<com.example.data.AiSeasonalForecast>) {
    val next = forecasts.minByOrNull { it.daysUntil } ?: return
    val context = LocalContext.current
    val nameResId = seasonalEventDisplayName(next.slug)
    GlassCard(
        modifier = Modifier.pressableScale(pressedScale = 0.98f, withHaptic = false),
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.events_radar_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (nameResId != null) stringResource(nameResId) else next.slug ?: "",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(stringResource(R.string.events_radar_days_until, next.daysUntil), color = onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.example.data.CurrencyFormatter.format(context, next.predictedTotal), color = dangerColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.prediction_confidence, (next.confidence * 100).toInt()), color = onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (next.tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(next.tip, color = onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
fun HomeScreenPreview() {}

@Composable
fun NotificationPermissionCard(onClick: () -> Unit) {
    com.example.ui.components.ZadListCard(containerColor = catFoodBg, contentPadding = 0.dp) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = catFoodIcon,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.enable_bank_notifications), fontWeight = FontWeight.Bold, color = onSurface)
                Text(
                    stringResource(R.string.enable_bank_notifications_desc),
                    fontSize = 12.sp,
                    color = onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    Log.d(TAG_HOME, "تفعيل إشعارات البنك clicked → startActivity(NOTIFICATION_LISTENER_SETTINGS)")
                    onClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.enable), fontSize = 12.sp)
            }
        }
    }
}

/** لا زرار تجاهل — حالة شبكة حقيقية (NetworkMonitor.isOnline) بتختفي وحدها أول ما النت
 * يرجع، مش تنبيه بيتفتكر زي باقي كروت الهوم القابلة للتجاهل. */
@Composable
fun OfflineBanner() {
    com.example.ui.components.ZadListCard(containerColor = warningColor.copy(alpha = 0.14f), contentPadding = 0.dp) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.offline_banner_title), fontWeight = FontWeight.Bold, color = onSurface)
                Text(stringResource(R.string.offline_banner_subtitle), fontSize = 12.sp, color = onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AiAlertBanner(title: String, description: String) {
    // errorContainer (0x1AF87171 — 10% alpha) composited over the light canvas leaves
    // dangerColor text at ~3.2:1 contrast, under the 4.5:1 AA bar for small text — worse
    // still once description dropped to 80% alpha on top of that. This card needs its
    // own solid, opaque tint instead of the shared low-alpha token.
    com.example.ui.components.ZadListCard(containerColor = Color(0xFFFDECEA), contentPadding = 0.dp) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = dangerColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = dangerColor)
                Text(
                    description,
                    fontSize = 12.sp,
                    color = dangerColor
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String, Boolean, String) -> Unit
) {
    // ── Quick Expense Bottom Sheet (من مرجع "new ui ux" — الرندر) ──
    // الرندر بيعرض الإضافة السريعة كـ bottom sheet مش dialog: عنوان + زر إغلاق،
    // حقول Name/Query/Total بحقول رمادية فاتحة مدوّرة، وزرار Send أخضر غامق
    // بعرض كامل تحت. نفس عقد onSave بالظبط — اتغير الشكل بس.
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("عام") }
    val noDescriptionFallback = stringResource(R.string.no_description_fallback)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // العنوان + زر الإغلاق (الرندر: "Quick Expense" + X)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isExpense) stringResource(R.string.add_expense_title) else stringResource(R.string.add_income_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = textTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                        .padding(4.dp)
                        .size(20.dp)
                )
            }

            // نوع العملية — حببتين زي الفورم القديم، بس بألوان المرجع
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    label = { Text(stringResource(R.string.expense_deduction)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZadV2.green800,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    label = { Text(stringResource(R.string.income_deposit)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZadV2.green800,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // الحقول — رمادية فاتحة مدوّرة زي الرندر (Name / Total / Query)
            val fieldShape = RoundedCornerShape(14.dp)
            val fieldBg = Color(0xFFF3F4F6)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.description_hint)) },
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (isExpense) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.category_hint)) },
                    shape = fieldShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = fieldBg,
                        unfocusedContainerColor = fieldBg,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // زرار Send الأخضر الغامق بعرض كامل (الرندر)
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    val finalCategory = if (!isExpense) "دخل" else category
                    val finalTitle = title.ifEmpty { noDescriptionFallback }
                    Log.d(TAG_HOME, "AddTransactionDialog → confirm: amount=$parsedAmount, title=$finalTitle, isExpense=$isExpense, category=$finalCategory")
                    onSave(parsedAmount, finalTitle, isExpense, finalCategory)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZadV2.green800),
                modifier = Modifier.fillMaxWidth().height(52.dp).pressableScale()
            ) {
                Text(
                    if (isExpense) stringResource(R.string.deduct_amount) else stringResource(R.string.add_amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class MiniTableRow(val name: String, val detail: String, val color: Color)


@Composable
fun MiniTableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    itemsCount: Int,
    items: List<MiniTableRow>,
    onSeeAll: () -> Unit
) {
    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSeeAll, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.view_all_count, itemsCount), style = Typography.labelSmall, color = primary)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (items.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.no_items_to_show),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.name, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = item.detail, style = Typography.labelMedium, color = item.color, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun ZadProactiveSummaryCard(insights: List<com.example.data.AiInsight>) {
    LaunchedEffect(insights) {
        Log.d("HomeScreen", "🤖 الذكاء الاستباقي (ميزة 2): تم عرض ${insights.size} نصيحة استباقية")
    }

    com.example.ui.components.ZadListCard(shape = RoundedCornerShape(24.dp), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.proactive_summary_title),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (insights.isEmpty()) {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.proactive_summary_loading)
                )
            } else {
                insights.forEachIndexed { index, insight ->
                    val (icon, color) = when (insight.type.uppercase()) {
                        "WARNING" -> Icons.Default.Warning to dangerColor
                        "REWARD" -> Icons.Default.Star to Color(0xFFFFC107) // Amber
                        else -> Icons.Default.Lightbulb to primary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(insight.title, style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
                            Text(insight.description, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                    if (index < insights.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun KidsModeContent(
    familyState: com.example.ui.viewmodels.FamilyState.Active?,
    onAddRequest: (title: String, amount: Double) -> Unit,
    onNavigateToFamily: () -> Unit = {},
    onNavigateToTasbiha: () -> Unit = {},
    myTasbiha: TasbihaTree? = null,
    affiliateProducts: List<com.example.data.AffiliateProduct> = emptyList(),
    viewModel: ZadViewModel? = null,
    familyViewModel: FamilyViewModel? = null,
    showBalanceNumber: Boolean = true
) {
    if (familyState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading_family_data), color = onSurfaceVariant)
        }
        return
    }

    val myChores = familyState.chores.filter { it.assignedTo == familyState.myMemberInfo.id }
    val myAllowance = familyState.myMemberInfo.balance
    val mySavingsGoal = familyState.myMemberInfo.savingsGoal
    val myAlias = familyState.myMemberInfo.alias.ifBlank { stringResource(R.string.hero_name_fallback) }
    val recentMessages = familyState.messages.takeLast(3)
    val context = LocalContext.current
    val unknownAliasFallback = stringResource(R.string.unknown_alias_fallback)
    var showWishDialog by remember { mutableStateOf(false) }
    // هدف الادخار اتحقق (الرصيد وصل أو عدى الهدف) — نشغّل confetti مرة واحدة لحظة الوصول
    val savingsGoalReached = mySavingsGoal > 0 && myAllowance >= mySavingsGoal

    // ملحوظة: الأب (الـ Column في HomeScreen) أصلاً بيعمل verticalScroll — سكرول تاني
    // هنا كان بيسبب IllegalStateException (infinity max height constraints)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── Cute Greeting Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            KidAvatar(seed = familyState.myMemberInfo.id.ifBlank { myAlias }, size = 52.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.greeting_hi_name, myAlias), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Text(stringResource(R.string.greeting_subtitle), style = Typography.labelMedium, color = onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))

        // ── Balance Card (Candy Gradient) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(28.dp), spotColor = kidsPrimary.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(kidsPrimary, Color(0xFFEC4899), Color(0xFFF59E0B))))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.available_spending), color = Color.White.copy(alpha = 0.9f), style = Typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (showBalanceNumber) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = myAllowance,
                            label = "BalanceAnimation"
                        ) { targetAllowance ->
                            Text(com.example.data.CurrencyFormatter.formatNumber(context, targetAllowance), color = Color.White, style = Typography.displayLarge, fontWeight = FontWeight.Black)
                        }
                        Text(" " + com.example.data.CurrencyFormatter.symbol(context), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    }
                }
                if (mySavingsGoal > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🎯 " + stringResource(R.string.savings_goal_colon, com.example.data.CurrencyFormatter.format(context, mySavingsGoal)), color = Color.White.copy(alpha = 0.95f), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("🚀", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (myAllowance / mySavingsGoal).toFloat().coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(1500, easing = FastOutSlowInEasing),
                        label = "ProgressAnimation"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                val myDailyLimit = familyState.myMemberInfo.dailyLimit
                if (myDailyLimit != null && myDailyLimit > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val spentToday = com.example.data.approvedSpendSince(familyState.messages, familyState.myMemberInfo.id, java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    val limitRatio = (spentToday / myDailyLimit).toFloat().coerceIn(0f, 1f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("💸 " + stringResource(R.string.spent_today_colon, com.example.data.CurrencyFormatter.format(context, spentToday), com.example.data.CurrencyFormatter.format(context, myDailyLimit)), color = Color.White.copy(alpha = 0.95f), style = Typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { limitRatio },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (limitRatio >= 1f) Color(0xFFFECACA) else Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { showWishDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = kidsPrimaryDark),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.pressableScale()
                ) {
                    Text("✋", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.need_extra_expense), fontWeight = FontWeight.Bold)
                }
            }
            // هدف الادخار اتحقق — نفجّر confetti فوق الكارت مرة واحدة (iterations = 1)
            androidx.compose.animation.AnimatedVisibility(
                visible = savingsGoalReached,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZadLottieAsset(
                    resId = R.raw.lottie_confetti_burst,
                    iterations = 1,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = stringResource(R.string.savings_goal_colon, com.example.data.CurrencyFormatter.format(context, mySavingsGoal))
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── My Chores Section (Fun cards) ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.my_tasks), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (myChores.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF0FDF4), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.no_tasks_now), color = Color(0xFF15803D), style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            myChores.forEachIndexed { index, chore ->
                AppearOnEntry(delayMs = (index * 60).coerceAtMost(400)) {
                val choreRowShape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .shadow(elevation = 4.dp, shape = choreRowShape, spotColor = kidsPrimary.copy(alpha = 0.12f))
                        .clip(choreRowShape)
                        .background(if (chore.isCompleted) Color(0xFFECFDF5) else surface)
                        .pressableScale()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                            .background(if (chore.isCompleted) Color(0xFF22C55E) else kidsPrimaryLight.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (chore.isCompleted) "✅" else "📋", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chore.title, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 14.sp)
                        if (chore.rewardAmount > 0) {
                            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFEF3C7), modifier = Modifier.padding(top = 2.dp)) {
                                Text("🪙 +" + com.example.data.CurrencyFormatter.format(context, chore.rewardAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Gamification Badges ──
        val completedChores = myChores.count { it.isCompleted }
        val savingsProgress = if (mySavingsGoal > 0) (myAllowance / mySavingsGoal).toFloat().coerceIn(0f, 1f) else 0f
        KidBadgeRow(
            badges = buildKidBadges(
                completedChores = completedChores,
                tasbihaStreakDays = myTasbiha?.streakDays ?: 0,
                savingsProgress = savingsProgress
            )
        )
        Spacer(modifier = Modifier.height(22.dp))

        // ── Mini Family Chat ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💬", fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.last_family_messages), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNavigateToFamily) {
                Text(stringResource(R.string.open_chat), fontSize = 12.sp)
                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (recentMessages.isEmpty()) {
            com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_messages_yet))
        } else {
            recentMessages.forEach { msg ->
                // msg.senderId بيخزن family_members.id، مش auth user id — it.userId كان
                // بيقارن بحقل غلط فمكانش بيلاقي أي عضو أبداً (نفس النمط الصح في
                // FamilyScreen.kt وFamilyViewModel.kt: it.id == msg.senderId).
                val senderAlias = familyState.members.find { it.id == msg.senderId }?.alias?.ifBlank { unknownAliasFallback } ?: unknownAliasFallback
                Surface(shape = RoundedCornerShape(14.dp), color = surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        KidAvatar(seed = msg.senderId.ifBlank { senderAlias }, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(senderAlias, fontSize = 10.sp, color = kidsPrimary, fontWeight = FontWeight.Bold)
                            Text(msg.message, fontSize = 13.sp, color = onSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Tasbiha Widget ──
        Surface(
            onClick = onNavigateToTasbiha,
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF1F8E9),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0xFF2E7D32).copy(alpha = 0.15f))
                .pressableScale()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌳", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasbiha_garden), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text(if (myTasbiha != null) stringResource(R.string.your_tree_score, myTasbiha.score) else stringResource(R.string.plant_your_tree), fontSize = 12.sp, color = Color(0xFF558B2F))
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF2E7D32))
            }
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ── Amazon Suggestions ──
        val activeAffiliate = affiliateProducts.filter { it.isActive }
        if (activeAffiliate.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛍️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.shopping_suggestions), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(activeAffiliate.take(5)) { product ->
                    com.example.ui.widgets.AffiliateProductCard(
                        product = product,
                        onBuyClick = {
                            viewModel?.recordAffiliateClick(product.id, "kids_home")
                            com.example.data.AffiliateHelper.openProduct(context, product)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── قائمة أمنيات: طلب صنف/مبلغ يروح لموافقة الأب/الأم (نفس آلية PURCHASE_REQUEST
    // الموجودة أصلاً) — قبل كده الزرار ده كان بيبعت amount:0 دايماً بدل مبلغ حقيقي ──
    if (showWishDialog) {
        var wishTitle by remember { mutableStateOf("") }
        var wishAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWishDialog = false },
            title = { Text(stringResource(R.string.expense_purchase_request)) },
            text = {
                Column {
                    OutlinedTextField(value = wishTitle, onValueChange = { wishTitle = it }, label = { Text(stringResource(R.string.what_to_buy)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = wishAmount, onValueChange = { wishAmount = it }, label = { Text(stringResource(R.string.requested_amount_with_currency, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = wishAmount.toDoubleOrNull() ?: 0.0
                        if (wishTitle.isNotBlank() && amount > 0) {
                            onAddRequest(wishTitle, amount)
                            showWishDialog = false
                        }
                    },
                    modifier = Modifier.pressableScale(),
                    shape = RoundedCornerShape(50)
                ) { Text(stringResource(R.string.send_request)) }
            },
            dismissButton = { TextButton(onClick = { showWishDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private val kidAvatarEmojis = listOf("🦁", "🐼", "🦊", "🐨", "🐯", "🐰", "🐸", "🦉", "🐵", "🐻", "🦄", "🐳")
private val kidAvatarColors = listOf(
    Color(0xFFFDE68A), Color(0xFFBFDBFE), Color(0xFFFBCFE8), Color(0xFFBBF7D0),
    Color(0xFFDDD6FE), Color(0xFFFED7AA), Color(0xFFA7F3D0), Color(0xFFC7D2FE)
)

/** أفاتار كيوت ثابت لكل طفل (إيموجي + لون) مبني من هاش الاسم/الـID — بدون الحاجة لصورة */
@Composable
fun KidAvatar(seed: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val idx = kotlin.math.abs(seed.hashCode())
    val emoji = kidAvatarEmojis[idx % kidAvatarEmojis.size]
    val bg = kidAvatarColors[idx % kidAvatarColors.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}
