package com.t2v.core.markup

import kotlin.math.roundToInt

/**
 * Парсер LTV-разметки. Полный порт `app/core/ltv_markup.py`.
 *
 * Поддерживаемые команды (1:1 с оригиналом):
 *   {{chapter "..."}}
 *   {{voice "..."}} / {{voice "ser spa"}}
 *   {{lang es}}
 *   {{pause 700ms}} / {{pause 0.7s}} / {{pause.long}} / {{pause random 500 1200}}
 *   {{speed 0.92}} / {{volume 80%}} / {{pitch 1.1}}
 *   {{sfx "name" 200ms}} / {{music "name" -2dB}}
 *   {{cmd key=value}} — произвольное кастомное поле
 *
 * Возвращает [ParsedMarkup]:
 *   - plainText: текст без разметки
 *   - commands: список применённых команд (с порядком и смещением)
 *   - chunks: готовые чанки, разбитые по абзацам/предложениям
 *             (структура совпадает с TextProcessor.splitChunks, но с
 *              markup-состоянием и событиями)
 */
class LTVMarkupParser(
    val paragraphPauseMinMs: Int = 450,
    val paragraphPauseMaxMs: Int = 900,
    val defaultPauseMs: Int = 700,
    val random: kotlin.random.Random = kotlin.random.Random.Default,
) {

    // {{...}} с произвольным содержимым (lazy: внутри могут быть кавычки с "}}").
    private val commandPattern = Regex("""\{\{([\s\S]+?)\}\}""")
    /**
     * XML-like audio tags inserted between voice chunks:
     *   <music>prompt</music>
     *   <sfx>prompt</sfx>
     * Prompts may contain any character (including `{{...}}` and nested text),
     * except for the literal sequence `</music>` or `</sfx>` which closes the
     * tag. We deliberately do not allow nesting.
     */
    private val musicTagPattern = Regex("""<music>([\s\S]+?)</music>""")
    private val sfxTagPattern = Regex("""<sfx>([\s\S]+?)</sfx>""")

    fun parse(input: String): ParsedMarkup {
        val commands = mutableListOf<MarkupCommand>()
        val plainBuilder = StringBuilder()
        var cursor = 0
        var state = MarkupState()

        // Сначала вырежем все команды и заменим их на маркер-плейсхолдер.
        // Это позволяет TextProcessor потом работать с "чистым" текстом.
        for (match in commandPattern.findAll(input)) {
            val inside = match.groupValues[1]
            val cmd = parseCommand(inside.trim(), match.range.first) ?: continue
            commands += cmd

            // Вставляем разделитель чанков, чтобы сохранить позицию
            plainBuilder.append(input, cursor, match.range.first)
            plainBuilder.append(PLACEHOLDER)
            cursor = match.range.last + 1

            state = applyCommand(state, cmd)
        }
        plainBuilder.append(input, cursor, input.length)
        val plain = plainBuilder.toString()

        // Заменяем плейсхолдеры на настоящие \n для переноса в чанки.
        val textForChunks = plain.replace(PLACEHOLDER, "\n")
        return ParsedMarkup(plainText = textForChunks, commands = commands, finalState = state)
    }

    /**
     * Splits source into spoken spans. Voice text is broken at three kinds of
     * boundaries:
     *   1. LTV command boundaries `{{...}}` (existing behaviour);
     *   2. The opening `<` of an `<music>`/`<sfx>` tag — voice before the tag
     *      becomes the end of the previous span; the tag itself is reported
     *      via [MarkupSpan.trailingAudioTag] so the pipeline can place the
     *      generated clip exactly at the byte where the tag opened.
     *
     * The prompt inside the XML tag is **not** part of any voice span.
     */
    fun parseSpans(input: String): List<MarkupSpan> {
        val spans = mutableListOf<MarkupSpan>()
        val audioTags = extractAudioTags(input)
        val audioIter = audioTags.iterator()
        var nextAudio: AudioTag? = if (audioIter.hasNext()) audioIter.next() else null
        var cursor = 0
        var state = MarkupState()
        var pauseBeforeMs = 0

        fun emit(text: String, trailing: AudioTag? = null) {
            if (text.isBlank() && trailing == null) return
            spans += MarkupSpan(text, state, pauseBeforeMs, trailing)
            pauseBeforeMs = 0
            if (trailing != null) nextAudio = if (audioIter.hasNext()) audioIter.next() else null
            state = state.copy(vocalCues = emptyList())
        }

        while (cursor < input.length) {
            val audioStart = nextAudio?.startOffset ?: input.length
            // Find the next command boundary at or after `cursor`.
            val sliceAfterCursor = input.substring(cursor)
            val cmdMatchInSlice = commandPattern.find(sliceAfterCursor)
            val cmdStart = if (cmdMatchInSlice != null) cursor + cmdMatchInSlice.range.first else input.length

            when {
                audioStart < cmdStart -> {
                    if (audioStart > cursor) emit(input.substring(cursor, audioStart))
                    val tag = nextAudio ?: break
                    cursor = tag.endOffset
                    emit("", trailing = tag)
                }
                cmdStart < audioStart -> {
                    if (cmdMatchInSlice == null) break
                    emit(input.substring(cursor, cmdStart))
                    val command = parseCommand(cmdMatchInSlice.groupValues[1].trim(), cmdStart)
                    if (command is MarkupCommand.Pause) {
                        pauseBeforeMs += command.durationMs.coerceAtLeast(0)
                    } else if (command != null) {
                        state = applyCommand(state, command)
                    }
                    cursor = cmdStart + (cmdMatchInSlice.range.last - cmdMatchInSlice.range.first) + 1
                }
                else -> {
                    emit(input.substring(cursor))
                    cursor = input.length
                }
            }
        }
        return spans
    }

    private fun parseCommand(inside: String, offset: Int): MarkupCommand? {
        val tokens = tokenize(inside)
        if (tokens.isEmpty()) return null
        val name = tokens[0]
        val args = tokens.drop(1)
        val dottedPause = name.lowercase().removePrefix("pause.")
            .takeIf { name.startsWith("pause.", ignoreCase = true) }
        if (dottedPause != null) {
            return MarkupCommand.Pause(
                parsePauseArgs(listOf(dottedPause) + args, defaultPauseMs),
                offset,
            )
        }
        return when (name.lowercase()) {
            "chapter" -> MarkupCommand.Chapter(textArg(args, 0, ""), offset)
            "voice" -> MarkupCommand.Voice(textArg(args, 0, ""), offset)
            "lang", "language" -> MarkupCommand.Lang(textArg(args, 0, ""), offset)
            "speed" -> MarkupCommand.Speed(doubleArg(args, 0, 1.0), offset)
            "volume" -> MarkupCommand.Volume(volumeArg(args, 0, 1.0), offset)
            "pitch" -> MarkupCommand.Pitch(doubleArg(args, 0, 1.0), offset)
            "emotion" -> MarkupCommand.Emotion(textArg(args, 0, ""), offset)
            "delivery", "style" -> MarkupCommand.Delivery(textArg(args, 0, "normal"), offset)
            "whisper" -> MarkupCommand.Delivery("whisper", offset)
            "shout" -> MarkupCommand.Delivery("shout", offset)
            "emphasis" -> MarkupCommand.Emphasis(textArg(args, 0, "moderate"), offset)
            "breath", "sigh", "laugh", "chuckle", "giggle", "cry", "sob",
            "gasp", "yawn", "cough", "clear_throat", "sniff", "pant", "hmm" ->
                MarkupCommand.VocalCue(
                    cue = name.lowercase(),
                    detail = textArg(args, 0, ""),
                    offset = offset,
                )
            "reset" -> MarkupCommand.Reset(textArg(args, 0, "all").lowercase(), offset)
            "pause" -> MarkupCommand.Pause(parsePauseArgs(args, defaultPauseMs), offset)
            "sfx" -> MarkupCommand.Sfx(textArg(args, 0, ""), intArg(args, 1, 0), offset)
            "music" -> MarkupCommand.Music(textArg(args, 0, ""), doubleArg(args, 1, -3.0), offset)
            "cmd", "set" -> MarkupCommand.Custom(parseCustomArgs(args), offset)
            else -> MarkupCommand.Unknown(name, args, offset)
        }
    }

    private fun tokenize(inside: String): List<String> {
        // Сначала выделим кавычки, потом пробелы.
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < inside.length) {
            val c = inside[i]
            when {
                c == '"' -> {
                    inQuote = !inQuote
                }
                c == '=' && !inQuote -> {
                    // key=value
                    out += cur.toString()
                    out += "="
                    cur.setLength(0)
                }
                (c == ' ' || c == '\t') && !inQuote -> {
                    if (cur.isNotEmpty()) {
                        out += cur.toString()
                        cur.setLength(0)
                    }
                }
                else -> cur.append(c)
            }
            i += 1
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun textArg(args: List<String>, idx: Int, default: String): String =
        args.getOrNull(idx)?.trim('"') ?: default

    private fun doubleArg(args: List<String>, idx: Int, default: Double): Double =
        args.getOrNull(idx)?.toDoubleOrNull() ?: default

    private fun intArg(args: List<String>, idx: Int, default: Int): Int =
        args.getOrNull(idx)?.toIntOrNull() ?: default

    /** "80%" → 0.8, иначе как Double. */
    private fun volumeArg(args: List<String>, idx: Int, default: Double): Double {
        val raw = args.getOrNull(idx) ?: return default
        if (raw.endsWith("%")) {
            val v = raw.dropLast(1).toDoubleOrNull() ?: return default
            return v / 100.0
        }
        return raw.toDoubleOrNull() ?: default
    }

    /**
     * Поддерживает:
     *   700ms / 0.7s / 1.2 / pause.long (=1500ms) / pause.random 500 1200
     */
    private fun parsePauseArgs(args: List<String>, defaultMs: Int): Int {
        if (args.isEmpty()) return defaultMs
        return when (args[0].lowercase()) {
            "long" -> 1500
            "short" -> 300
            "random" -> {
                val a = args.getOrNull(1)?.toIntOrNull() ?: 300
                val b = args.getOrNull(2)?.toIntOrNull() ?: 800
                random.nextInt(a, b + 1)
            }
            else -> {
                val raw = args[0]
                when {
                    raw.endsWith("ms", true) -> raw.dropLast(2).toIntOrNull() ?: defaultMs
                    raw.endsWith("s", true) -> {
                        val sec = raw.dropLast(1).toDoubleOrNull() ?: (defaultMs / 1000.0)
                        (sec * 1000).roundToInt()
                    }
                    else -> raw.toIntOrNull() ?: defaultMs
                }
            }
        }
    }

    private fun parseCustomArgs(args: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var i = 0
        while (i < args.size - 1) {
            val key = args[i]
            if (args[i + 1] == "=" && i + 2 < args.size) {
                result[key] = args[i + 2].trim('"')
                i += 3
            } else {
                i += 1
            }
        }
        return result
    }

    private fun applyCommand(state: MarkupState, cmd: MarkupCommand): MarkupState = when (cmd) {
        is MarkupCommand.Chapter -> state.copy(chapter = cmd.title)
        is MarkupCommand.Voice -> state.copy(voice = cmd.name)
        is MarkupCommand.Lang -> state.copy(language = cmd.code)
        is MarkupCommand.Speed -> state.copy(speed = cmd.value)
        is MarkupCommand.Volume -> state.copy(volume = cmd.value)
        is MarkupCommand.Pitch -> state.copy(pitch = cmd.value)
        is MarkupCommand.Emotion -> state.copy(emotion = cmd.value)
        is MarkupCommand.Delivery -> state.copy(delivery = cmd.value)
        is MarkupCommand.Emphasis -> state.copy(emphasis = cmd.value)
        is MarkupCommand.VocalCue -> state.copy(
            vocalCues = state.vocalCues + listOfNotNull(
                cmd.cue,
                cmd.detail.takeIf { it.isNotBlank() },
            ).joinToString(":"),
        )
        is MarkupCommand.Reset -> when (cmd.target) {
            "emotion" -> state.copy(emotion = null)
            "delivery" -> state.copy(delivery = null)
            "prosody" -> state.copy(speed = null, volume = null, pitch = null, emphasis = null)
            "voice" -> state.copy(voice = null, language = null)
            else -> MarkupState()
        }
        is MarkupCommand.Pause -> state
        is MarkupCommand.Sfx -> state
        is MarkupCommand.Music -> state
        is MarkupCommand.Custom -> state.copy(custom = state.custom + cmd.values)
        is MarkupCommand.Unknown -> state
    }

    /**
     * Находит все XML-теги `<music>` / `<sfx>` в исходнике. Возвращает список
     * с абсолютной позицией в тексте (offset) и промптом. Позиция — это
     * индекс открывающего `<` в исходной строке.
     *
     * Эти теги НЕ участвуют в parseSpans() — они не меняют голос. Но
     * [com.t2v.core.text.TextProcessor.process] использует их, чтобы разорвать
     * речевой поток в нужном месте: всё до тега становится концом чанка,
     * всё после открывающего тега — началом нового чанка. Это позволяет
     * синхронизировать музыку/звук с точной точкой голоса.
     */
    fun extractAudioTags(input: String): List<AudioTag> {
        val tags = mutableListOf<AudioTag>()
        for (match in musicTagPattern.findAll(input)) {
            tags += AudioTag(
                category = AudioTag.Category.Music,
                prompt = match.groupValues[1].trim(),
                startOffset = match.range.first,
                endOffset = match.range.last + 1,
            )
        }
        for (match in sfxTagPattern.findAll(input)) {
            tags += AudioTag(
                category = AudioTag.Category.Sound,
                prompt = match.groupValues[1].trim(),
                startOffset = match.range.first,
                endOffset = match.range.last + 1,
            )
        }
        return tags.sortedBy { it.startOffset }
    }

    companion object {
        const val PLACEHOLDER = "\u0001"   // невидимый символ-заместитель команды
    }
}

/** Описание одного XML-тега музыки/звука внутри текста. */
data class AudioTag(
    val category: Category,
    val prompt: String,
    /** Индекс открывающего `<` в исходном тексте. */
    val startOffset: Int,
    /** Индекс сразу за закрывающим `>` (для удаления из текста). */
    val endOffset: Int,
) {
    enum class Category { Music, Sound }
}

/** Все распознанные команды LTV-разметки. */
sealed interface MarkupCommand {
    val offset: Int

    data class Chapter(val title: String, override val offset: Int) : MarkupCommand
    data class Voice(val name: String, override val offset: Int) : MarkupCommand
    data class Lang(val code: String, override val offset: Int) : MarkupCommand
    data class Speed(val value: Double, override val offset: Int) : MarkupCommand
    data class Volume(val value: Double, override val offset: Int) : MarkupCommand
    data class Pitch(val value: Double, override val offset: Int) : MarkupCommand
    data class Emotion(val value: String, override val offset: Int) : MarkupCommand
    data class Delivery(val value: String, override val offset: Int) : MarkupCommand
    data class Emphasis(val value: String, override val offset: Int) : MarkupCommand
    data class VocalCue(
        val cue: String,
        val detail: String = "",
        override val offset: Int,
    ) : MarkupCommand
    data class Reset(val target: String, override val offset: Int) : MarkupCommand
    data class Pause(val durationMs: Int, override val offset: Int) : MarkupCommand
    data class Sfx(val name: String, val offsetMs: Int, override val offset: Int) : MarkupCommand
    data class Music(val name: String, val volumeDb: Double, override val offset: Int) : MarkupCommand
    data class Custom(val values: Map<String, String>, override val offset: Int) : MarkupCommand
    data class Unknown(val name: String, val args: List<String>, override val offset: Int) : MarkupCommand
}

/** Результат парсинга. */
data class ParsedMarkup(
    val plainText: String,
    val commands: List<MarkupCommand>,
    val finalState: MarkupState,
)

data class MarkupSpan(
    val text: String,
    val state: MarkupState,
    val pauseBeforeMs: Int = 0,
    /** Audio tag that ends this voice span (if any). */
    val trailingAudioTag: AudioTag? = null,
)
