package com.yapnix.game.updates

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionsTest {
    @Test fun comparesNumericVersionsInsteadOfStrings() {
        assertTrue(ReleaseVersions.newer("v0.10.0", "0.9.8"))
        assertTrue(ReleaseVersions.newer("v1.0.0", "0.99.99"))
        assertFalse(ReleaseVersions.newer("v0.3.0", "0.3.0"))
        assertFalse(ReleaseVersions.newer("v0.2.9", "0.3.0"))
    }
    @Test fun latestReleaseUpdatesExistingInstalledVersions() {
        for (installed in listOf("0.3.1", "0.4.0", "0.5.0", "0.6.0", "0.7.0", "0.7.1")) {
            assertTrue(ReleaseVersions.newer("v0.7.2", installed))
        }
        assertFalse(ReleaseVersions.newer("v0.7.2", "0.7.2"))
        assertTrue(ReleaseVersions.trustedApk("https://github.com/sahzadahmad246/yapnix/releases/download/v0.7.2/yapnix-v0.7.2.apk", "v0.7.2"))
    }
    @Test fun rejectsInvalidTags() {
        listOf("main", "v0.4", "v0.4.0-beta", "v-1.0.0").forEach { assertFalse(ReleaseVersions.newer(it, "0.3.0")) }
    }
    @Test fun acceptsOnlyExactRepositoryApkDownloads() {
        val path = "/sahzadahmad246/yapnix/releases/download/v0.4.0/yapnix-v0.4.0.apk"
        assertTrue(ReleaseVersions.trustedApk("https://github.com$path", "v0.4.0"))
        assertFalse(ReleaseVersions.trustedApk("http://github.com$path", "v0.4.0"))
        assertFalse(ReleaseVersions.trustedApk("https://github.com.evil.com$path", "v0.4.0"))
        assertFalse(ReleaseVersions.trustedApk("https://evil@github.com$path", "v0.4.0"))
        assertFalse(ReleaseVersions.trustedApk("https://github.com$path?redirect=other", "v0.4.0"))
        assertFalse(ReleaseVersions.trustedApk("https://github.com$path", "v0.5.0"))
    }
}
