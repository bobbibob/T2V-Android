package com.t2v.ui.screens.audioeditor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.app.AppContainer
import com.t2v.core.audio.AudioEditClip
import com.t2v.core.audio.AudioEditProject
import com.t2v.core.audio.AudioTrackKind
import com.t2v.core.audio.FFmpegBridge
import com.t2v.data.AudioClipEntity
import com.t2v.data.AudioTrackEntity
import com.t2v.data.ChapterExportEntity
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioEditorScreen(
    nav: NavController,
    audiobookId: Long,
    vm: AudioEditorViewModel = viewModel(
        factory = AudioEditorViewModelFactory(LocalContext.current, audiobookId),
    ),
    windowSizeClass: WindowSizeClass? = null,
) {
    val state by vm.state.collectAsState()
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(vm::addMusic)
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(vm::addSound)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stopPreview() }
    }
    LTVScaffold(nav, "Audio editor", onBack = { nav.popBackStack() }, windowSizeClass = windowSizeClass) { padding: PaddingValues ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Timeline ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = { vm.setZoom(state.pixelsPerSecond * 0.7f) }) { Text("−") }
                Text("Zoom", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = { vm.setZoom(state.pixelsPerSecond * 1.4f) }) { Text("+") }
            }
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                TimelineView(
                    voiceClips = state.project.voiceClips,
                    musicClips = state.project.musicClips,
                    soundClips = state.project.soundClips,
                    selectedClipId = state.selectedClipId,
                    playheadMs = state.playheadMs,
                    pixelsPerSecond = state.pixelsPerSecond,
                    onClipTap = vm::selectClip,
                )
            }

            // ── Selected clip properties ───────────────────────────────
            state.selectedClipId?.let { clipId ->
                val kind = state.selectedClipKind ?: AudioTrackKind.Voice
                val clips = when (kind) {
                    AudioTrackKind.Voice -> state.project.voiceClips
                    AudioTrackKind.Music -> state.project.musicClips
                    AudioTrackKind.Sound -> state.project.soundClips
                }
                val clip = clips.firstOrNull { it.id == clipId }
                if (clip != null) {
                    SelectedClipPanel(
                        clip = clip,
                        kind = kind,
                        isPlaying = state.previewingClipId == clip.id,
                        onPlay = { vm.previewClip(clip) },
                        onDelete = { vm.delete(kind, clip.id) },
                        onTimelineStart = { vm.setTimelineStart(kind, clip.id, it) },
                        onStart = { vm.setStart(kind, clip.id, it) },
                        onEnd = { vm.setEnd(kind, clip.id, it) },
                        onGain = { vm.setGain(kind, clip.id, it) },
                        onSpeed = { vm.setSpeed(kind, clip.id, it) },
                        onSplit = { vm.split(kind, clip.id) },
                    )
                }
            }

            // ── Generators ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { musicPicker.launch("audio/*") }) { Text("Import music") }
                OutlinedButton(onClick = { soundPicker.launch("audio/*") }) { Text("Import sound") }
            }
            GeneratorPanel(
                title = "Generate music",
                prompt = state.musicPrompt,
                onPromptChange = vm::setMusicPrompt,
                options = state.musicOptions,
                selectedId = state.selectedMusicGeneratorId,
                onPick = vm::pickMusicGenerator,
                onRun = vm::generateMusic,
                generating = state.generatingMusic,
                error = state.error,
            )
            GeneratorPanel(
                title = "Generate sound effect",
                prompt = state.soundPrompt,
                onPromptChange = vm::setSoundPrompt,
                options = state.soundOptions,
                selectedId = state.selectedSoundGeneratorId,
                onPick = vm::pickSoundGenerator,
                onRun = vm::generateSound,
                generating = state.generatingSound,
                error = state.error,
            )

            // ── Actions ────────────────────────────────────────────────
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::save,
                    enabled = !state.saving && !state.rendering,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.saving) "Сохранение…" else "Сохранить")
                }
                Button(
                    onClick = vm::exportMp3,
                    enabled = !state.rendering && state.project.voiceClips.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.rendering) "Экспорт…" else "Экспорт MP3")
                }
            }
            state.savedAt?.let { Text("Сохранено: ${java.text.DateFormat.getTimeInstance().format(it)}") }
            Button(
                onClick = vm::render,
                enabled = !state.rendering && state.project.voiceClips.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.rendering) "Rendering…" else "Render edited audio")
            }
            state.outputPath?.let { Text("Saved: $it") }
        }
    }
}

