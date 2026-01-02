package com.scholar.android.data.model

/**
 * Represents the result of a Google Scholar search query.
 *
 * @property articles List of articles returned in this search result page
 * @property totalResults Human-readable string of total results (e.g., "About 1,234,000 results")
 * @property nextPageUrl URL to fetch the next page of results, null if no more pages
 */
data class SearchResult(
    val articles: List<Article>,
    val totalResults: String?,
    val nextPageUrl: String?
) {
    companion object {
        /**
         * Creates an empty search result.
         */
        fun empty(): SearchResult = SearchResult(
            articles = emptyList(),
            totalResults = null,
            nextPageUrl = null
        )
    }

    /**
     * Returns true if there are more pages of results available.
     */
    fun hasNextPage(): Boolean = nextPageUrl != null

    /**
     * Returns true if no articles were found.
     */
    fun isEmpty(): Boolean = articles.isEmpty()

    /**
     * Returns the number of articles in this result page.
     */
    val count: Int get() = articles.size
}
