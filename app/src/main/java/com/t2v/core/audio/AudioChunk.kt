package com.t2v.core.audio

/**
 * Минимальное представление PCM-сэмплов.
 * 16-битный little-endian моно/стерео PCM с заданной частотой дискретизации.
 *
 * Соответствует структуре WAV-чанков, читаемых через AudioExtractor.kt.
 */
data class AudioChunk(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
) {
    val durationSec: Double
        get() = samples.size.toDouble() / (sampleRate * channels)

    fun isEmpty(): Boolean = samples.isEmpty()
}

/** WAV-параметры, читаемые из RIFF-заголовка. */
data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val dataSize: Int,
)
