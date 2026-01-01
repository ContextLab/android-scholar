package com.scholar.android.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.scholar.android.R
import com.scholar.android.databinding.ActivityMainBinding
import com.scholar.android.util.NetworkUtils
import com.scholar.android.util.ScholarUrls
import com.scholar.android.webview.ScholarWebChromeClient
import com.scholar.android.webview.ScholarWebViewClient

/**
 * Main activity containing the navigation drawer and WebView for Google Scholar.
 */
class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    ScholarWebViewClient.WebViewClientListener,
    ScholarWebChromeClient.WebChromeClientListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var currentUrl: String = ScholarUrls.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNavigationDrawer()
        setupWebView()
        setupSwipeRefresh()
        setupErrorLayout()

        // Handle incoming intent (deep links)
        handleIntent(intent)

        // Load initial page if no saved state
        if (savedInstanceState == null) {
            loadUrl(ScholarUrls.HOME)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }
    }

    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)

        // Select home by default
        binding.navigationView.setCheckedItem(R.id.nav_home)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            webViewClient = ScholarWebViewClient(this@MainActivity)
            webChromeClient = ScholarWebChromeClient(this@MainActivity)

            settings.apply {
                // Enable JavaScript (required for Google Scholar)
                javaScriptEnabled = true

                // Enable DOM storage
                domStorageEnabled = true

                // Enable zoom controls
                builtInZoomControls = true
                displayZoomControls = false

                // Cache settings
                cacheMode = WebSettings.LOAD_DEFAULT

                // Enable database storage
                databaseEnabled = true

                // Set user agent to mobile
                userAgentString = "$userAgentString Mobile"

                // Enable mixed content mode for HTTPS
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // Load images automatically
                loadsImagesAutomatically = true

                // Enable wide viewport
                useWideViewPort = true
                loadWithOverviewMode = true

                // Support multiple windows
                setSupportMultipleWindows(false)
            }

            // Enable cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Set scroll listener
            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                // Enable/disable swipe refresh only when at top
                binding.swipeRefresh.isEnabled = scrollY == 0
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeResources(
                R.color.primary,
                R.color.secondary
            )
            setOnRefreshListener {
                binding.webView.reload()
            }
        }
    }

    private fun setupErrorLayout() {
        binding.retryButton.setOnClickListener {
            hideError()
            loadUrl(currentUrl)
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.toString()?.let { url ->
                    if (ScholarUrls.isScholarUrl(url)) {
                        loadUrl(url)
                    }
                }
            }
        }
    }

    private fun loadUrl(url: String) {
        currentUrl = url

        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError(getString(R.string.error_no_internet))
            return
        }

        hideError()
        binding.webView.loadUrl(url)
    }

    private fun showError(message: String) {
        binding.errorMessage.text = message
        binding.errorLayout.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        binding.errorLayout.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
    }

    // NavigationView.OnNavigationItemSelectedListener
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                loadUrl(ScholarUrls.HOME)
            }
            R.id.nav_search -> {
                showSearchDialog()
            }
            R.id.nav_library -> {
                loadUrl(ScholarUrls.LIBRARY)
            }
            R.id.nav_profile -> {
                loadUrl(ScholarUrls.PROFILE)
            }
            R.id.nav_settings -> {
                loadUrl(ScholarUrls.SETTINGS)
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)

        // Setup SearchView
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        if (it.isNotBlank()) {
                            loadUrl(ScholarUrls.getSearchUrl(it))
                            clearFocus()
                            searchItem.collapseActionView()
                        }
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return false
                }
            })
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                binding.webView.reload()
                true
            }
            R.id.action_share -> {
                shareCurrentPage()
                true
            }
            R.id.action_open_browser -> {
                openInExternalBrowser(currentUrl)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.search_dialog, null)
        val searchInput = dialogView.findViewById<TextInputEditText>(R.id.search_input)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_search)
            .setView(dialogView)
            .setPositiveButton(R.string.action_search) { _, _ ->
                val query = searchInput.text?.toString()
                if (!query.isNullOrBlank()) {
                    loadUrl(ScholarUrls.getSearchUrl(query))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text?.toString()
                if (!query.isNullOrBlank()) {
                    loadUrl(ScholarUrls.getSearchUrl(query))
                    dialog.dismiss()
                }
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun shareCurrentPage() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl)
            putExtra(Intent.EXTRA_SUBJECT, binding.toolbar.title)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
    }

    private fun openInExternalBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show()
        }
    }

    // WebViewClientListener
    override fun onPageStarted(url: String) {
        currentUrl = url
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = true
    }

    override fun onPageFinished(url: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    override fun onPageError(errorCode: Int, description: String) {
        showError(getString(R.string.error_loading))
    }

    override fun onExternalUrlDetected(url: String) {
        openInExternalBrowser(url)
    }

    // WebChromeClientListener
    override fun onProgressChanged(progress: Int) {
        if (progress < 100) {
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onTitleChanged(title: String) {
        // Optionally update toolbar title
        // binding.toolbar.title = title
    }

    // Handle back button
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            binding.webView.canGoBack() -> {
                binding.webView.goBack()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
