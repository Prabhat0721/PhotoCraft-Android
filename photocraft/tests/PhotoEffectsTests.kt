import com.prabhat.photocraft.edit.PhotoEffects
import com.prabhat.photocraft.edit.PhotoEffectsMath

fun checkTest(name: String, passed: Boolean) {
    println("${if (passed) "PASS" else "FAIL"}: $name")
    check(passed) { name }
}

fun main() {
    val original = intArrayOf(
        0xFF202020.toInt(), 0xFF404040.toInt(), 0xFF606060.toInt(),
        0xFF808080.toInt(), 0xFFA0A0A0.toInt(), 0xFFC0C0C0.toInt(),
        0xFFE0E0E0.toInt(), 0xFF101010.toInt(), 0xFFF0F0F0.toInt()
    )

    val unchanged = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        unchanged, 3, 3, PhotoEffects()
    )
    checkTest("Neutral effects leave pixels unchanged", unchanged.contentEquals(original))

    val vignette = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        vignette, 3, 3, PhotoEffects(vignette = 80)
    )
    checkTest("Vignette changes image pixels", !vignette.contentEquals(original))
    checkTest("Vignette preserves alpha", vignette.indices.all {
        (vignette[it] ushr 24) == (original[it] ushr 24)
    })

    val sharp = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        sharp, 3, 3, PhotoEffects(sharpness = 80)
    )
    checkTest("Sharpness changes image pixels", !sharp.contentEquals(original))

    val texture = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        texture, 3, 3, PhotoEffects(texture = 80)
    )
    checkTest("Texture changes image pixels", !texture.contentEquals(original))

    val details = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        details, 3, 3, PhotoEffects(details = 80)
    )
    checkTest("Details changes image pixels", !details.contentEquals(original))

    val noise = original.copyOf()
    PhotoEffectsMath.applyInPlace(
        noise, 3, 3, PhotoEffects(noiseReduction = 80)
    )
    checkTest("Noise reduction changes image pixels", !noise.contentEquals(original))

    var rejected = false
    try {
        PhotoEffectsMath.applyInPlace(original, 2, 2, PhotoEffects(vignette = 50))
    } catch (_: IllegalArgumentException) {
        rejected = true
    }
    checkTest("Invalid pixel dimensions are rejected", rejected)

    println("All PhotoEffects tests passed.")
}
