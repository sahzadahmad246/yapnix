package com.yapnix.game.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {
    private val feed = """
        version=v0.7.2
        url=https://github.com/sahzadahmad246/yapnix/releases/download/v0.7.2/yapnix-v0.7.2.apk
        size=2356775
        sha256=${"a1".repeat(32)}
        whatsNew=Reliable update checks.\nProfile update card.
    """.trimIndent()

    @Test fun readsMetadataAndMultilineWhatsNew() {
        val update = UpdateManifest.parse(feed, "0.7.1")!!
        assertEquals("v0.7.2", update.version)
        assertEquals(2356775L, update.sizeBytes)
        assertEquals("a1".repeat(32), update.sha256)
        assertEquals("Reliable update checks.\nProfile update card.", update.releaseNotes)
    }

    @Test fun doesNotOfferCurrentOrOlderRelease() {
        assertNull(UpdateManifest.parse(feed, "0.7.2"))
        assertNull(UpdateManifest.parse(feed, "0.8.0"))
    }

    @Test fun rejectsUntrustedOrIncompleteReleaseMetadata() {
        val invalid = listOf(
            feed.replace("https://github.com/", "https://evil.com/"),
            feed.replace("size=2356775", "size=0"),
            feed.replace("size=2356775", "size=134217729"),
            feed.replace("sha256=${"a1".repeat(32)}", "sha256=bad"),
            feed.replace("version=v0.7.2", "version=main"),
            "x".repeat(32_769),
        )
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(value, "0.7.1") }
        }
    }

    @Test fun healthyStaticFeedDoesNotCallRateLimitedApi() = runBlocking {
        val update = discoverUpdate("0.7.1", { feed }) { error("API must not be called") }
        assertEquals("v0.7.2", update?.version)
        assertNull(discoverUpdate("0.7.2", { feed }) { error("Current manifest must not call API") })
    }

    @Test fun unavailableOrInvalidManifestFallsBackToReleaseApi() = runBlocking {
        val expected = AppUpdate("v0.7.2", "fallback")
        assertEquals(expected, discoverUpdate("0.7.1", { throw java.io.IOException("Unavailable") }) { expected })
        assertEquals(expected, discoverUpdate("0.7.1", { "invalid" }) { expected })
    }

    @Test fun cancellationNeverFallsBack() {
        assertThrows(CancellationException::class.java) {
            runBlocking { discoverUpdate("0.7.1", { throw CancellationException() }) { error("Do not fall back") } }
        }
    }
}
