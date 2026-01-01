package com.scholar.android.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Centralized URL management for Google Scholar navigation.
 */
object ScholarUrls {

    const val BASE_URL = "https://scholar.google.com"

    // Main pages
    const val HOME = "$BASE_URL/"
    const val LIBRARY = "$BASE_URL/scholar?scilib=1"
    const val PROFILE = "$BASE_URL/citations?view_op=list_works&hl=en"
    const val SETTINGS = "$BASE_URL/scholar_settings"

    // Search patterns
    private const val SEARCH_PATTERN = "$BASE_URL/scholar?q=%s"

    /**
     * Generates a search URL for the given query.
     */
    fun getSearchUrl(query: String): String {
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        return String.format(SEARCH_PATTERN, encodedQuery)
    }

    /**
     * Checks if a URL belongs to Google Scholar.
     */
    fun isScholarUrl(url: String): Boolean {
        return url.contains("scholar.google.com") ||
                url.contains("scholar.google.")
    }

    /**
     * Checks if a URL is a Google login/auth URL.
     */
    fun isGoogleAuthUrl(url: String): Boolean {
        return url.contains("accounts.google.com") ||
                url.contains("google.com/signin") ||
                url.contains("google.com/ServiceLogin")
    }

    /**
     * Checks if this is an allowed external URL (for OAuth flow, etc.)
     */
    fun isAllowedExternalUrl(url: String): Boolean {
        return isGoogleAuthUrl(url) ||
                url.contains("google.com/recaptcha") ||
                url.contains("gstatic.com")
    }
}
