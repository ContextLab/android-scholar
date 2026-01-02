package com.scholar.android.repository

import com.scholar.android.data.model.AuthorProfile
import com.scholar.android.data.model.AuthorSearchResult
import com.scholar.android.data.model.SearchResult
import com.scholar.android.network.ScholarApiClient
import com.scholar.android.network.ScholarApiClient.ApiResult
import com.scholar.android.network.ScholarHtmlParser

/**
 * Repository for Google Scholar data operations.
 * Coordinates between the API client and HTML parser.
 */
class ScholarRepository(
    private val apiClient: ScholarApiClient = ScholarApiClient(),
    private val parser: ScholarHtmlParser = ScholarHtmlParser()
) {

    /**
     * Performs a search query and returns parsed results.
     * @param query The search query string.
     * @return Result containing SearchResult or an error.
     */
    suspend fun search(query: String): Result<SearchResult> {
        if (query.isBlank()) {
            return Result.failure(IllegalArgumentException("Search query cannot be empty"))
        }

        return try {
            when (val result = apiClient.search(query)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    val searchResult = parser.parseSearchResults(html)
                    Result.success(searchResult)
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Search failed: ${e.message}", e))
        }
    }

    /**
     * Loads the next page of results.
     * @param nextPageUrl The URL for the next page (from SearchResult.nextPageUrl).
     * @return Result containing SearchResult or an error.
     */
    suspend fun loadNextPage(nextPageUrl: String): Result<SearchResult> {
        if (nextPageUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Next page URL cannot be empty"))
        }

        return try {
            when (val result = apiClient.fetchPage(nextPageUrl)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    val searchResult = parser.parseSearchResults(html)
                    Result.success(searchResult)
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Failed to load next page: ${e.message}", e))
        }
    }

    /**
     * Searches for articles that cite the given article.
     * @param clusterId The Google Scholar cluster ID for the article.
     * @return Result containing SearchResult or an error.
     */
    suspend fun searchCitations(clusterId: String): Result<SearchResult> {
        if (clusterId.isBlank()) {
            return Result.failure(IllegalArgumentException("Cluster ID cannot be empty"))
        }

        return try {
            when (val result = apiClient.fetchCitations(clusterId)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    val searchResult = parser.parseSearchResults(html)
                    Result.success(searchResult)
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Failed to load citations: ${e.message}", e))
        }
    }

    /**
     * Searches for authors by name.
     * @param name The author name to search for.
     * @return Result containing list of AuthorSearchResult or an error.
     */
    suspend fun searchAuthors(name: String): Result<List<AuthorSearchResult>> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Author name cannot be empty"))
        }

        return try {
            when (val result = apiClient.searchAuthors(name)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    val authors = parser.parseAuthorSearchResults(html)
                    Result.success(authors)
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Author search failed: ${e.message}", e))
        }
    }

    /**
     * Searches for authors by research interest/label.
     * @param label The research interest label to search for (e.g., "machine learning").
     * @return Result containing list of AuthorSearchResult or an error.
     */
    suspend fun searchAuthorsByLabel(label: String): Result<List<AuthorSearchResult>> {
        if (label.isBlank()) {
            return Result.failure(IllegalArgumentException("Research interest cannot be empty"))
        }

        return try {
            when (val result = apiClient.searchAuthorsByLabel(label)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    val authors = parser.parseAuthorSearchResults(html)

                    // Filter and sort authors by relevance to search query
                    val filteredAuthors = filterAndSortAuthorsByRelevance(authors, label)
                    Result.success(filteredAuthors)
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Author search by interest failed: ${e.message}", e))
        }
    }

    /**
     * Filters and sorts authors by relevance to the search query.
     * Prioritizes exact name matches, then partial matches, removing unrelated co-authors.
     */
    private fun filterAndSortAuthorsByRelevance(
        authors: List<AuthorSearchResult>,
        query: String
    ): List<AuthorSearchResult> {
        val queryLower = query.lowercase().trim()
        val queryParts = queryLower.split(" ").filter { it.isNotBlank() }

        // Score each author by relevance
        val scoredAuthors = authors.mapNotNull { author ->
            val nameLower = author.name.lowercase()
            val score = calculateRelevanceScore(nameLower, queryLower, queryParts)
            if (score > 0) Pair(author, score) else null
        }

        // Sort by score (highest first) and return just the authors
        return scoredAuthors
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id } // Remove duplicates by ID
    }

    /**
     * Calculates a relevance score for an author name against the search query.
     * Returns 0 if the name doesn't match at all.
     */
    private fun calculateRelevanceScore(
        nameLower: String,
        queryLower: String,
        queryParts: List<String>
    ): Int {
        // Exact match (highest priority)
        if (nameLower == queryLower) return 100

        // Name contains the full query
        if (nameLower.contains(queryLower)) return 80

        // Query contains the full name (e.g., searching "K Gurney" matches "Gurney")
        if (queryLower.contains(nameLower)) return 70

        // Check if any significant part of query matches name
        var partScore = 0
        for (part in queryParts) {
            if (part.length >= 2) { // Only check meaningful parts
                when {
                    nameLower.contains(part) -> partScore += 30
                    part.contains(nameLower.split(" ").lastOrNull() ?: "") -> partScore += 20
                }
            }
        }

        // Check if last name matches (common case: "K Gurney" -> "Gurney")
        val nameParts = nameLower.split(" ")
        val queryLastPart = queryParts.lastOrNull() ?: ""
        if (queryLastPart.length >= 2) {
            for (namePart in nameParts) {
                if (namePart.contains(queryLastPart) || queryLastPart.contains(namePart)) {
                    partScore += 40
                    break
                }
            }
        }

        return partScore
    }

    /**
     * Fetches an author's profile by their Google Scholar ID.
     * @param authorId The Google Scholar author ID (e.g., from the URL user= parameter).
     * @return Result containing AuthorProfile or an error.
     */
    suspend fun getAuthorProfile(authorId: String): Result<AuthorProfile> {
        if (authorId.isBlank()) {
            return Result.failure(IllegalArgumentException("Author ID cannot be empty"))
        }

        return try {
            when (val result = apiClient.fetchAuthorProfile(authorId)) {
                is ApiResult.Success -> {
                    val html = result.data

                    // Check if we're blocked by CAPTCHA
                    if (parser.isBlocked(html)) {
                        return Result.failure(
                            RepositoryException("Google Scholar is requesting verification. Please try again later.")
                        )
                    }

                    // Check if it's a valid author profile page
                    if (!parser.isAuthorProfilePage(html)) {
                        return Result.failure(
                            RepositoryException("Could not find author profile. Please check the Author ID.")
                        )
                    }

                    val profile = parser.parseAuthorProfile(html, authorId)
                    if (profile != null && profile.isValid()) {
                        Result.success(profile)
                    } else {
                        Result.failure(RepositoryException("Could not parse author profile."))
                    }
                }
                is ApiResult.Error -> {
                    Result.failure(RepositoryException(result.message, result.exception))
                }
            }
        } catch (e: Exception) {
            Result.failure(RepositoryException("Failed to load author profile: ${e.message}", e))
        }
    }

    /**
     * Clears the API session (cookies).
     */
    fun clearSession() {
        apiClient.clearSession()
    }
}

/**
 * Custom exception for repository-level errors.
 */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
