package com.yapnix.game.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.io.IOException
import java.util.Properties

// Explicit numeric versions prevent lexical ordering mistakes (0.10 is newer than 0.9).
object ReleaseVersions {
    fun newer(candidate: String, installed: String): Boolean {
        fun parse(value: String): List<Int>? {
            val normalized = value.removePrefix("v")
            if (!normalized.matches(Regex("[0-9]+[.][0-9]+[.][0-9]+"))) return null
            return normalized.split('.').map { it.toIntOrNull() ?: return null }
        }
        val next = parse(candidate) ?: return false
        val current = parse(installed) ?: return false
        return next.zip(current).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun trustedApk(url: String, tag: String): Boolean = runCatching {
        val uri = URI(url)
        tag.matches(Regex("v[0-9]+[.][0-9]+[.][0-9]+")) &&
            uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.rawPath == "/sahzadahmad246/yapnix/releases/download/$tag/yapnix-${tag}.apk" && uri.query == null && uri.fragment == null
    }.getOrDefault(false)
}

data class AppUpdate(
    val version: String, val url: String, val sizeBytes: Long = 0,
    val sha256: String? = null, val releaseNotes: String = "",
)

/** A public static feed avoids GitHub's shared-IP anonymous API quota. */
internal object UpdateManifest {
    fun parse(text: String, installed: String): AppUpdate? {
        require(text.length <= 32_768) { "Update feed is too large" }
        val properties = Properties().apply { load(text.reader()) }
        val tag = properties.getProperty("version").orEmpty()
        val url = properties.getProperty("url").orEmpty()
        val size = properties.getProperty("size")?.toLongOrNull() ?: 0
        val digest = UpdateSecurity.sha256Digest(properties.getProperty("sha256").orEmpty())
        require(ReleaseVersions.trustedApk(url, tag) && size in 1..UpdateSecurity.MAX_APK_BYTES && digest != null) { "Invalid update feed" }
        return if (ReleaseVersions.newer(tag, installed)) AppUpdate(tag, url, size, digest,
            properties.getProperty("whatsNew").orEmpty().take(3000)) else null
    }
}

internal suspend fun discoverUpdate(
    installed: String, manifest: suspend () -> String, fallback: suspend () -> AppUpdate?,
): AppUpdate? = try {
    UpdateManifest.parse(manifest(), installed)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    fallback()
}

object AppUpdates {
    suspend fun check(installed: String): AppUpdate? = withContext(Dispatchers.IO) {
        discoverUpdate(installed, manifest = {
            read("https://raw.githubusercontent.com/sahzadahmad246/yapnix/main/updates/android.properties?check=${System.currentTimeMillis() / 60_000}", installed)
        }, fallback = { checkGitHub(installed) })
    }

    private fun read(url: String, installed: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Accept", "application/vnd.github+json, text/plain")
            connection.setRequestProperty("User-Agent", "Yapnix/$installed")
            when (connection.responseCode) {
                200 -> return connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    val content = StringBuilder()
                    while (content.length <= 262_144) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 262_145 - content.length))
                        if (count < 0) break
                        content.append(buffer, 0, count)
                    }
                    val text = content.toString()
                    if (text.length > 262_144) throw IOException("Update service returned an invalid response.")
                    text
                }
                403, 429 -> throw IOException("Update checks are temporarily limited. Please try again shortly.")
                else -> throw IOException("The update service is unavailable. Please try again shortly.")
            }
        } finally { connection.disconnect() }
    }

    private fun checkGitHub(installed: String): AppUpdate? {
        val releases = JSONArray(read("https://api.github.com/repos/sahzadahmad246/yapnix/releases?per_page=20", installed))
        var selected: AppUpdate? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            if (!ReleaseVersions.newer(tag, selected?.version ?: installed)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val url = asset.optString("browser_download_url")
                if (ReleaseVersions.trustedApk(url, tag)) {
                    val notes = release.optString("body").substringBefore("\n## ").lineSequence()
                        .filter { it.startsWith("- ") }.take(6).joinToString("\n") { it.removePrefix("- ") }
                    selected = AppUpdate(tag, url, asset.optLong("size", 0), UpdateSecurity.sha256Digest(asset.optString("digest")), notes)
                }
            }
        }
        return selected
    }
}
