package com.prabhat.photocraft.edit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Compact persistence for masks. PCM2 is accepted for masks saved before component editing. */
object MaskingStateCodec {
    private const val MAGIC_V2 = 0x50434D32
    private const val MAGIC = 0x50434D33
    private const val MAX_LAYERS = 128
    private const val MAX_SELECTION_BYTES = 16 * 1024 * 1024

    fun encode(state: MaskingState): String = try {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(DeflaterOutputStream(bytes)).use { out ->
            out.writeInt(MAGIC)
            out.writeUTF(state.selectedLayerId)
            out.writeFloat(state.brushSize)
            out.writeFloat(state.brushFeather)
            out.writeFloat(state.brushFlow)
            out.writeInt(state.layers.size)
            state.layers.forEach { layer ->
                out.writeInt(layer.type.ordinal)
                out.writeUTF(layer.id)
                out.writeUTF(layer.name)
                out.writeBoolean(layer.enabled)
                out.writeBoolean(layer.inverted)
                out.writeBoolean(layer.retouchMode)
                out.writeInt(layer.combineMode.ordinal)
                writeSelection(out, layer.selection)
                out.writeInt(layer.components.size)
                layer.components.forEach { component ->
                    out.writeUTF(component.name)
                    out.writeBoolean(component.add)
                    writeSelection(out, component.selection)
                }
                val local = layer.adjustments
                val values = M5StateCodec.encode(EditState(
                    adjustments = local.adjustments,
                    filter = local.filter,
                    effects = local.effects,
                    colorGrading = local.colorGrading
                ))
                out.writeInt(values.size)
                values.forEach(out::writeInt)
            }
        }
        Base64.getEncoder().encodeToString(bytes.toByteArray())
    } catch (_: Exception) { "" }

    private fun writeSelection(out: DataOutputStream, selection: MaskSelection?) {
        out.writeBoolean(selection != null)
        if (selection != null) {
            out.writeInt(selection.width); out.writeInt(selection.height)
            out.writeInt(selection.coverage.size); out.write(selection.coverage)
        }
    }

    private fun readSelection(input: DataInputStream): MaskSelection? = if (!input.readBoolean()) null else {
        val width = input.readInt(); val height = input.readInt(); val size = input.readInt()
        require(width > 0 && height > 0 && size <= MAX_SELECTION_BYTES && width.toLong() * height == size.toLong())
        MaskSelection(width, height, ByteArray(size).also(input::readFully))
    }

    fun decode(encoded: String?): MaskingState? {
        if (encoded.isNullOrBlank()) return null
        return try {
            DataInputStream(InflaterInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded)))).use { input ->
                val magic = input.readInt().also { require(it == MAGIC || it == MAGIC_V2) }
                val selectedId = input.readUTF()
                val brushSize = input.readFloat().also { require(it.isFinite() && it in 1f..512f) }
                val feather = input.readFloat().also { require(it.isFinite() && it in 0f..100f) }
                val flow = input.readFloat().also { require(it.isFinite() && it in 0f..100f) }
                val count = input.readInt().also { require(it in 2..MAX_LAYERS) }
                val layers = ArrayList<MaskLayer>(count)
                repeat(count) {
                    val type = MaskType.entries.getOrNull(input.readInt()) ?: return null
                    val id = input.readUTF(); val name = input.readUTF()
                    val enabled = input.readBoolean(); val inverted = input.readBoolean()
                    val retouch = input.readBoolean()
                    val combine = MaskCombineMode.entries.getOrNull(input.readInt()) ?: return null
                    val selection = readSelection(input)
                    val components = if (magic == MAGIC) {
                        val componentCount = input.readInt().also { require(it in 0..MAX_LAYERS) }
                        List(componentCount) {
                            val componentName = input.readUTF(); val add = input.readBoolean()
                            MaskComponent(componentName, add, requireNotNull(readSelection(input)))
                        }
                    } else emptyList()
                    val valueCount = input.readInt().also { require(it in setOf(18, 53, 58, 81, 84, M5StateCodec.PRE_GLOBAL_GRADE_VALUE_COUNT, M5StateCodec.VALUE_COUNT)) }
                    val edit = M5StateCodec.decode(IntArray(valueCount) { input.readInt() }) ?: return null
                    layers += MaskLayer(
                        type = type, name = name, id = id, selection = selection,
                        components = components, enabled = enabled, inverted = inverted,
                        retouchMode = retouch, combineMode = combine,
                        adjustments = LocalMaskAdjustments(edit.adjustments, edit.filter, edit.effects, edit.colorGrading)
                    )
                }
                val subject = layers.firstOrNull { it.id == "subject" || it.type == MaskType.SUBJECT }
                    ?: return null
                val background = layers.firstOrNull { it.id == "background" || it.type == MaskType.BACKGROUND }
                    ?: return null
                val extras = layers.filter { it !== subject && it !== background }
                val selected = layers.firstOrNull { it.id == selectedId } ?: subject
                val isBrush = selected !== subject && selected !== background
                val state = MaskingState(
                    subject = subject, background = background, brushLayers = extras,
                    selectedMask = selected.type,
                    selectedBrushIndex = if (isBrush) extras.indexOfFirst { it.id == selected.id } else -1,
                    brushSize = brushSize, brushFeather = feather, brushFlow = flow
                )
                state
            }
        } catch (_: Exception) { null }
    }
}
