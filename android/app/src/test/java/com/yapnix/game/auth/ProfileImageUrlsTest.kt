package com.yapnix.game.auth

import org.junit.Assert.*
import org.junit.Test

class ProfileImageUrlsTest {
    @Test fun permitsGoogleProfilePhotoAndSafeHttpsMetadata() {
        val photo = "https://lh3.googleusercontent.com/a/photo=s96-c"
        assertEquals(photo, ProfileImageUrls.sanitize(" $photo "))
        assertEquals("https://cdn.example.com/avatar.png?v=2", ProfileImageUrls.sanitize("https://cdn.example.com/avatar.png?v=2"))
    }
    @Test fun rejectsLocalInsecureAndCredentialBearingUrls() {
        listOf(null, "", "null", "file:///data/session", "content://photos/1", "http://example.com/photo", "https://token@example.com/photo", "https://example.com:9000/photo", "https://example.com/photo#secret").forEach {
            assertNull(ProfileImageUrls.sanitize(it))
        }
    }
}
