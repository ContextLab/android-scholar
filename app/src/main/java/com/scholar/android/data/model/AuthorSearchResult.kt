package com.scholar.android.data.model

/**
 * Represents an author search result from Google Scholar.
 * This is a simplified version of AuthorProfile used for displaying search results.
 *
 * @property id The unique Google Scholar author ID
 * @property name The author's display name
 * @property affiliation The author's institutional affiliation
 * @property citedBy Total citation count (displayed as "Cited by X")
 * @property imageUrl URL to the author's profile thumbnail
 * @property interests List of research interests/topics (usually up to 3)
 */
data class AuthorSearchResult(
    val id: String,
    val name: String,
    val affiliation: String?,
    val citedBy: Int,
    val imageUrl: String?,
    val interests: List<String>
) {
    /**
     * Returns a formatted citation count string
     */
    fun getFormattedCitedBy(): String {
        return when {
            citedBy >= 1000000 -> String.format("%.1fM", citedBy / 1000000.0)
            citedBy >= 1000 -> String.format("%.1fK", citedBy / 1000.0)
            else -> citedBy.toString()
        }
    }

    /**
     * Returns interests as a comma-separated string
     */
    fun getInterestsString(): String = interests.take(3).joinToString(", ")
}
