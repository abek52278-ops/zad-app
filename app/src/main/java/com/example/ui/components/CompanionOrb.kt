package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.voice.LiveVoiceState
import com.example.voice.ZadCutePetSoundFx
import com.example.voice.VoiceState
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * حالة الأيجنت العاطفية — كل حالة بتحدد لون الكورة وشكل العيون.
 * زمردي: عادي/هادئ. سماوي: بيسمع. بنفسجي: مركّز/بيحلل. أزرق: بيتكلم.
 * أخضر فاتح: سعيد/إنجاز. أحمر: تنبيه. دهبي: احتفال.
 *
 * الألوان دي **هوية**، مش توكنات ثيم، ومقصود إنها ثابتة بين اللايت والدارك:
 * "أحمر = تنبيه" لازم يفضل أحمر زي ما شعار مابيتغيّرش. اللي بيتجاوب مع الثيم هو
 * الهالة حوالين الكورة، لأنها مرسومة بشفافية فبتتركّب فوق أرضية الصفحة.
 *
 * Listening/Speaking اتضافوا وقت توحيد الأفاتار (كانوا في ZadBotEmotion المتوازي).
 * سماوي وأزرق لأنهم لازم يتفرقوا عن الخمسة اللي فاتوا وعن بعض — دول لحظتا الإدخال
 * والإخراج في نفس المكالمة والمستخدم بيفرّق بينهم بالنظر.
 */
enum class CompanionState(val skyColor: Color, val deepColor: Color) {
    Idle(Color(0xFF34D399), Color(0xFF064E3B)),
    Listening(Color(0xFF67E8F9), Color(0xFF0E7490)),
    Focused(Color(0xFFB388FF), Color(0xFF4A148C)),
    Speaking(Color(0xFF93C5FD), Color(0xFF1D4ED8)),
    Happy(Color(0xFF7CFFB2), Color(0xFF00B26A)),
    Alert(Color(0xFFFF8A80), Color(0xFFD32F2F)),
    Celebrating(Color(0xFFFFE066), Color(0xFFF59E0B))
}

/**
 * ترجمة حالة الصوت لمزاج — أو null لو الصوت مش شغال.
 *
 * null معناها "مالكش دعوة"، **مش** "هادئ". ساعتها المزاج بيرجع للمسار الذكي
 * (شات/تنبيهات). لو رجّعنا Idle هنا، الصوت الخامل كان هيدهس تنبيه ميزانية متجاوزة
 * وتقعد الكورة تقول "هادئ" — والفرق ده مابيكسرش أي بناء، عشان كده متغطّى بتست.
 *
 * دالة نقية على مستوى الملف مش ميثود في الـViewModel: ZadViewModel مايتعملش منه
 * نسخة في تست JVM (SQLCipher محتاج مكتبة أصلية — نفس السبب اللي HomeScreenTest
 * متعلّم بيه @Ignore)، فحطّها هناك كان معناه إنها مستحيلة الاختبار.
 */
fun companionMoodForVoice(voice: VoiceState): CompanionState? = when (voice) {
    is VoiceState.Listening -> CompanionState.Listening
    is VoiceState.Thinking -> CompanionState.Focused
    is VoiceState.Speaking -> CompanionState.Speaking
    is VoiceState.Recognized -> CompanionState.Happy
    is VoiceState.Idle, is VoiceState.Error -> null
}

/**
 * نفس القاعدة للمكالمة الحية. Connecting → Focused لأن الاتصال شغل بيحصل ورا
 * الكواليس والمستخدم مستني — نفس معنى "بيفكر" بالظبط.
 */
fun companionMoodForLiveVoice(live: LiveVoiceState): CompanionState? = when (live) {
    is LiveVoiceState.Listening -> CompanionState.Listening
    is LiveVoiceState.ModelSpeaking -> CompanionState.Speaking
    is LiveVoiceState.Connecting -> CompanionState.Focused
    is LiveVoiceState.Idle, is LiveVoiceState.Error -> null
}

