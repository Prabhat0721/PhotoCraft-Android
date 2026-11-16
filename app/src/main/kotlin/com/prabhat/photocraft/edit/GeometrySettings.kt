package com.prabhat.photocraft.edit

data class GeometrySettings(
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    init { require(rotationDegrees.isFinite() && rotationDegrees in -45f..360f) }
    fun isNeutral() = rotationDegrees == 0f && !flipHorizontal && !flipVertical
    fun rotateClockwise() = copy(rotationDegrees = (rotationDegrees + 90f) % 360f)

    fun outputDimensions(width: Int, height: Int): Pair<Int, Int> {
        val normalized = ((rotationDegrees % 360f) + 360f) % 360f
        if (normalized == 90f || normalized == 270f) return height to width
        if (normalized == 0f || normalized == 180f) return width to height
        val radians = Math.toRadians(normalized.toDouble())
        val outWidth = kotlin.math.ceil(width * kotlin.math.abs(kotlin.math.cos(radians)) +
            height * kotlin.math.abs(kotlin.math.sin(radians))).toInt().coerceAtLeast(1)
        val outHeight = kotlin.math.ceil(width * kotlin.math.abs(kotlin.math.sin(radians)) +
            height * kotlin.math.abs(kotlin.math.cos(radians))).toInt().coerceAtLeast(1)
        return outWidth to outHeight
    }

    companion object { val NEUTRAL = GeometrySettings() }
}
