package com.yapnix.game.updates

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yapnix.game.AppScreen
import com.yapnix.game.GameIcon
import com.yapnix.game.GameViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UpdatePanel(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val downloads = remember { UpdateDownloads.get(context) }
    val download by downloads.state.collectAsStateWithLifecycle()
    val update = state.availableUpdate ?: return
    if (state.screen !in listOf(AppScreen.HOME, AppScreen.PROGRESS, AppScreen.RECORDINGS, AppScreen.PROFILE) ||
        state.dismissedUpdate == update.version) return
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var installAllowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    var openingInstaller by remember { mutableStateOf(false) }
    var installError by remember(update.version) { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installAllowed = context.packageManager.canRequestPackageInstalls()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installAllowed = context.packageManager.canRequestPackageInstalls()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val stage = if (download.version == update.version) download.stage else UpdateStage.IDLE
    val working = stage == UpdateStage.DOWNLOADING || stage == UpdateStage.VERIFYING
    val close = {
        if (working) downloads.cancel()
        viewModel.dismissUpdate()
    }
    AlertDialog(
        onDismissRequest = { if (!openingInstaller) close() },
        icon = { GameIcon("download", Modifier.size(28.dp), MaterialTheme.colorScheme.primary) },
        title = { Text("Yapnix ${update.version.removePrefix("v")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (stage) {
                    UpdateStage.IDLE -> "Update your installed Yapnix app here. Your local videos and progress stay on this device."
                    UpdateStage.DOWNLOADING -> "Downloading update…"
                    UpdateStage.VERIFYING -> "Checking update…"
                    UpdateStage.READY -> if (installAllowed) "Ready to install." else "Allow Yapnix to install this update in Android settings."
                    UpdateStage.FAILED -> download.message ?: "Download failed. Try again."
                })
                if (stage == UpdateStage.DOWNLOADING) {
                    if (download.totalBytes > 0) {
                        LinearProgressIndicator(progress = { (download.receivedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${download.receivedBytes * 100 / download.totalBytes}%", style = MaterialTheme.typography.labelMedium)
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (stage == UpdateStage.VERIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
                installError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!working) Button(enabled = !openingInstaller, onClick = {
                installError = null
                if (stage != UpdateStage.READY) downloads.start(update)
                else if (!installAllowed) {
                    runCatching {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    }.onFailure { installError = "Open Android settings to allow updates for Yapnix." }
                } else {
                    openingInstaller = true
                    scope.launch {
                        try {
                            context.startActivity(downloads.installationIntent(update))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            installError = failure.message ?: "Could not open the installer. Try again."
                        } finally { openingInstaller = false }
                    }
                }
            }) {
                Text(when {
                    stage == UpdateStage.READY && !installAllowed -> "Allow updates"
                    stage == UpdateStage.READY -> "Install update"
                    stage == UpdateStage.FAILED -> "Retry"
                    else -> "Update now"
                })
            }
        },
        dismissButton = {
            TextButton(enabled = !openingInstaller, onClick = close) { Text(if (working) "Cancel download" else "Later") }
        },
    )
}
