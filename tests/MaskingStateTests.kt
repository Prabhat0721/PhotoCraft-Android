package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.MaskCombineMode
import com.prabhat.photocraft.edit.MaskLayer
import com.prabhat.photocraft.edit.MaskSelection
import com.prabhat.photocraft.edit.MaskType
import com.prabhat.photocraft.edit.MaskingState

private var failures = 0

private fun verify(name: String, condition: Boolean) {
    if (condition) println("PASS  $name") else {
        failures++
        println("FAIL  $name")
    }
}

private fun mask(vararg coverage: Int) = MaskSelection(
    width = coverage.size,
    height = 1,
    coverage = ByteArray(coverage.size) { coverage[it].toByte() }
)

private fun values(selection: MaskSelection?): List<Int> =
    selection?.coverage?.map { it.toInt() and 0xFF }.orEmpty()

fun main() {
    val samePixelsA = MaskSelection(2, 1, byteArrayOf(12, 34))
    val samePixelsB = MaskSelection(2, 1, byteArrayOf(12, 34))
    verify("mask selections compare coverage by pixel value", samePixelsA == samePixelsB)
    verify("equal mask selections share a hash code", samePixelsA.hashCode() == samePixelsB.hashCode())

    val initial = MaskingState()
    verify("default stack has subject and background", initial.layers.size == 2)
    verify("default active layer is subject", initial.selected().type == MaskType.SUBJECT)

    val added = initial.addLayer()
    verify("adding a mask appends one layer", added.layers.size == 3)
    verify("new mask becomes active", added.selected().type == MaskType.BRUSH)

    val painted = added.updateSelected(added.selected().copy(selection = mask(0, 128, 255)))
    val duplicated = painted.duplicateSelected()
    verify("duplicate appends a layer", duplicated.layers.size == 4)
    verify("duplicate receives a different identity", duplicated.layers.last().id != painted.selected().id)
    verify("duplicate copies its selection", values(duplicated.selected().selection) == listOf(0, 128, 255))

    val deleted = duplicated.deleteSelected()
    verify("deleting an added layer removes it", deleted.layers.size == 3)
    verify("delete selects a remaining layer", deleted.layers.any { it.id == deleted.selectedLayerId })

    val source = mask(200, 100)
    val operand = mask(50, 150)
    fun combine(mode: MaskCombineMode, inverted: Boolean = false): List<Int> {
        val state = initial.copy(
            subject = initial.subject.copy(selection = source),
            background = initial.background.copy(
                selection = operand,
                combineMode = mode,
                inverted = inverted
            ),
            selectedLayerId = initial.background.id
        )
        return values(state.combineSelectedWithPrevious().background.selection)
    }

    verify("replace uses the selected mask", combine(MaskCombineMode.REPLACE) == listOf(50, 150))
    verify("add takes the maximum coverage", combine(MaskCombineMode.ADD) == listOf(200, 150))
    verify("subtract removes selected coverage", combine(MaskCombineMode.SUBTRACT) == listOf(150, 0))
    verify("intersect keeps shared coverage", combine(MaskCombineMode.INTERSECT) == listOf(50, 100))
    verify("combine applies inversion first", combine(MaskCombineMode.INTERSECT, inverted = true) == listOf(200, 100))
    verify("combined mask resets to replace mode", run {
        val state = initial.copy(
            subject = initial.subject.copy(selection = source),
            background = initial.background.copy(selection = operand, combineMode = MaskCombineMode.ADD),
            selectedLayerId = initial.background.id
        ).combineSelectedWithPrevious()
        state.background.combineMode == MaskCombineMode.REPLACE && !state.background.inverted
    })

    val brush = MaskLayer(MaskType.BRUSH).paint(
        sourceWidth = 5,
        sourceHeight = 5,
        x = 2f,
        y = 2f,
        radius = 1f,
        flow = 100,
        feather = 0
    )
    verify("brush creates a selection", brush.selection != null)
    verify("brush paints the touched pixel", brush.selection?.coverageAt(2, 2) == 255)
    verify("brush leaves distant pixels clear", brush.selection?.coverageAt(0, 0) == 0)

    val clearedDefault = initial.deleteSelected()
    verify("deleting a built-in mask clears its selection", clearedDefault.subject.selection == null)
    verify("clearing a built-in mask preserves the layer", clearedDefault.layers.size == 2)

    println("Failures: $failures")
    if (failures > 0) error("Masking state tests failed")
    println("All masking state tests passed")
}
