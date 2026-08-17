package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ads.RewardedBrainAdManager
import com.example.data.MarketPrefs
import com.example.ui.components.ZadListCard
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SubscriptionPlansScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentMarket = remember { MarketPrefs.getMarket(context) }
    var selectedTier by remember { mutableStateOf<ZadPlanTier>(ZadPlanTier.PLUS) }
    var isAnnualBilling by remember { mutableStateOf<Boolean>(false) }
    var showPaymentSheet by remember { mutableStateOf<Boolean>(false) }
    var showSuccessDialog by remember { mutableStateOf<Boolean>(false) }

    var adWatchCount by remember { mutableStateOf<Int>(RewardedBrainAdManager.getAdWatchCount(context)) }
    var isSessionUnlocked by remember { mutableStateOf<Boolean>(RewardedBrainAdManager.isSessionUnlocked(context)) }

    val annualDiscountMultiplier = 0.80 // 20% discount (2 months free)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.pressableScale()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "باقات زاد الشهرية والسنوية",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    "ذكاء اصطناعي فوري وبدون إعلانات",
                    style = Typography.labelSmall,
                    color = onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hero Liquid Glass Visual Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(primary, Color(0xFF0D5C46), Color(0xFF1E3A8A))
                    )
                )
                .padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = primaryFixed,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "أطلق كامل قدرات عقل زاد الخارق",
                    style = Typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "تحليل استباقي لحظي، مسح فوري للفواتير بالكاميرا، شات عائلي صوتي، وبدون إعلانات نهائياً.",
                    style = Typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                // Social proof pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "انضم لأكثر من 12,450 عائلة تدير منزلها بذكاء",
                            style = Typography.labelSmall,
                            color = primaryFixed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Billing Cycle Toggle (شهري / سنوي)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.05f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isAnnualBilling) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .pressableScale()
                    .clickable { isAnnualBilling = false }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "فاتورة شهرية",
                    style = Typography.labelMedium,
                    fontWeight = if (!isAnnualBilling) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isAnnualBilling) primary else onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isAnnualBilling) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .pressableScale()
                    .clickable { isAnnualBilling = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "اشتراك سنوي",
                        style = Typography.labelMedium,
                        fontWeight = if (isAnnualBilling) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAnnualBilling) primary else onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(successColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("وفّر 20%", color = Color.White, style = Typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Plan Cards
        ZadPlanTier.values().forEach { tier ->
            val isSelected = selectedTier == tier
            val cardShape = RoundedCornerShape(22.dp)

            val basePrice = when (currentMarket.countryCode) {
                "EG" -> tier.priceEgp
                "SA" -> tier.priceSar
                "AE" -> tier.priceUsd * 3.67
                "KW" -> tier.priceUsd * 0.31
                else -> tier.priceUsd
            }

            val finalPrice = if (isAnnualBilling) basePrice * annualDiscountMultiplier else basePrice
            val formattedPrice = when (currentMarket.countryCode) {
                "EG" -> "${finalPrice.toInt()} ج.م"
                "SA" -> "${finalPrice.toInt()} ر.س"
                "AE" -> "${finalPrice.toInt()} د.إ"
                "KW" -> "${finalPrice.toInt()} د.ك"
                else -> "$${finalPrice.toInt()}"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .pressableScale()
                    .clickable { selectedTier = tier }
            ) {
                ZadListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    containerColor = if (isSelected) primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
                    contentPadding = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) primary else outline.copy(alpha = 0.6f),
                                shape = cardShape
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        tier.titleAr,
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) primary else onSurface
                                    )
                                    tier.badge?.let { badge ->
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (tier.isPopular) Color(0xFFF59E0B).copy(alpha = 0.15f) else primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                badge,
                                                style = Typography.labelSmall,
                                                color = if (tier.isPopular) secondaryDark else primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tier.subtitleAr,
                                    style = Typography.bodySmall,
                                    color = onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formattedPrice,
                                    style = Typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) primary else onSurface
                                )
                                Text(
                                    if (isAnnualBilling) "شهرياً (فاتورة سنوية)" else "شهرياً",
                                    style = Typography.labelSmall,
                                    color = onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = outline.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))

                        // Features List
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tier.features.forEach { feat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isSelected) successColor else onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        feat,
                                        style = Typography.bodySmall,
                                        color = if (isSelected) onSurface else onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Big Upgrade CTA Button
        Button(
            onClick = { showPaymentSheet = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .pressableScale(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "اشترك الآن في ${selectedTier.titleAr}",
                style = Typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        // 14-day guarantee note
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = successColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "ضمان استرجاع كامل المبلغ خلال 14 يوماً بدون أي أسئلة",
                style = Typography.labelSmall,
                color = onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Ad battery alternative option
        ZadListCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFF0F172A).copy(alpha = 0.03f),
            contentPadding = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "⚡ لست مستعداً للاشتراك الآن؟",
                    style = Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "يمكنك شحن بطارية الذكاء الاصطناعي مجاناً بمشاهدة 3 إعلانات قصيرة (+5 رسائل فورية).",
                    style = Typography.bodySmall,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        RewardedBrainAdManager.showRewardedEnergyAd(
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
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showPaymentSheet) {
        PaymentGatewayBottomSheet(
            selectedTier = selectedTier,
            isAnnual = isAnnualBilling,
            onDismiss = { showPaymentSheet = false },
            onPaymentSuccess = {
                showPaymentSheet = false
                showSuccessDialog = true
            },
            viewModel = viewModel
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("مبروك! تم تفعيل الباقة بنجاح", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "أصبحت الآن على ${selectedTier.titleAr} مع صلاحيات غير محدودة ومساعد ذكاء اصطناعي بدون أي إعلانات.",
                    style = Typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("ابدأ استخدام زاد", color = Color.White)
                }
            }
        )
    }
}

