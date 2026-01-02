package com.scholar.android.ui.results

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scholar.android.data.local.ScholarDatabase
import com.scholar.android.data.model.Article
import com.scholar.android.data.model.SearchResult
import com.scholar.android.repository.LibraryRepository
import com.scholar.android.repository.ScholarRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Results screen, managing search state, pagination, and article list.
 */
class ResultsViewModel(
    application: Application,
    private val repository: ScholarRepository
) : AndroidViewModel(application) {

    // Library repository for save functionality
    private val database = ScholarDatabase.getDatabase(application)
    private val libraryRepository = LibraryRepository(database.articleDao())

    // Saved article IDs for showing saved state in the UI
    val savedArticleIds: StateFlow<Set<String>> = libraryRepository.getSavedArticles()
        .map { articles -> articles.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // UI State
    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Initial)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    // Articles list
    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Pagination
    private val _hasMoreResults = MutableStateFlow(false)
    val hasMoreResults: StateFlow<Boolean> = _hasMoreResults.asStateFlow()

    private var nextPageUrl: String? = null
    private var currentQuery = ""
    private var searchJob: Job? = null

    // Sort order
    private val _sortOrder = MutableStateFlow(SortOrder.RELEVANCE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Error handling
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Results info (e.g., "About 12,300 results")
    private val _resultsInfo = MutableStateFlow("")
    val resultsInfo: StateFlow<String> = _resultsInfo.asStateFlow()

    // Citations mode
    private val _citationsMode = MutableStateFlow<String?>(null)
    val citationsMode: StateFlow<String?> = _citationsMode.asStateFlow()

    /**
     * Performs a new search, resetting pagination.
     */
    fun search(query: String) {
        if (query.isBlank()) return

        // Cancel any existing search
        searchJob?.cancel()

        currentQuery = query
        _citationsMode.value = null
        _articles.value = emptyList()
        _uiState.value = ResultsUiState.Loading
        _isLoading.value = true

        searchJob = viewModelScope.launch {
            performSearch(query, isNewSearch = true)
        }
    }

    /**
     * Loads articles that cite the given article.
     */
    fun searchCitations(article: Article) {
        // The article.id should be the cluster ID from Google Scholar
        val clusterId = article.id
        if (clusterId.isBlank()) {
            _errorMessage.value = "Cannot find citations for this article"
            return
        }

        // Cancel any existing search
        searchJob?.cancel()

        currentQuery = ""
        _citationsMode.value = "Citations of: ${article.title}"
        _articles.value = emptyList()
        _uiState.value = ResultsUiState.Loading
        _isLoading.value = true

        searchJob = viewModelScope.launch {
            repository.searchCitations(clusterId).fold(
                onSuccess = { result ->
                    handleSearchResults(result, isNewSearch = true)
                },
                onFailure = { error ->
                    handleError(Exception(error.message ?: "Failed to load citations"))
                }
            )
        }
    }

    /**
     * Loads the next page of results for infinite scroll.
     */
    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMoreResults.value) return

        val url = nextPageUrl ?: return

        _isLoadingMore.value = true

        searchJob = viewModelScope.launch {
            repository.loadNextPage(url).fold(
                onSuccess = { result ->
                    handleSearchResults(result, isNewSearch = false)
                },
                onFailure = { error ->
                    _isLoadingMore.value = false
                    _errorMessage.value = error.message ?: "Failed to load more results"
                }
            )
        }
    }

    /**
     * Refreshes the current search results.
     */
    fun refresh() {
        if (currentQuery.isBlank() && _citationsMode.value == null) {
            _isLoading.value = false
            return
        }

        _isLoading.value = true

        searchJob = viewModelScope.launch {
            if (_citationsMode.value != null) {
                // Re-run citations search - would need to track the article
                // For now, just re-run the regular search if we have a query
                if (currentQuery.isNotBlank()) {
                    performSearch(currentQuery, isNewSearch = true)
                } else {
                    _isLoading.value = false
                }
            } else {
                performSearch(currentQuery, isNewSearch = true)
            }
        }
    }

    /**
     * Retries the last failed operation.
     */
    fun retry() {
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }

    /**
     * Toggles between relevance and date sort order.
     */
    fun toggleSortOrder() {
        _sortOrder.value = when (_sortOrder.value) {
            SortOrder.RELEVANCE -> SortOrder.DATE
            SortOrder.DATE -> SortOrder.RELEVANCE
        }
        // Re-search with new sort order
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }

    /**
     * Toggles the saved state of an article.
     * If already saved, removes it. If not saved, saves it.
     */
    fun toggleSaveState(article: Article) {
        viewModelScope.launch {
            val isSaved = savedArticleIds.value.contains(article.id)
            libraryRepository.toggleSaveState(article, isSaved)
        }
    }

    /**
     * Saves an article to the user's library.
     */
    fun saveToLibrary(article: Article) {
        viewModelScope.launch {
            libraryRepository.saveArticle(article)
        }
    }

    /**
     * Checks if an article is saved.
     */
    fun isArticleSaved(articleId: String): Boolean {
        return savedArticleIds.value.contains(articleId)
    }

    /**
     * Loads articles that cite the given article.
     */
    fun loadCitationsFor(article: Article) {
        searchCitations(article)
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Performs the actual search using the repository.
     */
    private suspend fun performSearch(query: String, isNewSearch: Boolean) {
        if (isNewSearch) {
            _articles.value = emptyList()
            nextPageUrl = null
        }

        repository.search(query).fold(
            onSuccess = { result ->
                handleSearchResults(result, isNewSearch)
            },
            onFailure = { error ->
                handleError(Exception(error.message ?: "Search failed"))
            }
        )
    }

    private fun handleSearchResults(result: SearchResult, isNewSearch: Boolean) {
        _isLoading.value = false
        _isLoadingMore.value = false

        if (isNewSearch) {
            _articles.value = result.articles
        } else {
            _articles.value = _articles.value + result.articles
        }

        nextPageUrl = result.nextPageUrl
        _hasMoreResults.value = result.hasNextPage()
        _resultsInfo.value = result.totalResults ?: ""

        _uiState.value = when {
            _articles.value.isEmpty() -> ResultsUiState.Empty
            else -> ResultsUiState.Content
        }
    }

    private fun handleError(e: Exception) {
        _isLoading.value = false
        _isLoadingMore.value = false
        _errorMessage.value = e.message ?: "An error occurred"

        if (_articles.value.isEmpty()) {
            _uiState.value = ResultsUiState.Error
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}

/**
 * Factory for creating ResultsViewModel with repository injection.
 */
class ResultsViewModelFactory(
    private val application: Application,
    private val repository: ScholarRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResultsViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
