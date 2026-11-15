package com.prabhat.photocraft.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar

class AccessibleSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : SeekBar(context, attrs, defStyleAttr) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
