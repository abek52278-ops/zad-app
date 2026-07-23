package com.example.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription as contentDescriptionSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * غلاف موحّد لكل أنميشن Lottie في التطبيق — أي شاشة عايزة تشغّل ملف من res/raw
 * لازم تعدي من هنا بدل ما تنادي LottieAnimation مباشرة، عشان نضمن نفس سلوك التشغيل
 * (iterations/autoPlay/speed) في كل مكان، ونسيب مكان واحد لو حبينا نضيف إعداد
 * "تقليل الحركة" لاحقًا من غير ما نغيّر كل شاشة.
 */
@Composable
fun ZadLottieAsset(
    @RawRes resId: Int,
    modifier: Modifier = Modifier.size(120.dp),
    iterations: Int = LottieConstants.IterateForever,
    autoPlay: Boolean = true,
    speed: Float = 1f,
    contentDescription: String? = null
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        isPlaying = autoPlay,
        speed = speed
    )
    val finalModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescriptionSemantics = contentDescription }
    } else {
        modifier
    }
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = finalModifier
    )
}
