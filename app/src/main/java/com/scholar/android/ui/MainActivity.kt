package com.scholar.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.databinding.ActivityMainBinding
import com.scholar.android.ui.library.LibraryFragment
import com.scholar.android.ui.profile.ProfileFragment
import com.scholar.android.ui.results.ResultsFragment
import com.scholar.android.ui.settings.SettingsFragment
import com.scholar.android.util.AppPreferences
import com.scholar.android.util.NetworkUtils

/**
 * Main activity containing the navigation drawer and fragment container.
 * This is the single-activity host for the native Google Scholar interface.
 */
class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    // Reference to the results fragment
    private var resultsFragment: ResultsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme from preferences before setting content view
        applyThemeFromPreferences()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNavigationDrawer()
        setupErrorLayout()

        // Load initial ResultsFragment if this is a fresh start
        if (savedInstanceState == null) {
            resultsFragment = ResultsFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, resultsFragment!!)
                .commit()
        } else {
            resultsFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_container) as? ResultsFragment
        }

        // Handle incoming intent (deep links)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
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

        // Set drawer toggle icon color to white to match toolbar
        drawerToggle.drawerArrowDrawable.color = getColor(R.color.on_primary)

        binding.navigationView.setNavigationItemSelectedListener(this)

        // Select home by default
        binding.navigationView.setCheckedItem(R.id.nav_home)
    }

    private fun setupErrorLayout() {
        binding.retryButton.setOnClickListener {
            hideError()
            // Refresh the current fragment's content
            getResultsFragment()?.refresh()
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // Handle deep links with search queries
                intent.data?.getQueryParameter("q")?.let { query ->
                    performSearch(query)
                }
            }
            Intent.ACTION_SEARCH -> {
                intent.getStringExtra("query")?.let { query ->
                    performSearch(query)
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorMessage.text = message
        binding.errorLayout.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
    }

    private fun hideError() {
        binding.errorLayout.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
    }

    /**
     * Shows progress indicator in the toolbar
     */
    fun showProgress() {
        binding.progressBar.visibility = View.VISIBLE
    }

    /**
     * Hides progress indicator in the toolbar
     */
    fun hideProgress() {
        binding.progressBar.visibility = View.GONE
    }

    /**
     * Gets the current ResultsFragment instance
     */
    private fun getResultsFragment(): ResultsFragment? {
        return supportFragmentManager.findFragmentById(R.id.fragment_container) as? ResultsFragment
    }

    /**
     * Performs a search using the ResultsFragment
     */
    fun performSearch(query: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError(getString(R.string.error_no_internet))
            return
        }

        hideError()
        getResultsFragment()?.performSearch(query)
    }

    // NavigationView.OnNavigationItemSelectedListener
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Navigate back to search/home screen
                hideError()
                navigateToHome()
            }
            R.id.nav_library -> {
                // Navigate to Library fragment
                navigateToLibrary()
            }
            R.id.nav_profile -> {
                // Navigate to Profile fragment
                navigateToProfile()
            }
            R.id.nav_settings -> {
                // Navigate to Settings fragment
                navigateToSettings()
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showComingSoonDialog(featureName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.coming_soon)
            .setMessage(getString(R.string.feature_coming_soon))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Navigates to the Library fragment
     */
    private fun navigateToLibrary() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment.newInstance())
            .addToBackStack("library")
            .commit()
    }

    /**
     * Navigates to the Profile fragment
     */
    private fun navigateToProfile() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment.newInstance())
            .addToBackStack("profile")
            .commit()
    }

    /**
     * Navigates to the Settings fragment
     */
    private fun navigateToSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment.newInstance())
            .addToBackStack("settings")
            .commit()
    }

    /**
     * Navigates back to the Results fragment (home)
     */
    private fun navigateToHome() {
        // Pop all fragments from back stack to return to home
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /**
     * Applies the theme preference on startup
     */
    private fun applyThemeFromPreferences() {
        // Apply the user's theme preference (light, dark, or system)
        val nightMode = AppPreferences.getThemeModeAsNightMode(this)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    // Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)

        // Setup SearchView in the toolbar
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = getString(R.string.search_hint)

            // When search is expanded, navigate to home if needed
            setOnSearchClickListener {
                // If not on home screen, navigate there first
                if (supportFragmentManager.backStackEntryCount > 0) {
                    navigateToHome()
                }
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        if (it.isNotBlank()) {
                            // Navigate to home first if needed
                            if (supportFragmentManager.backStackEntryCount > 0) {
                                navigateToHome()
                            }
                            performSearch(it.trim())
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
                // Refresh the current fragment's content
                getResultsFragment()?.refresh()
                true
            }
            R.id.action_share -> {
                shareCurrentPage()
                true
            }
            R.id.action_open_browser -> {
                // Show toast that this feature is not available in native mode
                Snackbar.make(
                    binding.root,
                    R.string.feature_coming_soon,
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentPage() {
        // Share the app or current search
        val shareText = getString(R.string.app_name)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
    }

    // Handle back button
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }
}
