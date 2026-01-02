package com.scholar.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Utility class for managing app preferences.
 * Provides type-safe access to SharedPreferences with default values.
 * Uses PreferenceManager for compatibility with AndroidX Preference library.
 */
object AppPreferences {

    private const val PREFS_NAME = "scholar_preferences"

    // Preference keys (matching preferences.xml)
    const val KEY_DEFAULT_SORT = "default_sort"
    const val KEY_INCLUDE_PATENTS = "include_patents"
    const val KEY_INCLUDE_CITATIONS = "include_citations"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_SHOW_ABSTRACTS = "show_abstracts"
    const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"

    // Legacy keys for backward compatibility
    const val KEY_SEARCH_HISTORY_ENABLED = "search_history_enabled"
    const val KEY_LAST_SEARCH_QUERY = "last_search_query"

    // Authentication keys
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_PHOTO_URL = "user_photo_url"
    private const val KEY_USER_SCHOLAR_ID = "user_scholar_id"

    // Default values
    private const val DEFAULT_SORT = "relevance"
    private const val DEFAULT_INCLUDE_PATENTS = true
    private const val DEFAULT_INCLUDE_CITATIONS = true
    private const val DEFAULT_THEME_MODE = "system"
    private const val DEFAULT_SHOW_ABSTRACTS = true
    private const val DEFAULT_WIFI_ONLY_DOWNLOADS = false
    private const val DEFAULT_SEARCH_HISTORY_ENABLED = true

    /**
     * Gets the default shared preferences (used by AndroidX Preference library).
     */
    private fun getDefaultPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Gets legacy shared preferences (for backward compatibility).
     */
    private fun getLegacyPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets the default sort order preference.
     * @return "relevance" or "date"
     */
    fun getDefaultSort(context: Context): String {
        return getDefaultPrefs(context).getString(KEY_DEFAULT_SORT, DEFAULT_SORT) ?: DEFAULT_SORT
    }

    /**
     * Checks if patents should be included in search results.
     */
    fun shouldIncludePatents(context: Context): Boolean {
        return getDefaultPrefs(context).getBoolean(KEY_INCLUDE_PATENTS, DEFAULT_INCLUDE_PATENTS)
    }

    /**
     * Checks if citations should be included in search results.
     */
    fun shouldIncludeCitations(context: Context): Boolean {
        return getDefaultPrefs(context).getBoolean(KEY_INCLUDE_CITATIONS, DEFAULT_INCLUDE_CITATIONS)
    }

    /**
     * Gets the current theme mode setting.
     * @return One of "light", "dark", or "system"
     */
    fun getThemeMode(context: Context): String {
        return getDefaultPrefs(context).getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
    }

    /**
     * Sets the theme mode setting.
     * @param mode One of "light", "dark", or "system"
     */
    fun setThemeMode(context: Context, mode: String) {
        getDefaultPrefs(context).edit { putString(KEY_THEME_MODE, mode) }
    }

    /**
     * Checks if abstracts should be shown in search results.
     */
    fun shouldShowAbstracts(context: Context): Boolean {
        return getDefaultPrefs(context).getBoolean(KEY_SHOW_ABSTRACTS, DEFAULT_SHOW_ABSTRACTS)
    }

    /**
     * Checks if PDF downloads should only occur on WiFi.
     */
    fun isWifiOnlyDownloads(context: Context): Boolean {
        return getDefaultPrefs(context).getBoolean(KEY_WIFI_ONLY_DOWNLOADS, DEFAULT_WIFI_ONLY_DOWNLOADS)
    }

    /**
     * Gets whether search history is enabled.
     */
    fun isSearchHistoryEnabled(context: Context): Boolean {
        return getLegacyPrefs(context).getBoolean(KEY_SEARCH_HISTORY_ENABLED, DEFAULT_SEARCH_HISTORY_ENABLED)
    }

    /**
     * Sets whether search history is enabled.
     */
    fun setSearchHistoryEnabled(context: Context, enabled: Boolean) {
        getLegacyPrefs(context).edit { putBoolean(KEY_SEARCH_HISTORY_ENABLED, enabled) }
    }

    /**
     * Gets the last search query (for restoring state).
     */
    fun getLastSearchQuery(context: Context): String? {
        return getLegacyPrefs(context).getString(KEY_LAST_SEARCH_QUERY, null)
    }

    /**
     * Sets the last search query.
     */
    fun setLastSearchQuery(context: Context, query: String?) {
        getLegacyPrefs(context).edit { putString(KEY_LAST_SEARCH_QUERY, query) }
    }

    /**
     * Clears all preferences.
     */
    fun clearAll(context: Context) {
        getDefaultPrefs(context).edit { clear() }
        getLegacyPrefs(context).edit { clear() }
    }

    /**
     * Converts theme mode string to AppCompatDelegate night mode constant.
     */
    fun getThemeModeAsNightMode(context: Context): Int {
        return when (getThemeMode(context)) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    // ==================== Authentication Methods ====================

    /**
     * Checks if user is logged in with Google.
     */
    fun isLoggedIn(context: Context): Boolean {
        return getDefaultPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Gets the logged-in user's email.
     */
    fun getUserEmail(context: Context): String? {
        return getDefaultPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    /**
     * Gets the logged-in user's display name.
     */
    fun getUserName(context: Context): String? {
        return getDefaultPrefs(context).getString(KEY_USER_NAME, null)
    }

    /**
     * Gets the logged-in user's photo URL.
     */
    fun getUserPhotoUrl(context: Context): String? {
        return getDefaultPrefs(context).getString(KEY_USER_PHOTO_URL, null)
    }

    /**
     * Gets the logged-in user's Google Scholar ID (if linked).
     */
    fun getUserScholarId(context: Context): String? {
        return getDefaultPrefs(context).getString(KEY_USER_SCHOLAR_ID, null)
    }

    /**
     * Saves user authentication info after successful sign-in.
     */
    fun saveUserAuth(
        context: Context,
        email: String,
        name: String?,
        photoUrl: String?
    ) {
        getDefaultPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_PHOTO_URL, photoUrl)
        }
    }

    /**
     * Links a Google Scholar profile ID to the authenticated user.
     */
    fun linkScholarProfile(context: Context, scholarId: String) {
        getDefaultPrefs(context).edit {
            putString(KEY_USER_SCHOLAR_ID, scholarId)
        }
    }

    /**
     * Clears user authentication data on sign-out.
     */
    fun clearUserAuth(context: Context) {
        getDefaultPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_PHOTO_URL)
            // Note: We keep the Scholar ID so they can reconnect to same profile
        }
    }

    /**
     * Fully clears user authentication data including Scholar ID link.
     */
    fun clearUserAuthComplete(context: Context) {
        getDefaultPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_PHOTO_URL)
            remove(KEY_USER_SCHOLAR_ID)
        }
    }
}
