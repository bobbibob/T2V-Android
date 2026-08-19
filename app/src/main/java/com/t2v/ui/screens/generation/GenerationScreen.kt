package com.t2v.ui.screens.generation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.R
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.t2v.app.AppContainer
import com.t2v.tts.EngineInfo
import com.t2v.tts.VoiceConfig
import com.t2v.ui.components.AudioPlaybackBar
import com.t2v.ui.components.LTVScaffold
import com.t2v.worker.GenerationPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationScreen(
    nav: NavController,
    projectId: Long,
    vm: GenerationViewModel = viewModel(factory = GenerationViewModelFactory(LocalContext.current, projectId)),
    windowSizeClass: WindowSizeClass? = null,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Auto-jump to the audio editor when the run produced any <music>/<sfx>
    // clips. This only fires once per generation.
    androidx.compose.runtime.LaunchedEffect(state.audiobookId, state.progress.phase, state.progress.audioTagClips) {
        val id = state.audiobookId ?: return@LaunchedEffect
        val completed = state.progress.phase == GenerationPipeline.Progress.Phase.Completed
        val hasExtras = state.progress.audioTagClips > 0
        if (completed && hasExtras && !state.autoNavigatedToEditor) {
            vm.markAutoNavigated()
            nav.navigate(com.t2v.ui.navigation.Routes.audioEditor(id)) {
                popUpTo(com.t2v.ui.navigation.Routes.Generation) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_generation),
        onBack = { nav.popBackStack() },
        windowSizeClass = windowSizeClass,
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Выбранный движок крупно на самом верху ──
            val selectedEngineInfo = state.engines.firstOrNull { it.id == state.selectedEngine }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (selectedEngineInfo != null) {
                        Text("▶ ${selectedEngineInfo.displayName}", style = MaterialTheme.typography.titleLarge)
                        Text(selectedEngineInfo.id, style = MaterialTheme.typography.bodySmall)
                        if (state.autoDetectResult != null) {
                            Text(
                                "Язык: ${state.autoDetectResult!!.bcp47} (${(state.autoDetectResult!!.confidence * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text("Движок не выбран", style = MaterialTheme.typography.titleMedium)
                        Text("Выберите движок ниже", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = state.chapterTitle,
                onValueChange = vm::setChapterTitle,
                label = { Text("Название главы / генерации") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Выбор движка: выбранный первым, потом остальные ──
            Text("Движок:", style = MaterialTheme.typography.labelLarge)
            val sortedEngines = state.engines.sortedByDescending { it.id == state.selectedEngine }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sortedEngines.take(6).forEach { e ->
                    val isSelected = e.id == state.selectedEngine
                    Button(
                        onClick = { vm.setEngine(e.id) },
                        modifier = Modifier.weight(1f),
                        colors = if (isSelected) ButtonDefaults.buttonColors()
                            else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(e.displayName.take(12), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── Пример промпта с учётом выбранной модели ──
            val examplePrompt = remember(state.selectedEngine) {
                engineExamplePrompt(state.selectedEngine)
            }
            if (examplePrompt.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Пример:", style = MaterialTheme.typography.labelSmall)
                        Text(examplePrompt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Auto-detect ──
            OutlinedButton(
                onClick = { vm.detectLanguage() },
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🌐 Определить язык автоматически")
            }

            // ── Скорость ──
            Text("Скорость: ${"%.2f".format(state.speed)}×", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.speed.toFloat(),
                onValueChange = { vm.setSpeed(it.toDouble()) },
                valueRange = 0.5f..2.0f,
            )

            // ── Прогресс ──
            if (state.progress.total > 0) {
                val frac = state.progress.done.toFloat() / state.progress.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress.done} / ${state.progress.total}")
            }

            // ── Кнопки генерации ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { vm.startGeneration(context) }
                                .onFailure { vm.setError(it.message ?: "Generation crashed") }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRunning && state.selectedEngine.isNotBlank(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  " + stringResource(R.string.gen_start))
                }
                OutlinedButton(
                    onClick = { vm.cancel() },
                    modifier = Modifier.weight(1f),
                    enabled = state.isRunning,
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Text("  " + stringResource(R.string.gen_cancel))
                }
            }

            // ── Ошибка (не вылет, а показ) ──
            state.error?.let { errMsg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⚠ Ошибка", style = MaterialTheme.typography.titleSmall)
                        Text(errMsg, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { vm.clearError() }) { Text("Закрыть") }
                    }
                }
            }

            if (
                state.audiobookId != null &&
                state.progress.phase == GenerationPipeline.Progress.Phase.Completed
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Audiobook ready", style = MaterialTheme.typography.titleMedium)
                        AudioPlaybackBar(
                            audioFile = java.io.File(
                                context.filesDir,
                                "audiobooks/${state.audiobookId}/audiobook.wav",
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = {
                                nav.navigate(com.t2v.ui.navigation.Routes.review(state.audiobookId!!))
                            }) { Text(stringResource(R.string.review_title)) }
                            Button(onClick = {
                                nav.navigate(com.t2v.ui.navigation.Routes.musicMix(state.audiobookId!!))
                            }) { Text(stringResource(R.string.mix_title)) }
                        }
                    }
                }
            }
        }
    }
}

data class GenState(
    val projectId: Long = 0,
    val projectTitle: String = "Loading…",
    val chapterTitle: String = "",
    val engines: List<EngineInfo> = emptyList(),
    val selectedEngine: String = "",
    val speed: Double = 1.0,
    val isRunning: Boolean = false,
    val progress: GenerationPipeline.Progress = GenerationPipeline.Progress(),
    val audiobookId: Long? = null,
    /** True once we've already auto-navigated to the audio editor for this run. */
    val autoNavigatedToEditor: Boolean = false,
    /** Auto-TTS detected language hint. */
    val autoDetectResult: com.t2v.tts.auto.AutoTtsDetector.VoiceHint? = null,
    /** Error message shown in UI instead of crashing. */
    val error: String? = null,
)

class GenerationViewModel(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val registry = AppContainer.registry(context)
    private val pipeline = AppContainer.pipeline(context)
    private val settings = AppContainer.settings(context)
    private val _state = MutableStateFlow(GenState(projectId = projectId))
    val state: StateFlow<GenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val project = db.projects().byId(projectId)
            if (project != null) {
                val voices = if (project.voiceConfigJson.isNotBlank()) {
                    runCatching { Json.decodeFromString(VoiceConfig.serializer(), project.voiceConfigJson) }
                        .getOrDefault(VoiceConfig.EMPTY)
                } else VoiceConfig.EMPTY
                _state.update {
                    it.copy(
                        projectTitle = project.title,
                        chapterTitle = "Глава ${db.audiobooks().nextOrderIndex(projectId) + 1}",
                        selectedEngine = project.ttsEngine,
                        speed = voices.speed,
                        engines = registry.allEngineInfos(),
                    )
                }
            }
        }
        viewModelScope.launch {
            pipeline.progress.collect { p ->
                _state.update {
                    it.copy(
                        progress = p,
                        isRunning = p.phase == GenerationPipeline.Progress.Phase.Processing ||
                            p.phase == GenerationPipeline.Progress.Phase.Synthesizing ||
                            p.phase == GenerationPipeline.Progress.Phase.Encoding,
                    )
                }
            }
        }
    }

    fun setEngine(id: String) = _state.update { it.copy(selectedEngine = id) }
    fun setChapterTitle(value: String) = _state.update { it.copy(chapterTitle = value) }
    fun setSpeed(v: Double) = _state.update { it.copy(speed = v) }
    fun cancel() = viewModelScope.launch { pipeline.cancel() }
    fun setError(msg: String) = _state.update { it.copy(error = msg) }
    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Auto-detect the language of the project text and suggest a matching
     * engine/voice. Uses [com.t2v.tts.auto.AutoTtsDetector] heuristics.
     */
    fun detectLanguage() {
        viewModelScope.launch {
            val text = db.projects().byId(projectId)?.rawText ?: return@launch
            val hint = com.t2v.tts.auto.AutoTtsDetector.detect(text)
            _state.update { it.copy(autoDetectResult = hint) }
            // Try to pick an engine that supports the detected language
            val matchingEngine = registry.allEngineInfos().firstOrNull { info ->
                val voices = runCatching { registry.get(info.id).listVoices() }.getOrDefault(emptyList())
                com.t2v.tts.auto.AutoVoicePicker.pickVoice(voices, hint.bcp47) != null
            }
            if (matchingEngine != null) {
                _state.update { it.copy(selectedEngine = matchingEngine.id) }
            }
        }
    }
    fun markAutoNavigated() {
        _state.update { it.copy(autoNavigatedToEditor = true) }
    }

    suspend fun startGeneration(context: android.content.Context) {
        val s = settings.flow.first()
        val project = db.projects().byId(projectId) ?: return
        val voices = if (project.voiceConfigJson.isNotBlank()) {
            runCatching { Json.decodeFromString(VoiceConfig.serializer(), project.voiceConfigJson) }
                .getOrDefault(VoiceConfig.EMPTY)
        } else VoiceConfig.EMPTY
        val engineId = _state.value.selectedEngine
        val v = voices.copy(
            speed = _state.value.speed,
            voice = s.voiceId.ifEmpty { voices.voice },
            lang = s.language.ifEmpty { voices.lang },
        )
        val startedAt = System.currentTimeMillis()
        val audiobookId = db.audiobooks().upsert(
            com.t2v.data.AudiobookEntity(
                projectId = projectId,
                status = "running",
                startedAt = startedAt,
                title = _state.value.chapterTitle.ifBlank { "Глава" },
                orderIndex = db.audiobooks().nextOrderIndex(projectId),
            ),
        )
        val outputDir = java.io.File(context.filesDir, "audiobooks/$audiobookId")
        val result = pipeline.generate(
            projectId = projectId,
            audiobookId = audiobookId,
            rawText = project.rawText,
            voice = v,
            engineId = engineId,
            outputDir = outputDir,
        )
        val finalStatus = if (result.isSuccess) "completed" else "failed"
        val segments = db.segments().listForAudiobook(audiobookId)
        db.audiobooks().update(
            com.t2v.data.AudiobookEntity(
                id = audiobookId,
                projectId = projectId,
                status = finalStatus,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                outputPath = result.getOrNull()?.absolutePath,
                durationMs = segments.sumOf { it.durationMs },
                segmentsTotal = segments.size,
                segmentsDone = segments.count { it.status == "completed" },
                errorMessage = result.exceptionOrNull()?.message,
                title = _state.value.chapterTitle.ifBlank { "Глава" },
                orderIndex = db.audiobooks().byId(audiobookId)?.orderIndex ?: 0,
            ),
        )
        _state.update { it.copy(audiobookId = audiobookId) }
    }
}

class GenerationViewModelFactory(
    private val context: android.content.Context,
    private val projectId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GenerationViewModel(context, projectId) as T
}

/**
 * Returns an example prompt/tag snippet for the selected engine, showing
 * what tags work and how to use them.
 */
private fun engineExamplePrompt(engineId: String): String = when (engineId) {
    "kokoro" -> """{{voice "af_sarah"}}Hello, this is a test with {{speed 1.2}} emphasis.
<whisper>Quiet part here.</whisper> Normal voice again.
{{pause 500ms}}After the pause."""
    "piper_ru" -> """{{voice "irina"}}Привет! {{speed 0.9}}Медленнее.
<whisper>Тихо.</whisper> Громко.
{{pause 700ms}}Пауза."""
    "openai" -> """{{emotion excited}}{{delivery fast}}This is amazing!
{{voice "nova"}}Switching voice.
{{pause 500ms}}Pause."""
    "elevenlabs" -> """{{emotion sad}}[long pause] [breath] Я так и не попрощался.
{{delivery whisper}}[whispers] [gasp] мы одни?"""
    "gemini" -> """{{emotion serious}}Прочитай это объявление внятно и с расстановкой.
{{voice "Aoede"}}Switching voice."""
    "azure" -> """{{emotion cheerful}}Привет и добро пожаловать!
{{delivery whisper}}[stage whisper] Держись рядом."""
    "yandex" -> """{{voice "alena"}}Привет! Это я.
{{lang en-US}}The quick brown fox."""
    else -> """Текст с тегами: <whisper>шёпот</whisper> нормально <fast>быстро</fast>
{{pause 500ms}} пауза <music>эмбиент 10 сек</music>"""
}
