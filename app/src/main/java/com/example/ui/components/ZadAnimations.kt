package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * ZadAnimations — مكتبة الأنيميشن الموحدة بفيزياء iOS
 *
 * الفلسفة: كل حركة في التطبيق تستخدم spring physics حقيقية
 * (زي iOS) بدل tween الخطي — الحركة تحس إنها "حية" مش ميكانيكية.
 *
 * الاستخدام:
 * - Modifier.pressableScale()          → أي عنصر قابل للضغط ينكمش بنعومة
 * - Modifier.shimmerLoading()          → skeleton loading احترافي
 * - Modifier.floatingIdle()            → طفو خفيف مستمر (للأيقونات المميزة)
 * - AnimatedCount(value)               → عداد أرقام متحرك
 * - ZadTransitions.enter/exit          → انتقالات صفحات موحدة
 */
object ZadSprings {
    /** الضغط: سريع وحيوي — زي أزرار iOS */
    val Press = spring<Float>(dampingRatio = 0.55f, stiffness = 600f)

    /** الظهور: ناعم مع bounce خفيف */
    val Appear = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

    /** الحركة الكبيرة: صفحات وكروت */
    val Screen = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    /** bounce قوي: احتفالات وإنجازات */
    val Celebrate = spring<Float>(dampingRatio = 0.4f, stiffness = 500f)
}

object ZadTransitions {
    /** دخول الصفحات: انزلاق من اليسار (RTL) + fade — زي iOS push */
    val enter: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    ) + fadeIn(tween(280))

    val exit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it / 5 },
        animationSpec = tween(300, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
    ) + fadeOut(tween(220))

    val popEnter: EnterTransition = slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    ) + fadeIn(tween(280))

    val popExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it / 5 },
        animationSpec = tween(300, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
    ) + fadeOut(tween(220))

    /** ظهور عناصر القوائم تدريجياً */
    fun listItemEnter(index: Int): EnterTransition =
        slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(300, delayMillis = (index * 40).coerceAtMost(400))
        ) + fadeIn(tween(300, delayMillis = (index * 40).coerceAtMost(400)))
}

/**
 * ضغطة iOS: العنصر ينكمش 4% بنعومة عند اللمس ويرجع بـ spring
 * مع haptic خفيف — الإحساس المميز لتطبيقات آبل
 */
fun Modifier.pressableScale(
    pressedScale: Float = 0.96f,
    withHaptic: Boolean = true
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = ZadSprings.Press,
        label = "pressScale"
    )
    this
        .scale(scale)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                if (withHaptic) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * Shimmer skeleton — بديل الشاشات الفاضية أثناء التحميل
 */
fun Modifier.shimmerLoading(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    background(
        Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.25f),
                Color.LightGray.copy(alpha = 0.55f),
                Color.LightGray.copy(alpha = 0.25f)
            ),
            start = Offset(translate - 400f, 0f),
            end = Offset(translate, 0f)
        )
    )
}

/**
 * طفو مستمر خفيف — للعناصر المميزة (شجرة التسبيحة، أيقونة الذكاء)
 */
fun Modifier.floatingIdle(amplitude: Float = 6f): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "float")
    val offsetY by transition.animateFloat(
        initialValue = -amplitude,
        targetValue = amplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    graphicsLayer { translationY = offsetY }
}

/**
 * اهتزاز جرس التنبيهات: نبضة خفيفة كل ~2.2 ثانية بدل اهتزاز مستمر —
 * يلفت الانتباه من غير ما يبقى مزعج لعين المستخدم طول الوقت.
 */
fun Modifier.bellShake(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "bellShake")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0f at 0
                0f at 1600
                -10f at 1700
                10f at 1800
                -6f at 1900
                6f at 2000
                0f at 2100
                0f at 2200
            }
        ),
        label = "bellRotation"
    )
    graphicsLayer { rotationZ = rotation }
}

/**
 * نبض توهج — للتنبيهات والإنجازات
 */
fun Modifier.pulseGlow(minScale: Float = 1f, maxScale: Float = 1.06f): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    scale(scale)
}

/**
 * عداد متحرك: الرقم يعد تدريجياً للقيمة الجديدة — زي تطبيقات البنوك
 */
@Composable
fun animatedCountAsState(targetValue: Int, durationMs: Int = 800): State<Int> {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(targetValue) {
        animated.animateTo(
            targetValue.toFloat(),
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        )
    }
    return remember { derivedStateOf { animated.value.toInt() } }
}

/**
 * ظهور محتوى تدريجي عند فتح الشاشة (استخدمها حوالين أول عنصر)
 */
@Composable
fun AppearOnEntry(
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        ) + fadeIn(tween(350))
    ) { content() }
}
