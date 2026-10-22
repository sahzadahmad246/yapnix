package com.yapnix.game.updates

import java.net.URI
import java.security.MessageDigest

/** Network and package identity rules are kept independent of Android for unit testing. */
object UpdateSecurity {
    const val MAX_APK_BYTES = 128L * 1024 * 1024

    fun trustedDownloadHop(url: String, tag: String): Boolean {
        if (ReleaseVersions.trustedApk(url, tag)) return true
        return runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.port == -1 && uri.userInfo == null && uri.fragment == null &&
                uri.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com") &&
                uri.rawPath.matches(Regex("/github-production-release-asset(?:/|-)[A-Za-z0-9/_.-]+")) &&
                uri.rawPath.split('/').none { it == "." || it == ".." }
        }.getOrDefault(false)
    }

    fun sha256Digest(value: String): String? = value.removePrefix("sha256:")
        .takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.lowercase()

    fun matchesDigest(expected: String, actual: String): Boolean {
        val first = sha256Digest(expected) ?: return false
        val second = sha256Digest(actual) ?: return false
        return MessageDigest.isEqual(first.toByteArray(Charsets.US_ASCII), second.toByteArray(Charsets.US_ASCII))
    }

    fun validIdentity(
        installedPackage: String, candidatePackage: String,
        installedCode: Long, candidateCode: Long,
        installedSigners: Set<String>, candidateSigners: Set<String>,
    ): Boolean = installedPackage == candidatePackage && candidateCode > installedCode &&
        installedSigners.isNotEmpty() && candidateSigners == installedSigners
}
