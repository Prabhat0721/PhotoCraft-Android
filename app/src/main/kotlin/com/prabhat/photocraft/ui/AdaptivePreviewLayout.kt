package com.prabhat.photocraft.ui

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

class AdaptivePreviewLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var aspectRatio = 1f

    fun setImageAspect(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        aspectRatio = width.toFloat() / height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minimumHeight = dp(if (landscape) 120 else 168)
        val screenCap = (
            resources.displayMetrics.heightPixels * if (landscape) 0.52f else 0.46f
            ).roundToInt()
        val maximumHeight = min(
            dp(if (landscape) 320 else 420),
            screenCap
        ).coerceAtLeast(minimumHeight)
        val targetHeight = (width / aspectRatio).roundToInt()
            .coerceIn(minimumHeight, maximumHeight)

        val resolvedHeight = resolveSize(targetHeight, heightMeasureSpec)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(resolvedHeight, View.MeasureSpec.EXACTLY)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
