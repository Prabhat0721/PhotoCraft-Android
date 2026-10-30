package com.prabhat.photocraft.edit

import kotlin.math.roundToInt

object EditStateCodec {
    const val VALUE_COUNT = 17
    private const val ADJUSTMENT_COUNT = 11
    private const val BASIS_POINT_SCALE = 10000f

    fun encode(state: EditState): IntArray {
        val values = IntArray(VALUE_COUNT)
        state.adjustments.toIntArray().copyInto(values, 0)
        values[11] = toBasisPoints(state.crop.left)
        values[12] = toBasisPoints(state.crop.top)
        values[13] = toBasisPoints(state.crop.right)
        values[14] = toBasisPoints(state.crop.bottom)
        values[15] = state.cropRatio.ordinal
        values[16] = if (state.blackAndWhite) 1 else 0
        return values
    }

    fun decode(values: IntArray?): EditState? {
        if (values == null || (values.size != 16 && values.size != VALUE_COUNT)) {
            return null
        }
        val adjustments = try {
            Adjustments.fromIntArray(values.copyOfRange(0, ADJUSTMENT_COUNT))
        } catch (_: IllegalArgumentException) {
            return null
        }
        val crop = try {
            CropSelection.fromBasisPoints(
                left = values[11],
                top = values[12],
                right = values[13],
                bottom = values[14]
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        val ratio = CropRatio.entries.getOrNull(values[15]) ?: return null
        return EditState(
            adjustments = adjustments,
            crop = crop,
            cropRatio = ratio,
            blackAndWhite = values.size > 16 && values[16] != 0
        )
    }

    fun encodeStack(states: List<EditState>): IntArray {
        val values = IntArray(states.size * VALUE_COUNT)
        states.forEachIndexed { index, state ->
            encode(state).copyInto(values, index * VALUE_COUNT)
        }
        return values
    }

    fun decodeStack(values: IntArray?): List<EditState> {
        if (values == null || values.isEmpty() || values.size % VALUE_COUNT != 0) {
            return emptyList()
        }
        val states = ArrayList<EditState>(values.size / VALUE_COUNT)
        var offset = 0
        while (offset < values.size) {
            val state = decode(values.copyOfRange(offset, offset + VALUE_COUNT))
                ?: return emptyList()
            states.add(state)
            offset += VALUE_COUNT
        }
        return states
    }

    fun decodeAdjustments(values: IntArray?): Adjustments? {
        if (values == null || values.size != ADJUSTMENT_COUNT) {
            return null
        }
        return try {
            Adjustments.fromIntArray(values)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun toBasisPoints(value: Float): Int =
        (value * BASIS_POINT_SCALE).roundToInt().coerceIn(0, 10000)
}
