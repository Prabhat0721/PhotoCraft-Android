package com.prabhat.photocraft.edit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Compact, validated persistence for mask layers and their raster selections. */
object MaskingStateCodec {
    private const val MAGIC = 0x50434D32
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
                val selection = layer.selection
                out.writeBoolean(selection != null)
                if (selection != null) {
                    out.writeInt(selection.width)
                    out.writeInt(selection.height)
                    out.writeInt(selection.coverage.size)
                    out.write(selection.coverage)
                }
                val local = layer.adjustments
                val localState = EditState(
                    adjustments = local.adjustments,
                    filter = local.filter,
                    effects = local.effects,
                    colorGrading = local.colorGrading
                )
                val values = M5StateCodec.encode(localState)
                out.writeInt(values.size)
                values.forEach(out::writeInt)
            }
        }
        Base64.getEncoder().encodeToString(bytes.toByteArray())
    } catch (_: Exception) {
        ""
    }

    fun decode(encoded: String?): MaskingState? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val compressed = Base64.getDecoder().decode(encoded)
            DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed))).use { input ->
                require(input.readInt() == MAGIC)
                val selected = input.readUTF()
                val brushSize = input.readFloat().also { require(it.isFinite() && it in 1f..512f) }
                val feather = input.readFloat().also { require(it.isFinite() && it in 0f..100f) }
                val flow = input.readFloat().also { require(it.isFinite() && it in 0f..100f) }
                val count = input.readInt().also { require(it in 2..MAX_LAYERS) }
                val layers = ArrayList<MaskLayer>(count)
                repeat(count) {
                    val type = MaskType.entries.getOrNull(input.readInt()) ?: return null
                    val id = input.readUTF()
                    val name = input.readUTF()
                    val enabled = input.readBoolean()
                    val inverted = input.readBoolean()
                    val retouchMode = input.readBoolean()
                    val combine = MaskCombineMode.entries.getOrNull(input.readInt()) ?: return null
                    val selection = if (input.readBoolean()) {
                        val width = input.readInt()
                        val height = input.readInt()
                        val size = input.readInt()
                        require(width > 0 && height > 0 && size <= MAX_SELECTION_BYTES &&
                            width.toLong() * height == size.toLong())
                        MaskSelection(width, height, ByteArray(size).also(input::readFully))
                    } else null
                    val valueCount = input.readInt()
                    require(valueCount in setOf(
                        18, 53, 58, 81, 84,
                        M5StateCodec.PRE_GLOBAL_GRADE_VALUE_COUNT,
                        M5StateCodec.VALUE_COUNT
                    ))
                    val state = M5StateCodec.decode(IntArray(valueCount) { input.readInt() }) ?: return null
                    layers += MaskLayer(
                        type, id, name, selection, enabled, inverted, retouchMode, combine,
                        LocalMaskAdjustments(state.adjustments, state.filter, state.effects, state.colorGrading)
                    )
                }
                val subject = layers.firstOrNull { it.id == "subject" } ?: return null
                val background = layers.firstOrNull { it.id == "background" } ?: return null
                MaskingState(
                    subject = subject,
                    background = background,
                    extraLayers = layers.filter { it.id != "subject" && it.id != "background" },
                    selectedLayerId = selected,
                    brushSize = brushSize,
                    brushFeather = feather,
                    brushFlow = flow
                ).takeIf { it.layers.any { layer -> layer.id == selected } }
            }
        } catch (_: Exception) {
            null
        }
    }
}
