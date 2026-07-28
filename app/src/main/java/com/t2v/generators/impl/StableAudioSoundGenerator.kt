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
 * On-device sound-effect generator.
 *
 * Uses [ProceduralAudioSynth] to synthesise SFX from a free-text prompt
 * in real time — no model download, no TFLite inference, runs in milliseconds.
 * The synthesiser recognises: door, whoosh, notification, rain, wind,
 * explosion, click, footstep, heartbeat and falls back to a prompt-hashed
 * generic tone for anything else.
 *
 * Output: mono 16-bit WAV at 22050 Hz, up to 5 seconds.
 */
class StableAudioSoundGenerator(
    appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "litert.stable-audio-clip.sound"
    override val displayName: String = "On-device synth (sound)"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    /** Always available — procedural synthesis needs no downloaded model. */
    override fun isAvailable(): Boolean = true

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.STABLE_AUDIO_CLIP,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("stable-audio-open-small")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        val durationSec = request.durationSeconds.coerceIn(1, 5).let {
            if (it == 0) 2 else it
        }
        val sampleRate = ProceduralAudioSynth.SAMPLE_RATE
        val pcm = ProceduralAudioSynth.synthSound(request.prompt, durationSec)
        request.outputFile.parentFile?.mkdirs()
        AudioEncoder.encodePcm16MonoWav(request.outputFile, pcm, sampleRate)
        GeneratorResult(
            outputFile = request.outputFile,
            sampleRate = sampleRate,
            channels = 1,
            durationMs = durationSec * 1000,
            bytesWritten = request.outputFile.length(),
        )
    }
}
