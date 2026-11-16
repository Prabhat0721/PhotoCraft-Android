package com.prabhat.photocraft.edit

import java.util.UUID

enum class MaskType {
    SUBJECT,
    BACKGROUND,
    BRUSH,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT,
    COLOR_RANGE,
    LUMINANCE_RANGE,
    SKY,
    LANDSCAPE,
    PEOPLE,
    OBJECT
}

enum class MaskCombineMode { REPLACE, ADD, SUBTRACT, INTERSECT }

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
    override fun equals(other: Any?): Boolean =
        other is MaskSelection && width == other.width && height == other.height &&
            coverage.contentEquals(other.coverage)

    override fun hashCode(): Int = 31 * (31 * width + height) + coverage.contentHashCode()

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
        sourceWidth: Int,
        sourceHeight: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        flow: Int = 100,
        erase: Boolean = false,
        feather: Int = 50
    ): MaskSelection {
        val scaleX = width.toFloat() / sourceWidth.coerceAtLeast(1)
        val scaleY = height.toFloat() / sourceHeight.coerceAtLeast(1)
        val mappedX = centerX * scaleX
        val mappedY = centerY * scaleY
        val mappedRadius = radius * maxOf(scaleX, scaleY)
        val output = coverage.copyOf()
        val r = mappedRadius.coerceAtLeast(1f)
        val left = (mappedX - r).toInt().coerceAtLeast(0)
        val right = (mappedX + r).toInt().coerceAtMost(width - 1)
        val top = (mappedY - r).toInt().coerceAtLeast(0)
        val bottom = (mappedY + r).toInt().coerceAtMost(height - 1)
        val amount = flow.coerceIn(0, 100) * 255 / 100
        val featherFraction = feather.coerceIn(0, 100) / 100f
        val featherStart = r * (1f - featherFraction)
        val featherWidth = (r - featherStart).coerceAtLeast(1f)
        for (y in top..bottom) for (x in left..right) {
            val dx = x - mappedX
            val dy = y - mappedY
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance <= r) {
                val index = y * width + x
                val old = output[index].toInt() and 0xFF
                val falloff = if (distance <= featherStart) 1f else
                    ((r - distance) / featherWidth).coerceIn(0f, 1f)
                val strength = (amount * falloff).toInt()
                output[index] = (if (erase) (old - strength).coerceAtLeast(0)
                else (old + strength).coerceAtMost(255)).toByte()
            }
        }
        return copy(coverage = output)
    }
}

data class MaskLayer(
    val type: MaskType,
    val id: String = UUID.randomUUID().toString(),
    val name: String = type.displayName(),
    val selection: MaskSelection? = null,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val retouchMode: Boolean = false,
    val combineMode: MaskCombineMode = MaskCombineMode.REPLACE,
    val adjustments: LocalMaskAdjustments = LocalMaskAdjustments()
) {
    fun paint(
        sourceWidth: Int,
        sourceHeight: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int,
        erase: Boolean = false,
        feather: Int = 50
    ): MaskLayer {
        require(sourceWidth > 0 && sourceHeight > 0)
        val current = selection ?: MaskSelection(sourceWidth, sourceHeight, ByteArray(sourceWidth * sourceHeight))
        return copy(selection = current.paint(sourceWidth, sourceHeight, x, y, radius, flow, erase, feather))
    }
}

private fun MaskType.displayName(): String = when (this) {
    MaskType.SUBJECT -> "Subject"
    MaskType.BACKGROUND -> "Background"
    MaskType.BRUSH -> "Brush"
    MaskType.LINEAR_GRADIENT -> "Linear Gradient"
    MaskType.RADIAL_GRADIENT -> "Radial Gradient"
    MaskType.COLOR_RANGE -> "Color Range"
    MaskType.LUMINANCE_RANGE -> "Luminance Range"
    MaskType.SKY -> "Sky"
    MaskType.LANDSCAPE -> "Landscape"
    MaskType.PEOPLE -> "People"
    MaskType.OBJECT -> "Object"
}

