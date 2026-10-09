package com.yapnix.game.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("yapnix_progress", Context.MODE_PRIVATE)
    private val lock = Mutex()

    suspend fun get(levelId: String): SavedProgress? = withContext(Dispatchers.IO) {
        val key = levelId
        if (!preferences.contains("$key.score")) return@withContext null
        SavedProgress(
            levelId = levelId,
            levelName = preferences.getString("$key.name", "Level").orEmpty(),
            questionIndex = preferences.getInt("$key.question", 0),
            score = preferences.getInt("$key.score", 0),
            completed = preferences.getBoolean("$key.completed", false),
            correctCount = preferences.getInt("$key.correct", preferences.getInt("$key.score", 0)),
            lastPlayedAt = preferences.getLong("$key.played", 0),
            contentFingerprint = preferences.getString("$key.fingerprint", "").orEmpty(),
        )
    }

    suspend fun save(progress: SavedProgress) = withContext(Dispatchers.IO) {
        lock.withLock {
        if (preferences.getLong("${progress.levelId}.played", 0) > progress.lastPlayedAt) return@withLock
        check(preferences.edit()
            .putString("${progress.levelId}.name", progress.levelName)
            .putInt("${progress.levelId}.question", progress.questionIndex)
            .putInt("${progress.levelId}.score", progress.score)
            .putBoolean("${progress.levelId}.completed", progress.completed)
            .putInt("${progress.levelId}.correct", progress.correctCount)
            .putLong("${progress.levelId}.played", progress.lastPlayedAt)
            .putString("${progress.levelId}.fingerprint", progress.contentFingerprint)
            .commit()) { "Could not save progress on this device" }
        }
    }

    suspend fun getAll(): List<SavedProgress> = withContext(Dispatchers.IO) {
        preferences.all.keys.filter { it.endsWith(".score") }.mapNotNull { get(it.removeSuffix(".score")) }
    }

    fun completedSessions(): Int = preferences.getInt("completed_sessions", 0)
    fun recordCompletedSession(): Int = (completedSessions() + 1).also {
        preferences.edit().putInt("completed_sessions", it).apply()
    }
    fun shouldPrompt(): Boolean = completedSessions() >= preferences.getInt("next_prompt", 3)
    fun dismissPrompt() { preferences.edit().putInt("next_prompt", completedSessions() + 5).apply() }
}
