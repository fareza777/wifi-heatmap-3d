package com.sinyal.app.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.sinyal.app.R
import java.util.Locale

/**
 * Which language the app should speak, regardless of the phone's own setting.
 *
 * English is the app's default resource set, so [SYSTEM] on a phone set to any
 * language we do not ship falls back to English on its own. This exists for the
 * case the system cannot serve: reading the app in English on a phone that is
 * set to Indonesian, or the reverse.
 */
enum class AppLanguage(@StringRes val label: Int, val tag: String?) {
    SYSTEM(R.string.language_system, null),
    ENGLISH(R.string.language_english, "en"),

    // Android resolves Indonesian under the legacy code "in", which is also what
    // Locale("id") normalises to at runtime — either spelling reaches values-in.
    INDONESIAN(R.string.language_indonesian, "in"),
    ;

    companion object {

        /**
         * Rebuilds [context] so resource lookups resolve in the chosen language.
         *
         * Applied in `attachBaseContext`, which is the only hook that runs before
         * the Activity resolves any resource. The framework's per-app language
         * API would be cleaner but only exists from Android 13, and this app runs
         * back to Android 8.
         */
        fun wrap(context: Context, language: AppLanguage): Context {
            // recreate() does not restart the process, so a previously chosen
            // language would otherwise survive in Locale.getDefault() and keep
            // formatting dates in a language the user just switched away from.
            val locale = language.tag?.let(Locale::forLanguageTag)
                ?: context.resources.configuration.locales[0]
            Locale.setDefault(locale)

            if (language.tag == null) return context

            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            return context.createConfigurationContext(configuration)
        }
    }
}
