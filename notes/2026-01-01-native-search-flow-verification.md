# Native Search Flow Verification Session
Date: 2026-01-01

## Task Summary
Ensure search results are displayed natively (not in WebView) and clicking on results shows native article details.

## Findings

### Search Flow is Already Native
The existing implementation already uses a fully native search flow:

1. **Search Bar Input**: `ResultsFragment.setupSearchView()` captures search queries via `SearchView.OnQueryTextListener`

2. **HTTP Fetch with Proper Headers**:
   - `ScholarApiClient` uses OkHttp with proper headers:
     - User-Agent: Mobile Chrome UA string
     - Accept, Accept-Language, Connection headers
   - Cookie persistence for session management
   - Proper timeout configuration (30s)

3. **HTML Parsing into Article List**:
   - `ScholarHtmlParser.parseSearchResults()` uses Jsoup to parse HTML
   - Extracts: title, authors, year, citation count, snippet, PDF URL, article URL, source
   - Handles pagination with `nextPageUrl`
   - Detects CAPTCHA/rate limiting

4. **Display in RecyclerView**:
   - `ArticleAdapter` with `ListAdapter<Article>` and `DiffUtil`
   - Material Design cards with title, authors, source, snippet, citation chip
   - Action buttons: PDF, Save, Share

5. **Pagination/Load More**:
   - Infinite scroll via `RecyclerView.OnScrollListener`
   - Loads next page when 5 items before end
   - `ResultsViewModel.loadNextPage()` fetches next page

## Changes Made

### 1. Updated ResultsFragment.openArticle()
Changed from opening in external browser to using `ArticleViewActivity`:

```kotlin
// Before:
val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.articleUrl))
startActivity(intent)

// After:
val intent = ArticleViewActivity.createIntent(
    context = requireContext(),
    url = article.articleUrl,
    title = article.title
)
startActivity(intent)
```

### 2. Updated LibraryFragment.openArticle()
Same change as ResultsFragment - now uses `ArticleViewActivity` instead of external browser.

### 3. Added ArticleViewActivity Import
Added import for `ArticleViewActivity` in both fragments.

## Files Modified
- `/app/src/main/java/com/scholar/android/ui/results/ResultsFragment.kt`
- `/app/src/main/java/com/scholar/android/ui/library/LibraryFragment.kt`

## Files Already Native (No Changes Needed)
- `/app/src/main/java/com/scholar/android/network/ScholarApiClient.kt` - HTTP client with proper headers
- `/app/src/main/java/com/scholar/android/network/ScholarHtmlParser.kt` - HTML parsing
- `/app/src/main/java/com/scholar/android/repository/ScholarRepository.kt` - Repository layer
- `/app/src/main/java/com/scholar/android/ui/results/ResultsViewModel.kt` - ViewModel with pagination
- `/app/src/main/java/com/scholar/android/ui/results/ArticleAdapter.kt` - RecyclerView adapter
- `/app/src/main/java/com/scholar/android/ui/profile/ProfileFragment.kt` - Already uses ArticleViewActivity

## Build Status
- Build: SUCCESSFUL
- Tests: PASSING

## Notes for Other Agent
- `ArticleViewActivity` is being updated by another agent to show native article details
- Current layout (`activity_article_view.xml`) has both WebView and native content views
- The layout includes placeholders for: title, authors, venue, citations chip, abstract, cited-by section, related articles
- String resources for native article view have been added

## Architecture Summary
```
User types search query
    |
SearchView.onQueryTextSubmit()
    |
ResultsViewModel.search(query)
    |
ScholarRepository.search(query)
    |
ScholarApiClient.search(query) -- OkHttp with proper headers
    |
ScholarHtmlParser.parseSearchResults(html) -- Jsoup parsing
    |
SearchResult (List<Article>, pagination info)
    |
ResultsViewModel updates StateFlow<List<Article>>
    |
ArticleAdapter.submitList(articles)
    |
RecyclerView displays native article cards
    |
User clicks article card
    |
ArticleViewActivity.createIntent() -- Opens in-app article view
```
