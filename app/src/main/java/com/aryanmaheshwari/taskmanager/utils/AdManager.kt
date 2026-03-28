package com.aryanmaheshwari.taskmanager.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aryanmaheshwari.taskmanager.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdManager handles loading and showing of Interstitial and Rewarded Ads.
 * It automatically switches between Test and Production IDs based on BuildConfig.DEBUG.
 */
object AdManager {
    private const val TAG = "AdManager"

    // TEST IDs (Standard Google AdMob Test IDs)
    private const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917"

    // PRODUCTION IDs (Placeholders - Replace with real IDs from AdMob Console)
    private const val INTERSTITIAL_PROD_ID = "ca-app-pub-7573894623963915/8364993255"
    private const val REWARDED_PROD_ID = "ca-app-pub-7573894623963915/2530114589"

    private var mInterstitialAd: InterstitialAd? = null
    private var mRewardedAd: RewardedAd? = null

    private fun getInterstitialId(): String = if (BuildConfig.DEBUG) INTERSTITIAL_TEST_ID else INTERSTITIAL_PROD_ID
    private fun getRewardedId(): String = if (BuildConfig.DEBUG) REWARDED_TEST_ID else REWARDED_PROD_ID

    fun loadInterstitialAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, getInterstitialId(), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Interstitial fail: ${adError.message}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Interstitial loaded")
                mInterstitialAd = interstitialAd
            }
        })
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial dismissed")
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial show fail: ${adError.message}")
                    mInterstitialAd = null
                    onAdDismissed()
                }
            }
            mInterstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial not ready")
            onAdDismissed()
        }
    }

    fun loadRewardedAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, getRewardedId(), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "Rewarded ad fail: ${loadAdError.message}")
                mRewardedAd = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Rewarded ad loaded")
                mRewardedAd = rewardedAd
            }
        })
    }

    fun showRewardedAd(activity: Activity, onUserEarnedReward: () -> Unit, onAdDismissed: () -> Unit) {
        if (mRewardedAd != null) {
            mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded dismissed")
                    mRewardedAd = null
                    loadRewardedAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Rewarded show fail: ${adError.message}")
                    mRewardedAd = null
                    onAdDismissed()
                }
            }
            mRewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward()
            }
        } else {
            Log.d(TAG, "Rewarded not ready")
            onAdDismissed()
            loadRewardedAd(activity) // Ensure it loads for next time
        }
    }
}
