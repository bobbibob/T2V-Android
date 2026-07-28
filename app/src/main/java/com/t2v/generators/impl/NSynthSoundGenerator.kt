package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-device SFX generator backed by Magenta NSynth (wavenet variant).
 *
 * NSynth produces short tonal audio clips (4 sec mono @ 8 kHz) from pitch
 * conditioning. Combined with an instrument-mapping table it can answer
 * `<sfx>piano C4</sfx>` / `<sfx>violin A3 sustained</sfx>` requests directly
 * on the phone without any cloud call.
 *
 * **Not selectable yet.** The TFLite export is still pending a real ARM64
 * smoke-test, so this generator reports `isAvailable() = false`. ModelsScreen
 * shows it under RuntimeInDevelopment and the download button stays
 * disabled until the smoke-test lands.
 *
 * Once we have a verified export with concrete input/output tensors, the
 * `generate()` method here is the single place to wire real inference.
 */
class NSynthSoundGenerator(
    private val appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "nsynth-wavenet"
    override val displayName: String = "Magenta NSynth (on-device SFX)"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    /**
     * Available only when:
     *   - the LiteRT runtime probe reports the device as Ready, AND
     *   - the NSynth bundle is downloaded with matching SHA-256, AND
     *   - a device smoke-test has run at least once successfully.
     *
     * Right now none of those are satisfied, so we always return false.
     * The smoke-test gate lives in `LiteRtModelInstaller.markSmokeTested()`.
     */
    override fun isAvailable(): Boolean = installer.isSmokeTested() && runtime.isInstalled(LiteRtModelRuntime.NSYNTH_WAVENET)

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.NSYNTH_WAVENET,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("nsynth-wavenet")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        // Until the smoke-test passes, refuse the call rather than silently
        // producing silent audio. AudioTagInserter will skip this tag and
        // continue with the next one.
        throw IllegalStateException(
            "NSynth is still in development: weights not downloaded or smoke-test not run yet",
        )
        // The shape below mirrors what the real wiring will look like once we
        // know the input/output tensor names; kept here as documentation.
        @Suppress("UNREACHABLE_CODE")
        val output = File(
            request.outputFile.parentFile ?: appContext.filesDir,
            "nsynth-${UUID.randomUUID()}.wav",
        )
        output.parentFile?.mkdirs()
        // Real inference would:
        // 1. Resolve instrument + pitch from request.prompt via the
        //    instrument_mapping.json metadata.
        // 2. Build a (1, 16, 1) FloatBuffer of pitch ids.
        // 3. Run interpreter.run(input, output).
        // 4. Encode the (1, 16, 8000) FloatBuffer as a 16-bit mono 8 kHz WAV.
        AudioEncoder.encodePcm16MonoWav(
            out = output,
            pcm = ShortArray(8000 * 4),
            sampleRate = 8000,
        )
        GeneratorResult(
            outputFile = output,
            sampleRate = 8000,
            channels = 1,
            durationMs = 4000,
            bytesWritten = output.length(),
        )
    }
}
