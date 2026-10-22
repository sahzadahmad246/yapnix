package com.yapnix.game.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI

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
            uri.path == "/sahzadahmad246/yapnix/releases/download/$tag/yapnix-${tag}.apk" && uri.query == null && uri.fragment == null
    }.getOrDefault(false)
}

data class AppUpdate(val version: String, val url: String)
object AppUpdates {
    suspend fun check(installed: String): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = URI("https://api.github.com/repos/sahzadahmad246/yapnix/releases?per_page=20").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Yapnix/$installed")
            check(connection.responseCode == 200)
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            var selected: AppUpdate? = null
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!ReleaseVersions.newer(tag, selected?.version ?: installed)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val url = assets.getJSONObject(j).optString("browser_download_url")
                    if (ReleaseVersions.trustedApk(url, tag)) selected = AppUpdate(tag, url)
                }
            }
            selected
        } finally { connection.disconnect() }
    }
}
