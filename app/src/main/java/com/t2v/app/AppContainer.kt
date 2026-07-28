package com.t2v.app

import android.content.Context
import com.t2v.core.text.TextProcessor
import com.t2v.data.AppDatabase
import com.t2v.data.SettingsRepository
import com.t2v.generators.GeneratorRegistry
import com.t2v.tts.registry.EngineRegistry
import com.t2v.worker.GenerationPipeline

/**
 * Простой DI-контейнер, доступный из любого места как (context.applicationContext as LTVApplication).
 */
object AppContainer {
    fun database(ctx: Context): AppDatabase =
        (ctx.applicationContext as LTVApplication).database
    fun settings(ctx: Context): SettingsRepository =
        (ctx.applicationContext as LTVApplication).settingsRepo
    fun registry(ctx: Context): EngineRegistry =
        (ctx.applicationContext as LTVApplication).engineRegistry
    fun generatorRegistry(ctx: Context): GeneratorRegistry =
        (ctx.applicationContext as LTVApplication).generatorRegistry
    fun pipeline(ctx: Context): GenerationPipeline =
        (ctx.applicationContext as LTVApplication).pipeline
    fun textProcessor(ctx: Context): TextProcessor =
        (ctx.applicationContext as LTVApplication).textProcessor
}
