package com.t2v.ui.screens.models

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.t2v.core.model.GenerationModelCatalog
import com.t2v.data.SettingsRepository
import com.t2v.server.HuggingFaceRepository
import com.t2v.tts.catalog.RussianVoiceInstaller
import com.t2v.tts.engines.PiperRussianTtsEngine
import com.t2v.ui.components.LTVScaffold
import com.t2v.ui.components.TagInfoDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun ModelsScreen(
    nav: NavController,
    vm: ModelsViewModel = viewModel(factory = ModelsViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableStateOf(ModelTab.Voice) }
    var infoTarget by remember { mutableStateOf<InfoTarget?>(null) }
    infoTarget?.let { target ->
        TagInfoDialog(
            title = target.title,
            tagline = target.tagline,
            tags = target.tags,
            runtimeLabel = target.runtime,
            repository = target.repository,
            license = target.license,
            categoryLabel = target.categoryLabel,
            onDismiss = { infoTarget = null },
        )
    }
    val context = LocalContext.current
    val infoCategoryLocalLabel = stringResource(R.string.info_category_local)
    val infoCategoryCloudLabel = stringResource(R.string.info_category_cloud)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setModelsFolder(uri.toString())
        }
    }

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_models),
        onBack = { nav.popBackStack() },
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
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Папка моделей на устройстве", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.modelsTreeUri.ifBlank { "Внутреннее хранилище приложения" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text("Сменить папку")
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ModelTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }

            if (selectedTab == ModelTab.Voice) {
                VoiceModelSection(
                    state = state,
                    vm = vm,
                    infoCategoryLocalLabel = infoCategoryLocalLabel,
                    onInfo = { target -> infoTarget = target },
                )
            }

            if (selectedTab == ModelTab.Music) {
                ModelDetailCard(
                    title = "Локальный синтезатор (музыка)",
                    status = "Процедурный синтез • до 11 секунд • ничего скачивать не нужно",
                    selected = state.selectedMusicModelId == MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL,
                    enabled = state.liteRtMusicReady,
                    tags = GenerationModelCatalog.tagDocsFor("stable-audio-open-small"),
                    onSelect = { vm.selectMusicModel(MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Локальный синтезатор (музыка)",
                            tagline = GenerationModelCatalog.tagDocsFor("stable-audio-open-small")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("stable-audio-open-small"),
                            runtime = "LiteRT (процедурный, без модели)",
                            repository = "",
                            license = "Процедурный синтез T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
                DownloadableModelCard(
                    catalogId = "musicgen-small",
                    title = "MusicGen-small (он-девайс музыка)",
                    status = "AI-генерация музыки • LiteRT • ~422 МБ • нужен ARM64 + 3 ГБ RAM",
                    tags = GenerationModelCatalog.tagDocsFor("musicgen-small"),
                    selected = state.selectedMusicModelId == "musicgen-small",
                    enabled = true,
                    installable = GenerationModelCatalog.entries
                        .firstOrNull { it.id == "musicgen-small" }?.canInstall == true,
                    state = state,
                    vm = vm,
                    infoRepository = GenerationModelCatalog.repositoryFor("musicgen-small"),
                    infoLicense = GenerationModelCatalog.licenseFor("musicgen-small"),
                    infoRuntime = "LiteRT",
                    infoCategoryLabel = infoCategoryLocalLabel,
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "MusicGen-small (он-девайс музыка)",
                            tagline = GenerationModelCatalog.tagDocsFor("musicgen-small")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("musicgen-small"),
                            runtime = "LiteRT",
                            repository = GenerationModelCatalog.repositoryFor("musicgen-small"),
                            license = GenerationModelCatalog.licenseFor("musicgen-small"),
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "ElevenLabs Sound Effects (облако)",
                    status = "Нужен API-ключ ElevenLabs • длительность 1-22 секунды",
                    selected = state.selectedMusicModelId == SOUND_MODEL_ELEVEN_SFX,
                    enabled = state.elevenLabsKeyConfigured,
                    tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                    onSelect = { vm.selectMusicModel(SOUND_MODEL_ELEVEN_SFX) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "ElevenLabs Sound Effects (облако)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                            runtime = "ElevenLabs Sound Effects API",
                            repository = "https://api.elevenlabs.io/v1/sound-generation",
                            license = "Условия ElevenLabs",
                            categoryLabel = infoCategoryCloudLabel,
                        )
                    },
                )
            }

            if (selectedTab == ModelTab.Sound) {
                ModelDetailCard(
                    title = "Локальный синтезатор (звуки)",
                    status = "Процедурный синтез • до 5 секунд • ничего скачивать не нужно",
                    selected = state.selectedSoundModelId == SOUND_MODEL_STABLE_AUDIO_CLIP,
                    enabled = state.liteRtSoundReady,
                    tags = GenerationModelCatalog.tagDocsFor("stable-audio-clip"),
                    onSelect = { vm.selectSoundModel(SOUND_MODEL_STABLE_AUDIO_CLIP) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "Локальный синтезатор (звуки)",
                            tagline = GenerationModelCatalog.tagDocsFor("stable-audio-clip")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("stable-audio-clip"),
                            runtime = "LiteRT (процедурный, без модели)",
                            repository = "",
                            license = "Процедурный синтез T2V",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    },
                )
                ModelDetailCard(
                    title = "ElevenLabs Sound Effects (облако)",
                    status = "Нужен API-ключ ElevenLabs • длительность 1-22 секунды",
                    selected = state.selectedSoundModelId == SOUND_MODEL_ELEVEN_SFX,
                    enabled = state.elevenLabsKeyConfigured,
                    tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                    onSelect = { vm.selectSoundModel(SOUND_MODEL_ELEVEN_SFX) },
                    onInfo = {
                        infoTarget = InfoTarget(
                            title = "ElevenLabs Sound Effects (облако)",
                            tagline = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForGenerator("elevenlabs.sound"),
                            runtime = "ElevenLabs Sound Effects API",
                            repository = "https://api.elevenlabs.io/v1/sound-generation",
                            license = "Условия ElevenLabs",
                            categoryLabel = infoCategoryCloudLabel,
                        )
                    },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Только Android-совместимые модели", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "T2V показывает модель только после того, как её точные файлы, рантайм и ревизия " +
                            "прошли смоук-тест синтеза на реальном Android-устройстве. Серверные модели не поддерживаются.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Проверены Kokoro и русские Piper/VITS-модели из официального каталога sherpa-onnx.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.installed.isNotEmpty()) {
                Text(
                    "${stringResource(R.string.models_installed)} (${state.installed.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.installed.forEach { model ->
                    InstalledModelCard(
                        model = model,
                        selected = state.selectedModelId == model.id,
                        onSelect = { vm.selectModel(model.id) },
                        onDelete = { vm.deleteModel(model.id) },
                    )
                }
            }
        }
    }
}

