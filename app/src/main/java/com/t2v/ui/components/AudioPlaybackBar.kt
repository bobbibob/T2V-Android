package com.t2v.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.t2v.util.AudioPlayer
import java.io.File

/**
 * Простой плеер для превью аудиофайлов в UI.
 *
 * Использует `MediaPlayer` под капотом и Compose-Slider для прогресса.
 */
@Composable
fun AudioPlaybackBar(
    audioFile: File?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { AudioPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && audioFile != null) {
            kotlinx.coroutines.delay(200)
            // Progress обновляется таймером
            progress = ((progress + 0.005f) % 1f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = {
                if (audioFile == null || !audioFile.exists()) return@FilledTonalIconButton
                if (isPlaying) {
                    player.stop()
                    isPlaying = false
                } else {
                    player.play(audioFile) {
                        isPlaying = false
                        progress = 0f
                    }
                    isPlaying = true
                }
            },
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
        IconButton(onClick = {
            player.stop()
            isPlaying = false
            progress = 0f
        }) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
        Slider(
            value = progress,
            onValueChange = { progress = it },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = audioFile?.takeIf { it.exists() }?.let {
                "${it.length() / 1024} KB"
            } ?: "—",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
