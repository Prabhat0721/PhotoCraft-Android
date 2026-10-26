package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.Adjustment
import com.prabhat.photocraft.edit.AdjustmentMath
import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.EditingState
import com.prabhat.photocraft.export.ExportDecision
import com.prabhat.photocraft.export.ExportGate
import com.prabhat.photocraft.export.ExportNaming
import com.prabhat.photocraft.export.ExportSettings
import com.prabhat.photocraft.export.ExportTiling
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

private fun testExportSettings() {
    verify("export uses the JPEG MIME type", ExportSettings.JPEG_MIME_TYPE == "image/jpeg")
    verify("export uses a valid JPEG quality", ExportSettings.JPEG_QUALITY in 0..100)
    verify("export requests quality 95", ExportSettings.JPEG_QUALITY == 95)
    verify("export uses the jpg extension", ExportSettings.FILE_EXTENSION == "jpg")
}

private fun testExportNaming() {
    verify(
        "export naming keeps a simple display name",
        ExportNaming.suggestedFileName("IMG_1234.JPG") == "IMG_1234.jpg"
    )
    verify(
        "export naming replaces the final extension",
        ExportNaming.suggestedFileName("vacation.photo.png") == "vacation.photo.jpg"
    )
    verify(
        "export naming removes a source directory",
        ExportNaming.suggestedFileName("/storage/emulated/0/DCIM/My Photo.jpeg") ==
            "My Photo.jpg"
    )
    verify(
        "export naming removes a Windows source directory",
        ExportNaming.suggestedFileName("C:\\photos\\trip.jpg") == "trip.jpg"
    )
    verify(
        "export naming replaces invalid file characters",
        ExportNaming.suggestedFileName("bad/name:with*chars?.jpg") == "name_with_chars_.jpg"
    )
    verify(
        "export naming replaces control characters",
        ExportNaming.suggestedFileName("line\nbreak.jpg") == "line_break.jpg"
    )
    verify(
        "export naming preserves unicode",
        ExportNaming.suggestedFileName("résumé.png") == "résumé.jpg"
    )
    verify(
        "export naming falls back for a null display name",
        ExportNaming.suggestedFileName(null) == "PhotoCraft-export.jpg"
    )
    verify(
        "export naming falls back for a blank display name",
        ExportNaming.suggestedFileName("   ") == "PhotoCraft-export.jpg"
    )
    verify(
        "export naming falls back for a dot-only display name",
        ExportNaming.suggestedFileName("...") == "PhotoCraft-export.jpg"
    )
    verify(
        "export naming falls back for an extension-only display name",
        ExportNaming.suggestedFileName(".jpg") == "PhotoCraft-export.jpg"
    )
    verify(
        "export naming removes a trailing dot",
        ExportNaming.suggestedFileName("name.") == "name.jpg"
    )

    val longName = ExportNaming.suggestedFileName("a".repeat(200) + ".jpg")
    verify(
        "export naming bounds the base name",
        longName.length == ExportSettings.MAX_BASE_NAME_LENGTH + 4
    )
    verify("export naming keeps the jpg extension", longName.endsWith(".jpg"))

    val splitSurrogate = ExportNaming.suggestedFileName(
        "a".repeat(ExportSettings.MAX_BASE_NAME_LENGTH - 1) + "😀.jpg"
    )
    verify(
        "export naming does not leave a broken surrogate",
        splitSurrogate.dropLast(4).lastOrNull()?.isHighSurrogate() != true
    )
}

private fun testExportTiling() {
    val large = ExportTiling.plan(4000, 3000)
    val maxPixels = ExportTiling.TARGET_TILE_BYTES / ExportTiling.BYTES_PER_PIXEL
    verify(
        "tiling covers every source pixel",
        large.sumOf { it.pixelCount.toLong() } == 4000L * 3000L
    )
    verify(
        "tiling keeps every tile within the byte target",
        large.all { it.pixelCount <= maxPixels }
    )
    verify(
        "tiling starts at the top-left",
        large.first().left == 0 && large.first().top == 0
    )

    val covered = BooleanArray(4000 * 3000)
    var duplicate = false
    for (tile in large) {
        for (row in tile.top until tile.top + tile.height) {
            for (column in tile.left until tile.left + tile.width) {
                val index = row * 4000 + column
                if (covered[index]) {
                    duplicate = true
                }
                covered[index] = true
            }
        }
    }
    verify("tiling produces no overlapping tiles", !duplicate)
    verify("tiling leaves no uncovered pixels", covered.all { it })

    val single = ExportTiling.plan(100, 100)
    verify(
        "tiling keeps a small image in one tile",
        single.size == 1 && single[0].pixelCount == 100 * 100
    )

    val narrow = ExportTiling.plan(2000, 1, 4000)
    verify(
        "tiling splits a wide image when the target is smaller",
        narrow.size == 2 && narrow.all { it.width == 1000 && it.height == 1 }
    )
    verify(
        "tiling preserves a one-pixel image",
        ExportTiling.plan(1, 1, 4).single().pixelCount == 1
    )
    verify(
        "tiling rejects a zero width",
        runCatching { ExportTiling.plan(0, 10) }.isFailure
    )
    verify(
        "tiling rejects a zero height",
        runCatching { ExportTiling.plan(10, 0) }.isFailure
    )
    verify(
        "tiling rejects a target smaller than one pixel",
        runCatching { ExportTiling.plan(10, 10, 3) }.isFailure
    )
}

