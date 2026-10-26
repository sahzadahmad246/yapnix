package com.yapnix.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    var mirrored by mutableStateOf(true)
    @Volatile var frame: Bitmap? = null
}

@Composable
private fun YapnixApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val capture by ScreenRecordingService.status.collectAsState()
    val context = LocalContext.current
    val camera = remember { ReactionLayout() }
    val sounds = remember { RoundAudio(context) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it) } }
    DisposableEffect(Unit) { onDispose { sounds.close() } }
    LaunchedEffect(capture.saved, capture.error) {
        if (capture.saved) viewModel.showNotice("Video saved")
        capture.error?.let(viewModel::showNotice)
    }
    val tabs = listOf(AppScreen.HOME, AppScreen.PROGRESS, AppScreen.RECORDINGS, AppScreen.PROFILE)
    UpdatePanel(viewModel)
    BackHandler(enabled = state.screen != AppScreen.HOME && state.screen != AppScreen.SESSION) {
        if (state.screen == AppScreen.SETUP || state.screen == AppScreen.RESULTS) viewModel.backToCategory()
        else if (state.screen != AppScreen.SESSION) viewModel.backToHome()
    }
    val onHome = state.screen == AppScreen.HOME && state.selectedCategoryId == null
    Scaffold(containerColor = if (onHome) Color(0xFFE7DFFA) else Paper, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (state.screen in tabs) HomeNavigation(state.screen) { screen ->
            sounds.click()
            viewModel.navigate(screen)
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Paper)) {
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
private fun HomeNavigation(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(AppScreen.HOME, AppScreen.PROGRESS, AppScreen.RECORDINGS, AppScreen.PROFILE)
    val labels = listOf("Home", "Progress", "Videos", "You")
    val icons = listOf(R.drawable.nav_home, R.drawable.nav_progress, R.drawable.nav_recordings, R.drawable.nav_profile)
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color = Line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(68.dp)) {
            tabs.forEachIndexed { index, screen ->
                val active = selected == screen
                Column(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(screen) }),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(28.dp).height(3.dp).background(if (active) Ink else Color.Transparent))
                    Spacer(Modifier.height(11.dp))
                    Icon(androidx.compose.ui.res.painterResource(icons[index]), contentDescription = null,
                        tint = if (active) Ink else Muted, modifier = Modifier.size(23.dp))
                    Text(labels[index], color = if (active) Ink else Muted, fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun YapnixWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(29.dp).graphicsLayer(rotationZ = -12f).clip(RoundedCornerShape(9.dp)).background(Ink), contentAlignment = Alignment.Center) {
            Text("y", color = Lime, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        }
        Text("yapnix", modifier = Modifier.padding(start = 7.dp), fontSize = 29.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1.7).sp, color = Ink)
        Box(Modifier.padding(start = 3.dp, top = 17.dp).size(6.dp).clip(CircleShape).background(Color(0xFF9672E6)))
    }
}

@Composable
private fun GameArtwork(url: String, modifier: Modifier, iconKey: String = "game-controller", containerColor: Color = Lilac) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(containerColor), contentAlignment = Alignment.Center) {
        if (url.isNotBlank() && !failed) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(), onError = { failed = true })
        else Icon(androidx.compose.ui.res.painterResource(catalogIconResource(iconKey)), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.fillMaxSize(.65f))
    }
}

@Composable
private fun PlayHero() {
    val density = LocalDensity.current.density
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val animation = rememberInfiniteTransition(label = "floating challenge")
    val float by animation.animateFloat(-7f, 7f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val turn by animation.animateFloat(-12f, 12f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "turn")
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * 14f * density }) {
            Text("A LITTLE PLAY. A BIG REACTION.", fontSize = 9.sp, letterSpacing = 1.1.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Think fast.\nSay it loud.", fontSize = 33.sp, lineHeight = 36.sp, letterSpacing = (-1.2).sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.padding(top = 14.dp))
            Text("One picture. Five seconds.\nWhat comes to mind?", fontSize = 12.sp, lineHeight = 19.sp, color = Ink.copy(alpha = .65f), modifier = Modifier.padding(top = 12.dp))
        }
        Box(Modifier.width(105.dp).height(170.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.offset(x = 8.dp, y = 44.dp).size(87.dp, 17.dp).clip(CircleShape).background(Color(0xFFB7AACF).copy(alpha = .25f)))
            Box(Modifier.offset(y = float.dp).size(85.dp).graphicsLayer(rotationZ = turn, rotationY = turn * 2, rotationX = -14f, cameraDistance = 16f * density)
                .shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color.White, Color(0xFFCFB9F5)))), contentAlignment = Alignment.Center) {
                Text("?", color = Ink, fontSize = 64.sp, fontWeight = FontWeight.Black)
            }
            GameIcon("spark", Modifier.align(Alignment.TopEnd).offset(y = (-float).dp).size(24.dp), Color(0xFF8D6AC8))
            Box(Modifier.align(Alignment.BottomStart).offset(y = (-float).dp).size(32.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) { GameIcon("play", Modifier.size(15.dp), Ink) }
        }
    }
}

