package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.Adjustment
import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.ComparisonMode
import com.prabhat.photocraft.edit.CropGeometry
import com.prabhat.photocraft.edit.CropRatio
import com.prabhat.photocraft.edit.CropSelection
import com.prabhat.photocraft.edit.EditState
import com.prabhat.photocraft.edit.EditStateCodec
import com.prabhat.photocraft.edit.EditingState
import com.prabhat.photocraft.export.ExportPlanner
import com.prabhat.photocraft.export.ExportQuality
import com.prabhat.photocraft.export.ExportRequest
import com.prabhat.photocraft.export.ExportSettings
import com.prabhat.photocraft.export.ExportSize
import com.prabhat.photocraft.export.OutputDimensions
import com.prabhat.photocraft.export.PixelCrop
import com.prabhat.photocraft.util.RequestSequence
import kotlin.math.abs
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

private fun close(actual: Float, expected: Float, tolerance: Float = 0.0001f): Boolean =
    abs(actual - expected) <= tolerance

private fun testCropModel() {
    verify("full crop reports full bounds", CropSelection.FULL.isFull())
    verify(
        "crop rejects a negative origin",
        runCatching { CropSelection(-0.01f, 0f, 1f, 1f) }.isFailure
    )
    verify(
        "crop rejects bounds beyond the image",
        runCatching { CropSelection(0f, 0f, 1.01f, 1f) }.isFailure
    )
    verify(
        "crop rejects zero width",
        runCatching { CropSelection(0.5f, 0f, 0.5f, 1f) }.isFailure
    )
    verify(
        "crop rejects zero height",
        runCatching { CropSelection(0f, 0.5f, 1f, 0.5f) }.isFailure
    )
    verify(
        "crop rejects non-finite values",
        runCatching { CropSelection(Float.NaN, 0f, 1f, 1f) }.isFailure
    )

    val decoded = CropSelection.fromBasisPoints(1250, 0, 8750, 10000)
    verify("crop decodes basis points", close(decoded.left, 0.125f) &&
        close(decoded.top, 0f) &&
        close(decoded.right, 0.875f) &&
        close(decoded.bottom, 1f))
    verify(
        "crop rejects invalid basis points",
        runCatching { CropSelection.fromBasisPoints(10000, 0, 0, 10000) }.isFailure
    )

    verify("free ratio has no fixed value", CropRatio.FREE.widthToHeight == null)
    verify("square ratio is 1:1", CropRatio.SQUARE.widthToHeight == 1f)
    verify(
        "four-five ratio is 0.8",
        CropRatio.FOUR_FIVE.widthToHeight?.let { close(it, 0.8f) } == true
    )
    verify(
        "nine-sixteen ratio is 0.5625",
        CropRatio.NINE_SIXTEEN.widthToHeight?.let { close(it, 0.5625f) } == true
    )
    verify(
        "sixteen-nine ratio is 16/9",
        CropRatio.SIXTEEN_NINE.widthToHeight?.let { close(it, 16f / 9f) } == true
    )
}

