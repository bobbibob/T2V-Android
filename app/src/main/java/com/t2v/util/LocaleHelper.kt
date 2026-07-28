package com.t2v.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Утилита для смены локали приложения.
 *
 * Соответствует `ui_language` в настройках и 11 локализованным
 * strings.xml в `res/values-{lang}/`.
 */
object LocaleHelper {

    /** Применяет локаль к контексту (для использования в attachBaseContext). */
    fun wrap(context: Context, language: String): Context {
        val locale = parseLocale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun parseLocale(tag: String): Locale {
        return when (tag.lowercase()) {
            "ru" -> Locale("ru")
            "es" -> Locale("es")
            "fr" -> Locale("fr")
            "de" -> Locale("de")
            "it" -> Locale("it")
            "pt" -> Locale("pt")
            "zh" -> Locale("zh")
            "ja" -> Locale("ja")
            "hi" -> Locale("hi")
            "ar" -> Locale("ar")
            else -> Locale.ENGLISH
        }
    }
}
