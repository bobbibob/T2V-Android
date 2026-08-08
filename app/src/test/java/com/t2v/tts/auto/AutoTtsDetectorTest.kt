package com.t2v.tts.auto

import com.t2v.tts.VoiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTtsDetectorTest {

    @Test
    fun `russian text is detected`() {
        val hint = AutoTtsDetector.detect("Он не мог понять, что происходит вокруг него, и тихо ждал.")
        assertEquals("ru-RU", hint.bcp47)
        assertTrue(hint.confidence > 0.5)
    }

    @Test
    fun `ukrainian text is told apart from russian`() {
        val hint = AutoTtsDetector.detect("Це не так, що вони прийшли додому, а потім пішли геть.")
        assertEquals("uk-UA", hint.bcp47)
    }

    @Test
    fun `latin script texts are ranked by function words`() {
        assertLang("The quick brown fox jumps over the lazy dog and then rests.", "en-US")
        assertLang("Der Mann und die Frau gehen in das Haus, aber er ist nicht da.", "de-DE")
        assertLang("Le chat et la souris sont dans la maison, mais je ne les vois pas.", "fr-FR")
        assertLang("El perro y el gato son grandes, pero no estan en la calle.", "es-ES")
        assertLang("Il gatto e il cane non sono qui, che cosa succede oggi?", "it-IT")
        assertLang("De man en de vrouw zijn thuis, maar ik zie hen niet in het huis.", "nl-NL")
        assertLang("O homem e a mulher estao na casa, mas nao vejo ninguem agora.", "pt-BR")
    }

    @Test
    fun `non-latin scripts are detected by their alphabet`() {
        assertLang("Ο άνδρας και η γυναίκα είναι στο σπίτι.", "el-GR")
        assertLang("الرجل والمرأة في المنزل", "ar")
        assertLang("من گفتم که میخواهم بروم", "fa-IR")
        assertLang("这是一个测试，让我们看看文字。", "zh-CN")
        assertLang("これはテストです。今日はいい天気です。", "ja-JP")
        assertLang("이것은 테스트입니다. 오늘은 날씨가 좋습니다.", "ko-KR")
        assertLang("यह एक परीक्षण है।", "hi-IN")
        assertLang("এটি একটি পরীক্ষা।", "bn-IN")
    }

    @Test
    fun `every returned tag is a supported voice language`() {
        val samples = listOf(
            "Это обычное русское предложение для проверки работы детектора.",
            "The meeting starts at nine in the morning and ends around noon.",
            "Dies ist ein deutscher Satz mit einigen Substantiven und Verben.",
            "Voici une phrase française assez longue pour bien détecter la langue.",
            "Это ещё одно русское предложение для теста.",
            "今は日本語の文章です。かなと漢字が混ざっています。",
            "Hola, ¿cómo estás? Es un día maravilloso para pasear.",
        )
        for (sample in samples) {
            val hint = AutoTtsDetector.detect(sample)
            assertTrue(
                "Detector returned unsupported tag ${hint.bcp47} for '$sample'",
                AutoTtsDetector.supportedTags.contains(hint.bcp47),
            )
        }
    }

    @Test
    fun `blank input degrades to a neutral en`() {
        val hint = AutoTtsDetector.detect("   ")
        assertEquals("en", hint.bcp47)
        assertEquals(0.0, hint.confidence, 0.0001)
    }

    private fun assertLang(sample: String, expected: String) {
        val hint = AutoTtsDetector.detect(sample)
        assertEquals("detect('$sample')", expected, hint.bcp47)
    }

    @Test
    fun `voice picker matches exact language first`() {
        val voices = listOf(
            voice("ru-RU", "ru_irina"),
            voice("en-US", "en_libby"),
        )
        assertEquals("ru_irina", AutoVoicePicker.pickVoice(voices, "ru-RU")?.id)
    }

    @Test
    fun `voice picker falls back to a shared primary subtag`() {
        val voices = listOf(voice("en-GB", "en_arthur"))
        assertNotNull(AutoVoicePicker.pickVoice(voices, "en-US"))
        assertEquals("en_arthur", AutoVoicePicker.pickVoice(voices, "en-US")?.id)
    }

    @Test
    fun `voice picker returns null when nothing matches`() {
        val voices = listOf(voice("ru-RU", "ru_irina"))
        assertNull(AutoVoicePicker.pickVoice(voices, "de-DE"))
        assertNull(AutoVoicePicker.pickVoice(emptyList(), "ru-RU"))
    }

    private fun voice(language: String, id: String): VoiceInfo =
        VoiceInfo(id = id, displayName = id, language = language, engineId = "test")
}
