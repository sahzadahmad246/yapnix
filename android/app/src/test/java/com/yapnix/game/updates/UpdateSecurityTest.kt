package com.yapnix.game.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {
    @Test fun permitsGitHubReleaseCdnRedirectWithSignedQuery() {
        assertTrue(UpdateSecurity.trustedDownloadHop(
            "https://release-assets.githubusercontent.com/github-production-release-asset/123/abc-456?sv=2018&sig=abc%2Fdef",
            "v0.5.0",
        ))
        assertTrue(UpdateSecurity.trustedDownloadHop(
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/abc?token=xyz",
            "v0.5.0",
        ))
    }

    @Test fun rejectsUntrustedRedirectsAndAmbiguousPaths() {
        val path = "/github-production-release-asset/123/abc"
        listOf(
            "http://release-assets.githubusercontent.com$path",
            "https://release-assets.githubusercontent.com.evil.com$path",
            "https://release-assets.githubusercontent.com@evil.com$path",
            "https://evil@release-assets.githubusercontent.com$path",
            "https://release-assets.githubusercontent.com:8443$path",
            "https://release-assets.githubusercontent.com$path#fragment",
            "https://release-assets.githubusercontent.com/github-production-release-asset/../other",
            "https://release-assets.githubusercontent.com/github-production-release-asset/%2e%2e/other",
            "https://github.com/another/repo/releases/download/v0.5.0/other.apk",
            "https://api.github.com/anything.apk",
        ).forEach { assertFalse(it, UpdateSecurity.trustedDownloadHop(it, "v0.5.0")) }
        assertFalse(ReleaseVersions.trustedApk(
            "https://github.com/sahzadahmad246/yapnix/releases/download/v0.5.0/yapnix-v0.5.%30.apk", "v0.5.0",
        ))
    }

    @Test fun acceptsOnlyMatchingNonemptyInstalledSignersAndHigherVersion() {
        fun valid(candidatePackage: String = "com.yapnix.game", candidateCode: Long = 6,
                  installedSigners: Set<String> = setOf("release-key"), candidateSigners: Set<String> = setOf("release-key")) =
            UpdateSecurity.validIdentity("com.yapnix.game", candidatePackage, 5, candidateCode, installedSigners, candidateSigners)
        assertTrue(valid())
        assertFalse(valid(candidatePackage = "com.evil.app"))
        assertFalse(valid(candidateCode = 5))
        assertFalse(valid(candidateCode = 4))
        assertFalse(valid(candidateSigners = setOf("other-key")))
        assertFalse(valid(candidateSigners = setOf("release-key", "other-key")))
        assertFalse(valid(installedSigners = emptySet(), candidateSigners = emptySet()))
    }

    @Test fun validatesAndComparesGitHubSha256Digests() {
        val digest = "a1".repeat(32)
        assertTrue(UpdateSecurity.matchesDigest("sha256:$digest", digest.uppercase()))
        assertFalse(UpdateSecurity.matchesDigest(digest, "b2".repeat(32)))
        assertFalse(UpdateSecurity.matchesDigest("", ""))
        assertNull(UpdateSecurity.sha256Digest("sha256:abcd"))
        assertNull(UpdateSecurity.sha256Digest("md5:$digest"))
        assertNull(UpdateSecurity.sha256Digest("sha256:${"z".repeat(64)}"))
    }
}
