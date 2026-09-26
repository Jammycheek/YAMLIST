package com.example.yamlist.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language override (spec: 日本語/英語の2言語対応). Stored in SharedPreferences so it
 * can be read synchronously in [android.app.Activity.attachBaseContext], before Hilt or
 * Room are available. "system" follows the device locale, which already resolves
 * values/ (English) vs values-ja/ (Japanese) automatically.
 */
object LocaleHelper {
    private const val PREFS = "locale_prefs"
    private const val KEY_LANG = "app_language"

    const val SYSTEM = "system"
    const val JAPANESE = "ja"
    const val ENGLISH = "en"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, SYSTEM) ?: SYSTEM

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
    }

    /** Wrap a base context with the chosen locale (or leave as-is for "system"). */
    fun wrap(base: Context): Context {
        val lang = getLanguage(base)
        if (lang == SYSTEM) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