private fun testCropGeometry() {
    verify(
        "centered free crop is the full image",
        CropGeometry.centered(CropRatio.FREE, 4000, 3000) == CropSelection.FULL
    )

    val landscapeSquare = CropGeometry.centered(CropRatio.SQUARE, 4000, 3000)
    verify(
        "landscape square crop uses the image height",
        close(landscapeSquare.left, 0.125f) &&
            close(landscapeSquare.top, 0f) &&
            close(landscapeSquare.right, 0.875f) &&
            close(landscapeSquare.bottom, 1f)
    )

    val portraitSquare = CropGeometry.centered(CropRatio.SQUARE, 3000, 4000)
    verify(
        "portrait square crop uses the image width",
        close(portraitSquare.left, 0f) &&
            close(portraitSquare.top, 0.125f) &&
            close(portraitSquare.right, 1f) &&
            close(portraitSquare.bottom, 0.875f)
    )

    val fourFive = CropGeometry.centered(CropRatio.FOUR_FIVE, 4000, 3000)
    verify(
        "four-five crop is centered horizontally",
        close(fourFive.left, 0.2f) &&
            close(fourFive.top, 0f) &&
            close(fourFive.right, 0.8f) &&
            close(fourFive.bottom, 1f)
    )

    val wide = CropGeometry.centered(CropRatio.SIXTEEN_NINE, 4000, 3000)
    verify(
        "sixteen-nine crop is centered vertically",
        close(wide.left, 0f) &&
            close(wide.top, 0.125f) &&
            close(wide.right, 1f) &&
            close(wide.bottom, 0.875f)
    )

    val tall = CropGeometry.centered(CropRatio.NINE_SIXTEEN, 3000, 4000)
    verify(
        "nine-sixteen crop is centered horizontally",
        close(tall.left, 0.125f) &&
            close(tall.top, 0f) &&
            close(tall.right, 0.875f) &&
            close(tall.bottom, 1f)
    )

    val free = CropGeometry.fromControls(
        ratio = CropRatio.FREE,
        sourceWidth = 4000,
        sourceHeight = 3000,
        widthFraction = 0.5f,
        heightFraction = 0.4f,
        centerX = 0.5f,
        centerY = 0.5f
    )
    verify(
        "free crop controls size and center",
        close(free.left, 0.25f) &&
            close(free.top, 0.3f) &&
            close(free.right, 0.75f) &&
            close(free.bottom, 0.7f)
    )

    val clamped = CropGeometry.fromControls(
        ratio = CropRatio.FREE,
        sourceWidth = 4000,
        sourceHeight = 3000,
        widthFraction = 1f,
        heightFraction = 0.5f,
        centerX = 0f,
        centerY = 1f
    )
    verify(
        "free crop keeps bounds inside the image",
        close(clamped.left, 0f) &&
            close(clamped.top, 0.5f) &&
            close(clamped.right, 1f) &&
            close(clamped.bottom, 1f)
    )

    val halfSquare = CropGeometry.fromControls(
        ratio = CropRatio.SQUARE,
        sourceWidth = 4000,
        sourceHeight = 3000,
        widthFraction = 0.5f,
        heightFraction = 1f,
        centerX = 0.5f,
        centerY = 0.5f
    )
    verify(
        "fixed ratio ignores the height control",
        close(halfSquare.width, 0.375f) &&
            close(halfSquare.height, 0.5f) &&
            close(halfSquare.left, 0.3125f) &&
            close(halfSquare.top, 0.25f)
    )

    verify("minimum size maps to zero progress", CropGeometry.sizeProgress(0.05f) == 0)
    verify("full size maps to maximum progress", CropGeometry.sizeProgress(1f) == 1000)
    verify(
        "mid progress maps to a larger fraction",
        close(CropGeometry.fractionFromProgress(500), 0.525f)
    )
    verify(
        "full-span position is centered",
        CropGeometry.positionProgress(0.5f, 1f) == 500 &&
            close(CropGeometry.centerFromProgress(500, 1f), 0.5f)
    )
    verify(
        "position progress maps to the available center range",
        CropGeometry.positionProgress(0.25f, 0.5f) == 0 &&
            CropGeometry.positionProgress(0.75f, 0.5f) == 1000 &&
            close(CropGeometry.centerFromProgress(0, 0.5f), 0.25f) &&
            close(CropGeometry.centerFromProgress(1000, 0.5f), 0.75f)
    )
    verify(
        "crop geometry rejects invalid source dimensions",
        runCatching { CropGeometry.centered(CropRatio.SQUARE, 0, 100) }.isFailure
    )
}

private fun testExportOptions() {
    verify("high quality maps to 95", ExportQuality.HIGH.jpegQuality == 95)
    verify("standard quality maps to 85", ExportQuality.STANDARD.jpegQuality == 85)
    verify(
        "smaller-file quality maps to 70",
        ExportQuality.SMALLER_FILE.jpegQuality == 70
    )
    verify(
        "quality values stay valid",
        ExportQuality.entries.all { it.jpegQuality in 0..100 }
    )
    verify(
        "legacy export quality remains 95",
        ExportSettings.JPEG_QUALITY == 95
    )
    verify("original size has no scaling", ExportSize.ORIGINAL.percent == null &&
        ExportSize.ORIGINAL.longEdgePx == null)
    verify("percent sizes expose their percentage", ExportSize.PERCENT_75.percent == 75 &&
        ExportSize.PERCENT_50.percent == 50 &&
        ExportSize.PERCENT_25.percent == 25)
    verify("long-edge sizes expose their bound", ExportSize.LONG_EDGE_2048.longEdgePx == 2048 &&
        ExportSize.LONG_EDGE_1080.longEdgePx == 1080)
}

