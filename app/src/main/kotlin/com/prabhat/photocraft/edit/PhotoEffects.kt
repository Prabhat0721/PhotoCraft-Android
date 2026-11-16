package com.prabhat.photocraft.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PhotoEffects(
    val sharpness: Int = 0,
    val sharpeningRadius: Int = 1,
    val sharpeningDetail: Int = 0,
    val sharpeningMasking: Int = 0,
    val texture: Int = 0,
    val details: Int = 0,
    val noiseReduction: Int = 0,
    val vignette: Int = 0,
    val backgroundBlur: Int = 0
) {
    init {
        require(listOf(sharpness, texture, details, noiseReduction, vignette, backgroundBlur)
            .all { it in -100..100 }) {
            "Effect values must be between -100 and 100."
        }
        require(sharpeningRadius in 1..5 && sharpeningDetail in 0..100 &&
            sharpeningMasking in 0..100) { "Sharpening controls are outside their supported range." }
    }

    fun isNeutral(): Boolean =
        sharpness == 0 && texture == 0 && details == 0 &&
            noiseReduction == 0 && vignette == 0 && backgroundBlur == 0

    companion object {
        val NEUTRAL = PhotoEffects()
    }
}

object PhotoEffectsMath {
    fun applyInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        effects: PhotoEffects,
        originX: Int = 0,
        originY: Int = 0,
        fullWidth: Int = width,
        fullHeight: Int = height
    ) {
        require(width > 0 && height > 0 && width.toLong() * height <= pixels.size.toLong()) {
            "Pixel dimensions must fit inside the buffer."
        }
        if (effects.isNeutral()) return

        if (effects.noiseReduction > 0) {
            smooth(pixels, width, height, effects.noiseReduction)
        }
        if (effects.sharpness != 0) {
            localContrast(
                pixels, width, height, effects.sharpness,
                effects.sharpeningRadius, detail = effects.sharpeningDetail,
                masking = effects.sharpeningMasking
            )
        }
        if (effects.texture != 0) {
            localContrast(pixels, width, height, effects.texture, 2)
        }
        if (effects.details != 0) {
            localContrast(pixels, width, height, effects.details, 1, 0.45f)
        }
        if (effects.vignette != 0) {
            applyVignette(
                pixels, width, height, effects.vignette,
                originX, originY, fullWidth, fullHeight
            )
        }
    }

    /** Apply a depth-like blur only where the supplied background selection is covered. */
    fun applyLensBlurInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        selection: MaskSelection?,
        amount: Int,
        crop: CropSelection = CropSelection.FULL
    ) {
        if (amount <= 0 || selection == null || pixels.size < width * height) return
        val radius = (amount / 18).coerceIn(1, 5)
        val horizontal = IntArray(width * height)
        val blurred = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
            for (dx in -radius..radius) {
                val xx = x + dx
                if (xx !in 0 until width) continue
                val c = pixels[y * width + xx]
                a += c ushr 24; r += (c ushr 16) and 255; g += (c ushr 8) and 255; b += c and 255; count++
            }
            horizontal[y * width + x] = ((a / count) shl 24) or ((r / count) shl 16) or
                ((g / count) shl 8) or (b / count)
        }
        for (y in 0 until height) for (x in 0 until width) {
            var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy !in 0 until height) continue
                val c = horizontal[yy * width + x]
                a += c ushr 24; r += (c ushr 16) and 255; g += (c ushr 8) and 255; b += c and 255; count++
            }
            blurred[y * width + x] = ((a / count) shl 24) or ((r / count) shl 16) or
                ((g / count) shl 8) or (b / count)
        }
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = crop.left + (x + 0.5f) / width * crop.width
            val sourceY = crop.top + (y + 0.5f) / height * crop.height
            val mx = (sourceX * selection.width).toInt().coerceIn(0, selection.width - 1)
            val my = (sourceY * selection.height).toInt().coerceIn(0, selection.height - 1)
            val coverage = selection.coverageAt(mx, my)
            if (coverage == 0) continue
            val i = y * width + x
            val original = pixels[i]
            val soft = blurred[i]
            val mix = (coverage / 255f * amount / 100f).coerceIn(0f, 1f)
            fun channel(shift: Int) = ((((original ushr shift) and 255) * (1f - mix) +
                ((soft ushr shift) and 255) * mix).toInt().coerceIn(0, 255))
            pixels[i] = (channel(24) shl 24) or (channel(16) shl 16) or
                (channel(8) shl 8) or channel(0)
        }
    }

    /** Clone-based spot healing using a nearby sample patch and local color matching. */
    fun applyHealInPlace(
        pixels: IntArray,
        width: Int,
        height: Int,
        selection: MaskSelection,
        crop: CropSelection = CropSelection.FULL,
        originX: Int = 0,
        originY: Int = 0,
        fullWidth: Int = width,
        fullHeight: Int = height
    ) {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return
        val original = pixels.copyOf(width * height)
        val offset = (width / 32).coerceIn(4, 32)
        fun channel(pixel: Int, shift: Int) = (pixel ushr shift) and 255
        for (y in 0 until height) for (x in 0 until width) {
            val outputX = x + originX
            val outputY = y + originY
            val sourceX = crop.left + (outputX + 0.5f) / fullWidth * crop.width
            val sourceY = crop.top + (outputY + 0.5f) / fullHeight * crop.height
            val maskX = (sourceX * selection.width).toInt().coerceIn(0, selection.width - 1)
            val maskY = (sourceY * selection.height).toInt().coerceIn(0, selection.height - 1)
            val coverage = selection.coverageAt(maskX, maskY)
            if (coverage == 0) continue
            val sx = (x - offset).coerceAtLeast(0)
            var targetR = 0; var targetG = 0; var targetB = 0
            var sourceR = 0; var sourceG = 0; var sourceB = 0
            var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val ty = (y + dy).coerceIn(0, height - 1)
                val sy = (y + dy).coerceIn(0, height - 1)
                val target = original[ty * width + (x + dx).coerceIn(0, width - 1)]
                val sample = original[sy * width + (sx + dx).coerceIn(0, width - 1)]
                targetR += channel(target, 16); targetG += channel(target, 8); targetB += channel(target, 0)
                sourceR += channel(sample, 16); sourceG += channel(sample, 8); sourceB += channel(sample, 0)
                count++
            }
            val originalPixel = original[y * width + x]
            val samplePixel = original[y * width + sx]
            val correctionR = targetR / count - sourceR / count
            val correctionG = targetG / count - sourceG / count
            val correctionB = targetB / count - sourceB / count
            val blend = coverage / 255f
            fun corrected(shift: Int, correction: Int): Int {
                val sampled = (channel(samplePixel, shift) + correction).coerceIn(0, 255)
                return (channel(originalPixel, shift) * (1f - blend) + sampled * blend)
                    .toInt().coerceIn(0, 255)
            }
            pixels[y * width + x] = (originalPixel and -0x1000000) or
                (corrected(16, correctionR) shl 16) or
                (corrected(8, correctionG) shl 8) or corrected(0, correctionB)
        }
    }

    private fun smooth(p: IntArray, w: Int, h: Int, amount: Int) {
        val src = p.copyOf()
        val strength = amount / 100f * 0.72f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = src[i]
            if ((c ushr 24) == 0) continue
            var sr = 0f; var sg = 0f; var sb = 0f; var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val n = src[yy * w + xx]
                val l1 = lum(c); val l2 = lum(n)
                val weight = if (abs(l1 - l2) < 36) 1f else 0.18f
                sr += ((n shr 16) and 255) * weight
                sg += ((n shr 8) and 255) * weight
                sb += (n and 255) * weight
                count += 1
            }
            val r = ((c shr 16) and 255).toFloat()
            val g = ((c shr 8) and 255).toFloat()
            val b = (c and 255).toFloat()
            val total = max(1f, count.toFloat())
            p[i] = (c and -0x1000000) or
                (mix(r, sr / total, strength) shl 16) or
                (mix(g, sg / total, strength) shl 8) or
                mix(b, sb / total, strength)
        }
    }

    private fun localContrast(
        p: IntArray, w: Int, h: Int, amount: Int,
        radius: Int, scale: Float = 1f,
        detail: Int = 0,
        masking: Int = 0
    ) {
        val src = p.copyOf()
        val strength = amount / 100f * 1.15f * scale
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = src[i]
            if ((c ushr 24) == 0) continue
            var sr = 0f; var sg = 0f; var sb = 0f; var count = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (dx == 0 && dy == 0) continue
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val n = src[yy * w + xx]
                sr += ((n shr 16) and 255).toFloat()
                sg += ((n shr 8) and 255).toFloat()
                sb += (n and 255).toFloat()
                count++
            }
            if (count == 0) continue
            val r = ((c shr 16) and 255).toFloat()
            val g = ((c shr 8) and 255).toFloat()
            val b = (c and 255).toFloat()
            val centerLum = lum(c)
            val above = src[(y - 1).coerceAtLeast(0) * w + x]
            val below = src[(y + 1).coerceAtMost(h - 1) * w + x]
            val left = src[y * w + (x - 1).coerceAtLeast(0)]
            val right = src[y * w + (x + 1).coerceAtMost(w - 1)]
            val gradient = maxOf(
                abs(centerLum - lum(above)), abs(centerLum - lum(below)),
                abs(centerLum - lum(left)), abs(centerLum - lum(right))
            ) / 255f
            val edgeWeight = ((gradient - masking / 100f) / (1f - masking / 100f).coerceAtLeast(0.01f))
                .coerceIn(0f, 1f)
            val detailWeight = 1f + detail / 100f * 0.5f
            p[i] = (c and -0x1000000) or
                (channel(r, sr / count, strength * detailWeight * edgeWeight.coerceAtLeast(0.2f)) shl 16) or
                (channel(g, sg / count, strength * detailWeight * edgeWeight.coerceAtLeast(0.2f)) shl 8) or
                channel(b, sb / count, strength * detailWeight * edgeWeight.coerceAtLeast(0.2f))
        }
    }

    private fun applyVignette(
        p: IntArray,
        w: Int,
        h: Int,
        amount: Int,
        originX: Int,
        originY: Int,
        fullWidth: Int,
        fullHeight: Int
    ) {
        val strength = amount / 100f * 0.72f
        val cx = (fullWidth - 1) / 2f
        val cy = (fullHeight - 1) / 2f
        val maxDist = max(1f, sqrt(cx * cx + cy * cy))
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = p[i]
            if ((c ushr 24) == 0) continue
            val globalX = x + originX
            val globalY = y + originY
            val d = sqrt(
                (globalX - cx) * (globalX - cx) +
                    (globalY - cy) * (globalY - cy)
            ) / maxDist
            val edge = (d * d).coerceIn(0f, 1f)
            val factor = (1f - strength * edge).coerceIn(0.15f, 1.85f)
            p[i] = (c and -0x1000000) or
                (clamp(((c shr 16) and 255) * factor) shl 16) or
                (clamp(((c shr 8) and 255) * factor) shl 8) or
                clamp((c and 255) * factor)
        }
    }

    private fun lum(c: Int): Int =
        ((((c shr 16) and 255) * 30 +
            ((c shr 8) and 255) * 59 +
            (c and 255) * 11) / 100)

    private fun mix(a: Float, b: Float, t: Float): Int =
        clamp(a + (b - a) * t)

    private fun channel(value: Float, average: Float, strength: Float): Int =
        clamp(value + (value - average) * strength)

    private fun clamp(v: Float): Int =
        v.toInt().coerceIn(0, 255)
}

