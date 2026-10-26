package com.prabhat.photocraft.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.prabhat.photocraft.R
import com.prabhat.photocraft.edit.Adjustment
import java.util.Locale

class AdjustmentRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    interface Listener {
        fun onAdjustmentStarted(row: AdjustmentRowView)
        fun onAdjustmentChanged(row: AdjustmentRowView, value: Int)
        fun onAdjustmentStopped(row: AdjustmentRowView)
    }

    private val labelView: TextView
    private val valueView: TextView
    private val seekBar: SeekBar
    private var boundAdjustment: Adjustment? = null
    private var listener: Listener? = null
    private var suppressCallback = false
    private val resetDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (boundAdjustment == null || !seekBar.isEnabled) {
                    return false
                }
                val current = seekBar.progress + Adjustment.MIN_VALUE
                if (current == 0) {
                    return true
                }
                listener?.onAdjustmentStarted(this@AdjustmentRowView)
                setValue(0)
                listener?.onAdjustmentChanged(this@AdjustmentRowView, 0)
                listener?.onAdjustmentStopped(this@AdjustmentRowView)
                return true
            }
        }
    )

    val adjustment: Adjustment?
        get() = boundAdjustment

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_adjustment_row, this, true)
        labelView = findViewById(R.id.adjustmentLabel)
        valueView = findViewById(R.id.adjustmentValue)
        seekBar = findViewById(R.id.adjustmentSeekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (suppressCallback) {
                    return
                }
                val value = progress + Adjustment.MIN_VALUE
                updateValue(value)
                listener?.onAdjustmentChanged(this@AdjustmentRowView, value)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                listener?.onAdjustmentStarted(this@AdjustmentRowView)
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                listener?.onAdjustmentStopped(this@AdjustmentRowView)
            }
        })
        seekBar.setOnTouchListener { _, event ->
            resetDetector.onTouchEvent(event)
        }
    }

    fun bind(adjustment: Adjustment, label: String, value: Int, listener: Listener) {
        boundAdjustment = adjustment
        this.listener = listener
        labelView.text = label
        setValue(value)
    }

    fun setValue(value: Int) {
        suppressCallback = true
        seekBar.progress = value - Adjustment.MIN_VALUE
        suppressCallback = false
        updateValue(value)
    }

    fun setAdjustmentEnabled(enabled: Boolean) {
        seekBar.isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
    }

    private fun updateValue(value: Int) {
        val current = boundAdjustment ?: return
        val formatted = formatValue(current, value)
        valueView.text = formatted
        seekBar.contentDescription = context.getString(
            R.string.adjustment_control_description,
            labelView.text,
            formatted
        )
    }

    companion object {
        fun formatValue(adjustment: Adjustment, value: Int): String = when (adjustment) {
            Adjustment.EXPOSURE -> String.format(Locale.US, "%+.2f EV", value / 50.0)
            else -> String.format(Locale.US, "%+d", value)
        }
    }
}
