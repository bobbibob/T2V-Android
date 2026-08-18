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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.projectTitle, style = MaterialTheme.typography.titleMedium)
                    Text("${state.engines.size} engines available", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = state.chapterTitle,
                onValueChange = vm::setChapterTitle,
                label = { Text("Название главы / генерации") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(if (state.selectedEngine.isBlank()) "Choose an engine" else "Engine: ${state.selectedEngine}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.engines.take(4).forEach { e ->
                    OutlinedButton(onClick = { vm.setEngine(e.id) }) {
                        Text(e.displayName.take(15))
                    }
                }
            }

            Text("Speed: ${"%.2f".format(state.speed)}")
            Slider(
                value = state.speed.toFloat(),
                onValueChange = { vm.setSpeed(it.toDouble()) },
                valueRange = 0.5f..2.0f,
            )

            if (state.progress.total > 0) {
                val frac = state.progress.done.toFloat() / state.progress.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress.done} / ${state.progress.total}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { scope.launch { vm.startGeneration(context) } },
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

            if (state.progress.phase == GenerationPipeline.Progress.Phase.Failed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Generation failed", style = MaterialTheme.typography.titleMedium)
                        Text(state.progress.error ?: "Unknown generation error")
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
