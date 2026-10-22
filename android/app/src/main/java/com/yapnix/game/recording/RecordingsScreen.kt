package com.yapnix.game.recording

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yapnix.game.GameIcon
import com.yapnix.game.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ClipsBackground = Color(0xFFF6F5EF)
private val ClipsInk = Color(0xFF232621)
private val ClipsAccent = Color(0xFFD2EC87)
private val ClipsMuted = Color(0xFF75776D)

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RecordingStore(context) }
    val thumbnails = remember { ClipThumbnailCache() }
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf(emptyList<LocalClip>()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val capture by ScreenRecordingService.status.collectAsState()
    LaunchedEffect(capture, refresh) {
        runCatching { withContext(Dispatchers.IO) { store.localClips() } }
            .onSuccess { recordings = it }
            .onFailure {
                if (it is CancellationException) throw it
                message = "Couldn't load your clips. Try refreshing."
            }
        loading = false
    }
    var preview by remember { mutableStateOf<LocalClip?>(null) }
    var deleting by remember { mutableStateOf<LocalClip?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
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
    Column(Modifier.fillMaxSize().background(ClipsBackground)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to games" }) {
                GameIcon("arrow", Modifier.size(23.dp), ClipsInk)
            }
            Text("Reels", color = ClipsInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = { refresh++ }, enabled = !loading,
                modifier = Modifier.semantics { contentDescription = "Refresh clips" }) {
                GameIcon("refresh", Modifier.size(21.dp), ClipsInk)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 9.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SAVED ON THIS PHONE", color = ClipsMuted, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, fontSize = 10.sp)
            Text("${recordings.size} ${if (recordings.size == 1) "clip" else "clips"}", color = ClipsInk,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(ClipsAccent, RoundedCornerShape(5.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp))
        }
        if (capture.finalizing || busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = ClipsInk, trackColor = ClipsAccent)
            Text(if (capture.finalizing) "Finishing your clip…" else "Saving…", color = ClipsMuted,
                fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        if (message.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                .background(Color.White, RoundedCornerShape(8.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = ClipsInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { message = "" },
                    modifier = Modifier.semantics { contentDescription = "Dismiss message" }) {
                    GameIcon("close", Modifier.size(18.dp), ClipsMuted)
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ClipsInk, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else if (recordings.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(82.dp).background(ClipsAccent, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center) { GameIcon("film", Modifier.size(34.dp), ClipsInk) }
                Spacer(Modifier.height(22.dp))
                Text("Make your first reel", color = ClipsInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Play a round with your camera on.", color = ClipsMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = ClipsInk,
                    contentColor = Color.White), shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)) {
                    GameIcon("play", Modifier.size(18.dp), ClipsAccent)
                    Text("Find a game", modifier = Modifier.padding(start = 9.dp))
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)) {
                items(recordings, key = { it.key }) { clip ->
                    RecordingTile(clip, thumbnails, onPlay = { preview = clip }, onSave = {
                        exporting = clip.file
                        exporter.launch(clip.file.name)
                    }, onShare = {
                        scope.launch {
                            runCatching {
                                val uri = withContext(Dispatchers.IO) { store.uri(clip.file) }
                                val intent = Intent(Intent.ACTION_SEND).setType("video/mp4")
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.clipData = ClipData.newUri(context.contentResolver, "Yapnix reel", uri)
                                context.startActivity(Intent.createChooser(intent, "Share reel"))
                            }.onFailure { message = "Couldn't share this clip. Try saving it first." }
                        }
                    }, onDelete = { deleting = clip }, actionsEnabled = !busy && exporting == null)
                }
            }
        }
    }
    preview?.let { clip ->
        RecordingPreview(clip, store, onClose = { preview = null }, onError = {
            preview = null
            message = "Couldn't play this clip. Save it to open in a video app."
        })
    }
    deleting?.let { clip ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = ClipsBackground,
            shape = RoundedCornerShape(18.dp),
            title = { Text("Delete reel?", color = ClipsInk, fontWeight = FontWeight.Bold) },
            text = { Text("This removes it from this phone.", color = ClipsMuted) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                scope.launch {
                    val removed = runCatching { withContext(Dispatchers.IO) { store.delete(clip.file) } }.getOrDefault(false)
                    message = if (removed) "Reel deleted" else "Couldn't delete this reel"
                    refresh++
                }
            }) { Text("Delete", color = Color(0xFFB83A3A)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep", color = ClipsInk) } })
    }
}

private data class LocalClip(val file: File, val modifiedAt: Long, val bytes: Long) {
    val key: String get() = "${file.absolutePath}:$modifiedAt:$bytes"
}

/** Called on IO; composing or scrolling the grid never stats the video files. */
private fun RecordingStore.localClips(): List<LocalClip> = list().map { LocalClip(it, it.lastModified(), it.length()) }

private data class ClipInfo(val thumbnail: Bitmap?, val durationSeconds: Long)

/** At most two frame readers and 4 MB of retained, small preview images. */
private class ClipThumbnailCache {
    private val readers = Semaphore(2)
    private val cache = object : LruCache<String, ClipInfo>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ClipInfo): Int = value.thumbnail?.allocationByteCount?.coerceAtLeast(1) ?: 1
    }

    suspend fun get(clip: LocalClip): ClipInfo = cache.get(clip.key) ?: readers.withPermit {
        cache.get(clip.key) ?: read(clip.file).also { cache.put(clip.key, it) }
    }

    @Suppress("DEPRECATION")
    private fun read(file: File): ClipInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
            // Never request a full-resolution HD frame for a small grid cell.
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 216, 384)
            } else {
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            }
            val thumbnail = frame?.let {
                val scale = minOf(216f / it.width, 384f / it.height, 1f)
                if (scale < 1f) Bitmap.createScaledBitmap(it, (it.width * scale).toInt().coerceAtLeast(1),
                    (it.height * scale).toInt().coerceAtLeast(1), true).also { _ -> it.recycle() } else it
            }
            ClipInfo(thumbnail, duration)
        } catch (_: Exception) {
            ClipInfo(null, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }
}

