package com.prabhat.photocraft.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class HslColor(val centerHue: Float) {
    RED(0f),
    ORANGE(30f),
    YELLOW(60f),
    GREEN(120f),
    CYAN(180f),
    BLUE(240f),
    PURPLE(270f),
    MAGENTA(300f)
}

data class HslAdjustment(
    val hue: Int = 0,
    val saturation: Int = 0,
    val luminance: Int = 0
) {
    init {
        require(hue in -180..180)
        require(saturation in -100..100)
        require(luminance in -100..100)
    }

    fun isNeutral() = hue == 0 && saturation == 0 && luminance == 0
}

data class HslSettings(
    val red: HslAdjustment = HslAdjustment(),
    val orange: HslAdjustment = HslAdjustment(),
    val yellow: HslAdjustment = HslAdjustment(),
    val green: HslAdjustment = HslAdjustment(),
    val cyan: HslAdjustment = HslAdjustment(),
    val blue: HslAdjustment = HslAdjustment(),
    val purple: HslAdjustment = HslAdjustment(),
    val magenta: HslAdjustment = HslAdjustment()
) {
    fun adjustment(color: HslColor): HslAdjustment = when (color) {
        HslColor.RED -> red
        HslColor.ORANGE -> orange
        HslColor.YELLOW -> yellow
        HslColor.GREEN -> green
        HslColor.CYAN -> cyan
        HslColor.BLUE -> blue
        HslColor.PURPLE -> purple
        HslColor.MAGENTA -> magenta
    }

    fun isNeutral() = HslColor.entries.all { adjustment(it).isNeutral() }

    companion object {
        val NEUTRAL = HslSettings()
    }
}

data class ToneGrade(
    val hue: Int = 0,
    val amount: Int = 0,
    val luminance: Int = 0
) {
    init {
        require(hue in -180..180)
        require(amount in 0..100)
        require(luminance in -100..100)
    }

    fun isNeutral() = amount == 0 && luminance == 0
}

data class ColorGradingSettings(
    val hsl: HslSettings = HslSettings.NEUTRAL,
    val shadows: ToneGrade = ToneGrade(),
    val midtones: ToneGrade = ToneGrade(),
    val highlights: ToneGrade = ToneGrade(),
    val balance: Int = 0,
    val blending: Int = DEFAULT_BLENDING
) {
    init {
        require(balance in -100..100)
        require(blending in 0..100)
    }

    fun isNeutral(): Boolean =
        hsl.isNeutral() &&
            shadows.isNeutral() &&
            midtones.isNeutral() &&
            highlights.isNeutral() &&
            balance == 0

    companion object {
        const val DEFAULT_BLENDING = 50
        val NEUTRAL = ColorGradingSettings()
    }
}

object ColorGradingMath {
    fun applyInPlace(
        pixels: IntArray,
        settings: ColorGradingSettings,
        pixelCount: Int = pixels.size
    ) {
        require(pixelCount in 0..pixels.size)
        if (settings.isNeutral() || pixelCount == 0) return

        for (index in 0 until pixelCount) {
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha == 0) continue

            val originalR = ((pixel shr 16) and 255) / 255f
            val originalG = ((pixel shr 8) and 255) / 255f
            val originalB = (pixel and 255) / 255f

            val hsl = rgbToHsl(originalR, originalG, originalB)
            var hue = hsl[0]
            var saturation = hsl[1]
            var lightness = hsl[2]

            for (color in HslColor.entries) {
                val adjustment = settings.hsl.adjustment(color)
                if (adjustment.isNeutral()) continue

                val weight = hueWeight(hue, color.centerHue)
                if (weight <= 0f) continue

                hue = wrapHue(hue + adjustment.hue * weight)
                saturation = (saturation + adjustment.saturation / 100f * weight)
                    .coerceIn(0f, 1f)
                lightness = (lightness + adjustment.luminance / 200f * weight)
                    .coerceIn(0f, 1f)
            }

            val weights = tonalWeight(lightness, settings.balance, settings.blending)

            lightness = (
                lightness +
                    settings.shadows.luminance / 200f * weights.first +
                    settings.midtones.luminance / 200f * weights.second +
                    settings.highlights.luminance / 200f * weights.third
                ).coerceIn(0f, 1f)

            val adjusted = hslToRgb(hue, saturation, lightness)
            var r = adjusted[0]
            var g = adjusted[1]
            var b = adjusted[2]

            fun applyTint(grade: ToneGrade, zoneWeight: Float) {
                if (grade.amount == 0 || zoneWeight <= 0f) return
                val tint = hslToRgb(grade.hue.toFloat(), 0.65f, 0.5f)
                val mix = (grade.amount / 100f * zoneWeight * 0.22f)
                    .coerceIn(0f, 0.22f)
                r = (r * (1f - mix) + tint[0] * mix).coerceIn(0f, 1f)
                g = (g * (1f - mix) + tint[1] * mix).coerceIn(0f, 1f)
                b = (b * (1f - mix) + tint[2] * mix).coerceIn(0f, 1f)
            }

            applyTint(settings.shadows, weights.first)
            applyTint(settings.midtones, weights.second)
            applyTint(settings.highlights, weights.third)

            pixels[index] = (alpha shl 24) or
                (toByte(r) shl 16) or
                (toByte(g) shl 8) or
                toByte(b)
        }
    }

    private fun hueWeight(hue: Float, center: Float): Float {
        val distance = abs(wrapHue(hue - center).let {
            if (it > 180f) it - 360f else it
        })
        return (1f - distance / 45f).coerceIn(0f, 1f)
    }

    private fun tonalWeight(
        lightness: Float,
        balance: Int,
        blending: Int
    ): Triple<Float, Float, Float> {
        val shifted = (lightness - balance / 200f).coerceIn(0f, 1f)
        val overlap = (blending / 100f - 0.5f) * 0.30f
        val shadows = 1f - smoothstep(0.15f, 0.55f - overlap, shifted)
        val highlights = smoothstep(0.45f + overlap, 0.85f, shifted)
        val midtones = (1f - shadows - highlights).coerceIn(0f, 1f)
        return Triple(shadows, midtones, highlights)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun circularHueBlend(a: Float, b: Float, weight: Float): Float {
        val delta = wrapHue(b - a).let { if (it > 180f) it - 360f else it }
        return wrapHue(a + delta * weight.coerceIn(0f, 1f))
    }

    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val maximum = max(r, max(g, b))
        val minimum = min(r, min(g, b))
        val delta = maximum - minimum
        var hue = 0f
        val lightness = (maximum + minimum) / 2f

        val saturation = if (delta == 0f) {
            0f
        } else {
            delta / (1f - abs(2f * lightness - 1f))
        }

        if (delta != 0f) {
            hue = when (maximum) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
        }

        return floatArrayOf(wrapHue(hue), saturation, lightness)
    }

    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): FloatArray {
        val h = wrapHue(hue)
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

        return floatArrayOf(
            (rgb[0] + m).coerceIn(0f, 1f),
            (rgb[1] + m).coerceIn(0f, 1f),
            (rgb[2] + m).coerceIn(0f, 1f)
        )
    }

    private fun wrapHue(value: Float): Float {
        val wrapped = value % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    private fun toByte(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
}
