package com.prabhat.photocraft.edit

/** Five evenly spaced control points (input 0, 25, 50, 75, 100 percent). */
data class ToneCurve(
    val shadows: Int = 0,
    val quartertones: Int = 25,
    val midtones: Int = 50,
    val threeQuartertones: Int = 75,
    val highlights: Int = 100
) {
    init {
        require(listOf(shadows, quartertones, midtones, threeQuartertones, highlights)
            .all { it in 0..100 }) { "Curve points must be between 0 and 100." }
    }

    fun points() = intArrayOf(shadows, quartertones, midtones, threeQuartertones, highlights)
    fun isNeutral() = this == NEUTRAL

    companion object { val NEUTRAL = ToneCurve() }
}

data class ToneCurves(
    val master: ToneCurve = ToneCurve.NEUTRAL,
    val red: ToneCurve = ToneCurve.NEUTRAL,
    val green: ToneCurve = ToneCurve.NEUTRAL,
    val blue: ToneCurve = ToneCurve.NEUTRAL
) {
    fun isNeutral() = master.isNeutral() && red.isNeutral() && green.isNeutral() && blue.isNeutral()
    fun values(): IntArray = master.points() + red.points() + green.points() + blue.points()

    companion object {
        val NEUTRAL = ToneCurves()
        fun fromValues(values: IntArray): ToneCurves {
            require(values.size == 20)
            fun curve(offset: Int) = ToneCurve(
                values[offset], values[offset + 1], values[offset + 2],
                values[offset + 3], values[offset + 4]
            )
            return ToneCurves(curve(0), curve(5), curve(10), curve(15))
        }
    }
}

object ToneCurvesMath {
    fun applyInPlace(pixels: IntArray, curves: ToneCurves) {
        if (curves.isNeutral()) return
        val master = lut(curves.master)
        val red = lut(curves.red)
        val green = lut(curves.green)
        val blue = lut(curves.blue)
        for (i in pixels.indices) {
            val color = pixels[i]
            if ((color ushr 24) == 0) continue
            val r = master[(color ushr 16) and 255]
            val g = master[(color ushr 8) and 255]
            val b = master[color and 255]
            pixels[i] = (color and -0x1000000) or
                (red[r] shl 16) or (green[g] shl 8) or blue[b]
        }
    }

    private fun lut(curve: ToneCurve): IntArray {
        val points = curve.points()
        return IntArray(256) { value ->
            val position = value / 255f * 4f
            val segment = position.toInt().coerceIn(0, 3)
            val fraction = position - segment
            val mapped = points[segment] + (points[segment + 1] - points[segment]) * fraction
            (mapped * 2.55f).toInt().coerceIn(0, 255)
        }
    }
}
