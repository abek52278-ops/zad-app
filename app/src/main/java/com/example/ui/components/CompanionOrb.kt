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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    // في ٤٥٠ms. القفزة (tapScale في FloatingMascot) بتحرّك الحجم، ودي بتحرّك الضوء —
    // الاتنين مع بعض هما اللي بيخلوا اللمسة تحس إنها اترددت، مش اتسجلت وخلاص.
    glowTrigger: Long = 0L
) {
    val skyColor by animateColorAsState(state.skyColor, tween(500), label = "orbSky")
    val deepColor by animateColorAsState(state.deepColor, tween(500), label = "orbDeep")

    val breathScale: Float
    val blobPhase: Float
    val eyeOpenAmount: Float
    // 1 = صاحية تماماً، أقل من كده = جفون نازلة. منفصلة عن الرمشة عشان الاتنين ممكن
    // يحصلوا مع بعض: بترمش وهي نعسانة برضه.
    var drowsiness: Float = 1f
    // 0 = ساكنة، 1 = في عزّ التمطّي. بتتمدّ رأسياً وتضيق أفقياً — ده اللي بيخلي الحركة
    // تتقري "تثاؤب" مش مجرد تكبير.
    var yawnStretch: Float = 0f

    if (animated) {
        val breathTransition = rememberInfiniteTransition(label = "orbBreath")
        breathScale = breathTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbBreathScale"
        ).value

        val blobTransition = rememberInfiniteTransition(label = "orbBlob")
        blobPhase = blobTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing)
            ),
            label = "orbBlobPhase"
        ).value

        var eyeOpen by remember { mutableFloatStateOf(1f) }
        val eyeOpenAnimated by animateFloatAsState(eyeOpen, tween(90), label = "orbBlink")
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2200, 5000))
                eyeOpen = 0.08f
                delay(110)
                eyeOpen = 1f
            }
        }
        LaunchedEffect(blinkTrigger) {
            if (blinkTrigger != 0L) {
                repeat(2) {
                    eyeOpen = 0.08f
                    delay(90)
                    eyeOpen = 1f
                    delay(90)
                }
            }
        }

        // ── النعاس ──────────────────────────────────────────────────────────────
        // لو محدش كلّمها ولا لمسها لفترة، بتنعس: العنين بتنّص، وبتتثاءب من وقت للتاني،
        // وبتتنفس أعمق وأبطأ. أي لمسة أو تغيير حالة بيصحّيها فوراً.
        //
        // التثاؤب من غير صوت عن قصد. صوت بيطلع من نفسه من موبايل في جيب حد من غير ما
        // يكون طلبه حاجة مزعجة مش لطيفة — الحركة لوحدها بتوصّل المعنى، والصوت محجوز
        // للحظة اللي العميل بيتعامل فيها فعلاً (اللمس، النجاح، التنبيه).
        //
        // الحالات النشطة مابتنعسش: واحدة بتفكّر أو بتنبّه مش المفروض تنام في نص شغلها.
        val canDoze = state == CompanionState.Idle || state == CompanionState.Happy
        var drowsy by remember { mutableStateOf(false) }
        var yawn by remember { mutableFloatStateOf(0f) }
        val yawnAmount by animateFloatAsState(yawn, tween(520, easing = FastOutSlowInEasing), label = "orbYawn")
        val lidTarget = if (drowsy) 0.45f else 1f
        val sleepyLid by animateFloatAsState(lidTarget, tween(900, easing = FastOutSlowInEasing), label = "orbSleepyLid")

        LaunchedEffect(blinkTrigger, glowTrigger, state) {
            drowsy = false
            yawn = 0f
            if (!canDoze) return@LaunchedEffect
            delay(DOZE_AFTER_MS)
            drowsy = true
            while (true) {
                delay(Random.nextLong(6000, 12000))
                yawn = 1f           // تتمطّ وتقفل عينيها
                delay(620)
                yawn = 0f           // وترجع تستقر أنعس شوية
                delay(520)
            }
        }
        drowsiness = if (drowsy) sleepyLid else 1f
        yawnStretch = yawnAmount
        eyeOpenAmount = eyeOpenAnimated
    } else {
        breathScale = 1f
        blobPhase = 0f
        eyeOpenAmount = 1f
    }

    var glowTarget by remember { mutableFloatStateOf(0f) }
    val glow by animateFloatAsState(glowTarget, tween(450, easing = FastOutSlowInEasing), label = "orbGlow")
    LaunchedEffect(glowTrigger) {
        if (glowTrigger != 0L) {
            glowTarget = 1f
            delay(120)
            glowTarget = 0f
        }
    }

    // الوصف الصوتي بس على النسخ البارزة (animated=true — رأس الشاشة/الشات). نسخ فقاعات
    // الشات (animated=false) عمداً من غير semantics عشان قارئ الشاشة ميكررش "زاد: ..." قبل كل
    // رسالة رسالة في محادثة طويلة — اسم "زاد" ونص الرسالة نفسه أصلاً بيتقروا.
    val orbModifier = if (animated) {
        val description = companionStateDescription(state)
        modifier.size(size).semantics { contentDescription = description }
    } else {
        modifier.size(size)
    }
    Canvas(modifier = orbModifier) {
        val radius = (this.size.minDimension / 2f) * breathScale
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // glow behind the body — a few widening, fading rings instead of a real blur
        drawCircle(color = skyColor.copy(alpha = 0.18f), radius = radius * 1.35f, center = center)
        drawCircle(color = skyColor.copy(alpha = 0.28f), radius = radius * 1.15f, center = center)

        // هالة اللمسة — بترسم فوق الهالة الساكنة وبتخبي لوحدها. صفر وقت السكون، فمفيش
        // أي رسم زيادة إلا في نص الثانية اللي بعد الضغطة.
        if (glow > 0.01f) {
            drawCircle(color = skyColor.copy(alpha = 0.30f * glow), radius = radius * (1.35f + 0.55f * glow), center = center)
            drawCircle(color = skyColor.copy(alpha = 0.22f * glow), radius = radius * (1.15f + 0.35f * glow), center = center)
        }

        // التمطّي: بتطول رأسياً وتضيق أفقياً في نفس اللحظة. لو كبّرناها في الاتجاهين كانت
        // هتتقري "بتكبر" مش "بتتثاءب" — الفرق كله في إن الحجم بيتحفظ والشكل هو اللي بيتغيّر.
        withTransform({
            if (yawnStretch > 0.001f) {
                scale(
                    scaleX = 1f - 0.06f * yawnStretch,
                    scaleY = 1f + 0.10f * yawnStretch,
                    pivot = center,
                )
            }
        }) {
            drawPath(
                path = blobPath(center, radius, blobPhase),
                brush = Brush.radialGradient(
                    colors = listOf(skyColor, deepColor),
                    center = center - Offset(radius * 0.3f, radius * 0.3f),
                    radius = radius * 1.6f
                )
            )

            // العين بتاخد أقل فتحة بين الرمشة والنعاس، والتثاؤب بيقفلها لآخرها — الواحدة
            // مابتفتحش عينيها وهي بتتثاءب.
            val lid = minOf(eyeOpenAmount, drowsiness) * (1f - 0.92f * yawnStretch)
            drawEyes(state, center, radius, lid.coerceIn(0f, 1f))
            drawBlushCheeks(state, center, radius)
            drawCuteMouth(state, center, radius, yawnStretch)
        }
    }
}

