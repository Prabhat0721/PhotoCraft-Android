import com.prabhat.photocraft.edit.ColorGradingMath
import com.prabhat.photocraft.edit.ColorGradingSettings
import com.prabhat.photocraft.edit.HslAdjustment
import com.prabhat.photocraft.edit.HslColor
import com.prabhat.photocraft.edit.HslSettings
import com.prabhat.photocraft.edit.ToneGrade

private fun checkTest(condition: Boolean, message: String) {
    check(condition) { message }
}

fun main() {
    val source = intArrayOf(
        0xFFFF0000.toInt(),
        0xFF00FF00.toInt(),
        0xFF0000FF.toInt(),
        0x804080C0.toInt(),
        0x00010203
    )

    run {
        val actual = source.copyOf()
        ColorGradingMath.applyInPlace(actual, ColorGradingSettings.NEUTRAL)
        checkTest(actual.contentEquals(source), "Neutral settings changed pixels")
    }

    run {
        val actual = source.copyOf()
        val settings = ColorGradingSettings(
            hsl = HslSettings(red = HslAdjustment(hue = 35))
        )
        ColorGradingMath.applyInPlace(actual, settings)
        checkTest(actual[0] != source[0], "Red hue adjustment had no effect")
        checkTest(actual[1] == source[1], "Green pixel changed from red-only HSL")
        checkTest(actual[2] == source[2], "Blue pixel changed from red-only HSL")
    }

    run {
        val actual = source.copyOf()
        val settings = ColorGradingSettings(
            hsl = HslSettings(blue = HslAdjustment(saturation = -70))
        )
        ColorGradingMath.applyInPlace(actual, settings)
        checkTest(actual[2] != source[2], "Blue saturation adjustment had no effect")
        checkTest(actual[0] == source[0], "Red pixel changed from blue-only HSL")
        checkTest(actual[1] == source[1], "Green pixel changed from blue-only HSL")
    }

    run {
        val actual = intArrayOf(0xFF302020.toInt())
        ColorGradingMath.applyInPlace(
            actual,
            ColorGradingSettings(shadows = ToneGrade(hue = -140, amount = 70))
        )
        checkTest(actual[0] != 0xFF302020.toInt(), "Shadow tint had no effect on dark pixel")
    }

    run {
        val actual = intArrayOf(0xFFFFD0A0.toInt())
        ColorGradingMath.applyInPlace(
            actual,
            ColorGradingSettings(highlights = ToneGrade(hue = 35, amount = 80))
        )
        checkTest(actual[0] != 0xFFFFD0A0.toInt(), "Highlight tint had no effect on bright pixel")
    }

    run {
        val actual = source.copyOf()
        ColorGradingMath.applyInPlace(
            actual,
            ColorGradingSettings(hsl = HslSettings(green = HslAdjustment(luminance = 40))),
            pixelCount = 2
        )
        checkTest(actual[2] == source[2], "Pixels beyond pixelCount were modified")
        checkTest(actual[3] == source[3], "Pixels beyond pixelCount were modified")
    }

    run {
        val actual = source.copyOf()
        ColorGradingMath.applyInPlace(
            actual,
            ColorGradingSettings(midtones = ToneGrade(hue = -80, amount = 60))
        )
        for (i in source.indices) {
            checkTest(
                (actual[i] ushr 24) == (source[i] ushr 24),
                "Alpha channel changed at pixel $i"
            )
        }
        checkTest(actual[4] == source[4], "Fully transparent pixel changed")
    }

    run {
        val colors = HslColor.entries.toSet()
        checkTest(colors.size == 8, "Expected 8 independent HSL color channels")
    }

    run {
        var rejected = false
        try {
            HslAdjustment(hue = 181)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        checkTest(rejected, "Invalid hue value was accepted")
    }

    run {
        var rejected = false
        try {
            ToneGrade(amount = 101)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        checkTest(rejected, "Invalid tonal grade amount was accepted")
    }

    println("PASS: neutral settings preserve pixels")
    println("PASS: HSL color-channel adjustments")
    println("PASS: shadow and highlight tint")
    println("PASS: pixel count and alpha preservation")
    println("PASS: model validation and 8 HSL channels")
    println("ALL COLOR GRADING TESTS PASSED")
}
