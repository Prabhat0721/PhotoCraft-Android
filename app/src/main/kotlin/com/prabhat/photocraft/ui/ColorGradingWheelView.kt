package com.prabhat.photocraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.prabhat.photocraft.edit.ToneGrade
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Touch wheel: angle selects hue and distance from center selects saturation. */
class ColorGradingWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF17191D.toInt()
    }
    private val markerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private var centerX = 0f
    private var centerY = 0f
    private var wheelRadius = 0f
    private var dragging = false
    private var grade = ToneGrade()
    private var gradeChangedListener: ((ToneGrade) -> Unit)? = null
    private var gestureStartedListener: (() -> Unit)? = null
    private var gestureEndedListener: (() -> Unit)? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Color grading wheel"
    }

    fun setGrade(value: ToneGrade) {
        grade = value
        refreshAccessibilityValue()
        invalidate()
    }

    fun setOnGradeChangedListener(listener: (ToneGrade) -> Unit) {
        gradeChangedListener = listener
    }

    fun setOnGradeGestureListener(onStart: () -> Unit, onEnd: () -> Unit) {
        gestureStartedListener = onStart
        gestureEndedListener = onEnd
    }

    fun currentGrade(): ToneGrade = grade

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (260f * density).roundToInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(width, size)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        centerX = width / 2f
        centerY = height / 2f
        wheelRadius = (min(width, height) / 2f - 22f * density).coerceAtLeast(1f)
        wheelPaint.shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                0xFFFF3030.toInt(), 0xFFFFFF30.toInt(), 0xFF30FF30.toInt(),
                0xFF30FFFF.toInt(), 0xFF3030FF.toInt(), 0xFFFF30FF.toInt(),
                0xFFFF3030.toInt()
            ),
            null
        )
        centerPaint.shader = RadialGradient(
            centerX,
            centerY,
            wheelRadius,
            intArrayOf(0xFFF2F2F5.toInt(), 0xBFF2F2F5.toInt(), 0x00F2F2F5),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (wheelRadius <= 0f) return
        canvas.drawCircle(centerX, centerY, wheelRadius, wheelPaint)
        canvas.drawCircle(centerX, centerY, wheelRadius, centerPaint)
        canvas.drawCircle(centerX, centerY, wheelRadius, outerPaint)

        val radians = Math.toRadians(grade.hue.toDouble())
        val markerRadius = wheelRadius * (grade.amount / 100f)
        val markerX = centerX + cos(radians).toFloat() * markerRadius
        val markerY = centerY + sin(radians).toFloat() * markerRadius
        canvas.drawCircle(markerX, markerY, 10f * density, markerHaloPaint)
        canvas.drawCircle(markerX, markerY, 7f * density, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled || distanceFromCenter(event.x, event.y) > wheelRadius + 12f * density) {
                    return false
                }
                dragging = true
                gestureStartedListener?.invoke()
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                updateFromTouch(event.x, event.y)
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                gestureEndedListener?.invoke()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                gestureEndedListener?.invoke()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = (distanceFromCenter(x, y) / wheelRadius).coerceIn(0f, 1f)
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val wrappedHue = ((angle % 360.0) + 360.0) % 360.0
        val signedHue = if (wrappedHue > 180.0) wrappedHue - 360.0 else wrappedHue
        val next = grade.copy(
            hue = signedHue.roundToInt().coerceIn(-180, 180),
            amount = (distance * 100f).roundToInt().coerceIn(0, 100)
        )
        if (next != grade) {
            grade = next
            refreshAccessibilityValue()
            gradeChangedListener?.invoke(next)
            invalidate()
        }
    }

    private fun distanceFromCenter(x: Float, y: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun refreshAccessibilityValue() {
        contentDescription = "Color grading wheel, hue ${grade.hue} degrees, saturation ${grade.amount} percent"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stateDescription = "Hue ${grade.hue} degrees, saturation ${grade.amount} percent"
        }
    }
}
