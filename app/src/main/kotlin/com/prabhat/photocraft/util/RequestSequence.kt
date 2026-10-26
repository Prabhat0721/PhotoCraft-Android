package com.prabhat.photocraft.util

import java.util.concurrent.atomic.AtomicInteger

class RequestSequence {
    private val generation = AtomicInteger(0)

    fun next(): Int = generation.incrementAndGet()

    fun current(): Int = generation.get()

    fun isCurrent(requestId: Int): Boolean = requestId == generation.get()

    fun invalidate() {
        generation.incrementAndGet()
    }
}
