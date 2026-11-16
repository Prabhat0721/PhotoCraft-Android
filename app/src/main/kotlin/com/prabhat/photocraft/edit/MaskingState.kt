package com.prabhat.photocraft.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class MaskType {
    SUBJECT,
    BACKGROUND,
    BRUSH
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

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - mappedX
                val dy = y - mappedY
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= r) {
                    val index = y * width + x
                    val old = output[index].toInt() and 0xFF
                    val falloff = if (distance <= featherStart) {
                        1f
                    } else {
                        ((r - distance) / featherWidth).coerceIn(0f, 1f)
                    }
                    val strength = (amount * falloff).toInt()
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

data class MaskComponent(
    val name: String,
    val add: Boolean,
    val selection: MaskSelection
)

data class MaskLayer(
    val type: MaskType,
    val name: String = type.name,
    val selection: MaskSelection? = null, // base/legacy coverage (preserved)
    val components: List<MaskComponent> = emptyList(),
    val selectedComponentIndex: Int = -1,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val adjustments: LocalMaskAdjustments = LocalMaskAdjustments()
) {
    fun hasCoverage(): Boolean = selection != null || components.isNotEmpty()

    fun effectiveSelection(): MaskSelection? {
        val base = selection
        if (components.isEmpty()) return base
        val w = base?.width ?: components.first().selection.width
        val h = base?.height ?: components.first().selection.height
        val out = base?.copiedCoverage() ?: ByteArray(w * h)
        for (comp in components) {
            val sel = comp.selection
            if (sel.width != w || sel.height != h) {
                // fallback: sample nearest
                for (y in 0 until h) {
                    val sy = (y * sel.height / h).coerceIn(0, sel.height - 1)
                    for (x in 0 until w) {
                        val sx = (x * sel.width / w).coerceIn(0, sel.width - 1)
                        val i = y * w + x
                        val v = out[i].toInt() and 0xFF
                        val c = sel.coverageAt(sx, sy)
                        val nv = if (comp.add) min(255, v + c) else max(0, v - c)
                        out[i] = nv.toByte()
                    }
                }
            } else {
                for (i in out.indices) {
                    val v = out[i].toInt() and 0xFF
                    val c = sel.coverage[i].toInt() and 0xFF
                    val nv = if (comp.add) min(255, v + c) else max(0, v - c)
                    out[i] = nv.toByte()
                }
            }
        }
        return MaskSelection(w, h, out)
    }

    fun selectedComponent(): MaskComponent? =
        components.getOrNull(selectedComponentIndex)

    fun addComponent(add: Boolean, sourceWidth: Int, sourceHeight: Int, displayName: String? = null): MaskLayer {
        val w = selection?.width ?: sourceWidth
        val h = selection?.height ?: sourceHeight
        val count = components.count { it.add == add } + 1
        val name = displayName ?: if (add) "Add $count" else "Subtract $count"
        val comp = MaskComponent(name, add, MaskSelection(w, h, ByteArray(w * h)))
        val list = components + comp
        return copy(components = list, selectedComponentIndex = list.lastIndex)
    }

    fun selectComponent(index: Int): MaskLayer =
        if (index in components.indices) copy(selectedComponentIndex = index) else this

    fun deleteSelectedComponent(): MaskLayer {
        if (selectedComponentIndex !in components.indices) return this
        val list = components.toMutableList().also { it.removeAt(selectedComponentIndex) }
        val newIndex = when {
            list.isEmpty() -> -1
            selectedComponentIndex >= list.size -> list.lastIndex
            else -> selectedComponentIndex
        }
        return copy(components = list, selectedComponentIndex = newIndex)
    }

    fun paintSelectedComponent(
        sourceWidth: Int,
        sourceHeight: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int,
        feather: Int
    ): MaskLayer {
        val idx = selectedComponentIndex
        if (idx !in components.indices) {
            // legacy/no component: paint base selection directly
            require(sourceWidth > 0 && sourceHeight > 0)
            val current = selection
                ?: MaskSelection(sourceWidth, sourceHeight, ByteArray(sourceWidth * sourceHeight))
            return copy(
                selection = current.paint(sourceWidth, sourceHeight, x, y, radius, flow, erase = false, feather = feather)
            )
        }
        val comp = components[idx]
        // component buffer always accumulates paint as "ink" (erase=false); subtract effect applied at combine time
        val updatedSel = comp.selection.paint(sourceWidth, sourceHeight, x, y, radius, flow, erase = false, feather = feather)
        val list = components.toMutableList()
        list[idx] = comp.copy(selection = updatedSel)
        return copy(components = list)
    }

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
        // legacy path
        require(sourceWidth > 0 && sourceHeight > 0)
        val current = selection
            ?: MaskSelection(sourceWidth, sourceHeight, ByteArray(sourceWidth * sourceHeight))
        return copy(
            selection = current.paint(sourceWidth, sourceHeight, x, y, radius, flow, erase, feather)
        )
    }
}

