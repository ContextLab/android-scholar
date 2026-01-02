# ViewModel Integration Session - December 31, 2025

## Summary

Connected the ResultsViewModel to the actual ScholarRepository and implemented citations search functionality.

## Changes Made

### 1. ResultsViewModel (`app/src/main/java/com/scholar/android/ui/results/ResultsViewModel.kt`)
- Added ScholarRepository as a constructor parameter
- Replaced placeholder `performSearch()` with actual repository calls
- Implemented `searchCitations(article)` for citations search using the article's cluster ID
- Added `citationsMode` StateFlow to track when viewing citations
- Proper error handling with `Result.fold()`
- Cancellation of previous search jobs when starting new searches
- Pagination support using `nextPageUrl` from SearchResult

### 2. ResultsViewModelFactory
- Created factory class in the same file for dependency injection
- Implements `ViewModelProvider.Factory` to inject ScholarRepository

### 3. ResultsFragment (`app/src/main/java/com/scholar/android/ui/results/ResultsFragment.kt`)
- Updated ViewModel initialization to use `ResultsViewModelFactory` with `ScholarRepository()`
- Added `observeCitationsMode()` to update search bar when viewing citations
- Import for ScholarRepository added

### 4. ScholarRepository (`app/src/main/java/com/scholar/android/repository/ScholarRepository.kt`)
- Added `searchCitations(clusterId: String): Result<SearchResult>` method
- Uses `apiClient.fetchCitations()` to get citations for an article

### 5. AppPreferences (`app/src/main/java/com/scholar/android/util/AppPreferences.kt`)
- Created new utility class (was missing and causing build errors)
- Handles theme preferences, search history settings
- Provides `getThemeModeAsNightMode()` for theme application

### 6. Additional Fixes
- Removed unused `AppPreferences` import from SettingsFragment
- Added missing string resources for library fragment:
  - `library_empty_title`
  - `library_empty_subtitle`

## Build Status

Build successful with only minor warnings:
- Deprecated `onBackPressed()` in MainActivity (acceptable)
- Unused parameter warning in `saveToLibrary()` (TODO for future implementation)
- Unused variable in PdfHandler (pre-existing)

## Architecture Flow

1. User interacts with ResultsFragment search view
2. ResultsFragment calls `viewModel.search(query)`
3. ResultsViewModel calls `repository.search(query)`
4. ScholarRepository -> ScholarApiClient (HTTP) -> ScholarHtmlParser (parse HTML)
5. Results flow back via StateFlow to UI

For citations:
1. User clicks "Cited by X" on an article card
2. ResultsFragment calls `viewModel.loadCitationsFor(article)`
3. ResultsViewModel calls `repository.searchCitations(article.id)`
4. Same flow as above for results

## Next Steps

- Implement save to library functionality (currently TODO)
- Add sort order parameter to search API
- Track article for refresh in citations mode
