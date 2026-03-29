package com.pixeleye.plantdoctor.data.api

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken

class AuthManager(
    private val supabaseClient: SupabaseClient,
    private val webClientId: String,
    private val billingManager: BillingManager? = null
) {

    companion object {
        private const val TAG = "AuthManager"
    }

    val isLoggedIn: Boolean
        get() = supabaseClient.auth.currentSessionOrNull() != null

    val currentUserEmail: String?
        get() = supabaseClient.auth.currentUserOrNull()?.email

    /**
     * Launches the Google Credential Manager flow, obtains an ID token,
     * and signs in to Supabase Auth with it.
     */
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        return try {
            // 1. Build the Google ID token request
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // 2. Launch the Credential Manager UI
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)

            // 3. Extract the Google ID token
            val credential = result.credential
            val idToken = when (credential) {
                is GoogleIdTokenCredential -> {
                    credential.idToken
                }
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        googleCredential.idToken
                    } else {
                        throw IllegalStateException("Unexpected credential type: ${credential.type}")
                    }
                }
                else -> throw IllegalStateException("Unexpected credential type: ${credential.type}")
            }

            Log.d(TAG, "Google ID token obtained, signing in to Supabase...")

            // 4. Sign in to Supabase with the ID token
            supabaseClient.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }

            val session = supabaseClient.auth.currentSessionOrNull()
            Log.d(TAG, "Supabase sign-in successful: ${session?.user?.email}")

            // Link RevenueCat to this Supabase user ID
            session?.user?.id?.let { userId ->
                billingManager?.identifyUser(userId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            supabaseClient.auth.signOut()
            Log.d(TAG, "Signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
            Result.failure(e)
        }
    }

    /**
     * Attempts to restore a previous session from local storage.
     */
    suspend fun restoreSession(): Boolean {
        return try {
            supabaseClient.auth.awaitInitialization()
            val session = supabaseClient.auth.currentSessionOrNull()
            val restored = session != null

            // Link RevenueCat to this Supabase user ID on session restore
            if (restored) {
                session?.user?.id?.let { userId ->
                    billingManager?.identifyUser(userId)
                }
            }

            restored
        } catch (e: Exception) {
            Log.e(TAG, "Session restore failed", e)
            false
        }
    }
}
