package com.t2v.ui.screens.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
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
import com.t2v.app.AppContainer
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.t2v.core.audio.AudioMixSettings
import com.t2v.core.audio.FFmpegBridge
import com.t2v.ui.components.LTVScaffold
import com.t2v.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MusicMixScreen(
    nav: NavController,
    audiobookId: Long,
    vm: MusicMixViewModel = viewModel(factory = MusicMixViewModelFactory(LocalContext.current, audiobookId)),
    windowSizeClass: WindowSizeClass? = null,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::importMusic)
    }

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.mix_title),
        onBack = { nav.popBackStack() },
        windowSizeClass = windowSizeClass,
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Voice: ${state.voicePath ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("Music: ${state.musicPath ?: "— (none)"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = { musicPicker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRendering,
            ) {
                Text("Choose background music")
            }
            if (state.musicPath != null) {
                Button(
                    onClick = vm::removeMusic,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRendering,
                ) {
                    Text("Remove background music")
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Text("Voice volume: ${state.settings.voiceVolumeDb.toInt()} dB")
            Slider(
                value = state.settings.voiceVolumeDb.toFloat(),
                onValueChange = { vm.setVoiceVolume(it.toDouble()) },
                valueRange = -20f..6f,
            )
            Text("Music volume: ${state.settings.musicVolumeDb.toInt()} dB")
            Slider(
                value = state.settings.musicVolumeDb.toFloat(),
                onValueChange = { vm.setMusicVolume(it.toDouble()) },
                valueRange = -30f..0f,
            )
            Text("Ducking: ${state.settings.duckingDb.toInt()} dB")
            Slider(
                value = state.settings.duckingDb.toFloat(),
                onValueChange = { vm.setDucking(it.toDouble()) },
                valueRange = -20f..0f,
            )
            Button(
                onClick = { scope.launch { vm.render(context) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRendering,
            ) {
                Text(stringResource(R.string.mix_render))
            }
            OutlinedButton(
                onClick = { nav.navigate(Routes.audioEditor(audiobookId)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRendering,
            ) {
                Text("Open multitrack audio editor")
            }
            if (state.outputPath != null) {
                Text("✓ ${state.outputPath}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

data class MusicMixState(
    val voicePath: String? = null,
    val musicPath: String? = null,
    val settings: AudioMixSettings = AudioMixSettings(voicePath = ""),
    val outputPath: String? = null,
    val isRendering: Boolean = false,
    val error: String? = null,
)

class MusicMixViewModel(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val _state = MutableStateFlow(MusicMixState())
    val state: StateFlow<MusicMixState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            val audiobook = db.audiobooks().byId(audiobookId)
            _state.update {
                it.copy(
                    voicePath = audiobook?.outputPath,
                    settings = it.settings.copy(voicePath = audiobook?.outputPath.orEmpty()),
                )
            }
        }
    }
    fun setVoiceVolume(db: Double) = _state.update { it.copy(settings = it.settings.copy(voiceVolumeDb = db)) }
    fun setMusicVolume(db: Double) = _state.update { it.copy(settings = it.settings.copy(musicVolumeDb = db)) }
    fun setDucking(db: Double) = _state.update { it.copy(settings = it.settings.copy(duckingDb = db)) }
    fun importMusic(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val destination = File(context.filesDir, "audiobooks/$audiobookId/background_music")
                destination.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                } ?: error("Cannot read selected music")
                destination
            }.onSuccess { file ->
                _state.update {
                    it.copy(
                        musicPath = file.absolutePath,
                        settings = it.settings.copy(musicPath = file.absolutePath),
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
        }
    }
    fun removeMusic() {
        _state.value.musicPath?.let(::File)?.delete()
        _state.update {
            it.copy(musicPath = null, settings = it.settings.copy(musicPath = null))
        }
    }
    suspend fun render(context: android.content.Context) {
        val s = _state.value
        val voiceFile = s.voicePath?.let { File(it) } ?: return
        if (!voiceFile.exists()) return
        _state.update { it.copy(isRendering = true) }
        val outFile = File(context.filesDir, "audiobooks/$audiobookId/mix.m4a")
        outFile.parentFile?.mkdirs()
        if (s.musicPath == null) {
            FFmpegBridge.encode(context, voiceFile, outFile, "m4a")
        } else {
            FFmpegBridge.applyMusicDucking(
                context = context,
                voice = voiceFile,
                music = File(s.musicPath),
                output = outFile,
                voiceVolumeDb = s.settings.voiceVolumeDb,
                musicVolumeDb = s.settings.musicVolumeDb,
                duckingDb = s.settings.duckingDb,
                format = "m4a",
            )
        }
        _state.update { it.copy(outputPath = outFile.absolutePath, isRendering = false) }
    }
}

class MusicMixViewModelFactory(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MusicMixViewModel(context, audiobookId) as T
}
