package com.yapnix.game.gameplay

/** Monotonic deadlines keep answer reveals independent of UI redraws and spoken input. */
object RevealTimer {
    const val SECONDS = 5
    fun remaining(deadlineMillis: Long, nowMillis: Long): Int =
        ((deadlineMillis - nowMillis).coerceAtLeast(0L).plus(999L) / 1000L).toInt()
}
