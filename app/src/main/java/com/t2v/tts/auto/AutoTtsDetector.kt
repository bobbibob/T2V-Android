package com.t2v.tts.auto

/**
 * Auto-TTS language detection: guesses the BCP-47 language of a sample of
 * text from its script and from how often language-specific function words
 * occur. Entirely heuristic, no network, runs in microseconds — this is what
 * the "Auto-TTS (распознавание языка текста)" roadmap item builds on.
 *
 * Returns [VoiceHint] with a primary tag out of the set T2V actually ships
 * voices for. Script detection is decisive (Cyrillic, Greek, Arabic, Han,
 * Kana, Hangul, Devanagari, Bengali); Latin-script languages are ranked by a
 * small function-word dictionary.
 */
object AutoTtsDetector {

    data class VoiceHint(
        val bcp47: String,
        val confidence: Double,
    )

    /** Every BCP-47 tag the detector can return. */
    val supportedTags: Set<String> = setOf(
        "ru-RU", "uk-UA", "en-US", "de-DE", "fr-FR", "es-ES", "it-IT",
        "pt-BR", "nl-NL", "cs-CZ", "da-DK", "fi-FI", "hu-HU", "ca-ES",
        "tr-TR", "el-GR", "ar", "fa-IR", "hi-IN", "bn-IN", "zh-CN",
        "ja-JP", "ko-KR",
    )

    fun detect(text: String): VoiceHint {
        if (text.isBlank()) return VoiceHint("en", 0.0)

        // --- Script is decisive where the writing system is unambiguous. ---
        val script = dominantScript(text)
        when (script) {
            Script.RUSSIAN_OR_UKRAINIAN -> return VoiceHint(
                bcp47 = if (ukIndicatorCount(text) > ruIndicatorCount(text)) "uk-UA" else "ru-RU",
                confidence = 0.95,
            )
            Script.GREEK -> return VoiceHint("el-GR", 0.95)
            Script.ARABIC_PERSIAN -> return VoiceHint(
                bcp47 = if (text.any { it.code in PersianExtras }) "fa-IR" else "ar",
                confidence = 0.95,
            )
            Script.HAN -> return VoiceHint(
                bcp47 = if (text.any { it.code in 0x3040..0x30FF }) "ja-JP" else "zh-CN",
                confidence = 0.9,
            )
            Script.HIRAGANA, Script.HIRAGANA_OR_HAN -> return VoiceHint("ja-JP", 0.95)
            Script.HANGUL -> return VoiceHint("ko-KR", 0.95)
            Script.DEVANAGARI -> return VoiceHint("hi-IN", 0.95)
            Script.BENGALI -> return VoiceHint("bn-IN", 0.95)
            Script.LATIN, Script.OTHER -> Unit
        }

        // --- Latin text: rank by function-word frequency. ---
        return rankLatin(text)
    }

    // --- scripting helpers ------------------------------------------------

    private enum class Script {
        RUSSIAN_OR_UKRAINIAN, GREEK, ARABIC_PERSIAN, HAN, HIRAGANA,
        HIRAGANA_OR_HAN, HANGUL, DEVANAGARI, BENGALI, LATIN, OTHER,
    }

    private val PersianExtras = intArrayOf(0x067E, 0x0686, 0x0698, 0x06AF)

    private fun scriptOfChar(c: Char): Script = when (c.code) {
        in 0x0400..0x04FF -> Script.RUSSIAN_OR_UKRAINIAN
        in 0x0370..0x03FF -> Script.GREEK
        in 0x0600..0x06FF, in 0x0750..0x077F -> Script.ARABIC_PERSIAN
        in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> Script.HAN
        in 0x3040..0x309F -> Script.HIRAGANA
        in 0x30A0..0x30FF -> Script.HIRAGANA_OR_HAN
        in 0xAC00..0xD7A3 -> Script.HANGUL
        in 0x0900..0x097F -> Script.DEVANAGARI
        in 0x0980..0x09FF -> Script.BENGALI
        in 0x0041..0x005A, in 0x0061..0x007A -> Script.LATIN
        else -> Script.OTHER
    }

    private fun dominantScript(text: String): Script {
        val counts = mutableMapOf<Script, Int>()
        for (c in text) {
            val s = scriptOfChar(c)
            counts[s] = (counts[s] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: Script.OTHER
    }

    private val ruIndicatorLetters = setOf('ы', 'э', 'ъ', 'ё')
    private val ukIndicatorLetters = setOf('і', 'ї', 'є', 'ґ')

    private fun ruIndicatorCount(text: String): Int = text.count { it in ruIndicatorLetters }
    private fun ukIndicatorCount(text: String): Int = text.count { it in ukIndicatorLetters }

    // --- Latin ranking -------------------------------------------------------

    private val FUNCTION_WORDS: Map<String, Array<String>> = mapOf(
        "en-US" to arrayOf("the", "and", "to", "of", "is", "in", "are", "with", "that", "you"),
        "de-DE" to arrayOf("und", "die", "der", "das", "ein", "eine", "nicht", "ist", "mit", "für"),
        "fr-FR" to arrayOf("le", "la", "les", "est", "et", "une", "pour", "dans", "que", "je"),
        "es-ES" to arrayOf("el", "la", "los", "las", "es", "y", "un", "una", "para", "por", "que"),
        "it-IT" to arrayOf("che", "e", "la", "il", "un", "una", "per", "non", "con", "della"),
        "pt-BR" to arrayOf("e", "é", "uma", "que", "na", "no", "para", "com", "dos", "das"),
        "nl-NL" to arrayOf("de", "het", "een", "en", "van", "ik", "niet", "dat", "met", "zijn"),
        "cs-CZ" to arrayOf("a", "je", "že", "pro", "jako", "nebo", "aby", "jsem", "není", "má"),
        "da-DK" to arrayOf("og", "er", "det", "en", "at", "han", "for", "til", "vær", "med"),
        "fi-FI" to arrayOf("ja", "että", "en", "se", "ei", "hän", "on", "ovat", "kun", "tämä"),
        "hu-HU" to arrayOf("és", "a", "az", "nem", "hogy", "van", "egy", "azt", "meg", "még"),
        "ca-ES" to arrayOf("el", "la", "de", "que", "és", "un", "una", "però", "amb", "com"),
        "tr-TR" to arrayOf("ve", "bir", "bu", "de", "da", "için", "ama", "ile", "çok", "şey"),
    )

    private fun rankLatin(text: String): VoiceHint {
        val words = Regex("[a-záéíóúüöäßèàçñøåæ]+").findAll(text.lowercase())
            .map { it.value }
            .toList()
        if (words.isEmpty()) return VoiceHint("en", 0.5)

        val scores = mutableMapOf<String, Int>()
        for (w in words) {
            for ((lang, fw) in FUNCTION_WORDS) {
                if (fw.contains(w)) {
                    scores[lang] = (scores[lang] ?: 0) + 1
                }
            }
        }
        val best = scores.maxByOrNull { it.value }
            ?: return VoiceHint("en-US", 0.5)
        val total = words.size
        val confidence = (best.value.toDouble() / total).coerceIn(0.0, 1.0)
        return VoiceHint(best.key, confidence)
    }
}