package com.scholar.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an article saved to the user's local library.
 *
 * @property id Unique identifier for the article (typically derived from the Google Scholar cluster ID)
 * @property title The article's title
 * @property authors Comma-separated list of author names
 * @property year Publication year, if available
 * @property citationCount Number of times this article has been cited
 * @property snippet Brief excerpt or abstract snippet from the article
 * @property pdfUrl Direct URL to PDF version, if available
 * @property articleUrl URL to the article's main page
 * @property source The publication source (e.g., "arxiv.org", "ieee.org")
 * @property savedAt Timestamp when the article was saved (milliseconds since epoch)
 */
@Entity(tableName = "saved_articles")
data class SavedArticle(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String,  // Comma-separated string
    val year: String?,
    val citationCount: Int,
    val snippet: String,
    val pdfUrl: String?,
    val articleUrl: String,
    val source: String?,
    val savedAt: Long = System.currentTimeMillis()
)
