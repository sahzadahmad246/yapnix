package com.yapnix.game.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmClockTest {
    @Test fun preservesAudioTimeAcrossChunksAndVideoStartOffset() {
        assertEquals(1_000_000L, PcmClock.timestampUs(32_000))
        assertEquals(50_000L, PcmClock.timestampUs(1_600))
        assertEquals(1_025_000L, PcmClock.timestampUs(32_000, 25_000))
        assertEquals(3_600_000_000L, PcmClock.timestampUs(115_200_000))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsPartialSamples() { PcmClock.timestampUs(3) }
}
