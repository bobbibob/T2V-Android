package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.core.midi.MidiRenderer
import com.t2v.core.midi.TinyMusicianMidiDecoder
import com.t2v.core.midi.sf2.SoundFontRenderer
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
 * TinyMusician (asigalov61) — компактный Music Transformer, генерирует
 * MIDI прямо на телефоне, а потом MIDI рендерится в WAV.
 *
 * **Status: scaffold with procedural + SoundFont fallback.**
 *
 * Полный путь до реального inference:
 *  1. Скачать TinyMusician-44M (или 100M) ONNX int8 (~180/420 МБ) с HF
 *  2. Скачать GeneralUser GS SoundFont (~30 МБ) с archive.org
 *  3. Загрузить ONNX через onnxruntime-mobile AAR
 *  4. Прогнать prompt → MIDI tokens → [MidiSequence]
 *  5. Рендерить [MidiSequence] → WAV через [MidiRenderer] (sine) или
 *     [com.t2v.core.midi.sf2.SoundFontRenderer] (когда есть SoundFont)
 *
 * Сейчас (без ONNX) мы генерируем [MidiSequence] из
 * [TinyMusicianMidiDecoder.fallbackFromPrompt] (mood-keyword chord
 * progression), затем рендерим через:
 *  - [SoundFontRenderer], если GeneralUser GS SoundFont скачан
 *  - [MidiRenderer.renderSine], иначе
 *
 * Лицензия: MIT — коммерчески свободная.
 *
 * Литература:
 *   - arxiv:2502.12945 (февраль 2025)
 *   - https://huggingface.co/asigalov61/TinyMusician
 */
class TinyMusicianMusicGenerator(
    private val appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
    private val soundFontInstaller: SoundFontInstaller = SoundFontInstaller(appContext),
) : Generator {
    override val id: String = "litert.tinymusician-small.music"
    override val displayName: String = "TinyMusician Small (44M, MIT)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = true

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            val sampleRate = 22050
            // TODO real TinyMusician inference:
            //   1. tokenizer.encode(prompt) -> tokenIds
            //   2. onnxSession.run(tokenIds) -> midiTokens
            //   3. sequence = TinyMusicianMidiDecoder.decode(midiTokens)
            // Until then, build a chord progression from mood keywords.
            val sequence = TinyMusicianMidiDecoder.fallbackFromPrompt(
                prompt = request.prompt,
                tempoBpm = 100,
            )
            // Try SoundFont first (much better quality), fall back to sine synth.
            val soundFont = soundFontInstaller.loadInstalled("generaluser-gs-soundfont")
            val pcm = if (soundFont != null) {
                SoundFontRenderer(soundFont, sampleRate).render(sequence)
            } else {
                MidiRenderer.renderSine(sequence, sampleRate = sampleRate)
            }
            request.outputFile.parentFile?.mkdirs()
            AudioEncoder.encodePcm16MonoWav(request.outputFile, pcm, sampleRate)
            GeneratorResult(
                outputFile = request.outputFile,
                sampleRate = sampleRate,
                channels = 1,
                durationMs = sequence.durationMs,
                bytesWritten = request.outputFile.length(),
            )
        }
}
