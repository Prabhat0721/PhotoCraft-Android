package com.prabhat.photocraft.tests

import com.prabhat.photocraft.edit.Adjustment
import com.prabhat.photocraft.edit.EditingState
import com.prabhat.photocraft.edit.Filter
import com.prabhat.photocraft.edit.FilterSettings
import com.prabhat.photocraft.export.DestinationAttestation
import com.prabhat.photocraft.export.DestinationCleanup
import com.prabhat.photocraft.export.DestinationCreation
import com.prabhat.photocraft.export.ExportWriteGuard
import com.prabhat.photocraft.util.PhotoRestore
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

private fun attestation(
    fromCreateAction: Boolean = true,
    requestedTitle: String? = "photo.jpg",
    observedTitle: String? = "photo.jpg",
    documentId: String? = "doc-1",
    lastModifiedMs: Long? = 1_100L,
    pickerStartedAtMs: Long = 1_000L,
    nowMs: Long = 2_000L,
    preWriteSize: Long? = 0L,
    isContentDocument: Boolean = true
): DestinationAttestation = DestinationCreation.establish(
    fromCreateAction = fromCreateAction,
    requestedTitle = requestedTitle,
    observedTitle = observedTitle,
    documentId = documentId,
    lastModifiedMs = lastModifiedMs,
    pickerStartedAtMs = pickerStartedAtMs,
    nowMs = nowMs,
    preWriteSize = preWriteSize,
    isContentDocument = isContentDocument
)

private fun testWriteGuard() {
    var writes = 0
    verify(
        "write guard reports success",
        ExportWriteGuard.writeIfActive({ false }) {
            writes += 1
            true
        } == ExportWriteGuard.Outcome.SUCCESS
    )
    verify("write guard invokes the write once", writes == 1)

    verify(
        "write guard maps a false write to failure",
        ExportWriteGuard.writeIfActive({ false }) { false } ==
            ExportWriteGuard.Outcome.WRITE_FAILED
    )
    verify(
        "write guard maps IOException to failure",
        ExportWriteGuard.writeIfActive({ false }) {
            throw IOException("simulated")
        } == ExportWriteGuard.Outcome.WRITE_FAILED
    )

    var beforeCancelledWrite = false
    verify(
        "write guard skips work when cancelled before opening",
        ExportWriteGuard.writeIfActive({ true }) {
            beforeCancelledWrite = true
            true
        } == ExportWriteGuard.Outcome.CANCELLED
    )
    verify("cancelled write is not attempted", !beforeCancelledWrite)

    var cancelDuringWrite = false
    verify(
        "write guard reports cancellation after the write",
        ExportWriteGuard.writeIfActive({ false }) {
            cancelDuringWrite = true
            true
        } == ExportWriteGuard.Outcome.SUCCESS
    )
    verify("write guard does not invent cancellation", cancelDuringWrite)

    var cancel = false
    val during = ExportWriteGuard.writeIfActive({ cancel }) {
        cancel = true
        true
    }
    verify(
        "write guard observes cancellation during the write",
        during == ExportWriteGuard.Outcome.CANCELLED
    )

    verify(
        "write guard accepts a clean close",
        ExportWriteGuard.afterClose { false } == ExportWriteGuard.Outcome.SUCCESS
    )
    verify(
        "write guard rejects a cancelled close",
        ExportWriteGuard.afterClose { true } == ExportWriteGuard.Outcome.CANCELLED
    )
}

private fun testDestinationCreation() {
    val valid = attestation()
    verify("creation attestation accepts a fresh empty document", valid.established)
    verify("creation attestation keeps the document id", valid.documentId == "doc-1")
    verify("creation attestation keeps the pre-write size", valid.preWriteSize == 0L)
    verify("creation attestation records content backing", valid.isContentDocument)
    verify("unattested value is not established", !DestinationAttestation.NONE.established)

    verify(
        "creation attestation requires the create action",
        !attestation(fromCreateAction = false).established
    )
    verify(
        "creation attestation rejects a different display name",
        !attestation(observedTitle = "photo-1.jpg").established
    )
    verify(
        "creation attestation matches names exactly",
        !attestation(observedTitle = "Photo.jpg").established
    )
    verify(
        "creation attestation requires an observed name",
        !attestation(observedTitle = null).established
    )
    verify(
        "creation attestation requires a requested name",
        !attestation(requestedTitle = null).established
    )
    verify(
        "creation attestation rejects a null document id",
        !attestation(documentId = null).established
    )
    verify(
        "creation attestation rejects a blank document id",
        !attestation(documentId = "   ").established
    )
    verify(
        "creation attestation requires a last-modified value",
        !attestation(lastModifiedMs = null).established
    )
    verify(
        "creation attestation rejects a document older than the picker",
        !attestation(lastModifiedMs = 999L).established
    )
    verify(
        "creation attestation accepts a timestamp at picker start",
        attestation(lastModifiedMs = 1_000L).established
    )
    verify(
        "creation attestation rejects a future timestamp beyond skew",
        !attestation(
            lastModifiedMs = 2_000L + DestinationCreation.MAX_CLOCK_SKEW_MS + 1L
        ).established
    )
    verify(
        "creation attestation accepts a timestamp at the skew boundary",
        attestation(
            lastModifiedMs = 2_000L + DestinationCreation.MAX_CLOCK_SKEW_MS
        ).established
    )
    verify(
        "creation attestation requires a picker start time",
        !attestation(pickerStartedAtMs = 0L).established
    )
    verify(
        "creation attestation requires an empty document",
        !attestation(preWriteSize = 1L).established
    )
    verify(
        "creation attestation rejects an unknown pre-write size",
        !attestation(preWriteSize = null).established
    )
    verify(
        "creation attestation requires content backing",
        !attestation(isContentDocument = false).established
    )
}

