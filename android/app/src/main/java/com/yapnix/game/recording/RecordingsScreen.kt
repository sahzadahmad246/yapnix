package com.yapnix.game.recording

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yapnix.game.GameIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val ClipsBackground = Color(0xFFF7F5FC)
private val ClipsInk = Color(0xFF252138)
private val ClipsPurple = Color(0xFF7352EF)
private val ClipsMuted = Color(0xFF827C94)

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RecordingStore(context) }
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf(emptyList<File>()) }
    var loading by remember { mutableStateOf(true) }
    val capture by ScreenRecordingService.status.collectAsState()
    LaunchedEffect(capture) {
        recordings = withContext(Dispatchers.IO) { store.list() }
        loading = false
    }
    var preview by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destination ->
        val file = exporting
        exporting = null
        if (destination != null && file != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) { store.export(file, destination) }
                "Video saved"
            }.getOrElse { "Couldn't save. Your video is still here." }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().background(ClipsBackground).padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.White, CircleShape)
                .semantics { contentDescription = "Back to games" }) {
                GameIcon("arrow", Modifier.size(22.dp), ClipsInk)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Your clips", color = ClipsInk, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("Saved on this phone", color = ClipsMuted, fontSize = 12.sp)
            }
            IconButton(onClick = {
                scope.launch { recordings = withContext(Dispatchers.IO) { store.list() } }
            }, modifier = Modifier.semantics { contentDescription = "Refresh recordings" }) {
                GameIcon("refresh", Modifier.size(22.dp), ClipsPurple)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (capture.finalizing || busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape), color = ClipsPurple,
                trackColor = ClipsPurple.copy(alpha = .1f))
            Text(if (capture.finalizing) "Finishing your clip…" else "Saving…", color = ClipsMuted,
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        if (message.isNotEmpty()) Text(message, color = ClipsPurple, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(ClipsPurple.copy(alpha = .08f)).padding(14.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ClipsPurple, modifier = Modifier.size(32.dp))
            }
        } else if (recordings.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(100.dp).background(ClipsPurple.copy(alpha = .10f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center) { GameIcon("film", Modifier.size(44.dp), ClipsPurple) }
                Spacer(Modifier.height(20.dp))
                Text("Your next great reaction", color = ClipsInk, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Play with the camera on to save a clip.", color = ClipsMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = ClipsPurple),
                    shape = RoundedCornerShape(16.dp)) {
                    GameIcon("play", Modifier.size(18.dp), Color.White)
                    Text("Play a game", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(recordings, key = { it.name }) { file ->
                    RecordingCard(file, onPlay = { preview = file }, onSave = {
                        exporting = file
                        exporter.launch(file.name)
                    }, onShare = {
                        runCatching {
                            val uri = store.uri(file)
                            val intent = Intent(Intent.ACTION_SEND).setType("video/mp4")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intent.clipData = ClipData.newUri(context.contentResolver, "Yapnix recording", uri)
                            context.startActivity(Intent.createChooser(intent, "Share clip"))
                        }.onFailure { message = "No sharing app available" }
                    }, onDelete = { deleting = file }, actionsEnabled = !busy && exporting == null)
                }
            }
        }
    }
    preview?.let { file ->
        RecordingPreview(file, store, onClose = { preview = null }, onError = {
            preview = null
            message = "Couldn't play this clip. Try saving it to open in a video app."
        })
    }
    deleting?.let { file ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = Color.White,
            title = { Text("Delete clip?", color = ClipsInk, fontWeight = FontWeight.Bold) },
            text = { Text("This removes it from this phone.", color = ClipsMuted) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                scope.launch {
                    val removed = withContext(Dispatchers.IO) { store.delete(file) }
                    message = if (removed) "Clip deleted" else "Couldn't delete this clip"
                    recordings = withContext(Dispatchers.IO) { store.list() }
                }
            }) { Text("Delete", color = Color(0xFFCC4646)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep", color = ClipsPurple) } })
    }
}

private data class ClipInfo(val thumbnail: Bitmap?, val durationSeconds: Long)

@Composable
private fun RecordingCard(
    file: File,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean,
) {
    val info by produceState<ClipInfo?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val duration = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
                val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 640)
                else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ClipInfo(thumbnail, duration)
            } catch (_: Exception) {
                ClipInfo(null, 0)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    Card(onClick = onPlay, shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(ClipsInk)) {
            info?.thumbnail?.let { Image(it.asImageBitmap(), contentDescription = "Recording thumbnail",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.align(Alignment.Center).size(58.dp).background(Color.White.copy(alpha = .95f), CircleShape),
                contentAlignment = Alignment.Center) { GameIcon("play", Modifier.size(25.dp), ClipsPurple) }
            info?.let { clip ->
                Text("${clip.durationSeconds / 60}:${(clip.durationSeconds % 60).toString().padStart(2, '0')}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        .background(ClipsInk.copy(alpha = .8f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())),
                    color = ClipsInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(formatClipSize(file.length()), color = ClipsMuted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ClipAction("download", "Save", onSave, actionsEnabled)
                ClipAction("share", "Share", onShare, actionsEnabled)
                ClipAction("trash", "Delete", onDelete, actionsEnabled, color = ClipsMuted)
            }
        }
    }
}

@Composable
private fun ClipAction(icon: String, label: String, onClick: () -> Unit, enabled: Boolean, color: Color = ClipsPurple) {
    TextButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        GameIcon(icon, Modifier.size(18.dp), if (enabled) color else ClipsMuted.copy(alpha = .4f))
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RecordingPreview(file: File, store: RecordingStore, onClose: () -> Unit, onError: () -> Unit) {
    var video by remember(file.absolutePath) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(file.absolutePath) { onDispose { video?.stopPlayback() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.94f).heightIn(max = 680.dp).clip(RoundedCornerShape(28.dp)).background(ClipsInk)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Your reaction", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close preview" }) {
                    GameIcon("close", Modifier.size(22.dp), Color.White)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(9f / 16f), contentAlignment = Alignment.Center) {
                AndroidView(factory = { context ->
                    VideoView(context).apply {
                        val controls = MediaController(context)
                        controls.setAnchorView(this)
                        setMediaController(controls)
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                        setOnErrorListener { _, _, _ -> onError(); true }
                        setVideoURI(store.uri(file))
                        video = this
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun formatClipSize(bytes: Long): String = if (bytes >= 1024 * 1024)
    String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f)) else "${bytes / 1024} KB"
