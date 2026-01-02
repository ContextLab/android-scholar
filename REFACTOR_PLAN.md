# Google Scholar App - Native Interface Refactoring Plan

## Current Problems
1. **Double hamburger menus**: App's navigation drawer + Google Scholar's mobile site menu
2. **PDF links broken**: WebView doesn't properly handle PDF downloads/viewing
3. **Limited control**: WebView wrapper limits customization and native feel

## Solution: Native Google Scholar Interface

Replace WebView with native Android components that parse and display Scholar content.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │ SearchFragment│  │ResultsFragment│ │ArticleDetail│     │
│  └─────────────┘  └─────────────┘  └─────────────┘      │
├─────────────────────────────────────────────────────────┤
│                   ViewModel Layer                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │            ScholarViewModel                      │    │
│  │  - searchResults: LiveData<List<Article>>       │    │
│  │  - loading: LiveData<Boolean>                   │    │
│  └─────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────┤
│                  Repository Layer                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │           ScholarRepository                      │    │
│  │  - search(query): List<Article>                 │    │
│  │  - getArticleDetails(id): ArticleDetails        │    │
│  └─────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────┤
│                   Network Layer                          │
│  ┌──────────────────┐  ┌────────────────────────┐       │
│  │  ScholarApiClient │  │   ScholarHtmlParser    │       │
│  │  (OkHttp)         │  │   (Jsoup)              │       │
│  └──────────────────┘  └────────────────────────┘       │
├─────────────────────────────────────────────────────────┤
│                    Data Models                           │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐  ┌────────┐    │
│  │ Article │  │  Author  │  │Citation │  │PdfLink │    │
│  └─────────┘  └──────────┘  └─────────┘  └────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Tasks

### Task 1: Data Models (`data/model/`)
Create Kotlin data classes:
- `Article` - title, authors, year, citations, abstract snippet, PDF link
- `Author` - name, affiliation, profile URL
- `SearchResult` - list of articles, pagination info
- `PdfSource` - URL, source name (publisher, arxiv, etc.)

### Task 2: Network Layer (`network/`)
- `ScholarApiClient` - OkHttp client with cookie handling, user-agent spoofing
- `ScholarHtmlParser` - Jsoup-based HTML parser for search results
- Handle rate limiting and CAPTCHA detection

### Task 3: Repository (`repository/`)
- `ScholarRepository` - Coordinates API calls and parsing
- Caching layer for recent searches
- Error handling and retry logic

### Task 4: ViewModel (`viewmodel/`)
- `ScholarViewModel` - Manages UI state
- `SearchState` sealed class (Loading, Success, Error)
- Pagination support

### Task 5: UI Components (`ui/`)
- `SearchFragment` - Search bar with suggestions
- `ResultsFragment` - RecyclerView with article cards
- `ArticleCardAdapter` - Material card for each result
- `ArticleDetailFragment` - Full article view with actions

### Task 6: PDF Handling (`util/`)
- `PdfHandler` - Download manager integration
- Intent handling for viewing PDFs
- Progress notification for downloads

### Task 7: Navigation Update
- Remove WebView completely
- Update navigation drawer for native fragments
- Single hamburger menu (app's only)

---

## New Dependencies

```kotlin
// HTML Parsing
implementation("org.jsoup:jsoup:1.17.2")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Image Loading (for author avatars, thumbnails)
implementation("io.coil-kt:coil:2.5.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

---

## File Structure After Refactoring

```
app/src/main/java/com/scholar/android/
├── ScholarApplication.kt
├── data/
│   └── model/
│       ├── Article.kt
│       ├── Author.kt
│       ├── SearchResult.kt
│       └── PdfSource.kt
├── network/
│   ├── ScholarApiClient.kt
│   └── ScholarHtmlParser.kt
├── repository/
│   └── ScholarRepository.kt
├── viewmodel/
│   └── ScholarViewModel.kt
├── ui/
│   ├── MainActivity.kt (refactored)
│   ├── search/
│   │   └── SearchFragment.kt
│   ├── results/
│   │   ├── ResultsFragment.kt
│   │   └── ArticleCardAdapter.kt
│   └── detail/
│       └── ArticleDetailFragment.kt
└── util/
    ├── PdfHandler.kt
    ├── NetworkUtils.kt
    └── ScholarUrls.kt (updated)
```

---

## Parallel Execution Plan

### Agent 1: Data & Network Layer
- Create data models
- Implement ScholarApiClient
- Implement ScholarHtmlParser
- Add new dependencies

### Agent 2: Repository & ViewModel
- Implement ScholarRepository
- Create ScholarViewModel
- Define state classes

### Agent 3: UI Components
- Create fragment layouts
- Implement RecyclerView adapter
- Create article card layout
- Update navigation

### Agent 4: PDF & Integration
- Implement PdfHandler
- Update MainActivity
- Remove old WebView code
- Test integration

---

## Risk Mitigation

1. **Google Scholar blocking**: Use realistic user-agent, respect rate limits
2. **HTML structure changes**: Parser should be resilient, log warnings
3. **CAPTCHA challenges**: Detect and prompt user, fallback to WebView if needed
4. **PDF access**: Some PDFs require authentication - handle gracefully

---

## Success Criteria

- [ ] Single hamburger menu (app navigation only)
- [ ] Search results display in native RecyclerView
- [ ] PDF links open in PDF viewer or download
- [ ] Smooth scrolling and native feel
- [ ] Navigation drawer works correctly
- [ ] Back navigation works properly
