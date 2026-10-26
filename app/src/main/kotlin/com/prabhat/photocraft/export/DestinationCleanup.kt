package com.prabhat.photocraft.export

data class DestinationAttestation(
    val established: Boolean,
    val documentId: String?,
    val preWriteSize: Long?,
    val isContentDocument: Boolean
) {
    companion object {
        val NONE = DestinationAttestation(
            established = false,
            documentId = null,
            preWriteSize = null,
            isContentDocument = false
        )
    }
}

object DestinationCreation {
    const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L

    fun establish(
        fromCreateAction: Boolean,
        requestedTitle: String?,
        observedTitle: String?,
        documentId: String?,
        lastModifiedMs: Long?,
        pickerStartedAtMs: Long,
        nowMs: Long,
        preWriteSize: Long?,
        isContentDocument: Boolean
    ): DestinationAttestation {
        val established =
            fromCreateAction &&
                isContentDocument &&
                requestedTitle != null &&
                requestedTitle == observedTitle &&
                !documentId.isNullOrBlank() &&
                lastModifiedMs != null &&
                pickerStartedAtMs > 0L &&
                lastModifiedMs >= pickerStartedAtMs &&
                lastModifiedMs <= nowMs + MAX_CLOCK_SKEW_MS &&
                preWriteSize == 0L
        return DestinationAttestation(
            established = established,
            documentId = documentId,
            preWriteSize = preWriteSize,
            isContentDocument = isContentDocument
        )
    }
}

object DestinationCleanup {
    data class Decision(
        val shouldDelete: Boolean,
        val skipReason: SkipReason?
    )

    enum class SkipReason {
        NOT_ATTESTED,
        NOT_CONTENT_DOCUMENT,
        SOURCE_CONFLICT,
        DOCUMENT_CHANGED,
        UNKNOWN_SIZE,
        NOT_EMPTY
    }

    enum class Outcome {
        DELETED,
        SKIPPED,
        REFUSED
    }

    fun plan(
        attestation: DestinationAttestation?,
        destinationIsContent: Boolean,
        sourceMatchesDestination: Boolean,
        currentDocumentId: String?,
        currentSize: Long?
    ): Decision {
        if (
            attestation == null ||
            !attestation.established ||
            attestation.preWriteSize != 0L
        ) {
            return Decision(false, SkipReason.NOT_ATTESTED)
        }
        if (!attestation.isContentDocument || !destinationIsContent) {
            return Decision(false, SkipReason.NOT_CONTENT_DOCUMENT)
        }
        if (sourceMatchesDestination) {
            return Decision(false, SkipReason.SOURCE_CONFLICT)
        }
        val attestedId = attestation.documentId
        if (
            attestedId.isNullOrBlank() ||
            currentDocumentId == null ||
            currentDocumentId != attestedId
        ) {
            return Decision(false, SkipReason.DOCUMENT_CHANGED)
        }
        if (currentSize == null) {
            return Decision(false, SkipReason.UNKNOWN_SIZE)
        }
        if (currentSize != 0L) {
            return Decision(false, SkipReason.NOT_EMPTY)
        }
        return Decision(true, null)
    }

    fun attempt(decision: Decision, delete: () -> Int): Outcome {
        if (!decision.shouldDelete) {
            return Outcome.SKIPPED
        }
        return try {
            if (delete() > 0) Outcome.DELETED else Outcome.REFUSED
        } catch (_: Exception) {
            Outcome.REFUSED
        }
    }
}