/**
 * الوصف المسموع لحالة الأيجنت — لقارئ الشاشة، الشكل واللون بصريين بس.
 *
 * اتحوّلت لـ stringResource وقت إضافة Listening/Speaking: دي نصوص contentDescription
 * وTalkBack بيقراها لضعاف البصر، فهي نصوص واجهة بحسب قاعدة i18n في CLAUDE.md.
 * كانت عربي ثابت؛ إضافة اتنين جداد بنفس الشكل كانت هتزوّد المخالفة مش تقفلها.
 */
@Composable
fun companionStateDescription(state: CompanionState): String = stringResource(
    when (state) {
        CompanionState.Idle -> R.string.companion_state_idle
        CompanionState.Listening -> R.string.companion_state_listening
        CompanionState.Focused -> R.string.companion_state_focused
        CompanionState.Speaking -> R.string.companion_state_speaking
        CompanionState.Happy -> R.string.companion_state_happy
        CompanionState.Alert -> R.string.companion_state_alert
        CompanionState.Celebrating -> R.string.companion_state_celebrating
    }
)

/**
 * خطوة الفلتر الأسّي للسعة — دالة نقية عشان تكون قابلة للاختبار.
 *
 * **الصعود أسرع من الهبوط بقصد:** 0.45 طالع عشان أول مقطع نطق يبان فوراً، و0.12
 * نازل عشان الكورة ماترجعش لصفر في كل سكتة بين كلمتين. لو الاتنين اتساووا، الحركة
 * بتتقري إما "بطيئة ومتأخرة" أو "مرتعشة" — والفرق ده هو كل الفرق بين كورة بتتجاوب
 * وكورة بتتنطط.
 */
internal fun smoothOrbLevel(current: Float, raw: Float): Float {
    val target = raw.coerceIn(0f, 1f)
    val factor = if (target > current) 0.45f else 0.12f
    return current + (target - current) * factor
}

/**
 * مصدر السعة الموحّد للكورة — بيجمّع الفلو بنفسه وبينعّمه.
 *
 * **بياخد الـflow مش القيمة، وده جوهر الحارس.** لو المستدعي عمل
 * `micLevel.collectAsState()` وبعت الـ`Float`، **المستدعي نفسه** كان هيعيد التركيب
 * مع كل انبعاث — والمصدر بيبعت مرة لكل بافر صوت (`ZadLiveVoiceSession.updateMicLevel`
 * جوه لوب القراءة، و`ZadVoiceManager` من `onRmsChanged`)، يعني عشرات المرات في
 * الثانية طول المكالمة. بالجمع هنا، الانبعاث بيكتب في `MutableFloatState` واللي
 * بيقراها هو **الـdraw scope بس** عن طريق اللامبدا اللي بترجع — فبيتبطّل الرسم
 * لوحده (`invalidateDraw`) ومفيش ولا recomposition واحدة من الصوت.
 *
 * **والتنعيم مش تجميل:** RMS خام بيقفز بين بافر وبافر فبيتقري "ارتعاش". فلتر أسّي
 * بصعود أسرع من الهبوط بيدي إحساس "بتتجاوب": بتلحق أول مقطع نطق فوراً، وماترجعش
 * لصفر في كل سكتة بين كلمتين.
 *
 * [levelFlow] = `null` معناها مفيش صوت شغال، فبترجع صفر والكورة تكمّل تنفسها العادي.
 */
@Composable
fun rememberOrbAudioLevel(levelFlow: kotlinx.coroutines.flow.StateFlow<Float>?): () -> Float {
    val smoothed = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(levelFlow) {
        if (levelFlow == null) {
            smoothed.floatValue = 0f
            return@LaunchedEffect
        }
        levelFlow.collect { raw ->
            smoothed.floatValue = smoothOrbLevel(smoothed.floatValue, raw)
        }
    }
    return remember(smoothed) { { smoothed.floatValue } }
}

