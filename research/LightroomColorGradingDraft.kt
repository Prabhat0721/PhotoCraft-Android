package com.prabhat.photocraft.research

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Standalone research prototype for Lightroom-style Color Grading.
 *
 * Adobe's public help describes the controls and their visual behavior, but not the
 * proprietary pixel-processing equations. This file is an independent approximation
 * The reviewed feature is now integrated in app/src/main/kotlin/com/prabhat/photocraft/edit/ColorGrading.kt,
 * with its Android wheel controller in app/src/main/kotlin/com/prabhat/photocraft/ui/ColorGradingWheelView.kt.
 *
 * Public behavior reference:
 * https://helpx.adobe.com/lightroom/mobile/adjust-light-and-color/adjust-colors.html
 * https://helpx.adobe.com/lightroom-classic/desktop/process-and-develop-photos/image-tone-color.html
 */
data class GradingWheel(
    /** Hue angle in degrees. */
    val hue: Float = 0f,
    /** Saturation from 0 (wheel center) through 100 (wheel edge). */
    val saturation: Float = 0f,
    /** Tonal luminance adjustment from -100 through +100. */
    val luminance: Float = 0f
) {
    init {
        require(hue.isFinite())
        require(saturation.isFinite() && saturation in 0f..100f)
        require(luminance.isFinite() && luminance in -100f..100f)
    }

    fun isNeutral(): Boolean = saturation == 0f && luminance == 0f
}

data class LightroomStyleColorGrading(
    val shadows: GradingWheel = GradingWheel(),
    val midtones: GradingWheel = GradingWheel(),
    val highlights: GradingWheel = GradingWheel(),
    val global: GradingWheel = GradingWheel(),
    /** 0 through 100. Larger values broaden the tonal transitions and overlap. */
    val blending: Float = 50f,
    /** -100 favors shadows; +100 favors highlights. */
    val balance: Float = 0f
) {
    init {
        require(blending.isFinite() && blending in 0f..100f)
        require(balance.isFinite() && balance in -100f..100f)
    }

    fun isNeutral(): Boolean = shadows.isNeutral() && midtones.isNeutral() &&
        highlights.isNeutral() && global.isNeutral()

    companion object {
        val NEUTRAL = LightroomStyleColorGrading()
    }
}

/** Applies four-wheel tonal grading to packed 8-bit ARGB pixels in place. */
object LightroomStyleColorGradingMath {
    private data class LinearRgb(val r: Float, val g: Float, val b: Float)
    private data class ToneWeights(val shadows: Float, val midtones: Float, val highlights: Float)

    /**
     * @param maskCoverage optional 0..255 coverage per pixel; null grades the whole frame.
     * The mask only scales the grading strength; it does not modify the mask or alpha.
     */
    fun applyInPlace(
        pixels: IntArray,
        settings: LightroomStyleColorGrading,
        maskCoverage: ByteArray? = null,
        pixelCount: Int = pixels.size
    ) {
        require(pixelCount in 0..pixels.size)
        require(maskCoverage == null || maskCoverage.size >= pixelCount) {
            "Mask coverage must contain at least one value per processed pixel."
        }
        if (pixelCount == 0 || settings.isNeutral()) return

        for (index in 0 until pixelCount) {
            val argb = pixels[index]
            val alpha = (argb ushr 24) and 0xFF
            if (alpha == 0) continue

            val maskWeight = maskCoverage?.let { (it[index].toInt() and 0xFF) / 255f } ?: 1f
            if (maskWeight <= 0f) continue

            val original = LinearRgb(
                srgbToLinear(((argb ushr 16) and 0xFF) / 255f),
                srgbToLinear(((argb ushr 8) and 0xFF) / 255f),
                srgbToLinear((argb and 0xFF) / 255f)
            )
            val originalLuma = luma(original)
            val tones = tonalWeights(originalLuma, settings.blending, settings.balance)

            var graded = applyWheel(original, settings.global, maskWeight)
            graded = applyWheel(graded, settings.shadows, tones.shadows * maskWeight)
            graded = applyWheel(graded, settings.midtones, tones.midtones * maskWeight)
            graded = applyWheel(graded, settings.highlights, tones.highlights * maskWeight)

            pixels[index] = (alpha shl 24) or
                (linearToSrgb(graded.r) shl 16) or
                (linearToSrgb(graded.g) shl 8) or
                linearToSrgb(graded.b)
        }
    }

