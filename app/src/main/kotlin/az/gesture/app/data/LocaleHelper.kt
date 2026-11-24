package az.gesture.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun getSavedLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)
        return AppLanguage.safeValueOf(prefs.getString("app_language", AppLanguage.AZ.name))
    }

    fun localeFor(language: AppLanguage): Locale {
        return Locale.forLanguageTag(language.tag)
    }

    fun wrapContext(base: Context): Context {
        val lang = getSavedLanguage(base)
        val locale = localeFor(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(config)
    }
}
