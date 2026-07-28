package com.t2v.core.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Генератор min/max envelope для отрисовки waveform.
 *
 * Прямой порт `app/core/waveform_preview.py:generate_waveform_preview`.
 * Возвращает пары (time, min, max) для каждой "точки" envelope.
 */
data class WaveformEnvelope(
    val times: List<Float>,
    val minimums: List<Float>,
    val maximums: List<Float>,
    val durationSec: Float,
    val sourceDurationSec: Float = 0f,
) {
    val isEmpty: Boolean get() = times.isEmpty()
    val size: Int get() = times.size
}

/**
 * Параметры построения envelope.
 */
data class WaveformOptions(
    val targetSampleRate: Int = 8000,
    val maxPoints: Int = 24000,
    val maxDurationSec: Float? = null,
)

/**
 * Строит envelope из [chunk] PCM.
 * Простой downsample: на каждый пиксель берём окно и считаем min/max.
 */
fun buildWaveform(chunk: AudioChunk, options: WaveformOptions = WaveformOptions()): WaveformEnvelope {
    if (chunk.samples.isEmpty()) {
        return WaveformEnvelope(emptyList(), emptyList(), emptyList(), 0f, 0f)
    }
    val duration = chunk.durationSec.toFloat()
    if (options.maxDurationSec != null && duration > options.maxDurationSec) {
        // Ограничиваем сверху
    }
    val points = min(options.maxPoints, max(1, (duration * 60).toInt())) // 60 точек/сек по умолчанию
    val samplesPerPoint = max(1, chunk.samples.size / points)
    val times = ArrayList<Float>(points)
    val mins = ArrayList<Float>(points)
    val maxs = ArrayList<Float>(points)
    for (p in 0 until points) {
        val start = p * samplesPerPoint
        val end = min(start + samplesPerPoint, chunk.samples.size)
        var lo = Short.MAX_VALUE.toInt()
        var hi = Short.MIN_VALUE.toInt()
        for (i in start until end) {
            val v = chunk.samples[i].toInt()
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        if (start >= end) {
            lo = 0; hi = 0
        }
        times += (start.toFloat() / chunk.sampleRate / chunk.channels)
        mins += (lo.toFloat() / Short.MAX_VALUE)
        maxs += (hi.toFloat() / Short.MAX_VALUE)
    }
    return WaveformEnvelope(times, mins, maxs, duration, duration)
}

/**
 * Считывает WAV-файл и сразу строит envelope.
 */
fun buildWaveformFromFile(file: java.io.File, options: WaveformOptions = WaveformOptions()): WaveformEnvelope {
    val (_, chunk) = AudioEncoder.readWav(file)
    return buildWaveform(chunk, options)
}
