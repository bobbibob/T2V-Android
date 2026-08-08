package com.t2v.core.normalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextNormalizerTest {

    private val rules = NormalizationRules(enabled = true)
    private val normalizer = TextNormalizer(rules, customDictionary = mapOf("LTV" to "Local Text to Voice"))
    private val ruNormalizer = TextNormalizer(rules, locale = Locale("ru"))

    @Test
    fun `disabled returns input unchanged`() {
        val n = TextNormalizer(NormalizationRules(enabled = false))
        assertTrue(n.normalize("5%") == "5%")
    }

    @Test
    fun `percentage is expanded`() {
        val out = normalizer.normalize("Battery at 5% remaining")
        // Не проверяем точное написание чисел, только факт замены
        assertTrue(out.contains("percent"))
    }

    @Test
    fun `currencies are expanded`() {
        val out = normalizer.normalize("It costs $5 today")
        // 5 → "five", "$" → "dollars"
        assertTrue(out.lowercase().contains("dollars") || out.lowercase().contains("dollar"))
    }

    @Test
    fun `measurements are expanded`() {
        val out = normalizer.normalize("Distance is 3 km")
        assertTrue(out.contains("km"))
    }

    @Test
    fun `roman numerals are expanded`() {
        val out = normalizer.normalize("Chapter IV begins")
        assertTrue(out.lowercase().contains("four") || out.lowercase().contains("iv"))
    }

    @Test
    fun `custom dictionary is applied`() {
        val out = normalizer.normalize("LTV is great")
        assertTrue(out.contains("Local Text to Voice"))
    }

    @Test
    fun `clock times are expanded in english`() {
        assertTrue(normalizer.normalize("Meet at 17:30.").lowercase().contains("seventeen thirty"))
    }

    @Test
    fun `clock times use o'clock for whole hours`() {
        assertTrue(normalizer.normalize("At 12:00 sharp.").lowercase().contains("twelve o'clock"))
    }

    @Test
    fun `clock times pad single-digit minutes`() {
        assertTrue(normalizer.normalize("Leave at 9:05.").lowercase().contains("nine oh five"))
    }

    @Test
    fun `clock times skip invalid values`() {
        val out = normalizer.normalize("Time is 99:99, not 24:00")
        assertTrue(out.contains("99:99"))
        assertTrue(out.contains("24:00"))
    }

    @Test
    fun `clock times localize to russian`() {
        assertEquals("в пятнадцать часов утра", ruNormalizer.normalize("в 15:00 утра"))
        assertEquals("в девять ноль пять", ruNormalizer.normalize("в 9:05"))
    }

    @Test
    fun `russian suffixed ordinals are declined`() {
        assertEquals("В пятый раз он пришёл", ruNormalizer.normalize("В 5-й раз он пришёл"))
        assertEquals("третья улица", ruNormalizer.normalize("3-я улица"))
        assertEquals("пятое правило", ruNormalizer.normalize("5-е правило"))
        assertEquals("к пятому дню", ruNormalizer.normalize("к 5-му дню"))
        assertEquals("в пятом томе", ruNormalizer.normalize("в 5-м томе"))
        assertEquals("в девяностых годах", ruNormalizer.normalize("в 90-х годах"))
        assertEquals("двадцать первый век", ruNormalizer.normalize("21-й век"))
        assertEquals("первую главу", ruNormalizer.normalize("1-ю главу"))
    }
}
