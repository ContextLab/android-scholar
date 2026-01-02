package com.scholar.android.ui.profile

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.scholar.android.auth.AuthState
import com.scholar.android.auth.GoogleAuthManager
import com.scholar.android.auth.SignInResult
import com.scholar.android.data.model.AuthorProfile
import com.scholar.android.data.model.AuthorSearchResult
import com.scholar.android.data.model.YearlyCitation
import com.scholar.android.repository.ScholarRepository
import java.util.Calendar
import com.scholar.android.util.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Time range options for filtering citation history chart.
 */
enum class CitationTimeRange {
    ALL_TIME,
    LAST_5_YEARS,
    SINCE_2020,
    SINCE_2015
}

/**
 * ViewModel for the Profile screen, managing author profile state, authentication,
 * and profile editing capabilities.
 *
 * Users can VIEW any profile without authentication.
 * Users must LOGIN (with Google) to EDIT their own profile.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "scholar_profile_prefs"
        private const val KEY_AUTHOR_ID = "saved_author_id"
    }

    private val repository = ScholarRepository()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Google Auth Manager
    val authManager = GoogleAuthManager(application)

    // Profile state
    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    // Current loaded profile
    private val _profile = MutableStateFlow<AuthorProfile?>(null)
    val profile: StateFlow<AuthorProfile?> = _profile.asStateFlow()

    // Saved author ID
    private val _savedAuthorId = MutableStateFlow<String?>(null)
    val savedAuthorId: StateFlow<String?> = _savedAuthorId.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Author search results
    private val _authorSearchResults = MutableStateFlow<List<AuthorSearchResult>>(emptyList())
    val authorSearchResults: StateFlow<List<AuthorSearchResult>> = _authorSearchResults.asStateFlow()

    // Authentication state - expose from authManager
    val authState: StateFlow<AuthState> = authManager.authState

    // Whether the currently viewed profile is the user's own profile
    private val _isOwnProfile = MutableStateFlow(false)
    val isOwnProfile: StateFlow<Boolean> = _isOwnProfile.asStateFlow()

    // Whether user can edit (is logged in AND viewing own profile)
    private val _canEdit = MutableStateFlow(false)
    val canEdit: StateFlow<Boolean> = _canEdit.asStateFlow()

    // Citation history time range
    private val _selectedTimeRange = MutableStateFlow(CitationTimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<CitationTimeRange> = _selectedTimeRange.asStateFlow()

    // Filtered citation history based on time range
    private val _filteredCitationHistory = MutableStateFlow<List<YearlyCitation>>(emptyList())
    val filteredCitationHistory: StateFlow<List<YearlyCitation>> = _filteredCitationHistory.asStateFlow()

    init {
        // Check auth state and load profile accordingly
        checkAuthAndLoadProfile()
    }

    /**
     * Checks authentication state and loads the user's profile if signed in.
     * "My Profile" should only show the signed-in user's own profile.
     */
    private fun checkAuthAndLoadProfile() {
        viewModelScope.launch {
            // Observe auth state changes
            authManager.authState.collect { authState ->
                when (authState) {
                    is AuthState.SignedIn -> {
                        // Check if user has linked their Scholar profile
                        val linkedScholarId = AppPreferences.getUserScholarId(getApplication())
                        if (!linkedScholarId.isNullOrBlank()) {
                            // Load the user's own profile
                            _savedAuthorId.value = linkedScholarId
                            _isOwnProfile.value = true
                            loadProfile(linkedScholarId)
                        } else {
                            // Signed in but no profile linked yet
                            _profileState.value = ProfileState.NeedsProfileLink
                        }
                    }
                    is AuthState.SignedOut -> {
                        // Not signed in - show sign-in prompt
                        _profileState.value = ProfileState.NotSignedIn
                        _profile.value = null
                        _savedAuthorId.value = null
                    }
                    is AuthState.Unknown -> {
                        // Still checking - show loading briefly
                        _profileState.value = ProfileState.Loading
                    }
                    is AuthState.Error -> {
                        // Auth error - treat as signed out
                        _profileState.value = ProfileState.NotSignedIn
                        _errorMessage.value = authState.message
                    }
                }
            }
        }
    }

    /**
     * Loads the saved author ID from SharedPreferences.
     * @deprecated Use checkAuthAndLoadProfile instead for "My Profile".
     */
    private fun loadSavedAuthorId() {
        val savedId = prefs.getString(KEY_AUTHOR_ID, null)
        _savedAuthorId.value = savedId

        // If there's a saved ID, automatically load the profile
        if (!savedId.isNullOrBlank()) {
            loadProfile(savedId)
        }
    }

    /**
     * Loads an author profile by ID.
     * @param authorId The Google Scholar author ID to load.
     * @param saveId Whether to save this ID as the user's profile.
     */
    fun loadProfile(authorId: String, saveId: Boolean = false) {
        if (authorId.isBlank()) {
            _errorMessage.value = "Please enter an Author ID"
            return
        }

        _profileState.value = ProfileState.Loading

        viewModelScope.launch {
            val result = repository.getAuthorProfile(authorId.trim())

            result.fold(
                onSuccess = { profile ->
                    _profile.value = profile
                    _profileState.value = ProfileState.Success

                    // Update filtered citation history
                    updateFilteredCitationHistory(profile.citationHistory)

                    // Save the author ID if requested
                    if (saveId) {
                        saveAuthorId(authorId.trim())
                    }
                },
                onFailure = { error ->
                    _profileState.value = ProfileState.Error
                    _errorMessage.value = error.message ?: "Failed to load profile"
                }
            )
        }
    }

    /**
     * Sets the time range for filtering citation history.
     * @param range The CitationTimeRange to apply.
     */
    fun setTimeRange(range: CitationTimeRange) {
        _selectedTimeRange.value = range
        _profile.value?.citationHistory?.let { history ->
            updateFilteredCitationHistory(history)
        }
    }

    /**
     * Filters the citation history based on the selected time range.
     */
    private fun updateFilteredCitationHistory(fullHistory: List<YearlyCitation>) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val filtered = when (_selectedTimeRange.value) {
            CitationTimeRange.ALL_TIME -> fullHistory
            CitationTimeRange.LAST_5_YEARS -> fullHistory.filter { it.year >= currentYear - 4 }
            CitationTimeRange.SINCE_2020 -> fullHistory.filter { it.year >= 2020 }
            CitationTimeRange.SINCE_2015 -> fullHistory.filter { it.year >= 2015 }
        }
        _filteredCitationHistory.value = filtered
    }

    /**
     * Searches for authors by name.
     * This performs a Google Scholar author search and returns matching profiles.
     * @param name The author name to search for.
     */
    fun searchAuthorByName(name: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Please enter a name"
            return
        }

        _profileState.value = ProfileState.Loading
        _authorSearchResults.value = emptyList()

        viewModelScope.launch {
            val result = repository.searchAuthors(name)

            result.fold(
                onSuccess = { authors ->
                    if (authors.isEmpty()) {
                        _profileState.value = ProfileState.Error
                        _errorMessage.value = "No authors found matching \"$name\""
                    } else {
                        _authorSearchResults.value = authors
                        _profileState.value = ProfileState.AuthorSearch
                    }
                },
                onFailure = { error ->
                    _profileState.value = ProfileState.Error
                    _errorMessage.value = error.message ?: "Search failed"
                }
            )
        }
    }

    /**
     * Searches for authors by research interest/label.
     * This performs a Google Scholar author search filtered by the specified interest.
     * @param interest The research interest to search for (e.g., "machine learning").
     */
    fun searchAuthorsByInterest(interest: String) {
        if (interest.isBlank()) {
            _errorMessage.value = "Invalid research interest"
            return
        }

        _profileState.value = ProfileState.Loading
        _authorSearchResults.value = emptyList()

        viewModelScope.launch {
            val result = repository.searchAuthorsByLabel(interest)

            result.fold(
                onSuccess = { authors ->
                    if (authors.isEmpty()) {
                        _profileState.value = ProfileState.Error
                        _errorMessage.value = "No authors found with interest \"$interest\""
                    } else {
                        _authorSearchResults.value = authors
                        _profileState.value = ProfileState.AuthorSearch
                    }
                },
                onFailure = { error ->
                    _profileState.value = ProfileState.Error
                    _errorMessage.value = error.message ?: "Search failed"
                }
            )
        }
    }

    /**
     * Selects an author from search results and loads their full profile.
     * @param author The selected AuthorSearchResult.
     */
    fun selectAuthor(author: AuthorSearchResult) {
        _authorSearchResults.value = emptyList()
        // Load the profile first, then link it to the user's account
        loadProfile(author.id, saveId = true)
        // Link this profile to the user's account
        AppPreferences.linkScholarProfile(getApplication(), author.id)
        _isOwnProfile.value = true
    }

    /**
     * Cancels author search and returns to appropriate state.
     */
    fun cancelAuthorSearch() {
        _authorSearchResults.value = emptyList()
        // Return to NeedsProfileLink state if signed in but no profile linked
        val linkedId = AppPreferences.getUserScholarId(getApplication())
        if (linkedId.isNullOrBlank()) {
            _profileState.value = ProfileState.NeedsProfileLink
        } else {
            _profileState.value = ProfileState.Idle
        }
    }

    /**
     * Refreshes the current profile.
     */
    fun refresh() {
        val currentId = _savedAuthorId.value ?: _profile.value?.id
        if (!currentId.isNullOrBlank()) {
            loadProfile(currentId)
        }
    }

    /**
     * Saves the author ID to SharedPreferences.
     */
    private fun saveAuthorId(authorId: String) {
        prefs.edit().putString(KEY_AUTHOR_ID, authorId).apply()
        _savedAuthorId.value = authorId
    }

    /**
     * Clears the saved profile and returns to input state.
     */
    fun clearProfile() {
        prefs.edit().remove(KEY_AUTHOR_ID).apply()
        _savedAuthorId.value = null
        _profile.value = null
        _profileState.value = ProfileState.Idle
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Retries loading the profile after an error.
     */
    fun retry() {
        val currentId = _savedAuthorId.value ?: return
        loadProfile(currentId)
    }

    /**
     * Checks if there's a saved profile.
     */
    fun hasSavedProfile(): Boolean = !_savedAuthorId.value.isNullOrBlank()

    // ==================== Authentication Methods ====================

    /**
     * Updates whether the currently viewed profile is the user's own profile.
     */
    private fun updateOwnProfileStatus() {
        val linkedScholarId = AppPreferences.getUserScholarId(getApplication())
        val currentProfileId = _profile.value?.id ?: _savedAuthorId.value
        val isLoggedIn = authManager.isSignedIn()

        _isOwnProfile.value = isLoggedIn && linkedScholarId != null && linkedScholarId == currentProfileId
        _canEdit.value = _isOwnProfile.value
    }

    /**
     * Gets the sign-in intent for launching Google Sign-In.
     */
    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    /**
     * Handles the result from Google Sign-In activity.
     * @param data The intent data from onActivityResult
     * @return SignInResult indicating success or failure
     */
    fun handleSignInResult(data: Intent?): SignInResult {
        val result = authManager.handleSignInResult(data)
        if (result is SignInResult.Success) {
            // After successful sign-in, check if we should link to current profile
            val currentProfileId = _profile.value?.id ?: _savedAuthorId.value
            if (currentProfileId != null) {
                // Prompt user to link or auto-link if they're viewing a profile
                linkScholarProfile(currentProfileId)
            }
            updateOwnProfileStatus()
        }
        return result
    }

    /**
     * Links the currently loaded Scholar profile to the authenticated user.
     */
    fun linkScholarProfile(scholarId: String? = null) {
        val idToLink = scholarId ?: _profile.value?.id ?: _savedAuthorId.value
        if (idToLink != null) {
            AppPreferences.linkScholarProfile(getApplication(), idToLink)
            updateOwnProfileStatus()
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        authManager.signOut {
            _isOwnProfile.value = false
            _canEdit.value = false
            onComplete()
        }
    }

    /**
     * Checks if user is currently signed in.
     */
    fun isSignedIn(): Boolean = authManager.isSignedIn()

    /**
     * Gets the currently signed-in Google account.
     */
    fun getSignedInAccount(): GoogleSignInAccount? = authManager.getSignedInAccount()

    /**
     * Gets the linked Scholar profile ID for the authenticated user.
     */
    fun getLinkedScholarId(): String? = AppPreferences.getUserScholarId(getApplication())

    /**
     * Checks if the user can edit profiles.
     * User can edit if they are logged in and viewing their own linked profile.
     */
    fun canEditProfile(): Boolean = _canEdit.value
}

/**
 * UI states for the profile screen.
 */
sealed class ProfileState {
    /** User is not signed in - show sign-in prompt */
    object NotSignedIn : ProfileState()

    /** Signed in but no Scholar profile linked - show search form */
    object NeedsProfileLink : ProfileState()

    /** No profile loaded yet, show input form (legacy - use NeedsProfileLink instead) */
    object Idle : ProfileState()

    /** Loading profile */
    object Loading : ProfileState()

    /** Profile loaded successfully */
    object Success : ProfileState()

    /** Error loading profile */
    object Error : ProfileState()

    /** Showing author search results for selection */
    object AuthorSearch : ProfileState()
}
