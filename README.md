# Google Scholar for Android

A native Android application that provides a wrapper for Google Scholar (https://scholar.google.com/), following Material Design 2 guidelines.

## Features

- **Full Google Scholar Access**: Browse, search, and access all Google Scholar features through a native Android interface
- **Navigation Drawer**: Easy access to main sections:
  - Home - Main Google Scholar page
  - Search - Quick search functionality
  - My Library - Access your saved articles
  - My Profile - View and manage your Google Scholar profile
  - Settings - Access Google Scholar settings
- **Search Functionality**:
  - Toolbar search action
  - Search dialog with Material Design text input
- **Material Design 2**: Modern UI following Google's Material Design 2 guidelines
- **Dark Mode Support**: Automatic dark theme based on system settings
- **Pull-to-Refresh**: Swipe down to refresh the current page
- **Share**: Share articles and pages via any app
- **Open in Browser**: Option to open the current page in an external browser
- **Deep Linking**: Handles Google Scholar URLs to open directly in the app

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection

## Building the App

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/android-scholar.git
   cd android-scholar
   ```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

5. The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

To create a release build:

1. Create a signing key (if you don't have one):
   ```bash
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
   ```

2. Configure signing in `app/build.gradle.kts`

3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

## Project Structure

```
app/
├── src/main/
│   ├── java/com/scholar/android/
│   │   ├── ScholarApplication.kt      # Application class
│   │   ├── ui/
│   │   │   └── MainActivity.kt        # Main activity with navigation
│   │   ├── util/
│   │   │   ├── NetworkUtils.kt        # Network connectivity utilities
│   │   │   └── ScholarUrls.kt         # URL management
│   │   └── webview/
│   │       ├── ScholarWebChromeClient.kt  # Chrome client for progress
│   │       └── ScholarWebViewClient.kt    # WebView client for navigation
│   └── res/
│       ├── layout/                    # Layout XML files
│       ├── menu/                      # Menu XML files
│       ├── drawable/                  # Vector icons and drawables
│       ├── values/                    # Colors, strings, themes
│       └── values-night/              # Dark theme resources
```

## Material Design 2 Implementation

This app follows Material Design 2 guidelines with:

- **Color System**: Google's blue primary color (#4285F4) with appropriate variants
- **Typography**: Material Design type scale
- **Navigation Drawer**: Standard navigation drawer with header
- **App Bar**: Material toolbar with actions
- **Components**: Material buttons, text inputs, and dialogs
- **Elevation**: Proper shadow and elevation values
- **Dark Theme**: Full dark mode support with appropriate color adaptations

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Disclaimer

This app is not affiliated with or endorsed by Google. Google Scholar is a trademark of Google LLC.