/**
 * الكورة الهلامية — أفتار الأيجنت.
 *
 * [animated] بيتحكم في كل الحركة المستمرة (نبض + تموّج السائل + الرمش العشوائي). خليه true
 * بس في الأماكن البارزة (رأس الشاشة/الشات) — نسخة كل فقاعة رسالة في لستة طويلة بتتقفل
 * (animated=false) عشان مانشغلش عشرات الـ infinite animation loops مع بعض في LazyColumn.
 */
@Composable
fun CompanionOrb(
    state: CompanionState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animated: Boolean = true,
    // كل تغيير في القيمة دي (مش القيمة نفسها) بيطلق رمشتين سريعتين فوراً — استخدامها
    // الوحيد دلوقتي: FloatingMascotCompanion بيغيّرها لحظة الـ tap عشان "تعبير لطيف"
    // بدل ما ينتظر الرمشة العشوائية العادية (٢٫٢-٥ ثواني).
    blinkTrigger: Long = 0L,
    // نفس اتفاقية blinkTrigger: التغيير هو الإشارة. بيولّع هالة حوالين الكورة وبتخبي
    // في ٤٥٠ms. القفزة (tapScale) بتحرّك الحجم، ودي بتحرّك الضوء — الاتنين مع بعض
    // هما اللي بيخلوا اللمسة تحس إنها اترددت، مش اتسجلت وخلاص.
    glowTrigger: Long = 0L,
    /**
     * سعة الصوت اللحظية 0..1 — الكورة بتنبض بيها وهي بتسمع.
     *
     * **لامبدا مش `Float`، والسبب أدائي مش أسلوبي.** المصدر
     * (`ZadLiveVoiceSession.updateMicLevel`) بيحدّث القيمة **مرة لكل بافر مايك** —
     * عشرات المرات في الثانية على 16kHz mono PCM16. لو البارامتر كان `Float`،
     * كل انبعاث كان هيعمل recomposition لشجرة الكورة كلها بنفس المعدل، طول المكالمة.
     * كلامبدا، القراءة بتحصل **جوه الـdraw scope** فبتبطّل الرسم لوحده
     * (`invalidateDraw`) من غير إعادة تركيب. استخدم [rememberOrbAudioLevel] كمصدر —
     * هي كمان بتنعّم القيمة عشان الحركة تتقري "حية" مش "مرتعشة".
     *
     * الافتراضي `{ 0f }` يعني كل نقطة نداء ماتمرّرهاش بتفضل زي ما هي بالظبط.
     */
    audioLevel: () -> Float = { 0f },
    /**
     * لمسة على الكورة. بتشغّل قفزة + زقزقة + رمشتين + هالة مع بعض.
     *
     * الرمش والهالة كان ليهم آلية كاملة (`blinkTrigger`/`glowTrigger`) و**صفر نقط
     * نداء** — التعليق فوقهم كان بيشاور على `FloatingMascotCompanion` وهو مابقاش
     * موجود. البارامتر ده بيوصّلهم.
     */
    onClick: (() -> Unit)? = null
) {
    // اللمسة بتولّد نفس الإشارتين اللي البارامترات الخارجية بتولّدهم، فالمسارين
    // بيروحوا لنفس المكان ومفيش منطق متكرر.
    var tapPulse by remember { mutableStateOf(0L) }
    val effectiveBlink = if (tapPulse != 0L) tapPulse else blinkTrigger
    val effectiveGlow = if (tapPulse != 0L) tapPulse else glowTrigger

    val skyColor by animateColorAsState(state.skyColor, tween(500), label = "orbSky")
    val deepColor by animateColorAsState(state.deepColor, tween(500), label = "orbDeep")

    val breathScale: Float
    val blobPhase: Float
    val rotationAngle: Float
    val sheenProgress: Float

    if (animated) {
        val breathTransition = rememberInfiniteTransition(label = "orbBreath")
        breathScale = breathTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbBreathScale"
        ).value

        val blobTransition = rememberInfiniteTransition(label = "orbBlob")
        blobPhase = blobTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing)
            ),
            label = "orbBlobPhase"
        ).value

        val rotateTransition = rememberInfiniteTransition(label = "orbRotate")
        rotationAngle = rotateTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbRotation"
        ).value

        val sheenTransition = rememberInfiniteTransition(label = "orbSheen")
        sheenProgress = sheenTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbSheen"
        ).value
    } else {
        breathScale = 1f
        blobPhase = 0f
        rotationAngle = 0f
        sheenProgress = 0.3f
    }

    var eyeOpen by remember { mutableFloatStateOf(1f) }
    val eyeOpenAnimated by animateFloatAsState(
        targetValue = eyeOpen,
        animationSpec = tween(85, easing = FastOutSlowInEasing),
        label = "orbBlink"
    )

    LaunchedEffect(effectiveBlink) {
        if (effectiveBlink != 0L) {
            eyeOpen = 0.08f
            delay(90)
            eyeOpen = 1f
            delay(70)
            eyeOpen = 0.08f
            delay(90)
            eyeOpen = 1f
        }
    }

    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2400, 5200))
            eyeOpen = 0.08f
            delay(100)
            eyeOpen = 1f
        }
    }

    var glowTarget by remember { mutableFloatStateOf(0f) }
    val glow by animateFloatAsState(glowTarget, tween(450, easing = FastOutSlowInEasing), label = "orbGlow")
    LaunchedEffect(effectiveGlow) {
        if (effectiveGlow != 0L) {
            glowTarget = 1f
            delay(120)
            glowTarget = 0f
        }
    }

    // القفزة النابية على اللمس
    val tapScale by animateFloatAsState(
        targetValue = if (glowTarget > 0f) 0.92f else 1f,
        animationSpec = ZadSprings.Press,
        label = "orbTapScale"
    )

    val orbModifier = if (animated) {
        val description = companionStateDescription(state)
        modifier.size(size).semantics { contentDescription = description }
    } else {
        modifier.size(size)
    }
    val clickableModifier = if (onClick != null) {
        orbModifier
            .scale(tapScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tapPulse = System.currentTimeMillis()
                ZadCutePetSoundFx.play(ZadCutePetSoundFx.PetSound.HappyChirp)
                onClick()
            }
    } else {
        orbModifier
    }

    Canvas(modifier = clickableModifier) {
        val level = audioLevel().coerceIn(0f, 1f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension / 2f) * 0.72f
        val radius = baseRadius * breathScale * (1f + 0.20f * level)

        // 1. Ambient Pulsing Outer Aura (ElevenLabs Glow Halo)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.25f + 0.35f * level),
                    skyColor.copy(alpha = 0.08f + 0.15f * level),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.65f + 0.35f * level)
            ),
            radius = radius * (1.65f + 0.35f * level),
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.40f + 0.30f * level),
                    deepColor.copy(alpha = 0.15f + 0.10f * level),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.28f + 0.20f * level)
            ),
            radius = radius * (1.28f + 0.20f * level),
            center = center
        )

        // هالة الضغطة السريعة
        if (glow > 0.01f) {
            drawCircle(
                color = skyColor.copy(alpha = 0.45f * glow),
                radius = radius * (1.4f + 0.5f * glow),
                center = center
            )
        }

        // 2. Organic Mesh Body with Fluid Dynamic Rotation
        val bodyPath = organicOrbPath(center, radius, blobPhase, level)

        withTransform({
            rotate(degrees = rotationAngle, pivot = center)
        }) {
            // Silky Sweep Gradient Mesh Core
            val meshSweepBrush = Brush.sweepGradient(
                colors = listOf(
                    skyColor,
                    ZadOrbMeshMint,
                    deepColor,
                    skyColor.copy(alpha = 0.9f),
                    ZadOrbMeshTeal,
                    deepColor,
                    skyColor
                ),
                center = center
            )
            drawPath(path = bodyPath, brush = meshSweepBrush)
        }

        // 3. Volumetric Specular Depth & Light Source
        val lightOffset = center - Offset(radius * 0.32f, radius * 0.35f)
        drawPath(
            path = bodyPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f + 0.25f * level),
                    skyColor.copy(alpha = 0.60f),
                    deepColor.copy(alpha = 0.85f)
                ),
                center = lightOffset,
                radius = radius * 1.45f
            )
        )

        // 4. Silky Highlight Crescent & Core Glow
        val highlightCenter = center - Offset(radius * 0.25f, radius * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (sheenProgress + 0.25f * level).coerceIn(0f, 0.9f)),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = highlightCenter,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = highlightCenter
        )

        // 5. Rim Luminescence & Edge Glass Finish
        drawPath(
            path = bodyPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f),
                    skyColor.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.85f),
                    deepColor.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.75f)
                ),
                center = center
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.5.dp.toPx() * (1f + 0.5f * level))
            )
        )

        // 6. Dynamic Expressive Living Eyes (Interactive Companion Orb)
        drawCompanionEyes(
            state = state,
            center = center,
            radius = radius,
            openAmount = eyeOpenAnimated,
            audioLevel = level,
            sheenProgress = sheenProgress
        )
    }
}

