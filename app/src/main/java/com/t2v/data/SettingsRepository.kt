package com.t2v.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow

private val Context.dataStore by preferencesDataStore(name = "t2v_settings")

/**
 * Хранилище пользовательских настроек. Прямой порт
 * `app/core/settings_manager.py:SettingsManager`.
 */
class SettingsRepository(private val context: Context) {

    object Keys {
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val OUTPUT_DIR = stringPreferencesKey("output_dir")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val LANGUAGE = stringPreferencesKey("language")
        val SPEED = doublePreferencesKey("speed")
        val SPLIT_MODE = stringPreferencesKey("split_mode")
        val EXPORT_MODE = stringPreferencesKey("export_mode")
        val CHUNK_SIZE = intPreferencesKey("chunk_size")
        val PAUSE_BETWEEN_BLOCKS = intPreferencesKey("pause_between_blocks_ms")
        val PAUSE_BETWEEN_CHAPTERS = intPreferencesKey("pause_between_chapters_ms")
        val PARAGRAPH_PAUSE_MIN = intPreferencesKey("paragraph_pause_min_ms")
        val PARAGRAPH_PAUSE_MAX = intPreferencesKey("paragraph_pause_max_ms")
        val MARKUP_TOOLBAR = booleanPreferencesKey("markup_toolbar")
        val SYNTAX_HIGHLIGHT = booleanPreferencesKey("syntax_highlight")
        /** Material You dynamic colors (Android 12+). Default true. */
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")

        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        /** Independently selected generators for each editor track. */
        val SELECTED_VOICE_MODEL_ID = stringPreferencesKey("selected_voice_model_id")
        val SELECTED_MUSIC_MODEL_ID = stringPreferencesKey("selected_music_model_id")
        val SELECTED_SOUND_MODEL_ID = stringPreferencesKey("selected_sound_model_id")
        /** Picked generator per category (music / sound). */
        val SELECTED_MUSIC_GENERATOR = stringPreferencesKey("selected_music_generator")
        val SELECTED_SOUND_GENERATOR = stringPreferencesKey("selected_sound_generator")
        /** local = downloaded models on the device; cloud = API providers. */
        val TTS_MODE = stringPreferencesKey("tts_mode")
        /** Persisted URI returned by ACTION_OPEN_DOCUMENT_TREE. */
        val MODELS_TREE_URI = stringPreferencesKey("models_tree_uri")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // engine configs
        val OPENAI_KEY = stringPreferencesKey("engines.openai.apiKey")
        val ELEVENLABS_KEY = stringPreferencesKey("engines.elevenlabs.apiKey")
        val GEMINI_KEY = stringPreferencesKey("engines.gemini.apiKey")
        val YANDEX_KEY = stringPreferencesKey("engines.yandex.apiKey")
        val YANDEX_FOLDER_ID = stringPreferencesKey("engines.yandex.folderId")
        val AZURE_KEY = stringPreferencesKey("engines.azure.subscriptionKey")
        val AZURE_REGION = stringPreferencesKey("engines.azure.region")
        val CUSTOM_URL = stringPreferencesKey("engines.custom_http.url")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("huggingface.token")
        val PREFERRED_LOCAL_VOICE = stringPreferencesKey("preferred_local_voice")
        val PREFERRED_LOCAL_VOICE_LABEL = stringPreferencesKey("preferred_local_voice_label")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    /** Hot state flow for synchronous reads from non-coroutine callers. */
    val state: StateFlow<Settings> = MutableStateFlow(defaultSettings()).also { mutable ->
        // Bridge the cold flow into the state flow on a process-wide scope.
        GlobalScope.launch(Dispatchers.Default) {
            flow.collect { mutable.value = it }
        }
    }

    private fun defaultSettings(): Settings = Settings(
        uiLanguage = "en",
        outputDir = "output",
        ttsEngine = "",
        voiceId = "",
        language = "",
        speed = 1.0,
        splitMode = "safe_chunks",
        exportMode = "single",
        chunkSize = 2500,
        pauseBetweenBlocksMs = 350,
        pauseBetweenChaptersMs = 900,
        paragraphPauseMinMs = 450,
        paragraphPauseMaxMs = 900,
        markupToolbar = true,
        syntaxHighlight = true,
        selectedModelId = "",
        selectedVoiceModelId = "",
        selectedMusicModelId = "",
        selectedSoundModelId = "",
        selectedMusicGenerator = "",
        selectedSoundGenerator = "",
        ttsMode = "",
        modelsTreeUri = "",
        onboardingCompleted = false,
        engines = emptyMap(),
    )

