package com.prabhat.photocraft.util

object PhotoRestore {
    data class RestoredPhoto(
        val uri: String,
        val persisted: Boolean,
        val resetEdits: Boolean
    )

    data class Plan(
        val selected: RestoredPhoto?,
        val toLoad: RestoredPhoto?
    )

    enum class SourceReadiness {
        READY,
        LOADING,
        MISSING
    }

    fun plan(selected: RestoredPhoto?, pending: RestoredPhoto?): Plan =
        Plan(selected = selected, toLoad = pending ?: selected)

    fun pendingForSave(
        loading: Boolean,
        pendingUri: String?,
        pendingPersisted: Boolean,
        resetEdits: Boolean
    ): RestoredPhoto? {
        if (!loading || pendingUri == null) {
            return null
        }
        return RestoredPhoto(
            uri = pendingUri,
            persisted = pendingPersisted,
            resetEdits = resetEdits
        )
    }

    fun sourceReadiness(
        sourceUri: String?,
        sourceLoaded: Boolean,
        loading: Boolean
    ): SourceReadiness = when {
        loading -> SourceReadiness.LOADING
        sourceUri == null || !sourceLoaded -> SourceReadiness.MISSING
        else -> SourceReadiness.READY
    }
}
