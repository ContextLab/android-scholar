# Citation Bar Chart Implementation

**Date**: 2026-01-01
**Status**: Completed

## Summary

Implemented an animated bar chart visualization for citations over time in the Profile screen, similar to Google Scholar's web interface.

## Changes Made

### 1. Dependencies Added
- **settings.gradle.kts**: Added JitPack repository for MPAndroidChart
- **app/build.gradle.kts**: Added MPAndroidChart library dependency (`com.github.PhilJay:MPAndroidChart:v3.1.0`)

### 2. Data Model Updates
- **AuthorProfile.kt**: Added `YearlyCitation` data class and `citationHistory` property to AuthorProfile

### 3. HTML Parser Updates
- **ScholarHtmlParser.kt**: Added `parseCitationHistory()` method to extract citation history from Google Scholar profile pages
  - Uses CSS selectors `.gsc_g_t` for years and `.gsc_g_al` for values
  - Implements fallback parsing strategies for different page structures

### 4. Layout Updates
- **fragment_profile.xml**: Added new Citation History Chart Card containing:
  - Title section
  - Horizontal scrollable ChipGroup for time range selection (All time, Last 5 years, Since 2020, Since 2015)
  - BarChart view (200dp height)
  - Selected value display TextView
  - Empty state TextView for when no data is available

### 5. ViewModel Updates
- **ProfileViewModel.kt**: Added:
  - `CitationTimeRange` enum (ALL_TIME, LAST_5_YEARS, SINCE_2020, SINCE_2015)
  - `selectedTimeRange` StateFlow for tracking current filter
  - `filteredCitationHistory` StateFlow for filtered data
  - `setTimeRange()` method to change filter
  - `updateFilteredCitationHistory()` private method for filtering logic

### 6. Fragment Updates
- **ProfileFragment.kt**: Added:
  - MPAndroidChart imports
  - `setupCitationChart()` method for chart configuration
  - `setupTimeRangeChips()` method for chip selection handling
  - `observeFilteredCitationHistory()` method for data observation
  - `updateCitationChart()` method for chart data updates with animation
  - `showSelectedCitation()` method for tap interaction display

### 7. String Resources
- **strings.xml**: Added new strings:
  - `citations_per_year`
  - `time_range_all`, `time_range_5_years`, `time_range_since_2020`, `time_range_since_2015`
  - `citations_in_year`
  - `no_citation_data`

### 8. Bug Fixes
- **ProfileEditActivity.kt**: Fixed `CookieManager.setAcceptThirdPartyCookies()` call to pass WebView instead of CookieManager

## Features

1. **Bar Chart Display**: Shows citations per year with Google Scholar blue color (#4285F4)
2. **Animated Loading**: Chart animates with EaseOutCubic easing over 1 second
3. **Time Range Filtering**: Four filter options via Material Chips
4. **Tap Interaction**: Tapping a bar shows exact citation count and year
5. **Empty State Handling**: Displays message when no citation data is available
6. **Responsive Design**: Chart hides values when there are more than 15 bars to avoid crowding

## Build Status

BUILD SUCCESSFUL - All changes compile without errors (only deprecation warnings for unrelated Google Sign-In APIs)

## Files Modified

1. `/app/build.gradle.kts`
2. `/settings.gradle.kts`
3. `/app/src/main/java/com/scholar/android/data/model/AuthorProfile.kt`
4. `/app/src/main/java/com/scholar/android/network/ScholarHtmlParser.kt`
5. `/app/src/main/res/layout/fragment_profile.xml`
6. `/app/src/main/java/com/scholar/android/ui/profile/ProfileViewModel.kt`
7. `/app/src/main/java/com/scholar/android/ui/profile/ProfileFragment.kt`
8. `/app/src/main/res/values/strings.xml`
9. `/app/src/main/java/com/scholar/android/ui/profile/ProfileEditActivity.kt` (bug fix)
