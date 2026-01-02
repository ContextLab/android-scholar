package com.scholar.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Scholar app.
 * Contains the saved_articles table for locally stored articles.
 */
@Database(entities = [SavedArticle::class], version = 1, exportSchema = false)
abstract class ScholarDatabase : RoomDatabase() {

    /**
     * Get the ArticleDao for interacting with saved articles.
     */
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: ScholarDatabase? = null

        /**
         * Get the singleton database instance.
         * Creates the database if it doesn't exist.
         *
         * @param context Application context
         * @return The ScholarDatabase instance
         */
        fun getDatabase(context: Context): ScholarDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ScholarDatabase::class.java,
                    "scholar_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
