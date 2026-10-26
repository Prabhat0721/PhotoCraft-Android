package com.prabhat.photocraft.edit

data class EditState(
    val adjustments: Adjustments = Adjustments.NEUTRAL,
    val crop: CropSelection = CropSelection.FULL,
    val cropRatio: CropRatio = CropRatio.FREE,
    val filter: FilterSettings = FilterSettings.NEUTRAL,
    val effects: PhotoEffects = PhotoEffects.NEUTRAL
) {
    fun isNeutral(): Boolean =
        adjustments.isNeutral() &&
            crop.isFull() &&
            cropRatio == CropRatio.FREE &&
            filter.isNeutral() &&
            effects.isNeutral()
}