data class InfoTarget(
    val title: String,
    val tagline: String?,
    val tags: com.t2v.core.model.GenerationModelCatalog.TagDocs?,
    val runtime: String? = null,
    val repository: String? = null,
    val license: String? = null,
    val categoryLabel: String? = null,
)

enum class ModelTab(val title: String) {
    Voice("Голос"),
    Music("Музыка"),
    Sound("Звуки"),
}

/**
 * Detail card used by every model/generator/engine entry. Shows:
 *   - human-readable title + status
 *   - tagline from TagDocs
 *   - supported / partial / ignored tag bullets
 *   - one or two usage examples in monospace
 *   - select button
 */
@Composable
fun ModelDetailCard(
    title: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    tags: com.t2v.core.model.GenerationModelCatalog.TagDocs?,
    onSelect: () -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onInfo != null) {
                    IconButton(onClick = { onInfo() }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_open),
                        )
                    }
                }
            }
            Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            tags?.let { docs ->
                Text(docs.tagline, style = MaterialTheme.typography.bodySmall)
                if (docs.supported.isNotEmpty()) {
                    Text("Supported tags:", style = MaterialTheme.typography.labelMedium)
                    docs.supported.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.partial.isNotEmpty()) {
                    Text("Partial support (approximated):", style = MaterialTheme.typography.labelMedium)
                    docs.partial.forEach { Text("~ $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.ignored.isNotEmpty()) {
                    Text("Ignored / dropped:", style = MaterialTheme.typography.labelMedium)
                    docs.ignored.forEach { Text("x $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.examples.isNotEmpty()) {
                    Text("Examples:", style = MaterialTheme.typography.labelMedium)
                    docs.examples.forEach { Text("  $it", style = MaterialTheme.typography.bodySmall) }
                }
                docs.promptHelp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = onSelect, enabled = enabled && !selected) {
                Text(
                    when {
                        selected -> "Selected"
                        enabled -> "Select"
                        else -> "Runtime not ready"
                    },
                )
            }
        }
    }
}

/**
 * Card that shows a single [com.t2v.core.model.GenerationModelCatalog.Entry]
 * alongside a "Download from Hugging Face" flow.
 *
 * Renders the same metadata as [ModelDetailCard] but adds:
 *  - a "Download (size)" button when the entry's repository is a real HF
 *    model (i.e. `author/name`) and we don't already have it installed;
 *  - a progress bar + cancel button while the download is in flight;
 *  - a "Select" / "Selected" button that only becomes enabled after the
 *    download finished.
 *
 * The actual install goes through [com.t2v.server.HuggingFaceRepository],
 * which is the same client Kokoro already uses. For catalog entries that
 * aren't actually backed by a downloadable HF repo (e.g. procedural music
 * synth, ElevenLabs SFX, experimental entries) [downloadableRepository] is
 * `false` and the card falls back to the old [ModelDetailCard] flow.
 */
@Composable
fun DownloadableModelCard(
    catalogId: String,
    title: String,
    status: String,
    tags: com.t2v.core.model.GenerationModelCatalog.TagDocs?,
    selected: Boolean,
    enabled: Boolean,
    installable: Boolean = true,
    state: ModelsState,
    vm: ModelsViewModel,
    infoRepository: String? = null,
    infoLicense: String? = null,
    infoRuntime: String? = null,
    infoCategoryLabel: String? = null,
    onInfo: (() -> Unit)? = null,
) {
    val isThisDownloading = state.downloadingCatalogId == catalogId
    val isInstalled = state.isInstalled(catalogId, infoRepository.orEmpty())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                if (onInfo != null) {
                    IconButton(onClick = { onInfo() }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_open),
                        )
                    }
                }
            }
            tags?.let { docs ->
                Text(docs.tagline, style = MaterialTheme.typography.bodySmall)
                if (docs.supported.isNotEmpty()) {
                    Text("Supported tags:", style = MaterialTheme.typography.labelMedium)
                    docs.supported.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.partial.isNotEmpty()) {
                    Text("Partial support (approximated):", style = MaterialTheme.typography.labelMedium)
                    docs.partial.forEach { Text("~ $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.ignored.isNotEmpty()) {
                    Text("Ignored / dropped:", style = MaterialTheme.typography.labelMedium)
                    docs.ignored.forEach { Text("x $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (docs.examples.isNotEmpty()) {
                    Text("Examples:", style = MaterialTheme.typography.labelMedium)
                    docs.examples.forEach { Text("  $it", style = MaterialTheme.typography.bodySmall) }
                }
                docs.promptHelp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            when {
                isThisDownloading -> {
                    if (state.catalogDownloadTotalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { state.catalogDownloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(downloadProgressText(state.catalogDownloadedBytes, state.catalogDownloadTotalBytes))
                    OutlinedButton(onClick = vm::cancelDownload) {
                        Text(stringResource(R.string.models_cancel_download))
                    }
                }
                isInstalled -> {
                    OutlinedButton(onClick = onSelectSafe(vm, catalogId), enabled = !selected) {
                        Text(if (selected) "Selected" else "Select")
                    }
                }
                installable -> {
                    Button(
                        enabled = enabled,
                        onClick = { vm.downloadModelFromCatalog(catalogId) },
                    ) {
                        Text(stringResource(R.string.models_download_button))
                    }
                }
            }
        }
    }
}

/**
 * Trivial helper that picks the right `selectXxxModel` method based on the
 * catalog id's category. Returns a no-op lambda when no mapping exists.
 */
private fun onSelectSafe(vm: ModelsViewModel, catalogId: String): () -> Unit = {
    val entry = com.t2v.core.model.GenerationModelCatalog.entries
        .firstOrNull { it.id == catalogId }
    when {
        entry == null -> { }
        com.t2v.core.model.GenerationModelCatalog.Category.Voice in entry.categories -> {
            vm.selectVoiceModel(catalogId)
        }
        com.t2v.core.model.GenerationModelCatalog.Category.Music in entry.categories -> {
            vm.selectMusicModel(catalogId)
        }
        com.t2v.core.model.GenerationModelCatalog.Category.Sound in entry.categories -> {
            vm.selectSoundModel(catalogId)
        }
    }
}


@Composable
private fun VoiceModelSection(
    state: ModelsState,
    vm: ModelsViewModel,
    infoCategoryLocalLabel: String,
    onInfo: (InfoTarget) -> Unit,
) {
    var selectedLanguage by rememberSaveable { mutableStateOf(ALL_LANGUAGES) }

    Text(
        text = "Доступные локальные голосовые модели",
        style = MaterialTheme.typography.titleMedium,
    )
    LanguageFilterDropdown(
        selectedLanguage = selectedLanguage,
        onSelect = { selectedLanguage = it },
    )

    val showKokoro = selectedLanguage == ALL_LANGUAGES || KOKORO_LANGUAGES.contains(selectedLanguage)
    if (showKokoro) {
        KokoroCard(
            state = state,
            vm = vm,
            infoCategoryLocalLabel = infoCategoryLocalLabel,
            onInfo = onInfo,
        )
    }

    val piperVoices = if (selectedLanguage == ALL_LANGUAGES) {
        PiperRussianTtsEngine.RUSSIAN_VOICES
    } else {
        PiperRussianTtsEngine.RUSSIAN_VOICES.filter { it.language == selectedLanguage }
    }

    Text(
        text = "Локальные Piper/VITS модели",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "ONNX • полностью на телефоне • общий runtime устанавливать отдельно не нужно",
        style = MaterialTheme.typography.bodySmall,
    )

    val groupedLanguages = piperVoices
        .groupBy { it.language }
        .toSortedMap()
    val languageLabels = piperLanguageLabels()
    groupedLanguages.forEach { (language, voices) ->
        val header = languageLabels[language]
            ?: "Язык $language (SherpaOnnx + VITS medium)"
        PiperVoiceGroup(
            header = header,
            voices = voices,
            state = state,
            vm = vm,
            infoCategoryLocalLabel = infoCategoryLocalLabel,
            onInfo = onInfo,
        )
    }
}

private const val ALL_LANGUAGES = "all"

private val KOKORO_LANGUAGES = setOf("en-US", "en-GB")

/** Языки, доступные в выпадающем фильтре: Piper + английский (Kokoro). */
private fun voiceFilterLanguages(): List<String> {
    val all = buildSet {
        addAll(PiperRussianTtsEngine.RUSSIAN_VOICES.map { it.language })
        addAll(KOKORO_LANGUAGES)
    }
    return all.sorted()
}

/** Человекочитаемые подписи языков для фильтра и заголовков групп. */
private fun piperLanguageLabels(): Map<String, String> = mapOf(
    "ru-RU" to "Русские голоса (SherpaOnnx + VITS medium)",
    "en-US" to "Английский (en-US, SherpaOnnx + VITS medium/low)",
    "en-GB" to "Английский (en-GB, SherpaOnnx + VITS medium)",
    "de-DE" to "Немецкие голоса (de-DE, SherpaOnnx + VITS)",
    "de-AT" to "Немецкий (de-AT, SherpaOnnx + VITS medium)",
    "fr-FR" to "Французские голоса (SherpaOnnx + VITS medium)",
    "es-ES" to "Испанские голоса (es-ES, SherpaOnnx + VITS)",
    "es-MX" to "Испанский (es-MX, SherpaOnnx + VITS medium)",
    "it-IT" to "Итальянский (it-IT, SherpaOnnx + VITS)",
    "zh-CN" to "Китайский (zh-CN, SherpaOnnx + VITS medium)",
    "ja-JP" to "Японский (ja-JP, SherpaOnnx + VITS medium)",
    "hi-IN" to "Хинди (hi-IN, SherpaOnnx + VITS medium)",
    "bn-IN" to "Бенгальский (bn-IN, SherpaOnnx + VITS medium)",
    "ar" to "Арабский (ar, SherpaOnnx + VITS medium)",
    "ko-KR" to "Корейский (ko-KR, SherpaOnnx + VITS medium)",
)

/** Короткое название языка для пунктов выпадающего списка. */
private fun languageShortName(language: String): String = when (language) {
    "ru-RU" -> "Русский (ru)"
    "en-US" -> "Английский (en-US)"
    "en-GB" -> "Английский (en-GB)"
    "de-DE" -> "Немецкий (de-DE)"
    "de-AT" -> "Немецкий (de-AT)"
    "fr-FR" -> "Французский (fr-FR)"
    "es-ES" -> "Испанский (es-ES)"
    "es-MX" -> "Испанский (es-MX)"
    "it-IT" -> "Итальянский (it-IT)"
    "zh-CN" -> "Китайский (zh-CN)"
    "ja-JP" -> "Японский (ja-JP)"
    "hi-IN" -> "Хинди (hi-IN)"
    "bn-IN" -> "Бенгальский (bn-IN)"
    "ar" -> "Арабский (ar)"
    "ko-KR" -> "Корейский (ko-KR)"
    else -> language
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterDropdown(
    selectedLanguage: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = if (selectedLanguage == ALL_LANGUAGES) {
                "Все языки"
            } else {
                languageShortName(selectedLanguage)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Язык голоса") },
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
                    onSelect(ALL_LANGUAGES)
                    expanded = false
                },
            )
            voiceFilterLanguages().forEach { language ->
                DropdownMenuItem(
                    text = { Text(languageShortName(language)) },
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
private fun KokoroCard(
    state: ModelsState,
    vm: ModelsViewModel,
    infoCategoryLocalLabel: String,
    onInfo: (InfoTarget) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kokoro 82M", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Английский • 11 голосов • ONNX • Apache-2.0 • целиком работает на телефоне",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = state.kokoroModel?.let { formatBytes(it.totalSizeBytes) } ?: "примерно 369 МБ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = {
                    onInfo(
                        InfoTarget(
                            title = "Kokoro 82M (англоязычный TTS, работает на устройстве)",
                            tagline = GenerationModelCatalog.tagDocsFor("kokoro-82m")?.tagline,
                            tags = GenerationModelCatalog.tagDocsFor("kokoro-82m"),
                            runtime = "SherpaOnnx (встроен)",
                            repository = GenerationModelCatalog.repositoryFor("kokoro-82m"),
                            license = GenerationModelCatalog.licenseFor("kokoro-82m"),
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    )
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.info_open),
                    )
                }
            }
            when {
                state.loadingCatalog -> CircularProgressIndicator()
                state.downloading -> {
                    if (state.downloadTotalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(downloadProgressText(state.downloadedBytes, state.downloadTotalBytes))
                    OutlinedButton(onClick = vm::cancelDownload) {
                        Text(stringResource(R.string.models_cancel_download))
                    }
                }
                state.kokoroInstalled -> {
                    ModelSelectionRow(
                        selected = state.selectedVoiceModelId == VOICE_MODEL_KOKORO,
                        onSelect = { vm.selectVoiceModel(VOICE_MODEL_KOKORO) },
                    )
                }
                else -> Button(
                    enabled = state.kokoroModel?.variants?.isNotEmpty() == true,
                    onClick = vm::downloadKokoro,
                ) {
                    Text("Скачать Kokoro")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PiperVoiceGroup(
    header: String,
    voices: List<PiperRussianTtsEngine.RussianVoice>,
    state: ModelsState,
    vm: ModelsViewModel,
    infoCategoryLocalLabel: String,
    onInfo: (InfoTarget) -> Unit,
) {
    if (voices.isEmpty()) return
    Text(
        text = header,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    voices.forEach { voice ->
        PiperVoiceCard(
            voice = voice,
            state = state,
            vm = vm,
            infoCategoryLocalLabel = infoCategoryLocalLabel,
            onInfo = onInfo,
        )
    }
}

@Composable
private fun PiperVoiceCard(
    voice: PiperRussianTtsEngine.RussianVoice,
    state: ModelsState,
    vm: ModelsViewModel,
    infoCategoryLocalLabel: String,
    onInfo: (InfoTarget) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(voice.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${voice.language} • ${
                            if (voice.gender == "female") "женский" else "мужской"
                        } • Piper medium • примерно ${formatBytes(voice.approximateSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = {
                    onInfo(
                        InfoTarget(
                            title = "Piper/VITS • ${voice.displayName}",
                            tagline = GenerationModelCatalog.tagDocsForEngine("piper_ru")?.tagline,
                            tags = GenerationModelCatalog.tagDocsForEngine("piper_ru"),
                            runtime = "SherpaOnnx (встроен)",
                            repository = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models",
                            license = "Model-specific (Piper/VITS)",
                            categoryLabel = infoCategoryLocalLabel,
                        )
                    )
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.info_open),
                    )
                }
            }
            when {
                state.downloadingVoiceId == voice.id -> {
                    if (state.downloadTotalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(downloadProgressText(state.downloadedBytes, state.downloadTotalBytes))
                    OutlinedButton(onClick = vm::cancelDownload) {
                        Text(stringResource(R.string.models_cancel_download))
                    }
                }
                voice.id in state.installedRussianVoices -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModelSelectionRow(
                            selected = state.selectedVoiceModelId == "piper:${voice.id}",
                            onSelect = { vm.selectVoiceModel("piper:${voice.id}") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { vm.deleteRussianVoice(voice.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
                else -> Button(
                    enabled = state.downloadingVoiceId == null && !state.downloading,
                    onClick = { vm.downloadRussianVoice(voice.id) },
                ) {
                    Text("Скачать голос")
                }
            }
        }
    }
}

@Composable
private fun LocalAudioModelCard(
    id: String,
    title: String,
    description: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Text(status, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = { onSelect(id) },
                enabled = enabled && !selected,
            ) {
                Text(
                    when {
                        selected -> "Выбрана"
                        enabled -> "Выбрать"
                        else -> "Runtime ещё не готов"
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionRow(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (selected) "Выбрана" else "Установлена",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (!selected) {
            OutlinedButton(onClick = onSelect) { Text("Выбрать") }
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: HuggingFaceRepository.InstalledModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.id, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${model.filesCount} файлов • ${formatBytes(model.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onSelect, enabled = !selected) {
                Text(if (selected) stringResource(R.string.models_active) else stringResource(R.string.models_select))
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.models_delete))
            }
        }
    }
}

data class ModelsState(
    val installed: List<HuggingFaceRepository.InstalledModel> = emptyList(),
    val selectedModelId: String = "",
    val liteRtMusicReady: Boolean = false,
    val liteRtSoundReady: Boolean = false,
    val elevenLabsKeyConfigured: Boolean = false,
    val selectedVoiceModelId: String = "",
    val selectedMusicModelId: String = "",
    val selectedSoundModelId: String = "",
    val modelsTreeUri: String = "",
    val installedRussianVoices: Set<String> = emptySet(),
    val downloadingVoiceId: String? = null,
    val kokoroModel: HuggingFaceRepository.Model? = null,
    val loadingCatalog: Boolean = true,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val downloadTotalBytes: Long = -1L,
    val error: String? = null,
    /**
     * Catalog id of the model whose download is currently in flight through
     * [ModelsViewModel.downloadModelFromCatalog]. `null` when no catalog
     * download is running.
     */
    val downloadingCatalogId: String? = null,
    /** Per-catalog progress for [downloadingCatalogId], in the range 0f..1f. */
    val catalogDownloadProgress: Float = 0f,
    val catalogDownloadedBytes: Long = 0L,
    val catalogDownloadTotalBytes: Long = -1L,
) {
    val kokoroInstalled: Boolean
        get() = installed.any { it.id == HuggingFaceRepository.KOKORO_REPOSITORY }

    /**
     * True when the catalog model with [catalogId] has been downloaded into
     * the Hugging Face cache (matched by its repository name, which is what
     * [HuggingFaceRepository.InstalledModel.id] reports).
     */
    fun isInstalled(catalogId: String, repository: String): Boolean {
        if (catalogId == "kokoro-82m") return kokoroInstalled
        return installed.any { it.id == repository || it.location.contains(repository) }
    }
}

class ModelsViewModel(private val context: android.content.Context) : ViewModel() {
    private val settings = AppContainer.settings(context)
    private val modelsRoot = File(context.filesDir, "models")
    private val russianInstaller = RussianVoiceInstaller(File(modelsRoot, "piper-ru"))
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()
    private var modelsTreeUri = ""
    private var huggingFaceToken = ""
    private var downloadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                modelsTreeUri = value.modelsTreeUri
                huggingFaceToken = value.engines["huggingface"]?.get("token").orEmpty()
                _state.update {
                    it.copy(
                        selectedModelId = value.selectedModelId,
                        selectedVoiceModelId = value.selectedVoiceModelId,
                        selectedMusicModelId = value.selectedMusicModelId,
                        selectedSoundModelId = value.selectedSoundModelId,
                        liteRtMusicReady = com.t2v.generators.GeneratorRegistry(
                            context,
                        ) { com.t2v.tts.registry.EngineRegistry.EngineSettings(value.engines) }
                            .forCategory(com.t2v.generators.GeneratorCategory.Music)
                            .any { it.id == "litert.stable-audio-open-small.music" },
                        liteRtSoundReady = com.t2v.generators.GeneratorRegistry(
                            context,
                        ) { com.t2v.tts.registry.EngineRegistry.EngineSettings(value.engines) }
                            .forCategory(com.t2v.generators.GeneratorCategory.Sound)
                            .any { it.id == "litert.stable-audio-clip.sound" },
                        elevenLabsKeyConfigured = value.engines["elevenlabs"]?.get("apiKey").orEmpty().isNotBlank(),
                        modelsTreeUri = value.modelsTreeUri,
                        installed = repository().installed(),
                        installedRussianVoices = installedRussianVoiceIds(),
                    )
                }
            }
        }
        loadKokoro()
    }

    private fun loadKokoro() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCatalog = true, error = null) }
            runCatching {
                repository().model(HuggingFaceRepository.KOKORO_REPOSITORY)
            }.onSuccess { model ->
                _state.update { it.copy(kokoroModel = model, loadingCatalog = false) }
            }.onFailure { error ->
                _state.update { it.copy(loadingCatalog = false, error = error.message) }
            }
        }
    }

    fun downloadKokoro() {
        if (downloadJob?.isActive == true) return
        val model = _state.value.kokoroModel ?: return
        val variant = model.variants.firstOrNull() ?: return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    downloadTotalBytes = model.totalSizeBytes,
                    error = null,
                )
            }
            runCatching {
                repository().install(model, variant) { downloaded, total ->
                    val progress = if (total > 0) {
                        (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _state.update {
                        it.copy(
                            downloadProgress = progress,
                            downloadedBytes = downloaded.coerceAtLeast(0L),
                            downloadTotalBytes = total,
                        )
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        installed = repository().installed(),
                        downloading = false,
                        downloadProgress = 0f,
                        downloadedBytes = 0L,
                        downloadTotalBytes = -1L,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 0f,
                        downloadedBytes = 0L,
                        downloadTotalBytes = -1L,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun downloadRussianVoice(voiceId: String) {
        if (downloadJob?.isActive == true) return
        val voice = PiperRussianTtsEngine.RUSSIAN_VOICES.firstOrNull { it.id == voiceId } ?: return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingVoiceId = voice.id,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    downloadTotalBytes = voice.approximateSizeBytes,
                    error = null,
                )
            }
            runCatching {
                russianInstaller.install(voice) { downloaded, total ->
                    _state.update {
                        it.copy(
                            downloadedBytes = downloaded,
                            downloadTotalBytes = total,
                            downloadProgress = if (total > 0) {
                                (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        )
                    }
                }
            }.onSuccess {
                settings.update {
                    it[SettingsRepository.Keys.TTS_ENGINE] = "piper_ru"
                    it[SettingsRepository.Keys.VOICE_ID] = voice.id
                    it[SettingsRepository.Keys.LANGUAGE] = voice.language
                    it[SettingsRepository.Keys.SELECTED_VOICE_MODEL_ID] = "piper:${voice.id}"
                }
                _state.update {
                    it.copy(
                        installedRussianVoices = installedRussianVoiceIds(),
                        downloadingVoiceId = null,
                        downloadProgress = 0f,
                        downloadedBytes = 0,
                        downloadTotalBytes = -1,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingVoiceId = null,
                        downloadProgress = 0f,
                        downloadedBytes = 0,
                        downloadTotalBytes = -1,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(
                downloading = false,
                downloadingVoiceId = null,
                downloadingCatalogId = null,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                downloadTotalBytes = -1L,
                catalogDownloadProgress = 0f,
                catalogDownloadedBytes = 0L,
                catalogDownloadTotalBytes = -1L,
            )
        }
    }

    /**
     * Download a model declared in [com.t2v.core.model.GenerationModelCatalog].
     *
     * Kokoro keeps its bespoke flow (voice.bin/tokens.txt aware). Every other
     * catalog entry whose repository is a real Hugging Face id (`author/name`)
     * is downloaded through the generic [downloadCatalogModel]/[HuggingFaceRepository]
     * path — the same client Kokoro uses. LiteRT music/sound bundles (e.g.
     * MusicGen) download all their files because [HuggingFaceRepository.install]
     * treats LiteRT entries as whole-bundle installs.
     */
    fun downloadModelFromCatalog(catalogId: String) {
        val entry = com.t2v.core.model.GenerationModelCatalog
            .entries
            .firstOrNull { it.id == catalogId }
            ?: return
        when {
            catalogId == "kokoro-82m" -> {
                // Kokoro has a bespoke flow that knows the model's voice.bin
                // and tokens.txt files.
                downloadKokoro()
            }
            com.t2v.core.model.GenerationModelCatalog.isHuggingFaceRepository(catalogId) -> {
                downloadCatalogModel(entry)
            }
            else -> _state.update {
                it.copy(
                    error = "Загрузка для «${entry.title}» пока не подключена. " +
                        "У этой записи нет Hugging Face-репозитория для скачивания.",
                )
            }
        }
    }

    /**
     * Generic Hugging Face download for a catalog [entry] backed by a real
     * `author/name` repository. Reuses the same progress/cancel UI that
     * Kokoro uses, but reports through the per-catalog fields so several
     * cards can be shown at once.
     */
    private fun downloadCatalogModel(entry: com.t2v.core.model.GenerationModelCatalog.Entry) {
        if (downloadJob?.isActive == true) return
        val repo = com.t2v.core.model.GenerationModelCatalog.repositoryFor(entry.id)
            ?: return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingCatalogId = entry.id,
                    catalogDownloadProgress = 0f,
                    catalogDownloadedBytes = 0L,
                    catalogDownloadTotalBytes = entry.approximateDownloadBytes ?: -1L,
                    error = null,
                )
            }
            runCatching {
                val model = repository().model(repo)
                val variant = model.variants.firstOrNull()
                    ?: return@runCatching error("у модели «${entry.title}» нет файлов, подходящих для Android")
                repository().install(model, variant) { downloaded, total ->
                    val progress = if (total > 0) {
                        (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _state.update {
                        it.copy(
                            catalogDownloadProgress = progress,
                            catalogDownloadedBytes = downloaded.coerceAtLeast(0L),
                            catalogDownloadTotalBytes = total,
                        )
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        installed = repository().installed(),
                        downloadingCatalogId = null,
                        catalogDownloadProgress = 0f,
                        catalogDownloadedBytes = 0L,
                        catalogDownloadTotalBytes = -1L,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingCatalogId = null,
                        catalogDownloadProgress = 0f,
                        catalogDownloadedBytes = 0L,
                        catalogDownloadTotalBytes = -1L,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun deleteRussianVoice(voiceId: String) {
        viewModelScope.launch {
            russianInstaller.delete(voiceId)
            _state.update { it.copy(installedRussianVoices = installedRussianVoiceIds()) }
        }
    }

    fun setModelsFolder(uri: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.MODELS_TREE_URI] = uri }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settings.update { it[SettingsRepository.Keys.SELECTED_MODEL_ID] = modelId }
        }
    }

    fun selectVoiceModel(modelId: String) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.SELECTED_VOICE_MODEL_ID] = modelId
                when {
                    modelId == VOICE_MODEL_KOKORO -> {
                        it[SettingsRepository.Keys.TTS_ENGINE] = "kokoro"
                    }
                    modelId.startsWith("piper:") -> {
                        val voiceId = modelId.substringAfter(':')
                        val voice = PiperRussianTtsEngine.RUSSIAN_VOICES
                            .firstOrNull { it.id == voiceId }
                        it[SettingsRepository.Keys.TTS_ENGINE] = "piper_ru"
                        it[SettingsRepository.Keys.VOICE_ID] = voiceId
                        it[SettingsRepository.Keys.LANGUAGE] = voice?.language ?: ""
                    }
                }
            }
        }
    }

    /**
     * Selecting a music/sound entry writes BOTH keys so downstream code
     * (`AudioTagInserter`, `AudioEditorScreen`) can read whichever one is
     * convenient without having to resolve the alias.
     */
    fun selectMusicModel(modelId: String) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.SELECTED_MUSIC_MODEL_ID] = modelId
                it[SettingsRepository.Keys.SELECTED_MUSIC_GENERATOR] = modelId
            }
        }
    }

    fun selectSoundModel(modelId: String) {
        viewModelScope.launch {
            settings.update {
                it[SettingsRepository.Keys.SELECTED_SOUND_MODEL_ID] = modelId
                it[SettingsRepository.Keys.SELECTED_SOUND_GENERATOR] = modelId
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val deleted = repository().delete(modelId)
            if (deleted && _state.value.selectedModelId == modelId) {
                settings.update { it[SettingsRepository.Keys.SELECTED_MODEL_ID] = "" }
            }
            _state.update { it.copy(installed = repository().installed()) }
        }
    }

    private fun repository(): HuggingFaceRepository =
        HuggingFaceRepository(context, modelsRoot, modelsTreeUri, huggingFaceToken)

    private fun installedRussianVoiceIds(): Set<String> =
        PiperRussianTtsEngine.RUSSIAN_VOICES
            .filter { russianInstaller.isInstalled(it.id) }
            .mapTo(mutableSetOf()) { it.id }
}

private const val VOICE_MODEL_KOKORO = "kokoro-82m"

/** Canonical generator ids (no category suffix). AudioTagInserter reads these. */
private const val GEN_STABLE_AUDIO_MUSIC = "litert.stable-audio-open-small.music"
private const val GEN_STABLE_AUDIO_SOUND = "litert.stable-audio-clip.sound"
private const val GEN_ELEVENLABS_SOUND = "elevenlabs.sound"
// Bundled placeholder generators were removed in favour of the
// LiteRT-based runtime (NSynth for sound, procedural for music).

/** Suffix-bearing ids used by ModelsScreen state to distinguish tabs. */
private const val MUSIC_MODEL_STABLE_AUDIO_OPEN_SMALL = "$GEN_STABLE_AUDIO_MUSIC:music"
private const val SOUND_MODEL_STABLE_AUDIO_CLIP = "$GEN_STABLE_AUDIO_SOUND:sound"
private const val SOUND_MODEL_ELEVEN_SFX = "$GEN_ELEVENLABS_SOUND:sound"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "size unknown"
    val unit = when {
        bytes >= 1_000_000_000L -> "GB" to 1_000_000_000.0
        bytes >= 1_000_000L -> "MB" to 1_000_000.0
        bytes >= 1_000L -> "KB" to 1_000.0
        else -> "B" to 1.0
    }
    val value = bytes / unit.second
    return if (unit.first == "B") {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, unit.first)
    }
}

internal fun downloadProgressText(downloadedBytes: Long, totalBytes: Long): String {
    val downloaded = formatBytes(downloadedBytes.coerceAtLeast(0L))
        .replace("size unknown", "0 B")
    if (totalBytes <= 0) return downloaded
    val percent = ((downloadedBytes.coerceAtLeast(0L).toDouble() / totalBytes) * 100)
        .toInt()
        .coerceIn(0, 100)
    return "$percent% • $downloaded / ${formatBytes(totalBytes)}"
}

class ModelsViewModelFactory(
    private val context: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ModelsViewModel(context) as T
}
