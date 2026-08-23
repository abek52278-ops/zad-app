package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * شعار زاد المتحرك — نسخة Compose من `zadCarrotIn` + `zadCarrotFloat`
 * في البروتوتايب:
 * - الدخول: bounce (scale .7 → 1.06 → 1 مع دوران -8° → 2° → 0)
 * - بعدها: طفو مستمر لأعلى/أسفل 3 ثواني (زي إنها عايمة في الهوا)
 */
@Composable
fun ZadAnimatedLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 80.dp) {
    // مرحلة الدخول (مرة واحدة)
    val enter = rememberInfiniteTransition(label = "zad_logo_enter")
    // ملاحظة: infiniteRepeatable بيتكرر — عشان الدخول مرة واحدة بنستخدم animateFloatAsState
    val enterProgress = androidx.compose.runtime.remember {
        androidx.compose.animation.core.Animatable(0f)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        enterProgress.animateTo(
            1f,
            androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }
    val enterVal = enterProgress.value

    // مرحلة الطفو (مستمرة بعد الدخول)
    val float = rememberInfiniteTransition(label = "zad_logo_float")
    val floatY by float.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "float_y"
    )

    val scale = if (enterVal < 0.6f) {
        // bounce up: .7 → 1.06
        0.7f + (enterVal / 0.6f) * 0.36f
    } else {
        // settle: 1.06 → 1
        1.06f - ((enterVal - 0.6f) / 0.4f) * 0.06f
    }
    val rotation = if (enterVal < 0.6f) -8f + (enterVal / 0.6f) * 10f else 2f - ((enterVal - 0.6f) / 0.4f) * 2f

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.ic_carrot_logo),
            contentDescription = "ZAD Logo",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                    translationY = floatY * (enterVal.coerceIn(0f, 1f))
                    alpha = enterVal.coerceIn(0f, 1f)
                }
        )
    }
}
