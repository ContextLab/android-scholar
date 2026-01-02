package com.scholar.android.data.model

/**
 * Represents detailed article information parsed from a Google Scholar article page.
 * Contains extended metadata like abstract, full citation info, and related articles.
 *
 * @property id Unique identifier for the article
 * @property title The article's full title
 * @property authors List of author names
 * @property year Publication year, if available
 * @property venue The publication venue (journal, conference, etc.)
 * @property abstract The article's abstract or summary
 * @property citationCount Number of times this article has been cited
 * @property pdfUrl Direct URL to PDF version, if available
 * @property articleUrl URL to the article's main page
 * @property citedByUrl URL to view all citing articles
 * @property relatedUrl URL to view related articles
 * @property citingArticles List of articles that cite this one (preview)
 * @property relatedArticles List of related articles (preview)
 * @property bibtex BibTeX citation format
 * @property clusterId Google Scholar cluster ID for this article
 */
data class ArticleDetail(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: String?,
    val venue: String?,
    val abstract: String?,
    val citationCount: Int,
    val pdfUrl: String?,
    val articleUrl: String,
    val citedByUrl: String?,
    val relatedUrl: String?,
    val citingArticles: List<Article> = emptyList(),
    val relatedArticles: List<Article> = emptyList(),
    val bibtex: String? = null,
    val clusterId: String? = null
) {
    /**
     * Returns authors as a comma-separated string.
     */
    fun getAuthorsString(): String = authors.joinToString(", ")

    /**
     * Returns a formatted venue/year string.
     */
    fun getFormattedVenue(): String {
        return listOfNotNull(venue, year).joinToString(" - ")
    }

    /**
     * Generates an MLA format citation.
     */
    fun getMlaCitation(): String {
        val authorsFormatted = when {
            authors.isEmpty() -> ""
            authors.size == 1 -> "${authors[0]}. "
            authors.size == 2 -> "${authors[0]}, and ${authors[1]}. "
            else -> "${authors[0]}, et al. "
        }
        val yearPart = year?.let { " ($it)" } ?: ""
        val venuePart = venue?.let { " $it." } ?: ""
        return "$authorsFormatted\"$title.\"$venuePart$yearPart"
    }

    /**
     * Generates an APA format citation.
     */
    fun getApaCitation(): String {
        val authorsFormatted = when {
            authors.isEmpty() -> ""
            authors.size == 1 -> "${formatAuthorApa(authors[0])} "
            authors.size == 2 -> "${formatAuthorApa(authors[0])}, & ${formatAuthorApa(authors[1])} "
            else -> "${formatAuthorApa(authors[0])}, et al. "
        }
        val yearPart = year?.let { "($it). " } ?: ""
        val venuePart = venue?.let { " $it." } ?: ""
        return "$authorsFormatted$yearPart$title.$venuePart"
    }

    /**
     * Generates a Chicago format citation.
     */
    fun getChicagoCitation(): String {
        val authorsFormatted = when {
            authors.isEmpty() -> ""
            authors.size == 1 -> "${authors[0]}. "
            authors.size == 2 -> "${authors[0]} and ${authors[1]}. "
            else -> "${authors[0]} et al. "
        }
        val yearPart = year ?: "n.d."
        val venuePart = venue?.let { " $it" } ?: ""
        return "$authorsFormatted\"$title.\"$venuePart ($yearPart)."
    }

    /**
     * Generates a Harvard format citation.
     */
    fun getHarvardCitation(): String {
        val authorsFormatted = when {
            authors.isEmpty() -> ""
            authors.size == 1 -> "${formatAuthorHarvard(authors[0])}"
            authors.size == 2 -> "${formatAuthorHarvard(authors[0])} and ${formatAuthorHarvard(authors[1])}"
            else -> "${formatAuthorHarvard(authors[0])} et al."
        }
        val yearPart = year?.let { " ($it)" } ?: ""
        val venuePart = venue?.let { " $it." } ?: ""
        return "$authorsFormatted$yearPart. $title.$venuePart"
    }

    /**
     * Generates a BibTeX format citation or returns the parsed one.
     */
    fun getBibtexCitation(): String {
        if (bibtex != null) return bibtex

        // Generate a basic BibTeX entry
        val citeKey = generateCiteKey()
        val authorsFormatted = authors.joinToString(" and ")
        val yearPart = year ?: ""
        val journalPart = venue?.let { "  journal = {$it},\n" } ?: ""

        return """
@article{$citeKey,
  title = {$title},
  author = {$authorsFormatted},
  year = {$yearPart},
$journalPart}
        """.trimIndent()
    }

    private fun generateCiteKey(): String {
        val authorPart = authors.firstOrNull()
            ?.split(" ")
            ?.lastOrNull()
            ?.lowercase()
            ?.filter { it.isLetter() }
            ?: "unknown"
        val yearPart = year ?: "nd"
        val titleWord = title.split(" ")
            .firstOrNull { it.length > 3 }
            ?.lowercase()
            ?.filter { it.isLetter() }
            ?: "article"
        return "$authorPart$yearPart$titleWord"
    }

    private fun formatAuthorApa(name: String): String {
        val parts = name.trim().split(" ")
        return if (parts.size > 1) {
            val lastName = parts.last()
            val initials = parts.dropLast(1).map { "${it.first()}." }.joinToString(" ")
            "$lastName, $initials"
        } else {
            name
        }
    }

    private fun formatAuthorHarvard(name: String): String {
        val parts = name.trim().split(" ")
        return if (parts.size > 1) {
            val lastName = parts.last()
            val initials = parts.dropLast(1).map { "${it.first()}." }.joinToString("")
            "$lastName, $initials"
        } else {
            name
        }
    }

    companion object {
        /**
         * Creates an ArticleDetail from a basic Article object.
         */
        fun fromArticle(article: Article): ArticleDetail {
            return ArticleDetail(
                id = article.id,
                title = article.title,
                authors = article.authors,
                year = article.year,
                venue = article.source,
                abstract = article.snippet.takeIf { it.isNotBlank() },
                citationCount = article.citationCount,
                pdfUrl = article.pdfUrl,
                articleUrl = article.articleUrl,
                citedByUrl = null,
                relatedUrl = null
            )
        }
    }
}
