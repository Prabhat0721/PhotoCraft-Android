package com.prabhat.photocraft.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent

/** Seek bar that resets to its configured default on a double tap. */
class ResettableSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AccessibleSeekBar(context, attrs, defStyleAttr) {
    private var defaultProgress = 0
    private var consumeResetTap = false
    var onResetToDefault: (() -> Unit)? = null

    private val resetDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (!isEnabled) return false
                consumeResetTap = true
                if (progress != defaultProgress) {
                    val reset = onResetToDefault
                    if (reset != null) reset() else resetProgressToDefault()
                }
                return true
            }
        }
    )

    init {
        defaultProgress = progress
    }

    fun setDefaultProgress(value: Int) {
        defaultProgress = value.coerceIn(0, max)
    }

    fun resetProgressToDefault() {
        setProgress(defaultProgress, true)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        resetDetector.onTouchEvent(event)
        if (consumeResetTap) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                consumeResetTap = false
                performClick()
            } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumeResetTap = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