enum class PaymentMethod(val titleAr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GOOGLE_PLAY("Google Play Billing (نقرة واحدة)", Icons.Default.PlayArrow),
    CARD("بطاقة بنكية (فيزا / ماستركارد / ميزة / مدى)", Icons.Default.CreditCard),
    WALLET("المحافظ الإلكترونية (فودافون كاش / STC Pay)", Icons.Default.AccountBalanceWallet),
    FAWRY("فوري / تابي / تمارا", Icons.Default.FlashOn)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewayBottomSheet(
    selectedTier: ZadPlanTier,
    isAnnual: Boolean,
    onDismiss: () -> Unit,
    onPaymentSuccess: () -> Unit,
    viewModel: ZadViewModel
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMethod by remember { mutableStateOf(PaymentMethod.GOOGLE_PLAY) }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var walletPhone by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val rawPrice = if (isAnnual) (selectedTier.priceEgp * 0.80 * 12) else selectedTier.priceEgp
    val formattedTotal = com.example.data.CurrencyFormatter.format(context, rawPrice)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "بوابة الدفع الآمنة",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(
                        "تشفير بنكي 256-bit SSL آمن 100%",
                        style = Typography.labelSmall,
                        color = successColor,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(successColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = successColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SSL Secure", style = Typography.labelSmall, color = successColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Order Summary Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                    .border(1.dp, outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الباقة المختارة:", style = Typography.bodySmall, color = onSurfaceVariant)
                        Text(selectedTier.titleAr, style = Typography.bodySmall, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("دورة الفاتورة:", style = Typography.bodySmall, color = onSurfaceVariant)
                        Text(if (isAnnual) "سنوية (شاملة خصم 20%)" else "شهرية", style = Typography.bodySmall, color = onSurface)
                    }
                    HorizontalDivider(color = outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المبلغ الإجمالي المستحق:", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(formattedTotal, style = Typography.titleMedium, fontWeight = FontWeight.Black, color = primary)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Payment Methods Selector
            Text("اختر وسيلة الدفع المفضلة:", style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(8.dp))

            PaymentMethod.values().forEach { method ->
                val isSel = selectedMethod == method
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSel) 1.5.dp else 1.dp,
                            color = if (isSel) primary else outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(if (isSel) primary.copy(alpha = 0.06f) else surface)
                        .clickable { selectedMethod = method }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSel,
                        onClick = { selectedMethod = method },
                        colors = RadioButtonDefaults.colors(selectedColor = primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(method.icon, contentDescription = null, tint = if (isSel) primary else onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        method.titleAr,
                        style = Typography.bodySmall,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) onSurface else onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Dynamic Payment Inputs
            when (selectedMethod) {
                PaymentMethod.CARD -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it.filter { c -> c.isDigit() }.take(16) },
                            label = { Text("رقم البطاقة (16 رقم)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = it.take(5) },
                                label = { Text("MM/YY") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cardCvv,
                                onValueChange = { cardCvv = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("CVV") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                PaymentMethod.WALLET -> {
                    OutlinedTextField(
                        value = walletPhone,
                        onValueChange = { walletPhone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                        label = { Text("رقم الهاتف المسجل بالمحفظة الذكية") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PaymentMethod.GOOGLE_PLAY -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "سيتم معالجة الاشتراك بأمان عبر حساب Google Play الخاص بك وفاتورة متجرك المسجلة بنقرة واحدة.",
                            style = Typography.bodySmall,
                            color = onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                PaymentMethod.FAWRY -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.04f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "سيتم إنشاء كود دفع فوري / تمارا لتسديد الاشتراك في أقرب منفذ خلال 48 ساعة.",
                            style = Typography.bodySmall,
                            color = onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            errorMessage?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = Typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("إلغاء")
                }

                Button(
                    onClick = {
                        if (selectedMethod == PaymentMethod.CARD && (cardNumber.length < 15 || cardExpiry.isBlank() || cardCvv.length < 3)) {
                            errorMessage = "يرجى إدخال بيانات بطاقة دفع صحيحة"
                            return@Button
                        }
                        if (selectedMethod == PaymentMethod.WALLET && walletPhone.length < 10) {
                            errorMessage = "يرجى إدخال رقم محفظة إلكترونية صحيح"
                            return@Button
                        }

                        errorMessage = null
                        isProcessing = true

                        scope.launch {
                            try {
                                kotlinx.coroutines.delay(1200)
                                val success = viewModel.activateSubscription(
                                    tierId = selectedTier.name.lowercase(),
                                    isAnnual = isAnnual,
                                    provider = selectedMethod.name.lowercase()
                                )
                                if (success) {
                                    onPaymentSuccess()
                                } else {
                                    errorMessage = "تعذر تأكيد المعاملة من مزود الدفع، يرجى المحاولة مرة أخرى"
                                }
                            } catch (e: Exception) {
                                errorMessage = "حدث خطأ أثناء الاتصال ببوابة الدفع: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جاري المعالجة...")
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إتمام الدفع الآمن", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
