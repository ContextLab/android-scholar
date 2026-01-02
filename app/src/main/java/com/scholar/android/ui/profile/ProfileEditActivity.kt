package com.scholar.android.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.databinding.ActivityProfileEditBinding
import com.scholar.android.util.ScholarUrls

/**
 * Activity for editing Google Scholar profile using WebView.
 *
 * This activity loads the Google Scholar profile edit pages in an authenticated
 * WebView session. The user must be logged in with Google before accessing this
 * activity, and the WebView shares cookies with the Google Sign-In session.
 *
 * Editable profile fields include:
 * - Profile photo
 * - Name
 * - Affiliation
 * - Email verification
 * - Homepage URL
 * - Research interests (keywords)
 * - Publications management
 * - Co-author management
 */
class ProfileEditActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SCHOLAR_ID = "scholar_id"
        private const val EXTRA_EDIT_TYPE = "edit_type"

        // Edit type constants
        const val EDIT_TYPE_PROFILE = "profile"
        const val EDIT_TYPE_PUBLICATIONS = "publications"
        const val EDIT_TYPE_COAUTHORS = "coauthors"

        /**
         * Creates an intent to open the profile edit activity.
         * @param context The context to create the intent from
         * @param scholarId The Google Scholar author ID
         * @param editType The type of edit (profile, publications, coauthors)
         */
        fun createIntent(
            context: Context,
            scholarId: String,
            editType: String = EDIT_TYPE_PROFILE
        ): Intent {
            return Intent(context, ProfileEditActivity::class.java).apply {
                putExtra(EXTRA_SCHOLAR_ID, scholarId)
                putExtra(EXTRA_EDIT_TYPE, editType)
            }
        }
    }

    private lateinit var binding: ActivityProfileEditBinding
    private var scholarId: String? = null
    private var editType: String = EDIT_TYPE_PROFILE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scholarId = intent.getStringExtra(EXTRA_SCHOLAR_ID)
        editType = intent.getStringExtra(EXTRA_EDIT_TYPE) ?: EDIT_TYPE_PROFILE

        if (scholarId.isNullOrBlank()) {
            showError(getString(R.string.error_no_profile_id))
            finish()
            return
        }

        setupToolbar()
        setupWebView()
        loadEditPage()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = when (editType) {
                EDIT_TYPE_PUBLICATIONS -> getString(R.string.edit_publications)
                EDIT_TYPE_COAUTHORS -> getString(R.string.edit_coauthors)
                else -> getString(R.string.edit_profile)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                // Enable JavaScript for interactive forms
                javaScriptEnabled = true

                // Enable DOM storage for Scholar's web app
                domStorageEnabled = true

                // Allow form data
                saveFormData = true

                // Enable zoom controls
                builtInZoomControls = true
                displayZoomControls = false

                // Set reasonable cache mode
                cacheMode = WebSettings.LOAD_DEFAULT

                // Enable file access for profile photo uploads
                allowFileAccess = true
                allowContentAccess = true

                // Set user agent to mobile browser
                userAgentString = userAgentString.replace("; wv", "")

                // Mixed content mode for Scholar's resources
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = ScholarEditWebViewClient()
            webChromeClient = ScholarEditChromeClient()

            // Enable cookies - critical for maintaining Google login session
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
        }
    }

    private fun loadEditPage() {
        showLoading(true)

        val url = when (editType) {
            EDIT_TYPE_PUBLICATIONS -> getPublicationsEditUrl()
            EDIT_TYPE_COAUTHORS -> getCoauthorsEditUrl()
            else -> getProfileEditUrl()
        }

        binding.webView.loadUrl(url)
    }

    /**
     * Gets the URL for editing the main profile (name, affiliation, interests, etc.)
     */
    private fun getProfileEditUrl(): String {
        // Google Scholar profile edit URL
        return "https://scholar.google.com/citations?view_op=edit_profile&hl=en&user=$scholarId"
    }

    /**
     * Gets the URL for managing publications.
     */
    private fun getPublicationsEditUrl(): String {
        // Google Scholar publications management URL
        return "https://scholar.google.com/citations?view_op=list_works&hl=en&user=$scholarId"
    }

    /**
     * Gets the URL for managing co-authors.
     */
    private fun getCoauthorsEditUrl(): String {
        // Google Scholar co-authors management URL
        return "https://scholar.google.com/citations?view_op=list_colleagues&hl=en&user=$scholarId"
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    /**
     * WebViewClient for handling Scholar edit page navigation.
     */
    private inner class ScholarEditWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            showLoading(true)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            showLoading(false)

            // Apply mobile-friendly styling via CSS injection
            applyMobileStyling(view)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false

            // Allow Google Scholar and auth URLs
            if (ScholarUrls.isScholarUrl(url) || ScholarUrls.isGoogleAuthUrl(url)) {
                return false // Let WebView handle it
            }

            // Allow other Google URLs (for file upload, etc.)
            if (url.contains("google.com") || url.contains("gstatic.com")) {
                return false
            }

            // Open external URLs in browser
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                showError(getString(R.string.error_opening_link))
            }
            return true
        }

        /**
         * Applies mobile-friendly CSS styling using safe DOM manipulation.
         * Creates a style element programmatically and sets its textContent.
         */
        private fun applyMobileStyling(view: WebView?) {
            // Use safe DOM API methods instead of innerHTML
            val cssContent = """
                input, button, select, textarea {
                    min-height: 44px !important;
                    font-size: 16px !important;
                }
                form {
                    padding: 8px !important;
                }
                body {
                    font-size: 14px !important;
                }
            """.trimIndent().replace("\n", " ")

            // Safe JavaScript that creates style element using DOM methods
            val safeJs = """
                javascript:(function() {
                    var style = document.createElement('style');
                    style.type = 'text/css';
                    style.appendChild(document.createTextNode('$cssContent'));
                    document.head.appendChild(style);
                })()
            """.trimIndent()

            view?.loadUrl(safeJs)
        }
    }

    /**
     * WebChromeClient for handling progress and other browser features.
     */
    private inner class ScholarEditChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (newProgress == 100) {
                showLoading(false)
            }
        }
    }
}
