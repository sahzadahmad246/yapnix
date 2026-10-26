package com.yapnix.game.data

import android.net.Uri
import com.yapnix.game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class GameApi(
    private val apiBaseUrl: String = BuildConfig.API_BASE_URL,
) {
    suspend fun getGames(): List<Game> = withContext(Dispatchers.IO) {
        requestArray("games").map { value ->
            Game(
                id = value.getString("id"),
                name = value.getString("name"),
                description = value.optString("description"),
                difficulty = value.optInt("difficulty", 1),
                categoryId = value.optString("category_id"),
                categoryName = value.optJSONObject("category")?.optString("name") ?: "Guess",
                categoryDescription = value.optJSONObject("category")?.optString("description").orEmpty(),
                iconKey = value.optString("icon_key", "game-controller"),
                categoryIconKey = value.optJSONObject("category")?.optString("icon_key", "question") ?: "question",
                iconUrl = value.optString("icon_url").takeIf { it.startsWith("https://") }.orEmpty(),
                categoryIconUrl = value.optJSONObject("category")?.optString("icon_url")?.takeIf { it.startsWith("https://") }.orEmpty(),
                sessionId = value.getString("session_id"),
                questionCount = value.optInt("question_count", 5),
                timeLimitSeconds = value.optInt("default_time_limit_seconds", 5),
            )
        }
    }

    suspend fun getPlayableLevel(level: Level): PlayableLevel = withContext(Dispatchers.IO) {
        val value = requestObject(
            "games/${Uri.encode(level.gameId)}/play",
        )
        val questionsJson = value.optJSONArray("questions") ?: JSONArray()
        val questions = (0 until questionsJson.length()).map { index ->
            val question = questionsJson.getJSONObject(index)
            val accepted = question.optJSONArray("accepted_answers") ?: JSONArray()
            val answers = (0 until accepted.length()).map(accepted::getString)
            val primary = question.getString("primary_answer")
            Question(
                id = question.getString("id"),
                imageUrl = question.optString("image_url").takeIf { it.startsWith("https://") }
                    ?: throw ApiException("A question image is unavailable. Please reload the level."),
                primaryAnswer = primary,
                acceptedAnswers = (listOf(primary) + answers).distinct(),
                language = question.optString("language", "en-US"),
            )
        }

        PlayableLevel(level = level.copy(questionCount = questions.size), questions = questions, prompt = value.optString("prompt", "What is this?"))
    }

    suspend fun getProgress(token: String): List<SavedProgress> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("progress", token = token))
        (0 until array.length()).map { parseProgress(array.getJSONObject(it)) }
    }

    suspend fun saveProgress(token: String, progress: SavedProgress): SavedProgress = withContext(Dispatchers.IO) {
        val body = JSONObject().put("levelId", progress.levelId).put("questionIndex", progress.questionIndex)
            .put("score", progress.score).put("correctCount", progress.correctCount).put("completed", progress.completed)
            .put("lastPlayedAt", Instant.ofEpochMilli(progress.lastPlayedAt).toString()).put("contentFingerprint", progress.contentFingerprint)
        parseProgress(JSONObject(request("progress", "POST", token, body.toString()))).copy(levelName = progress.levelName)
    }

    private fun parseProgress(value: JSONObject) = SavedProgress(
        levelId = value.getString("level_id"), levelName = value.optString("level_name", "Saved level"),
        questionIndex = value.getInt("question_index"), score = value.getInt("score"),
        completed = value.optBoolean("completed"), correctCount = value.getInt("correct_count"),
        lastPlayedAt = Instant.parse(value.getString("last_played_at")).toEpochMilli(),
        contentFingerprint = value.optString("content_fingerprint"),
    )

    private fun requestArray(path: String): List<JSONObject> {
        val array = JSONArray(request(path))
        return (0 until array.length()).map(array::getJSONObject)
    }

    private fun requestObject(path: String): JSONObject = JSONObject(request(path))

    private fun request(path: String, method: String = "GET", token: String? = null, body: String? = null): String {
        val base = apiBaseUrl.trimEnd('/')
        val connection = URL("$base/$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
        }

        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(message?.takeIf(String::isNotBlank) ?: "Server returned $status", status)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

}

class ApiException(message: String, val status: Int? = null) : Exception(message)
