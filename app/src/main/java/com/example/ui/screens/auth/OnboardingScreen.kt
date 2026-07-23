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
    val features = remember {
        listOf(
            OnboardingFeature(Icons.Default.Inventory2, "إدارة المخزين", "تتبع كل ما في مطبخك وبيتك بذكاء", primary),
            OnboardingFeature(Icons.Default.FamilyRestroom, "العائلة كلها", "شارك الميزانية والمهام مع عائلتك", secondary),
            OnboardingFeature(Icons.Default.SmartToy, "مساعد ذكي", "ذكاء اصطناعي يتنبأ باحتياجاتك ويتعلم منك", Color(0xFF7C3AED)),
            OnboardingFeature(Icons.Default.AccountBalanceWallet, "الميزانية بذكاء", "حلل إنفاقك ووفّر أكثر باقتراحات ذكية", tertiary)
        )
    }

    var currentPage by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition(label = "onboard_bg")
    val bgOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_shift"
    )

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        // Animated gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0D5C3F).copy(alpha = 0.08f),
                            Color(0xFF0D5C3F).copy(alpha = 0.15f),
                            Color(0xFF0D5C3F).copy(alpha = 0.08f),
                            Color(0xFF0D5C3F).copy(alpha = 0.02f)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(800f * bgOffset, 800f * bgOffset)
                    )
                )
        )

        // Decorative floating elements
        Box(Modifier.size(250.dp).offset(x = (-80).dp, y = (-100).dp).clip(CircleShape).background(primary.copy(alpha = 0.04f)))
        Box(Modifier.size(180.dp).offset(x = 280.dp, y = (-60).dp).clip(CircleShape).background(secondary.copy(alpha = 0.05f)))
        Box(Modifier.size(120.dp).offset(x = (-40).dp, y = 400.dp).clip(CircleShape).background(primary.copy(alpha = 0.03f)))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 60.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language Toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val currentLang = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    val newLang = if (currentLang == "ar") "en" else "ar"
                    Log.d("ZAD_TEST", "Language Toggle -> $newLang")
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(newLang)
                    )
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("English", color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Logo + Brand
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_carrot_logo),
                    contentDescription = "Zad Logo",
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
                FeatureCard(feature = feature, isSelected = page == currentPage)
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
            Button(
                onClick = {
                    if (currentPage < features.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    } else {
                        Log.d("Onboarding", "Get Started clicked")
                        onNavigateToLogin()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    if (currentPage < features.size - 1) "التالي" else "ابدأ الآن",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentPage < features.size - 1) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Skip link
            if (currentPage < features.size - 1) {
                TextButton(onClick = {
                    Log.d("Onboarding", "Skip clicked")
                    onNavigateToLogin()
                }) {
                    Text("تخطي", color = onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sign up link
            TextButton(onClick = {
                Log.d("Onboarding", "Create Account clicked")
                onNavigateToSignUp()
            }) {
                Text("ليس لديك حساب؟ أنشئ حساباً جديداً", color = primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FeatureCard(feature: OnboardingFeature, isSelected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = tween(300), label = "card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(colors = listOf(feature.color, feature.color.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
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
