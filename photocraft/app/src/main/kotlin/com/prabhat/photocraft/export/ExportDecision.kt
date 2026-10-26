package com.prabhat.photocraft.export

enum class ExportDecision {
    START,
    CANCELLED,
    MISSING_DESTINATION,
    SOURCE_CONFLICT;

    companion object {
        fun decide(
            resultOk: Boolean,
            destinationAvailable: Boolean,
            sourceMatchesDestination: Boolean
        ): ExportDecision = when {
            !resultOk -> CANCELLED
            !destinationAvailable -> MISSING_DESTINATION
            sourceMatchesDestination -> SOURCE_CONFLICT
            else -> START
        }
    }
}
