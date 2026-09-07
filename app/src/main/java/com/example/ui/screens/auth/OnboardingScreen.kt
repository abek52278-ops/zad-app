package com.example.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import coil.compose.AsyncImage
import com.example.R
import com.example.data.findActivity
import com.example.ui.components.ZadLottieAsset
import com.example.ui.theme.Typography
import com.example.ui.theme.primary
import com.example.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

data class OnboardingFeature(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val color: Color
)

@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    // remember's calculation lambda is not a composable context, so the theme-aware
    // tokens are read here and keyed into it — reading them inside does not compile.
    val featurePrimary = primary
    val featureSecondary = secondary
    val featureTertiary = tertiary
    val features = remember(featurePrimary, featureSecondary, featureTertiary) {
        listOf(
            OnboardingFeature(Icons.Default.Inventory2, "إدارة المخزين", "تتبع كل ما في مطبخك وبيتك بذكاء", featurePrimary),
            OnboardingFeature(Icons.Default.FamilyRestroom, "العائلة كلها", "شارك الميزانية والمهام مع عائلتك", featureSecondary),
            OnboardingFeature(Icons.Default.SmartToy, "مساعد ذكي", "ذكاء اصطناعي يتنبأ باحتياجاتك ويتعلم منك", Color(0xFF7C3AED)),
            OnboardingFeature(Icons.Default.AccountBalanceWallet, "الميزانية بذكاء", "حلل إنفاقك ووفّر أكثر باقتراحات ذكية", featureTertiary)
        )
    }

    var currentPage by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        // The mockup's splash canvas — two soft radial washes on #FBFAF8. What stood
        // here was a sliding green linear gradient plus three decorative circles, a
        // background the design does not have anywhere and which put onboarding on a
        // different surface from the login screen it hands off to.
        com.example.ui.components.ZadAuthBackground {}

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 60.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language Toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // الزرار بيعرض اللغة **الجاية** في الدورة، مش الحالية — نفس سلوكه قبل
                // كده، بس دلوقتي بيقراها من نفس القايمة اللي toggleLanguage بتلف عليها
                // بدل ما يفترض إن فيه لغتين بس.
                var language by remember { mutableStateOf(com.example.data.LocaleHelper.currentLanguage()) }
                val nextLanguageLabel = com.example.data.LocaleHelper.supported.let { list ->
                    list[(list.indexOfFirst { it.first == language } + 1) % list.size].second
                }
                TextButton(onClick = {
                    com.example.data.LocaleHelper.toggleLanguage(context)
                    language = com.example.data.LocaleHelper.currentLanguage()
                    Log.d("ZAD_TEST", "Language Toggle -> $language")
                    // MainActivity مش AppCompatActivity — لازم recreate يدوي عشان اتجاه RTL/LTR
                    // يتطبّق فعلياً على أجهزة أقدم من API 33 (نفس نمط MarketPrefs.setMarket).
                    context.findActivity()?.recreate()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(nextLanguageLabel, color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Logo + Brand
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_carrot_logo),
                    contentDescription = stringResource(R.string.auth_logo_content_description),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = Typography.displayLarge.copy(fontSize = 40.sp),
                    fontWeight = FontWeight.Bold,
                    color = primaryDark
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.slogan),
                style = Typography.labelLarge,
                color = onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            // Feature Carousel
            val pagerState = rememberPagerState(pageCount = { features.size })

            LaunchedEffect(pagerState.currentPage) {
                currentPage = pagerState.currentPage
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 16.dp
            ) { page ->
                val feature = features[page]
                FeatureCard(feature = feature, isSelected = page == currentPage, page = page)
            }

            Spacer(Modifier.height(24.dp))

            // Page Dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                features.indices.forEach { i ->
                    val dotSize by animateDpAsState(
                        targetValue = if (i == currentPage) 28.dp else 8.dp,
                        animationSpec = tween(300), label = "dot_size"
                    )
                    Box(
                        modifier = Modifier
                            .size(dotSize, 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (i == currentPage) primary else outlineVariant)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Action Buttons
            // fully-rounded pill with a white label, like every other CTA in the design
            // (this one was an 18dp-radius button with near-black text on green)
            com.example.ui.components.ZadPrimaryButton(
                text = if (currentPage < features.size - 1) stringResource(R.string.onboarding_next) else stringResource(R.string.cta_enter),
                onClick = {
                    if (currentPage < features.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    } else {
                        Log.d("Onboarding", "Get Started clicked")
                        onNavigateToLogin()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Skip link
            if (currentPage < features.size - 1) {
                TextButton(onClick = {
                    Log.d("Onboarding", "Skip clicked")
                    onNavigateToLogin()
                }) {
                    Text(stringResource(R.string.auto_onboarding_43594), color = onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sign up link
            TextButton(onClick = {
                Log.d("Onboarding", "Create Account clicked")
                onNavigateToSignUp()
            }) {
                Text(stringResource(R.string.auto_onboarding_21316), color = primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FeatureCard(feature: OnboardingFeature, isSelected: Boolean, page: Int = -1) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = tween(300), label = "card_scale"
    )

    com.example.ui.components.ZadListCard(
        modifier = Modifier
            .scale(scale)
            .padding(vertical = 8.dp),
        shape = com.example.ui.theme.ZadLuxe.squircle,
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (page) {
                // خطوة الترحيب الأولى — illustration بدل الأيقونة الثابتة
                0 -> ZadLottieAsset(
                    resId = R.raw.lottie_onboarding_welcome,
                    iterations = 1,
                    modifier = Modifier.size(200.dp),
                    contentDescription = feature.title
                )
                // خطوة التعريف بالمساعد الذكي
                2 -> ZadLottieAsset(
                    resId = R.raw.lottie_onboarding_ai,
                    iterations = 1,
                    modifier = Modifier.size(200.dp),
                    contentDescription = feature.title
                )
                else -> Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(colors = listOf(feature.color, feature.color.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(feature.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                feature.title,
                style = Typography.headlineMedium.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                feature.desc,
                style = Typography.bodyLarge,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}
