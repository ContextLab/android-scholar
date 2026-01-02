package com.scholar.android.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import coil.load
import coil.request.ErrorResult
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.scholar.android.R
import com.scholar.android.auth.AuthState
import com.scholar.android.auth.SignInResult
import com.scholar.android.data.model.Article
import com.scholar.android.data.model.AuthorProfile
import com.scholar.android.data.model.YearlyCitation
import com.scholar.android.databinding.FragmentProfileBinding
import com.scholar.android.ui.article.ArticleViewActivity
import com.scholar.android.ui.results.ArticleAdapter
import kotlinx.coroutines.launch

/**
 * Fragment displaying the user's Google Scholar profile.
 * Shows either an input form to enter Author ID or the loaded profile.
 *
 * Authentication:
 * - Users can VIEW any profile without authentication
 * - Users must LOGIN (with Google) to EDIT their own profile
 * - Once logged in, users can edit their profile via WebView
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var authorSearchAdapter: AuthorSearchAdapter

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
            } else {
                showSnackbar(getString(R.string.sign_in_failed))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAuthorSearchRecyclerView()
        setupInputForm()
        setupSwipeRefresh()
        setupButtons()
        setupAuthButtons()
        setupCitationChart()
        setupTimeRangeChips()
        setupSortChips()
        observeViewModel()
    }

    // Store current articles for sorting
    private var currentArticles: List<Article> = emptyList()

    private fun setupRecyclerView() {
        articleAdapter = ArticleAdapter(
            onArticleClick = { article -> openArticle(article) },
            onPdfClick = { article -> openPdf(article) },
            onSaveClick = { /* TODO: Implement save */ },
            onShareClick = { article -> shareArticle(article) },
            onCitationsClick = { /* TODO: Implement citations */ }
        )

        binding.recyclerPublications.apply {
            adapter = articleAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupAuthorSearchRecyclerView() {
        authorSearchAdapter = AuthorSearchAdapter { author ->
            viewModel.selectAuthor(author)
        }

        binding.recyclerAuthorResults.apply {
            adapter = authorSearchAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupInputForm() {
        // Handle keyboard "Done" action
        binding.editAuthorId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                findProfile()
                true
            } else {
                false
            }
        }

        // Find Profile button
        binding.buttonFindProfile.setOnClickListener {
            findProfile()
        }
    }

    private fun findProfile() {
        val input = binding.editAuthorId.text?.toString()?.trim() ?: ""
        if (input.isNotBlank()) {
            // Check if input looks like an author ID (typically alphanumeric, ~12 chars)
            // or if it's a name (contains spaces or is longer text)
            if (looksLikeAuthorId(input)) {
                viewModel.loadProfile(input, saveId = true)
            } else {
                // Treat as name search
                viewModel.searchAuthorByName(input)
            }
            // Hide keyboard
            binding.editAuthorId.clearFocus()
            hideKeyboard()
        } else {
            binding.inputLayoutAuthorId.error = getString(R.string.enter_author_id)
        }
    }

    /**
     * Heuristic to determine if input is an author ID vs a name.
     * Author IDs are typically alphanumeric strings like "JicYPdAAAAAJ".
     * Names are typically all letters, possibly with spaces.
     */
    private fun looksLikeAuthorId(input: String): Boolean {
        // If it contains spaces, it's definitely a name
        if (input.contains(" ")) return false
        // If it's all letters (no numbers), treat as a name search
        if (input.all { it.isLetter() }) return false
        // If it contains numbers or special chars, it's likely an ID
        return true
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.editAuthorId.windowToken, 0)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeResources(R.color.primary, R.color.secondary)
            setOnRefreshListener {
                viewModel.refresh()
            }
        }
    }

    private fun setupButtons() {
        binding.buttonChangeProfile.setOnClickListener {
            viewModel.clearProfile()
        }

        binding.buttonRetry.setOnClickListener {
            viewModel.retry()
        }

        binding.buttonTryDifferent.setOnClickListener {
            viewModel.clearProfile()
        }

        binding.buttonCancelSearch.setOnClickListener {
            viewModel.cancelAuthorSearch()
        }
    }

    /**
     * Sets up the authentication buttons (Sign In and Edit Profile).
     */
    private fun setupAuthButtons() {
        // Sign In button (on profile card) - initiates Google Sign-In
        binding.buttonSignIn.setOnClickListener {
            startSignIn()
        }

        // Sign In button (on not-signed-in state) - initiates Google Sign-In
        binding.buttonSignInPrompt.setOnClickListener {
            startSignIn()
        }

        // Edit Profile button - opens the profile edit activity
        binding.buttonEditProfile.setOnClickListener {
            openProfileEditor()
        }
    }

    /**
     * Starts the Google Sign-In flow.
     */
    private fun startSignIn() {
        val signInIntent = viewModel.getSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    /**
     * Handles the result from Google Sign-In.
     */
    private fun handleSignInResult(data: Intent?) {
        when (val result = viewModel.handleSignInResult(data)) {
            is SignInResult.Success -> {
                showSnackbar(getString(R.string.sign_in_success))
                updateAuthButtons()
            }
            is SignInResult.Error -> {
                showSnackbar(result.message)
            }
        }
    }

    /**
     * Opens the profile edit activity with WebView.
     */
    private fun openProfileEditor() {
        val scholarId = viewModel.profile.value?.id ?: viewModel.getLinkedScholarId()
        if (scholarId != null) {
            val intent = ProfileEditActivity.createIntent(
                requireContext(),
                scholarId,
                ProfileEditActivity.EDIT_TYPE_PROFILE
            )
            startActivity(intent)
        } else {
            showSnackbar(getString(R.string.must_sign_in_to_edit))
        }
    }

    /**
     * Updates the visibility of auth buttons based on current state.
     */
    private fun updateAuthButtons() {
        val isSignedIn = viewModel.isSignedIn()
        val canEdit = viewModel.canEditProfile()

        binding.apply {
            // Show sign-in button when viewing a profile and not signed in
            buttonSignIn.isVisible = !isSignedIn && viewModel.profile.value != null

            // Show edit button when signed in and can edit (viewing own profile)
            buttonEditProfile.isVisible = canEdit
        }
    }

    /**
     * Sets up the citation bar chart with styling and interaction.
     */
    private fun setupCitationChart() {
        binding.chartCitations.apply {
            // General settings
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setFitBars(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false

            // X-axis settings
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
            }

            // Left Y-axis settings
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
                axisMinimum = 0f
            }

            // Disable right Y-axis
            axisRight.isEnabled = false

            // Touch listener for selecting bars
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let { entry ->
                        val year = entry.x.toInt()
                        val citations = entry.y.toInt()
                        showSelectedCitation(year, citations)
                    }
                }

                override fun onNothingSelected() {
                    binding.textSelectedCitation.isVisible = false
                }
            })

            // Set empty data initially
            data = null
            invalidate()
        }
    }

    /**
     * Sets up the time range selection chips.
     */
    private fun setupTimeRangeChips() {
        binding.chipGroupTimeRange.setOnCheckedStateChangeListener { _, checkedIds ->
            val range = when {
                checkedIds.contains(R.id.chip_all_time) -> CitationTimeRange.ALL_TIME
                checkedIds.contains(R.id.chip_5_years) -> CitationTimeRange.LAST_5_YEARS
                checkedIds.contains(R.id.chip_since_2020) -> CitationTimeRange.SINCE_2020
                checkedIds.contains(R.id.chip_since_2015) -> CitationTimeRange.SINCE_2015
                else -> CitationTimeRange.ALL_TIME
            }
            viewModel.setTimeRange(range)
        }
    }

    /**
     * Sets up the sort chips for publications.
     */
    private fun setupSortChips() {
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val sortedArticles = when {
                checkedIds.contains(R.id.chip_sort_year) ->
                    currentArticles.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                else -> // Sort by citations (default)
                    currentArticles.sortedByDescending { it.citationCount }
            }
            articleAdapter.submitList(sortedArticles)
        }
    }

    /**
     * Displays the selected bar's citation information.
     */
    private fun showSelectedCitation(year: Int, citations: Int) {
        binding.textSelectedCitation.apply {
            text = getString(R.string.citations_in_year, citations, year)
            isVisible = true
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeProfileState() }
                launch { observeProfile() }
                launch { observeFilteredCitationHistory() }
                launch { observeError() }
                launch { observeAuthorSearchResults() }
                launch { observeAuthState() }
                launch { observeCanEdit() }
            }
        }
    }

    private suspend fun observeProfileState() {
        viewModel.profileState.collect { state ->
            updateUiState(state)
        }
    }

    private suspend fun observeProfile() {
        viewModel.profile.collect { profile ->
            profile?.let { displayProfile(it) }
        }
    }

    private suspend fun observeError() {
        viewModel.errorMessage.collect { errorMessage ->
            errorMessage?.let {
                binding.textErrorMessage.text = it
                showErrorSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    private suspend fun observeAuthorSearchResults() {
        viewModel.authorSearchResults.collect { authors ->
            authorSearchAdapter.submitList(authors)
        }
    }

    private suspend fun observeAuthState() {
        viewModel.authState.collect { state ->
            when (state) {
                is AuthState.SignedIn -> {
                    updateAuthButtons()
                }
                is AuthState.SignedOut -> {
                    updateAuthButtons()
                }
                is AuthState.Error -> {
                    showSnackbar(state.message)
                    updateAuthButtons()
                }
                AuthState.Unknown -> {
                    // Initial state, do nothing
                }
            }
        }
    }

    private suspend fun observeCanEdit() {
        viewModel.canEdit.collect { canEdit ->
            binding.buttonEditProfile.isVisible = canEdit
            binding.buttonSignIn.isVisible = !viewModel.isSignedIn() && viewModel.profile.value != null
        }
    }

    private suspend fun observeFilteredCitationHistory() {
        viewModel.filteredCitationHistory.collect { citations ->
            updateCitationChart(citations)
        }
    }

    /**
     * Updates the citation bar chart with the provided data.
     */
    private fun updateCitationChart(citations: List<YearlyCitation>) {
        binding.apply {
            if (citations.isEmpty()) {
                // Show empty state
                chartCitations.isVisible = false
                textNoCitationData.isVisible = true
                textSelectedCitation.isVisible = false
                cardCitationChart.isVisible = false
                return@apply
            }

            // Show chart
            cardCitationChart.isVisible = true
            chartCitations.isVisible = true
            textNoCitationData.isVisible = false

            // Create bar entries
            val entries = citations.map { citation ->
                BarEntry(citation.year.toFloat(), citation.citations.toFloat())
            }

            // Create dataset with Google Scholar blue color
            val dataSet = BarDataSet(entries, "Citations").apply {
                color = ContextCompat.getColor(requireContext(), R.color.primary)
                valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                valueTextSize = 9f
                setDrawValues(citations.size <= 15) // Only show values if not too crowded

                // Format values as integers
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }

            // Create bar data
            val barData = BarData(dataSet).apply {
                barWidth = 0.7f
            }

            // Set x-axis formatter to show years
            chartCitations.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            // Update chart
            chartCitations.apply {
                data = barData

                // Animate the chart
                animateY(1000, Easing.EaseOutCubic)

                // Refresh
                notifyDataSetChanged()
                invalidate()
            }
        }
    }

    private fun updateUiState(state: ProfileState) {
        binding.apply {
            swipeRefresh.isRefreshing = false
            loadingOverlay.isVisible = state == ProfileState.Loading
            notSignedInState.isVisible = state == ProfileState.NotSignedIn
            // NeedsProfileLink uses the same input form but with different context
            inputState.isVisible = state == ProfileState.Idle || state == ProfileState.NeedsProfileLink
            profileContent.isVisible = state == ProfileState.Success
            errorState.isVisible = state == ProfileState.Error
            authorSearchState.isVisible = state == ProfileState.AuthorSearch
        }
    }

    private fun displayProfile(profile: AuthorProfile) {
        binding.apply {
            // Name and affiliation
            textName.text = profile.name
            textAffiliation.text = profile.affiliation ?: ""
            textAffiliation.isVisible = !profile.affiliation.isNullOrBlank()

            // Email
            if (!profile.email.isNullOrBlank()) {
                textEmail.text = if (profile.email.contains("@")) {
                    profile.email
                } else {
                    getString(R.string.verified_email_at, profile.email)
                }
                textEmail.isVisible = true
            } else {
                textEmail.isVisible = false
            }

            // Profile image
            if (!profile.imageUrl.isNullOrBlank()) {
                Log.d("ProfileFragment", "Loading profile image: ${profile.imageUrl}")
                imageProfile.load(profile.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                    listener(
                        onSuccess = { _, result ->
                            Log.d("ProfileFragment", "Image loaded successfully: ${result.dataSource}")
                        },
                        onError = { _, result ->
                            Log.e("ProfileFragment", "Image load failed: ${result.throwable.message}", result.throwable)
                        }
                    )
                }
            } else {
                imageProfile.setImageResource(R.drawable.ic_person_placeholder)
            }

            // Stats
            textCitations.text = formatNumber(profile.citationCount)
            textHIndex.text = profile.hIndex.toString()
            textI10Index.text = profile.i10Index.toString()

            // Research interests
            if (profile.interests.isNotEmpty()) {
                labelInterests.isVisible = true
                chipGroupInterests.isVisible = true
                chipGroupInterests.removeAllViews()

                profile.interests.forEach { interest ->
                    val chip = Chip(requireContext()).apply {
                        text = interest
                        isClickable = true
                        isFocusable = true
                        isCheckable = false
                        // Use outlined style with smaller size
                        setChipBackgroundColorResource(android.R.color.transparent)
                        setChipStrokeColorResource(R.color.text_hint)
                        chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        // Make chips smaller
                        textSize = 12f
                        chipMinHeight = resources.getDimension(R.dimen.chip_min_height)
                        chipStartPadding = resources.getDimension(R.dimen.chip_padding)
                        chipEndPadding = resources.getDimension(R.dimen.chip_padding)
                        // Ensure ripple effect for touch feedback
                        isEnabled = true
                    }
                    // Set click listener to search for authors with this research interest
                    chip.setOnClickListener {
                        searchAuthorsByInterest(interest)
                    }
                    chipGroupInterests.addView(chip)
                }
            } else {
                labelInterests.isVisible = false
                chipGroupInterests.isVisible = false
            }

            // Articles - store and apply default sorting (by citations)
            currentArticles = profile.articles
            val sortedArticles = currentArticles.sortedByDescending { it.citationCount }
            articleAdapter.submitList(sortedArticles)

            // Update auth buttons when profile is displayed
            updateAuthButtons()
        }
    }

    /**
     * Shows a snackbar message.
     */
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

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry) { viewModel.retry() }
            .show()
    }

    private fun openArticle(article: Article) {
        if (article.articleUrl.isNotBlank()) {
            try {
                // Open article in in-app WebView instead of external browser
                val intent = ArticleViewActivity.createIntent(
                    requireContext(),
                    article.articleUrl,
                    article.title
                )
                startActivity(intent)
            } catch (e: Exception) {
                showErrorSnackbar(getString(R.string.error_loading))
            }
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

    private fun shareArticle(article: Article) {
        val shareText = "${article.title}\n${article.articleUrl}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, article.title)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)))
    }

    /**
     * Opens the author search screen for the given research interest.
     * @param interest The research interest to search for.
     */
    private fun searchAuthorsByInterest(interest: String) {
        val intent = AuthorSearchActivity.createIntent(requireContext(), interest)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ProfileFragment {
            return ProfileFragment()
        }
    }
}
