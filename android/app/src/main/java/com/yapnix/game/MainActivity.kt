package com.yapnix.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
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
import coil.compose.AsyncImage
import com.yapnix.game.updates.UpdatePanel
import com.yapnix.game.data.Game
import com.yapnix.game.recording.ReactionCamera
import com.yapnix.game.recording.RecordingsScreen
import com.yapnix.game.recording.ScreenRecordingService
import kotlinx.coroutines.delay

private val Ink = Color(0xFF232621)
private val Paper = Color(0xFFF6F5EF)
private val Muted = Color(0xFF75776D)
private val Lime = Color(0xFFD2EC87)
private val Lilac = Color(0xFFE9EDDE)
private val Peach = Color(0xFFF0E8D9)
private val Line = Color(0xFFE1E2D8)

class MainActivity : ComponentActivity() {
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameViewModel = ViewModelProvider(this, GameViewModel.Factory(application))[GameViewModel::class.java]
        intent.data?.let { gameViewModel.handleAuthCallback(it); intent.data = null }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Ink, onPrimary = Color.White, background = Paper,
                surface = Color.White, onSurface = Ink, onBackground = Ink,
                secondary = Ink, secondaryContainer = Lime, onSecondaryContainer = Ink,
                surfaceContainer = Paper, surfaceContainerHigh = Lilac,
                surfaceContainerHighest = Lilac, outline = Line,
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
    var x by mutableFloatStateOf(.5f)
    var y by mutableFloatStateOf(.5f)
    var size by mutableFloatStateOf(.72f)
    var round by mutableStateOf(false)
    var mirrored by mutableStateOf(true)
    @Volatile var frame: Bitmap? = null
    val ratio get() = ReelGeometry.ratio(round)
    val maxSize get() = ReelGeometry.maxSize(round)
    fun reset() { x = .5f; y = .5f; size = .72f; round = false; mirrored = true }
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
    val tabs = listOf(AppScreen.HOME, AppScreen.PROGRESS, AppScreen.RECORDINGS, AppScreen.PROFILE)
    UpdatePanel(viewModel)
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
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = Lime, unselectedIconColor = Muted, unselectedTextColor = Muted))
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
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedCategory != null) RoundIcon("arrow", "All games", onClick = { onTap(); selectedCategory = null })
            Text("yapnix", modifier = Modifier.weight(1f).padding(start = if (selectedCategory != null) 12.dp else 0.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = Ink)
            IconButton(onClick = { onTap(); viewModel.refreshGames() }) { GameIcon("refresh", Modifier.size(20.dp), Muted, "Refresh games") }
            AccountAvatar(state, Modifier.size(40.dp).clickable { viewModel.navigate(AppScreen.PROFILE) })
        }
        Text(selectedGames?.firstOrNull()?.categoryName ?: "Let's play.", fontSize = 39.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = Ink)
        Text(if (selectedGames != null) "Choose your next round" else "Five seconds. First thought.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 26.dp))
        when {
            state.loading && state.games.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink, strokeWidth = 3.dp) }
            state.error != null && state.games.isEmpty() -> MessageCard("You're offline", state.error, "Try again", viewModel::refreshGames)
            state.games.isEmpty() -> MessageCard("More games soon", "Check back for a new round.", "Refresh", viewModel::refreshGames)
            selectedGames != null -> selectedGames.forEachIndexed { index, game ->
                GameCard(game, index) { onTap(); viewModel.openGame(game) }
                Spacer(Modifier.height(10.dp))
            }
            else -> {
                state.games.firstOrNull()?.let { featured ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink).clickable { onTap(); viewModel.openGame(featured) }.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ON YOUR MARK", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                            Text(featured.name, color = Paper, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) { GameIcon("play", Modifier.size(16.dp), Ink) }
                                Text("Play a round", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 9.dp))
                            }
                        }
                        GameIcon(if (featured.categoryName.contains("speak", true)) "mic" else "shapes", Modifier.padding(start = 12.dp).size(78.dp), Lime)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Find your game", fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp)
                    Text("${state.games.size} games", color = Muted, fontSize = 12.sp)
                }
                categories.entries.toList().chunked(2).forEachIndexed { row, group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEachIndexed { column, entry ->
                            CategoryCard(entry.value.first().categoryName, entry.value.size, row * 2 + column, Modifier.weight(1f)) { onTap(); selectedCategory = entry.key }
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
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            GameIcon(if (name.contains("speak", true) || name.contains("say", true)) "mic" else "shapes", Modifier.size(43.dp), Ink)
            Text("0${index + 1}", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(name, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-.6).sp)
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count ${if (count == 1) "game" else "games"}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            GameIcon("chevron", Modifier.size(17.dp), Ink)
        }
    }
}

