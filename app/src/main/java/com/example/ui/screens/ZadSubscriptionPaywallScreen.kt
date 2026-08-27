package com.example.ui.screens

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
import com.example.data.MarketPrefs
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel

enum class ZadPlanTier(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val priceUsd: Double,
    val priceSar: Double,
    val priceEgp: Double,
    val brainConsultations: Int,
    val visionScans: Int,
    val chatMessages: String,
    val badge: String? = null,
    val isPopular: Boolean = false,
    val features: List<String>
) {
    STARTER(
        id = "starter",
        titleAr = "الباقة الأساسية",
        subtitleAr = "المدخل الاقتصادي بدون إعلانات",
        priceUsd = 4.99,
        priceSar = 25.0,
        priceEgp = 250.0,
        brainConsultations = 15,
        visionScans = 30,
        chatMessages = "150 رسالة شهرياً",
        features = listOf(
            "15 استشارة عميقة من عقل زاد",
            "30 مسح وتصوير للفواتير بالذكاء الاصطناعي",
            "150 رسالة شات واستفسارات سريعة",
            "🚫 بدون إعلانات تماماً",
            "حفظ وتقارير PDF قياسية"
        )
    ),
    PLUS(
        id = "plus",
        titleAr = "باقة النمو (Plus)",
        subtitleAr = "الخيار الذكي — 3 أضعاف المزايا",
        priceUsd = 9.99,
        priceSar = 49.0,
        priceEgp = 500.0,
        brainConsultations = 50,
        visionScans = 100,
        chatMessages = "500 رسالة شهرياً",
        badge = "🔥 الأكثر طلباً",
        isPopular = true,
        features = listOf(
            "50 استشارة عميقة من عقل زاد",
            "100 مسح وتصوير للفواتير (Vision)",
            "500 رسالة شات واستفسارات ذكية",
            "🚫 بدون إعلانات تماماً",
            "تصدير Excel + تنبؤ شهري بالميزانية",
            "تسجيل فويس ومسح فواتير عبر تليجرام"
        )
    ),
    PRO(
        id = "pro",
        titleAr = "باقة المحترفين (Pro)",
        subtitleAr = "لأصحاب الأعمال والمصاريف الكثيفة",
        priceUsd = 19.99,
        priceSar = 99.0,
        priceEgp = 990.0,
        brainConsultations = 150,
        visionScans = 300,
        chatMessages = "غير محدود (Groq السريع)",
        badge = "👑 للأعمال والعائلات",
        features = listOf(
            "150 استشارة عميقة من عقل زاد",
            "300 مسح وتصوير للفواتير",
            "رسائل واستفسارات شات غير محدودة",
            "🚫 بدون إعلانات تماماً",
            "شات صوتي تفاعلي كامل + دعم فني مخصص",
            "تحليلات مالية متقدمة للمتاجر والعائلات"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadSubscriptionPaywallScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentMarket = remember { MarketPrefs.getMarket(context) }
    var selectedTier by remember { mutableStateOf(ZadPlanTier.PLUS) }
    var adWatchCount by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.getAdWatchCount(context)) }
    var isSessionUnlocked by remember { mutableStateOf(com.example.ads.RewardedBrainAdManager.isSessionUnlocked(context)) }

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
                    Text(stringResource(R.string.auto_zadsubscriptionpaywall_23198),
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
                            colors = listOf(primary, secondary)
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.auto_zadsubscriptionpaywall_7174),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.auto_zadsubscriptionpaywall_93703),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Plan Cards
            ZadPlanTier.values().forEach { tier ->
                val isSelected = selectedTier == tier
                val cardShape = RoundedCornerShape(20.dp)

                val formattedPrice = when (currentMarket.countryCode) {
                    "EG" -> "${tier.priceEgp.toInt()} ج.م"
                    "SA" -> "${tier.priceSar.toInt()} ر.س"
                    "AE" -> "${(tier.priceUsd * 3.67).toInt()} د.إ"
                    "KW" -> "${(tier.priceUsd * 0.31).toInt()} د.ك"
                    else -> "$${tier.priceUsd}"
                }

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
                            color = if (isSelected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = cardShape
                        )
                        .clickable { selectedTier = tier }
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
                                    tier.titleAr,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    tier.subtitleAr,
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
                                    color = primary
                                )
                                Text(stringResource(R.string.auto_zadsubscriptionpaywall_78274),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (tier.badge != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (tier.isPopular) Color(0xFFFF9900).copy(alpha = 0.15f) else primaryContainer,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    tier.badge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tier.isPopular) Color(0xFFD97706) else primary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Spacer(Modifier.height(12.dp))

                        tier.features.forEach { feat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = primary,
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

            // Subscribe Button
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "جاري تحويلك لبوابة الاشتراك في ${selectedTier.titleAr}...",
                        Toast.LENGTH_LONG
                    ).show()
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
                Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF6EE7B7))
                Spacer(Modifier.width(8.dp))
                Text(
                    "تفعيل ${selectedTier.titleAr} الآن",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
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
                    "Moyasar / Tap" to "⚡"
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
            Text(stringResource(R.string.auto_zadsubscriptionpaywall_13807),
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
                    Text(stringResource(R.string.auto_zadsubscriptionpaywall_7318),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.auto_zadsubscriptionpaywall_41518),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            com.example.ads.RewardedBrainAdManager.showRewardedEnergyAd(
                                context = context,
                                onAdWatched = { newCount, isFullyUnlocked ->
                                    adWatchCount = newCount
                                    isSessionUnlocked = isFullyUnlocked
                                    Toast.makeText(context, "تمت مشاهدة الإعلان بنجاح ($newCount/3)", Toast.LENGTH_SHORT).show()
                                },
                                onFailed = {
                                    Toast.makeText(context, "لم نتمكن من تحميل الإعلان، يرجى المحاولة لاحقاً", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pressableScale()
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "مشاهدة إعلان مجاني (${adWatchCount}/3)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
