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
    Idle(ZadOrbIdleSky, ZadOrbIdleDeep),
    Listening(ZadOrbListeningSky, ZadOrbListeningDeep),
    Focused(ZadOrbFocusedSky, ZadOrbFocusedDeep),
    Speaking(ZadOrbSpeakingSky, ZadOrbSpeakingDeep),
    Happy(ZadOrbHappySky, ZadOrbHappyDeep),
    Alert(ZadOrbAlertSky, ZadOrbAlertDeep),
    Celebrating(ZadOrbCelebratingSky, ZadOrbCelebratingDeep)
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
                onClick()
            }
    } else {
        orbModifier
    }

    Canvas(modifier = clickableModifier) {
        val level = audioLevel().coerceIn(0f, 1f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension / 2f) * 0.68f
        val radius = baseRadius * breathScale * (1f + 0.16f * level)

        // 1. Ambient Atmospheric Neon Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.28f + 0.35f * level),
                    deepColor.copy(alpha = 0.10f + 0.15f * level),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.80f + 0.40f * level)
            ),
            radius = radius * (1.80f + 0.40f * level),
            center = center
        )

        // 2. Waveform Resonance Ring (Inner Energetic Orbital Ring)
        val ring1Radius = radius * (1.24f + 0.16f * level)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ZadOrbNeonCyan.copy(alpha = 0.45f + 0.45f * level),
                    ZadOrbNeonMint.copy(alpha = 0.20f),
                    ZadOrbNeonCyan.copy(alpha = 0.45f + 0.45f * level)
                ),
                center = center
            ),
            radius = ring1Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.5f + 2f * level).dp.toPx()
            )
        )

        // 3. Soundwave Ripple Ring (Outer Acoustic Ring)
        val ring2Radius = radius * (1.46f + 0.26f * level)
        drawCircle(
            color = ZadOrbNeonMint.copy(alpha = (0.16f + 0.32f * level).coerceIn(0f, 0.75f)),
            radius = ring2Radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.0f + 1.2f * level).dp.toPx()
            )
        )

        // 4. Luminous Orbital Motes (6 rotating energy particles)
        for (i in 0 until 6) {
            val moteAngle = (rotationAngle * 0.6f + i * 60f) * (Math.PI / 180.0)
            val moteDist = radius * (1.35f + 0.10f * sin(blobPhase + i).toFloat() * (1f + level))
            val moteCenter = Offset(
                center.x + (moteDist * cos(moteAngle)).toFloat(),
                center.y + (moteDist * sin(moteAngle)).toFloat()
            )
            drawCircle(
                color = ZadOrbNeonCyan.copy(alpha = (0.35f + 0.45f * level).coerceIn(0f, 1f)),
                radius = (1.8f + 1.2f * level).dp.toPx(),
                center = moteCenter
            )
        }

        // Quick Tap Glow Burst
        if (glow > 0.01f) {
            drawCircle(
                color = ZadOrbNeonCyan.copy(alpha = 0.45f * glow),
                radius = radius * (1.4f + 0.5f * glow),
                center = center
            )
        }

        // 5. Living Liquid Glass Core Body
        val bodyPath = organicOrbPath(center, radius, blobPhase, level)

        // 5a. Volumetric Deep Spherical Gradient (3D Light Source at Top-Left)
        val lightOffset = center - Offset(radius * 0.35f, radius * 0.38f)
        drawPath(
            path = bodyPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    ZadOrbGlassSpecular.copy(alpha = 0.95f),
                    skyColor.copy(alpha = 0.88f),
                    ZadOrbNeonMint.copy(alpha = 0.72f),
                    deepColor.copy(alpha = 0.95f),
                    ZadOrbCoreDark
                ),
                center = lightOffset,
                radius = radius * 1.55f
            )
        )

        // 5b. Chromatic Glass Mesh Sweep Rotation
        withTransform({
            rotate(degrees = rotationAngle, pivot = center)
        }) {
            val meshSweepBrush = Brush.sweepGradient(
                colors = listOf(
                    skyColor.copy(alpha = 0.55f),
                    ZadOrbMeshMint.copy(alpha = 0.45f),
                    deepColor.copy(alpha = 0.75f),
                    ZadOrbNeonCyan.copy(alpha = 0.60f),
                    ZadOrbMeshTeal.copy(alpha = 0.45f),
                    deepColor.copy(alpha = 0.75f),
                    skyColor.copy(alpha = 0.55f)
                ),
                center = center
            )
            drawPath(path = bodyPath, brush = meshSweepBrush)
        }

        // 5c. Top-Left Glass Specular Sheen Crescent (Curved Glass Refraction)
        val highlightCenter = center - Offset(radius * 0.28f, radius * 0.30f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.75f * sheenProgress + 0.25f * level).coerceIn(0f, 0.95f)),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = highlightCenter,
                radius = radius * 0.50f
            ),
            radius = radius * 0.50f,
            center = highlightCenter
        )

        // 5d. Fresnel Edge Glass Rim (Thin luminous boundary)
        drawPath(
            path = bodyPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    ZadOrbNeonCyan.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.90f),
                    deepColor.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.85f)
                ),
                center = center
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (1.8.dp.toPx() * (1f + 0.45f * level))
            )
        )

        // 6. Sleek Minimalist Robot Slit Eyes (Glow Arcs)
        drawRobotSlitEyes(
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
 * رسم اللمعات الأفقية المنحنية الحديثة للكائن التفاعلي (Sleek Minimalist Curved Slits / Glow Arcs)
 * كبديل عصري للعيون النقطية المشوهة، مع دعم الحركة والنبض مع نبرة الصوت.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRobotSlitEyes(
    state: CompanionState,
    center: Offset,
    radius: Float,
    openAmount: Float,
    audioLevel: Float,
    sheenProgress: Float
) {
    val eyeSpacing = radius * 0.34f
    val baseEyeY = center.y - radius * 0.04f
    val leftCenter = Offset(center.x - eyeSpacing, baseEyeY)
    val rightCenter = Offset(center.x + eyeSpacing, baseEyeY)
    val slitWidth = radius * 0.28f
    val baseHeight = radius * 0.075f
    val effectiveHeight = (baseHeight + radius * 0.05f * audioLevel) * openAmount.coerceAtLeast(0.06f)

    when (state) {
        CompanionState.Happy, CompanionState.Celebrating -> {
            // لمعات مبتسمة للأعلى بانحناءة نيون زاهية
            val glowCol = if (state == CompanionState.Celebrating) ZadHeartYellow else ZadOrbNeonMint
            drawGlowArc(leftCenter, slitWidth, effectiveHeight, isSmiling = true, glowColor = glowCol)
            drawGlowArc(rightCenter, slitWidth, effectiveHeight, isSmiling = true, glowColor = glowCol)
        }
        CompanionState.Listening -> {
            // لمعات يقظة ومائلة قليلاً للأعلى مع توهج سماوي كهربائي نابض
            val listeningWidth = slitWidth * (1f + 0.15f * audioLevel)
            drawGlowArc(leftCenter, listeningWidth, effectiveHeight * 1.15f, tilt = 3f, glowColor = ZadOrbNeonCyan)
            drawGlowArc(rightCenter, listeningWidth, effectiveHeight * 1.15f, tilt = -3f, glowColor = ZadOrbNeonCyan)
        }
        CompanionState.Speaking -> {
            // لمعات متجاوبة أفقياً تهتز مع سعة الصوت الصادر
            val voiceMod = 1f + 0.35f * sin(sheenProgress * Math.PI.toFloat() * 2f)
            val speakingHeight = effectiveHeight * voiceMod
            drawGlowArc(leftCenter, slitWidth * 1.05f, speakingHeight, glowColor = ZadOrbSlitGlow)
            drawGlowArc(rightCenter, slitWidth * 1.05f, speakingHeight, glowColor = ZadOrbSlitGlow)
        }
        CompanionState.Focused -> {
            // لمعات ليزرية أفقية رفيعة ومركزة
            drawGlowArc(leftCenter, slitWidth * 1.1f, effectiveHeight * 0.70f, glowColor = ZadOrbFocusedSky)
            drawGlowArc(rightCenter, slitWidth * 1.1f, effectiveHeight * 0.70f, glowColor = ZadOrbFocusedSky)
        }
        CompanionState.Alert -> {
            // لمعات حادة مائلة مع توهج مرجاني تحذيري
            drawGlowArc(leftCenter, slitWidth, effectiveHeight * 0.9f, tilt = -5f, glowColor = ZadOrbAlertSky)
            drawGlowArc(rightCenter, slitWidth, effectiveHeight * 0.9f, tilt = 5f, glowColor = ZadOrbAlertSky)
        }
        CompanionState.Idle -> {
            // لمعات روبوتية أفقية هادئة تتنفس بسلاسة
            drawGlowArc(leftCenter, slitWidth, effectiveHeight, glowColor = ZadOrbNeonCyan)
            drawGlowArc(rightCenter, slitWidth, effectiveHeight, glowColor = ZadOrbNeonCyan)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlowArc(
    center: Offset,
    width: Float,
    height: Float,
    isSmiling: Boolean = false,
    tilt: Float = 0f,
    glowColor: Color = ZadOrbNeonCyan
) {
    if (width < 2f) return
    val hw = width / 2f
    val path = Path()

    if (isSmiling) {
        val curveDepth = height * 1.2f
        path.moveTo(center.x - hw, center.y + curveDepth * 0.25f)
        path.quadraticTo(center.x, center.y + curveDepth + height, center.x + hw, center.y + curveDepth * 0.25f)
        path.quadraticTo(center.x, center.y + curveDepth, center.x - hw, center.y + curveDepth * 0.25f)
        path.close()
    } else {
        val bow = height * 0.25f
        path.moveTo(center.x - hw, center.y)
        path.quadraticTo(center.x, center.y - bow - height / 2f, center.x + hw, center.y)
        path.quadraticTo(center.x, center.y + bow + height / 2f, center.x - hw, center.y)
        path.close()
    }

    withTransform({
        if (tilt != 0f) rotate(tilt, center)
    }) {
        // 1. هالة النيون الخارجية الناعمة
        drawPath(
            path = path,
            color = glowColor.copy(alpha = 0.38f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = height * 2.2f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        // 2. جسم اللمعة النيون
        drawPath(
            path = path,
            color = glowColor.copy(alpha = 0.85f)
        )
        // 3. قلب ليزري فائق النصوع
        val corePath = Path().apply {
            val coreHw = hw * 0.75f
            moveTo(center.x - coreHw, center.y)
            lineTo(center.x + coreHw, center.y)
        }
        drawPath(
            path = corePath,
            color = ZadOrbSlitCore,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (height * 0.45f).coerceAtLeast(1.2f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
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
