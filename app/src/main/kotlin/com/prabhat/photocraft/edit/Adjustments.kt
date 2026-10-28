package com.prabhat.photocraft.edit

enum class AdjustmentGroup {
    LIGHT,
    COLOR
}

enum class Adjustment(val group: AdjustmentGroup) {
    EXPOSURE(AdjustmentGroup.LIGHT),
    BRIGHTNESS(AdjustmentGroup.LIGHT),
    CONTRAST(AdjustmentGroup.LIGHT),
    HIGHLIGHTS(AdjustmentGroup.LIGHT),
    SHADOWS(AdjustmentGroup.LIGHT),
    WHITES(AdjustmentGroup.LIGHT),
    BLACKS(AdjustmentGroup.LIGHT),
    TEMPERATURE(AdjustmentGroup.COLOR),
    TINT(AdjustmentGroup.COLOR),
    SATURATION(AdjustmentGroup.COLOR),
    VIBRANCE(AdjustmentGroup.COLOR);

    companion object {
        const val MIN_VALUE = -100
        const val MAX_VALUE = 100

        fun clamp(value: Int): Int = value.coerceIn(MIN_VALUE, MAX_VALUE)
    }
}

data class Adjustments(
    val exposure: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val whites: Int = 0,
    val blacks: Int = 0,
    val temperature: Int = 0,
    val tint: Int = 0,
    val saturation: Int = 0,
    val vibrance: Int = 0
) {
    init {
        require(
            listOf(
                exposure,
                brightness,
                contrast,
                highlights,
                shadows,
                whites,
                blacks,
                temperature,
                tint,
                saturation,
                vibrance
            ).all { it in Adjustment.MIN_VALUE..Adjustment.MAX_VALUE }
        ) { "Adjustment values must stay within the supported range." }
    }

    fun value(adjustment: Adjustment): Int = when (adjustment) {
        Adjustment.EXPOSURE -> exposure
        Adjustment.BRIGHTNESS -> brightness
        Adjustment.CONTRAST -> contrast
        Adjustment.HIGHLIGHTS -> highlights
        Adjustment.SHADOWS -> shadows
        Adjustment.WHITES -> whites
        Adjustment.BLACKS -> blacks
        Adjustment.TEMPERATURE -> temperature
        Adjustment.TINT -> tint
        Adjustment.SATURATION -> saturation
        Adjustment.VIBRANCE -> vibrance
    }

    fun with(adjustment: Adjustment, value: Int): Adjustments {
        val clamped = Adjustment.clamp(value)
        return when (adjustment) {
            Adjustment.EXPOSURE -> copy(exposure = clamped)
            Adjustment.BRIGHTNESS -> copy(brightness = clamped)
            Adjustment.CONTRAST -> copy(contrast = clamped)
            Adjustment.HIGHLIGHTS -> copy(highlights = clamped)
            Adjustment.SHADOWS -> copy(shadows = clamped)
            Adjustment.WHITES -> copy(whites = clamped)
            Adjustment.BLACKS -> copy(blacks = clamped)
            Adjustment.TEMPERATURE -> copy(temperature = clamped)
            Adjustment.TINT -> copy(tint = clamped)
            Adjustment.SATURATION -> copy(saturation = clamped)
            Adjustment.VIBRANCE -> copy(vibrance = clamped)
        }
    }

    fun isNeutral(): Boolean = this == NEUTRAL

    fun toIntArray(): IntArray = intArrayOf(
        exposure,
        brightness,
        contrast,
        highlights,
        shadows,
        whites,
        blacks,
        temperature,
        tint,
        saturation,
        vibrance
    )

    companion object {
        val NEUTRAL = Adjustments()

        fun fromIntArray(values: IntArray): Adjustments {
            require(values.size == 11) { "Expected 11 adjustment values." }
            return Adjustments(
                exposure = values[0],
                brightness = values[1],
                contrast = values[2],
                highlights = values[3],
                shadows = values[4],
                whites = values[5],
                blacks = values[6],
                temperature = values[7],
                tint = values[8],
                saturation = values[9],
                vibrance = values[10]
            )
        }

    }
}
