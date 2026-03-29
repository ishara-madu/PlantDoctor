package com.pixeleye.plantdoctor.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

private const val TAG = "AdMobUtils"
private const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"
private const val REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917"

fun loadInterstitialAd(context: Context, onAdLoaded: (InterstitialAd?) -> Unit) {
    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(context, INTERSTITIAL_TEST_ID, adRequest, object : InterstitialAdLoadCallback() {
        override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.d(TAG, adError.message)
            onAdLoaded(null)
        }

        override fun onAdLoaded(interstitialAd: InterstitialAd) {
            Log.d(TAG, "Ad was loaded.")
            onAdLoaded(interstitialAd)
        }
    })
}

fun showInterstitialAd(activity: Activity, ad: InterstitialAd?, onAdDismissed: () -> Unit) {
    if (ad != null) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed.")
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.d(TAG, "Ad failed to show.")
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }
        ad.show(activity)
    } else {
        onAdDismissed()
    }
}

fun loadRewardedAd(context: Context, onAdLoaded: (RewardedAd?) -> Unit) {
    val adRequest = AdRequest.Builder().build()
    RewardedAd.load(context, REWARDED_TEST_ID, adRequest, object : RewardedAdLoadCallback() {
        override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.d(TAG, "Rewarded ad failed to load: ${adError.message}")
            onAdLoaded(null)
        }

        override fun onAdLoaded(rewardedAd: RewardedAd) {
            Log.d(TAG, "Rewarded ad was loaded.")
            onAdLoaded(rewardedAd)
        }
    })
}

fun showRewardedAd(
    activity: Activity,
    ad: RewardedAd?,
    onRewardEarned: () -> Unit,
    onAdDismissed: () -> Unit
) {
    if (ad != null) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad was dismissed.")
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.d(TAG, "Rewarded ad failed to show.")
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed fullscreen content.")
            }
        }
        ad.show(activity) { rewardItem ->
            Log.d(TAG, "Reward earned: ${rewardItem.type} amount: ${rewardItem.amount}")
            onRewardEarned()
        }
    } else {
        onAdDismissed()
    }
}
