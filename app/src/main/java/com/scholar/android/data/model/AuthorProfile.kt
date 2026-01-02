package com.scholar.android.data.model

/**
 * Represents a single year's citation data.
 *
 * @property year The year (e.g., 2023)
 * @property citations Number of citations in that year
 */
data class YearlyCitation(
    val year: Int,
    val citations: Int
)

/**
 * Represents a Google Scholar author profile.
 *
 * @property id The unique Google Scholar author ID
 * @property name The author's display name
 * @property affiliation The author's institutional affiliation
 * @property email The author's email (if publicly visible)
 * @property imageUrl URL to the author's profile image
 * @property citationCount Total number of citations
 * @property hIndex The author's h-index
 * @property i10Index The author's i10-index
 * @property interests List of research interests/topics
 * @property articles List of the author's publications
 * @property citationHistory List of citations per year
 */
data class AuthorProfile(
    val id: String,
    val name: String,
    val affiliation: String?,
    val email: String?,
    val imageUrl: String?,
    val citationCount: Int,
    val hIndex: Int,
    val i10Index: Int,
    val interests: List<String>,
    val articles: List<Article>,
    val citationHistory: List<YearlyCitation> = emptyList()
) {
    companion object {
        /**
         * Creates an empty author profile for placeholder purposes.
         */
        fun empty(): AuthorProfile = AuthorProfile(
            id = "",
            name = "",
            affiliation = null,
            email = null,
            imageUrl = null,
            citationCount = 0,
            hIndex = 0,
            i10Index = 0,
            interests = emptyList(),
            articles = emptyList(),
            citationHistory = emptyList()
        )
    }

    /**
     * Returns the formatted citation count string
     */
    fun getFormattedCitationCount(): String {
        return when {
            citationCount >= 1000000 -> String.format("%.1fM", citationCount / 1000000.0)
            citationCount >= 1000 -> String.format("%.1fK", citationCount / 1000.0)
            else -> citationCount.toString()
        }
    }

    /**
     * Returns interests as a comma-separated string
     */
    fun getInterestsString(): String = interests.joinToString(", ")

    /**
     * Checks if this is a valid profile with required data
     */
    fun isValid(): Boolean = id.isNotBlank() && name.isNotBlank()
}
