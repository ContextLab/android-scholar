package com.scholar.android.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.data.model.AuthorSearchResult
import com.scholar.android.databinding.ActivityAuthorSearchBinding
import com.scholar.android.repository.ScholarRepository
import kotlinx.coroutines.launch

/**
 * Activity for displaying author search results.
 * Used when searching for authors by interest/keyword.
 */
class AuthorSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthorSearchBinding
    private val repository = ScholarRepository()
    private lateinit var adapter: AuthorSearchAdapter

    companion object {
        private const val EXTRA_QUERY = "search_query"

        fun createIntent(context: Context, query: String): Intent {
            return Intent(context, AuthorSearchActivity::class.java).apply {
                putExtra(EXTRA_QUERY, query)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthorSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()

        val query = intent.getStringExtra(EXTRA_QUERY)
        if (query.isNullOrBlank()) {
            showError(getString(R.string.error_loading))
            return
        }

        supportActionBar?.subtitle = "\"$query\""
        searchAuthors(query)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.research_interests)
        }
    }

    private fun setupRecyclerView() {
        adapter = AuthorSearchAdapter { author ->
            openAuthorProfile(author)
        }

        binding.recyclerAuthors.apply {
            this.adapter = this@AuthorSearchActivity.adapter
            layoutManager = LinearLayoutManager(this@AuthorSearchActivity)
        }
    }

    private fun searchAuthors(query: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = repository.searchAuthorsByLabel(query)

            result.fold(
                onSuccess = { authors ->
                    showLoading(false)
                    if (authors.isEmpty()) {
                        showEmpty(query)
                    } else {
                        showResults(authors)
                    }
                },
                onFailure = { error ->
                    showLoading(false)
                    showError(error.message ?: getString(R.string.error_loading))
                }
            )
        }
    }

    private fun openAuthorProfile(author: AuthorSearchResult) {
        val intent = ProfileViewActivity.createIntent(
            context = this,
            authorId = author.id,
            authorName = author.name
        )
        startActivity(intent)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.recyclerAuthors.isVisible = !loading && adapter.itemCount > 0
        binding.emptyState.isVisible = false
        binding.errorState.isVisible = false
    }

    private fun showResults(authors: List<AuthorSearchResult>) {
        adapter.submitList(authors)
        binding.recyclerAuthors.isVisible = true
        binding.emptyState.isVisible = false
        binding.errorState.isVisible = false
    }

    private fun showEmpty(query: String) {
        binding.recyclerAuthors.isVisible = false
        binding.emptyState.isVisible = true
        binding.textEmptyMessage.text = getString(R.string.empty_state_subtitle)
        binding.errorState.isVisible = false
    }

    private fun showError(message: String) {
        binding.recyclerAuthors.isVisible = false
        binding.emptyState.isVisible = false
        binding.errorState.isVisible = true
        binding.textErrorMessage.text = message

        binding.buttonRetry.setOnClickListener {
            intent.getStringExtra(EXTRA_QUERY)?.let { query ->
                searchAuthors(query)
            }
        }
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
}
