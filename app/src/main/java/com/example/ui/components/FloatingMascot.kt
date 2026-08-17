package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.Typography
import com.example.ui.theme.onSurface
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * الأيجنت العائم — نفس [CompanionOrb] بس عائم فوق البار السفلي في كل الشاشات (يتحط
 * كـ sibling فوق الـ Scaffold في MainScreen، مش جوه شاشة بعينها)، قابل للسحب أفقياً،
 * وبيتفاعل باللمس. المزاج مش لون ثابت — مركّب من companionState الحقيقي (بيفكر وقت
 * الشات، سعيد/تنبيه من ملخص الأجنت) مع healthScore من BrainReport لما محادثة مفيش
 * سبب لمزاج تاني، وبيتصعّد لـ Celebrating لحظياً أول ما المزاج يبقى Happy لأول مرة.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingMascotCompanion(
    viewModel: ZadViewModel,
    kidsMode: Boolean,
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    // W6 — سطح محادثة سريع من غير خروج من الشاشة (Phase 4: "persistent chat entry
    // point available on every screen"). المكوّن ده أصلاً موجود على كل شاشة chromeVisible
    // (MainScreen.kt) وبيفتح الشات الكامل بالضغط المزدوج/الطويل — مفيش داعي لفقاعة
    // عائمة تانية تتكرر معاه. onQuickChat لو معدّى بيحل محل onNavigateToChat في الضغط
    // الطويل بس (الضغط المزدوج فاضل بيروح للشات الكامل زي ما هو)؛ لو مش معدّى (null،
    // الافتراضي) السلوك القديم زي ما هو بالظبط — أي استدعاء تاني للمكوّن ده متأثرش.
    onQuickChat: (() -> Unit)? = null,
) {
    val chatState by viewModel.companionState.collectAsState()
    val brainReport by viewModel.brainReport.collectAsState()
    val agentSummary by viewModel.agentSummary.collectAsState()
    val remaining by viewModel.remainingBalance.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // healthScore بيبقى تقييم وهمي (افتراضي) لو hasEnoughData=false — متستخدمش كمزاج
    // في الحالة دي، وإلا الأيجنت هيبان "تنبيه" لمستخدم لسه ماسجلش أي بيانات.
    val financialMood = brainReport?.takeIf { it.hasEnoughData }?.let {
        when {
            it.healthScore >= 65 -> CompanionState.Happy
            it.healthScore >= 40 -> CompanionState.Idle
            else -> CompanionState.Alert
        }
    }
    // الشات بيغلب: لو زاد بيفكر فعلاً أو ملخص الأجنت طلّع تنبيه/نجاح حقيقي، ده أولى من
    // تقييم الصحة المالية العام. غير كده، الصحة المالية هي اللي بترسم مزاج الأيجنت وهو واقف.
    val baseMood = when (chatState) {
        CompanionState.Focused, CompanionState.Alert, CompanionState.Happy -> chatState
        else -> financialMood ?: chatState
    }

    var celebrateUntilMs by remember { mutableStateOf(0L) }
    var previousMood by remember { mutableStateOf(baseMood) }
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(baseMood) {
        if (baseMood == CompanionState.Happy && previousMood != CompanionState.Happy) {
            celebrateUntilMs = System.currentTimeMillis() + 1600
        }
        previousMood = baseMood
    }
    LaunchedEffect(celebrateUntilMs) {
        while (System.currentTimeMillis() < celebrateUntilMs) {
            tickMs = System.currentTimeMillis()
            delay(100)
        }
        tickMs = System.currentTimeMillis()
    }
    val displayMood = if (tickMs < celebrateUntilMs) CompanionState.Celebrating else baseMood

    // سحب أفقي بس — محدود بعرض الشاشة عشان الأيجنت ميتسحبش بره حدود الشاشة.
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxOffsetPx = with(density) {
        val screenWidthPx = LocalConfiguration.current.screenWidthDp.dp.toPx()
        ((screenWidthPx - 64.dp.toPx()) / 2f) - 16.dp.toPx()
    }
    val draggableState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-maxOffsetPx, maxOffsetPx)
    }

    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val tapScale = remember { Animatable(1f) }
    var showBubble by remember { mutableStateOf(false) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var blinkTrigger by remember { mutableStateOf(0L) }
    var glowTrigger by remember { mutableStateOf(0L) }

    fun fireHaptic(durationMs: Long, amplitude: Int) {
        // نفس نمط TasbihaScreen بالظبط: try/catch لازم لأن VibrationEffect مش موجودة
        // كـ class أصلاً قبل API 26، فـ NoClassDefFoundError (Error مش Exception) ممكن
        // تتفلت من catch (Exception) عادي لو الترتيب غلط.
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun fireChime(mood: CompanionState) {
        ZadChime.play(
            when (mood) {
                CompanionState.Happy, CompanionState.Celebrating -> ZadChime.Tone.Success
                CompanionState.Alert -> ZadChime.Tone.Alert
                else -> ZadChime.Tone.Tap
            },
        )
    }

    if (showVoiceAssistant) {
        ZadVoiceBottomSheet(
            viewModel = viewModel,
            onDismiss = { showVoiceAssistant = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .navigationBarsPadding()
            .padding(bottom = 94.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { translationX = offsetX }
        ) {
            AnimatedVisibility(visible = showBubble, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .widthIn(max = 220.dp)
                        .zadCardShadow(RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        mascotQuickInsight(displayMood, kidsMode, agentSummary, remaining, brainReport, context),
                        style = Typography.labelSmall,
                        color = onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .scale(tapScale.value)
                    .floatingIdle(amplitude = 4f)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { showBubble = false }
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = companionStateDescription(displayMood),
                        onLongClickLabel = "المساعد الصوتي الذكي",
                        onLongClick = {
                            fireHaptic(25, 180)
                            showBubble = false
                            showVoiceAssistant = true
                        },
                        onDoubleClick = {
                            fireHaptic(25, 180)
                            showBubble = false
                            onNavigateToChat()
                        },
                        onClick = {
                            scope.launch {
                                tapScale.animateTo(1.18f, animationSpec = ZadSprings.Celebrate)
                                tapScale.animateTo(1f, animationSpec = ZadSprings.Press)
                            }
                            fireHaptic(35, 200)
                            fireChime(displayMood)
                            val now = System.currentTimeMillis()
                            blinkTrigger = now
                            glowTrigger = now
                            showBubble = true
                            scope.launch { delay(2600); showBubble = false }
                        }
                    )
            ) {
                CompanionOrb(state = displayMood, size = 64.dp, blinkTrigger = blinkTrigger, glowTrigger = glowTrigger)
            }
        }
    }
}

private fun mascotQuickInsight(
    mood: CompanionState,
    kidsMode: Boolean,
    agentSummary: com.example.data.AiAgentSummary?,
    remaining: Double?,
    brainReport: com.example.data.ZadCentralBrain.BrainReport?,
    context: Context
): String {
    if (kidsMode) {
        // وضع الأطفال مايوصلش لأرقام مالية حقيقية أصلاً (نفس قاعدة PIN gate) — الأيجنت
        // بيفضل مرح عام هنا بدل ما يسرّب رصيد/ميزانية.
        return when (mood) {
            CompanionState.Celebrating, CompanionState.Happy -> "يا سلام! 🌟 استمر كده"
            CompanionState.Alert -> "خلي بالك على حاجاتك 👀"
            CompanionState.Focused -> "بفكر... 🤔"
            CompanionState.Idle -> "يلا نلعب ونتعلم التوفير! 🎈"
        }
    }
    return when (mood) {
        CompanionState.Celebrating -> brainReport?.let { "🎉 صحتك المالية ${it.healthScore}/100 — استمر كده!" }
            ?: "🎉 خبر حلو! استمر كده"
        CompanionState.Happy -> agentSummary?.alerts?.firstOrNull { it.type == "success" }?.description?.takeIf { it.isNotBlank() }
            ?: "الأمور ماشية كويس 🌿"
        CompanionState.Alert -> agentSummary?.alerts?.firstOrNull { it.type == "warning" }?.description?.takeIf { it.isNotBlank() }
            ?: remaining?.let { "باقيلك ${com.example.data.CurrencyFormatter.format(context, it)} بس من الميزانية" }
            ?: "خد بالك من مصاريفك 👀"
        CompanionState.Focused -> "بفكر في إجابتك... 🤔"
        CompanionState.Idle -> remaining?.let { "باقيلك ${com.example.data.CurrencyFormatter.format(context, it)} من الميزانية" }
            ?: "اضغط عليّا لو محتاج مساعدة 👋"
    }
}
