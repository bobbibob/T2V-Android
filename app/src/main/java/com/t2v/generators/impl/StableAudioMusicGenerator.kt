package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.generators.synth.ProceduralAudioSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device music generator.
 *
 * Uses [ProceduralAudioSynth] to synthesise music from a free-text prompt
 * in real time — no model download, no TFLite inference, runs in milliseconds.
 * The synthesiser parses mood keywords (ambient, calm, cinematic, uplifting,
 * dark, tension, dream) and generates a chord progression with oscillators,
 * a delay-line reverb and a low-pass filter for warmth.
 *
 * Output: mono 16-bit WAV at 22050 Hz, up to 11 seconds.
 */
class StableAudioMusicGenerator(
    appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "litert.stable-audio-open-small.music"
    override val displayName: String = "On-device synth (music)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    /** Always available — procedural synthesis needs no downloaded model. */
    override fun isAvailable(): Boolean = true

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("stable-audio-open-small")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        val durationSec = request.durationSeconds.coerceIn(1, 11).let {
            if (it == 0) 5 else it
        }
        val sampleRate = ProceduralAudioSynth.SAMPLE_RATE
        val pcm = ProceduralAudioSynth.synthMusic(request.prompt, durationSec)
        request.outputFile.parentFile?.mkdirs()
        AudioEncoder.encodePcm16MonoWav(request.outputFile, pcm, sampleRate)
        val gainFactor = if (request.gainDb != 0.0) {
            (Math.pow(10.0, request.gainDb / 20.0))
        } else 1.0
        if (gainFactor != 1.0) {
            // Re-read, apply gain, re-write
            val (_, chunk) = AudioEncoder.readWav(request.outputFile)
            val scaled = ShortArray(chunk.samples.size) { i ->
                (chunk.samples[i] * gainFactor)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            AudioEncoder.encodePcm16MonoWav(request.outputFile, scaled, sampleRate)
        }
        GeneratorResult(
            outputFile = request.outputFile,
            sampleRate = sampleRate,
            channels = 1,
            durationMs = durationSec * 1000,
            bytesWritten = request.outputFile.length(),
        )
    }
}
