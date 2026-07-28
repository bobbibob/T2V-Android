package com.t2v.core.subtitle

import java.io.File
import kotlin.math.roundToInt

/**
 * Экспорт субтитров SRT и karaoke-style ASS.
 *
 * Прямой порт `app/core/subtitle_export.py`.
 *
 * Использование:
 *   val srt = SubtitleWriter.formatSrt(cues)
 *   val ass = SubtitleWriter.formatAss(cues, resolution = 1280x720)
 *   File("out.srt").writeText(srt)
 */
data class SubtitleCue(
    /** Индекс (1-based, как требует SRT) */
    val index: Int,
    /** Начало в секундах */
    val startSec: Float,
    /** Конец в секундах */
    val endSec: Float,
    /** Текст реплики */
    val text: String,
    /** Пословные тайминги для karaoke (опционально, для ASS) */
    val words: List<WordTiming> = emptyList(),
) {
    val durationSec: Float get() = endSec - startSec
}

data class WordTiming(
    val word: String,
    val startSec: Float,
    val endSec: Float,
)

object SubtitleWriter {

    /** Формат SRT (SubRip). */
    fun formatSrt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        for (cue in cues) {
            sb.append(cue.index).append('\n')
            sb.append(formatSrtTime(cue.startSec)).append(" --> ").append(formatSrtTime(cue.endSec)).append('\n')
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString()
    }

    /** Формат ASS с karaoke-таймингами по словам. */
    fun formatAss(
        cues: List<SubtitleCue>,
        width: Int = 1280,
        height: Int = 720,
        fontName: String = "Arial",
        fontSize: Int = 56,
    ): String {
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        sb.append("ScriptType: v4.00+\n")
        sb.append("WrapStyle: 0\n")
        sb.append("PlayResX: $width\n")
        sb.append("PlayResY: $height\n\n")
        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        // PrimaryColour белый, SecondaryColour жёлтый (для karaoke)
        sb.append("Style: Default,$fontName,$fontSize,&H00FFFFFF,&H0000FFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,1,2,40,40,80,1\n\n")
        sb.append("[Events]\n")
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for (cue in cues) {
            val start = formatAssTime(cue.startSec)
            val end = formatAssTime(cue.endSec)
            val text = if (cue.words.isNotEmpty()) karaokeText(cue.words) else cue.text
            sb.append("Dialogue: 0,$start,$end,Default,,0,0,0,,$text\n")
        }
        return sb.toString()
    }

    private fun karaokeText(words: List<WordTiming>): String {
        val sb = StringBuilder()
        for (w in words) {
            val cs = ((w.endSec - w.startSec) * 100).roundToInt().coerceAtLeast(1)
            sb.append("{\\kf$cs}").append(w.word).append(' ')
        }
        return sb.toString().trim()
    }

    /** 00:00:01,000 */
    private fun formatSrtTime(sec: Float): String {
        val h = (sec / 3600).toInt()
        val m = ((sec % 3600) / 60).toInt()
        val s = (sec % 60).toInt()
        val ms = ((sec - sec.toInt()) * 1000).roundToInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }

    /** 0:00:01.00 (ASS) */
    private fun formatAssTime(sec: Float): String {
        val h = (sec / 3600).toInt()
        val m = ((sec % 3600) / 60).toInt()
        val s = (sec % 60)
        return "%d:%02d:%05.2f".format(h, m, s)
    }
}
