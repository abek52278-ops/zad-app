package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.billing.BillingState
import com.example.billing.GooglePlayBillingManager
import com.example.billing.ZadSubscriptionPlan
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadSubscriptionPaywallScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val billingManager = remember { GooglePlayBillingManager.getInstance(context) }

    val billingState by billingManager.billingState.collectAsState()
    val activePlan by billingManager.activePlan.collectAsState()
    // بيقرأ من نفس productDetails اللي queryAvailableProducts() بيملاها — لما يوصل، كل
    // سعر في الشاشة بيبقى هو نفسه اللي Google Play هيعرضه للمستخدم ده فعلياً وقت الدفع.
    val productDetails by billingManager.productDetails.collectAsState()

    var selectedPlan by remember { mutableStateOf(ZadSubscriptionPlan.PLUS) }
    var adWatchCount by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.getAdWatchCount(context)) }
    var isSessionUnlocked by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.isSessionUnlocked(context)) }
    var isAdLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        com.example.ads.RewardedBrainAdManager.syncServerState(context)?.let { state ->
            adWatchCount = state.adWatchCount
            isSessionUnlocked = state.brainSessionActive
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "اشتراكات زاد بريميوم (Zad VIP)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF064E3B), Color(0xFF0F9B76), Color(0xFF047857))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "اختر خطة زاد المناسبة لعائلتك",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "تجربة فورية بلا إعلانات، ذكاء اصطناعي فوري، ومزامنة عائلية ذكية عبر متجر Google Play الرسمي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            if (activePlan != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F9B76).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("👑", fontSize = 22.sp)
                        Column {
                            Text("أنت مشترك حالياً في باقة: ${activePlan?.titleAr}", fontWeight = FontWeight.Bold, color = Color(0xFF0F9B76), fontSize = 13.5.sp)
                            Text("جميع المزايا مفعلة ونشطة في حسابك", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 3 Subscription Plans (Basic, Plus, Ultra)
            ZadSubscriptionPlan.values().forEach { plan ->
                val isSelected = selectedPlan == plan
                val cardShape = RoundedCornerShape(22.dp)

                // كان في جدول أسعار مكتوب يدوياً لثلاث دول بس (مصر/السعودية/الإمارات) وأي
                // دولة تانية كانت بتاخد سعر بالدولار مكتوب بالغلط "شهر" بالعربي — أسعار
                // مش متزامنة مع Play Console، ولو السعر اتغيّر هناك الشاشة تفضل تكدب.
                // productDetails جاية من Google Play نفسه (عملة/ضريبة/دولة المستخدم
                // الفعليين)، فهي دايماً السعر الصح لأي دولة. لسه لحد ما queryAvailableProducts()
                // يوصل: نص صريح إنه بيتحمّل، مش رقم دولار مخمّن يوهم إنه نهائي.
                val formattedPrice = productDetails[plan.productId]
                    ?.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice
                    ?: stringResource(R.string.paywall_price_loading)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .zadCardShadow(cardShape, elevation = if (isSelected) 8.dp else 2.dp)
                        .clip(cardShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF0F9B76) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = cardShape
                        )
                        .clickable { selectedPlan = plan }
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    plan.titleAr,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color(0xFF0F9B76) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (plan.monthlyAiQuota == -1) "ذكاء اصطناعي غير محدود" else "${plan.monthlyAiQuota} طلب ذكي شهرياً",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formattedPrice,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F9B76)
                                )
                                Text(
                                    "اشتراك شهري تجديد تلقائي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (plan) {
                                ZadSubscriptionPlan.ULTRA -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                ZadSubscriptionPlan.PLUS -> Color(0xFFD97706).copy(alpha = 0.15f)
                                ZadSubscriptionPlan.BASIC -> Color(0xFF0F9B76).copy(alpha = 0.15f)
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(
                                plan.badgeAr,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (plan) {
                                    ZadSubscriptionPlan.ULTRA -> Color(0xFF7C3AED)
                                    ZadSubscriptionPlan.PLUS -> Color(0xFFD97706)
                                    ZadSubscriptionPlan.BASIC -> Color(0xFF0F9B76)
                                }
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Spacer(Modifier.height(12.dp))

                        plan.perks.forEach { feat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF0F9B76),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    feat,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Subscribe Button via Google Play
            Button(
                onClick = {
                    if (activity != null) {
                        billingManager.launchSubscription(activity, selectedPlan) { success, msg ->
                            if (!success && msg != null) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "تعذر تشغيل نافذة الدفع، يرجى إعادة فتح التطبيق", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressableScale(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F9B76),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF6EE7B7))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "اشترك في ${selectedPlan.titleAr} عبر Google Play",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Payment Gateways & Trust Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "Google Play" to "💳",
                    "Mada / مدى" to "🇸🇦",
                    "Apple Pay" to "🍏",
                    "دفع آمن ومحمي" to "🔒"
                ).forEach { (method, icon) ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$icon $method",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "يتم الدفع وتجديد الاشتراك الشهري بأمان عبر حساب Google Play الخاص بك، مع إمكانية الإلغاء في أي وقت من متجر التطبيقات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(24.dp))

            // Ad battery alternative option
            com.example.ui.components.ZadListCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                containerColor = Color(0xFF0F172A).copy(alpha = 0.03f),
                contentPadding = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "أو اشحن بطارية الذكاء الاصطناعي مجاناً ⚡",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "شاهد 3 إعلانات للحصول على 5 رسائل ذكاء اصطناعي وجلسة نشطة لمدة 12 ساعة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (isAdLoading) return@OutlinedButton
                            isAdLoading = true
                            com.example.ads.RewardedBrainAdManager.showRewardedEnergyAd(
                                context = context,
                                onAdWatched = { newCount, isFullyUnlocked ->
                                    isAdLoading = false
                                    adWatchCount = newCount
                                    isSessionUnlocked = isFullyUnlocked
                                    Toast.makeText(context, "تمت مشاهدة الإعلان بنجاح ($newCount/3)", Toast.LENGTH_SHORT).show()
                                },
                                onFailed = {
                                    coroutineScope.launch {
                                        Toast.makeText(context, context.getString(R.string.ad_loading_retry_toast), Toast.LENGTH_SHORT).show()
                                        kotlinx.coroutines.delay(2500)
                                        com.example.ads.RewardedBrainAdManager.showRewardedEnergyAd(
                                            context = context,
                                            onAdWatched = { newCount, isFullyUnlocked ->
                                                isAdLoading = false
                                                adWatchCount = newCount
                                                isSessionUnlocked = isFullyUnlocked
                                                Toast.makeText(context, "تمت مشاهدة الإعلان بنجاح ($newCount/3)", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailed = {
                                                isAdLoading = false
                                                Toast.makeText(context, context.getString(R.string.ad_failed_toast), Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pressableScale(),
                        enabled = !isAdLoading
                    ) {
                        if (isAdLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ad_loading_text))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "مشاهدة إعلان مجاني (${adWatchCount}/3)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
