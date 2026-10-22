package com.yapnix.game

import com.yapnix.game.updates.AppUpdate
import com.yapnix.game.updates.AppUpdates
import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yapnix.game.data.ApiException
import com.yapnix.game.data.Game
import com.yapnix.game.gameplay.RevealTimer
import com.yapnix.game.gameplay.RoundReadiness
import com.yapnix.game.data.GameImageCache
import com.yapnix.game.data.GameApi
import com.yapnix.game.data.Level
import com.yapnix.game.data.PlayableLevel
import com.yapnix.game.data.ProgressStore
import com.yapnix.game.data.SavedProgress
import com.yapnix.game.data.LocalGameStore
import com.yapnix.game.gameplay.ProgressSyncPolicy
import com.yapnix.game.auth.AuthRepository
import com.yapnix.game.auth.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen { HOME, PROGRESS, PROFILE, SETUP, SESSION, RESULTS, RECORDINGS }
enum class SessionPhase { PREPARING, PLAYING, FEEDBACK }

data class GameUiState(
    val screen: AppScreen = AppScreen.HOME,
    val games: List<Game> = emptyList(),
    val selectedGame: Game? = null,
    val selectedLevel: Level? = null,
    val playableLevel: PlayableLevel? = null,
    val savedProgress: SavedProgress? = null,
    val questionIndex: Int = 0,
    val score: Int = 0,
    val correctCount: Int = 0,
    val remainingSeconds: Int = 5,
    val preparationSeconds: Int = 0,
    val listenAttempt: Int = 0,
    val phase: SessionPhase = SessionPhase.PREPARING,
    val feedback: String = "",
    val loading: Boolean = false,
    val loadedImages: Int = 0,
    val readyImages: Map<String, android.graphics.Bitmap> = emptyMap(),
    val totalImages: Int = 0,
    val imageReadyId: String? = null,
    val promptReadyId: String? = null,
    val progress: List<SavedProgress> = emptyList(),
    val error: String? = null,
    val authSession: AuthSession? = null,
    val accountBusy: Boolean = false,
    val syncing: Boolean = false,
    val notice: String? = null,
    val showSignInPrompt: Boolean = false,
    val paused: Boolean = false,
    val recordEnabled: Boolean = false,
    val availableUpdate: AppUpdate? = null,
    val dismissedUpdate: String? = null,
    val checkingUpdate: Boolean = false,
)

