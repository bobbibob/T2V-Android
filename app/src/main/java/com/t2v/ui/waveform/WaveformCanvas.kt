package com.t2v.ui.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.t2v.core.audio.WaveformEnvelope

/**
 * Compose-канвас для отображения waveform.
 *
 * Прямой порт логики `paintEvent` из `audio_mix_preview_panel.py:WaveformPreview`
 * на Compose. Использует [WaveformEnvelope], построенный через
 * `core/audio/WaveformExtractor.kt`.
 */
@Composable
fun WaveformCanvas(
    envelope: WaveformEnvelope,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    color: Color = Color(0xFF5C2D91),
    progress: Float = 0f,                  // 0..1 — позиция воспроизведения
) {
    val path = remember(envelope) { buildPath(envelope) }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (envelope.isEmpty) return@Canvas
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val progressX = w * progress
        // Рисуем waveform линией
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            // Прогресс — горизонтальная линия
            drawLine(
                color = color.copy(alpha = 0.6f),
                start = Offset(progressX, 0f),
                end = Offset(progressX, h),
                strokeWidth = 2f.dp.toPx(),
            )
        }
    }
}

private fun buildPath(env: WaveformEnvelope): Path {
    val path = Path()
    if (env.isEmpty) return path
    val maxX = env.size
    for (i in 0 until env.size) {
        val x = env.times[i] / maxOf(env.times.lastOrNull() ?: 1f, 0.0001f)
        val lo = env.minimums[i]
        val hi = env.maximums[i]
        val mid = 0.5f
        val yLo = mid - (lo - mid).coerceAtMost(0f)
        val yHi = mid + (hi - mid).coerceAtLeast(0f)
        if (i == 0) path.moveTo(x, yHi) else path.lineTo(x, yHi)
        path.lineTo(x, yLo)
    }
    return path
}
