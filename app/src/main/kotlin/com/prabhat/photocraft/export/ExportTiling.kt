package com.prabhat.photocraft.export

data class ExportTile(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(left >= 0 && top >= 0) { "Tile origin must be non-negative." }
        require(width > 0 && height > 0) { "Tile dimensions must be positive." }
    }

    val pixelCount: Int
        get() = width * height
}

object ExportTiling {
    const val TARGET_TILE_BYTES = 4 * 1024 * 1024
    const val BYTES_PER_PIXEL = 4

    fun plan(
        width: Int,
        height: Int,
        targetBytes: Int = TARGET_TILE_BYTES
    ): List<ExportTile> {
        require(width > 0) { "Width must be positive." }
        require(height > 0) { "Height must be positive." }
        require(targetBytes >= BYTES_PER_PIXEL) {
            "Target bytes must hold at least one pixel."
        }

        val maxPixels = targetBytes / BYTES_PER_PIXEL
        val tileWidth = minOf(width, maxPixels)
        val tileHeight = minOf(height, maxPixels / tileWidth).coerceAtLeast(1)
        val tiles = ArrayList<ExportTile>()

        var top = 0
        while (top < height) {
            val currentHeight = minOf(tileHeight, height - top)
            var left = 0
            while (left < width) {
                val currentWidth = minOf(tileWidth, width - left)
                tiles.add(ExportTile(left, top, currentWidth, currentHeight))
                left += currentWidth
            }
            top += currentHeight
        }

        return tiles
    }
}
