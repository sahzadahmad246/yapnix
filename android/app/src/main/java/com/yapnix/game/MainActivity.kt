package com.yapnix.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.yapnix.game.data.Game
import com.yapnix.game.data.Level
import com.yapnix.game.recording.ReactionCamera
import com.yapnix.game.recording.RecordingsScreen

private val Ink = Color(0xFF111918)
private val Panel = Color(0xFF1B2724)
private val Lime = Color(0xFFD7F36A)
private val Mist = Color(0xFFF0F3E9)
private val Muted = Color(0xFFA8B2AA)
private val Coral = Color(0xFFFF876F)

class MainActivity : ComponentActivity() {
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameViewModel = ViewModelProvider(
            this,
            GameViewModel.Factory(application),
        )[GameViewModel::class.java]
        intent.data?.let { gameViewModel.handleAuthCallback(it); intent.data = null }

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Lime,
                    onPrimary = Ink,
                    background = Ink,
                    surface = Panel,
                    onSurface = Mist,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    YapnixApp(gameViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { gameViewModel.handleAuthCallback(it); intent.data = null }
        setIntent(intent)
    }

    override fun onStop() {
        gameViewModel.setForeground(false)
        super.onStop()
    }
}

@Composable
private fun YapnixApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = state.screen != AppScreen.HOME) {
        if (state.screen == AppScreen.SESSION) { viewModel.setForeground(false); confirmExit = true }
        else viewModel.backToHome()
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false; viewModel.resumeSession() },
        title = { Text("Leave this round?") }, text = { Text("Your progress is saved locally. You can resume the next unanswered question later.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; viewModel.backToHome() }) { Text("Leave round") } },
        dismissButton = { TextButton(onClick = { confirmExit = false; viewModel.resumeSession() }) { Text("Keep playing") } })
    when (state.screen) {
        AppScreen.HOME -> HomeScreen(state, viewModel::openGame, viewModel::refreshGames, viewModel)
        AppScreen.SETUP -> GameSetupScreen(
            state = state,
            onBack = viewModel::backToHome,
            onStart = viewModel::startSession,
            viewModel = viewModel,
        )
        AppScreen.SESSION -> SessionScreen(state, viewModel)
        AppScreen.RESULTS -> ResultsScreen(state, viewModel::replay, viewModel::backToHome, viewModel)
        AppScreen.RECORDINGS -> RecordingsScreen(viewModel::backToHome)
    }
}

