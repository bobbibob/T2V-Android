package com.t2v.core.normalization

import java.util.Locale

/**
 * Конвертер чисел в слова. Упрощённый порт библиотеки num2words (Python).
 *
 * Поддерживает:
 *   - целые числа до 999 999 999 999
 *   - десятичные дроби
 *   - порядковые числительные
 *   - локали: en (по умолчанию), ru, es, fr, de
 */
class Num2Words(private val locale: Locale) {

    fun numberToWords(value: Double): String {
        val intPart = value.toLong()
        val frac = ((value - intPart) * 100).toInt().let { if (it == 0) 0 else it }
        val intWords = intToWords(kotlin.math.abs(intPart))
        val signed = if (intPart < 0) "${negWord()} " else ""
        val fracWords = if (frac > 0) " ${pointWord()} ${intToWords(frac.toLong())}" else ""
        return "$signed$intWords$fracWords".trim()
    }

    fun ordinalToWords(value: Int): String {
        val cardinal = intToWords(value.toLong())
        return when (locale.language) {
            "ru" -> ordinalRu(value)
            "es" -> cardinal + "º".replace("uno", "primer").replace("una", "primera")
            "fr" -> "$cardinal${if (value == 1) "er" else "e"}"
            "de" -> "$cardinal."
            else -> {
                val last = value % 10
                val tens = (value % 100) / 10
                val suffix = when {
                    tens == 1 -> "th"
                    last == 1 -> "st"
                    last == 2 -> "nd"
                    last == 3 -> "rd"
                    else -> "th"
                }
                if (value == 0) "zeroth"
                else if (value < 0) "${negWord()} ${kotlin.math.abs(value)}$suffix"
                else "$value$suffix"
            }
        }
    }

    fun digitsToWords(value: Int): String {
        val s = value.toString()
        val parts = s.map { digitToWord(it.digitToInt()) }
        return when (locale.language) {
            "ru" -> parts.joinToString(" ")
            else -> parts.joinToString(" ")
        }
    }

    // --- internal --------------------------------------------------------

    private fun intToWords(value: Long): String {
        if (value == 0L) return zeroWord()
        return when (locale.language) {
            "ru" -> intToWordsRu(value)
            "es" -> intToWordsEs(value)
            "fr" -> intToWordsFr(value)
            "de" -> intToWordsDe(value)
            else -> intToWordsEn(value)
        }
    }

