package com.yapnix.game.auth

import java.net.URI

/** Treat profile metadata as external input; never open local or credential-bearing URIs. */
object ProfileImageUrls {
    fun sanitize(value: String?): String? = value?.trim()?.takeIf { url ->
        runCatching {
            val uri = URI(url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.fragment == null && (uri.port == -1 || uri.port == 443)
        }.getOrDefault(false)
    }
}
