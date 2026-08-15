package com.example.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * زاد بيصدر نغمة، مش بيب.
 *
 * الصوت قبل كده كان `ToneGenerator(STREAM_NOTIFICATION).startTone(TONE_PROP_BEEP)` — ودي
 * نغمة DTMF، يعني حرفياً صوت أزرار التليفون: موجة مربعة بحافة حادة وقطع مفاجئ. مفيش
 * إعداد فيها بيخليها ألطف، لأن الخشونة دي هي شكل الموجة نفسها. الشكوى إن "الصوت عايزه
 * بشري أكتر" شكوى عن ده بالظبط.
 *
 * اللي هنا بيولّد الموجة بنفسه: نغمتين جيبيتين بينهم فاصل موسيقي، مع ظرف صوتي (attack
 * ناعم + decay أسّي) وتوافقية خفيفة تدّي الصوت جرس بدل ما يبقى صافي وميكانيكي. الحواف
 * الناعمة هي الفرق كله — القطع المفاجئ هو اللي الودن بتسمعه "إلكتروني".
 *
 * التوليف بدل ملف صوت في res/raw عن قصد: مفيش أصول صوتية في المشروع، وملف WAV مرمي
 * كـ binary مش حاجة تتراجع في code review، بينما الأرقام تحت مقروءة وقابلة للتعديل.
 * لو اتجابت أصول احترافية بعدين، الواجهة دي تفضل زي ما هي والتنفيذ بس هو اللي يتغيّر.
 */
object ZadChime {

    private const val TAG = "ZadChime"
    private const val SAMPLE_RATE = 44100

    /** نغمة الحالة — كل واحدة فاصل موسيقي مختلف، عشان الودن تفرّق من غير ما تبص للشاشة. */
    enum class Tone(val hz1: Float, val hz2: Float, val durationMs: Int) {
        /** خامسة صاعدة (C6→G6) — "تمام، سمعتك". */
        Tap(1046.5f, 1568.0f, 200),
        /** ثالثة كبيرة صاعدة، أطول شوية — إنجاز. */
        Success(1318.5f, 1661.2f, 340),
        /** ثانية هابطة، أخفض — لفت انتباه من غير إنذار. */
        Alert(659.3f, 587.3f, 300),
    }

    /**
     * بيتنفذ على thread لوحده وبيسيب الموارد بعد ما يخلص. أي فشل بيتبلع: صوت مش شغال
     * إزعاج بسيط، أما استثناء من نداء داخل onClick فبيوقّع الواجهة.
     */
    fun play(tone: Tone, volume: Float = 0.35f) {
        Thread {
            var track: AudioTrack? = null
            try {
                val samples = render(tone, volume.coerceIn(0f, 1f))
                val bytes = samples.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            // SONIFICATION مش NOTIFICATION: ده رد فعل على لمسة المستخدم،
                            // مش إشعار. النظام بيخفض/يكتم الاتنين بقواعد مختلفة، والمستخدم
                            // اللي كاتم الإشعارات مش بالضرورة عايز يكتم رجع صدى ضغطته.
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(bytes, AudioTrack.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    )))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                // MODE_STATIC بيشغّل من بافر متكتوب مرة واحدة، فمفيش callback بيقول "خلص" —
                // النوم بطول النغمة + هامش هو أبسط حاجة تضمن إن release مابيقطعش الصوت.
                Thread.sleep((tone.durationMs + 120).toLong())
            } catch (e: Exception) {
                Log.w(TAG, "play(${tone.name}) failed: ${e.message}")
            } finally {
                try { track?.release() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * النغمتين بيتداخلوا: التانية بتدخل بعد ٣٥٪ من الطول والأولى لسه بتخبي، فالأذن
     * تسمعها نغمة واحدة بتتفتح مش نغمتين ورا بعض.
     */
    internal fun render(tone: Tone, volume: Float): ShortArray {
        val total = SAMPLE_RATE * tone.durationMs / 1000
        val out = ShortArray(total)
        val secondStart = (total * 0.35f).toInt()

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            var v = voice(tone.hz1, t, i.toFloat() / total)
            if (i >= secondStart) {
                val t2 = (i - secondStart).toFloat() / SAMPLE_RATE
                v += voice(tone.hz2, t2, (i - secondStart).toFloat() / (total - secondStart)) * 0.9f
            }
            out[i] = (v * volume * Short.MAX_VALUE / 2f)
                .coerceIn(-Short.MAX_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                .toInt().toShort()
        }
        return out
    }

    /** جيبية + توافقيتها التانية بربع القوة، مضروبة في الظرف الصوتي. */
    private fun voice(hz: Float, tSeconds: Float, progress: Float): Float {
        val w = 2f * PI.toFloat() * hz
        val fundamental = sin(w * tSeconds)
        val harmonic = sin(2f * w * tSeconds) * 0.25f
        return (fundamental + harmonic) * envelope(progress)
    }

    /**
     * attack قصير (٨٪) بيمنع الطقة اللي بتحصل لو الموجة بدأت من الصفر فجأة، وبعده انحدار
     * أسّي زي ما أي جسم بيرن بيخفت. ده اللي بيفرّق الجرس عن البيب.
     */
    private fun envelope(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val attack = 0.08f
        return if (p < attack) p / attack else exp(-3.2f * (p - attack) / (1f - attack))
    }
}
