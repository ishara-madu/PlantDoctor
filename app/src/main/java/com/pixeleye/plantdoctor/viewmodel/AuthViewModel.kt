package com.pixeleye.plantdoctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.data.api.AuthManager
import com.pixeleye.plantdoctor.data.api.BillingManager
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    application: Application,
    private val billingManager: BillingManager? = null
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val authManager = AuthManager(
        supabaseClient = SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ),
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        billingManager = billingManager
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _snackbarEvent = MutableStateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?>(null)
    val snackbarEvent: StateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?> = _snackbarEvent.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val restored = authManager.restoreSession()
            _authState.value = if (restored) {
                Log.d(TAG, "Session restored: ${authManager.currentUserEmail}")
                val userId = authManager.currentUserEmail ?: "anonymous"
                com.onesignal.OneSignal.login(userId)
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(onPostAuth: suspend () -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val context = getApplication<Application>().applicationContext
            val result = authManager.signInWithGoogle(context)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Sign-in successful: ${authManager.currentUserEmail}")
                    
                    val userId = authManager.currentUserEmail ?: "anonymous"
                    com.onesignal.OneSignal.login(userId)
                    
                    // 1. Await User Data & Premium Status sequentially
                    // Execution pauses here until the network request returns
                    onPostAuth()
                    
                    // 2. ONLY NOW update the state to trigger navigation
                    // This ensures HomeScreen observers see the correct 'isPremium' value
                    _authState.value = AuthState.Authenticated
                },
                onFailure = { e ->
                    Log.e(TAG, "Sign-in failed", e)
                    showSnackbar(e.message ?: "Sign-in failed. Please try again.", com.pixeleye.plantdoctor.ui.components.SnackbarType.ERROR)
                    _authState.value = AuthState.Error(
                        e.message ?: "Sign-in failed. Please try again."
                    )
                }
            )
        }
    }

    fun signOut(): kotlinx.coroutines.Deferred<Unit> {
        return viewModelScope.async {
            billingManager?.logOut()
            authManager.signOut()
            com.onesignal.OneSignal.logout()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        _authState.value = AuthState.Unauthenticated
    }

    fun consumeSnackbarEvent() {
        _snackbarEvent.value = null
    }

    fun showSnackbar(message: String, type: com.pixeleye.plantdoctor.ui.components.SnackbarType) {
        _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(message, type)
    }

    class Factory(
        private val application: Application,
        private val billingManager: BillingManager
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(application, billingManager) as T
        }
    }
}
