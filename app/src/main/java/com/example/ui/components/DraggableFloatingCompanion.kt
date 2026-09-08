package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.theme.Typography
import com.example.voice.ZadCutePetSoundFx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 🐱 كائن الأفاتار التفاعلي القابل للسحب واللعب (Draggable Companion Pet)
 * - يطفو أعلى شريط التنقل مع إمكانية السحب الحر في أي اتجاه
 * - ارتداد فيزيائي مرن (Spring Physics) نحو موضع الارتكاز
 * - فقاعة حديث تفاعلية ذكية (Speech Bubble) تظهر عند النقر
 * - إخفاء سلس تلقائي عند ظهور لوحة المفاتيح لمنع حجب حقول الإدخال
 * - معالج إيماءات موحّد يضمن سلاسة النقر الفردي، المزدوج، والمطول
 */
@Composable
fun DraggableFloatingCompanion(
    companionMood: CompanionState = CompanionState.Idle,
    bottomNavHeight: Dp = 80.dp,
    onOpenVoice: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val tapScale = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    var blinkTrigger by remember { mutableLongStateOf(0L) }
    var glowTrigger by remember { mutableLongStateOf(0L) }
    var showBubble by remember { mutableStateOf(false) }
    var bubbleDismissJob by remember { mutableStateOf<Job?>(null) }

    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

    fun fireHaptic(durationMs: Long, amplitude: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    val currentDisplayMood = when {
        isDragging -> CompanionState.Happy
        else -> companionMood
    }

    AnimatedVisibility(
        visible = !isKeyboardOpen,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(90f)
                .padding(bottom = bottomNavHeight + 12.dp, start = 18.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animOffsetX.value
                        translationY = animOffsetY.value
                    }
            ) {
                AnimatedVisibility(
                    visible = showBubble,
                    enter = fadeIn(tween(180)) + expandVertically(),
                    exit = fadeOut(tween(140)) + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .widthIn(max = 210.dp)
                            .clickable {
                                showBubble = false
                                onOpenVoice()
                            }
                    ) {
                        Text(
                            text = stringResource(
                                when (currentDisplayMood) {
                                    CompanionState.Happy -> R.string.companion_bubble_happy
                                    CompanionState.Alert -> R.string.companion_bubble_alert
                                    CompanionState.Celebrating -> R.string.companion_bubble_celebrating
                                    else -> R.string.companion_bubble_idle
                                }
                            ),
                            style = Typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .scale(tapScale.value * if (isDragging) 1.14f else 1f)
                        .pointerInput(Unit) {
                            var lastTapTime = 0L
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTime = System.currentTimeMillis()
                                val startPos = down.position
                                var hasExceededSlop = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!hasExceededSlop) {
                                            val pressDuration = System.currentTimeMillis() - downTime
                                            if (pressDuration >= viewConfiguration.longPressTimeoutMillis) {
                                                // ضغطة مطولة: مساعد صوتي مباشر
                                                showBubble = false
                                                fireHaptic(40, 240)
                                                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.50f)
                                                onOpenVoice()
                                            } else {
                                                val now = System.currentTimeMillis()
                                                if (now - lastTapTime < viewConfiguration.doubleTapTimeoutMillis) {
                                                    // نقر مزدوج: فتح المحادثة الذكية
                                                    lastTapTime = 0L
                                                    showBubble = false
                                                    fireHaptic(35, 220)
                                                    ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.CelebrationTrill, 0.50f)
                                                    onOpenChat()
                                                } else {
                                                    // نقر فردي: تفاعل صوتي ولمسي وفقاعة الحديث الذكية
                                                    lastTapTime = now
                                                    fireHaptic(30, 200)
                                                    ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.50f)
                                                    val triggerNow = System.currentTimeMillis()
                                                    blinkTrigger = triggerNow
                                                    glowTrigger = triggerNow
                                                    scope.launch {
                                                        tapScale.animateTo(1.22f, animationSpec = ZadSprings.Celebrate)
                                                        tapScale.animateTo(1f, animationSpec = ZadSprings.Press)
                                                    }
                                                    showBubble = true
                                                    bubbleDismissJob?.cancel()
                                                    bubbleDismissJob = scope.launch {
                                                        delay(3500)
                                                        showBubble = false
                                                    }
                                                }
                                            }
                                        } else {
                                            // إفلات السحب: ارتداد زنبركي مرن (Spring Physics)
                                            isDragging = false
                                            fireHaptic(15, 100)
                                            scope.launch {
                                                launch {
                                                    animOffsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    animOffsetY.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        break
                                    }

                                    val delta = change.position - startPos
                                    if (!hasExceededSlop && delta.getDistance() > viewConfiguration.touchSlop) {
                                        hasExceededSlop = true
                                        isDragging = true
                                        showBubble = false
                                        fireHaptic(20, 140)
                                        ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.Purr, 0.40f)
                                    }

                                    if (hasExceededSlop) {
                                        val dragDelta = change.position - change.previousPosition
                                        change.consume()
                                        scope.launch {
                                            animOffsetX.snapTo(animOffsetX.value + dragDelta.x)
                                            animOffsetY.snapTo(animOffsetY.value + dragDelta.y)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    CompanionOrb(
                        state = currentDisplayMood,
                        size = 58.dp,
                        blinkTrigger = blinkTrigger,
                        glowTrigger = glowTrigger,
                        onClick = null
                    )
                }
            }
        }
    }
}
