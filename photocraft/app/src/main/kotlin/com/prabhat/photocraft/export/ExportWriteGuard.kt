package com.prabhat.photocraft.export

import java.io.IOException

object ExportWriteGuard {
    enum class Outcome {
        SUCCESS,
        CANCELLED,
        WRITE_FAILED
    }

    fun writeIfActive(
        isCancelled: () -> Boolean,
        write: () -> Boolean
    ): Outcome {
        if (isCancelled()) {
            return Outcome.CANCELLED
        }
        val completed = try {
            write()
        } catch (_: IOException) {
            return Outcome.WRITE_FAILED
        }
        if (!completed) {
            return Outcome.WRITE_FAILED
        }
        return if (isCancelled()) Outcome.CANCELLED else Outcome.SUCCESS
    }

    fun afterClose(isCancelled: () -> Boolean): Outcome =
        if (isCancelled()) Outcome.CANCELLED else Outcome.SUCCESS
}
