package com.prabhat.photocraft.export

import com.prabhat.photocraft.edit.CropSelection

data class ExportDecodePlan(
    val decodeWidth: Int,
    val decodeHeight: Int,
    val output: OutputDimensions,
    val crop: PixelCrop,
    val estimatedBytes: Long,
    val reducedDecode: Boolean
)

object ExportMemoryPolicy {
    const val BYTES_PER_PIXEL = 4L

    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        crop: CropSelection,
        size: ExportSize
    ): ExportDecodePlan? {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null
        }

        val fullPlan = try {
            ExportPlanner.plan(sourceWidth, sourceHeight, crop, size)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val cropPixels = fullPlan.crop
        val widthScaleIsLarger =
            fullPlan.output.width.toLong() * cropPixels.height >=
                fullPlan.output.height.toLong() * cropPixels.width
        val scaleNumerator = if (widthScaleIsLarger) {
            fullPlan.output.width.toLong()
        } else {
            fullPlan.output.height.toLong()
        }
        val scaleDenominator = if (widthScaleIsLarger) {
            cropPixels.width.toLong()
        } else {
            cropPixels.height.toLong()
        }
        val decodeWidth = scaledDimension(sourceWidth, scaleNumerator, scaleDenominator)
        val decodeHeight = scaledDimension(sourceHeight, scaleNumerator, scaleDenominator)
        val decodePixels = decodeWidth.toLong() * decodeHeight.toLong()
        val outputPixels = fullPlan.output.width.toLong() * fullPlan.output.height.toLong()
        val estimatedBytes = saturatingAdd(
            saturatingMultiply(decodePixels, BYTES_PER_PIXEL),
            saturatingMultiply(outputPixels, BYTES_PER_PIXEL)
        )

        return ExportDecodePlan(
            decodeWidth = decodeWidth,
            decodeHeight = decodeHeight,
            output = fullPlan.output,
            crop = cropPixels,
            estimatedBytes = estimatedBytes,
            reducedDecode = decodeWidth < sourceWidth || decodeHeight < sourceHeight
        )
    }

    private fun scaledDimension(
        source: Int,
        numerator: Long,
        denominator: Long
    ): Int {
        val scaled = (source.toLong() * numerator + denominator - 1) / denominator
        return scaled.coerceAtMost(source.toLong()).toInt().coerceAtLeast(1)
    }

    private fun saturatingMultiply(value: Long, factor: Long): Long {
        if (value == 0L || factor == 0L) {
            return 0L
        }
        return if (value > Long.MAX_VALUE / factor) Long.MAX_VALUE else value * factor
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
