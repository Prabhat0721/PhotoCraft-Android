package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.CropSelection
import com.prabhat.photocraft.export.ExportMemoryPolicy
import com.prabhat.photocraft.export.ExportPlanner
import com.prabhat.photocraft.export.ExportSize
import com.prabhat.photocraft.export.ExportWriteGuard
import java.io.IOException
import kotlin.system.exitProcess

private var failureCount = 0
private var checkCount = 0

private fun verify(name: String, condition: Boolean) {
    checkCount += 1
    if (condition) {
        println("PASS  $name")
    } else {
        failureCount += 1
        println("FAIL  $name")
    }
}

private fun testCroppedOriginalUsesFullDecode() {
    val plan = ExportMemoryPolicy.plan(
        sourceWidth = 12000,
        sourceHeight = 9000,
        crop = CropSelection(0.1f, 0.2f, 0.8f, 0.9f),
        size = ExportSize.ORIGINAL
    )
    verify("cropped original plan exists", plan != null)
    verify(
        "cropped original uses floor and ceil pixel bounds",
        plan?.crop?.left == 1200 &&
            plan.crop.top == 1800 &&
            plan.crop.width == 8400 &&
            plan.crop.height == 6300
    )
    verify(
        "cropped original exports at crop resolution",
        plan?.output?.width == 8400 && plan.output.height == 6300
    )
    verify(
        "cropped original decodes the source without a cap",
        plan?.decodeWidth == 12000 && plan.decodeHeight == 9000
    )
    verify("cropped original is not marked reduced", plan?.reducedDecode == false)
}

private fun testExplicitSizeIsNotSilentlyCapped() {
    val plan = ExportMemoryPolicy.plan(
        sourceWidth = 60000,
        sourceHeight = 40000,
        crop = CropSelection.FULL,
        size = ExportSize.LONG_EDGE_2048
    )
    verify("large explicit-size plan exists", plan != null)
    verify(
        "large explicit size keeps the requested output edge",
        plan?.output?.width == 2048 && plan.output.height == 1365
    )
    verify("large explicit size reduces decode only as requested", plan?.reducedDecode == true)
    verify("large explicit size reports positive memory", (plan?.estimatedBytes ?: 0L) > 0L)
}

private fun testExtremeEstimateSaturates() {
    val plan = ExportMemoryPolicy.plan(
        sourceWidth = 2_000_000_000,
        sourceHeight = 2_000_000_000,
        crop = CropSelection.FULL,
        size = ExportSize.ORIGINAL
    )
    verify("extreme original is not rejected by a fixed cap", plan != null)
    verify("extreme original keeps its output dimensions",
        plan?.output?.width == 2_000_000_000 &&
            plan.output.height == 2_000_000_000)
    verify("extreme original estimate saturates instead of overflowing",
        plan?.estimatedBytes == Long.MAX_VALUE)
}

private fun testInvalidPlan() {
    verify(
        "invalid source dimensions remain invalid",
        ExportMemoryPolicy.plan(0, 9000, CropSelection.FULL, ExportSize.ORIGINAL) == null
    )
}

private fun testCropPlannerUsesPresentationBounds() {
    val crop = ExportPlanner.pixelCrop(
        sourceWidth = 4000,
        sourceHeight = 3000,
        crop = CropSelection(0.25f, 0.25f, 0.75f, 0.75f)
    )
    verify(
        "crop planner maps the visible crop bounds",
        crop.left == 1000 && crop.top == 750 &&
            crop.width == 2000 && crop.height == 1500
    )
}

private fun testCancellationRemainsAuthoritative() {
    var wrote = false
    val cancelled = ExportWriteGuard.writeIfActive(
        isCancelled = { true },
        write = {
            wrote = true
            true
        }
    )
    verify("cancelled export does not write", cancelled == ExportWriteGuard.Outcome.CANCELLED)
    verify("cancelled export leaves the write untouched", !wrote)

    val failed = ExportWriteGuard.writeIfActive(
        isCancelled = { false },
        write = { throw IOException("disk full") }
    )
    verify("export write errors remain failures", failed == ExportWriteGuard.Outcome.WRITE_FAILED)

    verify(
        "post-close cancellation remains authoritative",
        ExportWriteGuard.afterClose { true } == ExportWriteGuard.Outcome.CANCELLED
    )
}

fun main() {
    testCroppedOriginalUsesFullDecode()
    testExplicitSizeIsNotSilentlyCapped()
    testExtremeEstimateSaturates()
    testInvalidPlan()
    testCropPlannerUsesPresentationBounds()
    testCancellationRemainsAuthoritative()

    if (failureCount > 0) {
        println("$failureCount of $checkCount M5.2 test(s) failed")
        exitProcess(1)
    }
    println("All $checkCount M5.2 tests passed")
}
