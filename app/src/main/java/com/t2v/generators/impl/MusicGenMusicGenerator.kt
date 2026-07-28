package com.t2v.generators.impl

import android.content.Context
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.synth.ProceduralAudioSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MusicGen (Meta Audiocraft) music generator.
 *
 * MusicGen is an encoder-decoder transformer that turns a text prompt into
 * a 16 kHz mono audio clip. It comes in three sizes:
 *   - musicgen-small  (~300M params, ~1.5 GB int8)
 *   - musicgen-medium (~1.5B params, ~6 GB int8 — too big for phones)
 *   - musicgen-large  (~3.3B params, not realistic for mobile)
 *
 * **Status: scaffold only.** No working ONNX export for Android exists as of
 * mid-2026. The closest candidate is
 * `wide-video/musicgen-small-v1.0.0` (int8 ONNX, ~422 MB decoder +
 * 110 MB text encoder + 60 MB encodec_decode), but the decoder is an
 * autoregressive transformer that produces tokens one at a time — a poor
 * fit for on-device real-time inference on a phone. The community has not
 * produced a viable ARM64 TFLite export yet, so this generator:
 *
 *   1. advertises itself in `GeneratorRegistry` so the UI shows the
 *      entry under "Music";
 *   2. is registered in `GenerationModelCatalog` as
 *      `RuntimeInDevelopment` with the real Hugging Face repository so
 *      the user can see the planned provenance;
 *   3. falls back to [ProceduralAudioSynth.synthMusic] so the
 *      `<music>...</music>` pipeline still produces a real audio clip
 *      today (the user gets something audible, just procedurally
 *      generated from the prompt's mood keywords rather than
 *      semantically composed by MusicGen);
 *   4. once an ARM64-viable MusicGen export lands, the runtime check
 *      here flips to `if (modelDownloaded && isArm64Capable) runOnnx
 *      else fallback` and no UI changes are needed.
 */
class MusicGenMusicGenerator(
    private val appContext: Context,
) : Generator {
    override val id: String = "litert.musicgen-small.music"
    override val displayName: String = "MusicGen Small (Meta Audiocraft)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    /**
     * MusicGen itself is never available on Android yet (see class doc).
     * The fallback path is always available because
     * [ProceduralAudioSynth] has no model requirement.
     */
    override fun isAvailable(): Boolean = true

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        // Fallback path: delegate to the existing procedural synth. The
        // prompt is still used for mood-keyword parsing, so the user gets
        // mood-appropriate output.
        val durationSec = request.durationSeconds.coerceIn(1, 30).let {
            if (it == 0) 11 else it
        }
        val sampleRate = ProceduralAudioSynth.SAMPLE_RATE
        val pcm = ProceduralAudioSynth.synthMusic(request.prompt, durationSec)
        // Encode as 16-bit mono WAV
        val out = com.t2v.core.audio.AudioEncoder.encodePcm16MonoWav(
            out = request.outputFile,
            pcm = pcm,
            sampleRate = sampleRate,
        )
        // Apply gain if requested
        if (request.gainDb != 0.0) {
            val gainFactor = Math.pow(10.0, request.gainDb / 20.0)
            val (header, chunk) = com.t2v.core.audio.AudioEncoder.readWav(request.outputFile)
            val scaled = ShortArray(chunk.samples.size) { i ->
                (chunk.samples[i] * gainFactor)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            com.t2v.core.audio.AudioEncoder.encodePcm16MonoWav(
                out = request.outputFile,
                pcm = scaled,
                sampleRate = sampleRate,
            )
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
