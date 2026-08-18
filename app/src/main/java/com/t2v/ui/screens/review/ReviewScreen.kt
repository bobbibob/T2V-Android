package com.t2v.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.t2v.core.subtitle.SubtitleCue
import com.t2v.core.subtitle.SubtitleWriter
import com.t2v.data.SegmentEntity
import com.t2v.ui.components.AudioPlaybackBar
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReviewScreen(
    nav: NavController,
    audiobookId: Long,
    vm: ReviewViewModel = viewModel(factory = ReviewViewModelFactory(LocalContext.current, audiobookId)),
    windowSizeClass: WindowSizeClass? = null,
) {
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.review_title),
        onBack = { nav.popBackStack() },
        windowSizeClass = windowSizeClass,
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Audiobook #$audiobookId", style = MaterialTheme.typography.titleMedium)
            Text("Segments: ${state.segments.size}", style = MaterialTheme.typography.bodyMedium)
            AudioPlaybackBar(audioFile = state.audioPath?.let(::File))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(state.segments, key = { it.id }) { s -> SegmentRow(s) }
            }
            Button(onClick = { nav.navigate(com.t2v.ui.navigation.Routes.musicMix(audiobookId)) }) {
                Text(stringResource(R.string.mix_render))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.exportSubtitles("srt") }) {
                    Text("Export SRT")
                }
                OutlinedButton(onClick = { vm.exportSubtitles("ass") }) {
                    Text("Export ASS")
                }
                OutlinedButton(onClick = { vm.shareAudiobook(context) }) {
                    Text("Share")
                }
            }
            state.subtitleMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SegmentRow(s: SegmentEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("[#${s.orderIndex}] ${s.status}", style = MaterialTheme.typography.labelLarge)
            Text(s.text.take(120) + if (s.text.length > 120) "…" else "", style = MaterialTheme.typography.bodySmall)
            Text("${s.durationMs} ms", style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class ReviewState(
    val segments: List<SegmentEntity> = emptyList(),
    val audioPath: String? = null,
    val subtitleMessage: String? = null,
    val error: String? = null,
)

class ReviewViewModel(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModel() {
    private val db = AppContainer.database(context)
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            val segs = db.segments().listForAudiobook(audiobookId)
            val audioPath = db.audiobooks().byId(audiobookId)?.outputPath
            _state.update { it.copy(segments = segs, audioPath = audioPath) }
        }
    }

    /**
     * Builds subtitle cues from segment timings and writes an SRT or ASS
     * file next to the audiobook output.
     */
    fun exportSubtitles(format: String) {
        viewModelScope.launch {
            _state.update { it.copy(subtitleMessage = null, error = null) }
            runCatching {
                val segs = _state.value.segments
                if (segs.isEmpty()) error("No segments to export")
                val audioPath = _state.value.audioPath
                val baseName = audioPath?.substringBeforeLast('.') ?: "audiobook-$audiobookId"
                val outFile = File("$baseName.$format")
                val cues = buildCues(segs)
                val content = when (format) {
                    "srt" -> SubtitleWriter.formatSrt(cues)
                    "ass" -> SubtitleWriter.formatAss(cues)
                    else -> error("Unknown subtitle format: $format")
                }
                outFile.writeText(content)
                outFile.absolutePath
            }.onSuccess { path ->
                _state.update {
                    it.copy(subtitleMessage = "Subtitles exported: $path")
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(error = err.message ?: "Export failed")
                }
            }
        }
    }

    /**
     * Shares the audiobook audio file via Android Share Intent.
     */
    fun shareAudiobook(context: android.content.Context) {
        val audioPath = _state.value.audioPath ?: return
        val file = File(audioPath)
        if (!file.isFile) {
            _state.update { it.copy(error = "Audio file not found") }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                else -> "audio/*"
            }
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "Share audiobook"),
        )
    }

    private fun buildCues(segments: List<SegmentEntity>): List<SubtitleCue> {
        var currentTimeSec = 0f
        return segments.mapIndexed { idx, seg ->
            val startSec = currentTimeSec
            val durationSec = (seg.durationMs.coerceAtLeast(0) / 1000f)
            val endSec = startSec + durationSec + (seg.pauseAfterMs / 1000f)
            currentTimeSec = endSec
            SubtitleCue(
                index = idx + 1,
                startSec = startSec,
                endSec = endSec,
                text = seg.text,
            )
        }
    }
}

class ReviewViewModelFactory(
    private val context: android.content.Context,
    private val audiobookId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewViewModel(context, audiobookId) as T
}
