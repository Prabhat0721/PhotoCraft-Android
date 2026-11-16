package com.prabhat.photocraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import android.view.View
import android.view.MotionEvent
import com.prabhat.photocraft.edit.CropSelection
import com.prabhat.photocraft.edit.MaskSelection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

class CropPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var bitmap: Bitmap? = null
    private var crop = CropSelection.FULL
    private val sourceRect = Rect()
    private val overlayRect = Rect()
    private val fittedDestinationRect = RectF()
    private val destinationRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var maskSelection: MaskSelection? = null
    private var overlayBitmap: Bitmap? = null
    private var maskTouchListener: ((Float, Float, Int) -> Boolean)? = null
    private var mixTargetTouchListener: ((Float, Float, Int) -> Boolean)? = null
    private var mixTargetStrokeActive = false
    private var maskInteractionEnabled = false
    private var maskStrokeActive = false
    private var multiTouchGesture = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var zoomScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = zoomScale
                val nextScale = (oldScale * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (nextScale == oldScale) return true
                val ratio = nextScale / oldScale
                val centerX = width / 2f
                val centerY = height / 2f
                offsetX = detector.focusX - centerX -
                    (detector.focusX - centerX - offsetX) * ratio
                offsetY = detector.focusY - centerY -
                    (detector.focusY - centerY - offsetY) * ratio
                zoomScale = nextScale
                updateDestinationRect()
                invalidate()
                return true
            }
        }
    )

    val currentZoomScale: Float
        get() = zoomScale

    fun setMaskTouchListener(listener: ((Float, Float, Int) -> Boolean)?) {
        maskTouchListener = listener
    }

    fun setMixTargetTouchListener(listener: ((Float, Float, Int) -> Boolean)?) {
        mixTargetTouchListener = listener
    }

    fun setMaskInteractionEnabled(enabled: Boolean) {
        maskInteractionEnabled = enabled
    }

    fun resetZoom() {
        zoomScale = 1f
        offsetX = 0f
        offsetY = 0f
        updateDestinationRect()
        invalidate()
    }

    fun setMaskSelection(selection: MaskSelection?) {
        maskSelection = selection
        overlayBitmap?.recycle()
        overlayBitmap = null

        if (selection != null) {
            val scale = min(1f, 512f / maxOf(selection.width, selection.height).toFloat())
            val ow = (selection.width * scale).roundToInt().coerceAtLeast(1)
            val oh = (selection.height * scale).roundToInt().coerceAtLeast(1)
            val pixels = IntArray(ow * oh)

            for (y in 0 until oh) {
                val sy = (y.toFloat() / oh * selection.height).toInt()
                    .coerceIn(0, selection.height - 1)
                for (x in 0 until ow) {
                    val sx = (x.toFloat() / ow * selection.width).toInt()
                        .coerceIn(0, selection.width - 1)
                    val alpha = selection.coverageAt(sx, sy) * 110 / 255
                    pixels[y * ow + x] = Color.argb(alpha, 255, 45, 45)
                }
            }
            overlayBitmap = Bitmap.createBitmap(pixels, ow, oh, Bitmap.Config.ARGB_8888)
        }
        invalidate()
    }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        invalidate()
    }

    fun setCrop(selection: CropSelection) {
        crop = selection
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || destinationRect.isEmpty) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            !destinationRect.contains(event.x, event.y)
        ) return false

        scaleGestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouchGesture = false
                lastTouchX = event.x
                lastTouchY = event.y
                mixTargetStrokeActive = mixTargetTouchListener?.invoke(
                    event.x, event.y, MotionEvent.ACTION_DOWN
                ) == true
                if (mixTargetStrokeActive) return true
                parent?.requestDisallowInterceptTouchEvent(
                    maskInteractionEnabled || zoomScale > MIN_ZOOM
                )
                if (maskInteractionEnabled) {
                    maskStrokeActive = dispatchMaskTouch(event.x, event.y, MotionEvent.ACTION_DOWN)
                    return maskStrokeActive
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mixTargetStrokeActive) {
                    mixTargetTouchListener?.invoke(event.x, event.y, MotionEvent.ACTION_CANCEL)
                    mixTargetStrokeActive = false
                }
                multiTouchGesture = true
                if (maskStrokeActive) {
                    dispatchMaskTouch(event.x, event.y, MotionEvent.ACTION_CANCEL)
                    maskStrokeActive = false
                }
                lastFocusX = pointerFocusX(event)
                lastFocusY = pointerFocusY(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mixTargetStrokeActive) {
                    mixTargetTouchListener?.invoke(event.x, event.y, MotionEvent.ACTION_MOVE)
                    return true
                }
                if (multiTouchGesture && event.pointerCount >= 2) {
                    val focusX = pointerFocusX(event)
                    val focusY = pointerFocusY(event)
                    offsetX += focusX - lastFocusX
                    offsetY += focusY - lastFocusY
                    lastFocusX = focusX
                    lastFocusY = focusY
                    updateDestinationRect()
                    invalidate()
                    return true
                }
                if (multiTouchGesture) return true

                if (maskInteractionEnabled && maskStrokeActive) {
                    dispatchMaskTouch(event.x, event.y, MotionEvent.ACTION_MOVE)
                    return true
                }
                if (zoomScale > MIN_ZOOM) {
                    offsetX += event.x - lastTouchX
                    offsetY += event.y - lastTouchY
                    updateDestinationRect()
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> return true

            MotionEvent.ACTION_UP -> {
                if (mixTargetStrokeActive) {
                    mixTargetTouchListener?.invoke(event.x, event.y, MotionEvent.ACTION_UP)
                    mixTargetStrokeActive = false
                    performClick()
                    return true
                }
                if (maskStrokeActive) {
                    dispatchMaskTouch(event.x, event.y, MotionEvent.ACTION_UP)
                    performClick()
                }
                maskStrokeActive = false
                multiTouchGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mixTargetStrokeActive) {
                    mixTargetTouchListener?.invoke(event.x, event.y, MotionEvent.ACTION_CANCEL)
                    mixTargetStrokeActive = false
                    return true
                }
                if (maskStrokeActive) {
                    dispatchMaskTouch(event.x, event.y, MotionEvent.ACTION_CANCEL)
                }
                maskStrokeActive = false
                multiTouchGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun dispatchMaskTouch(x: Float, y: Float, action: Int): Boolean {
        val listener = maskTouchListener ?: return false
        if (destinationRect.width() <= 0f || destinationRect.height() <= 0f) return false
        val sourceX = (sourceRect.left +
            (x - destinationRect.left) / destinationRect.width() * sourceRect.width())
            .coerceIn(sourceRect.left.toFloat(), sourceRect.right.toFloat() - 1f)
        val sourceY = (sourceRect.top +
            (y - destinationRect.top) / destinationRect.height() * sourceRect.height())
            .coerceIn(sourceRect.top.toFloat(), sourceRect.bottom.toFloat() - 1f)
        return listener(sourceX, sourceY, action)
    }

    private fun pointerFocusX(event: MotionEvent): Float =
        (0 until event.pointerCount).map { event.getX(it) }.average().toFloat()

    private fun pointerFocusY(event: MotionEvent): Float =
        (0 until event.pointerCount).map { event.getY(it) }.average().toFloat()

    private fun updateDestinationRect() {
        if (fittedDestinationRect.isEmpty || width <= 0 || height <= 0) return
        val centerX = width / 2f
        val centerY = height / 2f
        val left = centerX + (fittedDestinationRect.left - centerX) * zoomScale
        val right = centerX + (fittedDestinationRect.right - centerX) * zoomScale
        val top = centerY + (fittedDestinationRect.top - centerY) * zoomScale
        val bottom = centerY + (fittedDestinationRect.bottom - centerY) * zoomScale

        val scaledWidth = right - left
        val scaledHeight = bottom - top
        val minX = if (scaledWidth <= width) 0f else width - right
        val maxX = if (scaledWidth <= width) 0f else -left
        val minY = if (scaledHeight <= height) 0f else height - bottom
        val maxY = if (scaledHeight <= height) 0f else -top
        offsetX = offsetX.coerceIn(minX, maxX)
        offsetY = offsetY.coerceIn(minY, maxY)
        destinationRect.set(left + offsetX, top + offsetY, right + offsetX, bottom + offsetY)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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
        fittedDestinationRect.set(left, top, left + destinationWidth, top + destinationHeight)
        updateDestinationRect()
        canvas.drawBitmap(image, sourceRect, destinationRect, paint)

        val overlay = overlayBitmap
        if (overlay != null && maskSelection != null) {
            val ol = floor(crop.left * overlay.width).toInt().coerceIn(0, overlay.width - 1)
            val ot = floor(crop.top * overlay.height).toInt().coerceIn(0, overlay.height - 1)
            val oright = ceil(crop.right * overlay.width).toInt().coerceIn(ol + 1, overlay.width)
            val obottom = ceil(crop.bottom * overlay.height).toInt().coerceIn(ot + 1, overlay.height)
            overlayRect.set(ol, ot, oright, obottom)
            canvas.drawBitmap(overlay, overlayRect, destinationRect, overlayPaint)
        }
    }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
    }
}