/**
 * رسم العيون التعبيرية الحية للكائن التفاعلي حسب الحالة والمشاعر ونبرة الصوت
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompanionEyes(
    state: CompanionState,
    center: Offset,
    radius: Float,
    openAmount: Float,
    audioLevel: Float,
    sheenProgress: Float
) {
    val eyeSpacing = radius * 0.38f
    val eyeWidth = radius * 0.22f
    val baseEyeY = center.y - radius * 0.08f
    val leftCenter = Offset(center.x - eyeSpacing, baseEyeY)
    val rightCenter = Offset(center.x + eyeSpacing, baseEyeY)

    when (state) {
        CompanionState.Happy, CompanionState.Celebrating -> {
            val heartHeight = radius * 0.44f * openAmount
            val heartColor = if (state == CompanionState.Celebrating) Color(0xFFFFF176) else Color.White
            if (openAmount > 0.25f) {
                drawHeart(leftCenter, eyeWidth * 1.25f, heartHeight, heartColor)
                drawHeart(rightCenter, eyeWidth * 1.25f, heartHeight, heartColor)
            } else {
                drawSmilingArc(leftCenter, eyeWidth, radius * 0.12f)
                drawSmilingArc(rightCenter, eyeWidth, radius * 0.12f)
            }
        }
        CompanionState.Listening -> {
            val eyeHeight = (radius * 0.42f + radius * 0.14f * audioLevel) * openAmount
            val pupilOffset = Offset(0f, -radius * 0.03f)
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
        }
        CompanionState.Speaking -> {
            val eyeHeight = (radius * 0.38f + radius * 0.12f * audioLevel) * openAmount
            val pupilOffset = Offset(0f, radius * 0.02f * sin(sheenProgress * Math.PI.toFloat()))
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, pupilOffset, openAmount)
        }
        CompanionState.Focused -> {
            val eyeHeight = radius * 0.28f * openAmount
            drawFocusedEye(leftCenter, eyeWidth * 1.15f, eyeHeight)
            drawFocusedEye(rightCenter, eyeWidth * 1.15f, eyeHeight)
        }
        CompanionState.Alert -> {
            val eyeHeight = radius * 0.45f * openAmount
            drawAlertEye(leftCenter, eyeWidth, eyeHeight)
            drawAlertEye(rightCenter, eyeWidth, eyeHeight)
        }
        CompanionState.Idle -> {
            val eyeHeight = radius * 0.38f * openAmount
            drawExpressiveEye(leftCenter, eyeWidth, eyeHeight, Offset.Zero, openAmount)
            drawExpressiveEye(rightCenter, eyeWidth, eyeHeight, Offset.Zero, openAmount)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpressiveEye(
    center: Offset,
    width: Float,
    height: Float,
    pupilOffset: Offset,
    openAmount: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, minOf(width / 2f, height / 2f))
    // Outer white sclera
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f)),
        cornerRadius = cornerRadius
    )
    // Dark expressive pupil
    if (openAmount > 0.35f) {
        val pupilRadius = minOf(width, height) * 0.36f
        val pCenter = center + pupilOffset
        drawCircle(
            color = Color(0xFF0F172A),
            radius = pupilRadius,
            center = pCenter
        )
        // Specular reflection glint
        drawCircle(
            color = Color.White,
            radius = pupilRadius * 0.38f,
            center = pCenter - Offset(pupilRadius * 0.32f, pupilRadius * 0.32f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusedEye(
    center: Offset,
    width: Float,
    height: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, height / 2f)
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f)),
        cornerRadius = cornerRadius
    )
    drawCircle(
        color = Color(0xFF4A148C),
        radius = minOf(width, height) * 0.32f,
        center = center
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlertEye(
    center: Offset,
    width: Float,
    height: Float
) {
    if (height < 2f) return
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, height / 2f)
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(3f)),
        cornerRadius = cornerRadius
    )
    drawCircle(
        color = Color(0xFFDC2626),
        radius = minOf(width, height) * 0.38f,
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = minOf(width, height) * 0.16f,
        center = center - Offset(width * 0.1f, height * 0.1f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmilingArc(
    center: Offset,
    width: Float,
    height: Float
) {
    val path = Path().apply {
        moveTo(center.x - width / 2f, center.y + height / 2f)
        quadraticTo(center.x, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
    }
    drawPath(
        path = path,
        color = Color.White,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.5f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    at: Offset,
    width: Float,
    height: Float,
    color: Color = Color.White
) {
    if (height < 3f) return
    val hw = width / 2f
    val path = Path().apply {
        moveTo(at.x, at.y - height * 0.35f)
        cubicTo(
            at.x - hw * 1.1f, at.y - height * 0.85f,
            at.x - hw * 1.3f, at.y + height * 0.05f,
            at.x, at.y + height * 0.55f
        )
        cubicTo(
            at.x + hw * 1.3f, at.y + height * 0.05f,
            at.x + hw * 1.1f, at.y - height * 0.85f,
            at.x, at.y - height * 0.35f
        )
        close()
    }
    drawPath(path, color = color)
}

/**
 * دالة مسار السائل العضوي — تموج جيب متعدد النغمات ينتج شكل هلامي انسيابي متطور
 */
