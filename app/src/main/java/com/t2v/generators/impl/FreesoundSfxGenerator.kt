package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.synth.ProceduralAudioSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Freesound SFX — локальный поиск по CC0-выборке Freesound.org.
 *
 * На 2026-07-28 — **scaffold with procedural fallback**. Полный путь:
 *  1. Скачать CC0-выборку (~150 МБ, 30 000 звуков) с
 *     https://freesound.org/ через Freesound API + filtering.
 *  2. Индексировать по тегам (используя tag-extractor).
 *  3. На <sfx> запрос: tag-match → top-1 → return.
 *  4. Если индекс пуст — fallback на [ProceduralAudioSynth.synthSound].
 *
 * Bundle живёт в `files/models/freesound/cc0-index.json` + `files/sounds/...`.
 */
class FreesoundSfxGenerator(
    private val appContext: Context,
) : Generator {
    override val id: String = "freesound.sound"
    override val displayName: String = "Freesound CC0 SFX (offline)"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    /**
     * Always available — procedural fallback works without the index.
     * Once the CC0 index is downloaded + a real device smoke-test passes,
     * we add the additional `isInstalled(...) && smokeTested` gates here.
     */
    override fun isAvailable(): Boolean = true

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            // TODO: load files/models/freesound/cc0-index.json,
            //       match prompt tokens against tags, return the top hit.
            //       For now: procedural fallback (still produces a real SFX clip).
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