data class MaskingState(
    val subject: MaskLayer = MaskLayer(MaskType.SUBJECT, "Subject"),
    val background: MaskLayer = MaskLayer(MaskType.BACKGROUND, "Background"),
    val brushLayers: List<MaskLayer> = emptyList(),
    val selectedMask: MaskType = MaskType.SUBJECT,
    val selectedBrushIndex: Int = -1,
    val brushSize: Float = 40f,
    val brushFeather: Float = 50f,
    val brushFlow: Float = 100f
) {
    fun selected(): MaskLayer = when {
        selectedMask == MaskType.BRUSH && selectedBrushIndex in brushLayers.indices ->
            brushLayers[selectedBrushIndex]
        selectedMask == MaskType.BACKGROUND -> background
        else -> subject
    }

    fun hasSelection(): Boolean = selected().hasCoverage()

    fun isNeutral(): Boolean =
        subject.selection == null && background.selection == null && brushLayers.isEmpty() &&
            subject.components.isEmpty() && background.components.isEmpty()

    fun allLayers(): List<MaskLayer> = listOf(subject, background) + brushLayers

    fun updateSelected(layer: MaskLayer): MaskingState = when {
        selectedMask == MaskType.BRUSH && selectedBrushIndex in brushLayers.indices ->
            copy(brushLayers = brushLayers.toMutableList().also { it[selectedBrushIndex] = layer })
        selectedMask == MaskType.BACKGROUND -> copy(background = layer)
        else -> copy(subject = layer)
    }

    fun paintSelected(
        sourceWidth: Int,
        sourceHeight: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int = 100,
        erase: Boolean = false,
        feather: Int = brushFeather.toInt()
    ): MaskingState {
        val updated = selected().paint(
            sourceWidth, sourceHeight, x, y, radius, flow, erase, feather
        )
        return updateSelected(updated)
    }

    fun ensureComponentForStroke(add: Boolean, sourceWidth: Int, sourceHeight: Int): MaskingState {
        val layer = selected().addComponent(add, sourceWidth, sourceHeight)
        return updateSelected(layer)
    }

    fun paintSelectedComponent(
        sourceWidth: Int,
        sourceHeight: Int,
        x: Float,
        y: Float,
        radius: Float,
        flow: Int = 100,
        feather: Int = brushFeather.toInt()
    ): MaskingState {
        val updated = selected().paintSelectedComponent(
            sourceWidth, sourceHeight, x, y, radius, flow, feather
        )
        return updateSelected(updated)
    }

    fun selectMask(type: MaskType): MaskingState =
        copy(selectedMask = type, selectedBrushIndex = -1)

    fun addBrushLayer(name: String? = null): MaskingState {
        val index = brushLayers.size + 1
        val layer = MaskLayer(MaskType.BRUSH, name ?: "Brush $index")
        return copy(
            brushLayers = brushLayers + layer,
            selectedMask = MaskType.BRUSH,
            selectedBrushIndex = brushLayers.size
        )
    }

    fun selectBrush(index: Int): MaskingState =
        if (index in brushLayers.indices) copy(selectedMask = MaskType.BRUSH, selectedBrushIndex = index) else this

    fun deleteSelectedBrush(): MaskingState {
        if (selectedMask != MaskType.BRUSH || selectedBrushIndex !in brushLayers.indices) return this
        val updated = brushLayers.toMutableList().also { it.removeAt(selectedBrushIndex) }
        return copy(brushLayers = updated, selectedMask = MaskType.SUBJECT, selectedBrushIndex = -1)
    }
}
