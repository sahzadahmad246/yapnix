package com.yapnix.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.yapnix.game.audio.RoundAudio
import com.yapnix.game.data.Game
import com.yapnix.game.recording.ReactionCamera
import com.yapnix.game.recording.RecordingsScreen
import com.yapnix.game.recording.ScreenRecordingService
import kotlinx.coroutines.delay

private val Ink = Color(0xFF252138)
private val Paper = Color(0xFFF7F5FC)
private val Purple = Color(0xFF7352EF)
private val Muted = Color(0xFF827C94)
private val Lime = Color(0xFFB6EF70)
private val Coral = Color(0xFFFF907A)
private val Lilac = Color(0xFFE9E0FF)
private val Mint = Color(0xFFDDF4DD)
private val Peach = Color(0xFFFFE3CC)
private val Sky = Color(0xFFDDEBFF)

class MainActivity : ComponentActivity() {
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameViewModel = ViewModelProvider(this, GameViewModel.Factory(application))[GameViewModel::class.java]
        intent.data?.let { gameViewModel.handleAuthCallback(it); intent.data = null }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Purple, onPrimary = Color.White, background = Paper,
                surface = Color.White, onSurface = Ink, onBackground = Ink,
                secondary = Purple, secondaryContainer = Lilac, onSecondaryContainer = Purple,
                surfaceContainer = Paper, surfaceContainerHigh = Lilac,
                surfaceContainerHighest = Color(0xFFEDE9F5), outline = Color(0xFFD9D3E7),
            )) {
                Surface(Modifier.fillMaxSize(), color = Paper) { YapnixApp(gameViewModel) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { gameViewModel.handleAuthCallback(it); intent.data = null }
        setIntent(intent)
    }

    override fun onResume() { super.onResume(); gameViewModel.checkForUpdates() }
    override fun onStop() { gameViewModel.setForeground(false); ScreenRecordingService.stop(this); super.onStop() }
}

@Stable
internal class ReactionLayout {
    var x by mutableFloatStateOf(.94f)
    var y by mutableFloatStateOf(.94f)
    var size by mutableFloatStateOf(.31f)
    var round by mutableStateOf(false)
    var mirrored by mutableStateOf(true)
    @Volatile var frame: Bitmap? = null
    val ratio get() = if (round) 1f else 1.25f
    fun reset() { x = .94f; y = .94f; size = .31f; round = false; mirrored = true }
}

