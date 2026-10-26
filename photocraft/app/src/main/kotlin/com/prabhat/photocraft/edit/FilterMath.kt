package com.prabhat.photocraft.edit

import kotlin.math.max

object FilterMath {
    fun applyInPlace(pixels: IntArray, settings: FilterSettings) {
        applyInPlace(pixels, settings, pixels.size)
    }

    fun applyInPlace(pixels: IntArray, settings: FilterSettings, pixelCount: Int) {
        require(pixelCount in 0..pixels.size) {
            "Pixel count must fit within the supplied buffer."
        }
        if (settings.isNeutral() || pixelCount == 0) {
            return
        }

        val strength = settings.intensity / 100f
        for (index in 0 until pixelCount) {
            val argb = pixels[index]
            val alpha = argb ushr 24
            if (alpha == 0) {
                continue
            }

            val red = ((argb shr 16) and 0xFF) / 255f
            val green = ((argb shr 8) and 0xFF) / 255f
            val blue = (argb and 0xFF) / 255f
            val target = filterColor(settings.filter, red, green, blue)

            pixels[index] =
                (alpha shl 24) or
                (toByte(red + (target.first - red) * strength) shl 16) or
                (toByte(green + (target.second - green) * strength) shl 8) or
                toByte(blue + (target.third - blue) * strength)
        }
    }

    private fun filterColor(
        filter: Filter,
        red: Float,
        green: Float,
        blue: Float
    ): Triple<Float, Float, Float> = when (filter) {
        Filter.NATURAL -> Triple(red, green, blue)
        Filter.WARM -> Triple(
            red * 1.08f + 0.025f,
            green * 1.01f + 0.010f,
            blue * 0.88f
        )
        Filter.COOL -> Triple(
            red * 0.88f,
            green * 1.01f,
            blue * 1.10f + 0.025f
        )
        Filter.BLACK_WHITE -> {
            val luma = luminance(red, green, blue)
            Triple(luma, luma, luma)
        }
        Filter.VINTAGE -> {
            val luma = luminance(red, green, blue)
            Triple(
                luma * 0.72f + red * 0.28f + 0.045f,
                luma * 0.68f + green * 0.18f + 0.020f,
                luma * 0.58f + blue * 0.10f
            )
        }
        Filter.CINEMATIC -> {
            val luma = luminance(red, green, blue)
            val shadow = (1f - luma).coerceIn(0f, 1f)
            val highlight = luma.coerceIn(0f, 1f)
            Triple(
                (red - 0.5f) * 1.08f + 0.5f + shadow * 0.045f - highlight * 0.025f,
                (green - 0.5f) * 1.05f + 0.5f + shadow * 0.020f,
                (blue - 0.5f) * 1.02f + 0.5f + shadow * 0.095f - highlight * 0.035f
            )
        }
    }

    private fun luminance(red: Float, green: Float, blue: Float): Float =
        0.2126f * red + 0.7152f * green + 0.0722f * blue

    private fun toByte(value: Float): Int =
        (max(0f, value) * 255f + 0.5f).toInt().coerceIn(0, 255)
}
