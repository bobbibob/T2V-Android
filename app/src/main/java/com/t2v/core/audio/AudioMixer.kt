package com.t2v.core.audio

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Микшер аудио. Прямой порт подмножества `app/core/audio_mix.py` (768 строк),
 * достаточного для подкаст-сценария: голос + фоновая музыка + ducking + fade.
 *
 * Использует ffmpeg-kit для финального рендеринга (MP3), но само
 * сведение PCM-данных делает на CPU — это и есть наш "движок микса".
 */
data class AudioMixSettings(
    val voicePath: String,
    val musicPath: String? = null,
    val voiceVolumeDb: Double = 0.0,
    val musicVolumeDb: Double = -12.0,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val musicFadeInMs: Int = 1500,
    val musicFadeOutMs: Int = 2500,
    val duckingDb: Double = -6.0,           // насколько приглушать музыку, когда говорит голос
    val normalize: Boolean = true,
    val targetLufs: Double = -16.0,
    val outputFormat: OutputFormat = OutputFormat.MP3,
    val outputBitrate: String = "192k",
) {
    enum class OutputFormat { MP3, WAV, OGG, M4A }
}

/**
 * Один проход сведения.
 *
 * Возвращает PCM-mix в формате AudioChunk; финальное кодирование в MP3
 * выполняет ffmpeg-kit ([com.t2v.core.audio.FFmpegBridge]).
 */
class AudioMixer {
    fun mix(
        voice: AudioChunk,
        music: AudioChunk?,
        settings: AudioMixSettings,
    ): AudioChunk {
        val voiceGain = dbToGain(settings.voiceVolumeDb)
        val v = applyGain(voice, voiceGain)

        if (music == null) {
            return if (settings.normalize) normalize(v, settings.targetLufs) else v
        }

        val musicGain = dbToGain(settings.musicVolumeDb)
        val m = applyGain(music, musicGain)

        val mixed = mixWithDucking(
            voice = v,
            music = m,
            voiceGain = 1.0,
            musicGain = 1.0,
            duckingDb = settings.duckingDb,
        )

        // Fade in/out голоса
        val withVoiceFades = applyFades(
            mixed,
            fadeInMs = settings.fadeInMs,
            fadeOutMs = settings.fadeOutMs,
        )

        return if (settings.normalize) normalize(withVoiceFades, settings.targetLufs)
        else withVoiceFades
    }

    // --- DSP helpers --------------------------------------------------------

    internal fun dbToGain(db: Double): Double = 10.0.pow(db / 20.0)

    internal fun applyGain(chunk: AudioChunk, gain: Double): AudioChunk {
        if (gain == 1.0) return chunk
        val out = ShortArray(chunk.samples.size)
        for (i in chunk.samples.indices) {
            val v = (chunk.samples[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = v.toShort()
        }
        return AudioChunk(out, chunk.sampleRate, chunk.channels)
    }

    /**
     * Наложить музыку на голос. Голос имеет приоритет:
     * если амплитуда голоса выше порога, музыка приглушается на [duckingDb].
     */
    internal fun mixWithDucking(
        voice: AudioChunk,
        music: AudioChunk,
        voiceGain: Double,
        musicGain: Double,
        duckingDb: Double,
    ): AudioChunk {
        require(voice.sampleRate == music.sampleRate) {
            "Sample rates must match: voice=${voice.sampleRate} music=${music.sampleRate}"
        }
        require(voice.channels == music.channels) { "Channels must match" }
        val len = max(voice.samples.size, music.samples.size)
        val out = ShortArray(len)
        val windowMs = 100
        val windowSamples = (voice.sampleRate * voice.channels * windowMs) / 1000
        val duckingThreshold = 0.05
        val musicDuckGain = dbToGain(duckingDb)

        var i = 0
        while (i < len) {
            val end = min(i + windowSamples, len)
            var voiceRms = 0.0
            var n = 0
            for (j in i until min(end, voice.samples.size)) {
                val s = voice.samples[j].toDouble()
                voiceRms += s * s
                n++
            }
            voiceRms = if (n > 0) Math.sqrt(voiceRms / n) / Short.MAX_VALUE else 0.0

            val localMusicGain = if (voiceRms > duckingThreshold) musicDuckGain else 1.0

            for (j in i until end) {
                val v = if (j < voice.samples.size) (voice.samples[j] * voiceGain).toInt() else 0
                val m = if (j < music.samples.size) {
                    (music.samples[j] * musicGain * localMusicGain).toInt()
                } else 0
                val sum = v + m
                out[j] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            i = end
        }
        return AudioChunk(out, voice.sampleRate, voice.channels)
    }

    /** Линейный fade-in / fade-out. */
    internal fun applyFades(chunk: AudioChunk, fadeInMs: Int, fadeOutMs: Int): AudioChunk {
        if (fadeInMs <= 0 && fadeOutMs <= 0) return chunk
        val out = chunk.samples.copyOf()
        val sampleRate = chunk.sampleRate
        val channels = chunk.channels

        if (fadeInMs > 0) {
            val n = ((sampleRate * channels * fadeInMs) / 1000).coerceAtMost(out.size)
            for (i in 0 until n) {
                val g = i.toDouble() / n
                out[i] = (out[i] * g).toInt().toShort()
            }
        }
        if (fadeOutMs > 0) {
            val n = ((sampleRate * channels * fadeOutMs) / 1000).coerceAtMost(out.size)
            val start = out.size - n
            for (i in 0 until n) {
                val g = (n - i).toDouble() / n
                out[start + i] = (out[start + i] * g).toInt().toShort()
            }
        }
        return AudioChunk(out, sampleRate, channels)
    }

    /**
     * Простой нормализатор по пиковой амплитуде + опциональный target LUFS-like RMS.
     * Это НЕ полный EBU R128, но даёт стабильный результат для подкастов.
     */
    internal fun normalize(chunk: AudioChunk, targetLufs: Double): AudioChunk {
        if (chunk.samples.isEmpty()) return chunk
        var peak = 1
        var sumSq = 0.0
        for (s in chunk.samples) {
            val abs = if (s < 0) -s.toInt() else s.toInt()
            if (abs > peak) peak = abs
            val d = s.toDouble()
            sumSq += d * d
        }
        val rms = Math.sqrt(sumSq / chunk.samples.size)
        val targetRms = 10.0.pow((targetLufs + 3.0) / 20.0) * Short.MAX_VALUE
        val rmsGain = if (rms > 0) targetRms / rms else 1.0
        val peakGain = Short.MAX_VALUE.toDouble() / peak
        val gain = min(rmsGain, peakGain).coerceAtMost(4.0)

        val out = ShortArray(chunk.samples.size)
        for (i in chunk.samples.indices) {
            out[i] = (chunk.samples[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return AudioChunk(out, chunk.sampleRate, chunk.channels)
    }
}
