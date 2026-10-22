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

/** Draws only game content and the reaction, without the phone display or tools. */
internal fun drawGameVideoFrame(canvas: Canvas, state: GameUiState, camera: ReactionLayout, includeCamera: Boolean = true, renderWidth: Float = canvas.width.toFloat(), renderHeight: Float = canvas.height.toFloat()) {
    val checkpoint = canvas.save()
    canvas.scale(renderWidth / ReelGeometry.WIDTH, renderHeight / ReelGeometry.HEIGHT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ink = Color.rgb(35, 38, 33)
    val muted = Color.rgb(117, 119, 109)
    val paper = Color.rgb(246, 245, 239)
    val accent = Color.rgb(210, 236, 135)
    canvas.drawColor(paper)
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 33f
    paint.color = ink
    canvas.drawText("yapnix", ReelGeometry.LEFT, 67f, paint)
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = 22f
    paint.color = muted
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("${state.questionIndex + 1} / ${state.playableLevel?.questions?.size ?: 0}", ReelGeometry.RIGHT, 65f, paint)
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.color = ink
    val prompt = state.playableLevel?.prompt ?: "What is this?"
    fitText(paint, prompt, 520f, 35f)
    canvas.drawText(prompt, ReelGeometry.LEFT, 120f, paint)
    paint.color = accent
    canvas.drawCircle(642f, 108f, 30f, paint)
    paint.color = ink
    paint.textSize = 30f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(if (state.phase == SessionPhase.FEEDBACK) "✓" else if (state.phase == SessionPhase.PLAYING) "${state.remainingSeconds}" else "5", 642f, 119f, paint)

    val question = state.playableLevel?.questions?.getOrNull(state.questionIndex)
    val imageRect = RectF(ReelGeometry.LEFT, ReelGeometry.IMAGE_TOP, ReelGeometry.RIGHT, ReelGeometry.IMAGE_BOTTOM)
    val imageCheckpoint = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(imageRect, 20f, 20f, Path.Direction.CW) })
    paint.color = Color.rgb(232, 232, 222)
    canvas.drawRect(imageRect, paint)
    state.readyImages[question?.id]?.let { drawCrop(canvas, it, imageRect, paint) }
    canvas.restoreToCount(imageCheckpoint)

    paint.textAlign = Paint.Align.CENTER
    if (state.phase == SessionPhase.FEEDBACK) {
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 17f
        paint.color = muted
        canvas.drawText("THE ANSWER", 360f, 810f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = ink
        val answer = question?.primaryAnswer.orEmpty()
        fitText(paint, answer, 608f, 35f)
        canvas.drawText(answer, 360f, 854f, paint)
    } else {
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 26f
        paint.color = ink
        canvas.drawText("Say it out loud", 360f, 830f, paint)
    }

    val pane = RectF(ReelGeometry.LEFT, ReelGeometry.CAMERA_TOP, ReelGeometry.RIGHT, ReelGeometry.CAMERA_BOTTOM)
    paint.color = ink
    canvas.drawRoundRect(pane, 20f, 20f, paint)
    val paneCheckpoint = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(pane, 20f, 20f, Path.Direction.CW) })
    if (includeCamera && state.recordEnabled && !state.paused) camera.frame?.takeUnless { it.isRecycled }?.let { frame ->
        val bounds = ReelGeometry.cameraRect(camera.x, camera.y, camera.size, camera.round)
        val rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val radius = if (camera.round) rect.width() / 2 else 14f
        val cameraCheckpoint = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        drawCrop(canvas, frame, rect, paint)
        canvas.restoreToCount(cameraCheckpoint)
    }
    canvas.restoreToCount(paneCheckpoint)
    paint.color = muted
    paint.textSize = 16f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("5 SECOND CHALLENGE", ReelGeometry.LEFT, 1265f, paint)
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("yapnix", ReelGeometry.RIGHT, 1265f, paint)
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