@Composable
private fun YapnixApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val capture by ScreenRecordingService.status.collectAsState()
    val context = LocalContext.current
    val camera = remember { ReactionLayout() }
    val sounds = remember { RoundAudio(context) }
    DisposableEffect(Unit) { onDispose { sounds.close() } }
    LaunchedEffect(capture.saved, capture.error) {
        if (capture.saved) viewModel.showNotice("Video saved")
        capture.error?.let(viewModel::showNotice)
    }
    val update = state.availableUpdate
    val tabs = listOf(AppScreen.HOME, AppScreen.PROGRESS, AppScreen.RECORDINGS, AppScreen.PROFILE)
    if (state.screen in tabs && update != null && state.dismissedUpdate != update.version) {
        AlertDialog(onDismissRequest = viewModel::dismissUpdate,
            title = { Text("Fresh Yapnix, ready to play") },
            text = { Text("Version ${update.version.removePrefix("v")} is ready. Download and open the APK to update.") },
            confirmButton = { TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url))) }
                    .onFailure { viewModel.showNotice("Could not open the download. Try Profile.") }
                viewModel.dismissUpdate()
            }) { Text("Download") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("Later") } })
    }
    BackHandler(enabled = state.screen != AppScreen.HOME) {
        if (state.screen == AppScreen.SESSION) viewModel.setForeground(false)
        else viewModel.backToHome()
    }
    Scaffold(containerColor = Paper, bottomBar = {
        if (state.screen in tabs) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            tabs.forEachIndexed { index, screen ->
                NavigationBarItem(selected = state.screen == screen,
                    onClick = { sounds.click(); viewModel.navigate(screen) },
                    icon = { Icon(androidx.compose.ui.res.painterResource(listOf(R.drawable.nav_home, R.drawable.nav_progress, R.drawable.nav_recordings, R.drawable.nav_profile)[index]), contentDescription = null, modifier = Modifier.size(23.dp)) },
                    label = { Text(listOf("Play", "Progress", "Videos", "You")[index], fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple, selectedTextColor = Purple, indicatorColor = Lilac, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                AppScreen.HOME -> HomeScreen(state, viewModel) { sounds.click() }
                AppScreen.SETUP -> GameSetupScreen(state, viewModel, camera)
                AppScreen.SESSION -> SessionScreen(state, viewModel, camera)
                AppScreen.RESULTS -> ResultsScreen(state, viewModel)
                AppScreen.PROGRESS -> ProgressScreen(state, viewModel)
                AppScreen.RECORDINGS -> RecordingsScreen(viewModel::backToHome)
                AppScreen.PROFILE -> ProfileScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: GameUiState, viewModel: GameViewModel, onTap: () -> Unit) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    BackHandler(selectedCategory != null) { selectedCategory = null }
    val categories = state.games.groupBy { it.categoryId }
    val selectedGames = selectedCategory?.let { categories[it] }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedCategory != null) RoundIcon("arrow", "All games", onClick = { onTap(); selectedCategory = null })
            else Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Purple), contentAlignment = Alignment.Center) {
                GameIcon("spark", Modifier.size(22.dp), Color.White)
            }
            Text("yapnix", modifier = Modifier.padding(start = 10.dp).weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink)
            RoundIcon("refresh", "Refresh games", onClick = { onTap(); viewModel.refreshGames() })
        }
        Text(selectedGames?.firstOrNull()?.categoryName ?: "Small games.\nBig reactions.", fontSize = 37.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("clock", "5 seconds", Lilac, Purple)
            Pill("volume", "Say it out loud", Color.White, Muted)
        }
        Spacer(Modifier.height(28.dp))
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Purple, strokeWidth = 3.dp) }
            state.error != null && state.games.isEmpty() -> MessageCard("You're offline", state.error, "Try again", viewModel::refreshGames)
            state.games.isEmpty() -> MessageCard("Games are on the way", "Come back for a new challenge.", "Refresh", viewModel::refreshGames)
            selectedGames != null -> {
                Text("Pick a challenge", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                selectedGames.forEachIndexed { index, game ->
                    GameCard(game, index) { onTap(); viewModel.openGame(game) }
                    Spacer(Modifier.height(12.dp))
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Explore games", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("${state.games.size} to play", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                categories.entries.toList().chunked(2).forEachIndexed { row, group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEachIndexed { column, entry ->
                            val games = entry.value
                            CategoryCard(games.first().categoryName, games.size, row * 2 + column, Modifier.weight(1f)) { onTap(); selectedCategory = entry.key }
                        }
                        if (group.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryCard(name: String, count: Int, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = listOf(Lilac, Mint, Peach, Sky)[index % 4]
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(accent).clickable(onClick = onClick).padding(18.dp)) {
        Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                GameIcon(if (name.contains("speak", true) || name.contains("say", true)) "mic" else "shapes", Modifier.size(43.dp), Ink)
            }
            Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)), contentAlignment = Alignment.Center) { GameIcon("spark", Modifier.size(12.dp), Purple) }
        }
        Spacer(Modifier.height(14.dp))
        Text(name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count ${if (count == 1) "game" else "games"}", color = Ink.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            GameIcon("chevron", Modifier.size(18.dp), Ink)
        }
    }
}

@Composable
private fun GameCard(game: Game, index: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(listOf(Lilac, Mint, Peach, Sky)[index % 4]), contentAlignment = Alignment.Center) {
            GameIcon(if (game.categoryName.contains("speak", true)) "mic" else "picture", Modifier.size(32.dp), Ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 15.dp)) {
            Text(game.name, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${game.questionCount} pictures · 5s", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(Purple), contentAlignment = Alignment.Center) { GameIcon("play", Modifier.size(18.dp), Color.White) }
    }
}