    private fun applyWheel(input: LinearRgb, wheel: GradingWheel, weight: Float): LinearRgb {
        if (weight <= 0f || wheel.isNeutral()) return input
        val strength = weight.coerceIn(0f, 1f)
        var r = input.r
        var g = input.g
        var b = input.b

        if (wheel.saturation > 0f) {
            val tint = hslToLinearRgb(wheel.hue, 1f, 0.5f)
            val tintLuma = luma(tint)
            // Add a zero-luma chroma vector so the wheel tint changes color while
            // retaining the source pixel's luminance as much as gamut allows.
            val tintStrength = wheel.saturation / 100f * strength * MAX_CHROMA_MIX
            r += (tint.r - tintLuma) * tintStrength
            g += (tint.g - tintLuma) * tintStrength
            b += (tint.b - tintLuma) * tintStrength
        }

        if (wheel.luminance != 0f) {
            // A bounded exposure-like multiplier gives the wheel's Luminance control
            // a smooth response without clipping the tonal mask before final encoding.
            val ev = wheel.luminance / 100f * MAX_LUMINANCE_EV * strength
            val multiplier = 2f.pow(ev)
            r *= multiplier
            g *= multiplier
            b *= multiplier
        }

        return LinearRgb(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    private fun tonalWeights(luma: Float, blending: Float, balance: Float): ToneWeights {
        // Positive balance moves the crossover toward darker pixels, giving more
        // tonal weight to highlights; negative balance does the reverse.
        val center = (0.5f - balance / 100f * MAX_BALANCE_SHIFT).coerceIn(0.2f, 0.8f)
        val halfWidth = MIN_TRANSITION + blending / 100f * BLENDING_TRANSITION_RANGE
        val lowEdge = (center - halfWidth).coerceIn(0f, 1f)
        val highEdge = (center + halfWidth).coerceIn(0f, 1f)
        val safeHigh = maxOf(highEdge, lowEdge + 0.0001f)

        val shadow = 1f - smoothstep(lowEdge, safeHigh, luma)
        val highlight = smoothstep(lowEdge, safeHigh, luma)
        val midWidth = MIN_MIDTONE_WIDTH + blending / 100f * MIDTONE_WIDTH_RANGE
        val distance = (luma - center) / midWidth
        val midtone = exp(-0.5f * distance * distance)

        // Normalize the overlap so simultaneous wheel adjustments do not become
        // unexpectedly stronger simply because blending is high.
        val total = shadow + midtone + highlight
        if (total <= 0f) return ToneWeights(0f, 1f, 0f)
        return ToneWeights(shadow / total, midtone / total, highlight / total)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun hslToLinearRgb(hue: Float, saturation: Float, lightness: Float): LinearRgb {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val rgb = when {
            h < 60f -> floatArrayOf(c, x, 0f)
            h < 120f -> floatArrayOf(x, c, 0f)
            h < 180f -> floatArrayOf(0f, c, x)
            h < 240f -> floatArrayOf(0f, x, c)
            h < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return LinearRgb(
            srgbToLinear(rgb[0] + m),
            srgbToLinear(rgb[1] + m),
            srgbToLinear(rgb[2] + m)
        )
    }

    private fun srgbToLinear(value: Float): Float {
        val channel = value.coerceIn(0f, 1f)
        return if (channel <= 0.04045f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val channel = value.coerceIn(0f, 1f)
        val srgb = if (channel <= 0.0031308f) channel * 12.92f
        else 1.055f * channel.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun luma(rgb: LinearRgb): Float =
        0.2126f * rgb.r + 0.7152f * rgb.g + 0.0722f * rgb.b

    private const val MAX_CHROMA_MIX = 0.40f
    private const val MAX_LUMINANCE_EV = 0.75f
    private const val MAX_BALANCE_SHIFT = 0.18f
    private const val MIN_TRANSITION = 0.10f
    private const val BLENDING_TRANSITION_RANGE = 0.28f
    private const val MIN_MIDTONE_WIDTH = 0.10f
    private const val MIDTONE_WIDTH_RANGE = 0.24f
}
