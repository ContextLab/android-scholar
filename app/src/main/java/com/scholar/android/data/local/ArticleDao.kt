package com.scholar.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved articles.
 * Provides methods to interact with the saved_articles table.
 */
@Dao
interface ArticleDao {

    /**
     * Get all saved articles ordered by most recently saved first.
     * @return Flow of list of saved articles for reactive updates
     */
    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC")
    fun getAllSavedArticles(): Flow<List<SavedArticle>>

    /**
     * Get a specific article by its ID.
     * @param id The article ID to search for
     * @return The saved article, or null if not found
     */
    @Query("SELECT * FROM saved_articles WHERE id = :id")
    suspend fun getArticleById(id: String): SavedArticle?

    /**
     * Check if an article is saved in the library.
     * @param id The article ID to check
     * @return Flow emitting true if the article is saved, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE id = :id)")
    fun isArticleSaved(id: String): Flow<Boolean>

    /**
     * Save an article to the library.
     * If an article with the same ID already exists, it will be replaced.
     * @param article The article to save
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveArticle(article: SavedArticle)

    /**
     * Delete a saved article.
     * @param article The article to delete
     */
    @Delete
    suspend fun deleteArticle(article: SavedArticle)

    /**
     * Delete a saved article by its ID.
     * @param id The ID of the article to delete
     */
    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun deleteArticleById(id: String)
}
