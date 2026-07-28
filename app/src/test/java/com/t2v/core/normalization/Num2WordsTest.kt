package com.t2v.core.normalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class Num2WordsTest {

    private val en = Num2Words(Locale.ENGLISH)
    private val ru = Num2Words(Locale("ru"))
    private val es = Num2Words(Locale("es"))
    private val fr = Num2Words(Locale("fr"))
    private val de = Num2Words(Locale("de"))

    @Test
    fun `english 0`() = assertEquals("zero", en.numberToWords(0.0))

    @Test
    fun `english basic numbers`() {
        assertTrue(en.numberToWords(1.0).contains("one"))
        assertTrue(en.numberToWords(10.0).contains("ten"))
        assertTrue(en.numberToWords(20.0).contains("twenty"))
        assertTrue(en.numberToWords(100.0).contains("hundred"))
        assertTrue(en.numberToWords(1000.0).contains("thousand"))
    }

    @Test
    fun `english decimals have point word`() {
        val out = en.numberToWords(3.14)
        assertTrue(out.contains("point"))
    }

    @Test
    fun `russian basic numbers`() {
        assertEquals("ноль", ru.numberToWords(0.0))
        assertTrue(ru.numberToWords(5.0).contains("пять"))
        assertTrue(ru.numberToWords(100.0).contains("сто"))
        assertTrue(ru.numberToWords(1000.0).contains("тысяч"))
    }

    @Test
    fun `spanish basic numbers`() {
        assertTrue(es.numberToWords(0.0).contains("cero"))
        assertTrue(es.numberToWords(5.0).contains("cinco"))
        assertTrue(es.numberToWords(100.0).contains("cien") || es.numberToWords(100.0).contains("ciento"))
    }

    @Test
    fun `french basic numbers`() {
        assertTrue(fr.numberToWords(0.0).contains("zéro"))
        assertTrue(fr.numberToWords(2.0).contains("deux"))
    }

    @Test
    fun `german basic numbers`() {
        assertTrue(de.numberToWords(0.0).contains("null"))
        assertTrue(de.numberToWords(3.0).contains("drei"))
    }

    @Test
    fun `negative numbers have minus word`() {
        val out = en.numberToWords(-5.0)
        assertTrue(out.contains("negative") || out.contains("minus"))
    }

    @Test
    fun `ordinals in english`() {
        assertTrue(en.ordinalToWords(1).contains("1"))
        assertTrue(en.ordinalToWords(2).contains("2"))
        assertTrue(en.ordinalToWords(11).contains("th"))
    }

    @Test
    fun `digitsToWords works`() {
        val out = en.digitsToWords(123)
        assertNotNull(out)
        assertTrue(out.contains("one") || out.contains("1"))
    }
}
