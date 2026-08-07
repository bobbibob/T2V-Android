package com.t2v.generators

import android.content.Context
import com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator
import com.t2v.generators.impl.MusicGenOnnxGenerator
import com.t2v.generators.impl.NSynthSoundGenerator
import com.t2v.generators.impl.StableAudioMusicGenerator
import com.t2v.generators.impl.StableAudioSoundGenerator
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.tts.registry.EngineRegistry

/**
 * Registry for music/sound generators, parallel to [com.t2v.tts.registry.EngineRegistry].
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
        add(NSynthSoundGenerator(appContext))
        val elevenCfg = settingsProvider().engines["elevenlabs"]
        val apiKey = elevenCfg?.get("apiKey")?.takeIf { it.isNotBlank() }
        add(ElevenLabsSoundEffectsGenerator(apiKey = apiKey.orEmpty()))
        add(StableAudioMusicGenerator(appContext, liteRtRuntime, installer))
        add(StableAudioSoundGenerator(appContext, liteRtRuntime, installer))
        add(MusicGenOnnxGenerator(appContext, liteRtRuntime, installer))
    }

    fun forCategory(category: GeneratorCategory): List<Generator> =
        all().filter { it.category == category && it.isAvailable() }

    fun get(id: String): Generator? = all().firstOrNull { it.id == id }

    fun defaultFor(category: GeneratorCategory): Generator? =
        forCategory(category).firstOrNull()

    /** Probe LiteRT support on this device. */
    fun probeLiteRt(): LiteRtModelRuntime.ProbeResult = liteRtRuntime.probe()
}
