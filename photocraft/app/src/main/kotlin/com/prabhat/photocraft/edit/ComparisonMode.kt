package com.prabhat.photocraft.edit

enum class ComparisonMode {
    AFTER,
    BEFORE;

    fun toggled(): ComparisonMode = if (this == AFTER) BEFORE else AFTER
}