private fun testDestinationCleanupPlan() {
    val valid = attestation()
    val allowed = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify("cleanup allows an attested empty destination", allowed.shouldDelete)
    verify("cleanup allowed decision has no skip reason", allowed.skipReason == null)

    val unattested = DestinationCleanup.plan(
        attestation = DestinationAttestation.NONE,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify(
        "cleanup skips without attestation",
        !unattested.shouldDelete &&
            unattested.skipReason == DestinationCleanup.SkipReason.NOT_ATTESTED
    )

    val notEstablished = DestinationCleanup.plan(
        attestation = valid.copy(established = false),
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify(
        "cleanup skips an unestablished attestation",
        !notEstablished.shouldDelete
    )

    val unknownInitial = DestinationCleanup.plan(
        attestation = valid.copy(preWriteSize = null),
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify(
        "cleanup skips an unknown original size",
        !unknownInitial.shouldDelete
    )

    val nonContent = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = false,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify(
        "cleanup skips a non-content destination",
        !nonContent.shouldDelete &&
            nonContent.skipReason == DestinationCleanup.SkipReason.NOT_CONTENT_DOCUMENT
    )

    val sourceConflict = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = true,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    verify(
        "cleanup refuses to delete the source",
        !sourceConflict.shouldDelete &&
            sourceConflict.skipReason == DestinationCleanup.SkipReason.SOURCE_CONFLICT
    )

    val changedDocument = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-2",
        currentSize = 0L
    )
    verify(
        "cleanup skips a changed document id",
        !changedDocument.shouldDelete &&
            changedDocument.skipReason == DestinationCleanup.SkipReason.DOCUMENT_CHANGED
    )

    val missingDocumentId = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = null,
        currentSize = 0L
    )
    verify("cleanup skips a missing current document id", !missingDocumentId.shouldDelete)

    val unknownSize = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = null
    )
    verify(
        "cleanup skips an unknown current size",
        !unknownSize.shouldDelete &&
            unknownSize.skipReason == DestinationCleanup.SkipReason.UNKNOWN_SIZE
    )

    val nonEmpty = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 7L
    )
    verify(
        "cleanup skips a non-empty current destination",
        !nonEmpty.shouldDelete &&
            nonEmpty.skipReason == DestinationCleanup.SkipReason.NOT_EMPTY
    )
}

private fun testDestinationCleanupAttempt() {
    val valid = attestation()
    val decision = DestinationCleanup.plan(
        attestation = valid,
        destinationIsContent = true,
        sourceMatchesDestination = false,
        currentDocumentId = "doc-1",
        currentSize = 0L
    )
    var deletes = 0
    verify(
        "cleanup attempt reports a completed deletion",
        DestinationCleanup.attempt(decision) {
            deletes += 1
            1
        } == DestinationCleanup.Outcome.DELETED
    )
    verify("cleanup attempt calls delete once", deletes == 1)

    var refusedCalls = 0
    verify(
        "cleanup attempt reports a provider refusal",
        DestinationCleanup.attempt(decision) {
            refusedCalls += 1
            0
        } == DestinationCleanup.Outcome.REFUSED
    )
    verify("refused deletion still invokes the provider", refusedCalls == 1)

    verify(
        "cleanup attempt catches a delete exception",
        DestinationCleanup.attempt(decision) {
            throw SecurityException("simulated")
        } == DestinationCleanup.Outcome.REFUSED
    )

    var skippedCalls = 0
    val skipped = decision.copy(shouldDelete = false)
    verify(
        "cleanup attempt skips a refused plan",
        DestinationCleanup.attempt(skipped) {
            skippedCalls += 1
            1
        } == DestinationCleanup.Outcome.SKIPPED
    )
    verify("skipped deletion does not call the provider", skippedCalls == 0)
}