private fun testExportPlanner() {
    val fullOriginal = ExportPlanner.plan(
        4000,
        3000,
        CropSelection.FULL,
        ExportSize.ORIGINAL
    )
    verify(
        "original export keeps full dimensions",
        fullOriginal.crop.left == 0 &&
            fullOriginal.crop.top == 0 &&
            fullOriginal.crop.width == 4000 &&
            fullOriginal.crop.height == 3000 &&
            fullOriginal.output == OutputDimensions(4000, 3000)
    )

    val quarterCrop = CropSelection(0.25f, 0.25f, 0.75f, 0.75f)
    val croppedOriginal = ExportPlanner.plan(
        4000,
        3000,
        quarterCrop,
        ExportSize.ORIGINAL
    )
    verify(
        "crop maps to exact pixel bounds",
        croppedOriginal.crop.left == 1000 &&
            croppedOriginal.crop.top == 750 &&
            croppedOriginal.crop.width == 2000 &&
            croppedOriginal.crop.height == 1500
    )
    verify(
        "crop output keeps crop dimensions",
        croppedOriginal.output == OutputDimensions(2000, 1500)
    )

    verify(
        "75 percent scales both dimensions",
        ExportPlanner.plan(4000, 3000, CropSelection.FULL, ExportSize.PERCENT_75).output ==
            OutputDimensions(3000, 2250)
    )
    verify(
        "50 percent scales both dimensions",
        ExportPlanner.plan(4000, 3000, CropSelection.FULL, ExportSize.PERCENT_50).output ==
            OutputDimensions(2000, 1500)
    )
    verify(
        "25 percent scales both dimensions",
        ExportPlanner.plan(4000, 3000, CropSelection.FULL, ExportSize.PERCENT_25).output ==
            OutputDimensions(1000, 750)
    )
    verify(
        "long edge 2048 preserves aspect ratio",
        ExportPlanner.plan(
            4000,
            3000,
            CropSelection.FULL,
            ExportSize.LONG_EDGE_2048
        ).output == OutputDimensions(2048, 1536)
    )
    verify(
        "long edge 1080 preserves aspect ratio",
        ExportPlanner.plan(
            4000,
            3000,
            CropSelection.FULL,
            ExportSize.LONG_EDGE_1080
        ).output == OutputDimensions(1080, 810)
    )
    verify(
        "long edge works for portrait images",
        ExportPlanner.plan(
            3000,
            4000,
            CropSelection.FULL,
            ExportSize.LONG_EDGE_1080
        ).output == OutputDimensions(810, 1080)
    )
    verify(
        "long edge does not upscale",
        ExportPlanner.plan(
            800,
            600,
            CropSelection.FULL,
            ExportSize.LONG_EDGE_2048
        ).output == OutputDimensions(800, 600)
    )
    verify(
        "size applies after crop",
        ExportPlanner.plan(
            4000,
            3000,
            quarterCrop,
            ExportSize.PERCENT_50
        ).output == OutputDimensions(1000, 750)
    )
    verify(
        "planner keeps at least one output pixel",
        ExportPlanner.plan(
            10,
            10,
            CropSelection(0.01f, 0.01f, 0.02f, 0.02f),
            ExportSize.PERCENT_25
        ).output == OutputDimensions(1, 1)
    )
    verify(
        "planner rejects invalid source dimensions",
        runCatching {
            ExportPlanner.plan(0, 100, CropSelection.FULL, ExportSize.ORIGINAL)
        }.isFailure
    )
    verify(
        "output dimensions reject zero",
        runCatching { OutputDimensions(0, 10) }.isFailure
    )
    verify(
        "pixel crop rejects zero width",
        runCatching { PixelCrop(0, 0, 0, 10) }.isFailure
    )
}

