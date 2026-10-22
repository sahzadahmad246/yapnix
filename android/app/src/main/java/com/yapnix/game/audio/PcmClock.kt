package com.yapnix.game.audio

/** Mono signed 16-bit PCM: two bytes per sample, independent of chunk boundaries. */
object PcmClock {
    const val SAMPLE_RATE = 16_000
    fun timestampUs(bytes: Long, offsetUs: Long = 0): Long {
        require(bytes >= 0 && bytes % 2 == 0L && offsetUs >= 0)
        return offsetUs + (bytes / 2) * 1_000_000L / SAMPLE_RATE
    }
}
