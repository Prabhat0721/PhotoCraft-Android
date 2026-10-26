package com.prabhat.photocraft.edit

import kotlin.math.roundToInt

object CropGeometry {
    const val MIN_FRACTION = 0.05f
    const val PROGRESS_MAX = 1000

    fun centered(
        ratio: CropRatio,
        sourceWidth: Int,
        sourceHeight: Int
    ): CropSelection {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source dimensions must be positive."
        }
        val fixedRatio = ratio.widthToHeight ?: return CropSelection.FULL
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        val cropWidth: Float
        val cropHeight: Float
        if (sourceAspect > fixedRatio) {
            cropHeight = 1f
            cropWidth = fixedRatio / sourceAspect
        } else {
            cropWidth = 1f
            cropHeight = sourceAspect / fixedRatio
        }
        val left = (1f - cropWidth) / 2f
        val top = (1f - cropHeight) / 2f
        return CropSelection(left, top, left + cropWidth, top + cropHeight)
    }

    fun fromControls(
        ratio: CropRatio,
        sourceWidth: Int,
        sourceHeight: Int,
        widthFraction: Float,
        heightFraction: Float,
        centerX: Float,
        centerY: Float
    ): CropSelection {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source dimensions must be positive."
        }
        val fixedRatio = ratio.widthToHeight
        if (fixedRatio == null) {
            return place(
                width = widthFraction.coerceIn(MIN_FRACTION, 1f),
                height = heightFraction.coerceIn(MIN_FRACTION, 1f),
                centerX = centerX,
                centerY = centerY
            )
        }

        val maximum = centered(ratio, sourceWidth, sourceHeight)
        val scale = widthFraction.coerceIn(MIN_FRACTION, 1f)
        return place(
            width = maximum.width * scale,
            height = maximum.height * scale,
            centerX = centerX,
            centerY = centerY
        )
    }

    fun centerX(crop: CropSelection): Float = (crop.left + crop.right) / 2f

    fun centerY(crop: CropSelection): Float = (crop.top + crop.bottom) / 2f

    fun sizeProgress(fraction: Float): Int {
        val normalized = ((fraction.coerceIn(MIN_FRACTION, 1f) - MIN_FRACTION) /
            (1f - MIN_FRACTION)) * PROGRESS_MAX
        return normalized.roundToInt().coerceIn(0, PROGRESS_MAX)
    }

    fun fractionFromProgress(progress: Int): Float {
        val normalized = progress.coerceIn(0, PROGRESS_MAX) / PROGRESS_MAX.toFloat()
        return MIN_FRACTION + normalized * (1f - MIN_FRACTION)
    }

    fun positionProgress(center: Float, span: Float): Int {
        val range = 1f - span.coerceIn(0f, 1f)
        if (range <= 0.000001f) {
            return PROGRESS_MAX / 2
        }
        val minimumCenter = span / 2f
        val normalized = (center - minimumCenter) / range
        return (normalized * PROGRESS_MAX).roundToInt().coerceIn(0, PROGRESS_MAX)
    }

    fun centerFromProgress(progress: Int, span: Float): Float {
        val boundedSpan = span.coerceIn(0f, 1f)
        if (boundedSpan >= 1f - 0.000001f) {
            return 0.5f
        }
        val normalized = progress.coerceIn(0, PROGRESS_MAX) / PROGRESS_MAX.toFloat()
        return boundedSpan / 2f + normalized * (1f - boundedSpan)
    }

    private fun place(
        width: Float,
        height: Float,
        centerX: Float,
        centerY: Float
    ): CropSelection {
        val boundedWidth = width.coerceIn(0.0001f, 1f)
        val boundedHeight = height.coerceIn(0.0001f, 1f)
        val boundedCenterX = centerX.coerceIn(
            boundedWidth / 2f,
            1f - boundedWidth / 2f
        )
        val boundedCenterY = centerY.coerceIn(
            boundedHeight / 2f,
            1f - boundedHeight / 2f
        )
        val left = (boundedCenterX - boundedWidth / 2f)
            .coerceIn(0f, 1f - boundedWidth)
        val top = (boundedCenterY - boundedHeight / 2f)
            .coerceIn(0f, 1f - boundedHeight)
        val right = (left + boundedWidth).coerceAtMost(1f)
        val bottom = (top + boundedHeight).coerceAtMost(1f)
        return CropSelection(left, top, right, bottom)
    }
}
