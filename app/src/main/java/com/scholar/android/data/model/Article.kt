package com.scholar.android.data.model

/**
 * Represents a scholarly article from Google Scholar search results.
 *
 * @property id Unique identifier for the article (typically derived from the Google Scholar cluster ID)
 * @property title The article's title
 * @property authors List of author names
 * @property year Publication year, if available
 * @property citationCount Number of times this article has been cited
 * @property snippet Brief excerpt or abstract snippet from the article
 * @property pdfUrl Direct URL to PDF version, if available
 * @property articleUrl URL to the article's main page
 * @property source The publication source (e.g., "arxiv.org", "ieee.org")
 */
data class Article(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: String?,
    val citationCount: Int,
    val snippet: String,
    val pdfUrl: String?,
    val articleUrl: String,
    val source: String?
) {
    companion object {
        /**
         * Creates an empty article for placeholder purposes.
         */
        fun empty(): Article = Article(
            id = "",
            title = "",
            authors = emptyList(),
            year = null,
            citationCount = 0,
            snippet = "",
            pdfUrl = null,
            articleUrl = "",
            source = null
        )
    }

    /**
     * Returns authors as a comma-separated string.
     */
    fun getAuthorsString(): String = authors.joinToString(", ")

    /**
     * Returns a formatted citation string (e.g., "Source - Year")
     */
    fun getFormattedSource(): String {
        return listOfNotNull(source, year).joinToString(" - ")
    }

    /**
     * Returns formatted citation count string
     */
    fun getFormattedCitationCount(): String {
        return when {
            citationCount >= 1000 -> String.format("%.1fK", citationCount / 1000.0)
            else -> citationCount.toString()
        }
    }
}
