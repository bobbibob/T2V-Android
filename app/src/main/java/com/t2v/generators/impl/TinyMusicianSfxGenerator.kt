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
 * TinyMusician в режиме SFX. Использует тот же ONNX-bundle, что и
 * [TinyMusicianMusicGenerator], но на выходе берёт только перкуссионные
 * каналы (channel 9) и ограничивает длительность 0.5-3 секундами.
 *
 * Подходит для UI-звуков, маркеров, переходов, dramatic hits в подкастах.
 *
 * **Status: scaffold with procedural fallback.**
 * До полного ONNX-экспорта TinyMusician — фоллбэк на [ProceduralAudioSynth.synthSound].
 */
class TinyMusicianSfxGenerator(
    private val appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {
    override val id: String = "litert.tinymusician.sound"
    override val displayName: String = "TinyMusician SFX (44M, MIT)"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    override fun isAvailable(): Boolean = true

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            val durationSec = request.durationSeconds.coerceIn(1, 3).let {
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
