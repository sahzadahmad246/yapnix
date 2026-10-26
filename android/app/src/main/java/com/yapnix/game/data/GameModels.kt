package com.yapnix.game.data

data class Game(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: Int,
    val categoryId: String = "",
    val categoryName: String = "Guess",
    val categoryDescription: String = "",
    val iconKey: String = "game-controller",
    val categoryIconKey: String = "question",
    val iconUrl: String = "",
    val categoryIconUrl: String = "",
    val sessionId: String = "",
    val questionCount: Int = 5,
    val timeLimitSeconds: Int = 5,
)

// One private session configuration per game. The legacy ID preserves saved progress.
data class Level(
    val id: String,
    val gameId: String,
    val name: String,
    val description: String,
    val questionCount: Int,
    val timeLimitSeconds: Int,
)

data class Question(
    val id: String,
    val imageUrl: String,
    val primaryAnswer: String,
    val acceptedAnswers: List<String>,
    val language: String = "en-US",
)

data class PlayableLevel(
    val level: Level,
    val questions: List<Question>,
    val prompt: String = "What is this?",
)

data class SavedProgress(
    val levelId: String,
    val levelName: String,
    val questionIndex: Int,
    val score: Int,
    val completed: Boolean,
    val correctCount: Int = score,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val contentFingerprint: String = "",
)
