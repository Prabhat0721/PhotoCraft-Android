package com.prabhat.photocraft.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CropHandle {
    MOVE,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

object CropHandleMath {
    fun hitTest(
        crop: CropSelection,
        x: Float,
        y: Float,
        toleranceX: Float,
        toleranceY: Float,
        allowEdges: Boolean
    ): CropHandle? {
        if (!x.isFinite() || !y.isFinite() || toleranceX < 0f || toleranceY < 0f) {
            return null
        }

        if (near(x, y, crop.left, crop.top, toleranceX, toleranceY)) {
            return CropHandle.TOP_LEFT
        }
        if (near(x, y, crop.right, crop.top, toleranceX, toleranceY)) {
            return CropHandle.TOP_RIGHT
        }
        if (near(x, y, crop.left, crop.bottom, toleranceX, toleranceY)) {
            return CropHandle.BOTTOM_LEFT
        }
        if (near(x, y, crop.right, crop.bottom, toleranceX, toleranceY)) {
            return CropHandle.BOTTOM_RIGHT
        }

        if (allowEdges) {
            val centerX = (crop.left + crop.right) / 2f
            val centerY = (crop.top + crop.bottom) / 2f
            if (near(x, y, crop.left, centerY, toleranceX, toleranceY)) {
                return CropHandle.LEFT
            }
            if (near(x, y, crop.right, centerY, toleranceX, toleranceY)) {
                return CropHandle.RIGHT
            }
            if (near(x, y, centerX, crop.top, toleranceX, toleranceY)) {
                return CropHandle.TOP
            }
            if (near(x, y, centerX, crop.bottom, toleranceX, toleranceY)) {
                return CropHandle.BOTTOM
            }
        }

        if (x in crop.left..crop.right && y in crop.top..crop.bottom) {
            return CropHandle.MOVE
        }
        return null
    }

    fun drag(
        crop: CropSelection,
        handle: CropHandle,
        deltaX: Float,
        deltaY: Float,
        ratio: CropRatio,
        sourceWidth: Int,
        sourceHeight: Int
    ): CropSelection {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Source dimensions must be positive."
        }
        if (!deltaX.isFinite() || !deltaY.isFinite()) {
            return crop
        }
        if (handle == CropHandle.MOVE) {
            return move(crop, deltaX, deltaY)
        }

        val fixedRatio = ratio.widthToHeight
        return if (fixedRatio == null) {
            resizeFree(crop, handle, deltaX, deltaY)
        } else {
            when (handle) {
                CropHandle.TOP_LEFT,
                CropHandle.TOP_RIGHT,
                CropHandle.BOTTOM_LEFT,
                CropHandle.BOTTOM_RIGHT -> resizeFixedCorner(
                    crop = crop,
                    handle = handle,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    ratio = fixedRatio,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )

                CropHandle.LEFT,
                CropHandle.RIGHT -> resizeFixedHorizontal(
                    crop = crop,
                    handle = handle,
                    deltaX = deltaX,
                    ratio = fixedRatio,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )

                CropHandle.TOP,
                CropHandle.BOTTOM -> resizeFixedVertical(
                    crop = crop,
                    handle = handle,
                    deltaY = deltaY,
                    ratio = fixedRatio,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                )

                CropHandle.MOVE -> crop
            }
        }
    }

    private fun move(crop: CropSelection, deltaX: Float, deltaY: Float): CropSelection {
        val left = (crop.left + deltaX).coerceIn(0f, 1f - crop.width)
        val top = (crop.top + deltaY).coerceIn(0f, 1f - crop.height)
        return CropSelection(left, top, left + crop.width, top + crop.height)
    }

    private fun resizeFree(
        crop: CropSelection,
        handle: CropHandle,
        deltaX: Float,
        deltaY: Float
    ): CropSelection {
        var left = crop.left
        var top = crop.top
        var right = crop.right
        var bottom = crop.bottom

        when (handle) {
            CropHandle.LEFT,
            CropHandle.TOP_LEFT,
            CropHandle.BOTTOM_LEFT -> left = (left + deltaX)
                .coerceIn(0f, (right - CropGeometry.MIN_FRACTION).coerceAtLeast(0f))

            CropHandle.RIGHT,
            CropHandle.TOP_RIGHT,
            CropHandle.BOTTOM_RIGHT -> right = (right + deltaX)
                .coerceIn((left + CropGeometry.MIN_FRACTION).coerceAtMost(1f), 1f)

            else -> Unit
        }

        when (handle) {
            CropHandle.TOP,
            CropHandle.TOP_LEFT,
            CropHandle.TOP_RIGHT -> top = (top + deltaY)
                .coerceIn(0f, (bottom - CropGeometry.MIN_FRACTION).coerceAtLeast(0f))

            CropHandle.BOTTOM,
            CropHandle.BOTTOM_LEFT,
            CropHandle.BOTTOM_RIGHT -> bottom = (bottom + deltaY)
                .coerceIn((top + CropGeometry.MIN_FRACTION).coerceAtMost(1f), 1f)

            else -> Unit
        }

        return CropSelection(left, top, right, bottom)
    }

    private fun resizeFixedCorner(
        crop: CropSelection,
        handle: CropHandle,
        deltaX: Float,
        deltaY: Float,
        ratio: Float,
        sourceWidth: Int,
        sourceHeight: Int
    ): CropSelection {
        val dragLeft = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
        val dragUp = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
        val anchorX = if (dragLeft) crop.right else crop.left
        val anchorY = if (dragUp) crop.bottom else crop.top
        val cornerX = if (dragLeft) crop.left else crop.right
        val cornerY = if (dragUp) crop.top else crop.bottom
        val pointerX = cornerX + deltaX
        val pointerY = cornerY + deltaY
        val normalizedHeightPerWidth = sourceWidth / (ratio * sourceHeight)

        val desiredWidth = abs(pointerX - anchorX)
        val desiredHeight = abs(pointerY - anchorY)
        var width = max(desiredWidth, desiredHeight / normalizedHeightPerWidth)

        val maxWidthX = if (dragLeft) anchorX else 1f - anchorX
        val maxHeightY = if (dragUp) anchorY else 1f - anchorY
        val maxWidth = min(maxWidthX, maxHeightY / normalizedHeightPerWidth)
        val minWidth = max(
            CropGeometry.MIN_FRACTION,
            CropGeometry.MIN_FRACTION / normalizedHeightPerWidth
        )
        if (maxWidth < minWidth) {
            return crop
        }
        width = width.coerceIn(minWidth, maxWidth)
        val height = width * normalizedHeightPerWidth

        val left = if (dragLeft) anchorX - width else anchorX
        val top = if (dragUp) anchorY - height else anchorY
        return CropSelection(left, top, left + width, top + height)
    }

    private fun resizeFixedHorizontal(
        crop: CropSelection,
        handle: CropHandle,
        deltaX: Float,
        ratio: Float,
        sourceWidth: Int,
        sourceHeight: Int
    ): CropSelection {
        val normalizedHeightPerWidth = sourceWidth / (ratio * sourceHeight)
        val centerY = (crop.top + crop.bottom) / 2f
        val maxWidthX = if (handle == CropHandle.LEFT) crop.right else 1f - crop.left
        val maxHeight = 2f * min(centerY, 1f - centerY)
        val maxWidth = min(maxWidthX, maxHeight / normalizedHeightPerWidth)
        val minWidth = max(
            CropGeometry.MIN_FRACTION,
            CropGeometry.MIN_FRACTION / normalizedHeightPerWidth
        )
        if (maxWidth < minWidth) {
            return crop
        }

        val desiredWidth = if (handle == CropHandle.LEFT) {
            crop.right - (crop.left + deltaX)
        } else {
            crop.right + deltaX - crop.left
        }
        val width = desiredWidth.coerceIn(minWidth, maxWidth)
        val height = width * normalizedHeightPerWidth
        val left = if (handle == CropHandle.LEFT) crop.right - width else crop.left
        val top = (centerY - height / 2f).coerceIn(0f, 1f - height)
        return CropSelection(left, top, left + width, top + height)
    }

    private fun resizeFixedVertical(
        crop: CropSelection,
        handle: CropHandle,
        deltaY: Float,
        ratio: Float,
        sourceWidth: Int,
        sourceHeight: Int
    ): CropSelection {
        val normalizedHeightPerWidth = sourceWidth / (ratio * sourceHeight)
        val centerX = (crop.left + crop.right) / 2f
        val maxHeightY = if (handle == CropHandle.TOP) crop.bottom else 1f - crop.top
        val maxWidth = 2f * min(centerX, 1f - centerX)
        val maxHeight = min(maxHeightY, maxWidth * normalizedHeightPerWidth)
        val minHeight = max(
            CropGeometry.MIN_FRACTION,
            CropGeometry.MIN_FRACTION * normalizedHeightPerWidth
        )
        if (maxHeight < minHeight) {
            return crop
        }

        val desiredHeight = if (handle == CropHandle.TOP) {
            crop.bottom - (crop.top + deltaY)
        } else {
            crop.bottom + deltaY - crop.top
        }
        val height = desiredHeight.coerceIn(minHeight, maxHeight)
        val width = height / normalizedHeightPerWidth
        val left = (centerX - width / 2f).coerceIn(0f, 1f - width)
        val top = if (handle == CropHandle.TOP) crop.bottom - height else crop.top
        return CropSelection(left, top, left + width, top + height)
    }

    private fun near(
        x: Float,
        y: Float,
        targetX: Float,
        targetY: Float,
        toleranceX: Float,
        toleranceY: Float
    ): Boolean = abs(x - targetX) <= toleranceX && abs(y - targetY) <= toleranceY
}
