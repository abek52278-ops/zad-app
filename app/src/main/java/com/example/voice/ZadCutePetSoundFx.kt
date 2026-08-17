package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 🐱 محاكي ومولّد أصوات الحيوان الأليف والرفيق اللطيف (Zad Cute Pet Sound FX)
 * يولّد أصواتاً طبيعية نقية ذات طابع مرح (خرخرة، مواء لطيف، نغمات سعادة، زغردة احتفال)
 * بدون الحاجة لملفات صوت خارجية، مع معالجة رقمية للإشارات (DSP) ذات حواف ناعمة.
 */
object ZadCutePetSoundFx {

    private const val TAG = "ZadCutePetSoundFx"
    private const val SAMPLE_RATE = 44100

    enum class PetSound {
        /** خرخرة دافئة وناعمة عند المداعبة والتمرير (Purr) */
        Purr,
        /** مواء/تغريد لطيف ومرح عند الضغط (Meow / Chirp) */
        MeowChirp,
        /** نغمة بهجة واستجابة سريعة (Happy Chirp) */
        HappyChirp,
        /** زغردة ونغمة احتفال متصاعدة عند تسجيل إنجاز أو توفير (Celebration Trill) */
        CelebrationTrill,
        /** نقرة خفيفة مرحة (Cute Squeak) */
        CuteSqueak,
        /** تنفس هادئ واستكانة (Sleepy Sigh) */
        SleepySigh
    }

    fun play(sound: PetSound, volume: Float = 0.45f) {
        Thread {
            var track: AudioTrack? = null
            try {
                val samples = generateSamples(sound, volume.coerceIn(0f, 1f))
                val bytes = samples.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(
                        maxOf(
                            bytes,
                            AudioTrack.getMinBufferSize(
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                        )
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                Thread.sleep(durationMs + 80)
            } catch (e: Exception) {
                Log.w(TAG, "play(${sound.name}) failed: ${e.message}")
            } finally {
                try {
                    track?.release()
                } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
    }

    private fun generateSamples(sound: PetSound, volume: Float): ShortArray {
        return when (sound) {
            PetSound.Purr -> generatePurr(volume)
            PetSound.MeowChirp -> generateMeowChirp(volume)
            PetSound.HappyChirp -> generateHappyChirp(volume)
            PetSound.CelebrationTrill -> generateCelebrationTrill(volume)
            PetSound.CuteSqueak -> generateCuteSqueak(volume)
            PetSound.SleepySigh -> generateSleepySigh(volume)
        }
    }

    /** توليد خرخرة قطة ناعمة ودافئة مع اهتزازات ترددية منخفضة (Purr) */
    private fun generatePurr(volume: Float): ShortArray {
        val durationMs = 420
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)
        val baseFreq = 78.0f // تردد الخرخرة الأساسي
        val flutterRate = 24.0f // سرعة رفرفة الحنجرة

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            // Envelope: ناعم في البداية والنهاية
            val env = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
            // Modulation: تذبذب دوري
            val mod = (0.5f + 0.5f * sin(2 * PI.toFloat() * flutterRate * t))
            val wave = sin(2 * PI.toFloat() * baseFreq * t) + 0.35f * sin(2 * PI.toFloat() * (baseFreq * 2) * t)
            val sampleVal = (wave * mod * env * volume * Short.MAX_VALUE * 0.7f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** مواء/تغريد لطيف ومرح بقوس ترددي طبيعي (Meow-Chirp) */
    private fun generateMeowChirp(volume: Float): ShortArray {
        val durationMs = 260
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            // Envelope
            val env = if (progress < 0.2f) {
                progress / 0.2f
            } else {
                exp(-4.2f * (progress - 0.2f))
            }
            // Frequency sweep: يبدأ من 580Hz ويصعد إلى 920Hz ثم ينحني قليلاً
            val freq = 580f + 340f * sin(progress * PI.toFloat())
            val wave = sin(2 * PI.toFloat() * freq * t) + 0.25f * sin(2 * PI.toFloat() * (freq * 2.02f) * t)
            val sampleVal = (wave * env * volume * Short.MAX_VALUE * 0.75f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** نغمة بهجة واستجابة سريعة (Happy Chirp) */
    private fun generateHappyChirp(volume: Float): ShortArray {
        val durationMs = 180
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            val env = sin(progress * PI.toFloat())
            val freq = 880f + 400f * progress // A5 to high E6
            val wave = sin(2 * PI.toFloat() * freq * t) + 0.2f * sin(2 * PI.toFloat() * freq * 1.5f * t)
            val sampleVal = (wave * env * volume * Short.MAX_VALUE * 0.7f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** زغردة احتفال سريعة (Celebration Trill) */
    private fun generateCelebrationTrill(volume: Float): ShortArray {
        val durationMs = 380
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)

        val notes = floatArrayOf(880f, 1174.66f, 1318.51f, 1760f) // A5, D6, E6, A6

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            val noteIndex = ((progress * notes.size).toInt()).coerceIn(0, notes.size - 1)
            val noteFreq = notes[noteIndex]
            val noteProgress = (progress * notes.size) - noteIndex
            val env = sin(noteProgress * PI.toFloat()).coerceIn(0f, 1f) * (1f - progress * 0.2f)

            val wave = sin(2 * PI.toFloat() * noteFreq * t) + 0.15f * sin(2 * PI.toFloat() * noteFreq * 2f * t)
            val sampleVal = (wave * env * volume * Short.MAX_VALUE * 0.8f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** نقرة خفيفة مرحة (Cute Squeak) */
    private fun generateCuteSqueak(volume: Float): ShortArray {
        val durationMs = 120
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            val env = (1f - progress) * (1f - progress)
            val freq = 1200f + 600f * (1f - progress)
            val wave = sin(2 * PI.toFloat() * freq * t)
            val sampleVal = (wave * env * volume * Short.MAX_VALUE * 0.65f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** تثاؤب هادئ واستكانة (Sleepy Sigh) */
    private fun generateSleepySigh(volume: Float): ShortArray {
        val durationMs = 500
        val total = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(total)

        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / total
            val env = sin(progress * PI.toFloat())
            // هبوط التردد بسلاسة
            val freq = 440f - 180f * progress
            val wave = sin(2 * PI.toFloat() * freq * t) + 0.1f * sin(2 * PI.toFloat() * freq * 3f * t)
            val sampleVal = (wave * env * volume * Short.MAX_VALUE * 0.5f).toInt()
            out[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
