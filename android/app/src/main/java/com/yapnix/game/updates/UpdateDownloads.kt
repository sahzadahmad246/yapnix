package com.yapnix.game.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

enum class UpdateStage { IDLE, DOWNLOADING, VERIFYING, READY, FAILED }
data class UpdateDownloadState(
    val version: String? = null,
    val stage: UpdateStage = UpdateStage.IDLE,
    val receivedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
)

/** Application-scoped work keeps a download alive through navigation and Activity recreation. */
class UpdateDownloads private constructor(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "updates")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    // A canceled job may still be returning from a blocking network read or package parse.
    // Keep its cleanup inside this lock so it cannot touch a replacement job's APK.
    private val fileOperation = Mutex()
    @Volatile private var generation = 0L

    @Synchronized fun start(update: AppUpdate) {
        if (mutableState.value.version == update.version && job?.isActive == true) return
        cancel()
        val request = generation
        fun publish(value: UpdateDownloadState) {
            synchronized(this@UpdateDownloads) {
                if (request == generation) mutableState.value = value
            }
        }
        mutableState.value = UpdateDownloadState(update.version, UpdateStage.DOWNLOADING, totalBytes = update.sizeBytes)
        job = scope.launch {
            fileOperation.withLock {
                val partial = File(directory, "$request.part")
                try {
                    validateRelease(update)
                    currentCoroutineContext().ensureActive()
                    check(directory.isDirectory || directory.mkdirs()) { "Could not create download folder" }
                    // Version-derived paths are constructed only after validating the release tag.
                    val target = File(directory, "${update.version}.apk")
                    currentCoroutineContext().ensureActive()
                    val cachedValid = target.exists() && runCatching { verify(target, update) }.isSuccess
                    currentCoroutineContext().ensureActive()
                    if (!cachedValid) {
                        target.delete()
                        directory.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach {
                            currentCoroutineContext().ensureActive()
                            it.delete()
                        }
                        download(update, partial) { received, total ->
                            publish(UpdateDownloadState(update.version, UpdateStage.DOWNLOADING, received, total))
                        }
                        currentCoroutineContext().ensureActive()
                        publish(UpdateDownloadState(update.version, UpdateStage.VERIFYING))
                        verify(partial, update)
                        currentCoroutineContext().ensureActive()
                        check(partial.renameTo(target)) { "Could not save update" }
                    }
                    currentCoroutineContext().ensureActive()
                    publish(UpdateDownloadState(update.version, UpdateStage.READY))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    publish(UpdateDownloadState(update.version, UpdateStage.FAILED,
                        message = failure.message ?: "Download failed. Try again."))
                } finally {
                    partial.delete()
                }
            }
        }
    }

    @Synchronized fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = UpdateDownloadState()
    }

    /** Called only after an explicit Install tap; the Android installer asks for confirmation. */
    suspend fun installationIntent(update: AppUpdate): Intent = withContext(Dispatchers.IO) {
        fileOperation.withLock {
            validateRelease(update)
            currentCoroutineContext().ensureActive()
            val apk = File(directory, "${update.version}.apk")
            verify(apk, update)
            currentCoroutineContext().ensureActive()
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Yapnix update", uri)
            }
        }
    }

    private fun validateRelease(update: AppUpdate) {
        check(ReleaseVersions.trustedApk(update.url, update.version)) { "Untrusted update link" }
        check(update.sizeBytes in 0..UpdateSecurity.MAX_APK_BYTES) { "Invalid update size" }
    }

    private suspend fun download(update: AppUpdate, destination: File, progress: (Long, Long) -> Unit) {
        var nextUrl = update.url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            check(UpdateSecurity.trustedDownloadHop(nextUrl, update.version)) { "Untrusted download redirect" }
            val connection = URI(nextUrl).toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "Yapnix Android updater")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: error("Missing download redirect")
                        nextUrl = URI(nextUrl).resolve(location).toString()
                    }
                    200 -> {
                        val reportedSize = connection.contentLengthLong
                        check(reportedSize <= UpdateSecurity.MAX_APK_BYTES) { "Update is too large" }
                        val total = update.sizeBytes.takeIf { size -> size > 0 } ?: reportedSize.coerceAtLeast(0)
                        if (reportedSize > 0 && update.sizeBytes > 0) {
                            check(reportedSize == update.sizeBytes) { "Update size changed. Check for updates again." }
                        }
                        connection.inputStream.use { input ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var received = 0L
                                var lastPublished = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    check(received <= UpdateSecurity.MAX_APK_BYTES && (total <= 0 || received <= total)) { "Update size is invalid" }
                                    output.write(buffer, 0, count)
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - lastPublished > 150) {
                                        progress(received, total)
                                        lastPublished = now
                                    }
                                }
                                check(received > 0 && (total <= 0 || received == total)) { "Download was interrupted. Try again." }
                                progress(received, total)
                            }
                        }
                        return
                    }
                    else -> error("Download failed (${connection.responseCode}). Try again.")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many download redirects")
    }

    @Suppress("DEPRECATION")
    private fun verify(apk: File, update: AppUpdate) {
        check(ReleaseVersions.trustedApk(update.url, update.version)) { "Untrusted update link" }
        check(apk.isFile && apk.length() in 1..UpdateSecurity.MAX_APK_BYTES) { "Update file is missing. Download it again." }
        check(update.sizeBytes <= 0 || apk.length() == update.sizeBytes) { "Update download is incomplete" }
        update.sha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            check(UpdateSecurity.matchesDigest(expected, digest.digest().joinToString("") { "%02x".format(it) })) { "Update checksum did not match. Download it again." }
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = app.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("This update file is invalid")
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        fun PackageInfo.code(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
        fun PackageInfo.signers(): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else this.signatures
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        check(UpdateSecurity.validIdentity(installed.packageName, candidate.packageName, installed.code(), candidate.code(), installed.signers(), candidate.signers())) {
            "This update does not match your installed Yapnix app"
        }
        check(candidate.versionName == update.version.removePrefix("v")) { "Update version does not match" }
    }

    companion object {
        @Volatile private var instance: UpdateDownloads? = null
        fun get(context: Context): UpdateDownloads = instance ?: synchronized(this) {
            instance ?: UpdateDownloads(context).also { instance = it }
        }
    }
}
