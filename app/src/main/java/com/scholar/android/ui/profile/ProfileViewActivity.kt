package com.scholar.android.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.data.model.AuthorProfile
import com.scholar.android.data.model.YearlyCitation
import com.scholar.android.databinding.ActivityProfileViewBinding
import com.scholar.android.repository.ScholarRepository
import com.scholar.android.ui.article.ArticleViewActivity
import com.scholar.android.ui.results.ArticleAdapter
import kotlinx.coroutines.launch

/**
 * Activity for viewing any author's profile in read-only mode.
 * This is used when clicking on authors from search results or interest chips.
 * No authentication required - just displays the profile.
 */
class ProfileViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileViewBinding
    private val repository = ScholarRepository()
    private var currentProfile: AuthorProfile? = null

    private lateinit var publicationsAdapter: ArticleAdapter

    // Citation history
    private var fullCitationHistory: List<YearlyCitation> = emptyList()
    private var selectedTimeRange = CitationTimeRange.ALL_TIME

    companion object {
        private const val EXTRA_AUTHOR_ID = "author_id"
        private const val EXTRA_AUTHOR_NAME = "author_name"

        fun createIntent(context: Context, authorId: String, authorName: String? = null): Intent {
            return Intent(context, ProfileViewActivity::class.java).apply {
                putExtra(EXTRA_AUTHOR_ID, authorId)
                putExtra(EXTRA_AUTHOR_NAME, authorName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupChartTimeRangeChips()
        setupSortChips()

        val authorId = intent.getStringExtra(EXTRA_AUTHOR_ID)
        val authorName = intent.getStringExtra(EXTRA_AUTHOR_NAME)

        if (authorId.isNullOrBlank()) {
            showError(getString(R.string.error_no_profile_id))
            return
        }

        // Set initial title if name provided
        supportActionBar?.title = authorName ?: getString(R.string.nav_my_profile)

        loadProfile(authorId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        publicationsAdapter = ArticleAdapter(
            onArticleClick = { article ->
                val intent = ArticleViewActivity.createIntent(
                    context = this,
                    url = article.articleUrl,
                    title = article.title
                )
                startActivity(intent)
            },
            onPdfClick = { article ->
                article.pdfUrl?.let { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        showSnackbar(getString(R.string.error_opening_pdf))
                    }
                }
            },
            onSaveClick = { /* Not implemented for view-only */ },
            onShareClick = { article ->
                val shareText = "${article.title}\n${article.articleUrl}"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
            },
            onCitationsClick = { /* Could implement citation search */ }
        )

        binding.recyclerPublications.apply {
            adapter = publicationsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupChartTimeRangeChips() {
        binding.chipGroupTimeRange.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            selectedTimeRange = when (checkedIds.first()) {
                R.id.chip_all_time -> CitationTimeRange.ALL_TIME
                R.id.chip_5_years -> CitationTimeRange.LAST_5_YEARS
                R.id.chip_since_2020 -> CitationTimeRange.SINCE_2020
                R.id.chip_since_2015 -> CitationTimeRange.SINCE_2015
                else -> CitationTimeRange.ALL_TIME
            }
            updateCitationChart()
        }
    }

    private fun setupSortChips() {
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val profile = currentProfile ?: return@setOnCheckedStateChangeListener
            val sorted = when (checkedIds.first()) {
                R.id.chip_sort_citations -> profile.articles.sortedByDescending { it.citationCount }
                R.id.chip_sort_year -> profile.articles.sortedByDescending { it.year ?: "0" }
                else -> profile.articles
            }
            publicationsAdapter.submitList(sorted)
        }
    }

    private fun loadProfile(authorId: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = repository.getAuthorProfile(authorId)

            result.fold(
                onSuccess = { profile ->
                    currentProfile = profile
                    displayProfile(profile)
                    showLoading(false)
                },
                onFailure = { error ->
                    showLoading(false)
                    showError(error.message ?: getString(R.string.profile_not_found))
                }
            )
        }
    }

    private fun displayProfile(profile: AuthorProfile) {
        binding.apply {
            profileContent.isVisible = true
            errorState.isVisible = false

            // Update toolbar title
            supportActionBar?.title = profile.name

            // Profile header
            textName.text = profile.name
            textAffiliation.text = profile.affiliation ?: ""
            textAffiliation.isVisible = !profile.affiliation.isNullOrBlank()

            // Email
            if (!profile.email.isNullOrBlank()) {
                textEmail.text = getString(R.string.verified_email_at, profile.email)
                textEmail.isVisible = true
            } else {
                textEmail.isVisible = false
            }

            // Profile image
            if (!profile.imageUrl.isNullOrBlank()) {
                imageProfile.load(profile.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }
            }

            // Stats
            textCitations.text = formatNumber(profile.citationCount)
            textHIndex.text = profile.hIndex.toString()
            textI10Index.text = profile.i10Index.toString()

            // Research interests
            setupInterestChips(profile.interests)

            // Citation chart
            fullCitationHistory = profile.citationHistory
            if (fullCitationHistory.isNotEmpty()) {
                cardCitationChart.isVisible = true
                updateCitationChart()
            } else {
                cardCitationChart.isVisible = false
            }

            // Publications
            publicationsAdapter.submitList(profile.articles.sortedByDescending { it.citationCount })

            // Hide action buttons (read-only mode)
            buttonSignIn.isVisible = false
            buttonEditProfile.isVisible = false
            buttonChangeProfile.isVisible = false
        }
    }

    private fun setupInterestChips(interests: List<String>) {
        binding.chipGroupInterests.removeAllViews()

        if (interests.isNotEmpty()) {
            binding.labelInterests.isVisible = true
            binding.chipGroupInterests.isVisible = true

            interests.forEach { interest ->
                val chip = Chip(this).apply {
                    text = interest
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        // Open search for authors with this interest
                        searchAuthorsByInterest(interest)
                    }
                }
                binding.chipGroupInterests.addView(chip)
            }
        } else {
            binding.labelInterests.isVisible = false
            binding.chipGroupInterests.isVisible = false
        }
    }

    private fun searchAuthorsByInterest(interest: String) {
        val intent = AuthorSearchActivity.createIntent(this, interest)
        startActivity(intent)
    }

    private fun updateCitationChart() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val filtered = when (selectedTimeRange) {
            CitationTimeRange.ALL_TIME -> fullCitationHistory
            CitationTimeRange.LAST_5_YEARS -> fullCitationHistory.filter { it.year >= currentYear - 4 }
            CitationTimeRange.SINCE_2020 -> fullCitationHistory.filter { it.year >= 2020 }
            CitationTimeRange.SINCE_2015 -> fullCitationHistory.filter { it.year >= 2015 }
        }

        if (filtered.isEmpty()) {
            binding.chartCitations.isVisible = false
            binding.textNoCitationData.isVisible = true
            binding.textSelectedCitation.isVisible = false
            return
        }

        binding.chartCitations.isVisible = true
        binding.textNoCitationData.isVisible = false

        val entries = filtered.mapIndexed { index, citation ->
            BarEntry(index.toFloat(), citation.citations.toFloat())
        }

        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) {
            resources.getColor(R.color.text_primary_dark, theme)
        } else {
            resources.getColor(R.color.text_primary, theme)
        }

        val dataSet = BarDataSet(entries, "Citations").apply {
            color = getColor(R.color.primary)
            valueTextColor = textColor
            valueTextSize = 10f
            setDrawValues(filtered.size <= 10)
        }

        binding.chartCitations.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            animateY(500)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                this.textColor = textColor
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in filtered.indices) {
                            filtered[index].year.toString()
                        } else ""
                    }
                }
            }

            axisLeft.apply {
                this.textColor = textColor
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = resources.getColor(R.color.divider, theme)
            }

            axisRight.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        if (index in filtered.indices) {
                            val citation = filtered[index]
                            binding.textSelectedCitation.text = getString(
                                R.string.citations_in_year,
                                citation.citations,
                                citation.year
                            )
                            binding.textSelectedCitation.isVisible = true
                        }
                    }
                }

                override fun onNothingSelected() {
                    binding.textSelectedCitation.isVisible = false
                }
            })

            invalidate()
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.loadingOverlay.isVisible = loading
        binding.profileContent.isVisible = !loading && currentProfile != null
    }

    private fun showError(message: String) {
        binding.apply {
            loadingOverlay.isVisible = false
            profileContent.isVisible = false
            errorState.isVisible = true
            textErrorMessage.text = message

            buttonRetry.setOnClickListener {
                intent.getStringExtra(EXTRA_AUTHOR_ID)?.let { id ->
                    loadProfile(id)
                }
            }

            buttonTryDifferent.setOnClickListener {
                finish()
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
            number >= 1000 -> String.format("%.1fK", number / 1000.0)
            else -> number.toString()
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
