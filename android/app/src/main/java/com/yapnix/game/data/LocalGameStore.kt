package com.yapnix.game.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A complete download is committed only after every picture can be decoded. */
class LocalGameStore(private val context: Context) {
    private val root = File(context.filesDir, "games").apply { mkdirs() }
    suspend fun saveCatalog(games: List<Game>) = withContext(Dispatchers.IO) {
        val items = JSONArray()
        games.forEach { game -> items.put(JSONObject().put("id", game.id).put("name", game.name).put("description", game.description).put("categoryId", game.categoryId).put("categoryName", game.categoryName).put("sessionId", game.sessionId).put("questionCount", game.questionCount)) }
        val pending = File(root, "catalog-pending.json")
        pending.writeText(items.toString())
        check(pending.renameTo(File(root, "catalog.json")))
    }
    suspend fun loadCatalog(): List<Game> = withContext(Dispatchers.IO) {
        runCatching {
            val items = JSONArray(File(root, "catalog.json").readText())
            (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                Game(item.getString("id"), item.getString("name"), item.getString("description"), 1, item.getString("categoryId"), item.getString("categoryName"), item.getString("sessionId"), item.getInt("questionCount"), 5)
            }
        }.getOrDefault(emptyList())
    }
    private fun directory(id: String) = File(root, MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }).apply { mkdirs() }
    suspend fun load(level: Level): PlayableLevel? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = directory(level.id)
            val json = JSONObject(File(dir, "game.json").readText())
            val items = json.getJSONArray("questions")
            val questions = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val image = File(dir, "$i.png")
                check(image.isFile && image.length() > 0)
                Question(item.getString("id"), image.toURI().toString(), item.getString("answer"), emptyList(), item.getString("language"))
            }
            check(questions.isNotEmpty())
            PlayableLevel(level.copy(timeLimitSeconds = 5), questions, json.getString("prompt"))
        }.getOrNull()
    }
    suspend fun save(playable: PlayableLevel): PlayableLevel = withContext(Dispatchers.IO) {
        val dir = directory(playable.level.id)
        val items = JSONArray()
        playable.questions.forEachIndexed { i, question ->
            val result = context.imageLoader.execute(GameImageCache.request(context, question))
            check(result is SuccessResult) { "Could not download picture ${i + 1}" }
            File(dir, "$i.png").outputStream().use { check(result.drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            items.put(JSONObject().put("id", question.id).put("answer", question.primaryAnswer).put("language", question.language))
        }
        val pending = File(dir, "pending.json")
        pending.writeText(JSONObject().put("prompt", playable.prompt).put("questions", items).toString())
        check(pending.renameTo(File(dir, "game.json")))
        checkNotNull(load(playable.level))
    }
}
