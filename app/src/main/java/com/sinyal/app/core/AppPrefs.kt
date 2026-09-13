package com.sinyal.app.core

import android.content.Context
import com.sinyal.app.ui.theme.AccentPalette
import com.sinyal.app.ui.theme.ThemeMode

/**
 * The handful of flags that must outlive a process death.
 *
 * SharedPreferences rather than DataStore: there is exactly one boolean here,
 * and it is read synchronously while deciding the start destination, before any
 * coroutine scope exists to await a flow.
 */
class AppPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /**
     * Dark by default.
     *
     * The screen this app is built around is a 3D map on a black ground, and a
     * light chrome around it both fights the model for attention and glares in
     * the half-lit corners where weak signal actually lives.
     */
    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.DARK.name).orEmpty())
        }.getOrDefault(ThemeMode.DARK)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /**
     * Local mirror of the Play entitlement, so ads stay hidden on launch before
     * the billing service has answered. Play is still the authority and
     * overwrites this in both directions on every connection.
     */
    var adsRemoved: Boolean
        get() = prefs.getBoolean(KEY_ADS_REMOVED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()

    /**
     * Follows the phone by default, which lands on English for any language the
     * app does not ship. Set explicitly only when someone wants to read the app
     * in a language their phone is not set to.
     */
    var language: AppLanguage
        get() = runCatching {
            AppLanguage.valueOf(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name).orEmpty())
        }.getOrDefault(AppLanguage.SYSTEM)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    var accent: AccentPalette
        get() = runCatching {
            AccentPalette.valueOf(prefs.getString(KEY_ACCENT, AccentPalette.VIOLET.name).orEmpty())
        }.getOrDefault(AccentPalette.VIOLET)
        set(value) = prefs.edit().putString(KEY_ACCENT, value.name).apply()

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_LANGUAGE = "language"
        const val KEY_ADS_REMOVED = "ads_removed"
        const val KEY_THEME = "theme_mode"
        const val NAME = "sinyal_prefs"
        const val KEY_ONBOARDED = "has_seen_onboarding"
    }
}
