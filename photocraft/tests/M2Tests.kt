package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.Adjustment
import com.prabhat.photocraft.edit.AdjustmentGroup
import com.prabhat.photocraft.edit.AdjustmentMath
import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.EditingState
import kotlin.system.exitProcess

private var failureCount = 0

private fun verify(name: String, condition: Boolean) {
    if (condition) {
        println("PASS  $name")
    } else {
        failureCount += 1
        println("FAIL  $name")
    }
}

private fun samplePixels(): IntArray = intArrayOf(
    0xFF808080.toInt(),
    0xFFB08050.toInt(),
    0xFF506070.toInt(),
    0xFFE8E8E8.toInt(),
    0xFF202020.toInt(),
    0xFFC03030.toInt(),
    0xFF30C060.toInt(),
    0xFF3050D0.toInt(),
    0xFF8080C0.toInt()
)

private fun testAdjustmentModel() {
    val clampedHigh = Adjustments.NEUTRAL.with(Adjustment.EXPOSURE, 250)
    val clampedLow = Adjustments.NEUTRAL.with(Adjustment.TINT, -250)
    verify("adjustments clamp high values", clampedHigh.exposure == Adjustment.MAX_VALUE)
    verify("adjustments clamp low values", clampedLow.tint == Adjustment.MIN_VALUE)

    val invalidRejected = runCatching { Adjustments(exposure = 101) }.isFailure
    verify("adjustments reject out-of-range construction", invalidRejected)
    verify(
        "adjustments reject an invalid serialized width",
        runCatching { Adjustments.fromIntArray(IntArray(10)) }.isFailure
    )
    verify(
        "adjustments reject invalid serialized values",
        runCatching { Adjustments.fromIntArray(IntArray(11) { 101 }) }.isFailure
    )

    val values = Adjustments(
        exposure = 10,
        brightness = -20,
        contrast = 30,
        highlights = -40,
        shadows = 50,
        whites = -60,
        blacks = 70,
        temperature = -80,
        tint = 90,
        saturation = -100,
        vibrance = 100
    )
    val roundTrip = Adjustments.fromIntArray(values.toIntArray())
    verify("adjustments array round trip", values == roundTrip)
    verify(
        "adjustment groups cover every control",
        Adjustment.entries.count { it.group == AdjustmentGroup.LIGHT } == 7 &&
            Adjustment.entries.count { it.group == AdjustmentGroup.COLOR } == 4
    )
    verify("adjustment enum exposes eleven controls", Adjustment.entries.size == 11)
}

private fun testAdjustmentMath() {
    val neutralPixels = samplePixels()
    val neutralCopy = neutralPixels.copyOf()
    AdjustmentMath.applyInPlace(neutralCopy, Adjustments.NEUTRAL)
    verify("neutral adjustments leave pixels unchanged", neutralPixels.contentEquals(neutralCopy))

    for (adjustment in Adjustment.entries) {
        for (value in intArrayOf(-40, 40)) {
            val base = samplePixels()
            val adjusted = samplePixels()
            AdjustmentMath.applyInPlace(
                adjusted,
                Adjustments.NEUTRAL.with(adjustment, value)
            )
            verify(
                "$adjustment at $value changes the preview",
                !base.contentEquals(adjusted)
            )

            val repeated = samplePixels()
            AdjustmentMath.applyInPlace(
                repeated,
                Adjustments.NEUTRAL.with(adjustment, value)
            )
            verify(
                "$adjustment at $value is deterministic",
                adjusted.contentEquals(repeated)
            )
        }
    }

    val alphaPixels = intArrayOf(0x80FF0000.toInt())
    AdjustmentMath.applyInPlace(
        alphaPixels,
        Adjustments.NEUTRAL.with(Adjustment.EXPOSURE, 50)
    )
    verify("adjustment math preserves alpha", alphaPixels[0] ushr 24 == 0x80)

    val transparentPixels = intArrayOf(0x00123456)
    AdjustmentMath.applyInPlace(
        transparentPixels,
        Adjustments.NEUTRAL.with(Adjustment.BRIGHTNESS, 100)
    )
    verify("fully transparent pixels stay untouched", transparentPixels[0] == 0x00123456)

    val extreme = Adjustments(
        exposure = 100,
        brightness = -100,
        contrast = 100,
        highlights = -100,
        shadows = 100,
        whites = -100,
        blacks = 100,
        temperature = -100,
        tint = 100,
        saturation = -100,
        vibrance = 100
    )
    val extremePixels = samplePixels()
    AdjustmentMath.applyInPlace(extremePixels, extreme)
    verify(
        "combined extreme values keep every pixel opaque",
        extremePixels.all { it ushr 24 == 0xFF }
    )
    val repeatedExtreme = samplePixels()
    AdjustmentMath.applyInPlace(repeatedExtreme, extreme)
    verify("combined extreme values stay deterministic", extremePixels.contentEquals(repeatedExtreme))
}

private fun testHistory() {
    val state = EditingState()
    state.beginGesture()
    state.update(Adjustment.EXPOSURE, 10)
    state.update(Adjustment.EXPOSURE, 20)
    state.endGesture()

    verify("gesture records one undo entry", state.canUndo)
    verify("gesture keeps latest value", state.adjustments.exposure == 20)
    verify("undo restores pre-gesture state", state.undo() && state.adjustments.exposure == 0)
    verify("undo consumes the coalesced entry", !state.canUndo)
    verify("redo restores gesture result", state.redo() && state.adjustments.exposure == 20)
    verify("redo consumes the entry", !state.canRedo)

    state.undo()
    state.update(Adjustment.BRIGHTNESS, 5)
    verify("new edit clears redo", !state.canRedo)

    val resetState = EditingState()
    resetState.update(Adjustment.CONTRAST, 30)
    verify("reset reports a change", resetState.reset())
    verify("reset returns neutral values", resetState.adjustments.isNeutral())
    verify("reset can be undone", resetState.undo() && resetState.adjustments.contrast == 30)

    val noOpState = EditingState()
    noOpState.beginGesture()
    noOpState.update(Adjustment.SHADOWS, 15)
    noOpState.update(Adjustment.SHADOWS, 0)
    noOpState.endGesture()
    verify("returning to the base value adds no history", !noOpState.canUndo)

    val restoredState = EditingState()
    restoredState.update(Adjustment.TEMPERATURE, 25)
    restoredState.update(Adjustment.TINT, -15)
    restoredState.undo()
    val snapshot = restoredState.snapshot()
    val restored = EditingState.restored(snapshot)
    verify("snapshot preserves current adjustments", restored.adjustments == snapshot.adjustments)
    verify("snapshot preserves undo availability", restored.canUndo == restoredState.canUndo)
    verify("snapshot preserves redo availability", restored.canRedo == restoredState.canRedo)
    verify("restored state can still undo", restored.undo())

    val limitState = EditingState()
    repeat(EditingState.HISTORY_LIMIT + 10) { index ->
        limitState.update(
            Adjustment.SATURATION,
            if (index % 2 == 0) 20 else 21
        )
    }
    var undoCount = 0
    while (limitState.undo()) {
        undoCount += 1
    }
    verify(
        "history respects its bounded depth",
        undoCount == EditingState.HISTORY_LIMIT
    )
}

fun main() {
    testAdjustmentModel()
    testAdjustmentMath()
    testHistory()

    if (failureCount > 0) {
        println("$failureCount test(s) failed")
        exitProcess(1)
    }
    println("All M2 tests passed")
}
