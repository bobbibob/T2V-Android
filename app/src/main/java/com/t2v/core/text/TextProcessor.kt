package com.t2v.core.text

import com.t2v.core.markup.LTVMarkupParser

/**
 * Прямой порт [TextProcessor] из `app/core/text_processor.py` (372 строки).
 *
 * Алгоритмы и регулярки намеренно сохранены 1:1, чтобы поведение чанкования
 * совпадало с десктопной версией (важно для воспроизводимости аудио-выхода).
 *
 * Основные шаги:
 *   1. `clean()`  — убрать управляющие символы, схлопнуть whitespace.
 *   2. `splitSections()` — найти главы/уроки по тем же regex'ам.
 *   3. `splitChunks()` — разбить на безопасные чанки под TTS (≤ [chunkSize]).
 */
class TextProcessor(
    val chunkSize: Int = 2500,
    val minChunkSize: Int = 200,
    val paragraphPauseMinMs: Int = 450,
    val paragraphPauseMaxMs: Int = 900,
    val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    init {
        require(chunkSize >= minChunkSize) { "chunkSize must be >= minChunkSize" }
    }

    // --- Шаг 1: очистка ----------------------------------------------------

    private val controlChars = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")
    private val horizontalSpace = Regex("[ \t]+")

    fun clean(raw: String): String {
        var s = controlChars.replace(raw, "")
        s = horizontalSpace.replace(s, " ")
        s = s.replace("\r\n", "\n").replace("\r", "\n")
        // Схлопнуть повторяющиеся пустые строки
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }

    // --- Шаг 2: разбиение на секции ----------------------------------------

    // Прямой перевод _heading_pattern из Python с флагом re.IGNORECASE.
    // Слова "chapter/lesson/module" + их переводы (capitulo, leccion, modulo).
    private val headingPattern = Regex(
        pattern = (
            "^(?:" +
                "(?:chapter|lesson|module|cap[ií]tulo|lecci[oó]n|m[oó]dulo)" +
                "\\s+(?:\\d+|[ivxlcdm]+)(?:\\s*[:.\\-]\\s*.*|\\s+.*)?\$" +
                "|#{1,6}\\s+\\S.*\$" +
            ")"
        ),
        option = RegexOption.IGNORE_CASE,
    )

    /** Возвращает список секций. Один кусок без заголовка → одна секция с пустым title. */
    fun splitSections(cleanText: String): List<TextSection> {
        val lines = cleanText.split("\n")
        val sections = mutableListOf<TextSection>()
        var currentTitle: String? = null
        val current = StringBuilder()

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty() || currentTitle != null) {
                sections += TextSection(currentTitle ?: "", text)
            }
            current.setLength(0)
        }

        for (line in lines) {
            if (headingPattern.matches(line.trim())) {
                flush()
                currentTitle = line.trim()
            } else {
                if (current.isNotEmpty()) current.append("\n")
                current.append(line)
            }
        }
        flush()
        return sections
    }

    // --- Шаг 3: разбиение на чанки ----------------------------------------

    private val sentenceBoundary = Regex("(?<=[.!?;…。！？；])\\s+")
    private val sentencePiece = Regex(".+?(?:[.!?;…。！？；]+(?=\\s+|\$)|\$)", RegexOption.DOT_MATCHES_ALL)
    private val clauseBoundary = Regex("(?<=[,,:;…、，：；])\\s+")

    /**
     * Разбивает текст одной секции на чанки.
     *
     * Стратегия (1:1 с оригиналом):
     *  - очень длинные предложения сначала режутся по clause_boundary;
     *  - короткие — собираются в чанк до границы параграфа;
     *  - длинный параграф режется по sentence_boundary / clause_boundary.
     */
    fun splitChunks(section: TextSection, paragraphNumber: Int = 0): List<TextChunk> {
        val result = mutableListOf<TextChunk>()
        if (section.text.isBlank()) return result

        val paragraphs = section.text.split(Regex("\n{2,}"))
        var paraIndex = paragraphNumber
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            val endsParagraph = true
            val paraLen = trimmed.length
            paraIndex += 1

            if (trimmed.length <= chunkSize) {
                result += TextChunk(
                    text = trimmed,
                    endsParagraph = endsParagraph,
                    paragraphLength = paraLen,
                    paragraphNumber = paraIndex,
                    markupPauseAfterMs = random.nextInt(paragraphPauseMinMs, paragraphPauseMaxMs + 1),
                )
                continue
            }

            // Длинный параграф: режем по предложениям.
            val sentences = sentencePiece.findAll(trimmed).map { it.value }.toList()
            val current = StringBuilder()
            for (s in sentences) {
                val candidate = if (current.isEmpty()) s else current.toString() + " " + s
                if (candidate.length <= chunkSize) {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(s)
                } else {
                    if (current.isNotEmpty()) {
                        val text = current.toString().trim()
                        if (text.length > chunkSize) {
                            // Слишком длинное предложение — режем по clause_boundary
                            splitLongSentenceInto(text, paraLen, paraIndex).forEach { result += it }
                        } else {
                            result += TextChunk(
                                text = text,
                                endsParagraph = false,
                                paragraphLength = paraLen,
                                paragraphNumber = paraIndex,
                            )
                        }
                        current.setLength(0)
                    }
                    if (s.length > chunkSize) {
                        splitLongSentenceInto(s, paraLen, paraIndex).forEach { result += it }
                    } else {
                        current.append(s)
                    }
                }
            }
            if (current.isNotEmpty()) {
                result += TextChunk(
                    text = current.toString().trim(),
                    endsParagraph = endsParagraph,
                    paragraphLength = paraLen,
                    paragraphNumber = paraIndex,
                    markupPauseAfterMs = random.nextInt(paragraphPauseMinMs, paragraphPauseMaxMs + 1),
                )
            }
        }
        return result
    }

    private fun splitLongSentenceInto(text: String, paraLen: Int, paraIndex: Int): List<TextChunk> {
        val pieces = mutableListOf<TextChunk>()
        val parts = clauseBoundary.split(text)
        val cur = StringBuilder()
        for (p in parts) {
            val candidate = if (cur.isEmpty()) p else cur.toString() + " " + p
            if (candidate.length <= chunkSize) {
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(p)
            } else {
                if (cur.isNotEmpty()) {
                    pieces += TextChunk(
                        text = cur.toString().trim(),
                        endsParagraph = false,
                        paragraphLength = paraLen,
                        paragraphNumber = paraIndex,
                    )
                    cur.setLength(0)
                }
                if (p.length > chunkSize) {
                    // Аварийная нарезка по chunkSize (не должно случаться в обычных текстах)
                    var i = 0
                    while (i < p.length) {
                        val end = (i + chunkSize).coerceAtMost(p.length)
                        pieces += TextChunk(
                            text = p.substring(i, end).trim(),
                            endsParagraph = false,
                            paragraphLength = paraLen,
                            paragraphNumber = paraIndex,
                        )
                        i = end
                    }
                } else {
                    cur.append(p)
                }
            }
        }
        if (cur.isNotEmpty()) {
            pieces += TextChunk(
                text = cur.toString().trim(),
                endsParagraph = false,
                paragraphLength = paraLen,
                paragraphNumber = paraIndex,
            )
        }
        return pieces
    }

    // --- Утилиты -----------------------------------------------------------

    /**
     * Полный пайплайн: очистка → секции → чанки + список аудио-тегов.
     *
     * Третий элемент возвращаемого [ProcessResult] — это список
     * [com.t2v.core.markup.AudioTag] в исходном порядке. Pipeline использует
     * его, чтобы после синтеза речи вставить клипы музыки/звука ровно между
     * соседними voice-чанками (конец одного = конец предыдущего span, начало
     * следующего = конец тега).
     */
    fun process(raw: String): ProcessResult {
        if ("{{" in raw || "<music>" in raw || "<sfx>" in raw) {
            val parser = LTVMarkupParser(
                paragraphPauseMinMs = paragraphPauseMinMs,
                paragraphPauseMaxMs = paragraphPauseMaxMs,
                random = random,
            )
            val allSections = mutableListOf<TextSection>()
            val allChunks = mutableListOf<TextChunk>()
            val audioTags = mutableListOf<com.t2v.core.markup.AudioTag>()
            parser.parseSpans(raw).forEach { span ->
                val (sections, chunks) = processPlain(span.text)
                allSections += sections
                val paragraphBase = allChunks.size
                chunks.forEachIndexed { index, chunk ->
                    allChunks += chunk.copy(
                        paragraphNumber = chunk.paragraphNumber + paragraphBase,
                        markupPauseBeforeMs = if (index == 0) span.pauseBeforeMs else 0,
                        markupState = span.state,
                    )
                }
                span.trailingAudioTag?.let { audioTags += it }
            }
            return ProcessResult(allSections, allChunks, audioTags)
        }
        val (sections, chunks) = processPlain(raw)
        return ProcessResult(sections, chunks, emptyList())
    }

    private fun processPlain(raw: String): Pair<List<TextSection>, List<TextChunk>> {
        val cleaned = clean(raw)
        val sections = splitSections(cleaned)
        val chunks = mutableListOf<TextChunk>()
        var pNum = 0
        for (sec in sections) {
            chunks += splitChunks(sec, pNum)
            pNum = chunks.size
        }
        return sections to chunks
    }
}

/** Полный результат пайплайна: секции, чанки, и аудио-теги в порядке появления. */
data class ProcessResult(
    val sections: List<TextSection>,
    val chunks: List<TextChunk>,
    val audioTags: List<com.t2v.core.markup.AudioTag>,
)