@Composable
private fun HomeScreen(
    state: GameUiState,
    onSelectGame: (Game) -> Unit,
    onRefresh: () -> Unit,
    viewModel: GameViewModel,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text("YAPNIX", color = Lime, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(34.dp))
        Text("See it.\nSay it.\nNail it.", color = Mist, fontSize = 48.sp, lineHeight = 51.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        Text("Quick-fire voice challenges. Your progress stays on this device.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        Text("CHOOSE A GAME", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.loading) {
            CircularProgressIndicator(color = Lime, modifier = Modifier.size(28.dp))
        } else if (state.error != null) {
            ErrorPanel(state.error, "API: ${BuildConfig.API_BASE_URL}", onRefresh)
        } else if (state.games.isEmpty()) {
            EmptyPanel(
                title = "No published games yet",
                detail = "Publish a category and a game with questions in Yapnix Studio, then refresh.",
                action = "Refresh",
                onAction = onRefresh,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.games.groupBy { it.categoryId }.values.forEach { categoryGames ->
                    Text(categoryGames.first().categoryName, color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    categoryGames.forEach { game ->
                    Button(
                        onClick = { onSelectGame(game) },
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mist),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(game.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (game.description.isNotBlank()) Text(game.description, color = Muted, fontSize = 13.sp)
                        }
                    }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("GUEST PLAY  •  LOCAL PROGRESS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        AccountPanel(state, viewModel)
        TextButton(onClick = viewModel::openRecordings) { Text("YOUR RECORDINGS", color = Lime) }
    }
}

@Composable
private fun GameSetupScreen(
    state: GameUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    viewModel: GameViewModel,
) {
    val context = LocalContext.current
    val permissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    var permissionMessage by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else permissionMessage = "Microphone permission is needed for voice answers. Enable it in your device's app settings or try again."
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setRecording(granted)
        if (!granted) permissionMessage = "Camera permission was declined. You can play without recording."
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        TextButton(onClick = onBack) { Text("←  GAMES", color = Lime) }
        Spacer(Modifier.height(20.dp))
        Text(state.selectedGame?.name ?: "Game", color = Mist, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(state.selectedGame?.description.orEmpty(), color = Muted)
        Spacer(Modifier.height(24.dp))

        when {
            state.loading -> CircularProgressIndicator(color = Lime)
            state.error != null -> ErrorPanel(state.error, "Check the backend and try again.", onBack)
            state.playableLevel != null -> Text(
                "${state.playableLevel.questions.size} questions · ${state.selectedLevel?.timeLimitSeconds ?: 5}s each",
                color = Muted, fontSize = 16.sp,
            )
        }

        state.savedProgress?.let { progress ->
            Spacer(Modifier.height(18.dp))
            Text(
                if (progress.completed) "Last score: ${progress.score}" else "Saved locally · question ${progress.questionIndex + 1} · score ${progress.score}",
                color = Muted,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Record my reaction", color = Mist)
            Switch(checked = state.recordEnabled, onCheckedChange = { enabled ->
                if (!enabled) viewModel.setRecording(false)
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) viewModel.setRecording(true)
                else cameraLauncher.launch(Manifest.permission.CAMERA)
            })
        }
        Text("Optional camera video, stored only on this device.", color = Muted, fontSize = 12.sp)
        if (permissionMessage.isNotEmpty()) Text(permissionMessage, color = Coral, modifier = Modifier.padding(vertical = 8.dp))
        if (state.selectedLevel != null && state.playableLevel != null) {
            Button(
                onClick = {
                    if (!SpeechRecognizer.isRecognitionAvailable(context)) permissionMessage = "Speech recognition is unavailable. Install or enable a speech recognition service on this device."
                    else if (permissionGranted) onStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !state.syncing && !state.loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("PLAY ${state.selectedLevel.name.uppercase()}", fontWeight = FontWeight.Black)
            }
            Text("Microphone access is needed to recognize answers.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun SessionScreen(state: GameUiState, viewModel: GameViewModel) {
    val playable = state.playableLevel
    val question = playable?.questions?.getOrNull(state.questionIndex)
    val context = LocalContext.current
    val permissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(state.screen, state.phase, state.preparationSeconds, state.paused) {
        if (!state.paused && state.screen == AppScreen.SESSION && state.phase == SessionPhase.PREPARING) {
            kotlinx.coroutines.delay(1_000)
            viewModel.tickPreparation()
        }
    }
    LaunchedEffect(state.screen, state.phase, state.questionIndex, state.remainingSeconds, state.paused) {
        if (!state.paused && state.screen == AppScreen.SESSION && state.phase == SessionPhase.PLAYING) {
            kotlinx.coroutines.delay(1_000)
            viewModel.tickQuestion()
        }
    }
    LaunchedEffect(state.screen, state.phase, state.questionIndex, state.paused) {
        if (!state.paused && state.screen == AppScreen.SESSION && state.phase == SessionPhase.FEEDBACK) {
            kotlinx.coroutines.delay(900)
            viewModel.advanceQuestion()
        }
    }

    SpeechRecognitionEffect(
        active = !state.paused && permissionGranted && state.screen == AppScreen.SESSION && state.phase == SessionPhase.PLAYING,
        retry = state.listenAttempt,
        questionId = question?.id.orEmpty(),
        language = question?.language ?: "en-US",
        onResult = viewModel::submitSpeechCandidates,
        onFailure = viewModel::speechFailed,
        onUnavailable = viewModel::speechUnavailable,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${state.questionIndex + 1} / ${playable?.questions?.size ?: 0}", color = Muted, fontWeight = FontWeight.Bold)
            Text("SCORE  ${state.score}", color = Lime, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (state.phase) {
                    SessionPhase.PREPARING -> "READY ${state.preparationSeconds}"
                    SessionPhase.PLAYING -> "${state.remainingSeconds}s"
                    SessionPhase.FEEDBACK -> "RESULT"
                },
                color = if (state.remainingSeconds <= 2 && state.phase == SessionPhase.PLAYING) Coral else Mist,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text(if (state.phase == SessionPhase.PLAYING) "● LISTENING" else "", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(playable?.prompt ?: "What is this?", color = Mist, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Panel),
            contentAlignment = Alignment.Center,
        ) {
            if (question != null) {
                AsyncImage(
                    model = question.imageUrl,
                    contentDescription = playable?.prompt ?: "What is this?",
                    modifier = Modifier.fillMaxSize().padding(14.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            if (state.phase == SessionPhase.PREPARING) {
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                    Text(state.preparationSeconds.toString(), color = Lime, fontSize = 88.sp, fontWeight = FontWeight.Black)
                }
            }
            if (state.recordEnabled && !state.paused) ReactionCamera(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(width = 100.dp, height = 140.dp).clip(RoundedCornerShape(10.dp)),
                onSaved = { viewModel.showNotice("Recording saved on this device") }, onError = viewModel::showNotice,
            )
            if (state.paused) {
                Column(Modifier.fillMaxSize().background(Ink.copy(alpha = .85f)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Round paused", color = Mist)
                    Button(onClick = viewModel::resumeSession) { Text("Resume") }
                    TextButton(onClick = viewModel::backToHome) { Text("Back to games") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(state.feedback, color = if (state.phase == SessionPhase.FEEDBACK && state.feedback.startsWith("Correct")) Lime else Mist, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Speak clearly. You can try again while time remains.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        state.notice?.let { Text(it, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun ResultsScreen(state: GameUiState, onReplay: () -> Unit, onHome: () -> Unit, viewModel: GameViewModel) {
    val total = state.playableLevel?.questions?.size ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ROUND COMPLETE", color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Text("${state.score} / $total", color = Mist, fontSize = 64.sp, fontWeight = FontWeight.Black)
        Text("${state.correctCount} correct  ·  ${total - state.correctCount} missed", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onReplay, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
            Text("PLAY AGAIN", fontWeight = FontWeight.Black)
        }
        TextButton(onClick = onHome) { Text("BACK TO GAMES", color = Mist) }
        Spacer(Modifier.height(18.dp))
        Text("Progress saved on this device", color = Muted, fontSize = 12.sp)
        if (state.recordEnabled) TextButton(onClick = viewModel::openRecordings) { Text("PREVIEW / SAVE / SHARE RECORDINGS", color = Lime) }
        AccountPanel(state, viewModel)
        if (state.showSignInPrompt && state.authSession == null) {
            Text("Keep your progress when changing devices. Google sign-in is optional.", color = Muted)
            TextButton(onClick = viewModel::dismissSignInPrompt) { Text("Not now", color = Lime) }
        }
    }
}

@Composable
private fun AccountPanel(state: GameUiState, viewModel: GameViewModel) {
    val context = LocalContext.current
    var confirmSync by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.authSession == null) {
            Text("Play as a guest, or sign in to sync progress.", color = Muted, fontSize = 13.sp)
            OutlinedButton(enabled = !state.accountBusy, onClick = {
                viewModel.beginGoogleSignIn { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) { Text(if (state.accountBusy) "Opening Google…" else "Sign in with Google", color = Lime) }
        } else {
            Text(state.authSession.email, color = Mist, fontSize = 13.sp)
            Row {
                OutlinedButton(enabled = !state.accountBusy && !state.syncing, onClick = { confirmSync = true }) {
                    Text(if (state.syncing) "Syncing…" else "Sync progress", color = Lime)
                }
                TextButton(enabled = !state.accountBusy && !state.syncing, onClick = viewModel::signOut) { Text("Sign out", color = Muted) }
            }
        }
        state.notice?.let { Text(it, color = Muted, fontSize = 12.sp) }
    }
    if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Sync progress?") },
        text = { Text("Merge this device's saves with ${state.authSession?.email}. The newer save for each level is kept. Recordings stay on this device.") },
        confirmButton = { TextButton(onClick = { confirmSync = false; viewModel.syncProgress() }) { Text("Sync now") } },
        dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Not now") } })
}

@Composable
private fun EmptyPanel(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(20.dp)) {
        Text(title, color = Mist, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Muted, fontSize = 14.sp)
        TextButton(onClick = onAction) { Text(action.uppercase(), color = Lime) }
    }
}

@Composable
private fun ErrorPanel(message: String, detail: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(20.dp)) {
        Text("Could not connect", color = Coral, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Mist, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Muted, fontSize = 12.sp)
        TextButton(onClick = onRetry) { Text("RETRY", color = Lime) }
    }
}

@Composable
private fun SpeechRecognitionEffect(
    active: Boolean,
    retry: Int,
    questionId: String,
    language: String,
    onResult: (String, List<String>) -> Unit,
    onFailure: () -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val latestResult by rememberUpdatedState(onResult)
    val latestFailure by rememberUpdatedState(onFailure)
    val latestActive by rememberUpdatedState(active)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val recognizer = remember(context, questionId) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(recognizer) {
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (latestActive && error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) latestUnavailable("Microphone access is unavailable. Enable it in app settings.")
                    else if (error != SpeechRecognizer.ERROR_CLIENT && latestActive) latestFailure()
                }

                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (latestActive) latestResult(questionId, words.orEmpty())
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
            })
        }
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(active, retry, recognizer, language) {
        if (recognizer == null) {
            if (active) latestUnavailable("Speech recognition is unavailable on this device")
        } else if (active) {
            if (retry > 0) kotlinx.coroutines.delay(250)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            runCatching { recognizer.startListening(intent) }.onFailure { latestFailure() }
        } else {
            recognizer?.cancel()
        }
    }
}
