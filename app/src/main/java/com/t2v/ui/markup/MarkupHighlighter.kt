package com.t2v.ui.markup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.t2v.ui.theme.LTVColors

/**
 * Подсветка LTV-разметки в Compose-редакторе.
 *
 * Прямой порт `app/ui/markup_highlighter.py:LTVMarkupHighlighter`
 * (QSyntaxHighlighter) на Compose. Стили:
 *   - {{voice "..."}}       — фиолетовый
 *   - {{lang ...}}          — бирюзовый
 *   - {{pause ...}}         — оранжевый
 *   - {{speed ...}}         — зелёный
 *   - {{volume ...}}        — розовый
 *   - {{chapter ...}}       — синий
 *   - {{cmd ...}}           — серый
 */
object MarkupHighlighter {

    private val pattern = Regex(
        """\{\{[\s\S]+?\}\}|<music>[\s\S]+?</music>|<sfx>[\s\S]+?</sfx>""",
    )
    private val musicOnlyPattern = Regex("""<music>[\s\S]+?</music>""")
    private val sfxOnlyPattern = Regex("""<sfx>[\s\S]+?</sfx>""")

    fun highlight(source: String): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (match in pattern.findAll(source)) {
            append(source.substring(cursor, match.range.first))
            val inside = match.value
            val (color, bg, italic, bold) = styleFor(inside)
            pushStyle(SpanStyle(color = color, background = bg, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))
            append(inside)
            pop()
            cursor = match.range.last + 1
        }
        if (cursor < source.length) append(source.substring(cursor))
    }

    private fun styleFor(command: String): Quad<Color, Color, Boolean, Boolean> {
        val name = when {
            musicOnlyPattern.matches(command) -> "music_tag"
            sfxOnlyPattern.matches(command) -> "sfx_tag"
            else -> command.substringAfter("{{").substringBefore(' ', "").lowercase()
        }
        return when (name) {
            "voice" -> Quad(LTVColors.VoiceColor, LTVColors.VoiceBg, false, true)
            "lang", "language" -> Quad(LTVColors.LangColor, LTVColors.LangBg, false, true)
            "pause" -> Quad(LTVColors.PauseColor, LTVColors.PauseBg, true, false)
            "speed" -> Quad(LTVColors.SpeedColor, LTVColors.SpeedBg, false, true)
            "volume" -> Quad(LTVColors.VolumeColor, LTVColors.VolumeBg, false, true)
            "chapter" -> Quad(LTVColors.ChapterColor, LTVColors.ChapterBg, false, true)
            "pitch", "emotion", "delivery", "style", "emphasis", "whisper", "shout",
            "breath", "sigh", "laugh", "chuckle", "giggle", "cry", "sob", "gasp",
            "yawn", "cough", "clear_throat", "sniff", "pant", "hmm", "reset" ->
                Quad(LTVColors.ChapterColor, LTVColors.ChapterBg, true, false)
            "sfx", "music", "sfx_tag", "music_tag" -> Quad(LTVColors.Accent, LTVColors.ChapterBg, true, true)
            "cmd", "set" -> Quad(Color.DarkGray, Color(0xFFEEEEEE), true, false)
            else -> Quad(Color.Gray, Color.Transparent, true, false)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
