package com.prabhat.photocraft.export

import com.prabhat.photocraft.edit.CropSelection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class ExportPlan(
    val crop: PixelCrop,
    val output: OutputDimensions
)

object ExportPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        crop: CropSelection,
        size: ExportSize
    ): ExportPlan {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source dimensions must be positive."
        }

        val pixelCrop = pixelCrop(sourceWidth, sourceHeight, crop)
        return ExportPlan(
            crop = pixelCrop,
            output = outputDimensions(pixelCrop.width, pixelCrop.height, size)
        )
    }

    fun pixelCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        crop: CropSelection
    ): PixelCrop {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source dimensions must be positive."
        }

        val left = floor(crop.left * sourceWidth).toInt()
            .coerceIn(0, sourceWidth - 1)
        val top = floor(crop.top * sourceHeight).toInt()
            .coerceIn(0, sourceHeight - 1)
        val right = ceil(crop.right * sourceWidth).toInt()
            .coerceIn(left + 1, sourceWidth)
        val bottom = ceil(crop.bottom * sourceHeight).toInt()
            .coerceIn(top + 1, sourceHeight)
        return PixelCrop(
            left = left,
            top = top,
            width = right - left,
            height = bottom - top
        )
    }

    private fun outputDimensions(
        width: Int,
        height: Int,
        size: ExportSize
    ): OutputDimensions {
        size.percent?.let { percent ->
            return OutputDimensions(
                width = scaledByPercent(width, percent),
                height = scaledByPercent(height, percent)
            )
        }
        size.longEdgePx?.let { longEdge ->
            val longestEdge = max(width, height)
            if (longestEdge <= longEdge) {
                return OutputDimensions(width, height)
            }
            val scale = longEdge.toDouble() / longestEdge
            return OutputDimensions(
                width = (width * scale).roundToInt().coerceAtLeast(1),
                height = (height * scale).roundToInt().coerceAtLeast(1)
            )
        }
        return OutputDimensions(width, height)
    }

    private fun scaledByPercent(value: Int, percent: Int): Int =
        ((value.toLong() * percent + 50L) / 100L)
            .toInt()
            .coerceAtLeast(1)
}
