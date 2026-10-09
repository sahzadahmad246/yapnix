package com.yapnix.game.gameplay

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    @Test fun normalizesCasePunctuationAndWhitespace() {
        assertEquals("table fan", AnswerMatcher.normalize("  TABLE,\t fan!! "))
    }

    @Test fun acceptsAliasesButRejectsPartialAnswersAndEmptySpeech() {
        val answers = listOf("bike", "bicycle", "road bike")
        assertTrue(AnswerMatcher.matches("Bicycle!", answers))
        assertTrue(AnswerMatcher.matches("road-bike", answers))
        assertFalse(AnswerMatcher.matches("motorbike", answers))
        assertFalse(AnswerMatcher.matches("road", answers))
        assertFalse(AnswerMatcher.matches("!!!", listOf("")))
    }

    @Test fun preservesUnicodeAndCanonicalEquivalence() {
        assertTrue(AnswerMatcher.matches("CAFÉ", listOf("cafe\u0301")))
        assertTrue(AnswerMatcher.matches("पंखा!", listOf("पंखा")))
        assertFalse(AnswerMatcher.matches("猫", listOf("犬")))
    }

    @Test fun matchingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(AnswerMatcher.matches("BIKE", listOf("bike")))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