@Composable
private fun GameSetupScreen(state: GameUiState, viewModel: GameViewModel, camera: ReactionLayout) {
    val context = LocalContext.current
    val capture by ScreenRecordingService.status.collectAsState()
    val pendingStartState = remember { mutableStateOf(false) }
    var pendingStart by pendingStartState
    DisposableEffect(context) {
        onDispose {
            // Read the state directly: navigation can dispose before another recomposition.
            if (pendingStartState.value) ScreenRecordingService.stop(context)
        }
    }
    var cameraReady by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val startRound = {
        permissionMessage = null
        if (state.recordEnabled) { pendingStart = true; ScreenRecordingService.start(context) }
        else viewModel.startSession()
    }
    LaunchedEffect(capture, pendingStart) {
        if (pendingStart && capture.active) { pendingStart = false; viewModel.startSession() }
        else if (pendingStart && capture.error != null) { pendingStart = false; permissionMessage = capture.error }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRound() else permissionMessage = "Allow microphone access to record sound."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setRecording(granted)
        if (!granted) permissionMessage = "Camera is off. You can still play."
    }
    val ready = !state.loading && state.error == null && state.playableLevel != null && state.selectedLevel != null
    val previewQuestion = state.playableLevel?.questions?.getOrNull(state.savedProgress?.takeUnless { it.completed }?.questionIndex ?: 0)
        ?: state.playableLevel?.questions?.firstOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        ScreenBar("Ready to play", viewModel::backToHome)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(state.selectedGame?.name ?: "Your challenge", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("picture", "${state.playableLevel?.questions?.size ?: state.selectedGame?.questionCount ?: 0} pictures", Color.White, Muted)
                    Pill("clock", "5s each", Lilac, Purple)
                }
            }
            if (state.loading) {
                Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Lilac), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Purple, strokeWidth = 3.dp)
                    Text("Saving pictures · ${state.loadedImages}/${state.totalImages}", color = Purple, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                }
            } else if (state.error != null) {
                MessageCard("Couldn't load this game", state.error, "Back to games", viewModel::backToHome)
            } else if (ready) {
                ReactionStage(state.readyImages[previewQuestion?.id], state.recordEnabled, camera,
                    onError = { permissionMessage = it }, onCameraReady = { cameraReady = it })
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GameIcon("camera", Modifier.size(23.dp), Purple)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Record my reaction", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Camera + microphone", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = state.recordEnabled, onCheckedChange = { enabled ->
                    permissionMessage = null
                    if (!enabled) { viewModel.setRecording(false); cameraReady = false }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) viewModel.setRecording(true)
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                })
            }
            if (ready && state.recordEnabled) CameraTools(camera)
            permissionMessage?.let { Text(it, color = Color(0xFFB34242), fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }
        Button(onClick = {
            if (!state.recordEnabled || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRound()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = ready && !state.syncing && !pendingStart && !capture.starting && !capture.finalizing && (!state.recordEnabled || cameraReady),
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(58.dp), shape = RoundedCornerShape(20.dp)) {
            if (pendingStart || capture.starting || capture.finalizing) CircularProgressIndicator(Modifier.size(19.dp), color = Purple, strokeWidth = 2.dp)
            else GameIcon("play", Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text(when { capture.finalizing -> "Saving video…"; pendingStart || capture.starting -> "Starting camera…"; else -> "Play now" }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReactionStage(bitmap: Bitmap?, showCamera: Boolean, camera: ReactionLayout, onError: (String) -> Unit, onCameraReady: (Boolean) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Lilac)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Game picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (showCamera) {
            val shape = if (camera.round) CircleShape else RoundedCornerShape(18.dp)
            Box(Modifier.offset { IntOffset((camera.x * width * (1-camera.size)).toInt(), (camera.y * (height-width*camera.size*camera.ratio).coerceAtLeast(0f)).toInt()) }
                .size(maxWidth * camera.size, maxWidth * camera.size * camera.ratio)
                .pointerInput(width, height) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        camera.size = (camera.size * zoom).coerceIn(.20f, .62f)
                        camera.x = (camera.x + pan.x / (width * (1-camera.size)).coerceAtLeast(1f)).coerceIn(0f,1f)
                        camera.y = (camera.y + pan.y / (height-width*camera.size*camera.ratio).coerceAtLeast(1f)).coerceIn(0f,1f)
                    }
                }.clip(shape).border(3.dp, Color.White, shape)) {
                ReactionCamera(modifier = Modifier.fillMaxSize(), onError = onError, onFrame = { camera.frame = it }, mirrored = camera.mirrored, onReady = onCameraReady)
            }
        }
    }
}

@Composable
private fun CameraTools(camera: ReactionLayout) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon("resize", Modifier.size(19.dp), Muted)
            Slider(value = camera.size, onValueChange = { camera.size = it }, valueRange = .20f.. .62f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            ToolIcon(if (camera.round) "circle" else "square", "Change camera shape", camera.round) { camera.round = !camera.round }
            ToolIcon("mirror", "Mirror camera", camera.mirrored) { camera.mirrored = !camera.mirrored }
            ToolIcon("reset", "Reset camera", false, camera::reset)
        }
        Text("Drag & pinch to frame your camera", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
    }
}

@Composable
private fun SessionScreen(state: GameUiState, viewModel: GameViewModel, camera: ReactionLayout) {
    val playable = state.playableLevel
    val question = playable?.questions?.getOrNull(state.questionIndex)
    val context = LocalContext.current
    val audio = remember { RoundAudio(context) }
    val capture by ScreenRecordingService.status.collectAsState()
    var feedbackReady by remember(question?.id) { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    DisposableEffect(Unit) {
        ScreenRecordingService.drawFrame = { canvas -> drawGameVideoFrame(canvas, latestState, camera) }
        onDispose { ScreenRecordingService.drawFrame = null; audio.close(); ScreenRecordingService.stop(context) }
    }
    LaunchedEffect(Unit) { audio.start() }
    LaunchedEffect(state.paused) { if (state.paused) { audio.stop(); ScreenRecordingService.stop(context) } }
    // Images were decoded before Play. Mark the current picture ready with no visual delay.
    LaunchedEffect(question?.id) { if (question != null && state.readyImages[question.id] != null) viewModel.imageReady(question.id) }
    LaunchedEffect(question?.id, state.phase, state.imageReadyId, state.paused) {
        if (!state.paused && question != null && state.phase == SessionPhase.PREPARING && state.imageReadyId == question.id && state.promptReadyId != question.id) {
            audio.speak(playable.prompt, question.language) { viewModel.promptReady(question.id) }
        }
        if (!state.paused && question != null && state.phase == SessionPhase.FEEDBACK && !feedbackReady) {
            audio.reveal()
            audio.speak(question.primaryAnswer, question.language) { feedbackReady = true }
        }
    }
    LaunchedEffect(state.phase, state.remainingSeconds, state.paused) {
        if (!state.paused && state.phase == SessionPhase.PLAYING) audio.tick()
    }
    LaunchedEffect(state.phase, state.questionIndex, state.remainingSeconds, state.paused) {
        if (!state.paused && state.phase == SessionPhase.PLAYING) { delay(1_000); viewModel.tickQuestion() }
    }
    LaunchedEffect(state.phase, state.questionIndex, state.paused, feedbackReady) {
        if (!state.paused && state.phase == SessionPhase.FEEDBACK && feedbackReady) { delay(1_200); viewModel.advanceQuestion() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("pause", "Pause round", onClick = { viewModel.setForeground(false) })
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(state.selectedGame?.name ?: "5 second challenge", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Picture ${state.questionIndex + 1} of ${playable?.questions?.size ?: 0}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (capture.active) Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE6E1)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE55757)))
                Text(" REC", color = Color(0xFFB34242), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(playable?.prompt ?: "What is this?", color = Ink, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).padding(end = 10.dp))
                val fraction by animateFloatAsState(if (state.phase == SessionPhase.PLAYING) state.remainingSeconds / 5f else if (state.phase == SessionPhase.FEEDBACK) 0f else 1f, tween(650), label = "Time remaining")
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        drawArc(Lilac, -90f, 360f, false, style = Stroke(4.dp.toPx()))
                        drawArc(if (state.remainingSeconds <= 2 && state.phase == SessionPhase.PLAYING) Coral else Purple, -90f, 360f * fraction, false, style = Stroke(4.dp.toPx()))
                    }
                    if (state.phase == SessionPhase.FEEDBACK) GameIcon("check", Modifier.size(26.dp), Purple)
                    else Text(if (state.phase == SessionPhase.PLAYING) "${state.remainingSeconds}" else "5", color = Purple, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
            ReactionStage(state.readyImages[question?.id], capture.active && !state.paused, camera, viewModel::showNotice)
            Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (state.phase == SessionPhase.FEEDBACK) {
                    Text("THE ANSWER IS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(question?.primaryAnswer.orEmpty(), color = Purple, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon("volume", Modifier.size(18.dp), Purple)
                    Text("Say it out loud!", color = Purple, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (capture.active) CameraTools(camera)
            if (capture.error != null) Text(capture.error.orEmpty(), color = Color(0xFFB34242), fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            Spacer(Modifier.height(14.dp))
        }
    }
    if (state.paused) AlertDialog(onDismissRequest = viewModel::resumeSession,
        icon = { GameIcon("pause", Modifier.size(32.dp), Purple) }, title = { Text("Take a breather") },
        text = { Text(when {
            capture.finalizing -> "Saving your video…"
            capture.saved -> "Your video is saved."
            capture.error != null -> capture.error.orEmpty()
            state.recordEnabled -> "Recording stops when paused."
            else -> "Ready when you are."
        }) },
        confirmButton = { TextButton(onClick = viewModel::resumeSession) { Text("Resume") } },
        dismissButton = { TextButton(onClick = viewModel::backToHome) { Text("Leave round") } })
}

@Composable
private fun ResultsScreen(state: GameUiState, viewModel: GameViewModel) {
    val capture by ScreenRecordingService.status.collectAsState()
    val context = LocalContext.current
    val audio = remember { RoundAudio(context) }
    LaunchedEffect(Unit) { audio.complete() }
    DisposableEffect(Unit) { onDispose { audio.close() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(138.dp).clip(RoundedCornerShape(44.dp)).background(Lime), contentAlignment = Alignment.Center) { GameIcon("spark", Modifier.size(76.dp), Ink) }
        Spacer(Modifier.height(28.dp))
        Text("That's a wrap!", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text("${state.playableLevel?.questions?.size ?: 0} pictures. All you.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(30.dp))
        if (state.recordEnabled) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    capture.finalizing || capture.active -> { CircularProgressIndicator(Modifier.size(22.dp), color = Purple, strokeWidth = 2.dp); Text("Saving your reaction…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                    capture.saved -> { GameIcon("check", Modifier.size(25.dp), Purple); Text("Your video is ready", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    capture.error != null -> Text(capture.error.orEmpty(), color = Color(0xFFB34242), fontSize = 13.sp)
                }
                TextButton(onClick = viewModel::openRecordings, enabled = !capture.finalizing && !capture.active) { GameIcon("film", Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("My videos") }
            }
            Spacer(Modifier.height(18.dp))
        }
        Button(onClick = viewModel::replay, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) {
            GameIcon("play", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Play again", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        TextButton(onClick = viewModel::backToHome, modifier = Modifier.padding(top = 8.dp)) { Text("Explore games", color = Muted) }
        if (state.showSignInPrompt && state.authSession == null) {
            Spacer(Modifier.height(14.dp))
            AccountPanel(state, viewModel)
            TextButton(onClick = viewModel::dismissSignInPrompt) { Text("Maybe later", color = Muted) }
        }
    }
}

@Composable
private fun ProgressScreen(state: GameUiState, viewModel: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(30.dp))
        Text("Your play streak", fontSize = 31.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${state.progress.count { it.completed }}", "Completed", Lilac, Modifier.weight(1f))
            StatCard("${state.progress.size}", "Games played", Mint, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        if (state.progress.isEmpty()) MessageCard("Your first round is waiting", "Pick a game and make some memories.", "Let's play", viewModel::backToHome)
        state.progress.sortedByDescending { it.lastPlayedAt }.forEach { save ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(if (save.completed) Mint else Lilac), contentAlignment = Alignment.Center) { GameIcon(if (save.completed) "check" else "play", Modifier.size(22.dp), Purple) }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(save.levelName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (save.completed) "Completed" else "In progress", color = Muted, fontSize = 12.sp)
                }
                state.games.firstOrNull { it.sessionId == save.levelId }?.let { game -> RoundIcon("chevron", "Play ${game.name}", onClick = { viewModel.openGame(game) }) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileScreen(state: GameUiState, viewModel: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("Your space", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Lilac), contentAlignment = Alignment.Center) { GameIcon("spark", Modifier.size(44.dp), Purple) }
        AccountPanel(state, viewModel)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon("film", Modifier.size(24.dp), Purple)
            Column(Modifier.padding(start = 14.dp)) { Text("Just yours", fontWeight = FontWeight.Bold); Text("Videos stay on this device.", color = Muted, fontSize = 12.sp) }
        }
        Column {
            Text("Yapnix ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
            TextButton(enabled = !state.checkingUpdate, onClick = { viewModel.checkForUpdates(force = true) }) {
                GameIcon("download", Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(if (state.checkingUpdate) "Checking…" else "Check for updates")
            }
        }
    }
}

@Composable
private fun AccountPanel(state: GameUiState, viewModel: GameViewModel) {
    val context = LocalContext.current
    var confirmSync by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.authSession == null) {
            Text("Guest player", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Sign in to sync your progress.", color = Muted, fontSize = 12.sp)
            OutlinedButton(enabled = !state.accountBusy, onClick = { viewModel.beginGoogleSignIn { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }, shape = RoundedCornerShape(16.dp)) { Text(if (state.accountBusy) "Opening Google…" else "Sign in with Google") }
        } else {
            Text(state.authSession.email, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row {
                OutlinedButton(enabled = !state.accountBusy && !state.syncing, onClick = { confirmSync = true }) { Text(if (state.syncing) "Syncing…" else "Sync progress") }
                TextButton(enabled = !state.accountBusy && !state.syncing, onClick = viewModel::signOut) { Text("Sign out", color = Muted) }
            }
        }
        state.notice?.let { Text(it, color = Muted, fontSize = 12.sp) }
    }
    if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Sync progress?") },
        text = { Text("Merge saves with ${state.authSession?.email}. Videos stay on this device.") },
        confirmButton = { TextButton(onClick = { confirmSync = false; viewModel.syncProgress() }) { Text("Sync now") } },
        dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Later") } })
}

@Composable
private fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon("arrow", "Back", onClick = onBack)
        Text(title, modifier = Modifier.padding(start = 12.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RoundIcon(icon: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White)) { GameIcon(icon, Modifier.size(21.dp), Ink, description) }
}

@Composable
private fun ToolIcon(icon: String, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(if (active) Lilac else Color.Transparent)) { GameIcon(icon, Modifier.size(20.dp), if (active) Purple else Muted, description) }
}

@Composable
private fun Pill(icon: String, label: String, background: Color, tint: Color) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(icon, Modifier.size(14.dp), tint)
        Text(label, modifier = Modifier.padding(start = 5.dp), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatCard(number: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(25.dp)).background(color).padding(20.dp)) {
        Text(number, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Ink)
        Text(label, color = Ink.copy(alpha = .6f), fontSize = 12.sp)
    }
}

@Composable
private fun MessageCard(title: String, message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 19.sp)
        Text(message, color = Muted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onAction) { Text(action, color = Purple) }
    }
}