    suspend fun update(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { p -> transform(p) }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        preferredLocalVoice = this[Keys.PREFERRED_LOCAL_VOICE] ?: "",
        preferredLocalVoiceLabel = this[Keys.PREFERRED_LOCAL_VOICE_LABEL] ?: "",
        uiLanguage = this[Keys.UI_LANGUAGE] ?: "en",
        outputDir = this[Keys.OUTPUT_DIR] ?: "output",
        ttsEngine = this[Keys.TTS_ENGINE] ?: "",
        voiceId = this[Keys.VOICE_ID] ?: "",
        language = this[Keys.LANGUAGE] ?: "",
        speed = this[Keys.SPEED] ?: 1.0,
        splitMode = this[Keys.SPLIT_MODE] ?: "safe_chunks",
        exportMode = this[Keys.EXPORT_MODE] ?: "single",
        chunkSize = this[Keys.CHUNK_SIZE] ?: 2500,
        pauseBetweenBlocksMs = this[Keys.PAUSE_BETWEEN_BLOCKS] ?: 350,
        pauseBetweenChaptersMs = this[Keys.PAUSE_BETWEEN_CHAPTERS] ?: 900,
        paragraphPauseMinMs = this[Keys.PARAGRAPH_PAUSE_MIN] ?: 450,
        paragraphPauseMaxMs = this[Keys.PARAGRAPH_PAUSE_MAX] ?: 900,
        markupToolbar = this[Keys.MARKUP_TOOLBAR] ?: true,
        syntaxHighlight = this[Keys.SYNTAX_HIGHLIGHT] ?: true,
        dynamicColors = this[Keys.DYNAMIC_COLORS] ?: true,
        selectedModelId = this[Keys.SELECTED_MODEL_ID] ?: "",
        selectedVoiceModelId = this[Keys.SELECTED_VOICE_MODEL_ID]
            ?: this[Keys.SELECTED_MODEL_ID]
            ?: "",
        selectedMusicModelId = this[Keys.SELECTED_MUSIC_MODEL_ID] ?: "",
        selectedSoundModelId = this[Keys.SELECTED_SOUND_MODEL_ID] ?: "",
        selectedMusicGenerator = this[Keys.SELECTED_MUSIC_GENERATOR] ?: "",
        selectedSoundGenerator = this[Keys.SELECTED_SOUND_GENERATOR] ?: "",
        ttsMode = this[Keys.TTS_MODE] ?: "",
        modelsTreeUri = this[Keys.MODELS_TREE_URI] ?: "",
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
        engines = mapOf(
            "openai" to mapOf("apiKey" to (this[Keys.OPENAI_KEY] ?: "")),
            "elevenlabs" to mapOf("apiKey" to (this[Keys.ELEVENLABS_KEY] ?: "")),
            "gemini" to mapOf("apiKey" to (this[Keys.GEMINI_KEY] ?: "")),
            "yandex" to mapOf(
                "apiKey" to (this[Keys.YANDEX_KEY] ?: ""),
                "folderId" to (this[Keys.YANDEX_FOLDER_ID] ?: ""),
            ),
            "azure" to mapOf(
                "subscriptionKey" to (this[Keys.AZURE_KEY] ?: ""),
                "region" to (this[Keys.AZURE_REGION] ?: ""),
            ),
            "custom_http" to mapOf("url" to (this[Keys.CUSTOM_URL] ?: "")),
            "huggingface" to mapOf("token" to (this[Keys.HUGGING_FACE_TOKEN] ?: "")),
        ),
    )
}

data class Settings(
    val preferredLocalVoice: String = "",
    val preferredLocalVoiceLabel: String = "",
    val uiLanguage: String,
    val outputDir: String,
    val ttsEngine: String,
    val voiceId: String,
    val language: String,
    val speed: Double,
    val splitMode: String,
    val exportMode: String,
    val chunkSize: Int,
    val pauseBetweenBlocksMs: Int,
    val pauseBetweenChaptersMs: Int,
    val paragraphPauseMinMs: Int,
    val paragraphPauseMaxMs: Int,
    val markupToolbar: Boolean,
    val syntaxHighlight: Boolean,
    val dynamicColors: Boolean = true,
    val selectedModelId: String,
    val selectedVoiceModelId: String,
    val selectedMusicModelId: String,
    val selectedSoundModelId: String,
    val selectedMusicGenerator: String,
    val selectedSoundGenerator: String,
    val ttsMode: String,
    val modelsTreeUri: String,
    val onboardingCompleted: Boolean,
    val engines: Map<String, Map<String, String>>,
)
