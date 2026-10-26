package com.prabhat.photocraft.edit

import kotlin.math.abs

data class CropSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
        ) { "Crop bounds must be finite." }
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) {
            "Crop bounds must stay within the normalized image."
        }
        require(right > left && bottom > top) {
            "Crop bounds must have positive width and height."
        }
    }

    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun isFull(): Boolean =
        left <= EPSILON &&
            top <= EPSILON &&
            right >= 1f - EPSILON &&
            bottom >= 1f - EPSILON

    companion object {
        private const val EPSILON = 0.000001f
        private const val BASIS_POINT_MAX = 10000

        val FULL = CropSelection(0f, 0f, 1f, 1f)

        fun fromBasisPoints(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ): CropSelection {
            require(
                left in 0..BASIS_POINT_MAX &&
                    top in 0..BASIS_POINT_MAX &&
                    right in 0..BASIS_POINT_MAX &&
                    bottom in 0..BASIS_POINT_MAX
            ) { "Crop basis points must stay within 0..10000." }
            return CropSelection(
                left = left / BASIS_POINT_MAX.toFloat(),
                top = top / BASIS_POINT_MAX.toFloat(),
                right = right / BASIS_POINT_MAX.toFloat(),
                bottom = bottom / BASIS_POINT_MAX.toFloat()
            )
        }
    }
}

enum class CropRatio(val widthToHeight: Float?) {
    FREE(null),
    SQUARE(1f),
    FOUR_FIVE(4f / 5f),
    NINE_SIXTEEN(9f / 16f),
    SIXTEEN_NINE(16f / 9f);

    val isFixed: Boolean
        get() = widthToHeight != null
}

fun CropRatio.matches(sourceWidth: Int, sourceHeight: Int): Boolean {
    val ratio = widthToHeight ?: return true
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return false
    }
    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    return abs(sourceAspect - ratio) <= 0.0001f
}
