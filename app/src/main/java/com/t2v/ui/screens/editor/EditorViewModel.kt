package com.t2v.ui.screens.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.t2v.app.AppContainer
import com.t2v.core.text.TextProcessor
import com.t2v.data.ProjectEntity
import com.t2v.tts.VoiceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class EditorState(
    val title: String = "Untitled",
    val author: String = "",
    val outputTreeUri: String = "",
    val text: String = "",
    val splitMode: String = "safe_chunks",
    val chunkCount: Int = 0,
)

class EditorViewModel(private val context: Context) : ViewModel() {

    private val db = AppContainer.database(context)
    private val settings = AppContainer.settings(context)
    private val textProcessor = AppContainer.textProcessor(context)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun setTitle(value: String) = _state.update { it.copy(title = value) }
    fun setAuthor(value: String) = _state.update { it.copy(author = value) }
    fun setOutputTreeUri(value: String) = _state.update { it.copy(outputTreeUri = value) }
    fun setText(value: String) {
        _state.update { it.copy(text = value, chunkCount = estimateChunkCount(value)) }
    }
    fun setSplitMode(value: String) = _state.update { it.copy(splitMode = value) }

    private fun estimateChunkCount(text: String): Int {
        if (text.isBlank()) return 0
        val (_, chunks) = textProcessor.process(text)
        return chunks.size
    }

    suspend fun save(context: Context): Long? {
        if (_state.value.text.isBlank() || _state.value.outputTreeUri.isBlank()) return null
        val s = settings.flow.first()
        val project = ProjectEntity(
            title = _state.value.title.ifBlank { "Untitled" },
            rawText = _state.value.text,
            ttsEngine = s.ttsEngine,
            voiceConfigJson = Json.encodeToString(VoiceConfig.serializer(), VoiceConfig.EMPTY.copy(voice = s.voiceId, lang = s.language, speed = s.speed)),
            splitMode = _state.value.splitMode,
            outputTreeUri = _state.value.outputTreeUri,
            author = _state.value.author,
        )
        return db.projects().upsert(project)
    }
}

class EditorViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(context) as T
}
