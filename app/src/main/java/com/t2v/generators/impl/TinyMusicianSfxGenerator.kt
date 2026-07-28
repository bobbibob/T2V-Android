package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.core.midi.MidiConstants
import com.t2v.core.midi.MidiEvent
import com.t2v.core.midi.MidiRenderer
import com.t2v.core.midi.MidiSequence
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
 * TinyMusician в режиме SFX. Генерирует MIDI-фразы на канале 9 (drums)
 * и рендерит их через [MidiRenderer] → [com.t2v.core.midi.synth.DrumKit].
 *
 * Подходит для UI-звуков, маркеров, переходов, dramatic hits.
 *
 * **Status: scaffold with procedural fallback** (DrumKit работает без SoundFont;
 * если SoundFont скачан — будет лучше, но DrumKit уже различим).
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
            val sampleRate = 22050
            val sequence = buildDrumPhrase(request.prompt, durationSec)
            val pcm = MidiRenderer.renderSine(sequence, sampleRate = sampleRate)
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

    /**
     * Build a small drum phrase from a free-text prompt. The TinyMusician
     * inference would produce real MIDI tokens; until then we use a
     * keyword-driven pattern recogniser.
     */
    private fun buildDrumPhrase(prompt: String, durationSec: Int): MidiSequence {
        val lower = prompt.lowercase()
        val out = mutableListOf<MidiEvent>()
        val beatMs = 250  // 120 BPM
        val totalBeats = (durationSec * 1000) / beatMs

        // Default: a basic rock pattern
        var pattern = intArrayOf(36, 42, 38, 42)  // kick, hihat closed, snare, hihat closed
        if ("timpani" in lower || "dramatic" in lower) {
            pattern = intArrayOf(36)  // single big hit
        } else if ("kick" in lower) {
            pattern = intArrayOf(36, 36, 36, 36)
        } else if ("snare" in lower) {
            pattern = intArrayOf(38, 38, 38, 38)
        } else if ("hihat" in lower || "hat" in lower) {
            pattern = intArrayOf(42, 46, 42, 46)  // closed, open
        } else if ("crash" in lower) {
            pattern = intArrayOf(49, 42, 42, 42)
        } else if ("cowbell" in lower) {
            pattern = intArrayOf(56, 42, 56, 42)
        } else if ("ui" in lower || "chime" in lower || "notif" in lower) {
            // Two short notes — notification-like
            pattern = intArrayOf(36, 42, 36, 42)
        }

        val channel = MidiConstants.DRUM_CHANNEL
        out.add(MidiEvent.InstrumentChange(0, channel, 0))  // Set drum kit
        for (beat in 0 until totalBeats) {
            val note = pattern[beat % pattern.size]
            val beatStart = beat * beatMs
            val beatEnd = beatStart + beatMs - 30
            out.add(MidiEvent.NoteOn(beatStart, channel, note, velocity = 100))
            out.add(MidiEvent.NoteOff(beatEnd, channel, note))
        }
        return MidiSequence(
            events = out,
            durationMs = durationSec * 1000,
        )
    }
}
