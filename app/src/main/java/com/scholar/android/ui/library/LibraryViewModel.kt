package com.scholar.android.ui.library

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scholar.android.auth.AuthState
import com.scholar.android.auth.GoogleAuthManager
import com.scholar.android.auth.SignInResult
import com.scholar.android.data.local.ScholarDatabase
import com.scholar.android.data.model.Article
import com.scholar.android.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI states for the library screen.
 */
sealed class LibraryState {
    /** User is not signed in - show sign-in prompt */
    object NotSignedIn : LibraryState()

    /** Loading library data */
    object Loading : LibraryState()

    /** Library loaded with articles */
    object HasArticles : LibraryState()

    /** Library is empty */
    object Empty : LibraryState()
}

/**
 * ViewModel for the Library screen.
 * Manages the list of saved articles and provides methods to remove articles.
 * Requires authentication to access the library.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScholarDatabase.getDatabase(application)
    private val repository = LibraryRepository(database.articleDao())

    // Google Auth Manager
    val authManager = GoogleAuthManager(application)

    // Library state
    private val _libraryState = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    // Authentication state - expose from authManager
    val authState: StateFlow<AuthState> = authManager.authState

    init {
        checkAuthAndLoadLibrary()
    }

    /**
     * Checks authentication state and loads the library if signed in.
     */
    private fun checkAuthAndLoadLibrary() {
        viewModelScope.launch {
            authManager.authState.collect { authState ->
                when (authState) {
                    is AuthState.SignedIn -> {
                        // User is signed in, start observing library
                        _libraryState.value = LibraryState.Loading
                        observeLibraryArticles()
                    }
                    is AuthState.SignedOut -> {
                        _libraryState.value = LibraryState.NotSignedIn
                    }
                    is AuthState.Unknown -> {
                        _libraryState.value = LibraryState.Loading
                    }
                    is AuthState.Error -> {
                        _libraryState.value = LibraryState.NotSignedIn
                    }
                }
            }
        }
    }

    /**
     * Observes library articles and updates state.
     */
    private fun observeLibraryArticles() {
        viewModelScope.launch {
            savedArticles.collect { articles ->
                _libraryState.value = if (articles.isEmpty()) {
                    LibraryState.Empty
                } else {
                    LibraryState.HasArticles
                }
            }
        }
    }

    /**
     * Flow of saved articles for reactive UI updates.
     */
    val savedArticles: StateFlow<List<Article>> = repository.getSavedArticles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Removes an article from the library.
     * @param article The article to remove
     */
    fun removeArticle(article: Article) {
        viewModelScope.launch {
            repository.removeArticle(article)
        }
    }

    /**
     * Saves an article to the library (used for undo functionality).
     * @param article The article to save
     */
    fun saveArticle(article: Article) {
        viewModelScope.launch {
            repository.saveArticle(article)
        }
    }

    /**
     * Check if an article is saved in the library.
     * @param articleId The article ID to check
     * @return Flow emitting true if saved, false otherwise
     */
    fun isArticleSaved(articleId: String) = repository.isArticleSaved(articleId)

    /**
     * Toggle the saved state of an article.
     * @param article The article to toggle
     * @param isSaved Current saved state
     */
    fun toggleSaveState(article: Article, isSaved: Boolean) {
        viewModelScope.launch {
            repository.toggleSaveState(article, isSaved)
        }
    }

    // ==================== Authentication Methods ====================

    /**
     * Gets the sign-in intent for launching Google Sign-In.
     */
    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    /**
     * Handles the result from Google Sign-In activity.
     * @param data The intent data from onActivityResult
     * @return SignInResult indicating success or failure
     */
    fun handleSignInResult(data: Intent?): SignInResult {
        val result = authManager.handleSignInResult(data)
        if (result is SignInResult.Success) {
            // Auth state collector will automatically update library state
        }
        return result
    }

    /**
     * Signs out the current user.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        authManager.signOut {
            onComplete()
        }
    }

    /**
     * Checks if user is currently signed in.
     */
    fun isSignedIn(): Boolean = authManager.isSignedIn()
}
