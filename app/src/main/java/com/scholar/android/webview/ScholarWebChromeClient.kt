package com.scholar.android.webview

import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Custom WebChromeClient for handling Chrome-related events.
 */
class ScholarWebChromeClient(
    private val listener: WebChromeClientListener
) : WebChromeClient() {

    interface WebChromeClientListener {
        fun onProgressChanged(progress: Int)
        fun onTitleChanged(title: String)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        listener.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { listener.onTitleChanged(it) }
    }
}
