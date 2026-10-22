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

/** Renders the square game composition, never the phone display or editing controls. */
internal fun drawGameVideoFrame(canvas: Canvas, state: GameUiState, camera: ReactionLayout) {
    val checkpoint = canvas.save()
    canvas.scale(canvas.width / 720f, canvas.height / 1280f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val purple = Color.rgb(115, 82, 239)
    val ink = Color.rgb(37, 33, 56)
    val muted = Color.rgb(130, 124, 148)
    canvas.drawColor(Color.rgb(247, 245, 252))
    paint.color = Color.WHITE
    canvas.drawRoundRect(RectF(32f, 36f, 209f, 100f), 24f, 24f, paint)
    paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    paint.textSize = 32f
    paint.color = purple
    canvas.drawText("yapnix", 57f, 79f, paint)
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = 25f
    paint.color = muted
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("${state.questionIndex + 1} / ${state.playableLevel?.questions?.size ?: 0}", 680f, 78f, paint)
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    paint.textSize = 43f
    paint.color = ink
    val prompt = state.playableLevel?.prompt ?: "What is this?"
    fitText(paint, prompt, 520f, 43f)
    canvas.drawText(prompt, 35f, 188f, paint)
    paint.color = Color.rgb(233, 224, 255)
    canvas.drawRoundRect(RectF(596f, 128f, 688f, 218f), 30f, 30f, paint)
    paint.color = purple
    paint.textSize = 43f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(if (state.phase == SessionPhase.FEEDBACK) "✓" else if (state.phase == SessionPhase.PLAYING) "${state.remainingSeconds}" else "5", 642f, 189f, paint)
    paint.textAlign = Paint.Align.LEFT

    val stage = RectF(32f, 250f, 688f, 906f)
    val clip = Path().apply { addRoundRect(stage, 38f, 38f, Path.Direction.CW) }
    val imageCheckpoint = canvas.save()
    canvas.clipPath(clip)
    paint.color = Color.rgb(233, 224, 255)
    canvas.drawRect(stage, paint)
    val question = state.playableLevel?.questions?.getOrNull(state.questionIndex)
    state.readyImages[question?.id]?.let { drawCrop(canvas, it, stage, paint) }
    if (state.recordEnabled && !state.paused) camera.frame?.takeUnless { it.isRecycled }?.let { frame ->
        val width = stage.width() * camera.size
        val height = width * camera.ratio
        val left = stage.left + camera.x * (stage.width() - width)
        val top = stage.top + camera.y * (stage.height() - height)
        val rect = RectF(left, top, left + width, top + height)
        val radius = if (camera.round) width / 2 else 25f
        val cameraClip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val cameraCheckpoint = canvas.save()
        canvas.clipPath(cameraClip)
        drawCrop(canvas, frame, rect, paint)
        canvas.restoreToCount(cameraCheckpoint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }
    canvas.restoreToCount(imageCheckpoint)

    paint.textAlign = Paint.Align.CENTER
    if (state.phase == SessionPhase.FEEDBACK) {
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 19f
        paint.color = muted
        canvas.drawText("THE ANSWER IS", 360f, 972f, paint)
        paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        paint.color = purple
        val answer = question?.primaryAnswer.orEmpty()
        fitText(paint, answer, 632f, 50f)
        canvas.drawText(answer, 360f, 1039f, paint)
    } else {
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 29f
        paint.color = purple
        canvas.drawText("Say it out loud!", 360f, 1004f, paint)
    }
    val total = state.playableLevel?.questions?.size ?: 1
    val progress = (state.questionIndex + if (state.phase == SessionPhase.FEEDBACK) 1f else 0f) / total.coerceAtLeast(1)
    paint.color = Color.rgb(233, 224, 255)
    canvas.drawRoundRect(RectF(96f, 1120f, 624f, 1128f), 4f, 4f, paint)
    if (progress > 0f) {
        paint.color = purple
        canvas.drawRoundRect(RectF(96f, 1120f, 96f + 528f * progress, 1128f), 4f, 4f, paint)
    }
    paint.color = muted
    paint.textSize = 21f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText("5 second challenge", 360f, 1200f, paint)
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