private fun organicOrbPath(center: Offset, baseRadius: Float, phase: Float, level: Float = 0f): Path {
    val pointsCount = 12
    val amplitude = 0.042f * (1f + 1.2f * level)
    val points = (0 until pointsCount).map { i ->
        val angle = (i.toFloat() / pointsCount) * 2 * Math.PI.toFloat()
        val freq1 = 2.0f
        val freq2 = 3.0f
        val wave = sin(phase * freq1 + i * 0.8f) * 0.6f + cos(phase * freq2 + i * 1.2f) * 0.4f
        val r = baseRadius * (1f + amplitude * wave.toFloat())
        Offset(center.x + r * cos(angle), center.y + r * sin(angle))
    }

    val path = Path()
    val start = Offset((points.last().x + points.first().x) / 2f, (points.last().y + points.first().y) / 2f)
    path.moveTo(start.x, start.y)
    for (i in points.indices) {
        val current = points[i]
        val next = points[(i + 1) % points.size]
        val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
        path.quadraticTo(current.x, current.y, mid.x, mid.y)
    }
    path.close()
    return path
}

private val alertToneWords = listOf("تنبيه", "تحذير", "خطر", "حذر", "تجاوزت", "نفاد", "أوشك", "قارب على النفاد")
private val happyToneWords = listOf("ممتاز", "أحسنت", "تهانينا", "مبروك", "رائع", "وفرت", "نجحت", "تحقيق هدف")

/** استنتاج حالة الأيجنت من نص رسالة الشات — مافيش استدعاء AI جديد، تصنيف كلمات مفتاحية محلي بس. */
fun companionStateForMessage(text: String): CompanionState = when {
    alertToneWords.any { text.contains(it) } -> CompanionState.Alert
    happyToneWords.any { text.contains(it) } -> CompanionState.Happy
    else -> CompanionState.Idle
}
