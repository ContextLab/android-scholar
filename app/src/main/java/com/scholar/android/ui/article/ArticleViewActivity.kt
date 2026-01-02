package com.scholar.android.ui.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.scholar.android.R
import com.scholar.android.data.model.Article
import com.scholar.android.data.model.ArticleDetail
import com.scholar.android.databinding.ActivityArticleViewBinding
import com.scholar.android.databinding.DialogCitationBinding
import com.scholar.android.databinding.ItemCitedByBinding
import com.scholar.android.network.ScholarHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Activity for viewing article details in a native layout.
 * Fetches article page HTML and parses it to display in native MaterialDesign components.
 */
class ArticleViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ArticleViewActivity"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTICLE_JSON = "article_json"

        // User agent matching Chrome on Android
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * Creates an intent to view an article.
         * @param context The context to create the intent from
         * @param url The URL of the article to view
         * @param title Optional title for the toolbar
         */
        fun createIntent(context: Context, url: String, title: String? = null): Intent {
            return Intent(context, ArticleViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
        }

        /**
         * Creates an intent to view an article with pre-populated data.
         * @param context The context to create the intent from
         * @param article The Article object with basic data
         */
        fun createIntent(context: Context, article: Article): Intent {
            return Intent(context, ArticleViewActivity::class.java).apply {
                putExtra(EXTRA_URL, article.articleUrl)
                putExtra(EXTRA_TITLE, article.title)
            }
        }
    }

    private lateinit var binding: ActivityArticleViewBinding
    private val parser = ScholarHtmlParser()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var currentUrl: String? = null
    private var articleTitle: String? = null
    private var articleDetail: ArticleDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL)
        articleTitle = intent.getStringExtra(EXTRA_TITLE)

        if (currentUrl.isNullOrBlank()) {
            finish()
            return
        }

        setupToolbar()
        setupClickListeners()
        loadArticle()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = articleTitle ?: getString(R.string.app_name)
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.buttonRetry.setOnClickListener {
            loadArticle()
        }

        binding.buttonOpenInBrowser.setOnClickListener {
            openInBrowser()
        }

        binding.buttonPdf.setOnClickListener {
            articleDetail?.pdfUrl?.let { url ->
                openUrl(url)
            }
        }

        binding.buttonCite.setOnClickListener {
            showCitationDialog()
        }

        binding.buttonShare.setOnClickListener {
            shareArticle()
        }

        binding.chipCitations.setOnClickListener {
            articleDetail?.citedByUrl?.let { url ->
                openUrl(url)
            }
        }

        binding.buttonViewAllCitations.setOnClickListener {
            articleDetail?.citedByUrl?.let { url ->
                openUrl(url)
            }
        }
    }

    private fun loadArticle() {
        showLoading()

        lifecycleScope.launch {
            try {
                val html = fetchArticlePage(currentUrl!!)
                if (html != null) {
                    val detail = parseArticle(html)
                    if (detail != null) {
                        articleDetail = detail
                        displayArticle(detail)
                    } else {
                        showError(getString(R.string.error_loading))
                    }
                } else {
                    showError(getString(R.string.error_loading))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading article", e)
                showError(getString(R.string.error_loading))
            }
        }
    }

    private suspend fun fetchArticlePage(url: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching article from: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.e(TAG, "HTTP error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching article", e)
            null
        }
    }

    private suspend fun parseArticle(html: String): ArticleDetail? = withContext(Dispatchers.Default) {
        // Check if blocked by CAPTCHA
        if (parser.isBlocked(html)) {
            Log.w(TAG, "Request blocked by CAPTCHA")
            return@withContext null
        }

        parser.parseArticleDetail(html, currentUrl!!)
    }

    private fun displayArticle(detail: ArticleDetail) {
        supportActionBar?.title = detail.title.take(50) + if (detail.title.length > 50) "..." else ""

        binding.textTitle.text = detail.title
        binding.textAuthors.text = detail.getAuthorsString()
        binding.textVenue.text = detail.getFormattedVenue()

        // Citation count
        if (detail.citationCount > 0) {
            binding.chipCitations.text = getString(R.string.cited_by, detail.citationCount)
            binding.chipCitations.visibility = View.VISIBLE
        } else {
            binding.chipCitations.visibility = View.GONE
        }

        // PDF button
        if (!detail.pdfUrl.isNullOrBlank()) {
            binding.buttonPdf.visibility = View.VISIBLE
        } else {
            binding.buttonPdf.visibility = View.GONE
        }

        // Abstract
        if (!detail.abstract.isNullOrBlank()) {
            binding.textAbstract.text = detail.abstract
            binding.labelAbstract.visibility = View.VISIBLE
            binding.textAbstract.visibility = View.VISIBLE
        } else {
            binding.textAbstract.text = getString(R.string.article_no_abstract)
            binding.labelAbstract.visibility = View.VISIBLE
            binding.textAbstract.visibility = View.VISIBLE
        }

        // Cited by section
        if (detail.citingArticles.isNotEmpty()) {
            displayCitingArticles(detail.citingArticles)
        }

        // Related articles section
        if (detail.relatedArticles.isNotEmpty()) {
            displayRelatedArticles(detail.relatedArticles)
        }

        showContent()
    }

    private fun displayCitingArticles(articles: List<Article>) {
        binding.containerCitedBy.removeAllViews()

        articles.take(5).forEach { article ->
            val itemBinding = ItemCitedByBinding.inflate(
                LayoutInflater.from(this),
                binding.containerCitedBy,
                false
            )

            itemBinding.textTitle.text = article.title
            itemBinding.textAuthors.text = buildString {
                append(article.getAuthorsString())
                article.year?.let { append(" - $it") }
            }
            if (article.citationCount > 0) {
                itemBinding.textCitations.text = article.getFormattedCitationCount()
                itemBinding.textCitations.visibility = View.VISIBLE
            } else {
                itemBinding.textCitations.visibility = View.GONE
            }

            itemBinding.root.setOnClickListener {
                if (article.articleUrl.isNotBlank()) {
                    startActivity(createIntent(this, article))
                }
            }

            binding.containerCitedBy.addView(itemBinding.root)
        }

        binding.dividerCitedBy.visibility = View.VISIBLE
        binding.sectionCitedBy.visibility = View.VISIBLE
    }

    private fun displayRelatedArticles(articles: List<Article>) {
        binding.containerRelated.removeAllViews()

        articles.take(5).forEach { article ->
            val itemBinding = ItemCitedByBinding.inflate(
                LayoutInflater.from(this),
                binding.containerRelated,
                false
            )

            itemBinding.textTitle.text = article.title
            itemBinding.textAuthors.text = buildString {
                append(article.getAuthorsString())
                article.year?.let { append(" - $it") }
            }
            if (article.citationCount > 0) {
                itemBinding.textCitations.text = article.getFormattedCitationCount()
                itemBinding.textCitations.visibility = View.VISIBLE
            } else {
                itemBinding.textCitations.visibility = View.GONE
            }

            itemBinding.root.setOnClickListener {
                if (article.articleUrl.isNotBlank()) {
                    startActivity(createIntent(this, article))
                }
            }

            binding.containerRelated.addView(itemBinding.root)
        }

        binding.dividerRelated.visibility = View.VISIBLE
        binding.sectionRelated.visibility = View.VISIBLE
    }

    private fun showCitationDialog() {
        val detail = articleDetail ?: return

        val dialogBinding = DialogCitationBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.article_select_citation_format)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        // Set initial citation (MLA)
        dialogBinding.textCitation.text = detail.getMlaCitation()

        // Tab selection listener
        dialogBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val citation = when (tab?.position) {
                    0 -> detail.getMlaCitation()
                    1 -> detail.getApaCitation()
                    2 -> detail.getChicagoCitation()
                    3 -> detail.getHarvardCitation()
                    4 -> detail.getBibtexCitation()
                    else -> detail.getMlaCitation()
                }
                dialogBinding.textCitation.text = citation
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Copy button
        dialogBinding.buttonCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("citation", dialogBinding.textCitation.text)
            clipboard.setPrimaryClip(clip)

            Snackbar.make(binding.root, R.string.article_citation_copied, Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareArticle() {
        val url = articleDetail?.articleUrl ?: currentUrl ?: return
        val title = articleDetail?.title ?: articleTitle ?: ""

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
    }

    private fun openInBrowser() {
        val url = currentUrl ?: return
        openUrl(url)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening URL", e)
        }
    }

    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showContent() {
        binding.loadingContainer.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingContainer.visibility = View.GONE
        binding.scrollView.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
        binding.textError.text = message
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.article_view_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_share -> {
                shareArticle()
                true
            }
            R.id.action_open_browser -> {
                openInBrowser()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
