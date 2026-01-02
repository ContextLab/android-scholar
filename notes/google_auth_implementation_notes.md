# Google Authentication Implementation Notes

**Date:** 2026-01-01
**Status:** Completed - Build Successful

## Summary
Implemented Google Sign-In authentication for profile editing capabilities in the Android Scholar app.

## Requirements Implemented
1. Users can VIEW any profile without authentication
2. Users must LOGIN (with Google) to edit their OWN profile
3. Login uses Google Sign-In (pass-through to Google authentication)
4. Once logged in, the user's own profile is automatically linked
5. Logged-in users can edit: publications, authors, homepage, affiliation, keywords
6. Edits use WebView to interact with actual Google Scholar (since there's no API)

## Files Created
1. **app/src/main/java/com/scholar/android/auth/GoogleAuthManager.kt**
   - Manages Google Sign-In authentication
   - Exposes AuthState (Unknown, SignedIn, SignedOut, Error)
   - Handles sign-in/sign-out flows
   - Provides getSignInIntent(), handleSignInResult(), signOut(), revokeAccess()

2. **app/src/main/java/com/scholar/android/ui/profile/ProfileEditActivity.kt**
   - WebView-based activity for editing Google Scholar profile
   - Supports three edit types: profile, publications, coauthors
   - Loads authenticated Google Scholar edit pages
   - Mobile-optimized CSS injection
   - Proper cookie handling for Google session sharing

3. **app/src/main/res/layout/activity_profile_edit.xml**
   - Layout with toolbar and WebView
   - Progress indicator for page loading

4. **app/src/main/res/drawable/ic_google.xml**
   - Google "G" logo icon for sign-in button

5. **app/src/main/res/drawable/ic_edit.xml**
   - Edit pencil icon for edit profile button

## Files Modified
1. **app/build.gradle.kts**
   - Added Google Play Services Auth dependency (20.7.0)
   - Added Credentials Manager dependencies for modern auth flow

2. **app/src/main/java/com/scholar/android/util/AppPreferences.kt**
   - Added auth storage keys (isLoggedIn, userEmail, userName, userPhotoUrl, userScholarId)
   - Added auth methods: saveUserAuth(), clearUserAuth(), linkScholarProfile(), etc.

3. **app/src/main/java/com/scholar/android/ui/profile/ProfileViewModel.kt**
   - Added GoogleAuthManager integration
   - Added auth state exposure (authState, isOwnProfile, canEdit)
   - Added auth methods: getSignInIntent(), handleSignInResult(), signOut(), linkScholarProfile()

4. **app/src/main/java/com/scholar/android/ui/profile/ProfileFragment.kt**
   - Added sign-in launcher using ActivityResultContracts
   - Added setupAuthButtons() for sign-in and edit profile buttons
   - Added auth state observers (observeAuthState, observeCanEdit)
   - Added updateAuthButtons() to manage button visibility

5. **app/src/main/res/layout/fragment_profile.xml**
   - Added button_sign_in (outlined button with Google icon)
   - Added button_edit_profile (filled button with edit icon)
   - Reorganized action buttons in a horizontal LinearLayout

6. **app/src/main/res/values/strings.xml**
   - Added auth-related strings (sign_in_to_edit, sign_in_success, sign_in_failed, etc.)

7. **app/src/main/AndroidManifest.xml**
   - Added ProfileEditActivity declaration

## Architecture
- **Authentication Flow:**
  1. User taps "Sign in to Edit" button
  2. GoogleAuthManager returns sign-in intent
  3. ProfileFragment launches intent with ActivityResultLauncher
  4. On success, GoogleAuthManager updates AuthState
  5. ProfileViewModel links current profile to authenticated user
  6. "Edit Profile" button becomes visible

- **Edit Flow:**
  1. User taps "Edit Profile" button
  2. ProfileEditActivity opens with scholar ID
  3. WebView loads Google Scholar edit page
  4. CookieManager shares Google session
  5. User edits directly in WebView (actual Scholar UI)

## Technical Notes
- Uses Google Play Services Auth (20.7.0) for sign-in
- WebView shares cookies with app for seamless auth
- No public API for Scholar edits - uses WebView pass-through
- CSS injection improves mobile form experience
- State managed via Kotlin StateFlow for reactive UI updates

## Future Improvements
- Add profile photo editing support
- Add direct publication management
- Add co-author management shortcuts
- Add sign-out option in settings/menu
- Add profile linking prompt on first sign-in
