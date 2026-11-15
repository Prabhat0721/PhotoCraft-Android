package com.prabhat.photocraft.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton

class AccessibleImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.imageButtonStyle
) : ImageButton(context, attrs, defStyleAttr) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
