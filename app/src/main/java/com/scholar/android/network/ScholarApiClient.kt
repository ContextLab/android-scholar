package com.scholar.android.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * API client for fetching Google Scholar pages.
 * Uses OkHttp with a realistic browser User-Agent and cookie persistence.
 */
class ScholarApiClient {

    companion object {
        private const val TAG = "ScholarApiClient"
        private const val BASE_URL = "https://scholar.google.com"

        // Realistic Chrome browser User-Agent
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }

    /**
     * Simple in-memory cookie jar for session persistence.
     */
    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Remove existing cookies with same name before adding new ones
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                }
                addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val cookies = cookieStore[host] ?: return emptyList()

            // Filter out expired cookies
            val now = System.currentTimeMillis()
            val validCookies = cookies.filter { !it.expiresAt.let { exp -> exp != 0L && exp < now } }

            // Update store if we removed any expired cookies
            if (validCookies.size != cookies.size) {
                cookies.clear()
                cookies.addAll(validCookies)
            }

            return validCookies
        }

        fun clearCookies() {
            cookieStore.clear()
        }
    }

    private val cookieJar = InMemoryCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Sealed class representing the result of an API call.
     */
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val exception: Throwable, val message: String) : ApiResult<Nothing>()
    }

    /**
     * Fetches raw HTML from the given URL.
     *
     * @param url The full URL to fetch
     * @return ApiResult containing the HTML string or error details
     */
    suspend fun fetchHtml(url: String): ApiResult<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchHtml: Starting request to $url")
        try {
            // Note: Don't set Accept-Encoding manually - OkHttp handles gzip automatically
            // and only decompresses when it adds the header itself
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            val response = client.newCall(request).execute()
            Log.d(TAG, "fetchHtml: Response code ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "fetchHtml: Request failed with ${response.code} ${response.message}")
                return@withContext ApiResult.Error(
                    IOException("HTTP ${response.code}"),
                    "Request failed with status: ${response.code} ${response.message}"
                )
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "fetchHtml: Empty response body")
                return@withContext ApiResult.Error(
                    IOException("Empty response body"),
                    "Server returned empty response"
                )
            }

            Log.d(TAG, "fetchHtml: Success, body length: ${body.length}")
            ApiResult.Success(body)
        } catch (e: IOException) {
            Log.e(TAG, "fetchHtml: IOException", e)
            ApiResult.Error(e, "Network error: ${e.message ?: "Unknown error"}")
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml: Exception", e)
            ApiResult.Error(e, "Unexpected error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Performs a search query on Google Scholar.
     *
     * @param query The search query string
     * @param start The starting result index (for pagination, typically multiples of 10)
     * @return ApiResult containing the HTML of search results
     */
    suspend fun search(query: String, start: Int = 0): ApiResult<String> {
        Log.d(TAG, "search: Query='$query', start=$start")
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, "UTF-8")
        }
        val url = buildString {
            append(BASE_URL)
            append("/scholar?q=")
            append(encodedQuery)
            if (start > 0) {
                append("&start=")
                append(start)
            }
        }
        Log.d(TAG, "search: URL=$url")
        return fetchHtml(url)
    }

    /**
     * Fetches a specific Google Scholar page by relative or absolute URL.
     *
     * @param path The path or full URL to fetch
     * @return ApiResult containing the HTML
     */
    suspend fun fetchPage(path: String): ApiResult<String> {
        val url = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "$BASE_URL$path"
        }
        return fetchHtml(url)
    }

    /**
     * Fetches citation details for a specific article.
     *
     * @param clusterId The Google Scholar cluster ID for the article
     * @return ApiResult containing the HTML of citing articles
     */
    suspend fun fetchCitations(clusterId: String): ApiResult<String> {
        val url = "$BASE_URL/scholar?cites=$clusterId"
        return fetchHtml(url)
    }

    /**
     * Fetches related articles for a specific article.
     *
     * @param clusterId The Google Scholar cluster ID for the article
     * @return ApiResult containing the HTML of related articles
     */
    suspend fun fetchRelatedArticles(clusterId: String): ApiResult<String> {
        val url = "$BASE_URL/scholar?q=related:$clusterId"
        return fetchHtml(url)
    }

    /**
     * Fetches articles by a specific author.
     *
     * @param authorId The Google Scholar author ID
     * @return ApiResult containing the HTML of the author's profile
     */
    suspend fun fetchAuthorProfile(authorId: String): ApiResult<String> {
        val url = "$BASE_URL/citations?user=$authorId"
        return fetchHtml(url)
    }

    /**
     * Searches for authors by name on Google Scholar.
     * Uses the regular search with author: prefix since the citations search
     * endpoint often requires authentication.
     *
     * @param query The author name to search for
     * @return ApiResult containing the HTML of search results
     */
    suspend fun searchAuthors(query: String): ApiResult<String> {
        Log.d(TAG, "searchAuthors: Query='$query'")
        val encodedQuery = withContext(Dispatchers.IO) {
            // Use author: prefix to search for authors via regular search
            URLEncoder.encode("author:\"$query\"", "UTF-8")
        }
        // Use regular search with author: prefix - more reliable than citations endpoint
        val url = "$BASE_URL/scholar?q=$encodedQuery&hl=en"
        Log.d(TAG, "searchAuthors: URL=$url")
        return fetchHtml(url)
    }

    /**
     * Searches for authors by research interest/label on Google Scholar.
     * Uses the author search endpoint with label filter.
     *
     * @param label The research interest label to search for (e.g., "machine learning")
     * @return ApiResult containing the HTML of author search results
     */
    suspend fun searchAuthorsByLabel(label: String): ApiResult<String> {
        Log.d(TAG, "searchAuthorsByLabel: Label='$label'")
        // Google Scholar's direct author search requires authentication.
        // Instead, search for articles mentioning this author/topic and extract
        // author profiles from the results. Authors with Google Scholar profiles
        // will have clickable links to their profile pages.
        val encodedQuery = withContext(Dispatchers.IO) {
            // Quote the search term for better matching
            val searchQuery = "author:\"$label\""
            URLEncoder.encode(searchQuery, "UTF-8")
        }
        val url = "$BASE_URL/scholar?q=$encodedQuery&hl=en"
        Log.d(TAG, "searchAuthorsByLabel: Using article search URL=$url")
        return fetchHtml(url)
    }

    /**
     * Clears all stored cookies.
     * Useful for resetting session state.
     */
    fun clearSession() {
        cookieJar.clearCookies()
    }
}
