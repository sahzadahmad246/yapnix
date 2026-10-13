package com.yapnix.game.recording

import android.content.ClipData
import android.content.Intent
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RecordingStore(context) }
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf(store.list()) }
    val capture by ScreenRecordingService.status.collectAsState()
    LaunchedEffect(capture) { recordings = withContext(Dispatchers.IO) { store.list() } }
    var preview by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    var video by remember { mutableStateOf<VideoView?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destination ->
        val file = exporting
        exporting = null
        if (destination != null && file != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) { store.export(file, destination) }; "Video saved to your chosen location" }
                .getOrElse { "Could not export the video. The original is still here." }
        }
    }
    DisposableEffect(video) { val current = video; onDispose { current?.stopPlayback() } }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Games") }
        Text("Your recordings", style = MaterialTheme.typography.headlineMedium)
        Text("Stored only on this device. Export or share a video when you choose.")
        if (message.isNotEmpty()) Text(message)
        Button(onClick = { recordings = store.list() }) { Text("Refresh") }
        if (recordings.isEmpty()) Text("No recordings yet. Turn on screen + camera recording before your next round.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(recordings, key = { it.name }) { file ->
                Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(16.dp)) {
                    Text(DateFormat.getDateTimeInstance().format(Date(file.lastModified())))
                    Text("${file.length() / 1024} KB")
                    Button(onClick = { preview = file }, modifier = Modifier.fillMaxWidth()) { Text("Play recording") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { exporting = file; exporter.launch(file.name) }) { Text("Save") }
                        TextButton(onClick = {
                            runCatching {
                                val uri = store.uri(file)
                                val intent = Intent(Intent.ACTION_SEND).setType("video/mp4")
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.clipData = ClipData.newUri(context.contentResolver, "Yapnix recording", uri)
                                context.startActivity(Intent.createChooser(intent, "Share recording"))
                            }.onFailure { message = "No app is available to share this recording" }
                        }) { Text("Share") }
                        TextButton(onClick = { deleting = file }) { Text("Delete") }
                    }
                } }
            }
        }
    }
    preview?.let { file ->
        AlertDialog(onDismissRequest = { video?.stopPlayback(); video = null; preview = null }, title = { Text("Recording preview") },
            text = { AndroidView(factory = { VideoView(it).apply {
                setVideoURI(store.uri(file)); setOnPreparedListener { player -> player.isLooping = true; start() }; video = this
            } }, modifier = Modifier.fillMaxWidth().height(360.dp)) },
            confirmButton = { TextButton(onClick = { video?.stopPlayback(); video = null; preview = null }) { Text("Close") } })
    }
    deleting?.let { file ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this recording?") },
            text = { Text("This removes the local video. Exported copies and game progress are kept.") },
            confirmButton = { TextButton(onClick = {
                message = if (store.delete(file)) "Recording deleted" else "Could not delete this recording"
                recordings = store.list(); deleting = null
            }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
}