@Composable
private fun HomeScreen(state: GameUiState, viewModel: GameViewModel, onTap: () -> Unit) {
    val context = LocalContext.current
    val selectedCategory = state.selectedCategoryId
    BackHandler(selectedCategory != null) { viewModel.selectCategory(null) }
    val categories = state.games.groupBy { it.categoryId }
    val selectedGames = selectedCategory?.let { categories[it] }
    var showTips by remember { mutableStateOf(false) }
    var detailGame by remember { mutableStateOf<Game?>(null) }
    val recent = state.progress.sortedByDescending { it.lastPlayedAt }.firstNotNullOfOrNull { save -> state.games.firstOrNull { it.sessionId == save.levelId } }
    val showDetails: (Game) -> Unit = { game -> onTap(); detailGame = game }
    val scrollState = key(selectedCategory) { rememberScrollState() }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        if (selectedGames == null) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFE7DFFA), Color(0xFFF0ECFF), Color(0xFFE9F2D6))))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    YapnixWordmark()
                    Spacer(Modifier.weight(1f))
                    if (state.authSession == null) FilledTonalButton(enabled = !state.accountBusy, onClick = { viewModel.beginGoogleSignIn { context.startActivity(Intent(Intent.ACTION_VIEW, it)) } }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)) { Text(if (state.accountBusy) "Opening…" else "Log in", fontWeight = FontWeight.Bold) }
                    else AccountAvatar(state, Modifier.size(43.dp).clickable { viewModel.navigate(AppScreen.PROFILE) })
                }
                PlayHero()
            }
        }
        Column(Modifier.padding(horizontal = 22.dp)) {
            if (selectedGames != null) {
                ScreenBar(selectedGames.first().categoryName, { viewModel.selectCategory(null) }) {
                    RoundIcon("help", "How to play", onClick = { showTips = true })
                }
                Text(selectedGames.first().categoryDescription.ifBlank { "A picture, a countdown and your first thought. Pick a game and let’s play." }, color = Muted, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Games", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 26.dp, bottom = 16.dp))
            }
            when {
                state.loading && state.games.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
                state.error != null && state.games.isEmpty() -> MessageCard("You're offline", state.error, "Try again", viewModel::refreshGames)
                state.games.isEmpty() -> MessageCard("More games soon", "Check back for a new round.", "Refresh", viewModel::refreshGames)
                selectedGames != null -> selectedGames.forEach { game ->
                    GameCard(game) { showDetails(game) }
                    Spacer(Modifier.height(12.dp))
                }
                else -> {
                    (recent ?: state.games.firstOrNull())?.let { featured ->
                        Text(if (recent != null) "LAST PLAYED" else "ON YOUR MARK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
                        GameCard(featured) { showDetails(featured) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Categories", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
                        IconButton(onClick = viewModel::refreshGames) { GameIcon("refresh", Modifier.size(20.dp), Muted, "Refresh games") }
                    }
                    categories.forEach { (id, games) ->
                        val game = games.first()
                        CategoryCard(game.categoryName, game.categoryIconUrl, game.categoryIconKey, games.size, Modifier.fillMaxWidth()) { onTap(); viewModel.selectCategory(id) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showTips) HowToPlaySheet { showTips = false }
    detailGame?.let { game ->
        GameDetailsSheet(game, onClose = { detailGame = null }) {
            detailGame = null
            viewModel.openGame(game)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailsSheet(game: Game, onClose: () -> Unit, onContinue: () -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .85f
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Paper) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GAME DETAILS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
                RoundIcon("close", "Close game details", onClick = onClose)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GameArtwork(game.iconUrl, Modifier.size(88.dp), game.iconKey)
                Column {
                    Text(game.categoryName, color = Muted, fontSize = 13.sp)
                    Text(game.name, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                Text(game.description.ifBlank { "Look at each picture and say your guess before the timer runs out. The answer appears when time is up." }, color = Muted, fontSize = 15.sp, lineHeight = 24.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon("picture", Modifier.size(18.dp), Muted)
                        Text("${game.questionCount} pictures", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 7.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon("clock", Modifier.size(18.dp), Muted)
                        Text("${game.timeLimitSeconds}s each", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 7.dp))
                    }
                }
                Text("Add your camera reaction on the next screen, or jump straight into the game.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(54.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Get ready", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                GameIcon("arrow", Modifier.size(18.dp).graphicsLayer(rotationZ = 180f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowToPlaySheet(onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper) {
        Column(Modifier.padding(horizontal = 26.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Small timer. Big energy.", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            listOf("Look at the picture and listen to the question.", "Say your guess out loud before the five seconds run out.", "The answer appears when time is up. Then it’s on to the next picture.", "Want to capture it? Add your reaction before you play. Videos stay on your device.").forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("0${index + 1}", color = Color(0xFF8563BC), fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) { Text("Let’s play") }
        }
    }
}

@Composable
private fun CategoryCard(name: String, iconUrl: String, iconKey: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        GameArtwork(iconUrl, Modifier.size(64.dp), iconKey, Color.Transparent)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count ${if (count == 1) "game" else "games"}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        }
        GameIcon("chevron", Modifier.size(19.dp), Muted)
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        GameArtwork(game.iconUrl, Modifier.size(64.dp), game.iconKey, Color.Transparent)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(game.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${game.questionCount} pictures · ${game.timeLimitSeconds}s each", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        GameIcon("chevron", Modifier.size(19.dp), Muted)
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
    var showTips by remember { mutableStateOf(false) }
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
        ScreenBar(state.selectedGame?.name ?: "Your challenge", viewModel::backToCategory) {
            if (ready && state.recordEnabled) RoundIcon("edit", "Camera settings", onClick = { toolsOpen = true })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameArtwork(state.selectedGame?.iconUrl.orEmpty(), Modifier.size(65.dp), state.selectedGame?.iconKey ?: "game-controller")
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(state.selectedGame?.description?.ifBlank { "Think fast and say your guess out loud." } ?: "Get ready for your next challenge.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                    TextButton(onClick = { showTips = true }, contentPadding = PaddingValues(0.dp)) { Text("How to play", fontWeight = FontWeight.Bold) }
                }
            }
            if (state.loading) {
                Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Lilac), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Ink, strokeWidth = 3.dp)
                    Text("Saving pictures · ${state.loadedImages}/${state.totalImages}", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                }
            } else if (state.error != null) {
                MessageCard("Couldn't load this game", state.error, "Back to games", viewModel::backToCategory)
            } else if (ready) {
                val previewIndex = state.playableLevel?.questions?.indexOfFirst { it.id == previewQuestion?.id }?.coerceAtLeast(0) ?: 0
                ReactionStage(state.copy(questionIndex = previewIndex, phase = SessionPhase.PREPARING), state.recordEnabled, camera,
                    onError = { permissionMessage = it }, onCameraReady = { cameraReady = it })
            }
            OutlinedButton(onClick = {
                permissionMessage = null
                if (state.recordEnabled) { viewModel.setRecording(false); cameraReady = false }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) viewModel.setRecording(true)
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }, enabled = !pendingStart && !capture.starting && !capture.finalizing, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (state.recordEnabled) Lime else Color.White), contentPadding = PaddingValues(17.dp)) {
                GameIcon("camera", Modifier.size(25.dp), Ink)
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(if (state.recordEnabled) "Reaction camera is ready" else "Add your reaction", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.recordEnabled) "Tap to play without recording" else "Capture your face + voice · saved on device", color = Muted, fontSize = 11.sp)
                }
                GameIcon(if (state.recordEnabled) "check" else "chevron", Modifier.size(20.dp), Ink)
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
    if (showTips) HowToPlaySheet { showTips = false }
}

@Composable
private fun ReactionStage(state: GameUiState, showCamera: Boolean, camera: ReactionLayout, onError: (String) -> Unit, onCameraReady: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(ReelGeometry.WIDTH / ReelGeometry.STAGE_HEIGHT)) {
        val widthPx = constraints.maxWidth.toFloat()
        Canvas(Modifier.fillMaxSize()) { drawGameVideoFrame(drawContext.canvas.nativeCanvas, state, camera, includeCamera = false, renderWidth = size.width, renderHeight = size.height, stageOnly = true) }
        if (showCamera) {
            val rect = ReelGeometry.cameraRect()
            Box(Modifier.offset { IntOffset((rect.left * widthPx / ReelGeometry.WIDTH).toInt(), (rect.top * widthPx / ReelGeometry.WIDTH).toInt()) }
                .size(maxWidth * (rect.width / ReelGeometry.WIDTH), maxWidth * (rect.height / ReelGeometry.WIDTH)).clip(RoundedCornerShape(12.dp))) {
                ReactionCamera(modifier = Modifier.fillMaxSize(), onError = onError, onFrame = { camera.frame = it }, mirrored = camera.mirrored, onReady = onCameraReady)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraToolsDrawer(camera: ReactionLayout, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text("Your reaction", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Your portrait sits beside the picture in your saved video.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
            FilledTonalButton(onClick = { camera.mirrored = !camera.mirrored }, modifier = Modifier.fillMaxWidth()) {
                GameIcon("mirror", Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(if (camera.mirrored) "Mirror camera: on" else "Mirror camera: off")
            }
        }
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
    var confirmingLeave by remember { mutableStateOf(false) }
    var resumeAfterCancel by remember { mutableStateOf(false) }
    val requestLeave = {
        resumeAfterCancel = !state.paused
        viewModel.setForeground(false)
        confirmingLeave = true
    }
    BackHandler { requestLeave() }
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
    LaunchedEffect(state.paused) {
        ScreenRecordingService.setPaused(state.paused)
        if (state.paused) { audio.stop(); Toast.makeText(context, "Game paused. Tap play to continue.", Toast.LENGTH_SHORT).show() }
    }
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
        ScreenBar(state.selectedGame?.name ?: "Your challenge", requestLeave) {
            RoundIcon(if (state.paused) "play" else "pause", if (state.paused) "Resume game" else "Pause game") {
                if (state.paused) viewModel.resumeSession() else viewModel.setForeground(false)
            }
        }
        LinearProgressIndicator(progress = { state.questionIndex.toFloat() / (playable?.questions?.size ?: 1).coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Color(0xFFA68ACE), trackColor = Line)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val reelWidth = minOf(maxWidth, maxHeight * (ReelGeometry.WIDTH / ReelGeometry.STAGE_HEIGHT))
            ReactionStage(state, state.recordEnabled && cameraError == null, camera,
                onError = { cameraError = it; viewModel.showNotice(it); ScreenRecordingService.stop(context) },
                onCameraReady = { cameraStreaming = it }, modifier = Modifier.width(reelWidth))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GameIcon(if (capture.active) "mic" else "volume", Modifier.size(15.dp), Muted)
            Text(if (state.paused) "Paused · tap play when you’re ready" else if (capture.active) "Recording your reaction" else "Say your answer out loud", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 7.dp))
        }
        (cameraError ?: capture.error)?.let { Text(it, color = Color(0xFFB34242), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)) }
    }
    if (confirmingLeave) {
        val cancel = { confirmingLeave = false; if (resumeAfterCancel) viewModel.resumeSession() }
        AlertDialog(onDismissRequest = cancel, title = { Text("Leave this round?") },
            text = { Text(if (state.recordEnabled) "Your progress and the reaction recorded so far will be saved on this device." else "Your progress is saved. You can pick up here later.") },
            confirmButton = { TextButton(onClick = { confirmingLeave = false; ScreenRecordingService.stop(context); viewModel.backToCategory() }) { Text("Save & leave") } },
            dismissButton = { TextButton(onClick = cancel) { Text("Keep playing") } })
    }
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
        Text("You played it loud!", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text("Round complete · ${state.selectedGame?.name.orEmpty()}", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${state.playableLevel?.questions?.size ?: 0}", "Pictures revealed", Color.White, Modifier.weight(1f))
            StatCard("5s", "Every first thought", Lilac, Modifier.weight(1f))
        }
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
        TextButton(onClick = viewModel::backToCategory, modifier = Modifier.padding(top = 8.dp)) { Text("More games", color = Muted) }
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
    Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { RoundIcon("arrow", "Back", onClick = onBack) }
        Text(title, modifier = Modifier.padding(horizontal = 54.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
    }
}

@Composable
private fun RoundIcon(icon: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White)) { GameIcon(icon, Modifier.size(21.dp), Ink, description) }
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
