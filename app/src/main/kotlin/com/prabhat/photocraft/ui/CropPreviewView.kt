package com.prabhat.photocraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.prabhat.photocraft.edit.CropSelection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class CropPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var bitmap: Bitmap? = null
    private var crop = CropSelection.FULL
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        invalidate()
    }

    fun setCrop(selection: CropSelection) {
        crop = selection
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        if (image.width <= 0 || image.height <= 0 || width <= 0 || height <= 0) {
            return
        }

        val sourceLeft = floor(crop.left * image.width).toInt().coerceIn(0, image.width - 1)
        val sourceTop = floor(crop.top * image.height).toInt().coerceIn(0, image.height - 1)
        val sourceRight = ceil(crop.right * image.width).toInt()
            .coerceIn(sourceLeft + 1, image.width)
        val sourceBottom = ceil(crop.bottom * image.height).toInt()
            .coerceIn(sourceTop + 1, image.height)
        sourceRect.set(sourceLeft, sourceTop, sourceRight, sourceBottom)

        val scale = min(
            width.toFloat() / sourceRect.width(),
            height.toFloat() / sourceRect.height()
        )
        val destinationWidth = sourceRect.width() * scale
        val destinationHeight = sourceRect.height() * scale
        val left = (width - destinationWidth) / 2f
        val top = (height - destinationHeight) / 2f
        destinationRect.set(left, top, left + destinationWidth, top + destinationHeight)
        canvas.drawBitmap(image, sourceRect, destinationRect, paint)
    }
}
