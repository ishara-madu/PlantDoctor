package com.pixeleye.plantdoctor.data.api

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith

class BillingManager {

    companion object {
        private const val TAG = "BillingManager"
        const val PRO_ENTITLEMENT_ID = "pro"
    }

    fun initialize(context: Context, apiKey: String) {
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(context, apiKey).build()
        )
        Log.d(TAG, "RevenueCat initialized")
    }

    /**
     * Links RevenueCat purchases to the given Supabase user ID.
     * Should be called after successful Supabase authentication.
     */
    fun identifyUser(appUserId: String) {
        Purchases.sharedInstance.logInWith(
            appUserId,
            onSuccess = { customerInfo, created ->
                Log.d(TAG, "RevenueCat user identified: $appUserId (new=$created)")
            },
            onError = { error ->
                Log.e(TAG, "RevenueCat identify failed: ${error.message}")
            }
        )
    }

    fun isProActive(customerInfo: CustomerInfo): Boolean {
        val entitlement: EntitlementInfo? = customerInfo.entitlements[PRO_ENTITLEMENT_ID]
        return entitlement?.isActive == true
    }

    fun fetchOfferings(
        onSuccess: (Map<String, Package>) -> Unit,
        onError: (String) -> Unit
    ) {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.e(TAG, "Failed to fetch offerings: ${error.message}")
                onError(error.message ?: "Failed to load subscription plans.")
            },
            onSuccess = { offerings ->
                val currentOffering = offerings.current
                if (currentOffering == null) {
                    Log.e(TAG, "No current offering found")
                    onError("No subscription plans available.")
                    return@getOfferingsWith
                }

                val packages = mutableMapOf<String, Package>()
                for (pkg in currentOffering.availablePackages) {
                    packages[pkg.product.id] = pkg
                    Log.d(TAG, "Found package: ${pkg.product.id} - ${pkg.product.title}")
                }

                if (packages.isEmpty()) {
                    onError("No subscription plans available.")
                } else {
                    onSuccess(packages)
                }
            }
        )
    }

    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String, Boolean) -> Unit
    ) {
        val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()
        Purchases.sharedInstance.purchaseWith(
            purchaseParams,
            onError = { error, userCancelled ->
                Log.e(TAG, "Purchase failed: ${error.message} (cancelled=$userCancelled)")
                onError(error.message ?: "Purchase failed.", userCancelled)
            },
            onSuccess = { _, customerInfo ->
                Log.d(TAG, "Purchase successful. Pro active: ${isProActive(customerInfo)}")
                onSuccess(customerInfo)
            }
        )
    }

    fun restorePurchases(
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                Log.e(TAG, "Restore failed: ${error.message}")
                onError(error.message ?: "Failed to restore purchases.")
            },
            onSuccess = { customerInfo ->
                Log.d(TAG, "Restore successful. Pro active: ${isProActive(customerInfo)}")
                onSuccess(customerInfo)
            }
        )
    }

    fun checkPremiumStatus(
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                Log.e(TAG, "Premium status check failed: ${error.message}")
                onSuccess(false)
            },
            onSuccess = { customerInfo ->
                val isPro = isProActive(customerInfo)
                Log.d(TAG, "Premium status: $isPro")
                onSuccess(isPro)
            }
        )
    }

    suspend fun logOut() {
        try {
            Purchases.sharedInstance.logOut()
            Log.d(TAG, "RevenueCat user logged out")
        } catch (e: Exception) {
            Log.e(TAG, "RevenueCat logOut failed: ${e.message}")
        }
    }
}
