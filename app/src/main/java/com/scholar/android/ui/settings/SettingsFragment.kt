package com.scholar.android.ui.settings

import android.os.Bundle
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.BuildConfig
import com.scholar.android.R

/**
 * Settings fragment that displays user preferences using AndroidX Preference library.
 * Handles theme changes, cache clearing, and other app settings.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        setupVersionPreference()
        setupClearCachePreference()
        setupThemePreference()
    }

    private fun setupVersionPreference() {
        findPreference<Preference>("app_version")?.apply {
            summary = BuildConfig.VERSION_NAME
        }
    }

    private fun setupClearCachePreference() {
        findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
            clearCache()
            true
        }
    }

    private fun setupThemePreference() {
        findPreference<Preference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            applyTheme(newValue as String)
            true
        }
    }

    private fun clearCache() {
        // Clear WebView cookies
        CookieManager.getInstance().removeAllCookies { success ->
            if (success) {
                CookieManager.getInstance().flush()
            }
        }

        // Clear WebView cache
        activity?.applicationContext?.cacheDir?.deleteRecursively()

        // Show confirmation
        view?.let { view ->
            Snackbar.make(view, R.string.cache_cleared, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme(themeMode: String) {
        val mode = when (themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }
}
