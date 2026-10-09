package com.yapnix.game.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test fun matchesRfc7636ChallengeVector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun producesIndependentUrlSafeVerifiers() {
        val first = Pkce.verifier()
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertFalse(first == Pkce.verifier())
    }
}
