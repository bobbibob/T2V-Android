package com.t2v.app

import android.app.Application
import com.t2v.core.text.TextProcessor
import com.t2v.data.AppDatabase
import com.t2v.data.SettingsRepository
import com.t2v.generators.GeneratorRegistry
import com.t2v.tts.registry.EngineRegistry
import com.t2v.worker.GenerationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-класс. Создаёт DI-контейнер вручную (без Hilt — для краткости).
 */
class LTVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(this) }

    @Volatile private var engineSettings = EngineRegistry.EngineSettings()
    val engineRegistry: EngineRegistry by lazy {
        EngineRegistry(
            appContext = this,
            settingsProvider = { engineSettings },
        )
    }

    val generatorRegistry: GeneratorRegistry by lazy {
        GeneratorRegistry(
            appContext = this,
            settingsProvider = { engineSettings },
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settingsRepo.flow.collect { settings ->
                engineSettings = EngineRegistry.EngineSettings(settings.engines)
                engineRegistry.closeAll()
            }
        }
    }

    val textProcessor: TextProcessor by lazy {
        // Берём из настроек, но по умолчанию 2500 символов.
        TextProcessor()
    }

    val pipeline: GenerationPipeline by lazy {
        GenerationPipeline(this, engineRegistry, textProcessor).also { pipeline ->
            pipeline.setAudioTagInserter(
                com.t2v.core.audio.AudioTagInserter(
                    appContext = this,
                    generatorRegistry = { generatorRegistry },
                    trackVolumeDb = {
                        mapOf(
                            com.t2v.core.audio.AudioTrackKind.Voice to 0.0,
                            com.t2v.core.audio.AudioTrackKind.Music to -24.0,
                            com.t2v.core.audio.AudioTrackKind.Sound to -8.0,
                        )
                    },
                    selectedMusicId = {
                        settingsRepo.state.value.selectedMusicGenerator
                    },
                    selectedSoundId = {
                        settingsRepo.state.value.selectedSoundGenerator
                    },
                ),
            )
        }
    }
}
