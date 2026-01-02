package com.scholar.android.repository

import com.scholar.android.data.local.ArticleDao
import com.scholar.android.data.local.SavedArticle
import com.scholar.android.data.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing saved articles in the local library.
 * Wraps ArticleDao operations and provides conversion between Article and SavedArticle.
 */
class LibraryRepository(private val articleDao: ArticleDao) {

    /**
     * Get all saved articles as Article objects.
     * @return Flow of list of articles for reactive updates
     */
    fun getSavedArticles(): Flow<List<Article>> {
        return articleDao.getAllSavedArticles().map { savedArticles ->
            savedArticles.map { it.toArticle() }
        }
    }

    /**
     * Get all saved articles as SavedArticle objects (with savedAt timestamp).
     * @return Flow of list of saved articles for reactive updates
     */
    fun getSavedArticlesWithMetadata(): Flow<List<SavedArticle>> {
        return articleDao.getAllSavedArticles()
    }

    /**
     * Check if an article is saved in the library.
     * @param articleId The article ID to check
     * @return Flow emitting true if the article is saved, false otherwise
     */
    fun isArticleSaved(articleId: String): Flow<Boolean> {
        return articleDao.isArticleSaved(articleId)
    }

    /**
     * Save an article to the library.
     * @param article The article to save
     */
    suspend fun saveArticle(article: Article) {
        articleDao.saveArticle(article.toSavedArticle())
    }

    /**
     * Remove an article from the library.
     * @param article The article to remove
     */
    suspend fun removeArticle(article: Article) {
        articleDao.deleteArticleById(article.id)
    }

    /**
     * Remove an article from the library by ID.
     * @param articleId The ID of the article to remove
     */
    suspend fun removeArticleById(articleId: String) {
        articleDao.deleteArticleById(articleId)
    }

    /**
     * Toggle the saved state of an article.
     * If saved, it will be removed. If not saved, it will be added.
     * @param article The article to toggle
     * @param isSaved Current saved state
     */
    suspend fun toggleSaveState(article: Article, isSaved: Boolean) {
        if (isSaved) {
            removeArticle(article)
        } else {
            saveArticle(article)
        }
    }

    companion object {
        /**
         * Convert Article to SavedArticle for database storage.
         */
        private fun Article.toSavedArticle(): SavedArticle {
            return SavedArticle(
                id = id,
                title = title,
                authors = authors.joinToString(", "),
                year = year,
                citationCount = citationCount,
                snippet = snippet,
                pdfUrl = pdfUrl,
                articleUrl = articleUrl,
                source = source
            )
        }

        /**
         * Convert SavedArticle to Article for UI display.
         */
        private fun SavedArticle.toArticle(): Article {
            return Article(
                id = id,
                title = title,
                authors = if (authors.isBlank()) emptyList() else authors.split(", "),
                year = year,
                citationCount = citationCount,
                snippet = snippet,
                pdfUrl = pdfUrl,
                articleUrl = articleUrl,
                source = source
            )
        }
    }
}
