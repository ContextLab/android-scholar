package com.scholar.android.ui.results

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.data.model.Article
import com.scholar.android.databinding.FragmentResultsBinding
import com.scholar.android.repository.ScholarRepository
import com.scholar.android.ui.article.ArticleViewActivity
import com.scholar.android.ui.profile.AuthorSearchActivity
import kotlinx.coroutines.launch

/**
 * Fragment displaying search results as a list of article cards.
 * Handles search input, pagination, and various UI states.
 */
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultsViewModel by viewModels {
        ResultsViewModelFactory(requireActivity().application, ScholarRepository())
    }

    private lateinit var articleAdapter: ArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupSwipeRefresh()
        setupRetryButton()
        setupSortButton()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        articleAdapter = ArticleAdapter(
            onArticleClick = { article -> openArticle(article) },
            onPdfClick = { article -> openPdf(article) },
            onSaveClick = { article -> saveArticle(article) },
            onShareClick = { article -> shareArticle(article) },
            onCitationsClick = { article -> viewCitations(article) },
            onAuthorClick = { authorName -> searchAuthor(authorName) }
        )

        binding.recyclerResults.apply {
            adapter = articleAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)

            // Infinite scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Only trigger when scrolling down
                    if (dy <= 0) return

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Load more when near the end (5 items before)
                    if (!viewModel.isLoading.value && viewModel.hasMoreResults.value) {
                        if ((visibleItemCount + firstVisibleItemPosition + 5) >= totalItemCount) {
                            viewModel.loadNextPage()
                        }
                    }
                }
            })
        }
    }

    private fun setupSearchView() {
        binding.searchView.apply {
            // Fix text color for light background
            val searchEditText = findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            searchEditText?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        if (it.isNotBlank()) {
                            viewModel.search(it.trim())
                            clearFocus()
                        }
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Could implement search suggestions here
                    return false
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeResources(R.color.primary, R.color.secondary)
            setOnRefreshListener {
                viewModel.refresh()
            }
        }
    }

    private fun setupRetryButton() {
        binding.buttonRetry.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun setupSortButton() {
        binding.buttonSort.setOnClickListener {
            viewModel.toggleSortOrder()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeUiState() }
                launch { observeArticles() }
                launch { observeIsLoading() }
                launch { observeIsLoadingMore() }
                launch { observeSortOrder() }
                launch { observeError() }
                launch { observeResultsInfo() }
                launch { observeCitationsMode() }
                launch { observeSavedArticles() }
            }
        }
    }

    private suspend fun observeUiState() {
        viewModel.uiState.collect { state ->
            updateUiState(state)
        }
    }

    private suspend fun observeArticles() {
        viewModel.articles.collect { articles ->
            articleAdapter.submitList(articles)
        }
    }

    private suspend fun observeIsLoading() {
        viewModel.isLoading.collect { isLoading ->
            binding.loadingOverlay.isVisible = isLoading && articleAdapter.itemCount == 0
            binding.swipeRefresh.isRefreshing = isLoading && articleAdapter.itemCount > 0
        }
    }

    private suspend fun observeIsLoadingMore() {
        viewModel.isLoadingMore.collect { isLoadingMore ->
            binding.loadingMore.isVisible = isLoadingMore
        }
    }

    private suspend fun observeSortOrder() {
        viewModel.sortOrder.collect { sortOrder ->
            binding.buttonSort.text = when (sortOrder) {
                SortOrder.RELEVANCE -> getString(R.string.sort_by_relevance)
                SortOrder.DATE -> getString(R.string.sort_by_date)
            }
        }
    }

    private suspend fun observeError() {
        viewModel.errorMessage.collect { errorMessage ->
            errorMessage?.let {
                showErrorSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    private suspend fun observeResultsInfo() {
        viewModel.resultsInfo.collect { info ->
            binding.textResultsInfo.text = info
            binding.resultsInfoBar.isVisible = info.isNotEmpty()
        }
    }

    private suspend fun observeCitationsMode() {
        viewModel.citationsMode.collect { citationsTitle ->
            if (citationsTitle != null) {
                // Update the search view to show the citations title
                binding.searchView.setQuery(citationsTitle, false)
            }
        }
    }

    private suspend fun observeSavedArticles() {
        viewModel.savedArticleIds.collect { savedIds ->
            articleAdapter.updateSavedArticles(savedIds)
        }
    }

    private fun updateUiState(state: ResultsUiState) {
        binding.apply {
            recyclerResults.isVisible = state == ResultsUiState.Content
            initialState.isVisible = state == ResultsUiState.Initial
            emptyState.isVisible = state == ResultsUiState.Empty
            errorState.isVisible = state == ResultsUiState.Error
            loadingOverlay.isVisible = state == ResultsUiState.Loading
        }
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry) { viewModel.retry() }
            .show()
    }

    private fun openArticle(article: Article) {
        if (article.articleUrl.isBlank()) {
            showErrorSnackbar(getString(R.string.error_loading))
            return
        }
        try {
            // Use ArticleViewActivity to display article details natively
            val intent = ArticleViewActivity.createIntent(
                context = requireContext(),
                url = article.articleUrl,
                title = article.title
            )
            startActivity(intent)
        } catch (e: Exception) {
            showErrorSnackbar(getString(R.string.error_loading))
        }
    }

    private fun openPdf(article: Article) {
        article.pdfUrl?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                showErrorSnackbar(getString(R.string.error_opening_pdf))
            }
        }
    }

    private fun saveArticle(article: Article) {
        val isSaved = viewModel.isArticleSaved(article.id)
        viewModel.toggleSaveState(article)
        val message = if (isSaved) R.string.article_removed else R.string.article_saved
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareArticle(article: Article) {
        val shareText = "${article.title}\n${article.articleUrl}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, article.title)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
    }

    private fun viewCitations(article: Article) {
        // Navigate to citations view or open Google Scholar citations page
        viewModel.loadCitationsFor(article)
    }

    private fun searchAuthor(authorName: String) {
        // Open AuthorSearchActivity to search for this author
        val intent = AuthorSearchActivity.createIntent(requireContext(), authorName)
        startActivity(intent)
    }

    /**
     * Performs a search from external source (e.g., navigation or deep link)
     */
    fun performSearch(query: String) {
        binding.searchView.setQuery(query, true)
    }

    /**
     * Focuses the search input field
     */
    fun focusSearchInput() {
        binding.searchView.requestFocus()
        binding.searchView.isIconified = false
    }

    /**
     * Refreshes the current search results
     */
    fun refresh() {
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ResultsFragment {
            return ResultsFragment()
        }
    }
}

/**
 * UI states for the results screen
 */
sealed class ResultsUiState {
    object Initial : ResultsUiState()
    object Loading : ResultsUiState()
    object Content : ResultsUiState()
    object Empty : ResultsUiState()
    object Error : ResultsUiState()
}

/**
 * Sort order options for search results
 */
enum class SortOrder {
    RELEVANCE,
    DATE
}
