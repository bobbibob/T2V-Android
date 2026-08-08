package com.t2v.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.R
import com.t2v.app.AppContainer
import com.t2v.data.AudiobookEntity
import com.t2v.data.ProjectEntity
import com.t2v.data.Settings
import com.t2v.data.SettingsRepository
import com.t2v.tts.VoiceConfig
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val modelsFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setModelsTreeUri(uri.toString())
        }
    }
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_settings),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_general))
            SliderSetting("Chunk size", state.settings.chunkSize.toFloat(), 500f..5000f) { vm.setChunkSize(it.toInt()) }
            SwitchSetting("Markup toolbar", state.settings.markupToolbar) { vm.setMarkupToolbar(it) }
            SwitchSetting("Syntax highlight", state.settings.syntaxHighlight) { vm.setSyntaxHighlight(it) }

            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_tts_mode))
            SwitchSetting(
                label = if (state.settings.ttsMode == "cloud") "Cloud services" else "Local models",
                value = state.settings.ttsMode == "cloud",
            ) { cloud -> vm.setTtsMode(if (cloud) "cloud" else "local") }
            if (state.settings.ttsMode != "cloud") {
                Text(stringResource(R.string.settings_models_folder), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = state.settings.modelsTreeUri.ifBlank { stringResource(R.string.settings_default_folder) },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { modelsFolderPicker.launch(null) }) {
                    Text(stringResource(R.string.settings_choose_folder))
                }
                OutlinedButton(onClick = { nav.navigate(com.t2v.ui.navigation.Routes.Models) }) {
                    Text("Manage TTS Models…")
                }
            }

            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_engines))
            PasswordField("OpenAI API Key", state.settings.engines["openai"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("openai", v) }
            PasswordField("ElevenLabs API Key", state.settings.engines["elevenlabs"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("elevenlabs", v) }
            PasswordField("Gemini API Key", state.settings.engines["gemini"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("gemini", v) }
            PasswordField("Yandex API Key", state.settings.engines["yandex"]?.get("apiKey").orEmpty()) { v -> vm.setApiKey("yandex", v) }
            OutlinedTextField(
                value = state.settings.engines["yandex"]?.get("folderId").orEmpty(),
                onValueChange = { vm.setApiKey("yandex", it, "folderId") },
                label = { Text("Yandex Folder ID") },
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField("Azure Subscription Key", state.settings.engines["azure"]?.get("subscriptionKey").orEmpty()) { v -> vm.setApiKey("azure", v) }
            PasswordField("Hugging Face Token (optional)", state.settings.engines["huggingface"]?.get("token").orEmpty()) { v ->
                vm.setApiKey("huggingface", v, "token")
            }
            OutlinedTextField(
                value = state.settings.engines["azure"]?.get("region").orEmpty(),
                onValueChange = { vm.setApiKey("azure", it, "region") },
                label = { Text("Azure region (e.g. eastus)") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_debug))
            Text(
                stringResource(R.string.settings_debug_selftest_help),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { vm.debugRunMarkupSelfTest() },
                enabled = !state.debugRunning,
            ) {
                Text(stringResource(R.string.settings_debug_selftest))
            }
            if (state.debugMessage.isNotBlank()) {
                Text(
                    state.debugMessage,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
    )
}

data class SettingsUiState(val settings: Settings = Settings(
    uiLanguage = "en", outputDir = "output", ttsEngine = "", voiceId = "",
    language = "", speed = 1.0, splitMode = "safe_chunks", exportMode = "single",
    chunkSize = 2500, pauseBetweenBlocksMs = 350, pauseBetweenChaptersMs = 900,
    paragraphPauseMinMs = 450, paragraphPauseMaxMs = 900, markupToolbar = true, syntaxHighlight = true,
    selectedModelId = "", selectedVoiceModelId = "", selectedMusicModelId = "",
    selectedSoundModelId = "", selectedMusicGenerator = "", selectedSoundGenerator = "", ttsMode = "",
    modelsTreeUri = "", onboardingCompleted = false, engines = emptyMap(),
),
    val debugMessage: String = "",
    val debugRunning: Boolean = false,
)

class SettingsViewModel(private val context: android.content.Context) : ViewModel() {
    private val repo = AppContainer.settings(context)
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    init {
        viewModelScope.launch { repo.flow.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    fun setChunkSize(v: Int) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.CHUNK_SIZE] = v } }
    fun setMarkupToolbar(v: Boolean) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.MARKUP_TOOLBAR] = v } }
    fun setSyntaxHighlight(v: Boolean) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.SYNTAX_HIGHLIGHT] = v } }
    fun setTtsMode(v: String) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.TTS_MODE] = v } }
    fun setModelsTreeUri(v: String) = viewModelScope.launch { repo.update { it[SettingsRepository.Keys.MODELS_TREE_URI] = v } }
    fun setApiKey(engine: String, v: String, field: String = "apiKey") = viewModelScope.launch {
        repo.update {
            when (engine) {
                "openai" -> it[SettingsRepository.Keys.OPENAI_KEY] = v
                "elevenlabs" -> it[SettingsRepository.Keys.ELEVENLABS_KEY] = v
                "gemini" -> it[SettingsRepository.Keys.GEMINI_KEY] = v
                "yandex" -> if (field == "folderId") it[SettingsRepository.Keys.YANDEX_FOLDER_ID] = v
                else it[SettingsRepository.Keys.YANDEX_KEY] = v
                "azure" -> if (field == "region") it[SettingsRepository.Keys.AZURE_REGION] = v
                else it[SettingsRepository.Keys.AZURE_KEY] = v
                "huggingface" -> it[SettingsRepository.Keys.HUGGING_FACE_TOKEN] = v
            }
        }
    }

    /**
     * Smoke-test the `<music>`/`<sfx>` markup pipeline end-to-end.
     *
     * Looks for the most recent project in the DB, appends two tags to its
     * `rawText`, creates a fresh `AudiobookEntity` and calls
     * `GenerationPipeline.generate()` on the device. When generation finishes,
     * `audio_tracks` / `audio_clips` should contain one MUSIC and one SOUND
     * clip, with `timelineStartMs` glued to the end of the preceding voice
     * segment.
     *
     * Errors are surfaced through `_state.debugMessage` instead of crashes so
     * the user can see them in the UI.
     */
    fun debugRunMarkupSelfTest() = viewModelScope.launch {
        if (_state.value.debugRunning) return@launch
        _state.update { it.copy(debugRunning = true, debugMessage = "Starting...") }
        try {
            val db = AppContainer.database(context)
            val project: ProjectEntity = db.projects().observeAll().first().firstOrNull()
                ?: error("No projects in DB - create one first")
            val newText = project.rawText.trimEnd() +
                " <music>ambient pad</music> <sfx>door creak</sfx>"
            db.projects().update(project.copy(rawText = newText, updatedAt = System.currentTimeMillis()))

            val settings = repo.flow.first()
            val engineId = settings.ttsEngine.ifBlank { project.ttsEngine }
                .ifBlank { AppContainer.registry(context).allEngineInfos().firstOrNull()?.id ?: "" }
            if (engineId.isBlank()) {
                throw IllegalStateException("No TTS engine available - download Kokoro or add an API key")
            }
            val voices = if (project.voiceConfigJson.isNotBlank()) {
                runCatching {
                    kotlinx.serialization.json.Json.decodeFromString(
                        VoiceConfig.serializer(), project.voiceConfigJson,
                    )
                }.getOrDefault(VoiceConfig.EMPTY)
            } else VoiceConfig.EMPTY
            val voice = voices.copy(
                speed = settings.speed,
                voice = settings.voiceId.ifEmpty { voices.voice },
                lang = settings.language.ifEmpty { voices.lang },
            )

            val orderIndex = db.audiobooks().nextOrderIndex(project.id)
            val startedAt = System.currentTimeMillis()
            val audiobookId = db.audiobooks().upsert(
                AudiobookEntity(
                    projectId = project.id,
                    status = "running",
                    startedAt = startedAt,
                    title = "Self-test <music>/<sfx>",
                    orderIndex = orderIndex,
                ),
            )
            val outputDir = java.io.File(context.filesDir, "audiobooks/$audiobookId")
            val result = AppContainer.pipeline(context).generate(
                projectId = project.id,
                audiobookId = audiobookId,
                rawText = newText,
                voice = voice,
                engineId = engineId,
                outputDir = outputDir,
            )
            val finalStatus = if (result.isSuccess) "completed" else "failed"
            val segments = db.segments().listForAudiobook(audiobookId)
            db.audiobooks().update(
                AudiobookEntity(
                    id = audiobookId,
                    projectId = project.id,
                    status = finalStatus,
                    startedAt = startedAt,
                    completedAt = System.currentTimeMillis(),
                    outputPath = result.getOrNull()?.absolutePath,
                    durationMs = segments.sumOf { it.durationMs },
                    segmentsTotal = segments.size,
                    segmentsDone = segments.count { it.status == "completed" },
                    errorMessage = result.exceptionOrNull()?.message,
                    title = "Self-test <music>/<sfx>",
                    orderIndex = orderIndex,
                ),
            )
            val clipsCount = db.audioTimeline().clips("$audiobookId-music").size +
                db.audioTimeline().clips("$audiobookId-sound").size
            val msg = buildString {
                append("audiobookId=$audiobookId status=$finalStatus segments=${segments.size}")
                append(" music+sound clips=$clipsCount")
                if (result.isFailure) append(" err=${result.exceptionOrNull()?.message}")
            }
            _state.update { it.copy(debugMessage = msg, debugRunning = false) }
        } catch (t: Throwable) {
            _state.update { it.copy(debugMessage = "FAILED: ${t.message}", debugRunning = false) }
        }
    }
}

class SettingsViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context) as T
}
