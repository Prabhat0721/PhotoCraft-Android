package com.prabhat.photocraft.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object AdjustmentMath {
    fun applyInPlace(pixels: IntArray, adjustments: Adjustments) {
        applyInPlace(pixels, adjustments, pixels.size)
    }

    fun applyInPlace(pixels: IntArray, adjustments: Adjustments, pixelCount: Int) {
        require(pixelCount in 0..pixels.size) {
            "Pixel count must fit within the supplied buffer."
        }
        if (adjustments.isNeutral() || pixelCount == 0) {
            return
        }

        val exposureGain = 2.0.pow(adjustments.exposure / 50.0).toFloat()
        val temperature = adjustments.temperature / 100f
        val tint = adjustments.tint / 100f
        val redGain = exposureGain * (1f + 0.28f * temperature) * (1f + 0.10f * tint)
        val greenGain = exposureGain * (1f - 0.16f * tint)
        val blueGain = exposureGain * (1f - 0.28f * temperature) * (1f + 0.10f * tint)
        val brightness = adjustments.brightness / 100f * 0.18f
        val contrast = 1f + adjustments.contrast / 100f * 0.65f
        val blacks = adjustments.blacks / 100f * 0.22f
        val shadows = adjustments.shadows / 100f * 0.18f
        val highlights = adjustments.highlights / 100f * 0.18f
        val whites = adjustments.whites / 100f * 0.16f
        val saturation = 1f + adjustments.saturation / 100f
        val vibrance = adjustments.vibrance / 100f * 0.90f

        for (index in 0 until pixelCount) {
            val argb = pixels[index]
            val alpha = argb ushr 24
            if (alpha == 0) {
                continue
            }

            var red = ((argb shr 16) and 0xFF) / 255f
            var green = ((argb shr 8) and 0xFF) / 255f
            var blue = (argb and 0xFF) / 255f

            red = red * redGain + brightness
            green = green * greenGain + brightness
            blue = blue * blueGain + brightness

            red = (red - 0.5f) * contrast + 0.5f
            green = (green - 0.5f) * contrast + 0.5f
            blue = (blue - 0.5f) * contrast + 0.5f

            var luma = luminance(red, green, blue)
            val toneShift =
                blacks * (1f - smoothStep(0f, 0.30f, luma)) +
                    shadows * (1f - smoothStep(0.05f, 0.55f, luma)) +
                    highlights * smoothStep(0.38f, 0.82f, luma) *
                    (1f - 0.55f * smoothStep(0.88f, 1f, luma)) +
                    whites * smoothStep(0.72f, 1f, luma)

            red += toneShift
            green += toneShift
            blue += toneShift

            luma = luminance(red, green, blue)
            red = luma + (red - luma) * saturation
            green = luma + (green - luma) * saturation
            blue = luma + (blue - luma) * saturation

            if (vibrance != 0f) {
                val maxChannel = max(red, max(green, blue))
                val minChannel = min(red, min(green, blue))
                val chroma = if (maxChannel > 0f) {
                    ((maxChannel - minChannel) / maxChannel).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val vibranceFactor = 1f + vibrance * (1f - chroma)
                red = luma + (red - luma) * vibranceFactor
                green = luma + (green - luma) * vibranceFactor
                blue = luma + (blue - luma) * vibranceFactor
            }

            pixels[index] =
                (alpha shl 24) or
                (toByte(red) shl 16) or
                (toByte(green) shl 8) or
                toByte(blue)
        }
    }

    private fun luminance(red: Float, green: Float, blue: Float): Float =
        0.2126f * red + 0.7152f * green + 0.0722f * blue

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) {
            return if (value < edge0) 0f else 1f
        }
        val normalized = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return normalized * normalized * (3f - 2f * normalized)
    }

    private fun toByte(value: Float): Int =
        (value * 255f + 0.5f).toInt().coerceIn(0, 255)
}