data class MaskingState(
    val subject: MaskLayer = MaskLayer(MaskType.SUBJECT, id = "subject"),
    val background: MaskLayer = MaskLayer(MaskType.BACKGROUND, id = "background"),
    val extraLayers: List<MaskLayer> = emptyList(),
    val selectedLayerId: String = "subject",
    val brushSize: Float = 40f,
    val brushFeather: Float = 50f,
    val brushFlow: Float = 100f
) {
    val layers: List<MaskLayer> get() = listOf(subject, background) + extraLayers

    fun transformed(geometry: GeometrySettings): MaskingState {
        if (geometry.isNeutral()) return this
        fun transformSelection(selection: MaskSelection?): MaskSelection? {
            selection ?: return null
            val radians = Math.toRadians(geometry.rotationDegrees.toDouble())
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)
            val outWidth = kotlin.math.ceil(
                selection.width * kotlin.math.abs(cos) + selection.height * kotlin.math.abs(sin)
            ).toInt().coerceAtLeast(1)
            val outHeight = kotlin.math.ceil(
                selection.width * kotlin.math.abs(sin) + selection.height * kotlin.math.abs(cos)
            ).toInt().coerceAtLeast(1)
            val out = ByteArray(outWidth * outHeight)
            val cx = outWidth / 2.0
            val cy = outHeight / 2.0
            for (y in 0 until outHeight) for (x in 0 until outWidth) {
                val rx = x + 0.5 - cx
                val ry = y + 0.5 - cy
                val rotatedX = cos * rx + sin * ry + selection.width / 2.0
                val rotatedY = -sin * rx + cos * ry + selection.height / 2.0
                val sourceX = (if (geometry.flipHorizontal) selection.width - 1 - rotatedX else rotatedX)
                    .toInt()
                val sourceY = (if (geometry.flipVertical) selection.height - 1 - rotatedY else rotatedY)
                    .toInt()
                if (sourceX in 0 until selection.width && sourceY in 0 until selection.height) {
                    out[y * outWidth + x] = selection.coverage[sourceY * selection.width + sourceX]
                }
            }
            return MaskSelection(outWidth, outHeight, out)
        }
        fun layer(value: MaskLayer) = value.copy(selection = transformSelection(value.selection))
        return copy(
            subject = layer(subject),
            background = layer(background),
            extraLayers = extraLayers.map(::layer)
        )
    }

    // Kept as a compatibility accessor for code that still refers to the active mask category.
    val selectedMask: MaskType get() = selected().type

    fun selected(): MaskLayer = layers.firstOrNull { it.id == selectedLayerId } ?: subject

    fun updateSelected(layer: MaskLayer): MaskingState = when (layer.id) {
        subject.id -> copy(subject = layer)
        background.id -> copy(background = layer)
        else -> copy(extraLayers = extraLayers.map { if (it.id == layer.id) layer else it })
    }

    fun addLayer(type: MaskType = MaskType.BRUSH, name: String? = null): MaskingState {
        val layer = MaskLayer(type, name = name ?: type.displayName())
        return copy(extraLayers = extraLayers + layer, selectedLayerId = layer.id)
    }

    fun duplicateSelected(): MaskingState {
        val source = selected()
        val duplicate = source.copy(id = UUID.randomUUID().toString(), name = "${source.name} copy")
        return copy(extraLayers = extraLayers + duplicate, selectedLayerId = duplicate.id)
    }

    fun combineSelectedWithPrevious(): MaskingState {
        val index = layers.indexOfFirst { it.id == selectedLayerId }
        if (index <= 0) return this
        val current = layers[index]
        val currentSelection = current.selection ?: return this
        val previous = layers[index - 1].selection ?: return this
        val output = ByteArray(currentSelection.width * currentSelection.height)
        for (y in 0 until currentSelection.height) for (x in 0 until currentSelection.width) {
            val a = previous.coverageAt(
                (x.toFloat() * previous.width / currentSelection.width).toInt(),
                (y.toFloat() * previous.height / currentSelection.height).toInt()
            )
            val raw = currentSelection.coverageAt(x, y)
            val b = if (current.inverted) 255 - raw else raw
            output[y * currentSelection.width + x] = when (current.combineMode) {
                MaskCombineMode.REPLACE -> b
                MaskCombineMode.ADD -> maxOf(a, b)
                MaskCombineMode.SUBTRACT -> (a - b).coerceAtLeast(0)
                MaskCombineMode.INTERSECT -> minOf(a, b)
            }.toByte()
        }
        return updateSelected(current.copy(
            selection = MaskSelection(currentSelection.width, currentSelection.height, output),
            inverted = false,
            combineMode = MaskCombineMode.REPLACE
        ))
    }

    fun deleteSelected(): MaskingState {
        if (selectedLayerId == subject.id || selectedLayerId == background.id) {
            val cleared = selected().copy(selection = null, adjustments = LocalMaskAdjustments(), inverted = false, retouchMode = false)
            return updateSelected(cleared)
        }
        val remaining = extraLayers.filterNot { it.id == selectedLayerId }
        return copy(extraLayers = remaining, selectedLayerId = remaining.lastOrNull()?.id ?: subject.id)
    }

    fun isNeutral(): Boolean = layers.all { it.selection == null }

    fun paintSelected(
        sourceWidth: Int,
        sourceHeight: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int = 100,
        erase: Boolean = false,
        feather: Int = brushFeather.toInt()
    ): MaskingState = updateSelected(selected().paint(
        sourceWidth, sourceHeight, x, y, radius, flow, erase, feather
    ))

    fun selectLayer(id: String): MaskingState =
        if (layers.any { it.id == id }) copy(selectedLayerId = id) else this

    fun selectMask(type: MaskType): MaskingState =
        selectLayer(layers.firstOrNull { it.type == type }?.id ?: subject.id)
}
