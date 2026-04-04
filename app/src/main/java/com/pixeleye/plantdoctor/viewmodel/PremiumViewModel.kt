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

    private val _monthlyPrice = MutableStateFlow("")
    val monthlyPrice: StateFlow<String> = _monthlyPrice.asStateFlow()

    private val _yearlyPrice = MutableStateFlow("")
    val yearlyPrice: StateFlow<String> = _yearlyPrice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _monthlyPackage = MutableStateFlow<Package?>(null)
    val monthlyPackage: StateFlow<Package?> = _monthlyPackage.asStateFlow()

    private val _yearlyPackage = MutableStateFlow<Package?>(null)
    val yearlyPackage: StateFlow<Package?> = _yearlyPackage.asStateFlow()

    private var cachedPackages: Map<String, Package>? = null

    init {
        viewModelScope.launch {
            try {
                syncPremiumStatus()
                fetchAndStoreOfferings()
            } catch (e: Exception) {
                Log.e(TAG, "Initial premium sync failed", e)
            }
        }
    }

    private suspend fun fetchAndStoreOfferings() {
        val offerings = billingManager.getOfferingsSuspended()
        val current = offerings?.current
        if (current != null) {
            _monthlyPrice.value = current.monthly?.product?.price?.formatted ?: ""
            _yearlyPrice.value = current.annual?.product?.price?.formatted ?: ""
            
            _monthlyPackage.value = current.monthly
            _yearlyPackage.value = current.annual
            val packages = mutableMapOf<String, Package>()
            for (pkg in current.availablePackages) {
                packages[pkg.product.id] = pkg
            }
            cachedPackages = packages
            Log.d(TAG, "Prices updated: Monthly=${_monthlyPrice.value}, Yearly=${_yearlyPrice.value}")
        }
    }

    /**
     * Fetches the latest customer info from RevenueCat and updates the premium state.
     */
    suspend fun syncPremiumStatus() {
        Log.d(TAG, "Syncing premium status from RevenueCat...")
        val customerInfo = billingManager.getCustomerInfoSuspended()
        _isPremium.value = if (customerInfo != null) {
            billingManager.isProActive(customerInfo)
        } else {
            false
        }
        Log.d(TAG, "Sync complete. isPremium: ${_isPremium.value}")
    }

    /**
     * Managed by RevenueCat. After purchase/restore, the state is updated automatically.
     */
    fun upgradeToPremium() {
        _isPremium.value = true
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
