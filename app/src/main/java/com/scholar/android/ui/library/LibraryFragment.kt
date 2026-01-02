package com.scholar.android.ui.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.auth.SignInResult
import com.scholar.android.data.model.Article
import com.scholar.android.databinding.FragmentLibraryBinding
import com.scholar.android.ui.article.ArticleViewActivity
import com.scholar.android.ui.results.ArticleAdapter
import kotlinx.coroutines.launch

/**
 * Fragment displaying the user's saved articles library.
 * Requires authentication to access.
 * Supports swipe-to-delete with undo functionality.
 */
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()

    private lateinit var articleAdapter: ArticleAdapter

    // Google Sign-In launcher
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register for sign-in result
        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleSignInResult(result.data)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupSwipeToDelete()
        setupSignInButton()
        observeViewModel()
    }

    private fun setupSignInButton() {
        binding.buttonSignInLibrary.setOnClickListener {
            startSignIn()
        }
    }

    private fun startSignIn() {
        val signInIntent = viewModel.getSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(data: Intent?) {
        when (val result = viewModel.handleSignInResult(data)) {
            is SignInResult.Success -> {
                showSnackbar(getString(R.string.sign_in_success))
            }
            is SignInResult.Error -> {
                showSnackbar(result.message.ifEmpty { getString(R.string.sign_in_failed) })
            }
        }
    }

    private fun setupRecyclerView() {
        articleAdapter = ArticleAdapter(
            onArticleClick = { article -> openArticle(article) },
            onPdfClick = { article -> openPdf(article) },
            onSaveClick = { article -> removeFromLibrary(article) },
            onShareClick = { article -> shareArticle(article) },
            onCitationsClick = { /* Could navigate to citations search */ }
        )

        binding.recyclerLibrary.apply {
            adapter = articleAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeResources(R.color.primary, R.color.secondary)
            // Since this is local data, just simulate a refresh
            setOnRefreshListener {
                isRefreshing = false
            }
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val article = articleAdapter.currentList[position]
                removeFromLibraryWithUndo(article)
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerLibrary)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeLibraryState() }
                launch { observeSavedArticles() }
            }
        }
    }

    private suspend fun observeLibraryState() {
        viewModel.libraryState.collect { state ->
            updateUiForState(state)
        }
    }

    private suspend fun observeSavedArticles() {
        viewModel.savedArticles.collect { articles ->
            articleAdapter.submitList(articles)
            updateArticleCount(articles)
        }
    }

    private fun updateUiForState(state: LibraryState) {
        binding.apply {
            swipeRefresh.isRefreshing = false

            // Hide all states first
            notSignedInState.isVisible = false
            loadingState.isVisible = false
            recyclerLibrary.isVisible = false
            emptyState.isVisible = false

            // Show appropriate state
            when (state) {
                is LibraryState.NotSignedIn -> {
                    notSignedInState.isVisible = true
                    textArticleCount.text = ""
                }
                is LibraryState.Loading -> {
                    loadingState.isVisible = true
                }
                is LibraryState.HasArticles -> {
                    recyclerLibrary.isVisible = true
                }
                is LibraryState.Empty -> {
                    emptyState.isVisible = true
                    textArticleCount.text = ""
                }
            }
        }
    }

    private fun updateArticleCount(articles: List<Article>) {
        // Only update count when signed in and has articles
        if (viewModel.libraryState.value == LibraryState.HasArticles) {
            binding.textArticleCount.text = when {
                articles.isEmpty() -> ""
                articles.size == 1 -> getString(R.string.library_article_count_singular)
                else -> getString(R.string.library_article_count, articles.size)
            }
        }
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

    private fun removeFromLibrary(article: Article) {
        removeFromLibraryWithUndo(article)
    }

    private fun removeFromLibraryWithUndo(article: Article) {
        viewModel.removeArticle(article)
        Snackbar.make(binding.root, R.string.article_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                viewModel.saveArticle(article)
            }
            .show()
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

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): LibraryFragment {
            return LibraryFragment()
        }
    }
}