/**
 * خدود وردية لطيفة تظهر عند السعادة والاحتفال والمداعبة
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlushCheeks(
    state: CompanionState,
    center: Offset,
    radius: Float
) {
    if (state == CompanionState.Happy || state == CompanionState.Celebrating) {
        val blushSpacing = radius * 0.52f
        val blushY = center.y + radius * 0.18f
        val blushRadius = radius * 0.12f
        val blushColor = Color(0xFFFF69B4).copy(alpha = 0.45f) // Pink blush

        drawCircle(
            color = blushColor,
            radius = blushRadius,
            center = Offset(center.x - blushSpacing, blushY)
        )
        drawCircle(
            color = blushColor,
            radius = blushRadius,
            center = Offset(center.x + blushSpacing, blushY)
        )
    }
}

/**
 * ابتسامة قطة/أليف لطيفة مقوسة (Cute Cat Smile Arc)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCuteMouth(
    state: CompanionState,
    center: Offset,
    radius: Float,
    yawnStretch: Float
) {
    val mouthY = center.y + radius * 0.28f
    val mouthWidth = radius * 0.22f
    val mouthPath = Path()

    if (yawnStretch > 0.1f) {
        // فم مفتوح للتثاؤب
        val openYawn = radius * 0.18f * yawnStretch
        mouthPath.addOval(
            androidx.compose.ui.geometry.Rect(
                center.x - mouthWidth / 2f,
                mouthY - openYawn / 2f,
                center.x + mouthWidth / 2f,
                mouthY + openYawn / 2f
            )
        )
        drawPath(mouthPath, color = Color(0xFF4A148C).copy(alpha = 0.6f))
    } else if (state == CompanionState.Happy || state == CompanionState.Celebrating) {
        // ابتسامة قطة لطيفة على شكل :3 أو قوس ناعم
        val hw = mouthWidth / 2f
        mouthPath.moveTo(center.x - hw, mouthY)
        mouthPath.quadraticTo(center.x - hw / 2f, mouthY + radius * 0.08f, center.x, mouthY)
        mouthPath.quadraticTo(center.x + hw / 2f, mouthY + radius * 0.08f, center.x + hw, mouthY)

        drawPath(
            path = mouthPath,
            color = Color.White.copy(alpha = 0.9f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/**
 * بعد قد إيه من السكون تبدأ تنعس. ٢٥ ثانية: أطول من إن حد بيقرا الشاشة يخليها تنام في
 * وشه، وأقصر من إن حد سايب التليفون جنبه ما يلحقش يشوفها بتتثاءب.
 */
