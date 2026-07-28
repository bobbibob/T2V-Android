package com.t2v.ui.screens.audioeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t2v.core.audio.AudioEditClip
import com.t2v.core.audio.AudioTrackKind
import com.t2v.core.audio.buildWaveformFromFile
import com.t2v.core.audio.WaveformEnvelope
import java.io.File

/**
 * CapCut-style single timeline with 3 horizontal lanes: voice, music, sound.
 *
 * Each clip is a coloured block positioned by [AudioEditClip.timelineStartMs]
 * and sized by its estimated duration. Tapping a clip selects it.
 */
@Composable
fun TimelineView(
    voiceClips: List<AudioEditClip>,
    musicClips: List<AudioEditClip>,
    soundClips: List<AudioEditClip>,
    selectedClipId: String?,
    playheadMs: Long,
    pixelsPerSecond: Float,
    onClipTap: (AudioTrackKind, AudioEditClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val totalDurationMs = listOf(voiceClips, musicClips, soundClips).flatten()
        .maxOfOrNull { it.timelineStartMs + clipDurationMs(it) } ?: 0L
    val totalDurationSec = (totalDurationMs / 1000.0).coerceAtLeast(5.0)
    val timelineWidthPx = with(density) { (totalDurationSec * pixelsPerSecond).toFloat().toDp() }
    val rulerHeight = 28.dp
    val laneHeight = 56.dp
    val laneGap = 4.dp
    val laneColor = mapOf(
        AudioTrackKind.Voice to MaterialTheme.colorScheme.primary,
        AudioTrackKind.Music to MaterialTheme.colorScheme.tertiary,
        AudioTrackKind.Sound to MaterialTheme.colorScheme.secondary,
    )

    Column(modifier = modifier) {
        // Time ruler
        TimelineRuler(
            totalDurationSec = totalDurationSec,
            pixelsPerSecond = pixelsPerSecond,
            rulerWidth = timelineWidthPx,
            rulerHeight = rulerHeight,
        )

        // Playhead overlay + 3 lanes
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rulerHeight + (laneHeight + laneGap) * 3),
        ) {
            // Lanes
            Column(modifier = Modifier.offset(y = rulerHeight)) {
                TrackLane(
                    label = "Voice",
                    clips = voiceClips,
                    color = laneColor[AudioTrackKind.Voice]!!,
                    timelineWidthPx = timelineWidthPx,
                    laneHeight = laneHeight,
                    pixelsPerSecond = pixelsPerSecond,
                    selectedClipId = selectedClipId,
                    onClipTap = { clip -> onClipTap(AudioTrackKind.Voice, clip) },
                )
                Spacer(Modifier.height(laneGap))
                TrackLane(
                    label = "Music",
                    clips = musicClips,
                    color = laneColor[AudioTrackKind.Music]!!,
                    timelineWidthPx = timelineWidthPx,
                    laneHeight = laneHeight,
                    pixelsPerSecond = pixelsPerSecond,
                    selectedClipId = selectedClipId,
                    onClipTap = { clip -> onClipTap(AudioTrackKind.Music, clip) },
                )
                Spacer(Modifier.height(laneGap))
                TrackLane(
                    label = "Sound",
                    clips = soundClips,
                    color = laneColor[AudioTrackKind.Sound]!!,
                    timelineWidthPx = timelineWidthPx,
                    laneHeight = laneHeight,
                    pixelsPerSecond = pixelsPerSecond,
                    selectedClipId = selectedClipId,
                    onClipTap = { clip -> onClipTap(AudioTrackKind.Sound, clip) },
                )
            }

            // Playhead
            val playheadX = with(density) {
                ((playheadMs / 1000f) * pixelsPerSecond).toDp()
            }
            if (playheadX > 0.dp) {
                Canvas(
                    modifier = Modifier
                        .offset(x = playheadX)
                        .height(rulerHeight + (laneHeight + laneGap) * 3)
                        .width(2.dp),
                ) {
                    drawRect(
                        color = Color.Red,
                        size = androidx.compose.ui.geometry.Size(
                            size.width,
                            size.height,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    totalDurationSec: Double,
    pixelsPerSecond: Float,
    rulerWidth: androidx.compose.ui.unit.Dp,
    rulerHeight: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    // Choose a "nice" interval: 1s, 5s, 10s, 30s, 60s
    val minLabelSpacingPx = with(density) { 50.dp.toPx() }
    val intervalSec = listOf(1, 2, 5, 10, 15, 30, 60, 120)
        .firstOrNull { it * pixelsPerSecond >= minLabelSpacingPx } ?: 120

    Canvas(
        modifier = Modifier
            .width(rulerWidth)
            .height(rulerHeight),
    ) {
        val h = size.height
        val tickH = h * 0.4f
        val labelY = h * 0.7f
        var sec = 0
        while (sec <= totalDurationSec) {
            val x = sec * pixelsPerSecond
            drawLine(
                color = Color.Gray,
                start = Offset(x, h - tickH),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
            if (sec % intervalSec == 0) {
                drawLine(
                    color = Color.White,
                    start = Offset(x, h - tickH * 1.5f),
                    end = Offset(x, h),
                    strokeWidth = 2f,
                )
            }
            sec++
        }
    }
    // Labels
    Row(modifier = Modifier.width(rulerWidth).height(rulerHeight)) {
        var sec = 0
        while (sec <= totalDurationSec) {
            if (sec % intervalSec == 0) {
                val x = with(density) { (sec * pixelsPerSecond).toDp() }
                Box(modifier = Modifier.offset(x = x)) {
                    Text(
                        text = formatTime(sec),
                        style = TextStyle(fontSize = 9.sp, color = Color.Gray),
                        modifier = Modifier.offset(y = 2.dp),
                    )
                }
            }
            sec++
        }
    }
}

@Composable
private fun TrackLane(
    label: String,
    clips: List<AudioEditClip>,
    color: Color,
    timelineWidthPx: androidx.compose.ui.unit.Dp,
    laneHeight: androidx.compose.ui.unit.Dp,
    pixelsPerSecond: Float,
    selectedClipId: String?,
    onClipTap: (AudioEditClip) -> Unit,
) {
    val density = LocalDensity.current
    val laneBg = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .width(timelineWidthPx)
            .height(laneHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(laneBg),
    ) {
        // Label on the left
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color,
            ),
            modifier = Modifier.offset(x = 4.dp, y = 2.dp),
        )

        // Clips
        clips.forEach { clip ->
            val startOffsetPx = with(density) {
                ((clip.timelineStartMs / 1000f) * pixelsPerSecond).toDp()
            }
            val durationMs = clipDurationMs(clip)
            val clipWidthPx = with(density) {
                ((durationMs / 1000f) * pixelsPerSecond).toDp()
            }.coerceAtLeast(12.dp) // minimum visible width
            val isSelected = clip.id == selectedClipId

            val envelope = remember(clip.id) { loadEnvelope(clip) }
            Box(
                modifier = Modifier
                    .offset(x = startOffsetPx, y = with(density) { 18.dp })
                    .width(clipWidthPx)
                    .height(with(density) { laneHeight - 22.dp })
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = if (isSelected) 0.85f else 0.55f))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .pointerInput(clip.id) {
                        detectTapGestures { onClipTap(clip) }
                    },
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    drawWaveform(envelope, color.copy(alpha = 0.85f))
                }
                val fileName = File(clip.sourcePath).name
                Text(
                    text = fileName.take(15),
                    style = TextStyle(
                        fontSize = 8.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = 3.dp, y = 2.dp),
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

/** Estimate clip duration in ms from the WAV file size or endMs-startMs. */
fun clipDurationMs(clip: AudioEditClip): Long {
    // If end is set, use it
    val sourceDuration = if (clip.endMs > clip.startMs) {
        (clip.endMs - clip.startMs)
    } else {
        // Estimate from file size: 22050 Hz * 2 bytes * 1ch = 44100 bytes/sec
        val file = File(clip.sourcePath)
        if (file.isFile) {
            val dataBytes = file.length() - 44 // subtract WAV header
            if (dataBytes > 0) (dataBytes * 1000L / 44100) else 1000L
        } else {
            1000L
        }
    }
    return (sourceDuration / clip.speed).toLong().coerceAtLeast(100L)
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
}

private fun loadEnvelope(clip: AudioEditClip): WaveformEnvelope? {
    val file = File(clip.sourcePath)
    if (!file.isFile || file.length() < 1024) return null
    return runCatching { buildWaveformFromFile(file) }.getOrNull()
}

/** Draw a vertical envelope inside the current DrawScope bounds. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    envelope: WaveformEnvelope?,
    color: Color,
) {
    if (envelope == null || envelope.isEmpty) return
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val duration = envelope.durationSec.coerceAtLeast(0.0001f)
    val path = Path()
    for (i in envelope.times.indices) {
        val x = (envelope.times[i] / duration) * w
        val lo = envelope.minimums[i]
        val hi = envelope.maximums[i]
        val yHi = mid - hi * mid * 0.9f
        val yLo = mid - lo * mid * 0.9f
        if (i == 0) path.moveTo(x, yHi) else path.lineTo(x, yHi)
        path.lineTo(x, yLo)
    }
    clipRect { drawPath(path, color = color, style = Stroke(width = 1.2f, cap = StrokeCap.Round)) }
}
