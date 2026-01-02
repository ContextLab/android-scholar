package com.scholar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scholar.android.data.model.Article
import com.scholar.android.data.model.SearchResult
import com.scholar.android.repository.ScholarRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Google Scholar search state and operations.
 * Follows Android Architecture Components best practices.
 */
class ScholarViewModel(
    application: Application,
    private val repository: ScholarRepository = ScholarRepository()
) : AndroidViewModel(application) {

    // Search state flow
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Accumulated articles flow (for pagination)
    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    // Current search query
    private val _currentQuery = MutableStateFlow<String?>(null)
    val currentQuery: StateFlow<String?> = _currentQuery.asStateFlow()

    // Current search result (for pagination info)
    private var currentSearchResult: SearchResult? = null

    // Active search job (for cancellation)
    private var searchJob: Job? = null

    /**
     * Performs a new search, clearing any previous results.
     * @param query The search query string.
     */
    fun search(query: String) {
        // Cancel any in-progress search
        searchJob?.cancel()

        // Clear previous results for new search
        _articles.value = emptyList()
        currentSearchResult = null
        _currentQuery.value = query

        // Set loading state
        _searchState.value = SearchState.Loading(isLoadingMore = false)

        searchJob = viewModelScope.launch {
            val result = repository.search(query)

            result.fold(
                onSuccess = { searchResult ->
                    currentSearchResult = searchResult
                    _articles.value = searchResult.articles
                    _searchState.value = SearchState.Success(searchResult)
                },
                onFailure = { error ->
                    _searchState.value = SearchState.Error(
                        message = error.message ?: "Search failed",
                        canRetry = true
                    )
                }
            )
        }
    }

    /**
     * Loads the next page of results if available.
     */
    fun loadNextPage() {
        val current = currentSearchResult ?: return
        val nextUrl = current.nextPageUrl ?: return

        // Don't start another pagination if one is already in progress
        if (_searchState.value is SearchState.Loading) return

        _searchState.value = SearchState.Loading(isLoadingMore = true)

        searchJob = viewModelScope.launch {
            val result = repository.loadNextPage(nextPageUrl = nextUrl)

            result.fold(
                onSuccess = { searchResult ->
                    currentSearchResult = searchResult
                    // Accumulate articles
                    _articles.value = _articles.value + searchResult.articles
                    _searchState.value = SearchState.Success(searchResult)
                },
                onFailure = { error ->
                    _searchState.value = SearchState.Error(
                        message = error.message ?: "Failed to load more results",
                        canRetry = true
                    )
                }
            )
        }
    }

    /**
     * Retries the last search operation.
     */
    fun retry() {
        val query = _currentQuery.value
        if (query != null) {
            search(query)
        }
    }

    /**
     * Clears the search state and results.
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchState.value = SearchState.Idle
        _articles.value = emptyList()
        currentSearchResult = null
        _currentQuery.value = null
    }

    /**
     * Returns true if there are more pages of results available.
     */
    fun hasNextPage(): Boolean {
        return currentSearchResult?.hasNextPage() == true
    }

    /**
     * Returns the total number of results string, if available.
     */
    fun getTotalResults(): String? {
        return currentSearchResult?.totalResults
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
