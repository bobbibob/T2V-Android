package com.t2v.generators

import android.content.Context
import com.t2v.generators.impl.ElevenLabsMusicGenerator
import com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator
import com.t2v.generators.impl.FreesoundSfxGenerator
import com.t2v.generators.impl.Lyria2MusicGenerator
import com.t2v.generators.impl.MusicGenMusicGenerator
import com.t2v.generators.impl.MusicGenOnnxGenerator
import com.t2v.generators.impl.NSynthSoundGenerator
import com.t2v.generators.impl.OpenAiMusicGenerator
import com.t2v.generators.impl.StableAudioCloudGenerator
import com.t2v.generators.impl.StableAudioMusicGenerator
import com.t2v.generators.impl.StableAudioSoundGenerator
import com.t2v.generators.impl.SunoMusicGenerator
import com.t2v.generators.impl.TinyMusicianMusicGenerator
import com.t2v.generators.impl.TinyMusicianSfxGenerator
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.tts.registry.EngineRegistry

/**
 * Registry for music/sound generators, parallel to [com.t2v.tts.registry.EngineRegistry].
 *
 * Все генераторы делятся на три группы:
 *  1. **Always-on** (procedural DSP): StableAudioMusic, StableAudioSound, MusicGen, TinyMusician*.
 *     Не требуют скачивания, работают на любом ARM64-устройстве.
 *  2. **Local + Download**: NSynth, TinyMusician (реальный runtime), Freesound.
 *     Требуют скачивания модели/бандла. Если не скачано — fallback на процедурный.
 *  3. **Cloud**: ElevenLabs, OpenAI Music, Suno, Lyria-2, Stable Audio cloud.
 *     Требуют API-ключ.
 */
class GeneratorRegistry(
    private val appContext: Context,
    private val settingsProvider: () -> EngineRegistry.EngineSettings,
) {
    private val instances = mutableMapOf<String, Generator>()
    private val liteRtRuntime: LiteRtModelRuntime by lazy { LiteRtModelRuntime(appContext) }
    val installer: LiteRtModelInstaller by lazy { LiteRtModelInstaller(liteRtRuntime) }
    val runtime: LiteRtModelRuntime get() = liteRtRuntime

    fun all(): List<Generator> = buildList {
        val settings = settingsProvider()
        val engines = settings.engines
        val elevenKey = engines["elevenlabs"]?.get("apiKey").orEmpty()
        val openaiKey = engines["openai"]?.get("apiKey").orEmpty()
        val googleKey = engines["google"]?.get("apiKey").orEmpty()
        val sunoKey = engines["suno"]?.get("apiKey").orEmpty()
        val stabilityKey = engines["stability"]?.get("apiKey").orEmpty()

        // ── Music generators ────────────────────────────────────────────
        add(StableAudioMusicGenerator(appContext, liteRtRuntime, installer))
        add(MusicGenMusicGenerator(appContext))
        add(MusicGenOnnxGenerator(appContext))
        add(TinyMusicianMusicGenerator(appContext, liteRtRuntime, installer))
        add(ElevenLabsMusicGenerator(apiKey = elevenKey))
        add(OpenAiMusicGenerator(apiKey = openaiKey))
        add(SunoMusicGenerator(apiKey = sunoKey))
        add(StableAudioCloudGenerator(apiKey = stabilityKey))
        add(Lyria2MusicGenerator(apiKey = googleKey))

        // ── Sound generators ────────────────────────────────────────────
        add(StableAudioSoundGenerator(appContext, liteRtRuntime, installer))
        add(TinyMusicianSfxGenerator(appContext, liteRtRuntime, installer))
        add(NSynthSoundGenerator(appContext, liteRtRuntime, installer))
        add(ElevenLabsSoundEffectsGenerator(apiKey = elevenKey))
        add(StableAudioCloudGenerator(apiKey = stabilityKey)) // one cloud engine, both music and SFX
        add(FreesoundSfxGenerator(appContext))
    }

    fun forCategory(category: GeneratorCategory): List<Generator> =
        all().filter { it.category == category && it.isAvailable() }

    fun get(id: String): Generator? = all().firstOrNull { it.id == id }

    fun defaultFor(category: GeneratorCategory): Generator? =
        forCategory(category).firstOrNull()

    /** Probe LiteRT support on this device. */
    fun probeLiteRt(): LiteRtModelRuntime.ProbeResult = liteRtRuntime.probe()
}
