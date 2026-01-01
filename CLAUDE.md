# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Requirements

- JDK 17
- Android SDK with API 34 (compileSdk)
- Minimum supported Android: API 24 (Android 7.0)

## Architecture

This is a native Android WebView wrapper for Google Scholar (scholar.google.com) using Kotlin and Material Design 2.

### Key Components

- **MainActivity** (`ui/MainActivity.kt`): Single-activity architecture with navigation drawer, toolbar, and WebView. Implements listener interfaces for WebView callbacks.

- **ScholarWebViewClient** (`webview/ScholarWebViewClient.kt`): Handles URL routing—Google Scholar and auth URLs load in-app, external URLs open in browser. Injects CSS for mobile experience improvements.

- **ScholarUrls** (`util/ScholarUrls.kt`): Centralized URL constants and helper methods for URL validation and search URL generation.

### Data Flow

1. User interacts via navigation drawer → `onNavigationItemSelected()` → `loadUrl()`
2. WebView loads URL → `ScholarWebViewClient` intercepts navigation
3. Non-Scholar URLs trigger `onExternalUrlDetected()` → opens external browser
4. Page state callbacks propagate to MainActivity via listener interfaces

### View Binding

Uses Android View Binding (`buildFeatures.viewBinding = true`). Layout bindings accessed via `binding.webView`, `binding.drawerLayout`, etc.