private const val DOZE_AFTER_MS = 25_000L

private const val BLOB_POINTS = 8
private const val BLOB_AMPLITUDE = 0.045f

/**
 * شكل الكورة كسائل عضوي بدل دايرة ثابتة: نقط حوالين المحيط، كل واحدة نصف قطرها بيتموّج
 * بمعدل وطور مختلف عن التانية (موجات جيبية غير متزامنة)، متوصلة بمنحنيات ناعمة (quadratic
 * لكل نقطة نص المسافة للنقطة الجاية) بدل خطوط مستقيمة — نفس أسلوب "blob shape" الشائع.
 */
private fun blobPath(center: Offset, baseRadius: Float, phase: Float): Path {
    val points = (0 until BLOB_POINTS).map { i ->
        val angle = (i.toFloat() / BLOB_POINTS) * 2 * Math.PI.toFloat()
        val freq = 1.5f + (i % 3) * 0.7f
        val wobble = 1f + BLOB_AMPLITUDE * sin(phase * freq + i * 1.1f)
        val r = baseRadius * wobble
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    state: CompanionState,
    center: Offset,
    radius: Float,
    openAmount: Float
) {
    val eyeSpacing = radius * 0.42f
    val eyeWidth = radius * 0.26f
    val leftCenter = center - Offset(eyeSpacing, 0f)
    val rightCenter = center + Offset(eyeSpacing, 0f)

    when (state) {
        CompanionState.Happy, CompanionState.Celebrating -> {
            val heartHeight = radius * 0.5f * openAmount
            drawHeart(leftCenter, eyeWidth, heartHeight)
            drawHeart(rightCenter, eyeWidth, heartHeight)
        }
        else -> {
            val baseHeight = when (state) {
                CompanionState.Focused -> radius * 0.32f
                CompanionState.Alert -> radius * 0.44f
                else -> radius * 0.42f
            }
            val eyeHeight = baseHeight * openAmount
            drawRoundRect(
                color = Color.White,
                topLeft = leftCenter - Offset(eyeWidth / 2f, eyeHeight / 2f),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight.coerceAtLeast(3f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeWidth / 2f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = rightCenter - Offset(eyeWidth / 2f, eyeHeight / 2f),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight.coerceAtLeast(3f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeWidth / 2f)
            )
        }
    }
}

private val alertToneWords = listOf("تنبيه", "تحذير", "خطر", "حذر", "تجاوزت", "نفاد", "أوشك", "قارب على النفاد")
private val happyToneWords = listOf("ممتاز", "أحسنت", "تهانينا", "مبروك", "رائع", "وفرت", "نجحت", "تحقيق هدف")

/** استنتاج حالة الأيجنت من نص رسالة الشات — مافيش استدعاء AI جديد، تصنيف كلمات مفتاحية محلي بس. */
fun companionStateForMessage(text: String): CompanionState = when {
    alertToneWords.any { text.contains(it) } -> CompanionState.Alert
    happyToneWords.any { text.contains(it) } -> CompanionState.Happy
    else -> CompanionState.Idle
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    at: Offset,
    width: Float,
    height: Float
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
    drawPath(path, color = Color.White)
}
