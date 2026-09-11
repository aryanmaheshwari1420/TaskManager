package com.aryanmaheshwari.taskmanager.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Global utility for unified interaction animations in TaskManager.
 * Standardizes click, card, icon button, and checkbox animations using native,
 * GPU-accelerated ViewPropertyAnimators.
 */
object ViewInteractionAnimator {

    /**
     * Applies standard click/scale feedback (1.0 -> 0.96 -> 1.0) on touch.
     */
    fun applyClickFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(80)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            false // Crucial: return false so click listeners can still fire!
        }
    }

    /**
     * Applies card feedback (subtle scale 0.98 and slight translationZ/elevation decrease).
     */
    fun applyCardFeedback(view: View) {
        val originalTranslationZ = view.translationZ
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .translationZ(originalTranslationZ - 4f)
                        .setDuration(80)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(originalTranslationZ)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            false
        }
    }

    /**
     * Applies icon button feedback (subtle scale 0.92, very fast).
     */
    fun applyIconButtonFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(60)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            false
        }
    }

    /**
     * Custom satisfying checkbox bounce animation (scale down -> scale overshoot -> normal).
     */
    fun animateCheckboxToggle(checkBox: View, onAnimationDone: () -> Unit) {
        checkBox.animate().cancel()
        
        val scaleDownX = ObjectAnimator.ofFloat(checkBox, "scaleX", 0.85f)
        val scaleDownY = ObjectAnimator.ofFloat(checkBox, "scaleY", 0.85f)
        val scaleDown = AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY)
            duration = 100
            interpolator = DecelerateInterpolator()
        }

        val scaleUpX = ObjectAnimator.ofFloat(checkBox, "scaleX", 1.15f)
        val scaleUpY = ObjectAnimator.ofFloat(checkBox, "scaleY", 1.15f)
        val scaleUp = AnimatorSet().apply {
            playTogether(scaleUpX, scaleUpY)
            duration = 85
            interpolator = DecelerateInterpolator()
        }

        val normalX = ObjectAnimator.ofFloat(checkBox, "scaleX", 1.0f)
        val normalY = ObjectAnimator.ofFloat(checkBox, "scaleY", 1.0f)
        val normal = AnimatorSet().apply {
            playTogether(normalX, normalY)
            duration = 65
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, normal)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationDone()
                }
            })
            start()
        }
    }
}

// -----------------------------------------------------------------------------
// View extension functions for clean integration
// -----------------------------------------------------------------------------

fun View.setClickFeedback() {
    ViewInteractionAnimator.applyClickFeedback(this)
}

fun View.setCardFeedback() {
    ViewInteractionAnimator.applyCardFeedback(this)
}

fun View.setIconButtonFeedback() {
    ViewInteractionAnimator.applyIconButtonFeedback(this)
}