@Composable
private fun TrackEditor(
    title: String,
    kind: AudioTrackKind,
    clips: List<AudioEditClip>,
    vm: AudioEditorViewModel,
    previewingClipId: String?,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (clips.isEmpty()) Text("No clips")
    clips.forEachIndexed { index, clip ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { vm.previewClip(clip) }) {
                        Icon(
                            if (previewingClipId == clip.id) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (previewingClipId == clip.id) "Stop" else "Play",
                        )
                    }
                    Text(File(clip.sourcePath).name.ifBlank { "Clip ${index + 1}" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clip.timelineStartMs.toString(),
                        onValueChange = { vm.setTimelineStart(kind, clip.id, it.toLongOrNull() ?: 0) },
                        label = { Text("Timeline, ms") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = clip.startMs.toString(),
                        onValueChange = { vm.setStart(kind, clip.id, it.toLongOrNull() ?: 0) },
                        label = { Text("Start, ms") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = clip.endMs.toString(),
                        onValueChange = { vm.setEnd(kind, clip.id, it.toLongOrNull() ?: 0) },
                        label = { Text("End, ms (0 = end)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = clip.gainDb.toString(),
                    onValueChange = { vm.setGain(kind, clip.id, it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Gain, dB") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Speed: ${"%.2f".format(clip.speed)}×")
                Slider(
                    value = clip.speed.toFloat(),
                    onValueChange = { vm.setSpeed(kind, clip.id, it.toDouble()) },
                    valueRange = 0.5f..2f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.move(kind, index, -1) }, enabled = index > 0) {
                        Text("Earlier")
                    }
                    OutlinedButton(
                        onClick = { vm.move(kind, index, 1) },
                        enabled = index < clips.lastIndex,
                    ) { Text("Later") }
                    OutlinedButton(onClick = { vm.split(kind, clip.id) }) { Text("Split") }
                    OutlinedButton(onClick = { vm.delete(kind, clip.id) }) { Text("Delete") }
                }
            }
        }
    }
}

data class GeneratorOption(
    val id: String,
    val displayName: String,
    val available: Boolean = true,
)

@Composable
private fun SelectedClipPanel(
    clip: AudioEditClip,
    kind: AudioTrackKind,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onTimelineStart: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onGain: (Double) -> Unit,
    onSpeed: (Double) -> Unit,
    onSplit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onPlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    )
                }
                Text(
                    "${kind.name}: ${File(clip.sourcePath).name}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onSplit) { Text("Split") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = clip.timelineStartMs.toString(),
                    onValueChange = { onTimelineStart(it.toLongOrNull() ?: 0) },
                    label = { Text("Timeline ms") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = clip.startMs.toString(),
                    onValueChange = { onStart(it.toLongOrNull() ?: 0) },
                    label = { Text("Start ms") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = clip.endMs.toString(),
                    onValueChange = { onEnd(it.toLongOrNull() ?: 0) },
                    label = { Text("End ms") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = clip.gainDb.toString(),
                    onValueChange = { onGain(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Gain dB") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Text("Speed: ${"%.2f".format(clip.speed)}×")
                Slider(
                    value = clip.speed.toFloat(),
                    onValueChange = { onSpeed(it.toDouble()) },
                    valueRange = 0.5f..2f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GeneratorPanel(
    title: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    options: List<GeneratorOption>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onRun: () -> Unit,
    generating: Boolean,
    error: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (options.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { opt ->
                        val isSelected = opt.id == selectedId
                        OutlinedButton(
                            onClick = { onPick(opt.id) },
                            enabled = opt.available,
                            colors = if (isSelected && opt.available)
                                androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                        ) {
                            Text(
                                if (opt.available) opt.displayName else "${opt.displayName} (unavailable)",
                                style = if (isSelected) MaterialTheme.typography.labelMedium
                                    else MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Prompt") },
                placeholder = { Text("e.g. ambient pad, cinema calm, door close, whoosh") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
            )
            Button(onClick = onRun, enabled = !generating && prompt.isNotBlank()) {
                Text(if (generating) "Generating…" else "Generate")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

data class AudioEditorState(
    val project: AudioEditProject = AudioEditProject(),
    val rendering: Boolean = false,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val outputPath: String? = null,
    val error: String? = null,
    val musicPrompt: String = "",
    val soundPrompt: String = "",
    val musicOptions: List<GeneratorOption> = emptyList(),
    val soundOptions: List<GeneratorOption> = emptyList(),
    val selectedMusicGeneratorId: String? = null,
    val selectedSoundGeneratorId: String? = null,
    val generatingMusic: Boolean = false,
    val generatingSound: Boolean = false,
    val previewingClipId: String? = null,
    val selectedClipId: String? = null,
    val selectedClipKind: AudioTrackKind? = null,
    val pixelsPerSecond: Float = 50f,
    val playheadMs: Long = 0L,
)

class AudioEditorViewModel(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val generators = AppContainer.generatorRegistry(context)
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshGeneratorOptions()
            loadTimeline()
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }

    private fun refreshGeneratorOptions() {
        val music = generators.all().filter { it.category == GeneratorCategory.Music }
            .map { GeneratorOption(it.id, it.displayName, it.isAvailable()) }
        val sound = generators.all().filter { it.category == GeneratorCategory.Sound }
            .map { GeneratorOption(it.id, it.displayName, it.isAvailable()) }
        _state.update {
            it.copy(
                musicOptions = music,
                soundOptions = sound,
                selectedMusicGeneratorId = it.selectedMusicGeneratorId
                    ?: music.firstOrNull { o -> o.available }?.id,
                selectedSoundGeneratorId = it.selectedSoundGeneratorId
                    ?: sound.firstOrNull { o -> o.available }?.id,
                musicPrompt = it.musicPrompt.ifBlank { "ambient" },
                soundPrompt = it.soundPrompt.ifBlank { "whoosh" },
            )
        }
    }

    fun setMusicPrompt(value: String) = _state.update { it.copy(musicPrompt = value) }
    fun setSoundPrompt(value: String) = _state.update { it.copy(soundPrompt = value) }
    fun pickMusicGenerator(id: String) = _state.update { it.copy(selectedMusicGeneratorId = id) }
    fun pickSoundGenerator(id: String) = _state.update { it.copy(selectedSoundGeneratorId = id) }

    fun generateMusic() = runGenerator(GeneratorCategory.Music, ::generateMusicImpl)
    fun generateSound() = runGenerator(GeneratorCategory.Sound, ::generateSoundImpl)

    private fun runGenerator(category: GeneratorCategory, impl: suspend (Generator, java.io.File) -> Unit) {
        viewModelScope.launch {
            val selectedId = if (category == GeneratorCategory.Music)
                _state.value.selectedMusicGeneratorId else _state.value.selectedSoundGeneratorId
            val gen = selectedId?.let { generators.get(it) }
                ?: generators.defaultFor(category)
            if (gen == null || !gen.isAvailable()) {
                val hint = if (selectedId != null) "Generator '$selectedId' is not ready. " else ""
                _state.update { it.copy(error = "${hint}No available generator for ${category.name}") }
                return@launch
            }
            val kindField = if (category == GeneratorCategory.Music) AudioTrackKind.Music else AudioTrackKind.Sound
            val output = java.io.File(context.filesDir, "audiobooks/$audiobookId/generated-${kindField.name.lowercase()}-${System.currentTimeMillis()}.wav")
            _state.update {
                if (category == GeneratorCategory.Music) it.copy(generatingMusic = true)
                else it.copy(generatingSound = true)
            }
            runCatching { impl(gen, output) }.onFailure { e ->
                _state.update {
                    (if (category == GeneratorCategory.Music) it.copy(generatingMusic = false)
                    else it.copy(generatingSound = false)).copy(error = e.message)
                }
            }
            _state.update {
                if (category == GeneratorCategory.Music) it.copy(generatingMusic = false)
                else it.copy(generatingSound = false)
            }
        }
    }

    private suspend fun generateMusicImpl(gen: Generator, output: java.io.File) {
        val req = com.t2v.generators.GeneratorRequest(
            prompt = _state.value.musicPrompt,
            outputFile = output,
            category = GeneratorCategory.Music,
        )
        gen.generate(req)
        addClipToTrack(AudioTrackKind.Music, output)
    }

    private suspend fun generateSoundImpl(gen: Generator, output: java.io.File) {
        val req = com.t2v.generators.GeneratorRequest(
            prompt = _state.value.soundPrompt,
            outputFile = output,
            category = GeneratorCategory.Sound,
        )
        gen.generate(req)
        addClipToTrack(AudioTrackKind.Sound, output)
    }

    private fun addClipToTrack(kind: AudioTrackKind, file: java.io.File) {
        mutate(kind) { it + AudioEditClip(sourcePath = file.absolutePath) }
        _state.update { it.copy(error = null) }
    }

    private val audioPlayer = com.t2v.util.AudioPlayer()

    fun previewClip(clip: AudioEditClip) {
        val file = java.io.File(clip.sourcePath)
        if (!file.isFile) {
            _state.update { it.copy(error = "Audio file not found: ${clip.sourcePath}") }
            return
        }
        if (_state.value.previewingClipId == clip.id) {
            audioPlayer.stop()
            _state.update { it.copy(previewingClipId = null) }
            return
        }
        audioPlayer.play(file) {
            _state.update { it.copy(previewingClipId = null, playheadMs = 0L) }
        }
        _state.update { it.copy(previewingClipId = clip.id, playheadMs = clip.timelineStartMs) }
    }

    fun stopPreview() {
        audioPlayer.stop()
        _state.update { it.copy(previewingClipId = null, playheadMs = 0L) }
    }

    fun selectClip(kind: AudioTrackKind, clip: AudioEditClip) {
        _state.update {
            it.copy(
                selectedClipId = if (it.selectedClipId == clip.id) null else clip.id,
                selectedClipKind = if (it.selectedClipId == clip.id) null else kind,
            )
        }
    }

    fun setZoom(pps: Float) {
        _state.update { it.copy(pixelsPerSecond = pps.coerceIn(10f, 300f)) }
    }

    fun addMusic(uri: Uri) = viewModelScope.launch {
        addImported(uri, AudioTrackKind.Music, "music")
    }

    fun addSound(uri: Uri) = viewModelScope.launch {
        addImported(uri, AudioTrackKind.Sound, "sound")
    }

    private suspend fun addImported(uri: Uri, kind: AudioTrackKind, prefix: String) {
        runCatching {
            val file = File(context.filesDir, "audiobooks/$audiobookId/editor-$prefix-${System.currentTimeMillis()}")
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            } ?: error("Cannot read music")
            file
        }.onSuccess { file ->
            mutate(kind) { it + AudioEditClip(sourcePath = file.absolutePath) }
        }.onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    fun setTimelineStart(kind: AudioTrackKind, id: String, value: Long) =
        updateClip(kind, id) { it.copy(timelineStartMs = value.coerceAtLeast(0)) }

    fun setStart(kind: AudioTrackKind, id: String, value: Long) =
        updateClip(kind, id) { it.copy(startMs = value.coerceAtLeast(0)) }

    fun setEnd(kind: AudioTrackKind, id: String, value: Long) =
        updateClip(kind, id) { it.copy(endMs = value.coerceAtLeast(0)) }

    fun setSpeed(kind: AudioTrackKind, id: String, value: Double) =
        updateClip(kind, id) { it.copy(speed = value.coerceIn(0.5, 2.0)) }

    fun setGain(kind: AudioTrackKind, id: String, value: Double) =
        updateClip(kind, id) { it.copy(gainDb = value.coerceIn(-60.0, 12.0)) }

    fun split(kind: AudioTrackKind, id: String) {
        val clips = clips(kind)
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return
        val clip = clips[index]
        val splitAt = if (clip.endMs > clip.startMs) {
            clip.startMs + (clip.endMs - clip.startMs) / 2
        } else {
            _state.update { it.copy(error = "Set clip end before splitting") }
            return
        }
        val replacement = listOf(clip.copy(endMs = splitAt), clip.copy(id = java.util.UUID.randomUUID().toString(), startMs = splitAt))
        mutate(kind) { it.take(index) + replacement + it.drop(index + 1) }
    }

    fun move(kind: AudioTrackKind, index: Int, offset: Int) {
        mutate(kind) { source ->
            val target = index + offset
            if (index !in source.indices || target !in source.indices) return@mutate source
            source.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun delete(kind: AudioTrackKind, id: String) = mutate(kind) { it.filterNot { clip -> clip.id == id } }

    fun render() = viewModelScope.launch {
        val project = _state.value.project
        _state.update { it.copy(rendering = true, error = null) }
        runCatching {
            val root = File(context.filesDir, "audiobooks/$audiobookId")
            val voice = FFmpegBridge.renderEditedTrack(context, project.voiceClips, File(root, "edited-voice.wav"))
            if (project.musicClips.isEmpty()) {
                voice
            } else {
                val music = FFmpegBridge.renderEditedTrack(context, project.musicClips, File(root, "edited-music.wav"))
                FFmpegBridge.applyMusicDucking(
                    context,
                    voice,
                    music,
                    File(root, "edited-final.m4a"),
                    format = "m4a",
                )
            }
        }.onSuccess { output ->
            _state.update { it.copy(rendering = false, outputPath = output.absolutePath) }
        }.onFailure { error ->
            _state.update { it.copy(rendering = false, error = error.message) }
        }
    }

    fun save() = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null) }
        runCatching { persistTimeline() }
            .onSuccess {
                _state.update { it.copy(saving = false, savedAt = System.currentTimeMillis()) }
            }
            .onFailure { error -> _state.update { it.copy(saving = false, error = error.message) } }
    }

    fun exportMp3() = viewModelScope.launch {
        _state.update { it.copy(rendering = true, error = null) }
        runCatching {
            persistTimeline()
            val project = _state.value.project
            val root = File(context.filesDir, "audiobooks/$audiobookId")
            val voice = FFmpegBridge.renderTimelineTrack(
                context, project.voiceClips, File(root, "timeline-voice.wav"), project.voiceVolumeDb,
            )
            val music = project.musicClips.takeIf { it.isNotEmpty() }?.let {
                FFmpegBridge.renderTimelineTrack(
                    context, it, File(root, "timeline-music.wav"), project.musicVolumeDb,
                )
            }
            val sound = project.soundClips.takeIf { it.isNotEmpty() }?.let {
                FFmpegBridge.renderTimelineTrack(
                    context, it, File(root, "timeline-sound.wav"), project.soundVolumeDb,
                )
            }
            val local = FFmpegBridge.mixProduction(
                context, voice, music, sound, File(root, "chapter-export.mp3"), "mp3",
            )
            exportToProjectFolder(local)
        }.onSuccess { uri ->
            _state.update { it.copy(rendering = false, outputPath = uri) }
        }.onFailure { error ->
            _state.update { it.copy(rendering = false, error = error.message) }
        }
    }

    private fun clips(kind: AudioTrackKind): List<AudioEditClip> = when (kind) {
        AudioTrackKind.Voice -> _state.value.project.voiceClips
        AudioTrackKind.Music -> _state.value.project.musicClips
        AudioTrackKind.Sound -> _state.value.project.soundClips
    }

    private fun mutate(kind: AudioTrackKind, transform: (List<AudioEditClip>) -> List<AudioEditClip>) {
        _state.update {
            val project = when (kind) {
                AudioTrackKind.Voice -> it.project.copy(voiceClips = transform(it.project.voiceClips))
                AudioTrackKind.Music -> it.project.copy(musicClips = transform(it.project.musicClips))
                AudioTrackKind.Sound -> it.project.copy(soundClips = transform(it.project.soundClips))
            }
            it.copy(project = project, error = null)
        }
    }

    private fun updateClip(kind: AudioTrackKind, id: String, transform: (AudioEditClip) -> AudioEditClip) {
        mutate(kind) { clips -> clips.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun loadTimeline() {
        val tracks = db.audioTimeline().tracks(audiobookId)
        if (tracks.isNotEmpty()) {
            suspend fun load(type: AudioTrackKind): List<AudioEditClip> {
                val track = tracks.firstOrNull { it.type == type.name.uppercase() } ?: return emptyList()
                return db.audioTimeline().clips(track.id).map(::toEditClip)
            }
            _state.update {
                it.copy(
                    project = AudioEditProject(
                        voiceClips = load(AudioTrackKind.Voice),
                        musicClips = load(AudioTrackKind.Music),
                        soundClips = load(AudioTrackKind.Sound),
                    ),
                )
            }
            return
        }
        val segments = db.segments().listForAudiobook(audiobookId)
        var cursor = 0L
        val voiceClips = segments.mapNotNull { segment ->
            val path = segment.audioPath?.takeIf { File(it).isFile } ?: return@mapNotNull null
            cursor += segment.pauseBeforeMs
            AudioEditClip(
                sourcePath = path,
                timelineStartMs = cursor,
                endMs = segment.durationMs.toLong(),
            ).also {
                cursor += segment.durationMs + segment.pauseAfterMs
            }
        }
        _state.update { it.copy(project = it.project.copy(voiceClips = voiceClips)) }
    }

    private suspend fun persistTimeline() {
        val now = System.currentTimeMillis()
        val tracks = AudioTrackKind.entries.mapIndexed { index, kind ->
            AudioTrackEntity(
                id = "$audiobookId-${kind.name.lowercase()}",
                audiobookId = audiobookId,
                type = kind.name.uppercase(),
                title = kind.name,
                orderIndex = index,
                volumeDb = when (kind) {
                    AudioTrackKind.Voice -> _state.value.project.voiceVolumeDb.toFloat()
                    AudioTrackKind.Music -> _state.value.project.musicVolumeDb.toFloat()
                    AudioTrackKind.Sound -> _state.value.project.soundVolumeDb.toFloat()
                },
                updatedAt = now,
            )
        }
        val clips = tracks.flatMap { track ->
            val kind = when (track.type) {
                "VOICE" -> AudioTrackKind.Voice
                "MUSIC" -> AudioTrackKind.Music
                else -> AudioTrackKind.Sound
            }
            clips(kind).map { clip ->
                AudioClipEntity(
                    id = clip.id,
                    trackId = track.id,
                    sourcePath = clip.sourcePath,
                    timelineStartMs = clip.timelineStartMs,
                    sourceStartMs = clip.startMs,
                    sourceEndMs = clip.endMs,
                    gainDb = clip.gainDb.toFloat(),
                    speed = clip.speed.toFloat(),
                    fadeInMs = clip.fadeInMs,
                    fadeOutMs = clip.fadeOutMs,
                    loop = clip.loop,
                    locked = clip.locked,
                    markupTagId = clip.markupTagId,
                    updatedAt = now,
                )
            }
        }
        db.audioTimeline().deleteTimeline(audiobookId)
        db.audioTimeline().upsertTracks(tracks)
        db.audioTimeline().upsertClips(clips)
    }

    private fun toEditClip(value: AudioClipEntity) = AudioEditClip(
        id = value.id,
        sourcePath = value.sourcePath,
        timelineStartMs = value.timelineStartMs,
        startMs = value.sourceStartMs,
        endMs = value.sourceEndMs,
        gainDb = value.gainDb.toDouble(),
        speed = value.speed.toDouble(),
        fadeInMs = value.fadeInMs,
        fadeOutMs = value.fadeOutMs,
        loop = value.loop,
        locked = value.locked,
        markupTagId = value.markupTagId,
    )

    private suspend fun exportToProjectFolder(source: File): String {
        val audiobook = db.audiobooks().byId(audiobookId) ?: error("Chapter not found")
        val project = db.projects().byId(audiobook.projectId) ?: error("Project not found")
        require(project.outputTreeUri.isNotBlank()) { "Choose a project folder first" }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(project.outputTreeUri))
            ?.takeIf { it.canWrite() }
            ?: error("Project folder is unavailable")
        val exports = root.findFile("exports") ?: root.createDirectory("exports")
            ?: error("Cannot create exports folder")
        val safeName = audiobook.title.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "Chapter" }
        var name = "$safeName.mp3"
        var suffix = 2
        while (exports.findFile(name) != null) name = "$safeName ($suffix).mp3".also { suffix++ }
        val target = exports.createFile("audio/mpeg", name) ?: error("Cannot create export file")
        context.contentResolver.openOutputStream(target.uri, "w").use { output ->
            requireNotNull(output) { "Cannot open export file" }
            source.inputStream().use { it.copyTo(output, 128 * 1024) }
        }
        db.chapterExports().insert(
            ChapterExportEntity(
                audiobookId = audiobookId,
                displayName = name,
                documentUri = target.uri.toString(),
                format = "mp3",
                bitrate = "192k",
            ),
        )
        return target.uri.toString()
    }
}

class AudioEditorViewModelFactory(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AudioEditorViewModel(context, audiobookId) as T
}
