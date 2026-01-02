package com.scholar.android.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.scholar.android.util.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Sign-In authentication for the app.
 *
 * This handles the Google authentication flow which is required for users
 * to edit their Google Scholar profile through the WebView. The Google
 * session is shared with the WebView, allowing authenticated access to
 * Scholar's edit pages.
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleAuthManager"
        const val RC_SIGN_IN = 9001
    }

    private val googleSignInClient: GoogleSignInClient

    // Authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Current signed-in account
    private val _currentAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val currentAccount: StateFlow<GoogleSignInAccount?> = _currentAccount.asStateFlow()

    init {
        // Configure Google Sign-In
        // We request email and profile for basic user info
        // The sign-in will share the auth session with WebViews
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)

        // Check for existing sign-in
        checkExistingSignIn()
    }

    /**
     * Checks if there's an existing signed-in account.
     */
    private fun checkExistingSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            _currentAccount.value = account
            _authState.value = AuthState.SignedIn(account)
            // Update preferences
            AppPreferences.saveUserAuth(
                context,
                account.email ?: "",
                account.displayName,
                account.photoUrl?.toString()
            )
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    /**
     * Gets the sign-in intent to start the Google Sign-In flow.
     * Launch this intent with startActivityForResult(intent, RC_SIGN_IN).
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Handles the result from the Google Sign-In activity.
     * Call this from onActivityResult() in your Activity or Fragment.
     *
     * @param data The intent data from onActivityResult
     * @return Result containing the signed-in account or an error
     */
    fun handleSignInResult(data: Intent?): SignInResult {
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                _currentAccount.value = account
                _authState.value = AuthState.SignedIn(account)

                // Save to preferences
                AppPreferences.saveUserAuth(
                    context,
                    account.email ?: "",
                    account.displayName,
                    account.photoUrl?.toString()
                )

                Log.d(TAG, "Sign-in successful: ${account.email}")
                SignInResult.Success(account)
            } else {
                Log.e(TAG, "Sign-in returned null account")
                SignInResult.Error("Sign-in failed: No account returned")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
            _authState.value = AuthState.Error(getErrorMessage(e.statusCode))
            SignInResult.Error(getErrorMessage(e.statusCode))
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        googleSignInClient.signOut().addOnCompleteListener {
            _currentAccount.value = null
            _authState.value = AuthState.SignedOut
            AppPreferences.clearUserAuth(context)
            Log.d(TAG, "Sign-out successful")
            onComplete()
        }
    }

    /**
     * Revokes access (disconnects the app from user's Google account).
     * Use this when user wants to completely remove the app's access.
     */
    fun revokeAccess(onComplete: () -> Unit = {}) {
        googleSignInClient.revokeAccess().addOnCompleteListener {
            _currentAccount.value = null
            _authState.value = AuthState.SignedOut
            AppPreferences.clearUserAuthComplete(context)
            Log.d(TAG, "Access revoked")
            onComplete()
        }
    }

    /**
     * Checks if a user is currently signed in.
     */
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    /**
     * Gets the currently signed-in account, if any.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Silently signs in (refreshes token) if there's a valid existing session.
     */
    fun silentSignIn(onResult: (SignInResult) -> Unit) {
        googleSignInClient.silentSignIn()
            .addOnSuccessListener { account ->
                _currentAccount.value = account
                _authState.value = AuthState.SignedIn(account)
                AppPreferences.saveUserAuth(
                    context,
                    account.email ?: "",
                    account.displayName,
                    account.photoUrl?.toString()
                )
                onResult(SignInResult.Success(account))
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "Silent sign-in failed, user needs to sign in interactively", e)
                _authState.value = AuthState.SignedOut
                onResult(SignInResult.Error("Sign-in required"))
            }
    }

    /**
     * Converts API error codes to user-friendly messages.
     */
    private fun getErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            12500 -> "Sign-in was cancelled"
            12501 -> "Sign-in was cancelled"
            12502 -> "Sign-in currently in progress"
            7 -> "Network error. Please check your connection."
            8 -> "Internal error. Please try again."
            10 -> "Developer error. Please contact support."
            else -> "Sign-in failed (error code: $statusCode)"
        }
    }
}

/**
 * Represents the current authentication state.
 */
sealed class AuthState {
    /** Initial state, hasn't been checked yet */
    object Unknown : AuthState()

    /** User is signed out */
    object SignedOut : AuthState()

    /** User is signed in */
    data class SignedIn(val account: GoogleSignInAccount) : AuthState()

    /** An error occurred during sign-in */
    data class Error(val message: String) : AuthState()
}

/**
 * Result from a sign-in attempt.
 */
sealed class SignInResult {
    data class Success(val account: GoogleSignInAccount) : SignInResult()
    data class Error(val message: String) : SignInResult()
}
