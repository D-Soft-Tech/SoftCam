package com.example.softcam.utils

import android.view.View

object Extensions {
    fun View.slideUp() {
        apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = height.toFloat()

            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500) // Slide up duration (500ms)
                .setListener(null)
                .start()
        }
    }

    fun View.slideDown() {
        animate()
            .translationY(height.toFloat())
            .alpha(0f)
            .setDuration(500) // Slide down duration (500ms)
            .withEndAction {
                visibility = View.GONE
            }
            .start()
    }
}