@Composable
private fun RecordingTile(
    clip: LocalClip,
    thumbnails: ClipThumbnailCache,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean,
) {
    val info by produceState<ClipInfo?>(null, clip.key) {
        value = withContext(Dispatchers.IO) { thumbnails.get(clip) }
    }
    val date = remember(clip.modifiedAt) { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(clip.modifiedAt)) }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(5.dp)).background(ClipsInk)
        .clickable(role = Role.Button, onClickLabel = "Play reel from $date", onClick = onPlay)) {
        info?.thumbnail?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (info == null) CircularProgressIndicator(color = ClipsAccent, modifier = Modifier.size(18.dp), strokeWidth = 1.5.dp)
            else GameIcon("film", Modifier.size(26.dp), Color.White.copy(alpha = .65f))
        }
        Box(Modifier.fillMaxWidth().height(66.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f)))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 7.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GameIcon("play", Modifier.size(12.dp), Color.White)
            Text(info?.let { durationLabel(it.durationSeconds) } ?: "—", color = Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp).weight(1f))
            Text(date, color = Color.White.copy(alpha = .88f), fontSize = 9.sp)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }, enabled = actionsEnabled,
                modifier = Modifier.size(44.dp).semantics { contentDescription = "Reel options for $date" }) {
                Box(Modifier.size(28.dp).background(Color.Black.copy(alpha = .38f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.recording_more), contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = Color.White)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ReelMenuAction("play", "Play", { menu = false; onPlay() })
                ReelMenuAction("download", "Save to device", { menu = false; onSave() })
                ReelMenuAction("share", "Share", { menu = false; onShare() })
                ReelMenuAction("trash", "Delete", { menu = false; onDelete() }, color = Color(0xFFB83A3A))
            }
        }
    }
}

@Composable
private fun ReelMenuAction(icon: String, text: String, onClick: () -> Unit, color: Color = ClipsInk) {
    DropdownMenuItem(text = { Text(text, color = color, fontSize = 14.sp) }, onClick = onClick,
        leadingIcon = { GameIcon(icon, Modifier.size(20.dp), color) })
}

@Composable
private fun RecordingPreview(clip: LocalClip, store: RecordingStore, onClose: () -> Unit, onError: () -> Unit) {
    var video by remember(clip.key) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(clip.key) { mutableStateOf(false) }
    var playing by remember(clip.key) { mutableStateOf(false) }
    var duration by remember(clip.key) { mutableIntStateOf(0) }
    var position by remember(clip.key) { mutableIntStateOf(0) }
    var seeking by remember(clip.key) { mutableStateOf(false) }
    var seekPosition by remember(clip.key) { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uri by produceState<Uri?>(null, clip.key) {
        runCatching { withContext(Dispatchers.IO) { store.uri(clip.file) } }
            .onSuccess { value = it }.onFailure {
                if (it is CancellationException) throw it
                onError()
            }
    }
    DisposableEffect(video, lifecycle) {
        val player = video
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { player?.pause(); playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player?.stopPlayback() }
    }
    LaunchedEffect(video, prepared) {
        val player = video ?: return@LaunchedEffect
        if (!prepared) return@LaunchedEffect
        while (true) {
            if (!seeking) position = runCatching { player.currentPosition }.getOrDefault(0)
            playing = player.isPlaying
            delay(200)
        }
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Box(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 110.dp), contentAlignment = Alignment.Center) {
                uri?.let { source ->
                    AndroidView(factory = { context ->
                        // A centered VideoView preserves the recorded aspect ratio, including older clips.
                        FrameLayout(context).apply {
                            val player = VideoView(context).apply {
                                setOnPreparedListener { media ->
                                    media.isLooping = true
                                    duration = media.duration.coerceAtLeast(0)
                                    prepared = true
                                    start()
                                    playing = true
                                }
                                setOnErrorListener { _, _, _ -> onError(); true }
                                setVideoURI(source)
                            }
                            addView(player, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                            video = player
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
                if (!prepared) CircularProgressIndicator(color = ClipsAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your reel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(remember(clip.modifiedAt) {
                        SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(Date(clip.modifiedAt))
                    }, color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close reel" }) {
                    GameIcon("close", Modifier.size(24.dp), Color.White)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Slider(value = if (seeking) seekPosition else position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { seeking = true; seekPosition = it },
                    onValueChangeFinished = {
                        video?.seekTo(seekPosition.toInt())
                        position = seekPosition.toInt()
                        seeking = false
                    }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = prepared,
                    colors = SliderDefaults.colors(thumbColor = ClipsAccent, activeTrackColor = ClipsAccent,
                        inactiveTrackColor = Color.White.copy(alpha = .2f)),
                    modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Reel playback position" })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        video?.let { player -> if (player.isPlaying) player.pause() else player.start(); playing = player.isPlaying }
                    }, enabled = prepared,
                        modifier = Modifier.semantics { contentDescription = if (playing) "Pause reel" else "Play reel" }) {
                        GameIcon(if (playing) "pause" else "play", Modifier.size(23.dp), Color.White)
                    }
                    Text("${durationLabel((if (seeking) seekPosition.toInt() else position) / 1000L)} / ${durationLabel(duration / 1000L)}",
                        color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    Text(formatClipSize(clip.bytes), color = Color.White.copy(alpha = .5f), fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

private fun durationLabel(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatClipSize(bytes: Long): String = if (bytes >= 1024 * 1024)
    String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f)) else "${bytes / 1024} KB"