@Composable
private fun GameCard(game: Game, index: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(57.dp).clip(RoundedCornerShape(12.dp)).background(if (index % 2 == 0) Lilac else Peach), contentAlignment = Alignment.Center) {
            GameIcon(if (game.categoryName.contains("speak", true)) "mic" else "picture", Modifier.size(28.dp), Ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(game.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${game.questionCount} pictures · 5s", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        GameIcon("chevron", Modifier.size(19.dp), Ink)
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
    var toolsOpen by remember { mutableStateOf(false) }
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
        ScreenBar("Ready to play", viewModel::backToHome) {
            if (ready && state.recordEnabled) RoundIcon("edit", "Camera settings", onClick = { toolsOpen = true })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(state.selectedGame?.name ?: "Your challenge", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("picture", "${state.playableLevel?.questions?.size ?: state.selectedGame?.questionCount ?: 0} pictures", Color.White, Muted)
                    Pill("camera", "HD · 9:16", Lilac, Ink)
                }
            }
            if (state.loading) {
                Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Lilac), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Ink, strokeWidth = 3.dp)
                    Text("Saving pictures · ${state.loadedImages}/${state.totalImages}", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                }
            } else if (state.error != null) {
                MessageCard("Couldn't load this game", state.error, "Back to games", viewModel::backToHome)
            } else if (ready) {
                val previewIndex = state.playableLevel?.questions?.indexOfFirst { it.id == previewQuestion?.id }?.coerceAtLeast(0) ?: 0
                ReactionStage(state.copy(questionIndex = previewIndex, phase = SessionPhase.PREPARING), state.recordEnabled, camera,
                    onError = { permissionMessage = it }, onCameraReady = { cameraReady = it })
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GameIcon("camera", Modifier.size(23.dp), Ink)
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

            permissionMessage?.let { Text(it, color = Color(0xFFB34242), fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }
        Button(onClick = {
            if (!state.recordEnabled || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRound()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = ready && !state.syncing && !pendingStart && !capture.starting && !capture.finalizing && (!state.recordEnabled || cameraReady),
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(58.dp), shape = RoundedCornerShape(20.dp)) {
            if (pendingStart || capture.starting || capture.finalizing) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
            else GameIcon("play", Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text(when { capture.finalizing -> "Saving video…"; pendingStart || capture.starting -> "Starting camera…"; else -> "Play now" }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (toolsOpen) CameraToolsDrawer(camera) { toolsOpen = false }
}

@Composable
private fun ReactionStage(state: GameUiState, showCamera: Boolean, camera: ReactionLayout, onError: (String) -> Unit, onCameraReady: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp))) {
        val widthPx = constraints.maxWidth.toFloat()
        val paneWidthPx = widthPx * ReelGeometry.PANE_WIDTH / ReelGeometry.WIDTH
        val paneHeightPx = widthPx * ReelGeometry.PANE_HEIGHT / ReelGeometry.WIDTH
        Canvas(Modifier.fillMaxSize()) { drawGameVideoFrame(drawContext.canvas.nativeCanvas, state, camera, includeCamera = false, renderWidth = size.width, renderHeight = size.height) }
        if (showCamera) {
            val rect = ReelGeometry.cameraRect(camera.x, camera.y, camera.size, camera.round)
            val shape = if (camera.round) CircleShape else RoundedCornerShape(8.dp)
            Box(Modifier.offset { IntOffset((rect.left * widthPx / ReelGeometry.WIDTH).toInt(), (rect.top * widthPx / ReelGeometry.WIDTH).toInt()) }
                .size(maxWidth * (rect.width / ReelGeometry.WIDTH), maxWidth * (rect.height / ReelGeometry.WIDTH))
                .pointerInput(widthPx, camera.round) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        camera.size = (camera.size * zoom).coerceIn(.30f, camera.maxSize)
                        camera.x = (camera.x + pan.x / (paneWidthPx * (1-camera.size)).coerceAtLeast(1f)).coerceIn(0f,1f)
                        camera.y = (camera.y + pan.y / (paneHeightPx-paneWidthPx*camera.size*camera.ratio).coerceAtLeast(1f)).coerceIn(0f,1f)
                    }
                }.clip(shape)) {
                ReactionCamera(modifier = Modifier.fillMaxSize(), onError = onError, onFrame = { camera.frame = it }, mirrored = camera.mirrored, onReady = onCameraReady)
            }
        } else {
            Row(Modifier.offset(y = maxWidth * (ReelGeometry.CAMERA_TOP / ReelGeometry.WIDTH)).fillMaxWidth().height(maxWidth * (ReelGeometry.PANE_HEIGHT / ReelGeometry.WIDTH)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GameIcon("camera", Modifier.size(18.dp), Paper.copy(alpha = .5f))
                Text("Camera off", color = Paper.copy(alpha = .5f), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraToolsDrawer(camera: ReactionLayout, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Camera", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp, modifier = Modifier.weight(1f))
                RoundIcon("close", "Close camera settings", onClose)
            }
            Text("Drag or pinch your reaction to frame it.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon("resize", Modifier.size(20.dp), Muted)
                Slider(value = camera.size.coerceIn(.30f, camera.maxSize), onValueChange = { camera.size = it }, valueRange = .30f..camera.maxSize, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                Text("${(camera.size * 100).toInt()}%", color = Muted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraTool("mirror", "Mirror", camera.mirrored, Modifier.weight(1f)) { camera.mirrored = !camera.mirrored }
                CameraTool(if (camera.round) "circle" else "square", "Shape", camera.round, Modifier.weight(1f)) { camera.round = !camera.round; camera.size = camera.size.coerceAtMost(camera.maxSize) }
                CameraTool("reset", "Reset", false, Modifier.weight(1f), camera::reset)
            }
        }
    }
}

@Composable
private fun CameraTool(icon: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(if (active) Lime else Color.White).clickable(onClick = onClick).padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GameIcon(icon, Modifier.size(23.dp), Ink)
        Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
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
    var toolsOpen by remember { mutableStateOf(false) }
    var cameraStreaming by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val readyToPrompt = !state.recordEnabled || !capture.active || cameraStreaming || cameraError != null
    val latestState by rememberUpdatedState(state)
    SideEffect {
        ScreenRecordingService.drawFrame = if (capture.active && cameraStreaming && cameraError == null) {
            { canvas -> drawGameVideoFrame(canvas, latestState, camera) }
        } else null
    }
    DisposableEffect(Unit) {
        onDispose { ScreenRecordingService.drawFrame = null; audio.close(); ScreenRecordingService.stop(context) }
    }
    LaunchedEffect(Unit) { audio.start() }
    LaunchedEffect(state.paused) { if (state.paused) { audio.stop(); ScreenRecordingService.stop(context) } }
    // Images were decoded before Play. Mark the current picture ready with no visual delay.
    LaunchedEffect(question?.id) { if (question != null && state.readyImages[question.id] != null) viewModel.imageReady(question.id) }
    LaunchedEffect(question?.id, state.phase, state.imageReadyId, state.paused, readyToPrompt) {
        if (readyToPrompt && !state.paused && question != null && state.phase == SessionPhase.PREPARING && state.imageReadyId == question.id && state.promptReadyId != question.id) {
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
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("pause", "Pause round", onClick = { viewModel.setForeground(false) })
            Text(state.selectedGame?.name ?: "5 second challenge", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (capture.active) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFC85442)))
                Text(" REC", color = Color(0xFFAD4537), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                RoundIcon("edit", "Camera settings", onClick = { toolsOpen = true })
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val reelWidth = minOf(maxWidth, maxHeight * (9f / 16f))
            ReactionStage(state, capture.active && !state.paused, camera,
                onError = { cameraError = it; viewModel.showNotice(it); ScreenRecordingService.stop(context) },
                onCameraReady = { cameraStreaming = it }, modifier = Modifier.width(reelWidth))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GameIcon(if (capture.active) "mic" else "volume", Modifier.size(15.dp), Muted)
            Text(if (capture.active) "Your reaction + sound · HD" else "Say your answer out loud", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 7.dp))
        }
        (cameraError ?: capture.error)?.let { Text(it, color = Color(0xFFB34242), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)) }
    }
    if (toolsOpen) CameraToolsDrawer(camera) { toolsOpen = false }
    if (state.paused) AlertDialog(onDismissRequest = viewModel::resumeSession,
        icon = { GameIcon("pause", Modifier.size(32.dp), Ink) }, title = { Text("Take a breather") },
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
        Box(Modifier.size(110.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) { GameIcon("check", Modifier.size(52.dp), Ink) }
        Spacer(Modifier.height(28.dp))
        Text("Round complete.", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text("${state.playableLevel?.questions?.size ?: 0} pictures. Nice one.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(30.dp))
        if (state.recordEnabled) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    capture.finalizing || capture.active -> { CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp); Text("Saving your reaction…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                    capture.saved -> { GameIcon("check", Modifier.size(25.dp), Ink); Text("Your video is ready", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
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
        Text("Progress", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 28.dp))
        Text("Keep the rounds coming.", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 7.dp, bottom = 25.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${state.progress.count { it.completed }}", "Completed", Lime, Modifier.weight(1f))
            StatCard("${state.progress.size}", "Games played", Color.White, Modifier.weight(1f))
        }
        Text("Recent rounds", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 14.dp))
        if (state.progress.isEmpty()) MessageCard("Your first round is waiting", "Choose a game to get started.", "Find a game", viewModel::backToHome)
        state.progress.sortedByDescending { it.lastPlayedAt }.forEach { save ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(if (save.completed) Lime else Lilac), contentAlignment = Alignment.Center) { GameIcon(if (save.completed) "check" else "play", Modifier.size(20.dp), Ink) }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(save.levelName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(if (save.completed) "Completed" else "Ready to continue", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                state.games.firstOrNull { it.sessionId == save.levelId }?.let { game -> RoundIcon("chevron", "Play ${game.name}", onClick = { viewModel.openGame(game) }) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileScreen(state: GameUiState, viewModel: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Text("You", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 28.dp, bottom = 27.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(state, Modifier.size(76.dp))
            Column(Modifier.padding(start = 17.dp).weight(1f)) {
                Text(state.authSession?.displayName?.takeIf { it.isNotBlank() } ?: state.authSession?.email?.substringBefore('@') ?: "Guest player", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.authSession?.email ?: "Ready when you are", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(22.dp))
        AccountPanel(state, viewModel, showIdentity = false)
        Text("YOUR APP", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 32.dp, bottom = 12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)) {
            SettingsRow("film", "My videos", "Saved on this device", viewModel::openRecordings)
            HorizontalDivider(Modifier.padding(horizontal = 17.dp), color = Line)
            SettingsRow("download", if (state.checkingUpdate) "Checking for updates…" else "Check for updates", "Yapnix ${BuildConfig.VERSION_NAME}", { viewModel.checkForUpdates(force = true) }, enabled = !state.checkingUpdate)
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun SettingsRow(icon: String, title: String, caption: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 17.dp, vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(icon, Modifier.size(23.dp), Ink)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(caption, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        GameIcon("chevron", Modifier.size(18.dp), Muted)
    }
}

@Composable
private fun AccountAvatar(state: GameUiState, modifier: Modifier = Modifier) {
    val session = state.authSession
    val url = session?.avatarUrl
    var failed by remember(url) { mutableStateOf(false) }
    val label = session?.displayName?.takeIf { it.isNotBlank() } ?: session?.email?.substringBefore('@')
    Box(modifier.clip(CircleShape).background(Lilac), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank() && !failed) AsyncImage(model = url, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        else if (!label.isNullOrBlank()) Text(label.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        else Icon(androidx.compose.ui.res.painterResource(R.drawable.nav_profile), contentDescription = "Guest profile", tint = Ink, modifier = Modifier.fillMaxSize(.48f))
    }
}

@Composable
private fun AccountPanel(state: GameUiState, viewModel: GameViewModel, showIdentity: Boolean = true) {
    val context = LocalContext.current
    var confirmSync by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.authSession == null) {
            if (showIdentity) Text("Guest player", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Sign in to sync your progress.", color = Muted, fontSize = 12.sp)
            OutlinedButton(enabled = !state.accountBusy, onClick = { viewModel.beginGoogleSignIn { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }, shape = RoundedCornerShape(16.dp)) { Text(if (state.accountBusy) "Opening Google…" else "Sign in with Google") }
        } else {
            if (showIdentity) Text(state.authSession.email, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
private fun ScreenBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon("arrow", "Back", onClick = onBack)
        Text(title, modifier = Modifier.weight(1f).padding(start = 12.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        trailing?.invoke()
    }
}

@Composable
private fun RoundIcon(icon: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White)) { GameIcon(icon, Modifier.size(21.dp), Ink, description) }
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
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(color).padding(20.dp)) {
        Text(number, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, color = Ink.copy(alpha = .6f), fontSize = 12.sp)
    }
}

@Composable
private fun MessageCard(title: String, message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 19.sp)
        Text(message, color = Muted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onAction) { Text(action, color = Ink) }
    }
}
