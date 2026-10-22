package com.yapnix.game.data

import kotlin.math.sqrt

/** Reserve headroom for the UI, CameraX and encoder even for 100-picture rounds. */
object ImageDecodeBudget {
    fun edge(questionCount: Int, maxHeapBytes: Long): Int {
        require(questionCount > 0)
        require(maxHeapBytes > 0)
        val budget = minOf(96L * 1024 * 1024, maxHeapBytes / 3).coerceAtLeast(4)
        return sqrt(budget.toDouble() / (questionCount.toDouble() * 4)).toInt().coerceIn(1, 1080)
    }
}