private fun testEditStateCodec() {
    val state = EditState(
        adjustments = Adjustments(exposure = 12, tint = -8, vibrance = 20),
        crop = CropSelection(0.125f, 0.25f, 0.875f, 0.75f),
        cropRatio = CropRatio.FOUR_FIVE
    )
    val encoded = EditStateCodec.encode(state)
    verify("codec writes sixteen integers", encoded.size == EditStateCodec.VALUE_COUNT)
    val decoded = EditStateCodec.decode(encoded)
    verify("codec round-trips adjustments", decoded?.adjustments == state.adjustments)
    verify("codec round-trips ratio", decoded?.cropRatio == state.cropRatio)
    verify(
        "codec round-trips crop",
        decoded?.crop?.let {
            close(it.left, state.crop.left) &&
                close(it.top, state.crop.top) &&
                close(it.right, state.crop.right) &&
                close(it.bottom, state.crop.bottom)
        } == true
    )
    verify(
        "codec rejects an invalid width",
        EditStateCodec.decode(IntArray(EditStateCodec.VALUE_COUNT - 1)) == null
    )
    val invalidRatio = encoded.copyOf()
    invalidRatio[15] = CropRatio.entries.size + 1
    verify("codec rejects an invalid ratio", EditStateCodec.decode(invalidRatio) == null)
    val invalidCrop = encoded.copyOf()
    invalidCrop[11] = 9000
    invalidCrop[13] = 1000
    verify("codec rejects inverted crop bounds", EditStateCodec.decode(invalidCrop) == null)

    val stack = listOf(
        state,
        EditState(
            adjustments = Adjustments(contrast = 30),
            crop = CropSelection(0.2f, 0.2f, 0.8f, 0.8f),
            cropRatio = CropRatio.SQUARE
        )
    )
    val decodedStack = EditStateCodec.decodeStack(EditStateCodec.encodeStack(stack))
    verify("codec round-trips an undo stack", decodedStack.size == 2)
    verify(
        "codec preserves stack order",
        decodedStack.firstOrNull()?.adjustments == state.adjustments &&
            decodedStack.lastOrNull()?.cropRatio == CropRatio.SQUARE
    )
    verify(
        "codec rejects a partial stack",
        EditStateCodec.decodeStack(IntArray(EditStateCodec.VALUE_COUNT - 1)).isEmpty()
    )
    verify(
        "codec decodes legacy adjustments",
        EditStateCodec.decodeAdjustments(Adjustments(exposure = 7).toIntArray())?.exposure == 7
    )
    verify(
        "codec rejects invalid legacy adjustments",
        EditStateCodec.decodeAdjustments(IntArray(11) { 101 }) == null
    )
}

private fun testEditingStateM4() {
    val state = EditingState()
    verify("new editing state is neutral", !state.isModified && !state.canUndo)

    val crop = CropSelection(0.2f, 0.2f, 0.8f, 0.8f)
    verify("crop update reports a change", state.updateCrop(crop, CropRatio.FREE))
    verify("crop update modifies the state", state.isModified && state.crop == crop)
    verify("crop update records undo", state.canUndo)
    verify("crop update clears redo", !state.canRedo)
    verify("crop update can be undone", state.undo() && state.crop == CropSelection.FULL)
    verify("crop update can be redone", state.redo() && state.crop == crop)

    val ratioState = EditingState()
    verify(
        "ratio-only change is a modification",
        ratioState.updateCrop(CropSelection.FULL, CropRatio.SQUARE) &&
            ratioState.isModified
    )
    verify(
        "ratio-only change can be undone",
        ratioState.undo() && ratioState.cropRatio == CropRatio.FREE
    )

    val gestureState = EditingState()
    gestureState.beginGesture()
    gestureState.updateCrop(CropSelection(0.1f, 0.1f, 0.9f, 0.9f), CropRatio.FREE)
    gestureState.updateCrop(CropSelection(0.2f, 0.2f, 0.8f, 0.8f), CropRatio.FREE)
    gestureState.endGesture()
    verify("crop gesture coalesces history", gestureState.canUndo)
    verify(
        "crop gesture keeps the latest crop",
        gestureState.crop == CropSelection(0.2f, 0.2f, 0.8f, 0.8f)
    )
    verify(
        "crop gesture undo restores the full image",
        gestureState.undo() && gestureState.crop == CropSelection.FULL
    )

    val resetState = EditingState()
    resetState.update(Adjustment.EXPOSURE, 20)
    resetState.updateCrop(CropSelection(0.1f, 0.1f, 0.9f, 0.9f), CropRatio.SQUARE)
    verify("combined reset reports a change", resetState.reset())
    verify(
        "combined reset clears adjustments, crop, and ratio",
        resetState.adjustments.isNeutral() &&
            resetState.crop == CropSelection.FULL &&
            resetState.cropRatio == CropRatio.FREE
    )
    verify(
        "combined reset restores the previous crop",
        resetState.undo() &&
            resetState.cropRatio == CropRatio.SQUARE &&
            resetState.adjustments.exposure == 20
    )

    val historyState = EditingState()
    historyState.update(Adjustment.CONTRAST, 25)
    historyState.updateCrop(CropSelection(0.25f, 0.25f, 0.75f, 0.75f), CropRatio.FREE)
    verify(
        "combined history undoes crop first",
        historyState.undo() &&
            historyState.crop == CropSelection.FULL &&
            historyState.adjustments.contrast == 25
    )
    verify(
        "combined history then undoes adjustments",
        historyState.undo() && historyState.adjustments.contrast == 0
    )

    val snapshotState = EditingState()
    snapshotState.updateCrop(CropSelection(0.3f, 0.2f, 0.7f, 0.8f), CropRatio.FOUR_FIVE)
    snapshotState.update(Adjustment.SHADOWS, 15)
    val snapshot = snapshotState.snapshot()
    verify("snapshot exposes current adjustments", snapshot.adjustments.shadows == 15)
    verify("snapshot exposes current crop", snapshot.crop == CropSelection(0.3f, 0.2f, 0.7f, 0.8f))
    verify("snapshot exposes current ratio", snapshot.cropRatio == CropRatio.FOUR_FIVE)
    val restored = EditingState.restored(snapshot)
    verify(
        "restored state preserves crop and ratio",
        restored.crop == snapshot.crop && restored.cropRatio == snapshot.cropRatio
    )
    verify("restored state preserves history", restored.canUndo)

    val noOpState = EditingState()
    noOpState.updateCrop(CropSelection.FULL, CropRatio.FREE)
    verify("no-op crop update is ignored", !noOpState.canUndo)
}

