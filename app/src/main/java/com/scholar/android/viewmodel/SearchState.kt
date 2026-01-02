package com.scholar.android.viewmodel

import com.scholar.android.data.model.SearchResult

/**
 * Represents the various states of a search operation.
 * Used by the UI to display appropriate content based on the current state.
 */
sealed class SearchState {

    /**
     * Initial state - no search has been performed yet.
     */
    object Idle : SearchState()

    /**
     * Loading state - a search or pagination request is in progress.
     * @param isLoadingMore True if loading more results (pagination), false for new search.
     */
    data class Loading(val isLoadingMore: Boolean = false) : SearchState()

    /**
     * Success state - search completed with results.
     * @param result The search result containing articles and metadata.
     */
    data class Success(val result: SearchResult) : SearchState()

    /**
     * Error state - search failed.
     * @param message User-friendly error message.
     * @param canRetry True if the operation can be retried.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : SearchState()

    /**
     * Helper properties for checking state type.
     */
    val isLoading: Boolean
        get() = this is Loading

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isIdle: Boolean
        get() = this is Idle
}
