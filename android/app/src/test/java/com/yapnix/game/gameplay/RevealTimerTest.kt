package com.yapnix.game.gameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class RevealTimerTest {
    @Test fun `reveal waits for all five seconds`() {
        val start = 12000L
        val deadline = start + RevealTimer.SECONDS * 1000
        assertEquals(5, RevealTimer.remaining(deadline, start))
        assertEquals(5, RevealTimer.remaining(deadline, start + 1))
        assertEquals(1, RevealTimer.remaining(deadline, deadline - 1))
        assertEquals(0, RevealTimer.remaining(deadline, deadline))
    }
    @Test fun `late callbacks still reveal immediately`() {
        assertEquals(0, RevealTimer.remaining(5000, 8900))
    }
}
