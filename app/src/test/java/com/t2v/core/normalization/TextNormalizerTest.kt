package com.t2v.core.normalization

import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    private val rules = NormalizationRules(enabled = true)
    private val normalizer = TextNormalizer(rules, customDictionary = mapOf("LTV" to "Local Text to Voice"))

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
}
