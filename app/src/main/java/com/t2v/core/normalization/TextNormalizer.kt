package com.t2v.core.normalization

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Прямой порт подмножества `app/core/text_normalization.py` (1 635 строк),
 * достаточный для базовых сценариев: числа, валюты, даты, проценты,
 * измерения, римские цифры.
 *
 * Полная версия использует SQLite-словари и "структурные правила";
 * на Android-клиенте применяем правила в коде + загружаемый
 * пользовательский словарь замен (см. [CustomDictionary]).
 */
data class NormalizationRules(
    val enabled: Boolean = false,
    val language: String = "auto",
    val numbers: Boolean = true,
    val ordinals: Boolean = true,
    val dates: Boolean = true,
    val currencies: Boolean = true,
    val percentages: Boolean = true,
    val measurements: Boolean = true,
    val romanNumerals: Boolean = true,
    val times: Boolean = true,
)

class TextNormalizer(
    val rules: NormalizationRules,
    val customDictionary: Map<String, String> = emptyMap(),
    val locale: Locale = Locale.getDefault(),
) {

    private val num2words = Num2Words.forLocale(locale)

    fun normalize(input: String): String {
        if (!rules.enabled || input.isEmpty()) return input
        var s = input
        if (rules.times) s = normalizeTimes(s)
        if (rules.currencies) s = normalizeCurrencies(s)
        if (rules.percentages) s = normalizePercentages(s)
        if (rules.measurements) s = normalizeMeasurements(s)
        if (rules.dates) s = normalizeDates(s)
        if (rules.romanNumerals) s = normalizeRomanNumerals(s)
        if (rules.ordinals) s = normalizeOrdinals(s)
        if (rules.numbers) s = normalizeNumbers(s)
        s = applyCustomDictionary(s)
        return s
    }

    // --- правила -----------------------------------------------------------

    private val timeRegex = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private fun normalizeTimes(s: String): String =
        timeRegex.replace(s) { m ->
            val hh = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            val mi = m.groupValues[2].toIntOrNull() ?: return@replace m.value
            if (hh > 23 || mi > 59) m.value
            else clockWords(hh, mi)
        }

    /** Reads a digital clock aloud, localized to the active numeric locale. */
    private fun clockWords(hour: Int, minute: Int): String {
        val hWord = num2words.numberToWords(hour.toDouble())
        val mWord = num2words.numberToWords(minute.toDouble())
        return when (locale.language) {
            "ru" -> if (minute == 0) "$hWord часов" else "$hWord $mWord"
            else -> when {
                minute == 0 -> "$hWord o'clock"
                minute < 10 -> "$hWord oh $mWord"
                else -> "$hWord $mWord"
            }
        }
    }

    // Русские порядковые формы вида «N-й/-я/-е/-го/-му/-м/-х».
    private val ruOrdinalSuffixRegex = Regex("""\b(\d{1,3})-([а-яё]{1,2})\b""", RegexOption.IGNORE_CASE)
    private fun normalizeRussianOrdinals(s: String): String =
        ruOrdinalSuffixRegex.replace(s) { m ->
            val num = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            val suffix = m.groupValues[2].lowercase()
            if (num < 1 || num >= 1000) return@replace m.value
            if (suffix !in RU_ORDINAL_SUFFIXES) return@replace m.value
            declineRuOrdinal(num2words.ordinalToWords(num), suffix)
        }

    private fun declineRuOrdinal(word: String, suffix: String): String = when (suffix) {
        "й" -> word
        "я" -> ruEnding(word, "ый" to "ая", "ой" to "ая", "ий" to "ья")
        "е" -> ruEnding(word, "ый" to "ое", "ой" to "ое", "ий" to "ье")
        "го", "его" -> ruEnding(word, "ый" to "ого", "ой" to "ого", "ий" to "его")
        "му", "ему" -> ruEnding(word, "ый" to "ому", "ой" to "ому", "ий" to "ему")
        "м" -> ruEnding(word, "ый" to "ом", "ой" to "ом", "ий" to "ем")
        "ю" -> ruEnding(word, "ый" to "ую", "ой" to "ую", "ий" to "ью")
        "х" -> ruEnding(word, "ый" to "ых", "ой" to "ых", "ий" to "их")
        else -> word
    }

    private fun ruEnding(word: String, vararg forms: Pair<String, String>): String {
        for ((from, to) in forms) if (word.endsWith(from)) return word.dropLast(from.length) + to
        return word
    }

    private val currencyRegexPrefix = Regex("""([\$€£¥₽]|USD|EUR|GBP|RUB|JPY|CNY)\s?(\d{1,9}(?:[.,]\d{1,2})?)""")
    private val currencyRegexSuffix = Regex("""(\d{1,9}(?:[.,]\d{1,2})?)\s?([\$€£¥₽]|USD|EUR|GBP|RUB|JPY|CNY)""")
    private fun normalizeCurrencies(s: String): String {
        var out = currencyRegexPrefix.replace(s) { m ->
            val (cur, num) = m.destructured
            val value = num.replace(',', '.').toDoubleOrNull() ?: return@replace m.value
            val amount = num2words.numberToWords(value)
            when (cur.uppercase()) {
                "$", "USD" -> "$amount dollars"
                "€", "EUR" -> "$amount euros"
                "£", "GBP" -> "$amount pounds"
                "¥", "JPY", "CNY" -> "$amount yuan"
                "₽", "RUB" -> "$amount rubles"
                else -> "$amount $cur"
            }
        }
        out = currencyRegexSuffix.replace(out) { m ->
            val (num, cur) = m.destructured
            val value = num.replace(',', '.').toDoubleOrNull() ?: return@replace m.value
            val amount = num2words.numberToWords(value)
            when (cur.uppercase()) {
                "$", "USD" -> "$amount dollars"
                "€", "EUR" -> "$amount euros"
                "£", "GBP" -> "$amount pounds"
                "¥", "JPY", "CNY" -> "$amount yuan"
                "₽", "RUB" -> "$amount rubles"
                else -> "$amount $cur"
            }
        }
        return out
    }

    private val percentRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?%""")
    private fun normalizePercentages(s: String): String =
        percentRegex.replace(s) { m ->
            val (num, _) = m.destructured
            val value = num.replace(',', '.').toDoubleOrNull() ?: return@replace m.value
            "${num2words.numberToWords(value)} percent"
        }

    private val measurementRegex = Regex("""(\d{1,9}(?:[.,]\d{1,2})?)\s?(km|m|cm|mm|mi|ft|in|kg|g|lb|oz|l|ml|°C|°F|kB|MB|GB|TB)""", RegexOption.IGNORE_CASE)
    private fun normalizeMeasurements(s: String): String =
        measurementRegex.replace(s) { m ->
            val (num, unit) = m.destructured
            val value = num.replace(',', '.').toDoubleOrNull() ?: return@replace m.value
            val amount = num2words.numberToWords(value)
            "$amount $unit"
        }

    private val dateRegex = Regex("""(\d{1,2})[./](\d{1,2})[./](\d{2,4})""")
    private fun normalizeDates(s: String): String =
        dateRegex.replace(s) { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            val monthName = monthNameOrNull(mo.toInt())
            if (monthName == null) m.value
            else "${num2words.numberToWords(d.toInt().toDouble())} $monthName ${num2words.numberToWords(year.toInt().toDouble())}"
        }

    private val romanRegex = Regex("""\b([IVXLCM]{2,8})\b""")
    private fun normalizeRomanNumerals(s: String): String =
        romanRegex.replace(s) { m ->
            val v = romanToInt(m.groupValues[1])
            if (v == null || v > 3999) m.value
            else num2words.numberToWords(v.toDouble())
        }

    private val ordinalRegex = Regex("""(\d{1,3})(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)
    private fun normalizeOrdinals(s: String): String {
        if (locale.language == "ru") {
            val afterRu = normalizeRussianOrdinals(s)
            return ordinalRegex.replace(afterRu) { m ->
                val v = m.groupValues[1].toIntOrNull() ?: return@replace m.value
                num2words.ordinalToWords(v)
            }
        }
        return ordinalRegex.replace(s) { m ->
            val v = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            num2words.ordinalToWords(v)
        }
    }

    private val numberRegex = Regex("""\b\d{1,9}(?:[.,]\d{1,2})?\b""")
    private fun normalizeNumbers(s: String): String =
        numberRegex.replace(s) { m ->
            val raw = m.value
            val v = raw.replace(',', '.').toDoubleOrNull() ?: return@replace raw
            // Не трогаем то, что уже нормализовано другими правилами
            if (v == v.toInt().toDouble() && raw.contains('.').not() && raw.contains(',').not() && raw.length > 4) {
                // 4+ цифры — как число по-цифрам
                num2words.digitsToWords(v.toInt())
            } else {
                num2words.numberToWords(v)
            }
        }

    private fun applyCustomDictionary(s: String): String {
        if (customDictionary.isEmpty()) return s
        var out = s
        for ((k, v) in customDictionary) {
            out = out.replace(Regex("(?i)\\b" + Regex.escape(k) + "\\b"), v)
        }
        return out
    }

    private fun monthNameOrNull(m: Int): String? = when (m) {
        1 -> "january"; 2 -> "february"; 3 -> "march"; 4 -> "april"
        5 -> "may"; 6 -> "june"; 7 -> "july"; 8 -> "august"
        9 -> "september"; 10 -> "october"; 11 -> "november"; 12 -> "december"
        else -> null
    }

    private fun romanToInt(s: String): Int? {
        val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var prev = 0
        var total = 0
        for (c in s.uppercase().reversed()) {
            val v = map[c] ?: return null
            if (v < prev) total -= v else total += v
            prev = v
        }
        return total
    }

    companion object {
        private val RU_ORDINAL_SUFFIXES = setOf("й", "я", "е", "ю", "го", "его", "му", "ему", "м", "х")
    }
}
