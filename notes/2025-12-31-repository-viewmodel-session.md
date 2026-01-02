# Session Notes: Repository and ViewModel Layers

Date: 2025-12-31

## Summary

Created the Repository and ViewModel layers for the Android Google Scholar app refactoring project.

## Files Created

### API Layer (`/app/src/main/java/com/scholar/android/api/`)

1. **ScholarApiClient.kt** - HTTP client for fetching Google Scholar pages
   - Uses HttpURLConnection for network requests
   - Includes proper User-Agent headers for mobile requests
   - Methods:
     - `fetchSearchResults(query: String): Result<String>` - Fetches HTML for a search query
     - `fetchUrl(url: String): Result<String>` - Fetches HTML for a specific URL (pagination)
   - Includes `NetworkException` for network-related errors

### Parser Layer (`/app/src/main/java/com/scholar/android/parser/`)

1. **ScholarHtmlParser.kt** - Parses Google Scholar HTML pages
   - Uses regex patterns to extract article data
   - Method:
     - `parseSearchResults(html: String): Result<SearchResult>` - Parses HTML into SearchResult
   - Extracts: title, authors, year, source, snippet, citation count, PDF URL, article URL
   - Includes `ParseException` for parsing errors
   - Uses existing `data.model.Article` and `data.model.SearchResult` classes

### Repository Layer (`/app/src/main/java/com/scholar/android/repository/`)

1. **ScholarRepository.kt** - Coordinates between API client and parser
   - Methods:
     - `search(query: String): Result<SearchResult>` - Performs a search
     - `loadNextPage(nextPageUrl: String): Result<SearchResult>` - Loads next page
   - Handles error mapping to user-friendly messages
   - Includes `RepositoryException` for repository-level errors

### ViewModel Layer (`/app/src/main/java/com/scholar/android/viewmodel/`)

1. **SearchState.kt** - Sealed class for UI states
   - `Idle` - Initial state, no search performed
   - `Loading(isLoadingMore: Boolean)` - Search or pagination in progress
   - `Success(result: SearchResult)` - Search completed with results
   - `Error(message: String, canRetry: Boolean)` - Search failed

2. **ScholarViewModel.kt** - ViewModel for search operations
   - Extends `AndroidViewModel`
   - StateFlows:
     - `searchState: StateFlow<SearchState>` - Current search state
     - `articles: StateFlow<List<Article>>` - Accumulated articles (for pagination)
     - `currentQuery: StateFlow<String?>` - Current search query
   - Methods:
     - `search(query: String)` - Performs new search, clears previous results
     - `loadNextPage()` - Loads next page, accumulates results
     - `retry()` - Retries last search
     - `clearSearch()` - Clears state and results
     - `hasNextPage(): Boolean` - Checks if more pages available
     - `getTotalResults(): String?` - Gets total results string
   - Uses `viewModelScope` for coroutines

3. **ScholarViewModelFactory.kt** - Factory for dependency injection
   - Enables custom repository injection for testing

## Key Design Decisions

1. Used existing data models from `com.scholar.android.data.model` package (Article, SearchResult)
2. Repository pattern for data operations
3. StateFlow for reactive UI updates
4. Result<T> for error handling throughout
5. Coroutines with `viewModelScope` for async operations
6. Pagination support with article accumulation

## Build Status

Build successful with `./gradlew assembleDebug`

## Dependencies Used

- Kotlin Coroutines (kotlinx.coroutines)
- AndroidX Lifecycle ViewModel KTX
- Android Architecture Components

## Notes

- The existing `ArticleAdapter` was updated (by linter) to use `adapterPosition` instead of `bindingAdapterPosition`
- local.properties was created to point to Android SDK at `/Users/jmanning/Library/Android/sdk`
