package com.yapnix.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** The same simple composition is used on screen and in the local video. */
internal fun drawGameVideoFrame(canvas: Canvas, state: GameUiState, camera: ReactionLayout, includeCamera: Boolean = true,
    renderWidth: Float = canvas.width.toFloat(), renderHeight: Float = canvas.height.toFloat(), stageOnly: Boolean = false) {
    val checkpoint = canvas.save()
    canvas.scale(renderWidth / ReelGeometry.WIDTH, renderHeight / if (stageOnly) ReelGeometry.STAGE_HEIGHT else ReelGeometry.HEIGHT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ink = Color.rgb(35, 38, 33)
    val muted = Color.rgb(117, 119, 109)
    val accent = Color.rgb(210, 236, 135)
    canvas.drawColor(Color.rgb(246, 245, 239))
    if (!stageOnly) canvas.translate(0f, (ReelGeometry.HEIGHT - ReelGeometry.STAGE_HEIGHT) / 2)
    fun text(value: String, x: Float, y: Float, width: Float, size: Float, color: Int = ink, bold: Boolean = false) {
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        fitText(paint, value, width, size)
        canvas.drawText(value, x, y, paint)
    }
    text(state.playableLevel?.prompt ?: "What is this?", 360f, 78f, 624f, 46f, bold = true)
    val question = state.playableLevel?.questions?.getOrNull(state.questionIndex)
    val left = if (state.recordEnabled) ReelGeometry.LEFT else (ReelGeometry.WIDTH - ReelGeometry.IMAGE_SIZE) / 2
    val imageRect = RectF(left, ReelGeometry.IMAGE_TOP, left + ReelGeometry.IMAGE_SIZE, ReelGeometry.IMAGE_BOTTOM)
    val imageCheckpoint = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(imageRect, 24f, 24f, Path.Direction.CW) })
    paint.color = Color.rgb(232, 232, 222)
    canvas.drawRect(imageRect, paint)
    state.readyImages[question?.id]?.let { drawCrop(canvas, it, imageRect, paint) }
    canvas.restoreToCount(imageCheckpoint)
    val centre = imageRect.centerX()
    paint.color = Color.rgb(225, 226, 216)
    val timer = RectF(left, 470f, left + ReelGeometry.IMAGE_SIZE, 480f)
    canvas.drawRoundRect(timer, 5f, 5f, paint)
    paint.color = accent
    timer.right = left + ReelGeometry.IMAGE_SIZE * (if (state.phase == SessionPhase.FEEDBACK) 0f else state.remainingSeconds / 5f)
    canvas.drawRoundRect(timer, 5f, 5f, paint)
    text(when { state.paused -> "Paused"; state.phase == SessionPhase.FEEDBACK -> "THE ANSWER"; state.phase == SessionPhase.PREPARING -> "Get ready…"; else -> "${state.remainingSeconds}s to guess" }, centre, 530f, 296f, 29f, muted)
    // The answer card ends at exactly the same baseline as the reaction camera.
    paint.color = if (state.phase == SessionPhase.FEEDBACK) accent else Color.rgb(233, 237, 222)
    canvas.drawRoundRect(RectF(left, 552f, left + ReelGeometry.IMAGE_SIZE, ReelGeometry.CAMERA_BOTTOM), 18f, 18f, paint)
    text(if (state.phase == SessionPhase.FEEDBACK) question?.primaryAnswer.orEmpty() else "Say it out loud", centre, 623f, 268f, 39f, bold = true)
    if (state.recordEnabled) {
        val pane = RectF(ReelGeometry.CAMERA_LEFT, ReelGeometry.CAMERA_TOP, ReelGeometry.RIGHT, ReelGeometry.CAMERA_BOTTOM)
        paint.color = Color.rgb(233, 237, 222)
        canvas.drawRoundRect(pane, 24f, 24f, paint)
        if (includeCamera) camera.frame?.takeUnless { it.isRecycled }?.let { frame ->
            val clip = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(pane, 24f, 24f, Path.Direction.CW) })
            drawCrop(canvas, frame, pane, paint)
            canvas.restoreToCount(clip)
        }
    }
    // A quiet watermark only on the exported video.
    if (!stageOnly) text("yapnix", 360f, 750f, 200f, 24f, muted, true)
    canvas.restoreToCount(checkpoint)
}

private fun fitText(paint: Paint, text: String, width: Float, desiredSize: Float) {
    paint.textSize = desiredSize
    paint.textSize = min(desiredSize, desiredSize * width / paint.measureText(text).coerceAtLeast(1f))
}

private fun drawCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF, paint: Paint) {
    if (bitmap.isRecycled) return
    val sourceRatio = bitmap.width.toFloat() / bitmap.height
    val targetRatio = destination.width() / destination.height()
    val source = if (sourceRatio > targetRatio) {
        val width = (bitmap.height * targetRatio).toInt()
        val left = (bitmap.width - width) / 2
        Rect(left, 0, left + width, bitmap.height)
    } else {
        val height = (bitmap.width / targetRatio).toInt()
        val top = (bitmap.height - height) / 2
        Rect(0, top, bitmap.width, top + height)
    }
    canvas.drawBitmap(bitmap, source, destination, paint)
}
