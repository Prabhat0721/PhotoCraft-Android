package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.ColorGradingSettings
import com.prabhat.photocraft.edit.EditState
import com.prabhat.photocraft.edit.FilterSettings
import com.prabhat.photocraft.edit.LocalMaskAdjustments
import com.prabhat.photocraft.edit.MaskCombineMode
import com.prabhat.photocraft.edit.MaskLayer
import com.prabhat.photocraft.edit.MaskSelection
import com.prabhat.photocraft.edit.MaskType
import com.prabhat.photocraft.edit.MaskingState
import com.prabhat.photocraft.edit.MaskingStateCodec
import com.prabhat.photocraft.edit.PhotoEffects
import com.prabhat.photocraft.edit.ToneGrade
import com.prabhat.photocraft.edit.ToneCurves
import com.prabhat.photocraft.preset.Preset
import com.prabhat.photocraft.preset.PresetCodec

fun main() {
    val selection = MaskSelection(3, 2, byteArrayOf(0, 16, 80, 128.toByte(), 220.toByte(), 255.toByte()))
    val layer = MaskLayer(
        type = MaskType.BRUSH,
        id = "painted-1",
        name = "Painted subject",
        selection = selection,
        enabled = true,
        inverted = true,
        retouchMode = true,
        combineMode = MaskCombineMode.ADD,
        adjustments = LocalMaskAdjustments(
            adjustments = Adjustments(exposure = 27, contrast = -13),
            filter = FilterSettings(intensity = 68),
            effects = PhotoEffects(sharpness = 45, sharpeningRadius = 3),
            colorGrading = com.prabhat.photocraft.edit.ColorGradingSettings.NEUTRAL
        )
    )
    val state = MaskingState().addLayer().let { added ->
        added.updateSelected(layer.copy(id = added.selectedLayerId))
    }.copy(brushSize = 73f, brushFeather = 32f, brushFlow = 81f)
    val encoded = MaskingStateCodec.encode(state)
    val decoded = MaskingStateCodec.decode(encoded)
    check(decoded == state) { "Mask selections and layer settings must survive codec round trip." }
    check(MaskingStateCodec.decode("broken") == null)

    val curveValues = ToneCurves.NEUTRAL.values().also { it[2] = 57 }
    val preset = Preset(
        "Mask and curve",
        EditState(
            curves = ToneCurves.fromValues(curveValues),
            colorGrading = ColorGradingSettings(global = ToneGrade(hue = 72, amount = 46)),
            masking = state
        )
    )
    val restored = PresetCodec.decode(PresetCodec.encode(preset))
    check(restored == preset) { "Preset should retain both curve settings and painted masks." }
    println("Masking state codec and preset persistence tests passed.")
}
