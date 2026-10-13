package com.yapnix.game

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yapnix.game.data.ApiException
import com.yapnix.game.data.Game
import com.yapnix.game.data.GameApi
import com.yapnix.game.data.Level
import com.yapnix.game.data.PlayableLevel
import com.yapnix.game.data.ProgressStore
import com.yapnix.game.data.SavedProgress
import com.yapnix.game.gameplay.AnswerMatcher
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

enum class AppScreen { HOME, SETUP, SESSION, RESULTS, RECORDINGS }
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
    val preparationSeconds: Int = 3,
    val listenAttempt: Int = 0,
    val phase: SessionPhase = SessionPhase.PREPARING,
    val feedback: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val authSession: AuthSession? = null,
    val accountBusy: Boolean = false,
    val syncing: Boolean = false,
    val notice: String? = null,
    val showSignInPrompt: Boolean = false,
    val paused: Boolean = false,
    val recordEnabled: Boolean = false,
)

private sealed interface ProgressWrite {
    data class Save(val progress: SavedProgress) : ProgressWrite
    data class Flush(val completion: CompletableDeferred<Unit>) : ProgressWrite
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val api = GameApi()
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
    }

    fun refreshGames() {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching { api.getGames() }
                .onSuccess { games ->
                    mutableState.value = mutableState.value.copy(games = games, loading = false)
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = failure.message ?: "Could not reach Yapnix API",
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
                loading = true,
                error = null,
            )
            runCatching { api.getPlayableLevel(level) }
                .onSuccess { playable ->
                    val flushed = CompletableDeferred<Unit>()
                    writes.send(ProgressWrite.Flush(flushed))
                    flushed.await()
                    val fingerprint = playable.questions.joinToString(",") { it.id }
                    val saved = progressStore.get(level.id)?.takeIf { it.contentFingerprint == fingerprint }
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
            mutableState.value = current.copy(error = "This level has no playable questions yet")
            return
        }
        val saved = current.savedProgress?.takeUnless { it.completed }
        val index = saved?.questionIndex?.coerceIn(0, (playable.questions.size - 1).coerceAtLeast(0)) ?: 0
        mutableState.value = current.copy(
            screen = AppScreen.SESSION,
            questionIndex = index,
            score = saved?.score ?: 0,
            correctCount = saved?.correctCount ?: 0,
            preparationSeconds = 3,
            remainingSeconds = playable.level.timeLimitSeconds,
            phase = SessionPhase.PREPARING,
            feedback = "Get ready",
            listenAttempt = 0,
            error = null,
            paused = false,
        )
        saveProgress(completed = false)
    }

    fun tickPreparation() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.PREPARING) return
        if (current.preparationSeconds <= 1) {
            questionDeadline = SystemClock.elapsedRealtime() + (current.playableLevel?.level?.timeLimitSeconds ?: 5) * 1000L
            mutableState.value = current.copy(
                preparationSeconds = 0,
                remainingSeconds = current.playableLevel?.level?.timeLimitSeconds ?: 5,
                phase = SessionPhase.PLAYING,
                feedback = "Listening",
            )
        } else {
            mutableState.value = current.copy(preparationSeconds = current.preparationSeconds - 1)
        }
    }

    fun tickQuestion() {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.PLAYING) return
        val seconds = ((questionDeadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
        if (seconds <= 0) {
            val answer = current.playableLevel?.questions?.getOrNull(current.questionIndex)?.primaryAnswer
                ?: ""
            mutableState.value = current.copy(
                remainingSeconds = 0,
                phase = SessionPhase.FEEDBACK,
                feedback = "Time. Answer: $answer",
            )
        } else {
            mutableState.value = current.copy(remainingSeconds = seconds)
        }
        if (seconds <= 0) saveProgress(completed = false)
    }

    fun submitSpeech(recognized: String) {
        val current = mutableState.value
        if (current.screen != AppScreen.SESSION || current.paused || current.phase != SessionPhase.PLAYING) return
        if (SystemClock.elapsedRealtime() >= questionDeadline) { tickQuestion(); return }
        val question = current.playableLevel?.questions?.getOrNull(current.questionIndex) ?: return
        val heard = AnswerMatcher.normalize(recognized)
        if (AnswerMatcher.matches(recognized, question.acceptedAnswers)) {
            mutableState.value = current.copy(
                phase = SessionPhase.FEEDBACK,
                score = current.score + 1,
                correctCount = current.correctCount + 1,
                feedback = "Correct. ${question.primaryAnswer}",
            )
            saveProgress(completed = false)
        } else {
            mutableState.value = current.copy(
                feedback = if (heard.isEmpty()) "Listening" else "Not quite. Try again",
                listenAttempt = current.listenAttempt + 1,
            )
        }
    }

    fun submitSpeechCandidates(questionId: String, candidates: List<String>) {
        val question = mutableState.value.playableLevel?.questions?.getOrNull(mutableState.value.questionIndex) ?: return
        if (question.id != questionId) return
        submitSpeech(candidates.firstOrNull { AnswerMatcher.matches(it, question.acceptedAnswers) } ?: candidates.firstOrNull().orEmpty())
    }

    fun speechFailed() {
        val current = mutableState.value
        if (!current.paused && current.screen == AppScreen.SESSION && current.phase == SessionPhase.PLAYING) {
            mutableState.value = current.copy(
                feedback = "Didn't catch that. Try again",
                listenAttempt = current.listenAttempt + 1,
            )
        }
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
            questionDeadline = SystemClock.elapsedRealtime() + playable.level.timeLimitSeconds * 1000L
            mutableState.value = current.copy(
                questionIndex = current.questionIndex + 1,
                remainingSeconds = playable.level.timeLimitSeconds,
                phase = SessionPhase.PLAYING,
                feedback = "Listening",
                listenAttempt = current.listenAttempt + 1,
            )
            saveProgress(completed = false)
        }
    }

    fun replay() {
        mutableState.value = mutableState.value.copy(savedProgress = null)
        startSession()
    }

    fun backToHome() {
        mutableState.value = mutableState.value.copy(
            screen = AppScreen.HOME,
            selectedGame = null,
            selectedLevel = null,
            playableLevel = null,
            error = null,
            paused = false,
        )
        refreshGames()
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
    }

    fun speechUnavailable(message: String) {
        mutableState.value = mutableState.value.copy(paused = true, notice = message)
    }

    fun setRecording(enabled: Boolean) { mutableState.value = mutableState.value.copy(recordEnabled = enabled) }
    fun openRecordings() { mutableState.value = mutableState.value.copy(screen = AppScreen.RECORDINGS) }
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
