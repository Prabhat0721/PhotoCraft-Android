package com.prabhat.photocraft.edit

enum class Filter {
    NATURAL,
    WARM,
    COOL,
    BLACK_WHITE,
    VINTAGE,
    CINEMATIC
}

data class FilterSettings(
    val filter: Filter = Filter.NATURAL,
    val intensity: Int = 100
) {
    init {
        require(intensity in MIN_INTENSITY..MAX_INTENSITY) {
            "Filter intensity must stay within the supported range."
        }
    }

    fun isNeutral(): Boolean = filter == Filter.NATURAL || intensity == MIN_INTENSITY

    companion object {
        const val MIN_INTENSITY = 0
        const val MAX_INTENSITY = 100
        const val DEFAULT_INTENSITY = 100
        val NEUTRAL = FilterSettings()

        fun selecting(filter: Filter, currentIntensity: Int): FilterSettings =
            FilterSettings(
                filter = filter,
                intensity = if (currentIntensity <= MIN_INTENSITY) {
                    DEFAULT_INTENSITY
                } else {
                    currentIntensity
                }
            )
    }
}