private fun testComparisonMode() {
    verify("comparison defaults to after", ComparisonMode.entries.first() == ComparisonMode.AFTER)
    verify("comparison toggles to before", ComparisonMode.AFTER.toggled() == ComparisonMode.BEFORE)
    verify("comparison toggles back to after", ComparisonMode.BEFORE.toggled() == ComparisonMode.AFTER)
}

private fun testRequestSequence() {
    val sequence = RequestSequence()
    verify("request sequence starts at zero", sequence.current() == 0 && sequence.isCurrent(0))
    val first = sequence.next()
    verify("request sequence issues the first request", first == 1 && sequence.isCurrent(first))
    verify("request sequence rejects an old request", !sequence.isCurrent(0))
    sequence.invalidate()
    verify("invalidation rejects the active request", !sequence.isCurrent(first))
    verify("invalidation advances the generation", sequence.current() == 2)
    verify("request sequence issues a later request", sequence.next() == 3)
}

private fun testExportRequestImmutability() {
    val editingState = EditingState()
    editingState.update(Adjustment.EXPOSURE, 10)
    editingState.updateCrop(CropSelection(0.1f, 0.1f, 0.9f, 0.9f), CropRatio.SQUARE)
    val request = ExportRequest(
        adjustments = editingState.adjustments,
        crop = editingState.crop,
        quality = ExportQuality.STANDARD,
        outputSize = ExportSize.LONG_EDGE_1080
    )
    editingState.update(Adjustment.EXPOSURE, 50)
    editingState.updateCrop(CropSelection.FULL, CropRatio.FREE)
    verify("export request keeps its adjustments", request.adjustments.exposure == 10)
    verify(
        "export request keeps its crop",
        request.crop == CropSelection(0.1f, 0.1f, 0.9f, 0.9f)
    )
    verify("export request keeps its quality", request.quality == ExportQuality.STANDARD)
    verify("export request keeps its output size", request.outputSize == ExportSize.LONG_EDGE_1080)
}

fun main() {
    testCropModel()
    testCropGeometry()
    testExportOptions()
    testExportPlanner()
    testEditStateCodec()
    testEditingStateM4()
    testComparisonMode()
    testRequestSequence()
    testExportRequestImmutability()

    if (failureCount > 0) {
        println("$failureCount test(s) failed")
        exitProcess(1)
    }
    println("All M4 tests passed")
}
