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
 * On-device AI music generator backed by a MusicGen-small LiteRT export.
 *
 * MusicGen is an autoregressive audio-language model: a text encoder maps the
 * prompt to embeddings, a transformer LM autoregressively predicts EnCodec
 * code tokens, and the audio decoder turns those tokens into a waveform.
 *
 * **Not selectable yet.** It follows the exact gate used by
 * [NSynthSoundGenerator]: it reports [isAvailable] = false until all three of
 * these are satisfied:
 *   - the LiteRT runtime probe reports the device as Ready, AND
 *   - the MusicGen bundle is installed with matching sizes/hashes, AND
 *   - a device ARM64 smoke-test has run once (via
 *     [LiteRtModelInstaller.markSmokeTested]).
 *
 * Until a real verified export with concrete tensor names/shapes is available
 * on the phone, that smoke-test has not happened and [generate] refuses rather
 * than emit silent audio.
 */
class MusicGenOnnxGenerator(
    private val appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "musicgen-small"
    override val displayName: String = "MusicGen (on-device music)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean =
        installer.isSmokeTestedInstalled(LiteRtModelRuntime.MUSIC_GEN_SMALL) &&
            runtime.isInstalled(LiteRtModelRuntime.MUSIC_GEN_SMALL)

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.MUSIC_GEN_SMALL,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("musicgen-small")),
        )

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        // The pipeline drives each generator. Until the ARM64 smoke-test lands
        // we must not claim to have produced audio; refuse the call so
        // AudioTagInserter skips this tag (matching NSynth behaviour).
        throw IllegalStateException(
            "MusicGen is still in development: LiteRT export not smoke-tested on a device yet",
        )
        @Suppress("UNREACHABLE_CODE")
        val output = File(
            request.outputFile.parentFile ?: appContext.filesDir,
            "musicgen-${UUID.randomUUID()}.wav",
        )
        output.parentFile?.mkdirs()
        // Real inference once tensors are verified:
        //  1. text_encoder.tflite: prompt -> (1, seq, 768) text embeddings.
        //  2. lm.tflite: autoregressive Sampling of EnCodec tokens (32 steps).
        //  3. audio_decoder.tflite: tokens -> (1, 1, 32000) PCM per 1sec.
        AudioEncoder.encodePcm16MonoWav(
            out = output,
            pcm = ShortArray(0),
            sampleRate = 32000,
        )
        GeneratorResult(
            outputFile = output,
            sampleRate = 32000,
            channels = 1,
            durationMs = 0,
            bytesWritten = output.length(),
        )
    }
}