private fun testExportGate() {
    val gate = ExportGate()
    verify("export gate starts idle", !gate.isExporting)
    verify("export gate admits the first export", gate.tryStart())
    verify("export gate reports an active export", gate.isExporting)
    verify("export gate rejects a duplicate export", !gate.tryStart())
    gate.finish()
    verify("export gate releases after completion", !gate.isExporting)
    verify("export gate admits a later export", gate.tryStart())
    gate.finish()
    gate.finish()
    verify("export gate release is idempotent", !gate.isExporting)
}

private fun testExportDecision() {
    verify(
        "export decision cancels a dismissed picker",
        ExportDecision.decide(false, true, false) == ExportDecision.CANCELLED
    )
    verify(
        "export decision requires a destination",
        ExportDecision.decide(true, false, false) ==
            ExportDecision.MISSING_DESTINATION
    )
    verify(
        "export decision blocks overwriting the source",
        ExportDecision.decide(true, true, true) == ExportDecision.SOURCE_CONFLICT
    )
    verify(
        "export decision starts a valid export",
        ExportDecision.decide(true, true, false) == ExportDecision.START
    )
    verify(
        "export decision prioritizes cancellation",
        ExportDecision.decide(false, false, true) == ExportDecision.CANCELLED
    )
}

private fun gradientPixels(width: Int, height: Int): IntArray =
    IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        val alpha = if (index % 17 == 0) 0 else 0xFF
        val red = (x * 7 + y * 3) and 0xFF
        val green = (x * 3 + y * 11) and 0xFF
        val blue = (x * 13 + y * 5) and 0xFF
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

private fun testTiledAdjustmentMath() {
    val width = 37
    val height = 23
    val adjustments = Adjustments(
        exposure = 13,
        brightness = -7,
        contrast = 22,
        highlights = 18,
        shadows = -12,
        whites = 9,
        blacks = -5,
        temperature = 14,
        tint = -9,
        saturation = 17,
        vibrance = -11
    )
    val original = gradientPixels(width, height)
    val full = original.copyOf()
    AdjustmentMath.applyInPlace(full, adjustments)

    val tiled = original.copyOf()
    for (tile in ExportTiling.plan(width, height, 16 * 4)) {
        val buffer = IntArray(tile.pixelCount)
        for (row in 0 until tile.height) {
            for (column in 0 until tile.width) {
                buffer[row * tile.width + column] =
                    tiled[(tile.top + row) * width + tile.left + column]
            }
        }
        AdjustmentMath.applyInPlace(buffer, adjustments, buffer.size)
        for (row in 0 until tile.height) {
            for (column in 0 until tile.width) {
                tiled[(tile.top + row) * width + tile.left + column] =
                    buffer[row * tile.width + column]
            }
        }
    }
    verify("tiled adjustment math matches full-image math", full.contentEquals(tiled))

    val prefix = intArrayOf(
        0xFF102030.toInt(),
        0xFF405060.toInt(),
        0xFF708090.toInt()
    )
    val untouched = prefix[2]
    AdjustmentMath.applyInPlace(
        prefix,
        Adjustments.NEUTRAL.with(Adjustment.EXPOSURE, 50),
        2
    )
    verify("pixel-count overload changes the requested prefix", prefix[0] != 0xFF102030.toInt())
    verify("pixel-count overload leaves the tail untouched", prefix[2] == untouched)

    val zeroCount = intArrayOf(0xFF112233.toInt())
    AdjustmentMath.applyInPlace(
        zeroCount,
        Adjustments.NEUTRAL.with(Adjustment.BRIGHTNESS, 100),
        0
    )
    verify("pixel-count overload accepts an empty prefix", zeroCount[0] == 0xFF112233.toInt())

    verify(
        "pixel-count overload rejects an oversized count",
        runCatching {
            AdjustmentMath.applyInPlace(IntArray(1), Adjustments.NEUTRAL, 2)
        }.isFailure
    )
    verify(
        "pixel-count overload rejects a negative count",
        runCatching {
            AdjustmentMath.applyInPlace(IntArray(1), Adjustments.NEUTRAL, -1)
        }.isFailure
    )
}

private fun testExportSnapshot() {
    val state = EditingState()
    state.update(Adjustment.EXPOSURE, 25)
    val snapshot = state.adjustments
    state.update(Adjustment.EXPOSURE, 40)
    verify(
        "export snapshot stays immutable after further edits",
        snapshot.exposure == 25 && state.adjustments.exposure == 40
    )
}

fun main() {
    testExportSettings()
    testExportNaming()
    testExportTiling()
    testExportGate()
    testExportDecision()
    testTiledAdjustmentMath()
    testExportSnapshot()

    if (failureCount > 0) {
        println("$failureCount test(s) failed")
        exitProcess(1)
    }
    println("All M3 tests passed")
}
