package com.t2v.ui.screens.voices

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.t2v.data.SettingsRepository
import com.t2v.tts.VoiceInfo
import com.t2v.tts.engines.ElevenLabsTtsEngine
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VoicesScreen(
    nav: NavController,
    vm: VoicesViewModel = viewModel(factory = VoicesViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneName by remember { mutableStateOf("") }
    var cloneAudioUri by remember { mutableStateOf<Uri?>(null) }
    var consent by remember { mutableStateOf(false) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        cloneAudioUri = uri
    }
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_voices),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { showCloneDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Клонировать голос")
            }
            Text(
                "Русское клонирование доступно через ElevenLabs после добавления API-ключа. " +
                    "Локальные Piper-голоса скачиваются на экране «Модели».",
                style = MaterialTheme.typography.bodySmall,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            if (state.cloning) CircularProgressIndicator()

            var selectedLanguage by rememberSaveable { mutableStateOf("all") }
            val languages = remember(state.byEngine) {
                state.byEngine.values.flatten()
                    .map { it.language }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            }
            LanguageFilterRow(
                selectedLanguage = selectedLanguage,
                languages = languages,
                onSelect = { selectedLanguage = it },
            )
            val filtered = if (selectedLanguage == "all") {
                state.byEngine
            } else {
                state.byEngine.mapValues { (_, voices) ->
                    voices.filter { it.language == selectedLanguage }
                }.filterValues { it.isNotEmpty() }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { (engineId, voices) ->
                    item(key = engineId) {
                        Text("$engineId (${voices.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    voices.forEach { v ->
                        item(key = "${engineId}-${v.id}") {
                            VoiceCard(
                                voice = v,
                                selected = state.selectedVoiceId == v.id,
                                onSelect = { vm.selectVoice(v) },
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Для выбранного языка нет голосов. Скачайте локальный голос на экране «Модели».",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
    if (showCloneDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.cloning) showCloneDialog = false },
            title = { Text("Клонировать голос") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Выберите чистую запись русской речи. Рекомендуется 30–120 секунд без музыки и шума.",
                    )
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = { cloneName = it },
                        label = { Text("Название голоса") },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                        Text(if (cloneAudioUri == null) "Выбрать аудиозапись" else "Аудиозапись выбрана")
                    }
                    Row {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "Я подтверждаю, что это мой голос или у меня есть явное разрешение владельца.",
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Text(
                        "Запись будет отправлена в ElevenLabs. Функция требует API-ключ и может зависеть от тарифа.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.cloning && cloneName.isNotBlank() && cloneAudioUri != null && consent,
                    onClick = {
                        vm.cloneElevenLabsVoice(cloneName, requireNotNull(cloneAudioUri)) {
                            showCloneDialog = false
                            cloneName = ""
                            cloneAudioUri = null
                            consent = false
                        }
                    },
                ) {
                    Text("Создать клон")
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !state.cloning,
                    onClick = { showCloneDialog = false },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterRow(
    selectedLanguage: String,
    languages: List<String>,
    onSelect: (String) -> Unit,
) {
    if (languages.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = if (selectedLanguage == "all") "Все языки" else selectedLanguage,
            onValueChange = {},
            readOnly = true,
            label = { Text("Язык") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Все языки") },
                onClick = {
                    onSelect("all")
                    expanded = false
                },
            )
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language) },
                    onClick = {
                        onSelect(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: VoiceInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(voice.displayName.ifBlank { voice.id }, style = MaterialTheme.typography.titleSmall)
            Text("${voice.language} · ${voice.gender}", style = MaterialTheme.typography.bodySmall)
            if (voice.previewUrl != null) Text("preview available", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelect, enabled = !selected) {
                    Text(if (selected) "Selected" else "Select")
                }
            }
        }
    }
}

data class VoicesState(
    val byEngine: Map<String, List<VoiceInfo>> = emptyMap(),
    val loading: Boolean = false,
    val selectedVoiceId: String = "",
    val cloning: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class VoicesViewModel(private val context: android.content.Context) : ViewModel() {
    private val registry = AppContainer.registry(context)
    private val settings = AppContainer.settings(context)
    private val _state = MutableStateFlow(VoicesState())
    val state: StateFlow<VoicesState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                _state.update { it.copy(selectedVoiceId = value.voiceId) }
            }
        }
        loadVoices()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val all = registry.allEngineInfos()
            val map = mutableMapOf<String, List<VoiceInfo>>()
            for (e in all) {
                runCatching { registry.get(e.id).listVoices() }.onSuccess { map[e.id] = it }
            }
            _state.update {
                it.copy(
                    byEngine = map,
                    loading = false,
                )
            }
        }
    }

    fun selectVoice(voice: VoiceInfo) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.VOICE_ID] = voice.id
                it[SettingsRepository.Keys.TTS_ENGINE] = voice.engineId
            }
        }
    }

    fun cloneElevenLabsVoice(name: String, audioUri: Uri, onSuccess: () -> Unit) {
        if (_state.value.cloning) return
        viewModelScope.launch {
            _state.update { it.copy(cloning = true, error = null, message = null) }
            runCatching {
                val engine = registry.get("elevenlabs") as? ElevenLabsTtsEngine
                    ?: error("Сначала добавьте ElevenLabs API-ключ в Настройках")
                val audioFile = copyAudioToCache(context, audioUri)
                try {
                    engine.cloneVoice(
                        name = name,
                        audioFile = audioFile,
                        mimeType = context.contentResolver.getType(audioUri) ?: "audio/mpeg",
                    )
                } finally {
                    audioFile.delete()
                }
            }.onSuccess { voiceId ->
                settings.update {
                    it[SettingsRepository.Keys.VOICE_ID] = voiceId
                    it[SettingsRepository.Keys.TTS_ENGINE] = "elevenlabs"
                }
                _state.update {
                    it.copy(
                        cloning = false,
                        selectedVoiceId = voiceId,
                        message = "Голос успешно клонирован и выбран",
                    )
                }
                onSuccess()
                loadVoices()
            }.onFailure { error ->
                _state.update {
                    it.copy(cloning = false, error = error.message ?: "Не удалось клонировать голос")
                }
            }
        }
    }

    private fun copyAudioToCache(context: Context, uri: Uri): File {
        val file = File.createTempFile("voice-sample-", ".audio", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть аудиозапись" }
            file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
        }
        return file
    }
}

class VoicesViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VoicesViewModel(context) as T
}
