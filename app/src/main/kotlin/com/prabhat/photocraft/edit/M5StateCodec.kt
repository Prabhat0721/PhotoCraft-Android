package com.prabhat.photocraft.edit

import kotlin.math.roundToInt

object M5StateCodec {
    const val LEGACY_VALUE_COUNT = 18
    const val VALUE_COUNT = 54
    private const val ADJUSTMENT_COUNT = 11
    private const val BASIS_POINT_SCALE = 10000f

    fun encode(state: EditState): IntArray {
        val values = IntArray(VALUE_COUNT)
        state.adjustments.toIntArray().copyInto(values)
        values[11] = toBasisPoints(state.crop.left)
        values[12] = toBasisPoints(state.crop.top)
        values[13] = toBasisPoints(state.crop.right)
        values[14] = toBasisPoints(state.crop.bottom)
        values[15] = state.cropRatio.ordinal
        values[16] = state.filter.filter.ordinal
        values[17] = state.filter.intensity

        var i = LEGACY_VALUE_COUNT
        for (color in HslColor.entries) {
            val a = state.colorGrading.hsl.adjustment(color)
            values[i++] = a.hue
            values[i++] = a.saturation
            values[i++] = a.luminance
        }
        for (grade in listOf(
            state.colorGrading.shadows,
            state.colorGrading.midtones,
            state.colorGrading.highlights
        )) {
            values[i++] = grade.hue
            values[i++] = grade.amount
            values[i++] = grade.luminance
        }
        values[i++] = state.colorGrading.balance
        values[i++] = state.colorGrading.blending
        values[i] = if (state.blackAndWhite) 1 else 0
        return values
    }

    fun decode(values: IntArray?): EditState? {
        if (values == null ||
            (values.size != LEGACY_VALUE_COUNT && values.size != 53 && values.size != VALUE_COUNT)
        ) return null

        return try {
            val adjustments = Adjustments.fromIntArray(
                values.copyOfRange(0, ADJUSTMENT_COUNT)
            )
            val crop = CropSelection.fromBasisPoints(
                left = values[11],
                top = values[12],
                right = values[13],
                bottom = values[14]
            )
            val ratio = CropRatio.entries.getOrNull(values[15]) ?: return null
            val filter = Filter.entries.getOrNull(values[16]) ?: return null
            val filterSettings = FilterSettings(
                filter = filter,
                intensity = values[17]
            )

            var grading = ColorGradingSettings.NEUTRAL
            if (values.size == 53 || values.size == VALUE_COUNT) {
                var i = LEGACY_VALUE_COUNT
                val adjustmentsByColor = HslColor.entries.associateWith {
                    HslAdjustment(
                        hue = values[i++],
                        saturation = values[i++],
                        luminance = values[i++]
                    )
                }
                val hsl = HslSettings(
                    red = adjustmentsByColor.getValue(HslColor.RED),
                    orange = adjustmentsByColor.getValue(HslColor.ORANGE),
                    yellow = adjustmentsByColor.getValue(HslColor.YELLOW),
                    green = adjustmentsByColor.getValue(HslColor.GREEN),
                    cyan = adjustmentsByColor.getValue(HslColor.CYAN),
                    blue = adjustmentsByColor.getValue(HslColor.BLUE),
                    purple = adjustmentsByColor.getValue(HslColor.PURPLE),
                    magenta = adjustmentsByColor.getValue(HslColor.MAGENTA)
                )

                fun readGrade() = ToneGrade(
                    hue = values[i++],
                    amount = values[i++],
                    luminance = values[i++]
                )

                val shadows = readGrade()
                val midtones = readGrade()
                val highlights = readGrade()
                grading = ColorGradingSettings(
                    hsl = hsl,
                    shadows = shadows,
                    midtones = midtones,
                    highlights = highlights,
                    balance = values[i++],
                    blending = values[i]
                )
            }

            EditState(
                adjustments = adjustments,
                crop = crop,
                cropRatio = ratio,
                filter = filterSettings,
                colorGrading = grading,
                blackAndWhite = values.size == VALUE_COUNT && values[53] != 0
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun encodeStack(states: List<EditState>): IntArray {
        val values = IntArray(states.size * VALUE_COUNT)
        states.forEachIndexed { index, state ->
            encode(state).copyInto(values, index * VALUE_COUNT)
        }
        return values
    }

    fun decodeStack(values: IntArray?): List<EditState> {
        if (values == null || values.isEmpty()) return emptyList()

        val recordSize = when {
            values.size % VALUE_COUNT == 0 -> VALUE_COUNT
            values.size % LEGACY_VALUE_COUNT == 0 -> LEGACY_VALUE_COUNT
            else -> return emptyList()
        }

        val states = ArrayList<EditState>(values.size / recordSize)
        var offset = 0
        while (offset < values.size) {
            val state = decode(
                values.copyOfRange(offset, offset + recordSize)
            ) ?: return emptyList()
            states.add(state)
            offset += recordSize
        }
        return states
    }

    private fun toBasisPoints(value: Float): Int =
        (value * BASIS_POINT_SCALE).roundToInt().coerceIn(0, 10000)
}
