package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.EditingState
import com.prabhat.photocraft.edit.PhotoEffects

private var failures = 0

private fun verify(name: String, condition: Boolean) {
    if (condition) {
        println("PASS  $name")
    } else {
        failures++
        println("FAIL  $name")
    }
}

fun main() {
    val editing = EditingState()
    val effects = PhotoEffects(
        sharpness = 35,
        texture = -20,
        details = 45,
        noiseReduction = 15,
        vignette = -30
    )

    verify(
        "initial effects are neutral",
        editing.state.effects == PhotoEffects.NEUTRAL
    )

    verify(
        "effects update changes state",
        editing.updateEffects(effects)
    )

    verify(
        "updated effects are stored",
        editing.state.effects == effects
    )

    verify("effects update creates undo entry", editing.canUndo)

    verify("undo succeeds", editing.undo())

    verify(
        "undo restores neutral effects",
        editing.state.effects == PhotoEffects.NEUTRAL
    )

    verify("redo succeeds", editing.redo())

    verify(
        "redo restores changed effects",
        editing.state.effects == effects
    )

    val restored = EditingState.restored(editing.snapshot())

    verify(
        "snapshot restore preserves effects",
        restored.state.effects == effects
    )

    verify("restored state can undo", restored.undo())

    verify(
        "restored undo preserves neutral effects",
        restored.state.effects == PhotoEffects.NEUTRAL
    )

    println("Failures: $failures")
    if (failures > 0) error("Effects history tests failed")
    println("All effects history tests passed")
}