private fun testPhotoRestore() {
    val selected = PhotoRestore.RestoredPhoto("selected", true, false)
    val pending = PhotoRestore.RestoredPhoto("pending", false, true)

    val both = PhotoRestore.plan(selected, pending)
    verify("restore plan keeps the selected fallback", both.selected == selected)
    verify("restore plan loads the pending photo first", both.toLoad == pending)

    val selectedOnly = PhotoRestore.plan(selected, null)
    verify("restore plan falls back to the selected photo", selectedOnly.toLoad == selected)

    val pendingOnly = PhotoRestore.plan(null, pending)
    verify("restore plan does not invent a selected photo", pendingOnly.selected == null)
    verify("restore plan loads an unselected pending photo", pendingOnly.toLoad == pending)

    val empty = PhotoRestore.plan(null, null)
    verify("restore plan handles an empty state", empty.selected == null && empty.toLoad == null)

    val savedPending = PhotoRestore.pendingForSave(
        loading = true,
        pendingUri = "pending",
        pendingPersisted = true,
        resetEdits = false
    )
    verify("pending save keeps the uri", savedPending?.uri == "pending")
    verify("pending save keeps the grant", savedPending?.persisted == true)
    verify("pending save keeps the reset flag", savedPending?.resetEdits == false)

    verify(
        "pending save ignores a non-loading state",
        PhotoRestore.pendingForSave(
            loading = false,
            pendingUri = "pending",
            pendingPersisted = true,
            resetEdits = true
        ) == null
    )
    verify(
        "pending save ignores a missing uri",
        PhotoRestore.pendingForSave(
            loading = true,
            pendingUri = null,
            pendingPersisted = true,
            resetEdits = true
        ) == null
    )

    verify(
        "readiness prioritizes loading",
        PhotoRestore.sourceReadiness(null, false, true) ==
            PhotoRestore.SourceReadiness.LOADING
    )
    verify(
        "readiness accepts a loaded source",
        PhotoRestore.sourceReadiness("uri", true, false) ==
            PhotoRestore.SourceReadiness.READY
    )
    verify(
        "readiness rejects a missing uri",
        PhotoRestore.sourceReadiness(null, true, false) ==
            PhotoRestore.SourceReadiness.MISSING
    )
    verify(
        "readiness rejects an unloaded source",
        PhotoRestore.sourceReadiness("uri", false, false) ==
            PhotoRestore.SourceReadiness.MISSING
    )
}

private fun testEditingRedoRetention() {
    val state = EditingState()
    state.update(Adjustment.EXPOSURE, 10, "Exposure")
    state.undo()
    verify("undo creates redo history", state.canRedo)

    state.beginGesture()
    state.update(Adjustment.CONTRAST, state.adjustments.contrast, "Contrast")
    state.endGesture()
    verify("a no-op gesture preserves redo history", state.canRedo)

    state.beginGesture()
    state.update(Adjustment.CONTRAST, 20, "Contrast")
    state.endGesture()
    verify("a changed gesture consumes redo history", !state.canRedo)

    state.undo()
    verify("undo restores redo history", state.canRedo)
    state.beginGesture()
    state.update(Adjustment.CONTRAST, 30, "Contrast")
    state.cancelGesture()
    verify("a cancelled gesture preserves redo history", state.canRedo)
    verify("a cancelled gesture restores its base state", state.adjustments.contrast == 0)

    state.beginGesture()
    state.update(Adjustment.CONTRAST, state.adjustments.contrast, "Contrast")
    state.endGesture()
    verify("a no-op gesture does not clear redo", state.canRedo)
    verify("redo still works after a no-op gesture", state.redo())
}

private fun testFilterSelection() {
    verify(
        "selecting a filter at zero intensity uses the default",
        FilterSettings.selecting(Filter.WARM, 0).intensity ==
            FilterSettings.DEFAULT_INTENSITY
    )
    verify(
        "selecting a filter keeps a positive intensity",
        FilterSettings.selecting(Filter.VINTAGE, 35).intensity == 35
    )
    verify(
        "selecting a filter keeps its style",
        FilterSettings.selecting(Filter.CINEMATIC, 0).filter == Filter.CINEMATIC
    )
    verify(
        "default intensity remains one hundred",
        FilterSettings.DEFAULT_INTENSITY == 100
    )
    verify(
        "selecting a filter replaces invalid low intensity",
        FilterSettings.selecting(Filter.WARM, -5).intensity == 100
    )
    verify(
        "selecting a filter rejects excessive intensity",
        runCatching {
            FilterSettings.selecting(Filter.WARM, 101)
        }.isFailure
    )
}

fun main() {
    testWriteGuard()
    testDestinationCreation()
    testDestinationCleanupPlan()
    testDestinationCleanupAttempt()
    testPhotoRestore()
    testEditingRedoRetention()
    testFilterSelection()

    if (failureCount > 0) {
        println("$failureCount test(s) failed")
        exitProcess(1)
    }
    println("All M5.1 tests passed ($checkCount checks)")
}
