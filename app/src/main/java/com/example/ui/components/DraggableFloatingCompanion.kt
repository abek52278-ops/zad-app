package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.voice.ZadCutePetSoundFx
import kotlinx.coroutines.launch

/**
 * 🐱 كائن الأفاتار القديم التفاعلي القابل للسحب واللعب (Draggable Companion Pet)
 * يطفو أعلى شريط التنقل السفلي مع إمكانية السحب الحر في أي مكان على الشاشة
 * والارتداد الفيزيائي المرن (Spring Physics) والاستجابة الحية بالخرخرة والنغمات.
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
    val scope = rememberCoroutineScope()
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val tapScale = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    var blinkTrigger by remember { mutableLongStateOf(0L) }
    var glowTrigger by remember { mutableLongStateOf(0L) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(90f)
            .padding(bottom = bottomNavHeight + 12.dp, start = 18.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = animOffsetX.value
                    translationY = animOffsetY.value
                }
                .scale(tapScale.value * if (isDragging) 1.14f else 1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            fireHaptic(20, 140)
                            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.Purr, 0.40f)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                animOffsetX.snapTo(animOffsetX.value + dragAmount.x)
                                animOffsetY.snapTo(animOffsetY.value + dragAmount.y)
                            }
                        },
                        onDragEnd = {
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
                        },
                        onDragCancel = {
                            isDragging = false
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
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            fireHaptic(30, 200)
                            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.50f)
                            val now = System.currentTimeMillis()
                            blinkTrigger = now
                            glowTrigger = now
                            scope.launch {
                                tapScale.animateTo(1.22f, animationSpec = ZadSprings.Celebrate)
                                tapScale.animateTo(1f, animationSpec = ZadSprings.Press)
                            }
                            onOpenVoice()
                        },
                        onDoubleTap = {
                            fireHaptic(35, 220)
                            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.CelebrationTrill, 0.50f)
                            onOpenChat()
                        },
                        onLongPress = {
                            fireHaptic(40, 240)
                            ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp, 0.50f)
                            onOpenVoice()
                        }
                    )
                }
        ) {
            CompanionOrb(
                state = currentDisplayMood,
                size = 58.dp,
                blinkTrigger = blinkTrigger,
                glowTrigger = glowTrigger,
                onClick = onOpenVoice
            )
        }
    }
}
