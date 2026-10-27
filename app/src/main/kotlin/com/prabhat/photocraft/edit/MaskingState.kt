package com.prabhat.photocraft.edit

enum class MaskType {
    SUBJECT,
    BACKGROUND
}

data class LocalMaskAdjustments(
    val adjustments: Adjustments = Adjustments.NEUTRAL,
    val filter: FilterSettings = FilterSettings.NEUTRAL,
    val effects: PhotoEffects = PhotoEffects.NEUTRAL,
    val colorGrading: ColorGradingSettings = ColorGradingSettings.NEUTRAL
)

data class MaskSelection(
    val width: Int,
    val height: Int,
    val coverage: ByteArray
) {
    init {
        require(width > 0 && height > 0) { "Mask dimensions must be positive" }
        require(width.toLong() * height.toLong() == coverage.size.toLong()) {
            "Coverage size must match mask dimensions"
        }
    }

    fun coverageAt(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        return coverage[y * width + x].toInt() and 0xFF
    }

    fun copiedCoverage(): ByteArray = coverage.copyOf()

    fun paint(
        centerX: Float,
        centerY: Float,
        radius: Float,
        flow: Int = 100,
        erase: Boolean = false
    ): MaskSelection {
        val output = coverage.copyOf()
        val r = radius.coerceAtLeast(1f)
        val left = (centerX - r).toInt().coerceAtLeast(0)
        val right = (centerX + r).toInt().coerceAtMost(width - 1)
        val top = (centerY - r).toInt().coerceAtLeast(0)
        val bottom = (centerY + r).toInt().coerceAtMost(height - 1)
        val amount = flow.coerceIn(0, 100) * 255 / 100

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance <= r) {
                    val index = y * width + x
                    val old = output[index].toInt() and 0xFF
                    val strength = (amount * (1f - distance / r)).toInt()
                    val updated = if (erase) {
                        (old - strength).coerceAtLeast(0)
                    } else {
                        (old + strength).coerceAtMost(255)
                    }
                    output[index] = updated.toByte()
                }
            }
        }
        return copy(coverage = output)
    }
}

data class MaskLayer(
    val type: MaskType,
    val selection: MaskSelection? = null,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val adjustments: LocalMaskAdjustments = LocalMaskAdjustments()
) {
    fun paint(
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int,
        erase: Boolean = false
    ): MaskLayer {
        require(width > 0 && height > 0)
        val current = selection
            ?.takeIf { it.width == width && it.height == height }
            ?: MaskSelection(width, height, ByteArray(width * height))

        return copy(
            selection = current.paint(x, y, radius, flow, erase)
        )
    }
}

data class MaskingState(
    val subject: MaskLayer = MaskLayer(MaskType.SUBJECT),
    val background: MaskLayer = MaskLayer(MaskType.BACKGROUND),
    val selectedMask: MaskType = MaskType.SUBJECT,
    val brushSize: Float = 40f,
    val brushFeather: Float = 50f,
    val brushFlow: Float = 100f
) {
    fun selected(): MaskLayer =
        if (selectedMask == MaskType.SUBJECT) subject else background

    fun isNeutral(): Boolean =
        subject.selection == null && background.selection == null

    fun paintSelected(
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int = 100,
        erase: Boolean = false
    ): MaskingState {
        val updated = selected().paint(width, height, x, y, radius, flow, erase)
        return when (selectedMask) {
            MaskType.SUBJECT -> copy(subject = updated)
            MaskType.BACKGROUND -> copy(background = updated)
        }
    }

    fun selectMask(type: MaskType): MaskingState =
        copy(selectedMask = type)
}
