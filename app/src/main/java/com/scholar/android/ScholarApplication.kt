package com.scholar.android

import android.app.Application
import android.webkit.WebView
import com.scholar.android.BuildConfig

/**
 * Application class for Google Scholar Android app.
 * Initializes WebView debugging in debug builds.
 */
class ScholarApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