private sealed interface ProgressWrite {
    data class Save(val progress: SavedProgress) : ProgressWrite
    data class Flush(val completion: CompletableDeferred<Unit>) : ProgressWrite
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val api = GameApi()
    private val localGames = LocalGameStore(application)
    private data class PreparedGame(val playable: PlayableLevel, val images: Map<String, android.graphics.Bitmap>)
    // Keep recently decoded rounds through navigation without retaining unbounded image memory.
    private val preparedGames = object : android.util.LruCache<String, PreparedGame>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: PreparedGame): Int =
            value.images.values.sumOf { it.allocationByteCount }.coerceAtLeast(1)
    }
    private val progressStore = ProgressStore(application)
    private val auth = AuthRepository(application)
    private var contentJob: Job? = null
    private var questionDeadline = 0L
    private val writes = Channel<ProgressWrite>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            var writeFailure: Throwable? = null
            for (command in writes) when (command) {
                is ProgressWrite.Save -> try { progressStore.save(command.progress); writeFailure = null }
                    catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        writeFailure = failure
                        mutableState.value = mutableState.value.copy(notice = "Could not save progress on this device")
                    }
                is ProgressWrite.Flush -> if (writeFailure == null) command.completion.complete(Unit)
                    else command.completion.completeExceptionally(writeFailure)
            }
        }
        viewModelScope.launch {
            auth.session.collect { session -> mutableState.value = mutableState.value.copy(authSession = session) }
        }
        refreshGames()
        viewModelScope.launch { mutableState.value = mutableState.value.copy(progress = progressStore.getAll()) }
    }

    private var lastUpdateCheck = 0L
    fun checkForUpdates(force: Boolean = false) {
        if (mutableState.value.checkingUpdate) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastUpdateCheck > 0 && now - lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        mutableState.value = mutableState.value.copy(checkingUpdate = true)
        viewModelScope.launch {
            runCatching { AppUpdates.check(BuildConfig.VERSION_NAME) }
                .onSuccess { update ->
                    lastUpdateCheck = SystemClock.elapsedRealtime()
                    mutableState.value = mutableState.value.copy(availableUpdate = update, checkingUpdate = false,
                        dismissedUpdate = if (force) null else mutableState.value.dismissedUpdate)
                    if (force) showNotice(if (update == null) "You have the latest available version" else "Update ${update.version} is available")
                }.onFailure {
                    if (it is CancellationException) throw it
                    mutableState.value = mutableState.value.copy(checkingUpdate = false)
                    if (force) showNotice("Could not check for updates. Try again when you’re online.")
                }
        }
    }
    fun dismissUpdate() { mutableState.value = mutableState.value.copy(dismissedUpdate = mutableState.value.availableUpdate?.version) }

    fun refreshGames() {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            val cachedCatalog = localGames.loadCatalog()
            mutableState.value = mutableState.value.copy(
                games = cachedCatalog.ifEmpty { mutableState.value.games },
                loading = cachedCatalog.isEmpty() && mutableState.value.games.isEmpty(), error = null,
            )
            runCatching { api.getGames().also { localGames.saveCatalog(it) } }
                .onSuccess { games ->
                    mutableState.value = mutableState.value.copy(games = games, loading = false)
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    val cached = localGames.loadCatalog()
                    mutableState.value = mutableState.value.copy(
                        games = cached, loading = false,
                        error = if (cached.isEmpty()) failure.message ?: "Could not load games" else null,
                    )
                }
        }
    }

    fun openGame(game: Game) {
        mutableState.value = mutableState.value.copy(
            selectedGame = game, selectedLevel = null, playableLevel = null,
            savedProgress = null, screen = AppScreen.SETUP, loading = true, error = null,
        )
        openLevel(Level(game.sessionId, game.id, game.name, game.description, game.questionCount, game.timeLimitSeconds))
    }

    fun openLevel(level: Level) {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                selectedLevel = level,
                playableLevel = null,
                savedProgress = null,
                loading = true, readyImages = emptyMap(), loadedImages = 0, totalImages = level.questionCount, imageReadyId = null,
                error = null,
            )
            runCatching {
                val prepared = preparedGames.get(level.id) ?: run {
                    val playable = localGames.load(level) ?: api.getPlayableLevel(level).let { localGames.save(it) }
                    check(playable.questions.isNotEmpty()) { "This game has no pictures yet" }
                    mutableState.value = mutableState.value.copy(totalImages = playable.questions.size)
                    val images = GameImageCache.preload(getApplication(), playable.questions) { count ->
                        mutableState.value = mutableState.value.copy(loadedImages = count)
                    }
                    PreparedGame(playable, images).also { preparedGames.put(level.id, it) }
                }
                val playable = prepared.playable
                mutableState.value = mutableState.value.copy(readyImages = prepared.images,
                    loadedImages = playable.questions.size, totalImages = playable.questions.size)
                val flushed = CompletableDeferred<Unit>()
                writes.send(ProgressWrite.Flush(flushed))
                flushed.await()
                val fingerprint = playable.questions.joinToString(",") { it.id }
                playable to progressStore.get(level.id)?.takeIf { it.contentFingerprint == fingerprint }
            }
                .onSuccess { (playable, saved) ->
                    mutableState.value = mutableState.value.copy(
                        selectedLevel = level,
                        playableLevel = playable,
                        savedProgress = saved,
                        loading = false,
                    )
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = failure.message ?: "Could not load game",
                    )
                }
        }
    }

    fun startSession() {
        val current = mutableState.value
        if (current.syncing || current.loading) return
        val playable = current.playableLevel ?: return
        if (playable.questions.isEmpty()) {
            mutableState.value = current.copy(error = "This game has no playable questions yet")
            return
        }
        if (playable.questions.any { it.id !in current.readyImages }) return
        val saved = current.savedProgress?.takeUnless { it.completed }
        val index = saved?.questionIndex?.coerceIn(0, (playable.questions.size - 1).coerceAtLeast(0)) ?: 0
        mutableState.value = current.copy(
            screen = AppScreen.SESSION,
            questionIndex = index,
            score = saved?.score ?: 0,
            correctCount = saved?.correctCount ?: 0,
            preparationSeconds = 0,
            remainingSeconds = 5,
            phase = SessionPhase.PREPARING,
            feedback = "",
            listenAttempt = 0,
            error = null,
            paused = false, imageReadyId = null, promptReadyId = null,
        )
        saveProgress(completed = false)
    }

    /** Start immediately after the visible image and spoken prompt are ready. */
    fun tickPreparation() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.PREPARING || !RoundReadiness.ready(current.playableLevel?.questions?.getOrNull(current.questionIndex)?.id, current.imageReadyId, current.promptReadyId)) return
        questionDeadline = SystemClock.elapsedRealtime() + RevealTimer.SECONDS * 1000L
        mutableState.value = current.copy(
            remainingSeconds = RevealTimer.SECONDS, phase = SessionPhase.PLAYING, feedback = "",
        )
    }

    fun tickQuestion() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.PLAYING) return
        val seconds = RevealTimer.remaining(questionDeadline, SystemClock.elapsedRealtime())
        if (seconds <= 0) {
            val answer = current.playableLevel?.questions?.getOrNull(current.questionIndex)?.primaryAnswer
                ?: ""
            mutableState.value = current.copy(
                remainingSeconds = 0,
                phase = SessionPhase.FEEDBACK,
                feedback = answer,
            )
        } else {
            mutableState.value = current.copy(remainingSeconds = seconds)
        }
        if (seconds <= 0) saveProgress(completed = false)
    }

    fun advanceQuestion() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.FEEDBACK) return
        val playable = current.playableLevel ?: return
        if (current.questionIndex + 1 >= playable.questions.size) {
            progressStore.recordCompletedSession()
            mutableState.value = current.copy(screen = AppScreen.RESULTS, showSignInPrompt = auth.session.value == null && progressStore.shouldPrompt())
            saveProgress(completed = true)
        } else {
            mutableState.value = current.copy(
                questionIndex = current.questionIndex + 1,
                remainingSeconds = 5,
                phase = SessionPhase.PREPARING, preparationSeconds = 0, imageReadyId = null, promptReadyId = null,
                feedback = "",
                listenAttempt = current.listenAttempt + 1,
            )
            saveProgress(completed = false)
        }
    }

    fun replay() { mutableState.value.selectedGame?.let { openGame(it) } }

    fun promptReady(id: String) {
        val current = mutableState.value
        if (current.phase == SessionPhase.PREPARING && current.playableLevel?.questions?.getOrNull(current.questionIndex)?.id == id) {
            mutableState.value = current.copy(promptReadyId = id)
            tickPreparation()
        }
    }

    fun imageReady(id: String) {
        val current = mutableState.value
        if (current.playableLevel?.questions?.getOrNull(current.questionIndex)?.id == id) {
            mutableState.value = current.copy(imageReadyId = id)
            tickPreparation()
        }
    }

    fun imageFailed() { mutableState.value = mutableState.value.copy(paused = true, notice = "This picture could not be displayed. Return to the game and try again; your progress is saved.") }

    fun navigate(screen: AppScreen) {
        if (screen == AppScreen.HOME) { backToHome(); return }
        if (screen !in listOf(AppScreen.PROGRESS, AppScreen.PROFILE, AppScreen.RECORDINGS)) return
        contentJob?.cancel()
        mutableState.value = mutableState.value.copy(screen = screen, loading = false, error = null)
        viewModelScope.launch {
            runCatching {
                val flushed = CompletableDeferred<Unit>()
                writes.send(ProgressWrite.Flush(flushed)); flushed.await()
                progressStore.getAll()
            }.onSuccess { mutableState.value = mutableState.value.copy(progress = it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    showNotice("Could not read saved progress on this device")
                }
        }
    }

    fun backToHome() {
        contentJob?.cancel()
        mutableState.value = mutableState.value.copy(
            screen = AppScreen.HOME,
            loading = false,
            selectedGame = null,
            selectedLevel = null,
            playableLevel = null,
            readyImages = emptyMap(),
            error = null,
            paused = false,
        )
        if (mutableState.value.games.isEmpty()) refreshGames()
    }

    private fun saveProgress(completed: Boolean) {
        val current = mutableState.value
        val level = current.selectedLevel ?: return
        // A resolved question must not be scored again when a session resumes.
        val nextIndex = current.questionIndex + if (current.phase == SessionPhase.FEEDBACK) 1 else 0
        val finished = completed || nextIndex >= (current.playableLevel?.questions?.size ?: Int.MAX_VALUE)
        writes.trySend(ProgressWrite.Save(
                SavedProgress(
                    levelId = level.id,
                    levelName = level.name,
                    questionIndex = nextIndex,
                    score = current.score,
                    completed = finished,
                    correctCount = current.correctCount,
                    contentFingerprint = current.playableLevel?.questions?.joinToString(",") { it.id }.orEmpty(),
                ),
            ))
    }

    fun setForeground(foreground: Boolean) {
        if (!foreground && mutableState.value.screen == AppScreen.SESSION) {
            tickQuestion()
            mutableState.value = mutableState.value.copy(paused = true)
            saveProgress(completed = false)
        }
    }

    fun resumeSession() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION) return
        questionDeadline = SystemClock.elapsedRealtime() + current.remainingSeconds * 1000L
        mutableState.value = current.copy(paused = false, listenAttempt = current.listenAttempt + 1)
        tickPreparation()
    }

    fun speechUnavailable(message: String) {
        mutableState.value = mutableState.value.copy(paused = true, notice = message)
    }

    fun setRecording(enabled: Boolean) { mutableState.value = mutableState.value.copy(recordEnabled = enabled) }
    fun openRecordings() { navigate(AppScreen.RECORDINGS) }
    fun dismissSignInPrompt() {
        progressStore.dismissPrompt(); mutableState.value = mutableState.value.copy(showSignInPrompt = false)
    }
    fun showNotice(message: String) { mutableState.value = mutableState.value.copy(notice = message) }

    fun beginGoogleSignIn(openBrowser: (Uri) -> Unit) {
        if (mutableState.value.accountBusy || mutableState.value.syncing) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(accountBusy = true, notice = null)
            try { openBrowser(auth.startGoogleSignIn()) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                showNotice(failure.message ?: "Could not open Google sign-in")
            } finally { mutableState.value = mutableState.value.copy(accountBusy = false) }
        }
    }

    fun handleAuthCallback(uri: Uri) {
        if (uri.scheme != "yapnix" || uri.host != "auth" || uri.path != "/callback") return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(accountBusy = true)
            try { auth.finishSignIn(uri); showNotice("Signed in. Choose Sync progress to back up or restore your saves.") }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                showNotice(failure.message ?: "Sign-in did not finish")
            } finally { mutableState.value = mutableState.value.copy(accountBusy = false) }
        }
    }

    fun signOut() {
        if (mutableState.value.syncing) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(accountBusy = true)
            try { auth.signOut(); showNotice("Signed out. Your local progress is still here.") }
            finally { mutableState.value = mutableState.value.copy(accountBusy = false) }
        }
    }

    fun syncProgress() {
        val owner = auth.session.value?.userId ?: return
        if (mutableState.value.syncing || mutableState.value.screen == AppScreen.SESSION) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(syncing = true, notice = "Syncing progress…")
            try {
                val flushed = CompletableDeferred<Unit>(); writes.send(ProgressWrite.Flush(flushed)); flushed.await()
                val local = progressStore.getAll().associateBy { it.levelId }
                val cloud = api.getProgress(auth.accessToken()).associateBy { it.levelId }
                var skipped = 0
                for (id in local.keys + cloud.keys) {
                    check(auth.session.value?.userId == owner) { "Your account changed. Sync again." }
                    val winner = ProgressSyncPolicy.choose(local[id], cloud[id]) ?: continue
                    val saved = if (winner == local[id]) try { api.saveProgress(auth.accessToken(), winner) }
                        catch (failure: ApiException) {
                            if (failure.status == 404 || failure.status == 400) { skipped++; continue } else throw failure
                        } else winner
                    check(auth.session.value?.userId == owner) { "Your account changed. Sync again." }
                    progressStore.save(saved.copy(levelName = local[id]?.levelName ?: saved.levelName))
                }
                val selected = mutableState.value.selectedLevel
                val playable = mutableState.value.playableLevel
                if (selected != null && playable != null) {
                    val fingerprint = playable.questions.joinToString(",") { it.id }
                    val restored = progressStore.get(selected.id)?.takeIf { it.contentFingerprint == fingerprint }
                    mutableState.value = mutableState.value.copy(savedProgress = restored)
                }
                showNotice(if (skipped == 0) "Progress synced. Newer saves were kept." else "Progress synced. $skipped updated or unpublished levels were kept locally.")
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                showNotice(failure.message ?: "Sync failed. Your local saves are still safe.")
            } finally { mutableState.value = mutableState.value.copy(syncing = false) }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(GameViewModel::class.java)) {
                throw IllegalArgumentException("Unsupported ViewModel class")
            }
            return GameViewModel(application) as T
        }
    }
}