    private fun intToWordsEn(n: Long): String {
        if (n == 0L) return "zero"
        val parts = mutableListOf<String>()
        var v = n
        // Millions, thousands (scales) — рекурсивно
        for ((scaleEng, scaleVal) in SCALES_EN) {
            if (v >= scaleVal) {
                val count = v / scaleVal
                if (count == 1L) {
                    parts += scaleEng
                } else {
                    parts += "${intToWordsEn(count)} $scaleEng"
                }
                v %= scaleVal
            }
        }
        // Hundreds (100..900)
        if (v >= 100L) {
            val h = (v / 100L).toInt()
            parts += ONES_EN[h] + " hundred"
            v %= 100L
        }
        // Tens and ones (1..99)
        if (v in 1L..20L) {
            parts += ONES_EN[v.toInt()]
        } else if (v > 0L) {
            val tens = (v / 10).toInt()
            val ones = (v % 10).toInt()
            parts += if (ones == 0) TENS_EN[tens] else "${TENS_EN[tens]}-${ONES_EN[ones]}"
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun intToWordsRu(n: Long): String {
        // Упрощённый порт num2words для русского
        if (n == 0L) return "ноль"
        val groups = mutableListOf<Long>()
        var v = kotlin.math.abs(n)
        while (v > 0) {
            groups += v % 1000
            v /= 1000
        }
        val out = StringBuilder()
        for ((i, g) in groups.withIndex()) {
            if (g == 0L) continue
            val groupName = when (i) {
                1 -> if (g % 10 in 2..4) "тысячи" else "тысяч"
                2 -> if (g % 10 in 2..4) "миллиона" else "миллионов"
                3 -> if (g % 10 in 2..4) "миллиарда" else "миллиардов"
                else -> ""
            }
            out.append(threeDigitsRu(g.toInt()))
            if (groupName.isNotEmpty()) out.append(' ').append(groupName).append(' ')
        }
        return out.toString().trim()
    }

    private fun threeDigitsRu(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        val tens = rest / 10
        val ones = rest % 10
        val sb = StringBuilder()
        if (hundreds > 0) sb.append(HUNDREDS_RU[hundreds]).append(' ')
        if (rest in 11..19) {
            sb.append(TEENS_RU[rest])
        } else {
            if (tens > 0) sb.append(TENS_RU[tens]).append(' ')
            if (ones > 0) sb.append(ONES_RU[ones])
        }
        return sb.toString().trim()
    }

    private val HUNDREDS_ES = arrayOf("", "ciento", "doscientos", "trescientos",
        "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos")

    private fun intToWordsEs(n: Long): String {
        if (n == 0L) return "cero"
        val parts = mutableListOf<String>()
        var v = n
        // Hundreds
        if (v >= 100L) {
            val h = (v / 100L).toInt()
            if (v in 100L..199L) {
                parts += "cien"
            } else {
                parts += HUNDREDS_ES[h]
            }
            v %= 100L
        }
        // Scales (millones, miles)
        // (обрабатываются выше в intToWords для тысяч и миллионов)
        // Tens and ones
        if (v in 1L..29L) {
            parts += ONES_ES[v.toInt()]
        } else if (v > 0L) {
            val tens = (v / 10).toInt()
            val ones = (v % 10).toInt()
            parts += if (ones == 0) TENS_ES[tens] else "${TENS_ES[tens]} y ${ONES_ES[ones]}"
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun intToWordsFr(n: Long): String {
        if (n == 0L) return "zéro"
        val parts = mutableListOf<String>()
        var v = n
        for ((scaleFr, scaleVal) in SCALES_FR) {
            if (v >= scaleVal) {
                val count = v / scaleVal
                if (count == 1L) parts += scaleFr
                else parts += "${intToWordsFr(count)} $scaleFr"
                v %= scaleVal
            }
        }
        if (v in 1L..16L) parts += ONES_FR[v.toInt()]
        else {
            val tens = (v / 10).toInt()
            val ones = (v % 10).toInt()
            parts += if (tens == 7 || tens == 9) {
                TENS_FR[tens] + (if (ones > 0) "-${intToWordsFr(ones.toLong())}" else "")
            } else if (ones == 0) TENS_FR[tens]
            else "${TENS_FR[tens]}-${ONES_FR[ones]}"
        }
        return parts.joinToString(" ")
    }

    private fun intToWordsDe(n: Long): String {
        if (n == 0L) return "null"
        val parts = mutableListOf<String>()
        var v = n
        for ((scaleDe, scaleVal) in SCALES_DE) {
            if (v >= scaleVal) {
                val count = v / scaleVal
                parts += if (count == 1L) scaleDe else "${intToWordsDe(count)} $scaleDe"
                v %= scaleVal
            }
        }
        if (v in 1L..19L) parts += ONES_DE[v.toInt()]
        else {
            val tens = (v / 10).toInt()
            val ones = (v % 10).toInt()
            parts += if (ones == 0) TENS_DE[tens] else "${TENS_DE[tens]}${ONES_DE[ones]}"
        }
        return parts.joinToString(" ").replace("  ", " ")
    }

    private fun digitToWord(d: Int): String = when (locale.language) {
        "ru" -> ONES_RU.getOrElse(d) { d.toString() }
        "es" -> ONES_ES.getOrElse(d) { d.toString() }
        "fr" -> ONES_FR.getOrElse(d) { d.toString() }
        "de" -> ONES_DE.getOrElse(d) { d.toString() }
        else -> ONES_EN.getOrElse(d) { d.toString() }
    }

    private fun zeroWord() = when (locale.language) {
        "ru" -> "ноль"; "es" -> "cero"; "fr" -> "zéro"; "de" -> "null"; else -> "zero"
    }

    private fun pointWord() = when (locale.language) {
        "ru" -> "и"; "es" -> "coma"; "fr" -> "virgule"; "de" -> "komma"; else -> "point"
    }

    private fun negWord() = when (locale.language) {
        "ru" -> "минус"; "es" -> "menos"; "fr" -> "moins"; "de" -> "minus"; else -> "negative"
    }

    private fun ordinalRu(value: Int): String = when {
        value == 0 -> "нулевой"
        value < 0 -> "минус ${ordinalRuPositive(kotlin.math.abs(value))}"
        else -> ordinalRuPositive(value)
    }

    /** Русское порядковое (м. род, именительный) для 1..999; ≥1000 — кардинальное зачисление. */
    private fun ordinalRuPositive(n: Int): String {
        if (n >= 1000) return intToWordsRu(n.toLong())
        val hundreds = n / 100
        val rest = n % 100
        val restWord = ordinalRuUnderHundred(rest)
        if (hundreds == 0) return restWord
        if (rest == 0) return ORD_HUNDREDS_RU[hundreds]
        return "${CARD_HUNDREDS_RU[hundreds]} $restWord"
    }

    private fun ordinalRuUnderHundred(rest: Int): String = when {
        rest == 0 -> ""
        rest in 1..20 -> ORD_UNITS_RU[rest]
        else -> {
            val tens = rest / 10
            val ones = rest % 10
            if (ones == 0) ORD_TENS_RU[tens]
            else "${CARD_TENS_RU[tens]} ${ORD_UNITS_RU[ones]}"
        }
    }

    companion object {
        fun forLocale(locale: Locale): Num2Words = Num2Words(locale)

        private val ONES_EN = arrayOf("", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
        private val TENS_EN = arrayOf("", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety", "hundred")
        private val SCALES_EN = listOf(
            "billion" to 1_000_000_000L, "million" to 1_000_000L, "thousand" to 1_000L,
        )

        private val ONES_RU = arrayOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь",
            "восемь", "девять")
        private val TEENS_RU = mapOf(11 to "одиннадцать", 12 to "двенадцать", 13 to "тринадцать",
            14 to "четырнадцать", 15 to "пятнадцать", 16 to "шестнадцать", 17 to "семнадцать",
            18 to "восемнадцать", 19 to "девятнадцать")
        private val TENS_RU = arrayOf("", "десять", "двадцать", "тридцать", "сорок", "пятьдесят",
            "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
        private val HUNDREDS_RU = arrayOf("", "сто", "двести", "триста", "четыреста", "пятьсот",
            "шестьсот", "семьсот", "восемьсот", "девятьсот")

        private val ONES_ES = arrayOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve", "diez", "once", "doce", "trece", "catorce", "quince",
            "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte",
            "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
            "veintiséis", "veintisiete", "veintiocho", "veintinueve")
        private val TENS_ES = arrayOf("", "", "veinte", "treinta", "cuarenta", "cincuenta",
            "sesenta", "setenta", "ochenta", "noventa")
        private val SCALES_ES = listOf(
            "mil millones" to 1_000_000_000L, "millón" to 1_000_000L, "mil" to 1_000L,
        )

        private val ONES_FR = arrayOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept",
            "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize")
        private val TENS_FR = arrayOf("", "dix", "vingt", "trente", "quarante", "cinquante",
            "soixante", "soixante", "quatre-vingt", "quatre-vingt")
        private val SCALES_FR = listOf(
            "milliard" to 1_000_000_000L, "million" to 1_000_000L, "mille" to 1_000L,
        )

        private val ONES_DE = arrayOf("", "ein", "zwei", "drei", "vier", "fünf", "sechs", "sieben",
            "acht", "neun", "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn",
            "sechzehn", "siebzehn", "achtzehn", "neunzehn")
        private val TENS_DE = arrayOf("", "zehn", "zwanzig", "dreißig", "vierzig", "fünfzig",
            "sechzig", "siebzig", "achtzig", "neunzig")
        private val SCALES_DE = listOf(
            "Milliarde" to 1_000_000_000L, "Million" to 1_000_000L, "tausend" to 1_000L,
        )

        // Русские порядковые (мужской род, именительный падеж) — индексы совпадают с числом.
        private val ORD_UNITS_RU = arrayOf(
            "", "первый", "второй", "третий", "четвёртый", "пятый", "шестой", "седьмой",
            "восьмой", "девятый", "десятый",
            "одиннадцатый", "двенадцатый", "тринадцатый", "четырнадцатый", "пятнадцатый",
            "шестнадцатый", "семнадцатый", "восемнадцатый", "девятнадцатый", "двадцатый",
        )
        private val ORD_TENS_RU = arrayOf(
            "", "", "двадцатый", "тридцатый", "сороковой", "пятидесятый", "шестидесятый",
            "семидесятый", "восьмидесятый", "девяностый",
        )
        private val ORD_HUNDREDS_RU = arrayOf(
            "", "сотый", "двухсотый", "трёхсотый", "четырёхсотый", "пятисотый",
            "шестисотый", "семисотый", "восьмисотый", "девятисотый",
        )
        private val CARD_HUNDREDS_RU = arrayOf(
            "", "сто", "двести", "триста", "четыреста", "пятьсот",
            "шестьсот", "семьсот", "восемьсот", "девятьсот",
        )
        private val CARD_TENS_RU = arrayOf(
            "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
            "шестьдесят", "семьдесят", "восемьдесят", "девяносто",
        )
    }
}
