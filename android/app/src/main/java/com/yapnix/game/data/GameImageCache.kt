package com.yapnix.game.data

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import android.content.Context
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Refresh game metadata from the API, but reuse image bytes across signed URL renewals. */
object GameImageCache {
    fun request(context: Context, question: Question): ImageRequest {
        val key = "${question.id}:${Uri.parse(question.imageUrl).path}"
        return ImageRequest.Builder(context).data(question.imageUrl)
            .memoryCacheKey(key).diskCacheKey(key).size(1024).allowHardware(false).build()
    }
    suspend fun preload(context: Context, questions: List<Question>, progress: (Int) -> Unit): Map<String, Bitmap> {
        val images = mutableMapOf<String, Bitmap>()
        questions.forEachIndexed { index, question ->
            val result = context.imageLoader.execute(request(context, question))
            check(result is SuccessResult) {
                "Could not download picture ${index + 1}. Check your connection and try again."
            }
            images[question.id] = result.drawable.toBitmap()
            progress(index + 1)
        }
        return images
    }
}
