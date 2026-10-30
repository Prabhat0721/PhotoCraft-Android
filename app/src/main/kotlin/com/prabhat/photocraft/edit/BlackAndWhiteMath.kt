package com.prabhat.photocraft.edit

object BlackAndWhiteMath {
    fun applyInPlace(pixels: IntArray) {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            val gray = (
                red * 0.299f +
                green * 0.587f +
                blue * 0.114f
            ).toInt().coerceIn(0, 255)

            pixels[i] = (pixel and -0x1000000) or
                (gray shl 16) or
                (gray shl 8) or gray
        }
    }
}
