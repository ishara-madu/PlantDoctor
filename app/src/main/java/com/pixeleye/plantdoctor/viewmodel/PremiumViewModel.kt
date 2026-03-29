package com.pixeleye.plantdoctor.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.data.api.BillingManager
import com.pixeleye.plantdoctor.data.api.UserQuotaRepository
import com.revenuecat.purchases.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class PremiumViewModel(
    private val billingManager: BillingManager,
    private val userQuotaRepository: UserQuotaRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "PremiumVM"
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedPackages: Map<String, Package>? = null

    init {
        viewModelScope.launch {
            try {
                syncPremiumStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Initial premium sync failed", e)
            }
        }
        prefetchOfferings()
    }

    /**
     * Suspend function that awaits Supabase premium check and RevenueCat fallback.
     * Call from LaunchedEffect — blocks until the sync completes.
     */
    suspend fun syncPremiumStatus() {
        val repo = userQuotaRepository
        if (repo == null) {
            // No repo — check RevenueCat directly and await
            val isPro = suspendCancellableCoroutine<Boolean> { cont ->
                    billingManager.checkPremiumStatus(
                        onSuccess = { cont.resumeWith(Result.success(it)) },
                        onError = {
                            Log.e(TAG, "RevenueCat premium check failed: $it")
                            cont.resumeWith(Result.success(false))
                        }
                    )
            }
            _isPremium.value = isPro
            return
        }

        try {
            // Step 1: Supabase as source of truth
            val supabasePremium = repo.isPremium()
            _isPremium.value = supabasePremium
            Log.d(TAG, "Supabase premium status: $supabasePremium")

            // Step 2: RevenueCat fallback if Supabase says false
            if (!supabasePremium) {
                val isProFromRc = suspendCancellableCoroutine<Boolean> { cont ->
                    billingManager.getCustomerInfo(
                        onSuccess = { customerInfo ->
                            cont.resumeWith(Result.success(billingManager.isProActive(customerInfo)))
                        },
                        onError = {
                            Log.e(TAG, "RevenueCat fallback failed: $it")
                            cont.resumeWith(Result.success(false))
                        }
                    )
                }
                if (isProFromRc) {
                    Log.d(TAG, "RevenueCat says PRO but Supabase says false. Backfilling.")
                    _isPremium.value = true
                    try {
                        repo.upgradeToPremium()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to backfill Supabase from RevenueCat", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase premium check failed, falling back to RevenueCat", e)
            val isPro = suspendCancellableCoroutine<Boolean> { cont ->
                billingManager.checkPremiumStatus(
                    onSuccess = { cont.resumeWith(Result.success(it)) },
                    onError = {
                        Log.e(TAG, "RevenueCat fallback also failed: $it")
                        cont.resumeWith(Result.success(false))
                    }
                )
            }
            _isPremium.value = isPro
        }
    }

    fun upgradeToPremium() {
        userQuotaRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.upgradeToPremium()
                    _isPremium.value = true
                    Log.d(TAG, "Successfully upgraded to premium in Supabase")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upgrade to premium in Supabase", e)
                    throw e
                }
            }
        } ?: run {
            _isPremium.value = true
        }
    }

    private fun prefetchOfferings() {
        billingManager.fetchOfferings(
            onSuccess = { packages ->
                cachedPackages = packages
                Log.d(TAG, "Offerings prefetched: ${packages.keys}")
            },
            onError = { error ->
                Log.e(TAG, "Failed to prefetch offerings: $error")
            }
        )
    }

    fun startPurchase(
        activity: Activity,
        billingManager: BillingManager,
        planId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true

        val packages = cachedPackages
        if (packages == null) {
            billingManager.fetchOfferings(
                onSuccess = { fetchedPackages ->
                    cachedPackages = fetchedPackages
                    executePurchase(activity, billingManager, fetchedPackages, planId, onSuccess, onError)
                },
                onError = { error ->
                    _isLoading.value = false
                    onError(error)
                }
            )
        } else {
            executePurchase(activity, billingManager, packages, planId, onSuccess, onError)
        }
    }

    private fun executePurchase(
        activity: Activity,
        billingManager: BillingManager,
        packages: Map<String, Package>,
        planId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val targetPackage = packages.entries.firstOrNull { (key, _) ->
            key.contains(planId, ignoreCase = true)
        }?.value

        if (targetPackage == null) {
            _isLoading.value = false
            onError("Selected plan not available.")
            return
        }

        billingManager.purchase(
            activity = activity,
            packageToPurchase = targetPackage,
            onSuccess = { customerInfo ->
                _isLoading.value = false
                _isPremium.value = billingManager.isProActive(customerInfo)
                onSuccess()
            },
            onError = { message, userCancelled ->
                _isLoading.value = false
                if (!userCancelled) {
                    onError(message)
                }
            }
        )
    }

    fun restorePurchases(
        billingManager: BillingManager,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true
        billingManager.restorePurchases(
            onSuccess = { customerInfo ->
                _isLoading.value = false
                val isPro = billingManager.isProActive(customerInfo)
                if (isPro) {
                    _isPremium.value = true
                }
                onSuccess(isPro)
            },
            onError = { error ->
                _isLoading.value = false
                onError(error)
            }
        )
    }

    fun setPremium(isPremium: Boolean) {
        _isPremium.value = isPremium
    }

    class Factory(
        private val billingManager: BillingManager,
        private val userQuotaRepository: UserQuotaRepository? = null
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PremiumViewModel(billingManager, userQuotaRepository) as T
        }
    }
}
