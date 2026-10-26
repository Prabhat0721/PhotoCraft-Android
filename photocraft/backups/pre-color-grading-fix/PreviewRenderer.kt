package com.prabhat.photocraft.image

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import com.prabhat.photocraft.edit.AdjustmentMath
import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.FilterMath
import com.prabhat.photocraft.edit.FilterSettings
import com.prabhat.photocraft.edit.PhotoEffects
import com.prabhat.photocraft.edit.PhotoEffectsMath
import com.prabhat.photocraft.util.RequestSequence
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class PreviewRenderer : Closeable {
    sealed class Result {
        data class Success(val bitmap: Bitmap) : Result()
        data class Failure(val reason: FailureReason) : Result()
    }

    enum class FailureReason {
        OUT_OF_MEMORY,
        RENDER_FAILED
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderThread = HandlerThread(
        "PhotoCraftPreview",
        Process.THREAD_PRIORITY_BACKGROUND
    ).apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private val requests = RequestSequence()
    private val closed = AtomicBoolean(false)
    private var pixelBuffer: IntArray? = null

    fun request(
        source: Bitmap,
        adjustments: Adjustments,
        filter: FilterSettings,
        effects: PhotoEffects,
        callback: (Result) -> Unit
    ) {
        if (closed.get()) {
            return
        }

        val requestId = requests.next()
        renderHandler.post {
            if (closed.get() || !requests.isCurrent(requestId)) {
                return@post
            }

            val result = render(source, adjustments, filter, effects)

            if (closed.get() || !requests.isCurrent(requestId)) {
                recycleDiscarded(result, source)
                return@post
            }

            mainHandler.post {
                if (closed.get() || !requests.isCurrent(requestId)) {
                    recycleDiscarded(result, source)
                    return@post
                }
                callback(result)
            }
        }
    }

    private fun render(
        source: Bitmap,
        adjustments: Adjustments,
        filter: FilterSettings,
        effects: PhotoEffects
    ): Result {
        if (adjustments.isNeutral() && filter.isNeutral() && effects.isNeutral()) {
            return Result.Success(source)
        }

        var output: Bitmap? = null
        return try {
            val width = source.width
            val height = source.height
            val size = width * height
            val pixels = pixelBuffer
                ?.takeIf { it.size == size }
                ?: IntArray(size).also { pixelBuffer = it }

            source.getPixels(pixels, 0, width, 0, 0, width, height)
            AdjustmentMath.applyInPlace(pixels, adjustments)
            FilterMath.applyInPlace(pixels, filter)
            PhotoEffectsMath.applyInPlace(pixels, width, height, effects)

            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                density = source.density
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
            Result.Success(output)
        } catch (_: OutOfMemoryError) {
            output?.recycle()
            Result.Failure(FailureReason.OUT_OF_MEMORY)
        } catch (_: IllegalArgumentException) {
            output?.recycle()
            Result.Failure(FailureReason.RENDER_FAILED)
        } catch (_: IllegalStateException) {
            output?.recycle()
            Result.Failure(FailureReason.RENDER_FAILED)
        }
    }

    private fun recycleDiscarded(result: Result, source: Bitmap) {
        if (result is Result.Success && result.bitmap !== source) {
            result.bitmap.recycle()
        }
    }

    override fun close() {
        closed.set(true)
        requests.invalidate()
        mainHandler.removeCallbacksAndMessages(null)
        renderHandler.removeCallbacksAndMessages(null)
        renderThread.quitSafely()
        pixelBuffer = null
    }
}
