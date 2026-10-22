package com.yapnix.game.auth

import android.content.Context
import android.net.Uri
import com.yapnix.game.BuildConfig
import com.yapnix.game.data.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(
    val accessToken: String, val refreshToken: String, val userId: String, val email: String,
    val expiresAt: Long, val displayName: String? = null, val avatarUrl: String? = null,
)

class AuthRepository(context: Context) {
    private val store = SecureAuthStore(context)
    private val lock = Mutex()
    private val mutableSession = MutableStateFlow(store.read("session")?.let { runCatching { parse(JSONObject(it)) }.getOrNull() })
    val session = mutableSession.asStateFlow()
    val configured get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun startGoogleSignIn(): Uri = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!configured) throw ApiException("Google sign-in is not configured yet. You can still play as a guest.")
            val verifier = Pkce.verifier()
            store.write("pending", JSONObject().put("verifier", verifier).put("started", System.currentTimeMillis()).toString())
            Uri.parse("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", "google")
                .appendQueryParameter("redirect_to", "yapnix://auth/callback")
                .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
                .appendQueryParameter("code_challenge_method", "s256")
                .build()
        }
    }

    suspend fun finishSignIn(uri: Uri) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (uri.scheme != "yapnix" || uri.host != "auth" || uri.path != "/callback") return@withLock
            if (uri.getQueryParameter("error") != null) {
                store.remove("pending")
                throw ApiException("Google sign-in was cancelled or declined. Guest play is still available.")
            }
            val code = uri.getQueryParameter("code") ?: throw ApiException("Sign-in did not finish. Try again.")
            val pending = store.read("pending")?.let(::JSONObject) ?: throw ApiException("Start Google sign-in from Yapnix first.")
            if (System.currentTimeMillis() - pending.getLong("started") > 10 * 60_000) {
                store.remove("pending"); throw ApiException("Sign-in expired. Please try again.")
            }
            val value = request("token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", pending.getString("verifier")))
            save(value)
            store.remove("pending")
        }
    }

    suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val current = mutableSession.value ?: throw ApiException("Sign in to sync your progress")
            if (current.expiresAt > System.currentTimeMillis() + 60_000) return@withLock current.accessToken
            try {
                save(request("token?grant_type=refresh_token", JSONObject().put("refresh_token", current.refreshToken)))
            } catch (failure: ApiException) {
                if (failure.status in listOf(400, 401, 403)) { store.remove("session"); mutableSession.value = null }
                throw failure
            }
            mutableSession.value!!.accessToken
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        lock.withLock {
            val token = mutableSession.value?.accessToken
            // Local sign-out succeeds even when offline. Local game saves remain.
            if (token != null) runCatching { request("logout?scope=local", JSONObject(), token) }
            store.remove("session"); store.remove("pending"); mutableSession.value = null
        }
    }

    private fun save(value: JSONObject) {
        val withExpiry = JSONObject(value.toString()).put("expires_at", value.optLong("expires_at", System.currentTimeMillis() / 1000 + value.optLong("expires_in", 3600)))
        val parsed = parse(withExpiry)
        store.write("session", withExpiry.toString())
        mutableSession.value = parsed
    }

    private fun parse(value: JSONObject): AuthSession {
        val user = value.getJSONObject("user")
        val metadata = user.optJSONObject("user_metadata")
        val name = listOf("full_name", "name").firstNotNullOfOrNull { key ->
            metadata?.optString(key)?.trim()?.takeIf { it.isNotBlank() && it != "null" }?.take(100)
        }
        val avatar = listOf("avatar_url", "picture").firstNotNullOfOrNull { key ->
            ProfileImageUrls.sanitize(metadata?.optString(key))
        }
        return AuthSession(value.getString("access_token"), value.getString("refresh_token"), user.getString("id"),
            user.optString("email", "Google account"), value.getLong("expires_at") * 1000, name, avatar)
    }

    private fun request(path: String, body: JSONObject, token: String? = null): JSONObject {
        val connection = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000; connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw ApiException("Your session could not be verified. Try signing in again.", status)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }
}
