package com.prabhat.photocraft.image

import android.graphics.Bitmap
import android.graphics.Matrix
import com.prabhat.photocraft.edit.GeometrySettings

object BitmapGeometry {
    fun transform(source: Bitmap, geometry: GeometrySettings): Bitmap {
        if (geometry.isNeutral()) return source
        val matrix = Matrix().apply {
            postScale(
                if (geometry.flipHorizontal) -1f else 1f,
                if (geometry.flipVertical) -1f else 1f
            )
            postRotate(geometry.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
