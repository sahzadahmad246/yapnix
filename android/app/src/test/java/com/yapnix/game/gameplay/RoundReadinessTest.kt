package com.yapnix.game.gameplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundReadinessTest {
    @Test fun waitsForImageAndSpokenPrompt() {
        assertFalse(RoundReadiness.ready("cat", null, null))
        assertFalse(RoundReadiness.ready("cat", "cat", null))
        assertFalse(RoundReadiness.ready("cat", null, "cat"))
        assertTrue(RoundReadiness.ready("cat", "cat", "cat"))
    }
    @Test fun ignoresPreviousQuestionCompletions() {
        assertFalse(RoundReadiness.ready("dog", "cat", "cat"))
        assertFalse(RoundReadiness.ready("dog", "dog", "cat"))
        assertFalse(RoundReadiness.ready(null, null, null))
    }
}
