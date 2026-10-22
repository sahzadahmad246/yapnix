package com.yapnix.game.recording

import org.junit.Assert.*
import org.junit.Test

class RecordingQualityTest {
    @Test fun everySupportedOutputIsPortraitReelAndAtLeastHd() {
        RecordingQuality.profiles.forEach {
            assertEquals(it.width * 16, it.height * 9)
            assertTrue(it.width >= 720 && it.height >= 1280)
            assertTrue(it.width % 2 == 0 && it.height % 2 == 0)
        }
    }
    @Test fun qualityAttemptsPreferFullHdBeforeFallback() {
        assertEquals(1080, RecordingQuality.profiles.first().width)
        assertEquals(1920, RecordingQuality.profiles.first().height)
        assertEquals(720, RecordingQuality.profiles.last().width)
        assertTrue(RecordingQuality.profiles.first().bitrate > RecordingQuality.profiles.last().bitrate)
    }
}
