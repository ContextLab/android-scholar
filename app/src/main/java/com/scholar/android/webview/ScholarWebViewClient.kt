package com.scholar.android.webview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.scholar.android.util.ScholarUrls

/**
 * Custom WebViewClient for handling Google Scholar navigation.
 */
class ScholarWebViewClient(
    private val listener: WebViewClientListener
) : WebViewClient() {

    interface WebViewClientListener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onPageError(errorCode: Int, description: String)
        fun onExternalUrlDetected(url: String)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { listener.onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { listener.onPageFinished(it) }

        // Inject CSS to improve mobile experience
        view?.let { injectMobileStyles(it) }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)

        // Only handle errors for the main frame
        if (request?.isForMainFrame == true) {
            error?.let {
                listener.onPageError(
                    it.errorCode,
                    it.description?.toString() ?: "Unknown error"
                )
            }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        return when {
            // Allow Google Scholar URLs
            ScholarUrls.isScholarUrl(url) -> false

            // Allow Google authentication URLs
            ScholarUrls.isGoogleAuthUrl(url) -> false

            // Allow other necessary Google URLs
            ScholarUrls.isAllowedExternalUrl(url) -> false

            // Open external URLs in external browser
            else -> {
                listener.onExternalUrlDetected(url)
                true
            }
        }
    }

    /**
     * Injects custom CSS to improve the mobile viewing experience.
     */
    private fun injectMobileStyles(webView: WebView) {
        val css = """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `
                    /* Improve touch targets */
                    a, button {
                        min-height: 44px;
                        min-width: 44px;
                    }

                    /* Better text readability */
                    body {
                        -webkit-text-size-adjust: 100%;
                    }

                    /* Prevent horizontal scroll */
                    html, body {
                        max-width: 100%;
                        overflow-x: hidden;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()

        webView.evaluateJavascript(css, null)
    }
}
